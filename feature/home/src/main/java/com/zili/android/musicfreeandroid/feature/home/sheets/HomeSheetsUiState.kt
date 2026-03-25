package com.zili.android.musicfreeandroid.feature.home.sheets

data class HomeSheetsUiState(
    val selectedTab: HomeSheetTab = HomeSheetTab.Mine,
    val mineCount: Int = 0,
    val starredCount: Int = 0,
    val items: List<HomeSheetUiModel> = emptyList(),
)
