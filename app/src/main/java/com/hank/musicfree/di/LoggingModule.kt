package com.hank.musicfree.di

import android.content.Context
import com.hank.musicfree.BuildConfig
import com.hank.musicfree.logging.FeedbackLogExporter
import com.hank.musicfree.logging.FeedbackLogExporterContract
import com.hank.musicfree.logging.LoggingConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Provides
    @Singleton
    fun provideLoggingConfig(
        @ApplicationContext context: Context,
    ): LoggingConfig = LoggingConfig(
        cacheDir = context.filesDir.resolve("logan-cache"),
        logDir = context.filesDir.resolve("logan"),
        feedbackDir = File(context.cacheDir, "feedback"),
        feedbackShareRootDir = context.cacheDir,
        aesKey16 = BuildConfig.LOGAN_AES_KEY,
        aesIv16 = BuildConfig.LOGAN_AES_IV,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        applicationId = BuildConfig.APPLICATION_ID,
        buildType = BuildConfig.BUILD_TYPE,
    )

    @Provides
    @Singleton
    fun provideFeedbackLogExporter(
        loggingConfig: LoggingConfig,
    ): FeedbackLogExporterContract = FeedbackLogExporter(loggingConfig)
}
