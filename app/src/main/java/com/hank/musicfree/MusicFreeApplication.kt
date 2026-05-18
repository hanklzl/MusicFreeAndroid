package com.hank.musicfree

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.hank.musicfree.bootstrap.DefaultPluginsBootstrapper
import com.hank.musicfree.bootstrap.PlaybackStartupCoordinator
import com.hank.musicfree.bootstrap.PluginAutoUpdateCoordinator
import com.hank.musicfree.core.network.BaseOkHttp
import com.hank.musicfree.data.backup.StartupBackupRestore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.LoggingConfig
import com.hank.musicfree.logging.LoggingInitializer
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.engine.AxiosShim
import com.hank.musicfree.updater.bootstrap.UpdateCheckCoordinator
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltAndroidApp
class MusicFreeApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var defaultPluginsBootstrapper: DefaultPluginsBootstrapper
    @Inject lateinit var pluginAutoUpdateCoordinator: PluginAutoUpdateCoordinator
    @Inject lateinit var playbackStartupCoordinator: PlaybackStartupCoordinator
    @Inject lateinit var updateCheckCoordinator: UpdateCheckCoordinator
    @Inject lateinit var appPreferences: AppPreferences

    /**
     * `@BaseOkHttp` is the only OkHttp client whose [okhttp3.EventListener.Factory]
     * is wired to [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory].
     * Held here so [onCreate] can hand it to [AxiosShim.setBaseClient] — AxiosShim
     * is a Kotlin `object` and can't be field-injected, so it has a static
     * injection point that runs after Hilt finishes wiring.
     */
    @Inject @BaseOkHttp lateinit var baseOkHttpClient: OkHttpClient

    /**
     * 通过 [SingletonImageLoader.Factory.newImageLoader] 提供给 Coil 的全局
     * ImageLoader。其 HTTP fetcher 由 [com.hank.musicfree.core.coil.ImageLoaderModule]
     * 接到 `@BaseOkHttp` 派生 client,因此图片加载流量也会进入 traffic_daily。
     */
    @Inject lateinit var imageLoader: ImageLoader

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupBackupRestore.applyIfPending(this)
    }

    override fun onCreate() {
        super.onCreate()

        // AxiosShim is a Kotlin `object`; field-injecting `@BaseOkHttp` into a
        // singleton via Hilt isn't possible. Static-set the base client AFTER
        // `super.onCreate()` (which is when Hilt finishes injecting `@Inject`
        // fields on this Application) so subsequent plugin JS requests share the
        // NetworkTrafficEventListener.Factory and feed into `traffic_daily`.
        AxiosShim.setBaseClient(baseOkHttpClient)

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

        // Parity-audit agent 抓取通道,仅 Debug 构建且显式打开时启用。
        // 启用方式(adb): `adb shell setprop debug.parity_audit 1` 后冷启动应用,
        // 或在启动 Activity 时通过 `am start ... --es PARITY_AUDIT 1`(由调用方自定)。
        // Spec: docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md §4.2
        if (BuildConfig.DEBUG && System.getenv("PARITY_AUDIT") == "1") {
            MfLog.enableParitySink()
        }

        startLoggingPreferenceBridge()
        defaultPluginsBootstrapper.start()
        pluginAutoUpdateCoordinator.start()
        playbackStartupCoordinator.start()
        updateCheckCoordinator.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

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
