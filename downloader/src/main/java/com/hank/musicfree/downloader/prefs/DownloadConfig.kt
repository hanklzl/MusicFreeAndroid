package com.hank.musicfree.downloader.prefs

import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder

data class DownloadConfig(
    val maxDownload: Int,
    val useCellularDownload: Boolean,
    val defaultDownloadQuality: PlayQuality,
    val downloadQualityOrder: QualityFallbackOrder,
    val downloadDirRelative: String,
)
