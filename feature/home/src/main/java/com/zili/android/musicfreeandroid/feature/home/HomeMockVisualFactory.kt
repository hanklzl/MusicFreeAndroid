package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModel

private const val SEARCH_PLACEHOLDER = "点击这里开始搜索"

private val HOME_OPERATIONS = listOf(
    HomeOperationUiModel(id = "mock-operation-recommend", title = "推荐歌单"),
    HomeOperationUiModel(id = "mock-operation-top-list", title = "榜单"),
    HomeOperationUiModel(id = "mock-operation-history", title = "播放历史"),
    HomeOperationUiModel(id = "mock-operation-local", title = "本地音乐"),
)

private val MINE_ROWS = listOf(
    HomeSheetUiModel(
        id = "mock-mine-liked",
        platform = null,
        tab = HomeSheetTab.Mine,
        title = "日常收藏",
        subtitle = "18 首歌曲",
        coverUri = null,
    ),
    HomeSheetUiModel(
        id = "mock-mine-cloud",
        platform = null,
        tab = HomeSheetTab.Mine,
        title = "云端备份",
        subtitle = "32 首歌曲",
        coverUri = null,
    ),
    HomeSheetUiModel(
        id = "mock-mine-focus",
        platform = null,
        tab = HomeSheetTab.Mine,
        title = "专注循环",
        subtitle = "24 首歌曲",
        coverUri = null,
    ),
    HomeSheetUiModel(
        id = "mock-mine-drive",
        platform = null,
        tab = HomeSheetTab.Mine,
        title = "通勤节奏",
        subtitle = "16 首歌曲",
        coverUri = null,
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

fun buildHomeVisualUiModel(selectedTab: HomeSheetTab): HomeVisualUiModel {
    val rows = when (selectedTab) {
        HomeSheetTab.Mine -> MINE_ROWS
        HomeSheetTab.Starred -> STARRED_ROWS
    }

    return HomeVisualUiModel(
        searchPlaceholder = SEARCH_PLACEHOLDER,
        operations = HOME_OPERATIONS,
        playlistSection = HomePlaylistSectionUiModel(
            selectedTab = selectedTab,
            mineCount = MINE_ROWS.size,
            starredCount = STARRED_ROWS.size,
            rows = rows,
        ),
    )
}
