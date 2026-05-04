package com.zili.android.musicfreeandroid.core.model

data class LyricDocument(
    val musicId: String,
    val musicPlatform: String,
    val lines: List<ParsedLyricLine>,
    val metaOffsetMs: Long = 0L,
    val source: LyricSourceInfo,
    val isTimed: Boolean = lines.any { it.timeMs > 0L },
    val rawLrc: String? = null,
    val rawLrcTxt: String? = null,
    val translationRaw: String? = null,
) {
    val hasTranslation: Boolean get() = lines.any { !it.translation.isNullOrBlank() }
}
