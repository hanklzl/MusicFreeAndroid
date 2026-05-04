package com.zili.android.musicfreeandroid.core.lyric

import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    private val source = LyricSourceInfo.Plugin(platform = "demo")

    @Test
    fun parsesTimestampedLrc() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:01.00]Hello\n[00:02.50]World"),
            source = source,
        )

        assertEquals(2, doc.lines.size)
        assertEquals(1_000L, doc.lines[0].timeMs)
        assertEquals("Hello", doc.lines[0].text)
        assertEquals(2_500L, doc.lines[1].timeMs)
        assertEquals("World", doc.lines[1].text)
    }

    @Test
    fun parsesMultipleTimestampsOnOneLine() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:01.00][00:03.00]Repeat"),
            source = source,
        )

        assertEquals(listOf(1_000L, 3_000L), doc.lines.map { it.timeMs })
        assertEquals(listOf("Repeat", "Repeat"), doc.lines.map { it.text })
    }

    @Test
    fun parsesOffsetMetaInMilliseconds() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[offset:250]\n[00:01.00]Hello"),
            source = source,
        )

        assertEquals(250L, doc.metaOffsetMs)
    }

    @Test
    fun parsesPlainTextAsStaticLines() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrcTxt = "Line A\nLine B"),
            source = source,
        )

        assertFalse(doc.isTimed)
        assertEquals(listOf("Line A", "Line B"), doc.lines.map { it.text })
        assertEquals(listOf(0L, 0L), doc.lines.map { it.timeMs })
    }

    @Test
    fun mergesTranslationByTimestamp() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(
                rawLrc = "[00:01.00]Hello\n[00:02.00]World",
                translation = "[00:01.00]你好\n[00:02.00]世界",
            ),
            source = source,
        )

        assertTrue(doc.hasTranslation)
        assertEquals("你好", doc.lines[0].translation)
        assertEquals("世界", doc.lines[1].translation)
    }

    @Test
    fun timestampOnlyLrcReturnsEmptyLines() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:01.00]\n[offset:250]"),
            source = source,
        )

        assertTrue(doc.lines.isEmpty())
    }
}
