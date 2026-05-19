package com.hank.musicfree.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.startup.StartupTelemetry
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeRestoreCoordinatorTest {
    @After
    fun tearDown() {
        StartupTelemetry.resetForTest()
        MfLog.resetForTest()
    }

    @Test
    fun runtimeRestoreLogsAggregateStartupFlow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = RecordingLogger()
        MfLog.install(logger)
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { testScheduler.currentTime * 1_000_000L },
            idProvider = { "startup-id" },
        )
        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()
        StartupTelemetry.markLoggingReady()
        val playback = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
        val plugin = FakeRuntimeStore("plugin", RuntimeRestoreResult.Skipped("empty"))
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(playback, plugin)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()
        runCurrent()

        val start = logger.events.single { it.event == "startup_flow_start" }
        val complete = logger.events.single { it.event == "startup_flow_complete" }
        assertEquals(LogCategory.APP, start.category)
        assertEquals(LogCategory.APP, complete.category)
        assertEquals("runtime_restore", start.fields["flowName"])
        assertEquals("runtime_restore", complete.fields["flowName"])
        assertEquals(LogFields.Result.SUCCESS, complete.fields["result"])
        assertEquals(2, complete.fields["targetCount"])
        assertNotNull(complete.fields["durationMs"])
    }

    @Test
    fun startReturnsImmediatelyAndRestoresStoresInApplicationScope() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fast = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
        val failed = FakeRuntimeStore("plugin", RuntimeRestoreResult.Failed("boom"))
        val slow = FakeRuntimeStore("download", RuntimeRestoreResult.Skipped("empty"))
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(fast, failed, slow)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()

        assertEquals(0, fast.restoreCount)
        assertEquals(0, failed.restoreCount)
        assertEquals(0, slow.restoreCount)
        runCurrent()
        assertEquals(1, fast.restoreCount)
        assertEquals(1, failed.restoreCount)
        assertEquals(1, slow.restoreCount)
    }

    @Test
    fun startSkipsLazyStoresButStillPrunesThem() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val startupStore = FakeRuntimeStore("ui_runtime", RuntimeRestoreResult.Restored)
        val lazySearch = FakeRuntimeStore(
            "search_session",
            RuntimeRestoreResult.Restored,
            restoreOnStartup = false,
        )
        val lazyDetail = FakeRuntimeStore(
            "detail_session",
            RuntimeRestoreResult.Restored,
            restoreOnStartup = false,
        )
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(startupStore, lazySearch, lazyDetail)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()
        runCurrent()

        assertEquals(1, startupStore.restoreCount)
        assertEquals(0, lazySearch.restoreCount)
        assertEquals(0, lazyDetail.restoreCount)

        advanceTimeBy(6L * 60 * 60 * 1000)
        runCurrent()

        assertEquals(1, startupStore.pruneCount)
        assertEquals(1, lazySearch.pruneCount)
        assertEquals(1, lazyDetail.pruneCount)
    }

    @Test
    fun startCalledTwiceOnlyRestoresAndCleansUpOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(store)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()
        coordinator.start()
        runCurrent()

        assertEquals(1, store.restoreCount)

        advanceTimeBy(6L * 60 * 60 * 1000)
        runCurrent()
        assertEquals(1, store.pruneCount)
    }

    @Test
    fun restoreFailedResultLogsErrorWithEmbeddedThrowable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = RecordingLogger()
        val failure = RuntimeException("boom")
        val store = FakeRuntimeStore(
            "plugin",
            RuntimeRestoreResult.Failed("deserialize", failure),
        )
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(store)),
            workerDispatcher = dispatcher,
        )
        MfLog.install(logger)

        coordinator.start()
        runCurrent()

        val event = logger.events.single { it.event == "runtime_restore_failed" && it.level == "error" }
        assertEquals(LogCategory.RUNTIME, event.category)
        assertEquals(failure, event.throwable)
        assertEquals("plugin", event.fields["store"])
        assertEquals("plugin:startup", event.fields["key"])
        assertEquals(LogFields.Result.FAILURE, event.fields["result"])
        assertEquals("deserialize", event.fields["reason"])
    }

    @Test
    fun restoreFailureIsolationDoesNotBlockOtherStores() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val restored = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
        val failed = FakeRuntimeStore("plugin", RuntimeRestoreResult.Failed("skip"))
        val throwing = FakeRuntimeStore("download", RuntimeRestoreResult.Restored) {
            throw IllegalStateException("explode")
        }
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(restored, failed, throwing)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()

        runCurrent()
        assertEquals(1, restored.restoreCount)
        assertEquals(1, failed.restoreCount)
        assertEquals(1, throwing.restoreCount)
    }

    @Test
    fun coordinatorRunsPrunePeriodicallyAfterRestore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeRuntimeStore("search_session", RuntimeRestoreResult.Restored)
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(store)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()
        runCurrent()
        advanceTimeBy(6L * 60 * 60 * 1000)
        runCurrent()

        assertEquals(1, store.pruneCount)
    }

    @Test
    fun startRunsCleanupEvenIfOneStoreRestoreHangs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val restoreHang = CompletableDeferred<Unit>()
        val fastStore = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
        val hangingStore = FakeRuntimeStore(
            "plugin",
            RuntimeRestoreResult.Restored,
            restoreAction = { restoreHang.await() },
        )
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(fastStore, hangingStore)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()
        runCurrent()
        assertEquals(1, fastStore.restoreCount)
        assertEquals(1, hangingStore.restoreCount)

        advanceTimeBy(6L * 60 * 60 * 1000)
        runCurrent()
        assertEquals(1, fastStore.pruneCount)
        assertEquals(1, hangingStore.pruneCount)

        restoreHang.complete(Unit)
        runCurrent()
    }

    @Test
    fun slowPruneDoesNotBlockAnotherStorePrune() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val slowPruneBlocker = CompletableDeferred<Unit>()
        val slowPruneRelease = CompletableDeferred<Unit>()
        val fastStore = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
        val slowStore = FakeRuntimeStore(
            "plugin",
            RuntimeRestoreResult.Restored,
            pruneAction = {
                slowPruneBlocker.complete(Unit)
                slowPruneRelease.await()
            },
        )
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(fastStore, slowStore)),
            workerDispatcher = dispatcher,
        )

        coordinator.start()
        runCurrent()

        advanceTimeBy(6L * 60 * 60 * 1000)
        runCurrent()

        assertEquals(1, fastStore.pruneCount)
        assertEquals(0, slowStore.pruneCount)
        assertEquals(Unit, slowPruneBlocker.await())

        slowPruneRelease.complete(Unit)
        runCurrent()
    }

    @Test
    fun restoreCancellationLogsRuntimeRestoreCancelled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = RecordingLogger()
        val store = FakeRuntimeStore(
            "plugin",
            RuntimeRestoreResult.Restored,
            restoreAction = {
                throw CancellationException("cancel restore")
            },
        )
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(store)),
            workerDispatcher = dispatcher,
        )
        MfLog.install(logger)

        coordinator.start()
        runCurrent()

        val event = logger.events.single { it.event == "runtime_restore_cancelled" && it.level == "detail" }
        assertEquals(LogCategory.RUNTIME, event.category)
        assertEquals("plugin", event.fields["store"])
        assertEquals("plugin:startup", event.fields["key"])
        assertEquals(LogFields.Result.CANCELLED, event.fields["result"])
        assertEquals("cancelled", event.fields["reason"])
    }

    @Test
    fun pruneCancellationLogsRuntimeSnapshotPruneCancelled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = RecordingLogger()
        val store = FakeRuntimeStore(
            "search_session",
            RuntimeRestoreResult.Restored,
            pruneAction = {
                throw CancellationException("cancel prune")
            },
        )
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(store)),
            workerDispatcher = dispatcher,
        )
        MfLog.install(logger)

        coordinator.start()
        runCurrent()
        advanceTimeBy(6L * 60 * 60 * 1000)
        runCurrent()
        advanceUntilIdle()

        val event = logger.events.single { it.event == "runtime_snapshot_prune_cancelled" && it.level == "detail" }
        assertEquals(LogCategory.RUNTIME, event.category)
        assertEquals("runtime_snapshot_prune", event.fields["operation"])
        assertEquals("search_session", event.fields["store"])
        assertEquals("search_session:snapshot-prune", event.fields["key"])
        assertEquals(LogFields.Result.CANCELLED, event.fields["result"])
        assertEquals("cancelled", event.fields["reason"])
    }

    @Test
    fun periodicPruneFailureIncludesDetailedFields() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = RecordingLogger()
        val pruneFailure = RuntimeException("bad")
        val store = FakeRuntimeStore(
            "search_session",
            RuntimeRestoreResult.Restored,
            pruneAction = { throw pruneFailure },
        )
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = backgroundScope,
            registry = RuntimeStoreRegistry(setOf(store)),
            workerDispatcher = dispatcher,
        )
        MfLog.install(logger)

        coordinator.start()
        runCurrent()
        advanceTimeBy(6L * 60 * 60 * 1000)
        runCurrent()

        val event = logger.events.single { it.event == "runtime_snapshot_prune_failed" && it.level == "error" }
        assertEquals(LogCategory.RUNTIME, event.category)
        assertEquals("runtime_snapshot_prune", event.fields["operation"])
        assertEquals("search_session", event.fields["store"])
        assertEquals("search_session:snapshot-prune", event.fields["key"])
        assertEquals(LogFields.Result.FAILURE, event.fields["result"])
        assertEquals("exception", event.fields["reason"])
        assertEquals(RuntimeException::class.java, event.throwable?.javaClass)
        assertEquals("bad", event.throwable?.message)
        assertNotNull(event.fields["durationMs"])
    }

    private class FakeRuntimeStore(
        override val storeName: String,
        private val result: RuntimeRestoreResult,
        override val restoreOnStartup: Boolean = true,
        private val restoreAction: (suspend () -> Unit)? = null,
        private val pruneAction: (suspend () -> Unit)? = null,
    ) : RuntimeStore<Unit> {
        override val state = MutableStateFlow(Unit)
        var restoreCount = 0
        var pruneCount = 0

        override suspend fun restore(): RuntimeRestoreResult {
            restoreCount += 1
            restoreAction?.invoke()
            return result
        }

        override suspend fun persist() = Unit

        override suspend fun prune(nowEpochMs: Long) {
            pruneAction?.invoke()
            pruneCount += 1
        }
    }

    private data class RecordedLogEvent(
        val level: String,
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
        val throwable: Throwable? = null,
    )

    private class RecordingLogger : MfLogger {
        val events = CopyOnWriteArrayList<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent("trace", category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent("detail", category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent("error", category, event, fields, throwable)
        }

        override fun flush() = Unit
    }
}
