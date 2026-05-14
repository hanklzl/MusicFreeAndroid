package com.zili.android.musicfreeandroid

import android.app.Application
import com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapper
import com.zili.android.musicfreeandroid.bootstrap.PlaybackStartupCoordinator
import com.zili.android.musicfreeandroid.bootstrap.PluginAutoUpdateCoordinator
import com.zili.android.musicfreeandroid.logging.LoggingConfig
import com.zili.android.musicfreeandroid.logging.LoggingInitializer
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.bootstrap.UpdateCheckCoordinator
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MusicFreeApplication : Application() {

    @Inject lateinit var defaultPluginsBootstrapper: DefaultPluginsBootstrapper
    @Inject lateinit var pluginAutoUpdateCoordinator: PluginAutoUpdateCoordinator
    @Inject lateinit var playbackStartupCoordinator: PlaybackStartupCoordinator
    @Inject lateinit var updateCheckCoordinator: UpdateCheckCoordinator

    override fun onCreate() {
        super.onCreate()

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

        defaultPluginsBootstrapper.start()
        pluginAutoUpdateCoordinator.start()
        playbackStartupCoordinator.start()
        updateCheckCoordinator.start()
    }
}
