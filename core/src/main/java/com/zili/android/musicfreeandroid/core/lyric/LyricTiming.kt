package com.zili.android.musicfreeandroid.core.lyric

import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine

object LyricTiming {

    fun currentLineIndex(
        lines: List<ParsedLyricLine>,
        playbackPositionMs: Long,
        userOffsetMs: Long = 0L,
        metaOffsetMs: Long = 0L,
    ): Int? {
        if (lines.isEmpty()) return null
        if (lines.size > 1 && lines.all { it.timeMs == 0L }) return null

        val lyricClockMs = playbackPositionMs + userOffsetMs - metaOffsetMs
        if (lyricClockMs < lines.first().timeMs) return null

        val insertionPoint = lines.binarySearchBy(lyricClockMs) { it.timeMs }
        return if (insertionPoint >= 0) {
            lines[insertionPoint].index
        } else {
            val previousPosition = -insertionPoint - 2
            lines.getOrNull(previousPosition)?.index
        }
    }

    fun seekPositionForLine(
        lineTimeMs: Long,
        userOffsetMs: Long = 0L,
        metaOffsetMs: Long = 0L,
        durationMs: Long,
    ): Long = (lineTimeMs - userOffsetMs + metaOffsetMs).coerceIn(0L, durationMs)
}
