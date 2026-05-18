package com.hank.musicfree.feature.search.runtime

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.feature.search.PluginSearchState
import com.hank.musicfree.feature.search.SearchMediaType
import com.hank.musicfree.feature.search.SearchPageStatus
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.PluginSearchItem
import com.hank.musicfree.plugin.api.SearchResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchSessionStoreTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun restoreSnapshotPublishesPreviousResultsWhenSignatureMatches() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        snapshotStore.write(searchSnapshot(query = "hello", sourceSignature = "plugin:demo:1"))
        val store = searchSessionStore(
            snapshotStore = snapshotStore,
            signature = "plugin:demo:1",
        )

        assertEquals(false, store.restoreOnStartup)
        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Restored, result)
        assertEquals("hello", store.state.value.query)
        assertEquals("demo", store.state.value.selectedPlatform)
        assertEquals(SearchPageStatus.RESULT, store.state.value.pageStatus)
        val success = store.state.value.results.getValue(SearchMediaType.MUSIC).getValue("demo")
            as PluginSearchState.Success
        assertEquals("song-1", (success.items.single() as PluginSearchItem.Music).item.id)

        val event = logger.events.single { it.event == "search_session_restore_success" }
        assertEquals(LogCategory.SEARCH, event.category)
        assertEquals(SearchSessionStore.STORE_NAME, event.fields["store"])
        assertEquals("search_session_restore", event.fields["operation"])
        assertEquals("hello", event.fields["query"])
        assertEquals("demo", event.fields["platform"])
        assertEquals("music", event.fields["mediaType"])
        assertEquals(1, event.fields["count"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
        assertTrue(event.fields.containsKey("durationMs"))
    }

    @Test
    fun restoreSnapshotWithChangedPluginSignatureKeepsQueryButDropsTrustedResults() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        snapshotStore.write(searchSnapshot(query = "hello", sourceSignature = "plugin:demo:old"))
        val store = searchSessionStore(
            snapshotStore = snapshotStore,
            signature = "plugin:demo:new",
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Stale("plugin_signature_changed"), result)
        assertEquals("hello", store.state.value.query)
        assertEquals("demo", store.state.value.selectedPlatform)
        assertEquals("plugin_signature_changed", store.state.value.restoreReason)
        assertTrue(store.state.value.results.isEmpty())

        val event = logger.events.single { it.event == "search_session_restore_stale" }
        assertEquals(LogFields.Result.STALE, event.fields["result"])
        assertEquals("plugin_signature_changed", event.fields["reason"])
        assertEquals(0, event.fields["count"])
    }

    @Test
    fun restoreDefersSignatureCheckUntilPluginsAreReady() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        val signatureProvider = MutableSignatureProvider(signature = "")
        snapshotStore.write(searchSnapshot(query = "hello", sourceSignature = "plugin:demo:1"))
        val store = searchSessionStore(
            snapshotStore = snapshotStore,
            signatureProvider = signatureProvider,
        )

        assertEquals(RuntimeRestoreResult.Restored, store.restore())
        assertEquals("plugin_signature_pending", store.state.value.restoreReason)
        assertTrue(store.state.value.results.isNotEmpty())

        signatureProvider.signature = "plugin:demo:2"
        store.setPluginsReady()

        assertEquals("hello", store.state.value.query)
        assertEquals("plugin_signature_changed", store.state.value.restoreReason)
        assertTrue(store.state.value.results.isEmpty())
        val event = logger.events.last { it.event == "search_session_restore_stale" }
        assertEquals(LogFields.Result.STALE, event.fields["result"])
        assertEquals("plugin_signature_changed", event.fields["reason"])
    }

    @Test
    fun staleSearchResultDoesNotOverwriteNewGeneration() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val gateway = ControllableSearchGateway()
        val store = searchSessionStore(gateway = gateway)
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("demo")))

        val oldSearch = launch { store.search("old") }
        advanceUntilIdle()
        val newSearch = launch { store.search("new") }
        advanceUntilIdle()

        gateway.complete(
            query = "new",
            result = SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("new-result")))),
        )
        advanceUntilIdle()
        gateway.complete(
            query = "old",
            result = SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("old-result")))),
        )
        oldSearch.join()
        newSearch.join()

        assertEquals("new", store.state.value.query)
        val success = store.state.value.results.getValue(SearchMediaType.MUSIC).getValue("demo")
            as PluginSearchState.Success
        assertEquals("new-result", (success.items.single() as PluginSearchItem.Music).item.id)
        assertEquals(SearchPageStatus.RESULT, store.state.value.pageStatus)

        val stale = logger.events.single { it.event == "search_session_result_stale" }
        assertEquals(LogFields.Result.STALE, stale.fields["result"])
        assertEquals(LogFields.Reason.STALE_GENERATION, stale.fields["reason"])
        assertEquals("old", stale.fields["query"])
    }

    @Test
    fun terminalSearchSuccessPersistsSnapshotAndStructuredLog() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        val store = searchSessionStore(
            snapshotStore = snapshotStore,
            gateway = StaticSearchGateway(
                SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("song-2")))),
            ),
        )
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("demo")))

        store.search("hello")

        val key = snapshotStore.keys(NAMESPACE, limit = 10).single()
        val snapshot = snapshotStore.read(NAMESPACE, key)
        assertEquals("sig", snapshot?.sourceSignature)
        assertTrue(snapshot?.payloadJson.orEmpty().contains("song-2"))

        val event = logger.events.single { it.event == "search_session_persist_success" }
        assertEquals("search_session_persist", event.fields["operation"])
        assertEquals(key, event.fields["key"])
        assertEquals("hello", event.fields["query"])
        assertEquals("demo", event.fields["platform"])
        assertEquals(1, event.fields["count"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
        assertTrue(event.fields.containsKey("durationMs"))
    }

    private fun searchSessionStore(
        snapshotStore: InMemorySnapshotStore = InMemorySnapshotStore(),
        gateway: SearchSessionGateway = StaticSearchGateway(
            SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("song-1")))),
        ),
        signature: String = "sig",
        signatureProvider: SearchPluginSignatureProvider = SearchPluginSignatureProvider { signature },
        clock: SearchSessionClock = SearchSessionClock { 1_000L },
    ): SearchSessionStore = SearchSessionStore(
        snapshotStore = snapshotStore,
        gateway = gateway,
        signatureProvider = signatureProvider,
        json = Json { ignoreUnknownKeys = true },
        clock = clock,
    )

    private fun searchSnapshot(query: String, sourceSignature: String): RuntimeSnapshot {
        val payload = SearchSessionSnapshot(
            query = query,
            mediaType = SearchMediaType.MUSIC.key,
            selectedPlatform = "demo",
            generation = 3L,
            platformResults = mapOf(
                "demo" to SearchPlatformSnapshot(
                    page = 1,
                    isEnd = true,
                    itemCount = 1,
                    payloadJson = """
                        [
                          {
                            "type": "music",
                            "item": {
                              "id": "song-1",
                              "platform": "demo",
                              "title": "Song 1",
                              "artist": "Artist 1",
                              "album": null,
                              "duration": 180000,
                              "url": null,
                              "artwork": null,
                              "raw": {},
                              "addedAt": 0,
                              "localPath": null
                            }
                          }
                        ]
                    """.trimIndent(),
                ),
            ),
        )
        return RuntimeSnapshot(
            namespace = NAMESPACE,
            key = "search:music:demo:fixture",
            snapshotVersion = 1,
            sourceSignature = sourceSignature,
            createdAtEpochMs = 500L,
            updatedAtEpochMs = 500L,
            expiresAtEpochMs = 60_000L,
            payloadJson = Json.encodeToString(payload),
        )
    }

    private class StaticSearchGateway(
        private val result: SearchResult,
    ) : SearchSessionGateway {
        override suspend fun search(
            platform: String,
            query: String,
            page: Int,
            mediaType: SearchMediaType,
        ): SearchResult = result
    }

    private class ControllableSearchGateway : SearchSessionGateway {
        private val requests = CopyOnWriteArrayList<PendingRequest>()

        override suspend fun search(
            platform: String,
            query: String,
            page: Int,
            mediaType: SearchMediaType,
        ): SearchResult {
            val request = PendingRequest(query = query, deferred = CompletableDeferred())
            requests += request
            return request.deferred.await()
        }

        fun complete(query: String, result: SearchResult) {
            requests.single { it.query == query }.deferred.complete(result)
        }
    }

    private data class PendingRequest(
        val query: String,
        val deferred: CompletableDeferred<SearchResult>,
    )

    private class MutableSignatureProvider(
        var signature: String,
    ) : SearchPluginSignatureProvider {
        override fun currentSignature(): String = signature
    }

    private class InMemorySnapshotStore : SnapshotStore {
        private val snapshots = mutableMapOf<Pair<String, String>, RuntimeSnapshot>()

        override suspend fun read(namespace: String, key: String): RuntimeSnapshot? =
            snapshots[namespace to key]

        override suspend fun write(snapshot: RuntimeSnapshot) {
            snapshots[snapshot.namespace to snapshot.key] = snapshot
        }

        override suspend fun delete(namespace: String, key: String) {
            snapshots.remove(namespace to key)
        }

        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int {
            val expired = snapshots.filter { (identity, snapshot) ->
                identity.first == namespace && snapshot.isExpired(nowEpochMs)
            }.keys
            expired.forEach { snapshots.remove(it) }
            return expired.size
        }

        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int {
            val stale = snapshots.values
                .filter { it.namespace == namespace }
                .sortedByDescending { it.updatedAtEpochMs }
                .drop(keepLatest)
            stale.forEach { snapshots.remove(it.namespace to it.key) }
            return stale.size
        }

        override suspend fun keys(namespace: String, limit: Int): List<String> =
            snapshots.values
                .filter { it.namespace == namespace }
                .sortedByDescending { it.updatedAtEpochMs }
                .take(limit)
                .map { it.key }
    }

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = CopyOnWriteArrayList<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(category, event, fields + ("throwable" to throwable))
        }

        override fun flush() = Unit
    }

    private companion object {
        const val NAMESPACE = "search_session"
    }
}

private fun plugin(platform: String): PluginInfo = PluginInfo(
    platform = platform,
    version = "1",
    author = null,
    description = null,
    srcUrl = null,
    supportedSearchType = listOf(SearchMediaType.MUSIC.key),
    supportedMethods = setOf("search"),
    hash = "hash-$platform",
)

private fun music(id: String): MusicItem = MusicItem(
    id = id,
    platform = "demo",
    title = id,
    artist = "Artist $id",
    album = null,
    duration = 180_000L,
    url = null,
    artwork = null,
    qualities = null,
)
