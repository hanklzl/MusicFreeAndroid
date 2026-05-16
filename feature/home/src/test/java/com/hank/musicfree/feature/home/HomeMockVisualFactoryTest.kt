package com.hank.musicfree.feature.home

import com.hank.musicfree.feature.home.sheets.HomeSheetTab
import com.hank.musicfree.feature.home.sheets.HomeSheetUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMockVisualFactoryTest {
    private data class OperationExpectation(
        val id: String,
        val title: String,
        val action: HomeOperationAction,
    )

    private val fakeMineRows = listOf(
        HomeSheetUiModel(
            id = "test-mine-liked",
            platform = null,
            tab = HomeSheetTab.Mine,
            title = "我喜欢",
            subtitle = "18首",
            coverUri = null,
            isDefault = true,
        ),
        HomeSheetUiModel(
            id = "test-mine-cloud",
            platform = null,
            tab = HomeSheetTab.Mine,
            title = "云端备份",
            subtitle = "32首",
            coverUri = null,
        ),
        HomeSheetUiModel(
            id = "test-mine-focus",
            platform = null,
            tab = HomeSheetTab.Mine,
            title = "专注循环",
            subtitle = "24首",
            coverUri = null,
        ),
        HomeSheetUiModel(
            id = "test-mine-drive",
            platform = null,
            tab = HomeSheetTab.Mine,
            title = "通勤节奏",
            subtitle = "16首",
            coverUri = null,
        ),
    )

    private val fakeStarredRows = listOf(
        HomeSheetUiModel(
            id = "starred-sheet-1",
            platform = "demo",
            tab = HomeSheetTab.Starred,
            title = "收藏歌单 A",
            subtitle = "Demo Artist",
            coverUri = "https://example.com/cover-a.jpg",
        ),
        HomeSheetUiModel(
            id = "starred-sheet-2",
            platform = "kuwo",
            tab = HomeSheetTab.Starred,
            title = "收藏歌单 B",
            subtitle = "Kuwo Artist",
            coverUri = "https://example.com/cover-b.jpg",
        ),
    )

    @Test
    fun `mine tab exposes passed-in playlist rows`() {
        val uiModel = buildHomeVisualUiModel(
            selectedTab = HomeSheetTab.Mine,
            mineRows = fakeMineRows,
            starredRows = fakeStarredRows,
        )

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
        assertEquals(2, uiModel.playlistSection.starredCount)
        assertEquals(fakeMineRows.map { it.id }, uiModel.playlistSection.rows.map { it.id })
        assertTrue(uiModel.playlistSection.rows.all { it.subtitle.isNotBlank() })
    }

    @Test
    fun `starred tab exposes passed-in starred rows without mock fallback`() {
        val mine = buildHomeVisualUiModel(
            selectedTab = HomeSheetTab.Mine,
            mineRows = fakeMineRows,
            starredRows = fakeStarredRows,
        )
        val starred = buildHomeVisualUiModel(
            selectedTab = HomeSheetTab.Starred,
            mineRows = fakeMineRows,
            starredRows = fakeStarredRows,
        )

        assertEquals(mine.playlistSection.mineCount, starred.playlistSection.mineCount)
        assertEquals(mine.playlistSection.starredCount, starred.playlistSection.starredCount)
        assertEquals(4, mine.operations.size)
        assertEquals(4, starred.operations.size)
        assertNotEquals(mine.playlistSection.rows.map { it.id }, starred.playlistSection.rows.map { it.id })
        assertEquals(fakeStarredRows.map { it.id }, starred.playlistSection.rows.map { it.id })
    }
}
