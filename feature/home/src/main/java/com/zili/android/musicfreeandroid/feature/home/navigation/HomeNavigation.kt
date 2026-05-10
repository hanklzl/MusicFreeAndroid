package com.zili.android.musicfreeandroid.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
import com.zili.android.musicfreeandroid.feature.home.HomeScreen
import com.zili.android.musicfreeandroid.feature.home.HomeSystemActionHandler
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModel

fun NavGraphBuilder.homeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onNavigateToSettings: (SettingsType) -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    onNavigateToStarredSheet: (HomeSheetUiModel) -> Unit,
    onNavigateToStarredAlbum: (HomeSheetUiModel) -> Unit,
    homeSystemActionHandler: HomeSystemActionHandler,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToRecommendSheets = onNavigateToRecommendSheets,
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToLocal = onNavigateToLocal,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPermissions = onNavigateToPermissions,
            onNavigateToTopList = onNavigateToTopList,
            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
            onNavigateToStarredSheet = onNavigateToStarredSheet,
            onNavigateToStarredAlbum = onNavigateToStarredAlbum,
            homeSystemActionHandler = homeSystemActionHandler,
        )
    }
}
