package com.hank.musicfree.feature.home.artistdetail.navigation

import com.hank.musicfree.plugin.api.ArtistItemBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistDetailSeedStoreTest {

    @After
    fun tearDown() {
        ArtistDetailSeedStore.clear()
    }

    @Test
    fun `take resolves stored seed repeatedly`() {
        val seed = artist("artist-1")

        val token = ArtistDetailSeedStore.put(seed)

        assertEquals(seed, ArtistDetailSeedStore.take(token))
        assertEquals(seed, ArtistDetailSeedStore.take(token))
    }

    @Test
    fun `take returns null for blank or unknown token`() {
        assertNull(ArtistDetailSeedStore.take(null))
        assertNull(ArtistDetailSeedStore.take(""))
        assertNull(ArtistDetailSeedStore.take("missing"))
    }

    private fun artist(id: String): ArtistItemBase = ArtistItemBase(
        id = id,
        platform = "demo",
        name = "Artist $id",
        avatar = "https://example.com/$id.jpg",
        fans = 1234,
        description = "Description",
        worksNum = 12,
        raw = mapOf("id" to id, "custom" to "kept"),
    )
}
