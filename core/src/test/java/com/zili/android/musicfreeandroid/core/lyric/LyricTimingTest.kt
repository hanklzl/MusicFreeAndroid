package com.zili.android.musicfreeandroid.core.lyric

import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricTimingTest {

    private val lines = listOf(
        ParsedLyricLine(index = 0, timeMs = 1_000L, text = "A"),
        ParsedLyricLine(index = 1, timeMs = 2_000L, text = "B"),
        ParsedLyricLine(index = 2, timeMs = 4_000L, text = "C"),
    )

    @Test
    fun currentLineBeforeFirstLineIsNull() {
        assertNull(LyricTiming.currentLineIndex(lines, playbackPositionMs = 500L))
    }

    @Test
    fun currentLineBetweenLinesReturnsPreviousLine() {
        assertEquals(1, LyricTiming.currentLineIndex(lines, playbackPositionMs = 3_000L))
    }

    @Test
    fun positiveUserOffsetAdvancesLyricClock() {
        assertEquals(
            1,
            LyricTiming.currentLineIndex(
                lines = lines,
                playbackPositionMs = 1_500L,
                userOffsetMs = 700L,
                metaOffsetMs = 0L,
            ),
        )
    }

    @Test
    fun metaOffsetDelaysLyricClock() {
        assertEquals(
            1,
            LyricTiming.currentLineIndex(
                lines = lines,
                playbackPositionMs = 3_000L,
                userOffsetMs = 0L,
                metaOffsetMs = 1_000L,
            ),
        )
    }

    @Test
    fun seekTargetInvertsDisplayOffset() {
        assertEquals(
            1_300L,
            LyricTiming.seekPositionForLine(
                lineTimeMs = 2_000L,
                userOffsetMs = 700L,
                metaOffsetMs = 0L,
                durationMs = 10_000L,
            ),
        )
    }

    @Test
    fun seekTargetClampsToZero() {
        assertEquals(
            0L,
            LyricTiming.seekPositionForLine(
                lineTimeMs = 500L,
                userOffsetMs = 1_000L,
                metaOffsetMs = 0L,
                durationMs = 10_000L,
            ),
        )
    }

    @Test
    fun seekTargetClampsToDuration() {
        assertEquals(
            10_000L,
            LyricTiming.seekPositionForLine(
                lineTimeMs = 11_000L,
                userOffsetMs = 0L,
                metaOffsetMs = 0L,
                durationMs = 10_000L,
            ),
        )
    }
}
