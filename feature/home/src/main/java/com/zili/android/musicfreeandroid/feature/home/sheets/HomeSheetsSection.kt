package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

fun LazyListScope.homeSheetsSection(
    uiState: HomeSheetsUiState,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
) {
    item(key = FidelityAnchors.Home.SheetsRoot) {
        HomeSheetsHeader(
            uiState = uiState,
            onSelectTab = onSelectTab,
            onRequestCreate = onCreateClick,
            onImportSheet = onImportClick,
        )
    }

    item(key = "${FidelityAnchors.Home.SheetsRoot}.spacer") {
        Spacer(modifier = androidx.compose.ui.Modifier.height(rpx(12)))
    }

    homeSheetsList(
        uiState = uiState,
        onOpenMineSheet = onOpenMineSheet,
        onOpenStarredSheet = onOpenStarredSheet,
    )
}

fun LazyListScope.homeSheetsSection(
    uiState: HomeSheetsUiState,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateSheet: (String) -> Unit,
    onImportSheet: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit = {},
) {
    homeSheetsSection(
        uiState = uiState,
        onSelectTab = onSelectTab,
        onCreateClick = {},
        onImportClick = onImportSheet,
        onOpenMineSheet = onOpenMineSheet,
        onOpenStarredSheet = onOpenStarredSheet,
    )
}
