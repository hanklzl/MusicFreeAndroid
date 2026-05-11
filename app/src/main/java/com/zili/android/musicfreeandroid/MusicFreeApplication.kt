package com.zili.android.musicfreeandroid

import android.app.Application
import com.zili.android.musicfreeandroid.bootstrap.DefaultPluginsBootstrapper
import com.zili.android.musicfreeandroid.logging.LoggingConfig
import com.zili.android.musicfreeandroid.logging.LoggingInitializer
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MusicFreeApplication : Application() {

    @Inject lateinit var defaultPluginsBootstrapper: DefaultPluginsBootstrapper

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

        defaultPluginsBootstrapper.start()
    }
}
