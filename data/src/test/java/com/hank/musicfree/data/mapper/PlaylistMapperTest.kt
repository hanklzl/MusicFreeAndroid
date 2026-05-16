package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.data.db.entity.PlaylistEntity
import org.junit.Assert.*
import org.junit.Test

class PlaylistMapperTest {

    @Test
    fun `model to entity and back preserves fields including new ones`() {
        val original = Playlist(
            id = "p1",
            name = "Mix",
            coverUri = "playlist_covers/p1.jpg",
            description = "我的工作时音乐",
            sortMode = SortMode.Newest,
            createdAt = 1700000000000L,
            updatedAt = 1700000000005L,
            worksNum = 0, // worksNum is computed at query time, not persisted; round-trip stays 0
        )
        val entity = original.toEntity(createdAt = original.createdAt, updatedAt = original.updatedAt)
        val back = entity.toModel()
        assertEquals(original.copy(worksNum = 0), back)
    }

    @Test
    fun `entity preserves timestamps`() {
        val model = Playlist(id = "pl2", name = "Empty", coverUri = null)
        val entity = model.toEntity(createdAt = 1000L, updatedAt = 2000L)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
    }

    @Test
    fun `parseSortMode falls back to Manual for unknown value`() {
        val entity = PlaylistEntity(
            id = "p2",
            name = "Legacy",
            coverUri = null,
            description = null,
            sortMode = "UNKNOWN_SORT_MODE",
            createdAt = 0L,
            updatedAt = 0L,
        )
        val model = entity.toModel()
        assertEquals(SortMode.Manual, model.sortMode)
    }

    @Test
    fun `toEntity maps all sortMode values correctly`() {
        SortMode.values().forEach { mode ->
            val playlist = Playlist(
                id = "id_${mode.name}",
                name = mode.name,
                sortMode = mode,
            )
            val entity = playlist.toEntity(createdAt = 0L, updatedAt = 0L)
            assertEquals(mode.name, entity.sortMode)
            val back = entity.toModel()
            assertEquals(mode, back.sortMode)
        }
    }

    @Test
    fun `toModel without resolver preserves coverUri verbatim`() {
        val entity = PlaylistEntity(
            id = "p1", name = "Mix",
            coverUri = "playlist_covers/p1.jpg",
            description = null, sortMode = "Manual",
            createdAt = 0L, updatedAt = 0L,
        )
        assertEquals("playlist_covers/p1.jpg", entity.toModel().coverUri)
    }

    @Test
    fun `toModel applies resolver only when it returns non-null`() {
        val entity = PlaylistEntity(
            id = "p1", name = "Mix",
            coverUri = "playlist_covers/p1.jpg",
            description = null, sortMode = "Manual",
            createdAt = 0L, updatedAt = 0L,
        )
        val resolved = entity.toModel(legacyCoverResolver = { raw ->
            if (raw.startsWith("playlist_covers/")) "file:///abs/$raw" else null
        })
        assertEquals("file:///abs/playlist_covers/p1.jpg", resolved.coverUri)
    }

    @Test
    fun `toModel resolver returning null falls back to raw value`() {
        val entity = PlaylistEntity(
            id = "p1", name = "Mix",
            coverUri = "https://example.com/cover.jpg",
            description = null, sortMode = "Manual",
            createdAt = 0L, updatedAt = 0L,
        )
        val resolved = entity.toModel(legacyCoverResolver = { _ -> null })
        assertEquals("https://example.com/cover.jpg", resolved.coverUri)
    }

    @Test
    fun `toModel preserves null coverUri regardless of resolver`() {
        val entity = PlaylistEntity(
            id = "p1", name = "Empty",
            coverUri = null,
            description = null, sortMode = "Manual",
            createdAt = 0L, updatedAt = 0L,
        )
        val resolved = entity.toModel(legacyCoverResolver = { _ -> "file:///should/not/show" })
        assertEquals(null, resolved.coverUri)
    }
}
