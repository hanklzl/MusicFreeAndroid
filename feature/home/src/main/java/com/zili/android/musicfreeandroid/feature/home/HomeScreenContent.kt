package com.zili.android.musicfreeandroid.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeStatusBarChrome
import com.zili.android.musicfreeandroid.feature.home.component.HomeDrawerContent
import com.zili.android.musicfreeandroid.feature.home.component.HomeDrawerDialogs
import com.zili.android.musicfreeandroid.feature.home.component.HomeNavBar
import com.zili.android.musicfreeandroid.feature.home.component.HomeOperations
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModel
import com.zili.android.musicfreeandroid.feature.home.sheets.homeSheetsSection

internal fun handleDrawerEntryClick(
    state: HomeScreenState,
    action: HomeDrawerAction,
    onDrawerEntryClick: (HomeDrawerAction) -> Unit,
) {
    state.closeDrawer()
    when (action) {
        HomeDrawerAction.ShowScheduleClosePanel -> state.showTimingCloseDialog()
        HomeDrawerAction.ShowLanguageDialog -> state.showLanguageDialog()
        HomeDrawerAction.ShowUpdateCheckDialog -> state.showUpdateCheck()
        else -> onDrawerEntryClick(action)
    }
}

@Composable
fun HomeScreenContent(
    state: HomeScreenState,
    visualUiModel: HomeVisualUiModel,
    drawerUiModel: HomeDrawerUiModel,
    currentLanguage: String,
    currentVersion: String,
    scheduleCloseSummary: String,
    onDrawerEntryClick: (HomeDrawerAction) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
) {
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed,
        confirmStateChange = { value ->
            when (value) {
                DrawerValue.Open -> state.openDrawer()
                DrawerValue.Closed -> state.closeDrawer()
            }
            true
        },
    )

    LaunchedEffect(state.isDrawerOpen) {
        when {
            state.isDrawerOpen && drawerState.currentValue != DrawerValue.Open -> drawerState.open()
            !state.isDrawerOpen && drawerState.currentValue != DrawerValue.Closed -> drawerState.close()
        }
    }

    BackHandler(
        enabled = state.isDrawerOpen ||
            state.isTimingCloseVisible ||
            state.isLanguageDialogVisible ||
            state.isUpdateCheckVisible,
    ) {
        state.onBackPressedConsumed()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HomeDrawerContent(
                uiModel = drawerUiModel,
                onEntryClick = { action ->
                    handleDrawerEntryClick(
                        state = state,
                        action = action,
                        onDrawerEntryClick = onDrawerEntryClick,
                    )
                },
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(FidelityAnchors.Screen.HomeRoot)
                .semantics { testTagsAsResourceId = true },
            contentPadding = PaddingValues(bottom = rpx(160)),
        ) {
            item {
                MusicFreeStatusBarChrome(color = Color.Transparent)
            }
            item {
                HomeNavBar(
                    searchPlaceholder = visualUiModel.searchPlaceholder,
                    onOpenMenu = state::openDrawer,
                    onOpenSearch = onNavigateToSearch,
                )
            }
            item {
                HomeOperations(
                    operations = visualUiModel.operations,
                    onRecommendClick = onNavigateToRecommendSheets,
                    onTopListClick = onNavigateToTopList,
                    onHistoryClick = onNavigateToHistory,
                    onLocalMusicClick = onNavigateToLocal,
                )
            }
            homeSheetsSection(
                uiModel = visualUiModel.playlistSection,
                onSelectTab = onSelectTab,
                onCreateClick = onCreateClick,
                onImportClick = onImportClick,
                onOpenMineSheet = onOpenMineSheet,
                onOpenStarredSheet = onOpenStarredSheet,
            )
        }
    }

    HomeDrawerDialogs(
        isTimingCloseVisible = state.isTimingCloseVisible,
        isLanguageDialogVisible = state.isLanguageDialogVisible,
        isUpdateCheckVisible = state.isUpdateCheckVisible,
        currentLanguage = currentLanguage,
        currentVersion = currentVersion,
        scheduleCloseSummary = scheduleCloseSummary,
        onDismissTimingClose = state::dismissTimingCloseDialog,
        onDismissLanguage = state::dismissLanguageDialog,
        onDismissUpdateCheck = state::dismissUpdateCheck,
    )
}
