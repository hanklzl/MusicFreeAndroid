package com.zili.android.musicfreeandroid.logging

import java.io.File

data class LoggingConfig(
    val cacheDir: File,
    val logDir: File,
    val feedbackDir: File,
    val aesKey16: String,
    val aesIv16: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val applicationId: String,
    val buildType: String,
    val retentionDays: Int = 7,
    val maxTotalBytes: Long = 50L * 1024L * 1024L,
)
