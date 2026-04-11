package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMockVisualFactoryTest {
    @Test
    fun `mine tab mock state exposes four stable playlist rows`() {
        val uiModel = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Mine)

        assertEquals(HomeSheetTab.Mine, uiModel.playlistSection.selectedTab)
        assertEquals(4, uiModel.playlistSection.rows.size)
        assertTrue(uiModel.playlistSection.rows.none { it.title.contains("mock", ignoreCase = true) })
        assertTrue(uiModel.playlistSection.rows.all { it.subtitle.isNotBlank() })
    }

    @Test
    fun `starred tab mock state swaps row set without changing header counts`() {
        val mine = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Mine)
        val starred = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Starred)

        assertEquals(mine.playlistSection.mineCount, starred.playlistSection.mineCount)
        assertEquals(mine.playlistSection.starredCount, starred.playlistSection.starredCount)
        assertNotEquals(
            mine.playlistSection.rows.map { it.id },
            starred.playlistSection.rows.map { it.id },
        )
    }
}
