package com.hank.musicfree.core.model

data class ParsedLyricLine(
    val index: Int,
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)
