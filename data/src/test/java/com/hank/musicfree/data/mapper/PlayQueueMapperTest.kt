package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.PlayQueueEntity
import org.junit.Assert.*
import org.junit.Test

class PlayQueueMapperTest {

    private val converters = Converters()

    @Test
    fun `MusicItem to PlayQueueEntity and back`() {
        val item = MusicItem(
            id = "q1",
            platform = "local",
            title = "Queue Song",
            artist = "Artist",
            album = "Album",
            duration = 180_000L,
            url = "https://example.com/song.mp3",
            artwork = "https://example.com/art.jpg",
            qualities = mapOf(PlayQuality.STANDARD to QualityInfo("url", 4_000_000L)),
        )
        val entity = item.toPlayQueueEntity(sortOrder = 3, converters = converters)
        val roundTripped = entity.toMusicItem(converters)
        assertEquals(item, roundTripped)
        assertEquals(3, entity.sortOrder)
    }

    @Test
    fun `MusicItem with raw payload survives PlayQueueEntity round trip`() {
        val item = MusicItem(
            id = "4930516",
            platform = "yuanliqq",
            title = "Queue Song",
            artist = "Artist",
            album = "Album",
            duration = 180_000L,
            url = null,
            artwork = "https://example.com/art.jpg",
            qualities = mapOf(PlayQuality.HIGH to QualityInfo("quality-id", 1_234L)),
            raw = mapOf(
                "songmid" to "003abc",
                "pay" to mapOf("play" to 1),
                "ids" to listOf("4930516", "003abc"),
            ),
            addedAt = 1_778_000_000_000L,
            localPath = "/storage/emulated/0/Music/Queue Song.mp3",
        )

        val entity = item.toPlayQueueEntity(sortOrder = 7, converters = converters)
        val roundTripped = entity.toMusicItem(converters)

        assertEquals(item.raw, roundTripped.raw)
        assertEquals(item.localPath, roundTripped.localPath)
        assertEquals(item.addedAt, roundTripped.addedAt)
        assertEquals(item.qualities, roundTripped.qualities)
        assertEquals(7, entity.sortOrder)
    }

    @Test
    fun `MusicItem with nulls to PlayQueueEntity and back`() {
        val item = MusicItem("q2", "local", "Song", "Art", null, 0, null, null, null)
        val entity = item.toPlayQueueEntity(sortOrder = 0, converters = converters)
        val roundTripped = entity.toMusicItem(converters)
        assertEquals(item, roundTripped)
    }
}
