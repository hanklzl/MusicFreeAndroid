package com.hank.musicfree.core.model

import org.junit.Assert.*
import org.junit.Test

class MusicItemTest {

    @Test
    fun `MusicItem construction with required fields`() {
        val item = MusicItem(
            id = "song1",
            platform = "local",
            title = "Test Song",
            artist = "Test Artist",
            album = null,
            duration = 180_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
        assertEquals("song1", item.id)
        assertEquals("local", item.platform)
        assertEquals("Test Song", item.title)
        assertEquals(180_000L, item.duration)
    }

    @Test
    fun `MusicItem equality uses id and platform`() {
        val a = MusicItem("1", "local", "A", "Artist", null, 0, null, null, null)
        val b = MusicItem("1", "local", "B", "Other", "Album", 999, "url", "art", null)
        assertNotEquals(a, b)
        assertEquals(a.id, b.id)
        assertEquals(a.platform, b.platform)
    }

    @Test
    fun `MusicItem with qualities`() {
        val qualities = mapOf(
            PlayQuality.HIGH to QualityInfo(url = "https://example.com/high.mp3", size = 5_000_000L),
            PlayQuality.LOW to QualityInfo(url = "https://example.com/low.mp3", size = 2_000_000L),
        )
        val item = MusicItem("1", "plugin1", "Song", "Artist", null, 240_000, null, null, qualities)
        assertEquals(2, item.qualities!!.size)
        assertEquals("https://example.com/high.mp3", item.qualities!![PlayQuality.HIGH]?.url)
    }

    @Test
    fun `PlayQuality values`() {
        val values = PlayQuality.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(PlayQuality.LOW))
        assertTrue(values.contains(PlayQuality.STANDARD))
        assertTrue(values.contains(PlayQuality.HIGH))
        assertTrue(values.contains(PlayQuality.SUPER))
    }

    @Test
    fun `RepeatMode values`() {
        val values = RepeatMode.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(RepeatMode.OFF))
        assertTrue(values.contains(RepeatMode.ONE))
        assertTrue(values.contains(RepeatMode.ALL))
    }

    @Test
    fun `MediaSourceResult construction`() {
        val result = MediaSourceResult(
            url = "https://example.com/stream.mp3",
            headers = mapOf("Referer" to "https://example.com"),
            userAgent = "MusicFree/1.0",
            quality = PlayQuality.HIGH,
        )
        assertEquals("https://example.com/stream.mp3", result.url)
        assertEquals("MusicFree/1.0", result.userAgent)
        assertEquals(PlayQuality.HIGH, result.quality)
        assertEquals(1, result.headers!!.size)
    }

    @Test
    fun `Playlist construction`() {
        val playlist = Playlist(
            id = "pl1",
            name = "My Favorites",
            coverUri = null,
        )
        assertEquals("pl1", playlist.id)
        assertEquals("My Favorites", playlist.name)
        assertNull(playlist.coverUri)
    }

    @Test
    fun `LyricLine construction`() {
        val line = LyricLine(timeMs = 30_500L, text = "Hello world")
        assertEquals(30_500L, line.timeMs)
        assertEquals("Hello world", line.text)
    }
}
