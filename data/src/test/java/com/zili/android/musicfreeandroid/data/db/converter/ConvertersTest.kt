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
}
