package com.zili.android.musicfreeandroid.core.model

data class ParsedLyricLine(
    val index: Int,
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)
