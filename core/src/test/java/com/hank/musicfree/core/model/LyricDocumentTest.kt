package com.hank.musicfree.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricDocumentTest {

    @Test
    fun constructorKeepsTaskOnePositionalShape() {
        val doc = LyricDocument(
            "m1",
            "demo",
            emptyList(),
            0L,
            LyricSourceInfo.Plugin("demo"),
            null,
            null,
            null,
        )

        assertFalse(doc.isTimed)
    }

    @Test
    fun timedLinesWithoutRawLrcAreTimed() {
        val doc = LyricDocument(
            musicId = "m1",
            musicPlatform = "demo",
            lines = listOf(ParsedLyricLine(index = 0, timeMs = 1_000L, text = "A")),
            metaOffsetMs = 0L,
            source = LyricSourceInfo.Plugin("demo"),
        )

        assertTrue(doc.isTimed)
    }

    @Test
    fun zeroTimestampLinesCanBeExplicitlyTimedWithoutRawLrc() {
        val doc = LyricDocument(
            musicId = "m1",
            musicPlatform = "demo",
            lines = listOf(ParsedLyricLine(index = 0, timeMs = 0L, text = "Intro")),
            metaOffsetMs = 0L,
            source = LyricSourceInfo.Plugin("demo"),
            isTimed = true,
        )

        assertTrue(doc.isTimed)
    }
}
