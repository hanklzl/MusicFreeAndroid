package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.playlist.CreatePlaylistDialog

fun LazyListScope.homeSheetsSection(
    uiState: HomeSheetsUiState,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateSheet: (String) -> Unit,
    onImportSheet: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit = {},
) {
    item(key = FidelityAnchors.Home.SheetsRoot) {
        HomeSheetsSectionHeader(
            uiState = uiState,
            onSelectTab = onSelectTab,
            onCreateSheet = onCreateSheet,
            onImportSheet = onImportSheet,
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

@Composable
private fun HomeSheetsSectionHeader(
    uiState: HomeSheetsUiState,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateSheet: (String) -> Unit,
    onImportSheet: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = onCreateSheet,
        )
    }

    HomeSheetsHeader(
        uiState = uiState,
        onSelectTab = onSelectTab,
        onRequestCreate = { showCreateDialog = true },
        onImportSheet = onImportSheet,
    )
}
