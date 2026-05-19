package com.hank.musicfree

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.hank.musicfree.bootstrap.DefaultPluginsBootstrapper
import com.hank.musicfree.bootstrap.PlaybackStartupCoordinator
import com.hank.musicfree.bootstrap.PluginAutoUpdateCoordinator
import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.data.backup.StartupBackupRestore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PinnedKeysProvider
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.LoggingConfig
import com.hank.musicfree.player.cache.SimpleCacheHolder
import com.hank.musicfree.player.prefetch.PrefetchCoordinator
import com.hank.musicfree.logging.LoggingInitializer
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.engine.AxiosShim
import com.hank.musicfree.runtime.RuntimeRestoreCoordinator
import com.hank.musicfree.startup.StartupTelemetry
import com.hank.musicfree.updater.bootstrap.UpdateCheckCoordinator
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class MusicFreeApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var defaultPluginsBootstrapper: DefaultPluginsBootstrapper
    @Inject lateinit var pluginAutoUpdateCoordinator: PluginAutoUpdateCoordinator
    @Inject lateinit var playbackStartupCoordinator: PlaybackStartupCoordinator
    @Inject lateinit var updateCheckCoordinator: UpdateCheckCoordinator
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var runtimeRestoreCoordinator: RuntimeRestoreCoordinator
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    /**
     * `@BaseOkHttp` is the only OkHttp client whose [okhttp3.EventListener.Factory]
     * is wired to [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory].
     * Held here so [onCreate] can hand it to [AxiosShim.setBaseClient] — AxiosShim
     * is a Kotlin `object` and can't be field-injected, so it has a static
     * injection point that runs after Hilt finishes wiring.
     */
    @Inject @BaseOkHttp lateinit var baseOkHttpClient: OkHttpClient

    @Inject lateinit var simpleCacheHolder: SimpleCacheHolder
    @Inject lateinit var pinnedKeysProvider: PinnedKeysProvider
    @Inject lateinit var prefetchCoordinator: PrefetchCoordinator

    /**
     * 通过 [SingletonImageLoader.Factory.newImageLoader] 提供给 Coil 的全局
     * ImageLoader。其 HTTP fetcher 由 [com.hank.musicfree.core.coil.ImageLoaderModule]
     * 接到 `@BaseOkHttp` 派生 client,因此图片加载流量也会进入 traffic_daily。
     */
    @Inject lateinit var imageLoader: ImageLoader

    override fun attachBaseContext(base: Context) {
        StartupTelemetry.attachBaseContextStart()
        super.attachBaseContext(base)
        val pendingRestore = StartupTelemetry.startApplicationPhase("pending_restore")
        StartupBackupRestore.applyIfPending(this)
        StartupTelemetry.completeApplicationPhase(
            event = "app_startup_pending_restore_complete",
            phase = "pending_restore",
            span = pendingRestore,
            result = LogFields.Result.SUCCESS,
        )
    }

    override fun onCreate() {
        val applicationStartup = StartupTelemetry.applicationOnCreateStart()
        super.onCreate()

        // AxiosShim is a Kotlin `object`; field-injecting `@BaseOkHttp` into a
        // singleton via Hilt isn't possible. Static-set the base client AFTER
        // `super.onCreate()` (which is when Hilt finishes injecting `@Inject`
        // fields on this Application) so subsequent plugin JS requests share the
        // NetworkTrafficEventListener.Factory and feed into `traffic_daily`.
        AxiosShim.setBaseClient(baseOkHttpClient)

        val loggingStartup = StartupTelemetry.startApplicationPhase("logging_initialized")
        LoggingInitializer.initialize(
            LoggingConfig(
                cacheDir = File(filesDir, "logan-cache"),
                logDir = File(filesDir, "logan"),
                feedbackDir = File(cacheDir, "feedback"),
                feedbackShareRootDir = cacheDir,
                aesKey16 = BuildConfig.LOGAN_AES_KEY,
                aesIv16 = BuildConfig.LOGAN_AES_IV,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                applicationId = BuildConfig.APPLICATION_ID,
                buildType = BuildConfig.BUILD_TYPE,
            ),
        )
        StartupTelemetry.markLoggingReady()
        StartupTelemetry.completeApplicationPhase(
            event = "app_startup_logging_initialized",
            phase = "logging_initialized",
            span = loggingStartup,
            result = LogFields.Result.SUCCESS,
        )

        // Parity-audit agent 抓取通道,仅 Debug 构建且显式打开时启用。
        // 启用方式(adb): `adb shell setprop debug.parity_audit 1` 后冷启动应用,
        // 或在启动 Activity 时通过 `am start ... --es PARITY_AUDIT 1`(由调用方自定)。
        // Spec: docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md §4.2
        if (BuildConfig.DEBUG && System.getenv("PARITY_AUDIT") == "1") {
            MfLog.enableParitySink()
        }

        simpleCacheHolder.migrateOnceIfNeeded()
        startCacheCapacityBridge()
        startPinnedKeysBridge()
        startLoggingPreferenceBridge()
        val preferenceBridgeFlow = StartupTelemetry.startFlow("logging_preference_bridge")
        StartupTelemetry.completeFlow(
            token = preferenceBridgeFlow,
            result = LogFields.Result.SUCCESS,
            reason = "scheduled",
        )
        prefetchCoordinator.start()
        runtimeRestoreCoordinator.start()
        defaultPluginsBootstrapper.start()
        pluginAutoUpdateCoordinator.start()
        playbackStartupCoordinator.start()
        val playbackObserversFlow = StartupTelemetry.startFlow("playback_startup_observers")
        StartupTelemetry.completeFlow(
            token = playbackObserversFlow,
            result = LogFields.Result.SUCCESS,
            reason = "scheduled",
        )
        updateCheckCoordinator.start(
            startupFields = StartupTelemetry.startupContextFields(flowName = "update_check_on_launch"),
        )
        StartupTelemetry.completeApplicationStartup(
            result = LogFields.Result.SUCCESS,
            extraFields = mapOf(
                "applicationOnCreateDurationMs" to
                    ((System.nanoTime() - applicationStartup.startedAtNano) / 1_000_000L).coerceAtLeast(0L),
            ),
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    /**
     * Watches [AppPreferences.maxMusicCacheSizeBytes] and forwards changes to
     * [SimpleCacheHolder.updateMaxBytes] so the SimpleCache LRU budget stays in sync
     * with the user-configured limit without requiring an app restart.
     */
    private fun startCacheCapacityBridge() {
        applicationScope.launch {
            appPreferences.maxMusicCacheSizeBytes.collect { newBytes ->
                simpleCacheHolder.updateMaxBytes(newBytes)
            }
        }
    }

    /**
     * Watches [PinnedKeysProvider.observe] and forwards key sets to
     * [SimpleCacheHolder.updatePinned] so starred sheets and recently-queued
     * tracks are protected from LRU cache eviction.
     */
    private fun startPinnedKeysBridge() {
        applicationScope.launch {
            pinnedKeysProvider.observe().collect { keys ->
                simpleCacheHolder.updatePinned(keys)
            }
        }
    }

    private fun startLoggingPreferenceBridge() {
        applicationScope.launch {
            combine(
                appPreferences.debugErrorLogEnabled,
                appPreferences.debugTraceLogEnabled,
                appPreferences.debugDevLogEnabled,
            ) { errorEnabled, traceEnabled, devEnabled ->
                MfLog.LogSwitches(
                    errorEnabled = errorEnabled,
                    detailEnabled = traceEnabled,
                    devEnabled = devEnabled,
                )
            }.collect { switches ->
                MfLog.configure(switches)
            }
        }
    }
}
