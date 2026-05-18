package com.hank.musicfree.core.runtime

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRuntimeStoreTest {
    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settersPersistSingleUiRuntimeSnapshotWithoutTtl() = runTest {
        val snapshotStore = InMemorySnapshotStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = UiRuntimeStore(
            snapshotStore = snapshotStore,
            dispatcher = dispatcher,
            clock = { 1_000L },
            debounceMs = 100L,
            scope = TestScope(dispatcher),
        )

        store.setHomeTab("Starred")
        store.setSearchTab("album")
        store.setPlayerView(PlayerView.LYRIC)
        advanceUntilIdle()

        assertEquals("ui_runtime", store.storeName)
        assertEquals(RuntimeStoreKey.singleton("ui_runtime").value, snapshotStore.lastWritten?.key)
        assertEquals("ui_runtime", snapshotStore.lastWritten?.namespace)
        assertNull(snapshotStore.lastWritten?.expiresAtEpochMs)
        assertEquals(1, snapshotStore.pruneNamespaceCalls.single().second)

        val restored = UiRuntimeStore.decodeSnapshotPayload(snapshotStore.lastWritten!!.payloadJson)
        assertEquals("Starred", restored.homeTab)
        assertEquals("album", restored.searchTab)
        assertEquals("LYRIC", restored.playerView)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun persistRoundTripRestoresTabsAndPlayerView() = runTest {
        val snapshotStore = InMemorySnapshotStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = UiRuntimeStore(
            snapshotStore = snapshotStore,
            dispatcher = dispatcher,
            clock = { 1_000L },
            debounceMs = 0L,
            scope = TestScope(dispatcher),
        )
        first.setHomeTab("Mine")
        first.setSearchTab("music")
        first.setPlayerView(PlayerView.LYRIC)
        advanceUntilIdle()

        val logger = RecordingLogger()
        MfLog.install(logger)
        val second = UiRuntimeStore(
            snapshotStore = snapshotStore,
            dispatcher = dispatcher,
            clock = { 2_000L },
            debounceMs = 0L,
            scope = TestScope(dispatcher),
        )

        val result = second.restore()

        assertEquals(RuntimeRestoreResult.Restored, result)
        assertEquals("Mine", second.state.value.homeTab)
        assertEquals("music", second.state.value.searchTab)
        assertEquals(PlayerView.LYRIC, second.state.value.playerView)
        assertTrue(second.state.value.restored)

        val event = logger.events.single { it.event == "ui_runtime_restore_success" }
        assertEquals("success", event.fields["result"])
        assertEquals("Mine", event.fields["homeTab"])
        assertEquals("music", event.fields["searchTab"])
        assertEquals("LYRIC", event.fields["playerView"])
    }

    @Test
    fun restoreEmptySnapshotReturnsSkippedAndLogsReason() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val store = UiRuntimeStore(
            snapshotStore = InMemorySnapshotStore(),
            debounceMs = 0L,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Skipped("empty_ui_snapshot"), result)
        val event = logger.events.single { it.event == "ui_runtime_restore_skipped" }
        assertEquals(LogCategory.RUNTIME, event.category)
        assertEquals("ui_runtime", event.fields["store"])
        assertEquals("ui_runtime:current", event.fields["key"])
        assertEquals("skipped", event.fields["result"])
        assertEquals("empty_ui_snapshot", event.fields["reason"])
        assertTrue(event.fields["durationMs"] is Long)
    }

    @Test
    fun restoreUnknownPlayerViewFallsBackToCoverAndLogsStale() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        snapshotStore.write(
            RuntimeSnapshot(
                namespace = "ui_runtime",
                key = "ui_runtime:current",
                snapshotVersion = 1,
                sourceSignature = "ui_runtime:v1",
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 2_000L,
                expiresAtEpochMs = null,
                payloadJson = """{"homeTab":"Mine","searchTab":"sheet","playerView":"DETAIL"}""",
            ),
        )
        val store = UiRuntimeStore(
            snapshotStore = snapshotStore,
            debounceMs = 0L,
            scope = TestScope(StandardTestDispatcher(testScheduler)),
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Stale("unknown_player_view"), result)
        assertEquals("Mine", store.state.value.homeTab)
        assertEquals("sheet", store.state.value.searchTab)
        assertEquals(PlayerView.COVER, store.state.value.playerView)
        assertTrue(store.state.value.restored)

        val event = logger.events.single { it.event == "ui_runtime_restore_stale" }
        assertEquals("stale", event.fields["result"])
        assertEquals("unknown_player_view", event.fields["reason"])
        assertEquals("Mine", event.fields["homeTab"])
        assertEquals("sheet", event.fields["searchTab"])
        assertEquals("COVER", event.fields["playerView"])
    }

    private class InMemorySnapshotStore : SnapshotStore {
        private val snapshots = LinkedHashMap<Pair<String, String>, RuntimeSnapshot>()
        var lastWritten: RuntimeSnapshot? = null
        val pruneNamespaceCalls = mutableListOf<Pair<String, Int>>()

        override suspend fun read(namespace: String, key: String): RuntimeSnapshot? =
            snapshots[namespace to key]

        override suspend fun write(snapshot: RuntimeSnapshot) {
            snapshots[snapshot.namespace to snapshot.key] = snapshot
            lastWritten = snapshot
        }

        override suspend fun delete(namespace: String, key: String) {
            snapshots.remove(namespace to key)
        }

        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int = 0

        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int {
            pruneNamespaceCalls += namespace to keepLatest
            return 0
        }

        override suspend fun keys(namespace: String, limit: Int): List<String> =
            snapshots.keys
                .asSequence()
                .filter { it.first == namespace }
                .map { it.second }
                .take(limit)
                .toList()
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
            events += RecordedLogEvent(category, event, fields)
        }

        override fun flush() = Unit
    }
}
