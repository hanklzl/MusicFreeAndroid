package com.hank.musicfree.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.LogFields.Result
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.RuntimeLogFields
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class RuntimeRestoreCoordinator internal constructor(
    private val applicationScope: CoroutineScope,
    private val registry: RuntimeStoreRegistry,
    private val workerDispatcher: CoroutineDispatcher,
) {
    private val started = AtomicBoolean(false)

    @Inject
    constructor(
        @ApplicationScope applicationScope: CoroutineScope,
        registry: RuntimeStoreRegistry,
    ) : this(applicationScope, registry, Dispatchers.IO)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch {
            launch {
                restoreAll()
            }
            launch {
                schedulePeriodicCleanup()
            }
        }
    }

    private suspend fun restoreAll() {
        supervisorScope {
            registry.stores
                .filter { it.restoreOnStartup }
                .forEach { store ->
                    launch {
                        restoreOne(store)
                    }
                }
        }
    }

    private suspend fun restoreOne(store: RuntimeStore<*>) {
        val startedAt = System.nanoTime()
        val key = "${store.storeName}:startup"
        MfLog.detail(
            category = LogCategory.RUNTIME,
            event = "runtime_restore_start",
            fields = RuntimeLogFields.restoreStart(store.storeName, key),
        )
        try {
            val result = withContext(workerDispatcher) {
                store.restore()
            }
            val (resultValue, reason) = result.toLogResult()
            val event = when (result) {
                RuntimeRestoreResult.Restored -> "runtime_restore_success"
                is RuntimeRestoreResult.Skipped -> "runtime_restore_skipped"
                is RuntimeRestoreResult.Stale -> "runtime_restore_stale"
                is RuntimeRestoreResult.Failed -> "runtime_restore_failed"
            }
            val terminalFields = RuntimeLogFields.restoreTerminal(
                store = store.storeName,
                key = key,
                result = resultValue,
                durationMs = elapsedMs(startedAt),
                reason = reason,
            )
            when (result) {
                is RuntimeRestoreResult.Failed -> {
                    MfLog.error(
                        category = LogCategory.RUNTIME,
                        event = event,
                        throwable = result.error,
                        fields = terminalFields,
                    )
                }
                else -> {
                    MfLog.detail(
                        category = LogCategory.RUNTIME,
                        event = event,
                        fields = terminalFields,
                    )
                }
            }
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.RUNTIME,
                event = "runtime_restore_cancelled",
                fields = RuntimeLogFields.restoreTerminal(
                    store = store.storeName,
                    key = key,
                    result = Result.CANCELLED,
                    durationMs = elapsedMs(startedAt),
                    reason = LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.RUNTIME,
                event = "runtime_restore_failed",
                throwable = error,
                fields = RuntimeLogFields.restoreTerminal(
                    store = store.storeName,
                    key = key,
                    result = LogFields.Result.FAILURE,
                    durationMs = elapsedMs(startedAt),
                    reason = "exception",
                ),
            )
        }
    }

    private suspend fun schedulePeriodicCleanup() {
        val operation = "runtime_snapshot_prune"
        while (true) {
            delay(CLEANUP_INTERVAL_MS)
            val now = System.currentTimeMillis()
            supervisorScope {
                registry.stores.forEach { store ->
                    launch {
                        pruneOne(store, operation, now)
                    }
                }
            }
        }
    }

    private suspend fun pruneOne(store: RuntimeStore<*>, operation: String, now: Long) {
        val startedAt = System.nanoTime()
        val key = "${store.storeName}:snapshot-prune"
        try {
            withContext(workerDispatcher) {
                store.prune(now)
            }
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.RUNTIME,
                event = "runtime_snapshot_prune_cancelled",
                fields = mapOf(
                    LogFields.operation(operation).first to operation,
                    "store" to store.storeName,
                    "key" to key,
                    LogFields.result(Result.CANCELLED).first to Result.CANCELLED,
                    "durationMs" to elapsedMs(startedAt),
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.RUNTIME,
                event = "runtime_snapshot_prune_failed",
                throwable = error,
                fields = mapOf(
                    LogFields.operation(operation).first to operation,
                    "store" to store.storeName,
                    "key" to key,
                    LogFields.result(Result.FAILURE).first to Result.FAILURE,
                    "durationMs" to elapsedMs(startedAt),
                    "reason" to "exception",
                ),
            )
        }
    }

    private fun RuntimeRestoreResult.toLogResult(): Pair<String, String?> = when (this) {
        RuntimeRestoreResult.Restored -> LogFields.Result.SUCCESS to null
        is RuntimeRestoreResult.Skipped -> LogFields.Result.SKIPPED to reason
        is RuntimeRestoreResult.Stale -> LogFields.Result.STALE to reason
        is RuntimeRestoreResult.Failed -> LogFields.Result.FAILURE to reason
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val CLEANUP_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
