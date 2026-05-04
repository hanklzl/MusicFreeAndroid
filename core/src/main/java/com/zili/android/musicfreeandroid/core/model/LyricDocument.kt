package com.zili.android.musicfreeandroid.core.model

data class LyricDocument(
    val musicId: String,
    val musicPlatform: String,
    val lines: List<ParsedLyricLine>,
    val metaOffsetMs: Long = 0L,
    val source: LyricSourceInfo,
    val rawLrc: String? = null,
    val rawLrcTxt: String? = null,
    val translationRaw: String? = null,
    val isTimed: Boolean = lines.any { it.timeMs > 0L } ||
        (lines.isNotEmpty() && rawLrc?.containsValidTimestamp() == true),
) {
    val hasTranslation: Boolean get() = lines.any { !it.translation.isNullOrBlank() }
}

private val lyricTimestampRegex = Regex("\\[(?:\\d+:)?\\d+:\\d+(?:\\.\\d+)?]")

private fun String.containsValidTimestamp(): Boolean = lyricTimestampRegex.containsMatchIn(this)
