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

    @Test
    fun `toMusicSheetItemBase keeps raw fields`() {
        val map = mapOf<String, Any?>(
            "id" to "sheet-1",
            "platform" to "demo",
            "title" to "Top 100",
            "extraField" to "x",
        )
        val item = JsBridge.toMusicSheetItemBase(map)
        assertEquals("sheet-1", item.id)
        assertEquals("demo", item.platform)
        assertEquals("x", item.raw["extraField"])
    }

    @Test
    fun `parseTopListGroups parses groups and items`() {
        val list = listOf(
            mapOf(
                "title" to "官方榜",
                "data" to listOf(
                    mapOf("id" to "sheet-1", "platform" to "demo", "title" to "热歌榜"),
                ),
            ),
        )
        val groups = JsBridge.parseTopListGroups(list)
        assertEquals(1, groups.size)
        assertEquals("官方榜", groups[0].title)
        assertEquals("sheet-1", groups[0].data[0].id)
    }

    @Test
    fun `parseTopListDetailResult parses page payload`() {
        val payload = mapOf<String, Any?>(
            "isEnd" to false,
            "topListItem" to mapOf(
                "id" to "sheet-1",
                "platform" to "demo",
                "title" to "热歌榜",
            ),
            "musicList" to listOf(
                mapOf("id" to "1", "platform" to "demo", "title" to "Song", "artist" to "A"),
            ),
        )
        val result = JsBridge.parseTopListDetailResult(payload)
        assertFalse(result.isEnd)
        assertEquals("sheet-1", result.topListItem?.id)
        assertEquals(1, result.musicList.size)
    }

    @Test
    fun `parseMusicSheetInfoResult parses sheet detail payload`() {
        val payload = mapOf<String, Any?>(
            "isEnd" to true,
            "sheetItem" to mapOf(
                "id" to "sheet-2",
                "platform" to "demo",
                "title" to "推荐歌单",
            ),
            "musicList" to listOf(
                mapOf("id" to "101", "platform" to "demo", "title" to "Song A", "artist" to "A"),
                mapOf("id" to "102", "platform" to "demo", "title" to "Song B", "artist" to "B"),
            ),
        )

        val result = JsBridge.parseMusicSheetInfoResult(payload)
        assertTrue(result.isEnd)
        assertEquals("sheet-2", result.sheetItem?.id)
        assertEquals(2, result.musicList.size)
    }

    @Test
    fun `parseRecommendSheetTagsResult parses pinned and grouped tags`() {
        val payload = mapOf<String, Any?>(
            "pinned" to listOf(
                mapOf("id" to "tag-1", "platform" to "demo", "title" to "流行"),
            ),
            "data" to listOf(
                mapOf(
                    "title" to "风格",
                    "data" to listOf(
                        mapOf("id" to "tag-2", "platform" to "demo", "title" to "摇滚"),
                    ),
                ),
            ),
        )

        val result = JsBridge.parseRecommendSheetTagsResult(payload)
        assertEquals(1, result.pinned.size)
        assertEquals("tag-1", result.pinned.first().id)
        assertEquals(1, result.data.size)
        assertEquals("风格", result.data.first().title)
        assertEquals("tag-2", result.data.first().data.first().id)
    }
}
