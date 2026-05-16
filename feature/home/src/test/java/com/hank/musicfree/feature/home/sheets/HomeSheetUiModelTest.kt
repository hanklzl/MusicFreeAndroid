package com.hank.musicfree.feature.home.sheets

import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.StarredKind
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.plugin.api.AlbumItemBase
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSheetUiModelTest {

    @Test
    fun `playlist maps to mine sheet row`() {
        val playlist = Playlist(id = "pl-1", name = "Night Drive", coverUri = "file:///cover.jpg")

        val row = HomeSheetUiModel.fromPlaylist(playlist, musicCount = 12)

        assertEquals(HomeSheetTab.Mine, row.tab)
        assertEquals("Night Drive", row.title)
        assertEquals("12首", row.subtitle)
    }

    @Test
    fun `starred sheet maps with structured identity`() {
        val sheet = StarredSheet(
            id = "sheet-1",
            platform = "demo",
            title = "Starred A",
            artist = "Demo Artist",
            coverUri = "https://example.com/cover.jpg",
            sourceUrl = null,
            description = "A remote sheet",
            artwork = "https://example.com/artwork.jpg",
            worksNum = 88,
            raw = mapOf("id" to "sheet-1", "source" to "demo"),
        )

        val row = HomeSheetUiModel.fromStarredSheet(sheet)
        val seed = row.toMusicSheetItemBase()

        assertEquals(HomeSheetTab.Starred, row.tab)
        assertEquals("sheet-1", row.id)
        assertEquals("demo", row.platform)
        assertEquals("Starred A", row.title)
        assertEquals("Demo Artist", row.subtitle)
        assertEquals("sheet-1", seed.id)
        assertEquals("demo", seed.platform)
        assertEquals("Starred A", seed.title)
        assertEquals("Demo Artist", seed.artist)
        assertEquals("A remote sheet", seed.description)
        assertEquals("https://example.com/cover.jpg", seed.coverImg)
        assertEquals("https://example.com/artwork.jpg", seed.artwork)
        assertEquals(88, seed.worksNum)
        assertEquals(mapOf("id" to "sheet-1", "source" to "demo"), seed.raw)
    }

    @Test
    fun `starred sheet seed preserves artist when artist equals platform`() {
        val sheet = StarredSheet(
            id = "sheet-1",
            platform = "demo",
            title = "Starred A",
            artist = "demo",
            coverUri = null,
            sourceUrl = null,
        )

        val seed = HomeSheetUiModel.fromStarredSheet(sheet).toMusicSheetItemBase()

        assertEquals("demo", seed.artist)
    }

    @Test
    fun `fromStarredSheet propagates kind ALBUM`() {
        val sheet = StarredSheet(
            id = "alb-1", platform = "qq",
            title = "Album", artist = "X", coverUri = null, sourceUrl = null,
            kind = StarredKind.ALBUM,
        )
        val ui = HomeSheetUiModel.fromStarredSheet(sheet)
        assertEquals(StarredKind.ALBUM, ui.kind)
    }

    @Test
    fun `toAlbumItemBase reconstructs identity and merges sourceUrl`() {
        val ui = HomeSheetUiModel(
            id = "alb-2", platform = "qq",
            kind = StarredKind.ALBUM,
            tab = HomeSheetTab.Starred,
            title = "AlbumTwo",
            subtitle = "ArtistTwo",
            coverUri = "art://2",
            artist = "ArtistTwo",
            sourceUrl = "https://example.com/2",
            description = "desc",
            artwork = "art://2",
            worksNum = 5,
            raw = mapOf("foo" to "bar"),
        )
        val album: AlbumItemBase = ui.toAlbumItemBase()
        assertEquals("alb-2", album.id)
        assertEquals("qq", album.platform)
        assertEquals("AlbumTwo", album.title)
        assertEquals("ArtistTwo", album.artist)
        assertEquals("art://2", album.artwork)
        assertEquals(5, album.worksNum)
        assertEquals("https://example.com/2", album.raw["sourceUrl"])
        assertEquals("bar", album.raw["foo"])
    }
}
