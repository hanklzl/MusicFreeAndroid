package com.zili.android.musicfreeandroid.logging

import android.util.Log
import com.dianping.logan.Logan
import com.dianping.logan.LoganConfig
import java.util.UUID

object LoggingInitializer {
    private const val TAG = "LoggingInitializer"
    @Volatile
    var currentSessionId: String = "unknown"
        private set

    fun initialize(config: LoggingConfig): String {
        val sessionId = UUID.randomUUID().toString()
        currentSessionId = sessionId
        return try {
            config.cacheDir.mkdirs()
            config.logDir.mkdirs()
            config.feedbackDir.mkdirs()
            ReadableLogStore.install(config.readableLogFile)

            Logan.init(
                LoganConfig.Builder()
                    .setCachePath(config.cacheDir.absolutePath)
                    .setPath(config.logDir.absolutePath)
                    .setEncryptKey16(config.aesKey16.toByteArray())
                    .setEncryptIV16(config.aesIv16.toByteArray())
                    .build(),
            )
            MfLog.install(LoganMfLogger(sessionId))
            installUncaughtExceptionHandler()
            LogPruner.prune(
                config.logDir,
                config.retentionDays,
                config.maxTotalBytes,
            )
            MfLog.trace(
                LogCategory.APP,
                "app_start",
                mapOf(
                    "versionName" to config.appVersionName,
                    "versionCode" to config.appVersionCode,
                    "applicationId" to config.applicationId,
                    "buildType" to config.buildType,
                ),
            )
            sessionId
        } catch (error: Throwable) {
            MfLog.resetForTest()
            Log.e(TAG, "Failed to initialize logging", error)
            sessionId
        }
    }

    private fun installUncaughtExceptionHandler() {
        val existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (existingHandler is LoggingUncaughtExceptionHandler) {
            return
        }
        Thread.setDefaultUncaughtExceptionHandler(
            LoggingUncaughtExceptionHandler(
                previousHandler = existingHandler,
            ),
        )
    }

    private class LoggingUncaughtExceptionHandler(
        private val previousHandler: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                MfLog.error(
                    LogCategory.APP,
                    "uncaught_exception",
                    throwable,
                    mapOf("thread" to thread.name),
                )
                MfLog.flush()
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
