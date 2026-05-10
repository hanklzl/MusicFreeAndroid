package com.zili.android.musicfreeandroid.feature.home.albumdetail

import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumStarredMapperTest {

    @Test
    fun `toStarredSheet preserves identity coverArtworkAndKind`() {
        val album = AlbumItemBase(
            id = "alb-7",
            platform = "qq",
            title = "AlbumSeven",
            date = "2026-05-10",
            artist = "ArtistSeven",
            description = "desc",
            artwork = "art://7",
            worksNum = 8,
            raw = mapOf("sourceUrl" to "https://example.com/alb-7", "extra" to "x"),
        )

        val starred = album.toStarredSheet()

        assertEquals("alb-7", starred.id)
        assertEquals("qq", starred.platform)
        assertEquals(StarredKind.ALBUM, starred.kind)
        assertEquals("AlbumSeven", starred.title)
        assertEquals("ArtistSeven", starred.artist)
        assertEquals("art://7", starred.coverUri)
        assertEquals("art://7", starred.artwork)
        assertEquals("desc", starred.description)
        assertEquals(8, starred.worksNum)
        assertEquals("https://example.com/alb-7", starred.sourceUrl)
        assertEquals("x", starred.raw["extra"])
    }

    @Test
    fun `toStarredSheet falls back when title and sourceUrl missing`() {
        val album = AlbumItemBase(
            id = "alb-8",
            platform = "kuwo",
            title = null,
            date = null,
            artist = null,
            description = null,
            artwork = null,
            worksNum = null,
            raw = emptyMap(),
        )

        val starred = album.toStarredSheet()

        assertEquals("专辑", starred.title)
        assertNull(starred.sourceUrl)
        assertEquals(StarredKind.ALBUM, starred.kind)
    }
}
