package com.hank.musicfree.feature.home.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSeedRuntimeStoreTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
        RouteSeedRuntimeStoreProvider.resetForTest()
    }

    @Test
    fun resolveCanBeCalledMultipleTimesBeforePrune() = runTest {
        val store = routeSeedStore(clock = FakeClock(now = 1_000), persistScope = this)
        val key = RuntimeStoreKey.routeSeed("album", "demo", "id-1").value

        store.put(key, payloadJson = """{"title":"Album A","raw":{"id":"id-1"}}""", ttlMs = 60_000)

        assertEquals("Album A", store.resolve(key)?.payload?.title())
        assertEquals("Album A", store.resolve(key)?.payload?.title())
    }

    @Test
    fun restoreSkipsExpiredSeeds() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        val key = RuntimeStoreKey.routeSeed("album", "demo", "id-1").value
        snapshotStore.write(
            routeSeedSnapshot(
                key = key,
                payloadJson = """{"title":"Album A"}""",
                updatedAtEpochMs = 1_000,
                expiresAtEpochMs = 1_500,
            ),
        )
        val store = routeSeedStore(
            snapshotStore = snapshotStore,
            clock = FakeClock(now = 2_000),
            persistScope = this,
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Skipped("empty_route_seeds"), result)
        assertNull(store.resolve(key))
        assertNull(snapshotStore.read(RouteSeedRuntimeStore.NAMESPACE, key))

        val event = logger.events.single { it.event == "route_seed_restore_skipped" }
        assertEquals("route_seed:batch", event.fields["key"])
        assertEquals("skipped", event.fields["result"])
    }

    @Test
    fun putPersistsSnapshotAndLogsStructuredFields() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        val key = RuntimeStoreKey.routeSeed("plugin_sheet", "demo", "sheet-1").value
        val store = routeSeedStore(
            snapshotStore = snapshotStore,
            clock = FakeClock(now = 1_000),
            persistScope = this,
        )

        store.put(key, payloadJson = """{"title":"Sheet A"}""", ttlMs = 60_000)
        advanceUntilIdle()

        val snapshot = snapshotStore.read(RouteSeedRuntimeStore.NAMESPACE, key)
        assertEquals("""{"title":"Sheet A"}""", snapshot?.payloadJson)
        assertEquals(61_000L, snapshot?.expiresAtEpochMs)

        val event = logger.events.single { it.event == "route_seed_persist_success" }
        assertEquals(LogCategory.RUNTIME, event.category)
        assertEquals("route_seed", event.fields["store"])
        assertEquals("route_seed_persist", event.fields["operation"])
        assertEquals(key, event.fields["key"])
        assertEquals("success", event.fields["result"])
        assertEquals(1, event.fields["count"])
        assertTrue(event.fields.containsKey("durationMs"))
    }

    @Test
    fun restoreLoadsNonExpiredSeedsAndLogsCount() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val snapshotStore = InMemorySnapshotStore()
        val key = RuntimeStoreKey.routeSeed("album", "demo", "id-1").value
        snapshotStore.write(
            routeSeedSnapshot(
                key = key,
                payloadJson = """{"title":"Album A"}""",
                updatedAtEpochMs = 1_000,
                expiresAtEpochMs = 60_000,
            ),
        )
        val store = routeSeedStore(
            snapshotStore = snapshotStore,
            clock = FakeClock(now = 2_000),
            persistScope = this,
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Restored, result)
        assertEquals("Album A", store.resolve(key)?.payload?.title())

        val event = logger.events.single { it.event == "route_seed_restore_success" }
        assertEquals(LogCategory.RUNTIME, event.category)
        assertEquals("route_seed", event.fields["store"])
        assertEquals("route_seed_restore", event.fields["operation"])
        assertEquals("route_seed:batch", event.fields["key"])
        assertEquals("success", event.fields["result"])
        assertEquals(1, event.fields["count"])
        assertEquals(0, event.fields["skippedCount"])
    }

    @Test
    fun olderAsyncPersistCannotOverwriteLatestEntryForSameKey() = runTest {
        val snapshotStore = InMemorySnapshotStore(
            writeDelayMs = { snapshot ->
                if (snapshot.payloadJson.contains("Album Old")) 100L else 0L
            },
        )
        val key = RuntimeStoreKey.routeSeed("album", "demo", "id-1").value
        val store = routeSeedStore(
            snapshotStore = snapshotStore,
            clock = FakeClock(now = 1_000),
            persistScope = this,
        )

        store.put(key, payloadJson = """{"title":"Album Old"}""", ttlMs = 60_000)
        store.put(key, payloadJson = """{"title":"Album New"}""", ttlMs = 60_000)
        advanceUntilIdle()

        val snapshot = snapshotStore.read(RouteSeedRuntimeStore.NAMESPACE, key)
        assertEquals("Album New", Json.parseToJsonElement(snapshot!!.payloadJson).title())
    }

    @Test
    fun pruneRemovesExpiredEntriesFromMemoryAndSnapshotStore() = runTest {
        val snapshotStore = InMemorySnapshotStore()
        val store = routeSeedStore(
            snapshotStore = snapshotStore,
            clock = FakeClock(now = 1_000),
            persistScope = this,
        )
        val key = RuntimeStoreKey.routeSeed("artist", "demo", "artist-1").value
        store.put(key, payloadJson = """{"name":"Artist A"}""", ttlMs = 500)
        advanceUntilIdle()

        store.prune(nowEpochMs = 2_000)

        assertNull(store.resolve(key))
        assertNull(snapshotStore.read(RouteSeedRuntimeStore.NAMESPACE, key))
    }

    private fun routeSeedStore(
        snapshotStore: InMemorySnapshotStore = InMemorySnapshotStore(),
        clock: FakeClock = FakeClock(now = 1_000),
        persistScope: CoroutineScope,
    ): RouteSeedRuntimeStore = RouteSeedRuntimeStore(
        snapshotStore = snapshotStore,
        json = Json { ignoreUnknownKeys = true },
        clock = clock,
        persistScope = persistScope,
    )

    private fun routeSeedSnapshot(
        key: String,
        payloadJson: String,
        updatedAtEpochMs: Long,
        expiresAtEpochMs: Long?,
    ): RuntimeSnapshot = RuntimeSnapshot(
        namespace = RouteSeedRuntimeStore.NAMESPACE,
        key = key,
        snapshotVersion = 1,
        sourceSignature = "route_seed:v1",
        createdAtEpochMs = updatedAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        payloadJson = payloadJson,
    )

    private fun kotlinx.serialization.json.JsonElement.title(): String? =
        jsonObject["title"]?.jsonPrimitive?.contentOrNull

    private class FakeClock(private val now: Long) : RouteSeedClock {
        override fun nowEpochMs(): Long = now
    }

    private class InMemorySnapshotStore(
        private val writeDelayMs: (RuntimeSnapshot) -> Long = { 0L },
    ) : SnapshotStore {
        private val snapshots = mutableMapOf<Pair<String, String>, RuntimeSnapshot>()

        override suspend fun read(namespace: String, key: String): RuntimeSnapshot? =
            snapshots[namespace to key]

        override suspend fun write(snapshot: RuntimeSnapshot) {
            val delayMs = writeDelayMs(snapshot)
            if (delayMs > 0L) {
                delay(delayMs)
            }
            snapshots[snapshot.namespace to snapshot.key] = snapshot
        }

        override suspend fun delete(namespace: String, key: String) {
            snapshots.remove(namespace to key)
        }

        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int {
            val expired = snapshots.values
                .filter { it.namespace == namespace && it.isExpired(nowEpochMs) }
                .map { it.namespace to it.key }
            expired.forEach { snapshots.remove(it) }
            return expired.size
        }

        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int {
            val candidates = snapshots.values
                .filter { it.namespace == namespace }
                .sortedWith(compareByDescending<RuntimeSnapshot> { it.updatedAtEpochMs }.thenBy { it.key })
            val prune = candidates.drop(keepLatest.coerceAtLeast(0))
            prune.forEach { snapshots.remove(it.namespace to it.key) }
            return prune.size
        }

        override suspend fun keys(namespace: String, limit: Int): List<String> =
            snapshots.values
                .filter { it.namespace == namespace }
                .sortedWith(compareByDescending<RuntimeSnapshot> { it.updatedAtEpochMs }.thenBy { it.key })
                .take(limit.coerceAtLeast(0))
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
}
