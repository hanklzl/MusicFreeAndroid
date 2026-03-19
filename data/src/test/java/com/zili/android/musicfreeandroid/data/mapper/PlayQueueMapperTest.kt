package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
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
    fun `MusicItem with nulls to PlayQueueEntity and back`() {
        val item = MusicItem("q2", "local", "Song", "Art", null, 0, null, null, null)
        val entity = item.toPlayQueueEntity(sortOrder = 0, converters = converters)
        val roundTripped = entity.toMusicItem(converters)
        assertEquals(item, roundTripped)
    }
}
