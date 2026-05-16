package com.hank.musicfree.core.lyric

import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.RawLyricPayload
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
    fun zeroTimestampLrcIsTimed() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:00.00]Intro"),
            source = source,
        )

        assertTrue(doc.isTimed)
        assertEquals(0L, doc.lines.single().timeMs)
        assertEquals("Intro", doc.lines.single().text)
    }

    @Test
    fun parsesHourTimestampedLrc() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[01:02:03.45]Hour"),
            source = source,
        )

        assertEquals(3_723_450L, doc.lines.single().timeMs)
        assertEquals("Hour", doc.lines.single().text)
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
    fun fallsBackToPlainTextWhenLrcHasNoLyricLines() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(
                rawLrc = "[offset:250]\n[00:01.00]",
                rawLrcTxt = "Line A",
            ),
            source = source,
        )

        assertFalse(doc.isTimed)
        assertEquals(250L, doc.metaOffsetMs)
        assertEquals(listOf("Line A"), doc.lines.map { it.text })
        assertEquals(listOf(0L), doc.lines.map { it.timeMs })
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
    fun preservesBracketedTextAfterTimestamp() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:01.00][Chorus] Hello"),
            source = source,
        )

        assertEquals("[Chorus] Hello", doc.lines.single().text)
    }

    @Test
    fun inlineOffsetTagIsLyricText() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:01.00][offset:250] Hello"),
            source = source,
        )

        assertEquals(0L, doc.metaOffsetMs)
        assertEquals("[offset:250] Hello", doc.lines.single().text)
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

    @Test
    fun parsesSecondOnlyTimestamp() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[60]Hello"),
            source = source,
        )

        assertTrue(doc.isTimed)
        assertEquals(60_000L, doc.lines.single().timeMs)
        assertEquals("Hello", doc.lines.single().text)
    }

    @Test
    fun parsesFractionalSecondOnlyTimestamp() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[265.35]而我只是嘉宾"),
            source = source,
        )

        assertEquals(265_350L, doc.lines.single().timeMs)
        assertEquals("而我只是嘉宾", doc.lines.single().text)
    }

    @Test
    fun mixedFormatsLrcAreTimedAndOrdered() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[5.5]Five and a half\n[00:01.00]One\n[10]Ten"),
            source = source,
        )

        assertTrue(doc.isTimed)
        assertEquals(listOf(1_000L, 5_500L, 10_000L), doc.lines.map { it.timeMs })
        assertEquals(listOf("One", "Five and a half", "Ten"), doc.lines.map { it.text })
    }

    @Test
    fun secondOnlyTranslationMergesByTimestamp() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(
                rawLrc = "[1]A\n[2]B",
                translation = "[1]甲\n[2]乙",
            ),
            source = source,
        )

        assertTrue(doc.hasTranslation)
        assertEquals("甲", doc.lines[0].translation)
        assertEquals("乙", doc.lines[1].translation)
    }

    @Test
    fun secondOnlyOffsetTagIsNotTreatedAsTimestamp() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[offset:250]\n[1.5]Hello"),
            source = source,
        )

        assertEquals(250L, doc.metaOffsetMs)
        assertEquals(1_500L, doc.lines.single().timeMs)
        assertEquals("Hello", doc.lines.single().text)
    }
}
