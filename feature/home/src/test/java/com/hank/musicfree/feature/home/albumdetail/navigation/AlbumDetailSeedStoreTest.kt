package com.hank.musicfree.feature.home.albumdetail.navigation

import com.hank.musicfree.plugin.api.AlbumItemBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumDetailSeedStoreTest {

    @After
    fun tearDown() {
        AlbumDetailSeedStore.clear()
    }

    @Test
    fun `take returns stored seed once`() {
        val seed = album("album-1")

        val token = AlbumDetailSeedStore.put(seed)

        assertEquals(seed, AlbumDetailSeedStore.take(token))
        assertNull(AlbumDetailSeedStore.take(token))
    }

    @Test
    fun `take returns null for blank or unknown token`() {
        assertNull(AlbumDetailSeedStore.take(null))
        assertNull(AlbumDetailSeedStore.take(""))
        assertNull(AlbumDetailSeedStore.take("missing"))
    }

    private fun album(id: String): AlbumItemBase = AlbumItemBase(
        id = id,
        platform = "demo",
        title = "Album $id",
        date = "2026-05-10",
        artist = "Artist",
        description = "Description",
        artwork = "https://example.com/$id.jpg",
        worksNum = 12,
        raw = mapOf("id" to id, "custom" to "kept"),
    )
}
