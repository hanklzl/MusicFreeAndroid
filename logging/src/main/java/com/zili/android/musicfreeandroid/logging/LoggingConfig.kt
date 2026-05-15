package com.zili.android.musicfreeandroid.logging

import java.io.File

data class LoggingConfig(
    /** Logan's own temporary cache directory, passed to LoganConfig.setCachePath. */
    val cacheDir: File,
    val logDir: File,
    val readableLogFile: File = File(logDir, "readable-errors.log"),
    val feedbackDir: File,
    /** Root matched by app/src/main/res/xml/feedback_file_paths.xml cache-path. */
    val feedbackShareRootDir: File = cacheDir,
    val aesKey16: String,
    val aesIv16: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val applicationId: String,
    val buildType: String,
    val retentionDays: Int = 7,
    val maxTotalBytes: Long = 50L * 1024L * 1024L,
)
