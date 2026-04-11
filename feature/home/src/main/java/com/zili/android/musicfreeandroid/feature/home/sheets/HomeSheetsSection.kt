package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.HomePlaylistSectionUiModel

fun LazyListScope.homeSheetsSection(
    uiModel: HomePlaylistSectionUiModel,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
) {
    item(key = FidelityAnchors.Home.SheetsRoot) {
        HomeSheetsHeader(
            uiModel = uiModel,
            onSelectTab = onSelectTab,
            onCreateSheetClick = onCreateClick,
            onImportSheetClick = onImportClick,
        )
    }

    item(key = "${FidelityAnchors.Home.SheetsRoot}.spacer") {
        Spacer(modifier = androidx.compose.ui.Modifier.height(rpx(12)))
    }

    homeSheetsList(
        uiModel = uiModel,
        onOpenMineSheet = onOpenMineSheet,
        onOpenStarredSheet = onOpenStarredSheet,
    )
}
