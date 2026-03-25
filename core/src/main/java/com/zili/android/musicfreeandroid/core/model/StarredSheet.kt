package com.zili.android.musicfreeandroid.core.model

data class StarredSheet(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
)
