package com.zili.android.musicfreeandroid.feature.home.sheets

import com.zili.android.musicfreeandroid.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSheetUiModelTest {

    @Test
    fun `playlist maps to mine sheet row`() {
        val playlist = Playlist(id = "pl-1", name = "Night Drive", coverUri = "file:///cover.jpg")

        val row = HomeSheetUiModel.fromPlaylist(playlist, musicCount = 12)

        assertEquals(HomeSheetTab.Mine, row.tab)
        assertEquals("Night Drive", row.title)
        assertEquals("12 首歌曲", row.subtitle)
    }
}
