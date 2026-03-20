package com.zili.android.musicfreeandroid.plugin.engine

import org.junit.Assert.*
import org.junit.Test

class JsBridgeTest {
    @Test
    fun `toMusicItem parses map correctly`() {
        val map = mapOf(
            "id" to "123",
            "platform" to "test",
            "title" to "Song",
            "artist" to "Artist",
            "album" to "Album",
            "duration" to 180.0,
            "url" to "http://example.com/song.mp3",
            "artwork" to "http://example.com/cover.jpg",
        )
        val item = JsBridge.toMusicItem(map)
        assertEquals("123", item.id)
        assertEquals("test", item.platform)
        assertEquals("Song", item.title)
        assertEquals("Artist", item.artist)
        assertEquals(180000L, item.duration)
    }

    @Test
    fun `toMusicItem handles missing optional fields`() {
        val map = mapOf<String, Any?>(
            "id" to "1",
            "platform" to "test",
            "title" to "Song",
            "artist" to "Artist",
        )
        val item = JsBridge.toMusicItem(map)
        assertNull(item.url)
        assertNull(item.artwork)
        assertEquals(0L, item.duration)
    }

    @Test
    fun `musicItemToMap converts correctly`() {
        val item = com.zili.android.musicfreeandroid.core.model.MusicItem(
            id = "1", platform = "test", title = "Song", artist = "Artist",
            album = "Album", duration = 180000L, url = null, artwork = null, qualities = null,
        )
        val map = JsBridge.musicItemToMap(item)
        assertEquals("1", map["id"])
        assertEquals(180.0, map["duration"])
    }

    @Test
    fun `parseSearchResult parses correctly`() {
        val map = mapOf<String, Any?>(
            "isEnd" to true,
            "data" to listOf(
                mapOf("id" to "1", "platform" to "test", "title" to "Song", "artist" to "A"),
            ),
        )
        val result = JsBridge.parseSearchResult(map)
        assertTrue(result.isEnd)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `parseMediaSourceResult parses correctly`() {
        val map = mapOf<String, Any?>(
            "url" to "http://example.com/song.mp3",
            "headers" to mapOf("User-Agent" to "test"),
        )
        val result = JsBridge.parseMediaSourceResult(map)
        assertNotNull(result)
        assertEquals("http://example.com/song.mp3", result!!.url)
    }
}
