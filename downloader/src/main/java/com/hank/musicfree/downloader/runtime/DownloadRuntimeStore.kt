package com.hank.musicfree.downloader.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.downloader.engine.DownloadEngine
import com.hank.musicfree.downloader.model.DownloadStatus.DOWNLOADING
import com.hank.musicfree.downloader.model.DownloadStatus.FAILED
import com.hank.musicfree.downloader.model.DownloadStatus.PREPARING
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadRuntimeState(
    val taskIds: List<String> = emptyList(),
    val activeCount: Int = 0,
    val failedCount: Int = 0,
    val restoring: Boolean = false,
    val lastFailureReason: String? = null,
)

@Singleton
class DownloadRuntimeStore @Inject constructor(
    private val engine: DownloadEngine,
) : RuntimeStore<DownloadRuntimeState> {
    override val storeName: String = STORE_NAME
    private val key = RuntimeStoreKey.singleton(storeName).value
    private val _state = MutableStateFlow(DownloadRuntimeState())
    override val state: StateFlow<DownloadRuntimeState> = _state.asStateFlow()

    override suspend fun restore(): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        _state.value = _state.value.copy(restoring = true, lastFailureReason = null)

        return try {
            val tasks = engine.taskSnapshot()
            if (tasks.isEmpty()) {
                _state.value = DownloadRuntimeState(restoring = false)
                MfLog.detail(
                    category = LogCategory.DOWNLOAD,
                    event = "download_runtime_restore_skipped",
                    fields = mapOf(
                        "store" to STORE_NAME,
                        "operation" to OPERATION_RESTORE,
                        "key" to key,
                        "count" to 0,
                        "activeCount" to 0,
                        "failedCount" to 0,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.SKIPPED,
                        "reason" to "empty_download_tasks",
                    ),
                )
                return RuntimeRestoreResult.Skipped("empty_download_tasks")
            }

            val activeCount = tasks.count { it.status == PREPARING || it.status == DOWNLOADING }
            val failedCount = tasks.count { it.status == FAILED }

            _state.value = DownloadRuntimeState(
                taskIds = tasks.map { it.key.value },
                activeCount = activeCount,
                failedCount = failedCount,
                restoring = false,
            )

            MfLog.detail(
                category = LogCategory.DOWNLOAD,
                event = "download_runtime_restore_success",
                fields = mapOf(
                    "store" to STORE_NAME,
                    "operation" to OPERATION_RESTORE,
                    "key" to key,
                    "count" to tasks.size,
                    "activeCount" to activeCount,
                    "failedCount" to failedCount,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            RuntimeRestoreResult.Restored
        } catch (error: CancellationException) {
            _state.value = _state.value.copy(restoring = false, lastFailureReason = null)
            throw error
        } catch (error: Throwable) {
            val reason = "exception"
            _state.value = DownloadRuntimeState(
                taskIds = emptyList(),
                activeCount = 0,
                failedCount = 0,
                restoring = false,
                lastFailureReason = reason,
            )
            MfLog.error(
                category = LogCategory.DOWNLOAD,
                event = "download_runtime_restore_failed",
                throwable = error,
                fields = mapOf(
                    "store" to STORE_NAME,
                    "operation" to OPERATION_RESTORE,
                    "key" to key,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to reason,
                ),
            )
            RuntimeRestoreResult.Failed(reason, error)
        }
    }

    override suspend fun persist() = Unit

    override suspend fun prune(nowEpochMs: Long) = Unit

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val STORE_NAME = "download_runtime"
        const val OPERATION_RESTORE = "runtime_restore"
    }
}
