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

private val STARRED_ROWS = listOf(
    HomeSheetUiModel(
        id = "mock-starred-neo",
        platform = "neo",
        tab = HomeSheetTab.Starred,
        title = "Neo Wave",
        subtitle = "Neo Artist",
        coverUri = null,
    ),
    HomeSheetUiModel(
        id = "mock-starred-midnight",
        platform = "midnight",
        tab = HomeSheetTab.Starred,
        title = "Midnight Drive",
        subtitle = "Night Crew",
        coverUri = null,
    ),
    HomeSheetUiModel(
        id = "mock-starred-summer",
        platform = "summer",
        tab = HomeSheetTab.Starred,
        title = "Summer Tape",
        subtitle = "Tape Club",
        coverUri = null,
    ),
    HomeSheetUiModel(
        id = "mock-starred-mountain",
        platform = "mountain",
        tab = HomeSheetTab.Starred,
        title = "Mountain Echo",
        subtitle = "Echo Lab",
        coverUri = null,
    ),
)

fun buildHomeVisualUiModel(
    selectedTab: HomeSheetTab,
    mineRows: List<HomeSheetUiModel>,
): HomeVisualUiModel {
    val rows = when (selectedTab) {
        HomeSheetTab.Mine -> mineRows
        HomeSheetTab.Starred -> STARRED_ROWS
    }

    return HomeVisualUiModel(
        searchPlaceholder = SEARCH_PLACEHOLDER,
        operations = HOME_OPERATIONS,
        playlistSection = HomePlaylistSectionUiModel(
            selectedTab = selectedTab,
            mineCount = mineRows.size,
            starredCount = STARRED_ROWS.size,
            rows = rows,
        ),
    )
}
