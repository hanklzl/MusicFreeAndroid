package com.hank.musicfree.bootstrap

import androidx.annotation.VisibleForTesting
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.startup.StartupTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginAutoUpdateCoordinator @Inject constructor(
    private val appPreferences: AppPreferences,
    private val pluginManager: PluginManager,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun start() {
        applicationScope.launch(Dispatchers.IO) {
            runIfDue(nowMs = System.currentTimeMillis())
        }
    }

    @VisibleForTesting
    internal suspend fun runIfDue(nowMs: Long) = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val startupFlow = StartupTelemetry.startFlow("plugin_auto_update")
        try {
            val enabled = appPreferences.autoUpdatePlugins.first()
            if (!enabled) {
                logSkipped(
                    reason = "disabled",
                    lastAtMs = appPreferences.pluginAutoUpdateLastAtEpochMs.first(),
                    nowMs = nowMs,
                    startedAt = startedAt,
                )
                StartupTelemetry.completeFlow(
                    token = startupFlow,
                    result = LogFields.Result.SKIPPED,
                    reason = "disabled",
                )
                return@withContext
            }

            val lastAtMs = appPreferences.pluginAutoUpdateLastAtEpochMs.first()
            val elapsedMs = nowMs - lastAtMs
            if (lastAtMs > 0L && elapsedMs in 0 until AUTO_UPDATE_INTERVAL_MS) {
                logSkipped(
                    reason = "interval_not_elapsed",
                    lastAtMs = lastAtMs,
                    nowMs = nowMs,
                    startedAt = startedAt,
                )
                StartupTelemetry.completeFlow(
                    token = startupFlow,
                    result = LogFields.Result.SKIPPED,
                    reason = "interval_not_elapsed",
                )
                return@withContext
            }

            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_auto_update_start",
                fields = mapOf(
                    "operation" to "auto_update_plugins",
                    "lastAtEpochMs" to lastAtMs,
                    "nowEpochMs" to nowMs,
                ),
            )

            pluginManager.ensurePluginsLoaded()
            val result = pluginManager.updateAllPlugins()
            appPreferences.setPluginAutoUpdateLastAtEpochMs(nowMs)

            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_auto_update_completed",
                fields = mapOf(
                    "operation" to "auto_update_plugins",
                    "targetCount" to result.targetPlugins.size,
                    "successCount" to result.successCount,
                    "failureCount" to result.failureCount,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "result" to if (result.failureCount == 0) {
                        LogFields.Result.SUCCESS
                    } else {
                        LogFields.Result.FAILURE
                    },
                ),
            )
            val startupResult = if (result.failureCount == 0) {
                LogFields.Result.SUCCESS
            } else {
                LogFields.Result.FAILURE
            }
            StartupTelemetry.completeFlow(
                token = startupFlow,
                result = startupResult,
                reason = if (startupResult == LogFields.Result.SUCCESS) null else "partial_failure",
                extraFields = mapOf(
                    "targetCount" to result.targetPlugins.size,
                    "successCount" to result.successCount,
                    "failureCount" to result.failureCount,
                ),
            )
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_auto_update_completed",
                fields = mapOf(
                    "operation" to "auto_update_plugins",
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            StartupTelemetry.completeFlow(
                token = startupFlow,
                result = LogFields.Result.CANCELLED,
                reason = LogFields.Reason.CANCELLED,
            )
            throw error
        } catch (error: Throwable) {
            runCatching { appPreferences.setPluginAutoUpdateLastAtEpochMs(nowMs) }
            StartupTelemetry.completeFlow(
                token = startupFlow,
                result = LogFields.Result.FAILURE,
                reason = "exception",
            )
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_auto_update_failed",
                throwable = error,
                fields = mapOf(
                    "operation" to "auto_update_plugins",
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
        }
    }

    private fun logSkipped(
        reason: String,
        lastAtMs: Long,
        nowMs: Long,
        startedAt: Long,
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_auto_update_skipped",
            fields = mapOf(
                "operation" to "auto_update_plugins",
                "reason" to reason,
                "lastAtEpochMs" to lastAtMs,
                "nowEpochMs" to nowMs,
                "durationMs" to (System.currentTimeMillis() - startedAt),
                "result" to LogFields.Result.SKIPPED,
            ),
        )
    }

    private companion object {
        const val AUTO_UPDATE_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L
    }
}
