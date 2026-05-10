package com.zili.android.musicfreeandroid.downloader.prefs

import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder

data class DownloadConfig(
    val maxDownload: Int,
    val useCellularDownload: Boolean,
    val defaultDownloadQuality: PlayQuality,
    val downloadQualityOrder: QualityFallbackOrder,
    val downloadDirRelative: String,
)
