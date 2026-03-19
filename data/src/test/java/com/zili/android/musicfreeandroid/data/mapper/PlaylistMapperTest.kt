package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import org.junit.Assert.*
import org.junit.Test

class PlaylistMapperTest {

    @Test
    fun `model to entity and back preserves fields`() {
        val model = Playlist(id = "pl1", name = "Favorites", coverUri = "https://example.com/cover.jpg")
        val now = System.currentTimeMillis()
        val entity = model.toEntity(createdAt = now, updatedAt = now)
        val roundTripped = entity.toModel()
        assertEquals(model, roundTripped)
    }

    @Test
    fun `entity preserves timestamps`() {
        val model = Playlist(id = "pl2", name = "Empty", coverUri = null)
        val entity = model.toEntity(createdAt = 1000L, updatedAt = 2000L)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
    }
}
