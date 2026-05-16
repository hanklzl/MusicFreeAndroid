package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.MusicItemEntity
import org.junit.Assert.*
import org.junit.Test

class MusicItemMapperTest {

    private val converters = Converters()

    @Test
    fun `model to entity and back preserves all fields`() {
        val model = MusicItem(
            id = "song1",
            platform = "netease",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 240_000L,
            url = "https://example.com/song.mp3",
            artwork = "https://example.com/cover.jpg",
            qualities = mapOf(
                PlayQuality.HIGH to QualityInfo("https://example.com/high.mp3", 8_000_000L),
                PlayQuality.LOW to QualityInfo("https://example.com/low.mp3", 3_000_000L),
            ),
            raw = mapOf(
                "traceId" to "abc",
                "nested" to mapOf("token" to "secret", "page" to 3),
                "flags" to listOf("album", true),
            ),
            localPath = "content://media/external/audio/media/99",
        )
        val entity = model.toEntity(converters)
        val roundTripped = entity.toModel(converters)
        assertEquals(model, roundTripped)
    }

    @Test
    fun `localPath and raw survive entity conversion`() {
        val item = MusicItem(
            id = "local-copy",
            platform = "demo",
            title = "Downloaded",
            artist = "Artist",
            album = "Album",
            duration = 180_000L,
            url = "https://example.test/download.mp3",
            artwork = "https://example.test/cover.jpg",
            qualities = null,
            raw = mapOf("origin" to "plugin"),
            localPath = "content://media/external/audio/media/99",
        )

        val restored = item.toEntity(converters).toModel(converters)

        assertEquals(item.localPath, restored.localPath)
        assertEquals(item.raw, restored.raw)
    }

    @Test
    fun `model with null optionals round-trips`() {
        val model = MusicItem(
            id = "song2",
            platform = "local",
            title = "Minimal",
            artist = "Unknown",
            album = null,
            duration = 0L,
            url = null,
            artwork = null,
            qualities = null,
        )
        val entity = model.toEntity(converters)
        val roundTripped = entity.toModel(converters)
        assertEquals(model, roundTripped)
    }

    @Test
    fun `entity fields are correctly set`() {
        val model = MusicItem("id1", "platform1", "Title", "Artist", "Album", 100, "url", "art", null)
        val entity = model.toEntity(converters)
        assertEquals("id1", entity.id)
        assertEquals("platform1", entity.platform)
        assertEquals("Title", entity.title)
        assertEquals("Artist", entity.artist)
        assertEquals("Album", entity.album)
        assertEquals(100L, entity.duration)
        assertEquals("url", entity.url)
        assertEquals("art", entity.artwork)
        assertNull(entity.qualitiesJson)
    }
}
