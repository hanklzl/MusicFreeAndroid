package com.hank.musicfree.feature.home.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.home.HomePlaylistSectionUiModel

fun LazyListScope.homeSheetsSection(
    uiModel: HomePlaylistSectionUiModel,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
    onOpenStarredAlbum: (HomeSheetUiModel) -> Unit,
    onTrashClick: (HomeSheetUiModel) -> Unit,
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
        onOpenStarredAlbum = onOpenStarredAlbum,
        onTrashClick = onTrashClick,
    )
}
