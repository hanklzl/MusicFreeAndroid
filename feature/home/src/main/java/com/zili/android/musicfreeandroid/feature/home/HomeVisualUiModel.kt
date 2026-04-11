package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModel

data class HomeVisualUiModel(
    val searchPlaceholder: String,
    val operations: List<HomeOperationUiModel>,
    val playlistSection: HomePlaylistSectionUiModel,
)

data class HomeOperationUiModel(
    val id: String,
    val title: String,
    val action: HomeOperationAction,
)

enum class HomeOperationAction {
    RecommendSheets,
    TopList,
    History,
    LocalMusic,
}

data class HomePlaylistSectionUiModel(
    val selectedTab: HomeSheetTab,
    val mineCount: Int,
    val starredCount: Int,
    val rows: List<HomeSheetUiModel>,
)
