package com.hank.musicfree.feature.home.runtime

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.plugin.api.MusicSheetItemBase
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
class DetailSessionStoreTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun restoreDetailSnapshotPublishesHeaderAndPageResults() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val key = detailKey()
        val snapshotStore = InMemorySnapshotStore().apply {
            write(detailSnapshot(key = key, sourceSignature = "plugin:demo:1"))
        }
        val store = detailStore(
            snapshotStore = snapshotStore,
            signature = "plugin:demo:1",
        )

        assertEquals(false, store.restoreOnStartup)
        val result = store.restore(key)

        assertEquals(RuntimeRestoreResult.Restored, result)
        val detail = store.state.value.sessions.getValue(key)
        assertEquals("Demo Sheet", detail.header.title)
        assertEquals(20, detail.items.size)
        assertEquals(1, detail.page)
        assertEquals(false, detail.needsRefresh)

        val event = logger.events.single { it.event == "detail_session_restore_success" }
        assertEquals(LogCategory.HOME, event.category)
        assertEquals("detail_session", event.fields["store"])
        assertEquals("detail_session_restore", event.fields["operation"])
        assertEquals(DetailRouteTypes.PLUGIN_SHEET, event.fields["routeType"])
        assertEquals("demo", event.fields["platform"])
        assertEquals("sheet-1", event.fields["itemId"])
        assertEquals(20, event.fields["count"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
        assertTrue(event.fields.containsKey("durationMs"))
    }

    @Test
    fun pluginSignatureMismatchMarksSnapshotStale() = runTest {
        val key = detailKey()
        val snapshotStore = InMemorySnapshotStore().apply {
            write(detailSnapshot(key = key, sourceSignature = "plugin:demo:old"))
        }
        val store = detailStore(
            snapshotStore = snapshotStore,
            signature = "plugin:demo:new",
        )

        val result = store.restore(key)

        assertEquals(RuntimeRestoreResult.Stale("plugin_signature_changed"), result)
        val detail = store.state.value.sessions.getValue(key)
        assertEquals("Demo Sheet", detail.header.title)
        assertTrue(detail.items.isEmpty())
        assertEquals(true, detail.needsRefresh)
    }

    @Test
    fun loadInitialPersistsSnapshotAndSkipsReloadWhenSessionExists() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        val gateway = RecordingGateway(
            results = mutableMapOf(
                1 to DetailLoadResult(sheetHeader("Loaded Sheet"), musicItems(2), isEnd = false),
            ),
        )
        val store = detailStore(snapshotStore = snapshotStore, gateway = gateway)
        val request = request()

        store.loadInitial(request)
        store.loadInitial(request)

        assertEquals(listOf(1), gateway.pages)
        val detail = store.state.value.sessions.getValue(request.key)
        assertEquals("Loaded Sheet", detail.header.title)
        assertEquals(2, detail.items.size)
        assertEquals(1, snapshotStore.keys(NAMESPACE, limit = 20).size)

        val event = logger.events.single { it.event == "detail_session_persist_success" }
        assertEquals("detail_session_persist", event.fields["operation"])
        assertEquals(2, event.fields["count"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
    }

    @Test
    fun loadInitialRestoresSnapshotLazilyAndSkipsNetworkReload() = runTest {
        val key = detailKey()
        val snapshotStore = InMemorySnapshotStore().apply {
            write(detailSnapshot(key = key, sourceSignature = "plugin:demo:1"))
        }
        val gateway = RecordingGateway(results = mutableMapOf())
        val store = detailStore(
            snapshotStore = snapshotStore,
            gateway = gateway,
            signature = "plugin:demo:1",
        )

        store.loadInitial(request())

        assertTrue(gateway.pages.isEmpty())
        val detail = store.state.value.sessions.getValue(key)
        assertEquals("Demo Sheet", detail.header.title)
        assertEquals(20, detail.items.size)
    }

    @Test
    fun staleDetailResultDoesNotOverwriteNewGeneration() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val gateway = ControllableGateway()
        val store = detailStore(gateway = gateway)
        val request = request()

        val oldLoad = launch { store.loadInitial(request) }
        advanceUntilIdle()
        val newLoad = launch { store.loadInitial(request, forceRefresh = true) }
        advanceUntilIdle()

        gateway.completeLatest(page = 1, title = "New Sheet", ids = listOf("new"))
        advanceUntilIdle()
        gateway.completeOldest(page = 1, title = "Old Sheet", ids = listOf("old"))
        oldLoad.join()
        newLoad.join()

        val detail = store.state.value.sessions.getValue(request.key)
        assertEquals("New Sheet", detail.header.title)
        assertEquals("new", detail.items.single().id)

        val event = logger.events.single { it.event == "detail_session_result_stale" }
        assertEquals(LogFields.Result.STALE, event.fields["result"])
        assertEquals(LogFields.Reason.STALE_GENERATION, event.fields["reason"])
    }

    private fun detailStore(
        snapshotStore: InMemorySnapshotStore = InMemorySnapshotStore(),
        gateway: DetailSessionGateway = RecordingGateway(
            results = mutableMapOf(1 to DetailLoadResult(sheetHeader("Demo Sheet"), musicItems(1), isEnd = true)),
        ),
        signature: String = "plugin:demo:1",
    ): DetailSessionStore = DetailSessionStore(
        snapshotStore = snapshotStore,
        gateway = gateway,
        signatureProvider = DetailPluginSignatureProvider { signature },
        json = Json { ignoreUnknownKeys = true },
        clock = DetailSessionClock { 1_000L },
    )

    private fun detailSnapshot(key: String, sourceSignature: String): RuntimeSnapshot {
        val snapshot = DetailSessionSnapshot(
            routeType = DetailRouteTypes.PLUGIN_SHEET,
            platform = "demo",
            id = "sheet-1",
            headerJson = sheetHeader("Demo Sheet").toSnapshotJson(Json),
            rawJson = """{"id":"sheet-1","platform":"demo"}""",
            page = 1,
            isEnd = false,
            itemCount = 20,
            itemsJson = Json.encodeMusicItems(musicItems(20)),
        )
        return RuntimeSnapshot(
            namespace = NAMESPACE,
            key = key,
            snapshotVersion = 1,
            sourceSignature = sourceSignature,
            createdAtEpochMs = 500,
            updatedAtEpochMs = 500,
            expiresAtEpochMs = 60_000,
            payloadJson = Json.encodeToString(snapshot),
        )
    }

    private class RecordingGateway(
        private val results: MutableMap<Int, DetailLoadResult?>,
    ) : DetailSessionGateway {
        val pages = mutableListOf<Int>()

        override suspend fun load(request: DetailLoadRequest): DetailLoadResult? {
            pages += request.page
            return results[request.page]
        }
    }

    private class ControllableGateway : DetailSessionGateway {
        private val pending = CopyOnWriteArrayList<PendingRequest>()

        override suspend fun load(request: DetailLoadRequest): DetailLoadResult? {
            val deferred = CompletableDeferred<DetailLoadResult?>()
            pending += PendingRequest(request.page, deferred)
            return deferred.await()
        }

        fun completeLatest(page: Int, title: String, ids: List<String>) {
            pending.last { it.page == page && !it.deferred.isCompleted }
                .complete(title = title, ids = ids)
        }

        fun completeOldest(page: Int, title: String, ids: List<String>) {
            pending.first { it.page == page && !it.deferred.isCompleted }
                .complete(title = title, ids = ids)
        }

        private fun PendingRequest.complete(title: String, ids: List<String>) {
            deferred.complete(
                DetailLoadResult(
                    header = sheetHeader(title),
                    items = ids.map { music(it) },
                    isEnd = true,
                ),
            )
        }
    }

    private data class PendingRequest(
        val page: Int,
        val deferred: CompletableDeferred<DetailLoadResult?>,
    )

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
        const val NAMESPACE = "detail_session"
    }
}

private fun request(): DetailSessionRequest {
    val key = detailKey()
    return DetailSessionRequest(
        key = key,
        routeType = DetailRouteTypes.PLUGIN_SHEET,
        platform = "demo",
        itemId = "sheet-1",
        seed = sheetHeader("Demo Sheet"),
        fallbackTitle = "歌单详情",
    )
}

private fun detailKey(): String =
    RuntimeStoreKey.detail(DetailRouteTypes.PLUGIN_SHEET, "demo", "sheet-1").value

private fun sheetHeader(title: String): DetailSessionHeader.Sheet =
    DetailSessionHeader.Sheet(
        MusicSheetItemBase(
            id = "sheet-1",
            platform = "demo",
            title = title,
            artist = null,
            description = null,
            coverImg = null,
            artwork = null,
            worksNum = null,
            raw = mapOf("id" to "sheet-1", "platform" to "demo"),
        ),
    )

private fun musicItems(count: Int): List<MusicItem> =
    (1..count).map { music("song-$it") }

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
