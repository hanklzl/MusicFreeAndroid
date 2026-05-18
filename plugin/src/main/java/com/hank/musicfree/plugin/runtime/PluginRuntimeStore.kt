package com.hank.musicfree.plugin.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.repository.CachedPluginMetadata
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PluginRuntimeEntry(
    val platform: String,
    val version: String?,
    val filePath: String,
    val loaded: Boolean,
    val failedReason: String?,
)

data class PluginRuntimeState(
    val plugins: List<PluginRuntimeEntry> = emptyList(),
    val restoring: Boolean = false,
    val lastFailureReason: String? = null,
)

@Singleton
class PluginRuntimeStore @Inject constructor(
    private val metadataCacheGateway: PluginMetadataCacheGateway,
    private val snapshotStore: SnapshotStore,
) : RuntimeStore<PluginRuntimeState> {
    override val storeName: String = STORE_NAME
    private val _state = MutableStateFlow(PluginRuntimeState())
    override val state: StateFlow<PluginRuntimeState> = _state.asStateFlow()
    private val key = RuntimeStoreKey.singleton(storeName).value

    override suspend fun restore(): RuntimeRestoreResult {
        val startedAt = System.nanoTime()
        _state.update { it.copy(restoring = true, lastFailureReason = null) }

        return try {
            val rows = metadataCacheGateway.getAll()
            if (rows.isEmpty()) {
                _state.value = PluginRuntimeState(
                    plugins = emptyList(),
                    restoring = false,
                    lastFailureReason = null,
                )
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_runtime_restore_skipped",
                    fields = baseLogFields(
                        rows = rows,
                        result = LogFields.Result.SKIPPED,
                        reason = "empty_plugin_metadata",
                        durationMs = elapsedMs(startedAt),
                    ),
                )
                return RuntimeRestoreResult.Skipped("empty_plugin_metadata")
            }

            val entries = rows.map { rowToEntry(it) }
            _state.value = PluginRuntimeState(
                plugins = entries,
                restoring = false,
                lastFailureReason = null,
            )
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_runtime_restore_success",
                fields = baseLogFields(
                    rows = rows,
                    result = LogFields.Result.SUCCESS,
                    reason = null,
                    durationMs = elapsedMs(startedAt),
                ),
            )
            RuntimeRestoreResult.Restored
        } catch (error: CancellationException) {
            _state.update {
                it.copy(restoring = false, lastFailureReason = null)
            }
            throw error
        } catch (error: Throwable) {
            val reason = "metadata_restore_failed"
            _state.value = PluginRuntimeState(
                plugins = emptyList(),
                restoring = false,
                lastFailureReason = reason,
            )
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_runtime_restore_failed",
                throwable = error,
                fields = baseLogFields(
                    rows = emptyList(),
                    result = LogFields.Result.FAILURE,
                    reason = reason,
                    durationMs = elapsedMs(startedAt),
                ),
            )
            RuntimeRestoreResult.Failed(reason, error)
        }
    }

    override suspend fun persist() = Unit

    override suspend fun prune(nowEpochMs: Long) {
        snapshotStore.deleteExpired(storeName, nowEpochMs)
    }

    private fun rowToEntry(row: CachedPluginMetadata): PluginRuntimeEntry =
        PluginRuntimeEntry(
            platform = row.platform,
            version = row.version,
            filePath = row.filePath,
            loaded = false,
            failedReason = null,
        )

    private fun baseLogFields(
        rows: List<CachedPluginMetadata>,
        result: String,
        reason: String?,
        durationMs: Long,
    ): Map<String, Any?> = buildMap {
        put("store", STORE_NAME)
        put("key", key)
        put("operation", "plugin_runtime_restore")
        put("result", result)
        put("count", rows.size)
        if (reason != null) {
            put("reason", reason)
        }
        put("durationMs", durationMs)
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val STORE_NAME = "plugin_runtime"
    }
}
