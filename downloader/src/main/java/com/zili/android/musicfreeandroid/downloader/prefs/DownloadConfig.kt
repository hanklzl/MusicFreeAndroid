package com.zili.android.musicfreeandroid.downloader.prefs

import com.zili.android.musicfreeandroid.core.model.PlayQuality

data class DownloadConfig(
    val maxDownload: Int,
    val useCellularDownload: Boolean,
    val defaultDownloadQuality: PlayQuality,
    val downloadDirRelative: String,
)
