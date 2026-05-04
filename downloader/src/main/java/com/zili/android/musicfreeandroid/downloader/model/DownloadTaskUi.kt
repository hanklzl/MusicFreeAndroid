package com.zili.android.musicfreeandroid.downloader.model

data class DownloadTaskUi(
    val key: MediaKey,
    val title: String,
    val artist: String,
    val artwork: String?,
    val status: DownloadStatus,
    val targetQuality: String,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val errorReason: DownloadFailReason?,
)
