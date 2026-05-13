package com.zili.android.musicfreeandroid.data.db.converter

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun musicItemRoundTripPreservesMainFields() {
        val item = MusicItem(
            id = "1",
            platform = "demo",
            title = "Title",
            artist = "Artist",
            album = "Album",
            duration = 123_000L,
            url = "https://example.test/song.mp3",
            artwork = "https://example.test/art.jpg",
            localPath = "content://media/external/audio/media/42",
            qualities = mapOf(
                PlayQuality.STANDARD to QualityInfo(
                    url = "https://example.test/std.mp3",
                    size = 1234L,
                ),
            ),
            raw = mapOf(
                "source" to "plugin",
                "nested" to mapOf("rank" to 1),
                "tags" to listOf("a", "b"),
            ),
            addedAt = 123L,
        )

        val restored = converters.jsonToMusicItem(converters.musicItemToJson(item))

        assertEquals(item.id, restored?.id)
        assertEquals(item.platform, restored?.platform)
        assertEquals(item.title, restored?.title)
        assertEquals(item.artist, restored?.artist)
        assertEquals(item.album, restored?.album)
        assertEquals(item.duration, restored?.duration)
        assertEquals(item.url, restored?.url)
        assertEquals(item.artwork, restored?.artwork)
        assertEquals(item.localPath, restored?.localPath)
        assertEquals(item.qualities, restored?.qualities)
        assertEquals(item.raw, restored?.raw)
        assertEquals(item.addedAt, restored?.addedAt)
    }

    @Test
    fun jsonToMusicItemReturnsNullForEmptyInput() {
        assertNull(converters.jsonToMusicItem(null))
        assertNull(converters.jsonToMusicItem(""))
    }

    @Test
    fun musicItemRoundTripPreservesNullableFields() {
        val item = MusicItem(
            id = "2",
            platform = "demo",
            title = "Title",
            artist = "Artist",
            album = null,
            duration = 0L,
            url = null,
            artwork = null,
            qualities = null,
        )

        val restored = converters.jsonToMusicItem(converters.musicItemToJson(item))

        assertNull(restored?.album)
        assertNull(restored?.url)
        assertNull(restored?.artwork)
        assertNull(restored?.qualities)
    }

    @Test
    fun jsonToMusicItemHandlesLegacyJsonWithoutLocalPath() {
        val legacyJson = """
            {
                "id":"legacy-1",
                "platform":"demo",
                "title":"Legacy Song",
                "artist":"Legacy Artist",
                "album":"Legacy Album",
                "duration":180000,
                "url":"https://example.test/legacy.mp3",
                "artwork":"https://example.test/legacy.jpg",
                "qualities":null,
                "raw":{"origin":"plugin"},
                "addedAt":321
            }
        """.trimIndent()

        val restored = converters.jsonToMusicItem(legacyJson)

        assertEquals("legacy-1", restored?.id)
        assertEquals("demo", restored?.platform)
        assertEquals("Legacy Song", restored?.title)
        assertEquals("Legacy Artist", restored?.artist)
        assertEquals("Legacy Album", restored?.album)
        assertEquals(180000L, restored?.duration)
        assertEquals("https://example.test/legacy.mp3", restored?.url)
        assertEquals("https://example.test/legacy.jpg", restored?.artwork)
        assertEquals(null, restored?.localPath)
        assertEquals(mapOf("origin" to "plugin"), restored?.raw)
        assertEquals(321L, restored?.addedAt)
        assertNull(restored?.qualities)
    }
}
