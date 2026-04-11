package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMockVisualFactoryTest {
    private data class OperationExpectation(
        val id: String,
        val title: String,
        val action: HomeOperationAction,
    )

    @Test
    fun `mine tab mock state exposes four stable playlist rows`() {
        val uiModel = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Mine)

        assertEquals("点击这里开始搜索", uiModel.searchPlaceholder)
        assertEquals(
            listOf(
                OperationExpectation("mock-operation-recommend", "推荐歌单", HomeOperationAction.RecommendSheets),
                OperationExpectation("mock-operation-top-list", "榜单", HomeOperationAction.TopList),
                OperationExpectation("mock-operation-history", "播放历史", HomeOperationAction.History),
                OperationExpectation("mock-operation-local", "本地音乐", HomeOperationAction.LocalMusic),
            ),
            uiModel.operations.map { OperationExpectation(it.id, it.title, it.action) },
        )
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
        assertEquals(4, mine.operations.size)
        assertEquals(4, starred.operations.size)
        assertNotEquals(
            mine.playlistSection.rows.map { it.id },
            starred.playlistSection.rows.map { it.id },
        )
    }
}
