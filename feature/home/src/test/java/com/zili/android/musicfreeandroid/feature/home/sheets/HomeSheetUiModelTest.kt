package com.zili.android.musicfreeandroid.feature.home.sheets

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.StarredSheet
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
        )

        val row = HomeSheetUiModel.fromStarredSheet(sheet)

        assertEquals(HomeSheetTab.Starred, row.tab)
        assertEquals("sheet-1", row.id)
        assertEquals("demo", row.platform)
        assertEquals("Starred A", row.title)
        assertEquals("Demo Artist", row.subtitle)
    }
}
