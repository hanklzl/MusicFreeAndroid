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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
        runCurrent()
        val newSearch = launch { store.search("new") }
        runCurrent()

        gateway.complete(
            query = "new",
            result = SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("new-result")))),
        )
        runCurrent()
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
    fun staleSearchFailureDoesNotOverwriteNewGeneration() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val gateway = ControllableSearchGateway()
        val store = searchSessionStore(gateway = gateway)
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("demo")))

        val oldSearch = launch { store.search("old") }
        runCurrent()
        val newSearch = launch { store.search("new") }
        runCurrent()

        gateway.complete(
            query = "new",
            result = SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("new-result")))),
        )
        runCurrent()
        gateway.fail(query = "old", error = IllegalStateException("old failed"))
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

    // Search is now page-lifecycle (see 2026-06-05-search-session-page-lifecycle spec):
    // the search hot path no longer auto-persists. persist() is retained for the
    // RuntimeStore contract and exercised here directly to keep snapshot coverage.
    @Test
    fun persistWritesSnapshotAndStructuredLog() = runTest {
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
        store.persist()

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

    @Test
    fun selectedPlatformIsIndependentPerMediaType() = runTest {
        val store = searchSessionStore()
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("music-a"), plugin("music-b")))
        store.setSearchablePlugins(SearchMediaType.ALBUM, listOf(plugin("album-a", listOf(SearchMediaType.ALBUM.key))))

        store.selectPlatform(SearchMediaType.MUSIC, "music-b")
        store.selectMediaType(SearchMediaType.ALBUM)
        store.selectPlatform(SearchMediaType.ALBUM, "album-a")

        assertEquals("music-b", store.state.value.selectedPlatforms[SearchMediaType.MUSIC])
        assertEquals("album-a", store.state.value.selectedPlatforms[SearchMediaType.ALBUM])
        assertEquals("album-a", store.state.value.selectedPlatform)
    }

    @Test
    fun ensureMediaSearchedKeepsMediaResultsIndependent() = runTest {
        val gateway = MultiMediaGateway(
            musicResult = SearchResult(
                isEnd = true,
                data = listOf(PluginSearchItem.Music(music("music-result"))),
            ),
            albumResult = SearchResult(
                isEnd = true,
                data = listOf(
                    PluginSearchItem.Album(
                        com.hank.musicfree.plugin.api.AlbumItemBase(
                            id = "album-1",
                            platform = "album-plugin",
                            title = "Album 1",
                            date = null,
                            artist = null,
                            description = null,
                            artwork = null,
                            worksNum = null,
                            raw = emptyMap(),
                        ),
                    ),
                ),
            ),
        )
        val store = searchSessionStore(gateway = gateway)
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("music-plugin")))
        store.setSearchablePlugins(SearchMediaType.ALBUM, listOf(plugin("album-plugin", listOf(SearchMediaType.ALBUM.key))))

        store.search("hello")
        val musicResult = store.state.value.results[SearchMediaType.MUSIC]?.get("music-plugin")
        assertTrue(musicResult is PluginSearchState.Success)
        assertEquals(
            "music-result",
            (musicResult as PluginSearchState.Success).items.first().let { (it as PluginSearchItem.Music).item.id },
        )

        store.selectMediaType(SearchMediaType.ALBUM)
        store.ensureMediaSearched(SearchMediaType.ALBUM)
        val albumResult = store.state.value.results[SearchMediaType.ALBUM]?.get("album-plugin")
        assertTrue(albumResult is PluginSearchState.Success)
        assertEquals(1, (albumResult as PluginSearchState.Success).items.size)
    }

    @Test
    fun loadMoreOnlyUpdatesRequestedMediaAndPlatformScene() = runTest {
        val gateway = MultiMediaLoadMoreGateway(
            musicFirst = listOf(PluginSearchItem.Music(music("music-1"))),
            musicSecond = listOf(PluginSearchItem.Music(music("music-2"))),
            albumFirst = listOf(
                PluginSearchItem.Album(
                    com.hank.musicfree.plugin.api.AlbumItemBase(
                        id = "album-1",
                        platform = "album-plugin",
                        title = "Album 1",
                        date = null,
                        artist = null,
                        description = null,
                        artwork = null,
                        worksNum = null,
                        raw = emptyMap(),
                    ),
                ),
            ),
            albumSecond = listOf(
                PluginSearchItem.Album(
                    com.hank.musicfree.plugin.api.AlbumItemBase(
                        id = "album-2",
                        platform = "album-plugin",
                        title = "Album 2",
                        date = null,
                        artist = null,
                        description = null,
                        artwork = null,
                        worksNum = null,
                        raw = emptyMap(),
                    ),
                ),
            ),
        )
        val store = searchSessionStore(gateway = gateway)
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("music-plugin")))
        store.setSearchablePlugins(SearchMediaType.ALBUM, listOf(plugin("album-plugin", listOf(SearchMediaType.ALBUM.key))))

        store.search("hello")
        val musicBefore = store.state.value.results[SearchMediaType.MUSIC]?.get("music-plugin")
        assertTrue(musicBefore is PluginSearchState.Success)
        assertEquals(1, (musicBefore as PluginSearchState.Success).items.size)

        store.ensureMediaSearched(SearchMediaType.ALBUM)
        val albumBefore = store.state.value.results[SearchMediaType.ALBUM]?.get("album-plugin")
        assertTrue(albumBefore is PluginSearchState.Success)
        assertEquals(1, (albumBefore as PluginSearchState.Success).items.size)

        store.loadMore(SearchMediaType.ALBUM, "album-plugin")
        val albumAfter = store.state.value.results[SearchMediaType.ALBUM]?.get("album-plugin")
        val musicAfter = store.state.value.results[SearchMediaType.MUSIC]?.get("music-plugin")
        assertTrue(albumAfter is PluginSearchState.Success)
        assertEquals(2, (albumAfter as PluginSearchState.Success).items.size)
        assertTrue(musicAfter is PluginSearchState.Success)
        assertEquals(1, (musicAfter as PluginSearchState.Success).items.size)
    }

    @Test
    fun selectingCompletedMediaWhileOtherMediaSearchesRestoresResultStatus() = runTest {
        val gateway = DelayedAlbumSearchGateway()
        val store = searchSessionStore(gateway = gateway)
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("music-plugin")))
        store.setSearchablePlugins(SearchMediaType.ALBUM, listOf(plugin("album-plugin", listOf(SearchMediaType.ALBUM.key))))

        store.search("hello")
        assertEquals(SearchPageStatus.RESULT, store.state.value.pageStatus)

        store.selectMediaType(SearchMediaType.ALBUM)
        val albumSearch = launch { store.ensureMediaSearched(SearchMediaType.ALBUM) }
        runCurrent()
        assertEquals(SearchPageStatus.SEARCHING, store.state.value.pageStatus)

        store.selectMediaType(SearchMediaType.MUSIC)

        assertEquals(SearchPageStatus.RESULT, store.state.value.pageStatus)
        gateway.completeAlbum()
        albumSearch.join()
    }

    @Test
    fun completedPluginShowsResultPanelWhilePeerPluginStillLoads() = runTest {
        val gateway = ControllablePlatformSearchGateway()
        val store = searchSessionStore(gateway = gateway)
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("slow"), plugin("fast")))

        val searchJob = launch { store.search("hello") }
        runCurrent()

        gateway.complete(
            platform = "fast",
            result = SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("fast-result")))),
        )
        runCurrent()

        assertEquals(SearchPageStatus.RESULT, store.state.value.pageStatus)
        val typeResults = store.state.value.results.getValue(SearchMediaType.MUSIC)
        assertTrue(typeResults.getValue("slow") is PluginSearchState.Loading)
        val fastResult = typeResults.getValue("fast")
        assertTrue(fastResult is PluginSearchState.Success)
        assertEquals(
            "fast-result",
            ((fastResult as PluginSearchState.Success).items.single() as PluginSearchItem.Music).item.id,
        )
        assertTrue(searchJob.isActive)

        gateway.complete(
            platform = "slow",
            result = SearchResult(isEnd = true, data = listOf(PluginSearchItem.Music(music("slow-result")))),
        )
        searchJob.join()
    }

    @Test
    fun pluginSearchTimeoutMarksOnlyThatPluginAsError() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val store = searchSessionStore(gateway = NeverReturningSearchGateway())
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("demo")))

        val searchJob = launch { store.search("hello") }
        runCurrent()
        advanceTimeBy(15_001L)
        advanceUntilIdle()

        assertEquals(SearchPageStatus.RESULT, store.state.value.pageStatus)
        val state = store.state.value.results.getValue(SearchMediaType.MUSIC).getValue("demo")
        assertTrue(state is PluginSearchState.Error)
        assertEquals("搜索超时", (state as PluginSearchState.Error).message)

        val event = logger.events.single { it.event == "search_plugin_timeout" }
        assertEquals("demo", event.fields["platform"])
        assertEquals("music", event.fields["mediaType"])
        assertEquals("hello", event.fields["query"])
        assertEquals(LogFields.Result.FAILURE, event.fields["result"])
        assertEquals("timeout", event.fields["reason"])
        assertEquals(0, event.fields["pendingCount"])
        searchJob.join()
    }

    @Test
    fun loadMoreTimeoutLogsFailureAndKeepsExistingResults() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val gateway = LoadMoreTimeoutGateway()
        val store = searchSessionStore(gateway = gateway)
        store.setSearchablePlugins(SearchMediaType.MUSIC, listOf(plugin("demo")))
        store.search("hello")

        val loadMoreJob = launch { store.loadMore(SearchMediaType.MUSIC, "demo") }
        runCurrent()
        advanceTimeBy(15_001L)
        advanceUntilIdle()

        val state = store.state.value.results.getValue(SearchMediaType.MUSIC).getValue("demo")
        assertTrue(state is PluginSearchState.Success)
        assertEquals(1, (state as PluginSearchState.Success).items.size)
        assertEquals(1, state.page)

        val event = logger.events.single { it.event == "search_session_page_timeout" }
        assertEquals("demo", event.fields["platform"])
        assertEquals(2, event.fields["page"])
        assertEquals(LogFields.Result.FAILURE, event.fields["result"])
        assertEquals("timeout", event.fields["reason"])
        loadMoreJob.join()
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

        fun fail(query: String, error: Throwable) {
            requests.single { it.query == query }.deferred.completeExceptionally(error)
        }
    }

    private data class PendingRequest(
        val query: String,
        val deferred: CompletableDeferred<SearchResult>,
    )

    private data class PendingPlatformRequest(
        val platform: String,
        val deferred: CompletableDeferred<SearchResult>,
    )

    private class ControllablePlatformSearchGateway : SearchSessionGateway {
        private val requests = CopyOnWriteArrayList<PendingPlatformRequest>()

        override suspend fun search(
            platform: String,
            query: String,
            page: Int,
            mediaType: SearchMediaType,
        ): SearchResult {
            val request = PendingPlatformRequest(platform = platform, deferred = CompletableDeferred())
            requests += request
            return request.deferred.await()
        }

        fun complete(platform: String, result: SearchResult) {
            requests.single { it.platform == platform }.deferred.complete(result)
        }
    }

    private class NeverReturningSearchGateway : SearchSessionGateway {
        override suspend fun search(
            platform: String,
            query: String,
            page: Int,
            mediaType: SearchMediaType,
        ): SearchResult = CompletableDeferred<SearchResult>().await()
    }

    private class LoadMoreTimeoutGateway : SearchSessionGateway {
        override suspend fun search(
            platform: String,
            query: String,
            page: Int,
            mediaType: SearchMediaType,
        ): SearchResult {
            if (page == 1) {
                return SearchResult(
                    isEnd = false,
                    data = listOf(PluginSearchItem.Music(music("first-page"))),
                )
            }
            return CompletableDeferred<SearchResult>().await()
        }
    }

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

private fun plugin(platform: String, supportedSearchType: List<String>): PluginInfo = PluginInfo(
    platform = platform,
    version = "1",
    author = null,
    description = null,
    srcUrl = null,
    supportedSearchType = supportedSearchType,
    supportedMethods = setOf("search"),
    hash = "hash-$platform",
)

private class MultiMediaGateway(
    private val musicResult: SearchResult,
    private val albumResult: SearchResult,
) : SearchSessionGateway {
    override suspend fun search(
        platform: String,
        query: String,
        page: Int,
        mediaType: SearchMediaType,
    ): SearchResult = when (mediaType) {
        SearchMediaType.MUSIC -> musicResult
        SearchMediaType.ALBUM -> albumResult
        else -> SearchResult(isEnd = true, data = emptyList())
    }
}

private class MultiMediaLoadMoreGateway(
    private val musicFirst: List<PluginSearchItem>,
    private val musicSecond: List<PluginSearchItem>,
    private val albumFirst: List<PluginSearchItem>,
    private val albumSecond: List<PluginSearchItem>,
) : SearchSessionGateway {
    override suspend fun search(
        platform: String,
        query: String,
        page: Int,
        mediaType: SearchMediaType,
    ): SearchResult = when (mediaType) {
        SearchMediaType.MUSIC -> {
            if (page == 1) SearchResult(isEnd = false, data = musicFirst) else SearchResult(isEnd = true, data = musicSecond)
        }
        SearchMediaType.ALBUM -> {
            if (page == 1) SearchResult(isEnd = false, data = albumFirst) else SearchResult(isEnd = true, data = albumSecond)
        }
        else -> SearchResult(isEnd = true, data = emptyList())
    }
}

private class DelayedAlbumSearchGateway : SearchSessionGateway {
    private val albumResult = CompletableDeferred<SearchResult>()

    override suspend fun search(
        platform: String,
        query: String,
        page: Int,
        mediaType: SearchMediaType,
    ): SearchResult = when (mediaType) {
        SearchMediaType.MUSIC -> SearchResult(
            isEnd = true,
            data = listOf(PluginSearchItem.Music(music("music-1"))),
        )
        SearchMediaType.ALBUM -> albumResult.await()
        else -> SearchResult(isEnd = true, data = emptyList())
    }

    fun completeAlbum() {
        albumResult.complete(
            SearchResult(
                isEnd = true,
                data = listOf(
                    PluginSearchItem.Album(
                        com.hank.musicfree.plugin.api.AlbumItemBase(
                            id = "album-1",
                            platform = "album-plugin",
                            title = "Album 1",
                            date = null,
                            artist = null,
                            description = null,
                            artwork = null,
                            worksNum = null,
                            raw = emptyMap(),
                        ),
                    ),
                ),
            ),
        )
    }
}

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
