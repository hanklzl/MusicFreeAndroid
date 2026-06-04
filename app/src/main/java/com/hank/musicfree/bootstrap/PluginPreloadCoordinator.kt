package com.hank.musicfree.bootstrap

import androidx.annotation.VisibleForTesting
import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.startup.StartupTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eagerly loads installed plugins at cold start, in the background, so playing a
 * queued / playlist track right after launch does not fail with `no_source`.
 *
 * Plugins are otherwise lazy-loaded — only when a plugin-backed screen (search,
 * detail) opens, or when the auto-update interval elapses. A cold-start play
 * before any of those triggers would hit an empty plugin set; the media-source
 * resolver also calls [PluginManager.ensurePluginsLoaded] as a fallback, but this
 * coordinator shrinks the window so the first play rarely has to wait for a load.
 *
 * Non-blocking by contract (startup harness rule
 * `#rule-startup-background-flows-nonblocking`): runs on the application scope +
 * IO dispatcher and never blocks the first frame.
 * [PluginManager.ensurePluginsLoaded] coalesces with any concurrent caller, so
 * this is cheap even when another consumer triggers loading first.
 */
@Singleton
class PluginPreloadCoordinator @Inject constructor(
    private val pluginManager: PluginManager,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun start() {
        applicationScope.launch(Dispatchers.IO) {
            preload()
        }
    }

    @VisibleForTesting
    internal suspend fun preload() = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val startupFlow = StartupTelemetry.startFlow("plugin_preload")
        try {
            pluginManager.ensurePluginsLoaded()
            StartupTelemetry.completeFlow(
                token = startupFlow,
                result = LogFields.Result.SUCCESS,
            )
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "plugin_preload_completed",
                fields = mapOf(
                    "operation" to "plugin_preload",
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (error: CancellationException) {
            StartupTelemetry.completeFlow(
                token = startupFlow,
                result = LogFields.Result.CANCELLED,
                reason = LogFields.Reason.CANCELLED,
            )
            throw error
        } catch (error: Throwable) {
            // Preload failure must not crash startup or cancel other background
            // flows; the resolver fallback will still trigger a load on first play.
            StartupTelemetry.completeFlow(
                token = startupFlow,
                result = LogFields.Result.FAILURE,
                reason = "exception",
            )
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_preload_failed",
                throwable = error,
                fields = mapOf(
                    "operation" to "plugin_preload",
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
        }
    }
}
