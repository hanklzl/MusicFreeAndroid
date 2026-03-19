package com.zili.android.musicfreeandroid.core.model

data class MediaSourceResult(
    val url: String,
    val headers: Map<String, String>?,
    val userAgent: String?,
    val quality: PlayQuality?,
)
