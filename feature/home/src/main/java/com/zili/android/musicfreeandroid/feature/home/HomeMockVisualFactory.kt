package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModel

private const val SEARCH_PLACEHOLDER = "点击这里开始搜索"

private val HOME_OPERATIONS = listOf(
    HomeOperationUiModel(
        id = "mock-operation-recommend",
        title = "推荐歌单",
        action = HomeOperationAction.RecommendSheets,
    ),
    HomeOperationUiModel(
        id = "mock-operation-top-list",
        title = "榜单",
        action = HomeOperationAction.TopList,
    ),
    HomeOperationUiModel(
        id = "mock-operation-history",
        title = "播放历史",
        action = HomeOperationAction.History,
    ),
    HomeOperationUiModel(
        id = "mock-operation-local",
        title = "本地音乐",
        action = HomeOperationAction.LocalMusic,
    ),
)

fun buildHomeVisualUiModel(
    selectedTab: HomeSheetTab,
    mineRows: List<HomeSheetUiModel>,
    starredRows: List<HomeSheetUiModel> = emptyList(),
): HomeVisualUiModel {
    val rows = when (selectedTab) {
        HomeSheetTab.Mine -> mineRows
        HomeSheetTab.Starred -> starredRows
    }

    return HomeVisualUiModel(
        searchPlaceholder = SEARCH_PLACEHOLDER,
        operations = HOME_OPERATIONS,
        playlistSection = HomePlaylistSectionUiModel(
            selectedTab = selectedTab,
            mineCount = mineRows.size,
            starredCount = starredRows.size,
            rows = rows,
        ),
    )
}
