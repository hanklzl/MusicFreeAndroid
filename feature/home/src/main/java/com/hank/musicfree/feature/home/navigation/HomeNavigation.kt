package com.hank.musicfree.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.HomeRoute
import com.hank.musicfree.core.navigation.SettingsType
import com.hank.musicfree.feature.home.HomeScreen
import com.hank.musicfree.feature.home.sheets.HomeSheetUiModel

fun NavGraphBuilder.homeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToListenStats: () -> Unit,
    onNavigateToTrafficStats: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onNavigateToSettings: (SettingsType) -> Unit,
    onNavigateToPluginList: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    onNavigateToStarredSheet: (HomeSheetUiModel) -> Unit,
    onNavigateToStarredAlbum: (HomeSheetUiModel) -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToRecommendSheets = onNavigateToRecommendSheets,
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToListenStats = onNavigateToListenStats,
            onNavigateToTrafficStats = onNavigateToTrafficStats,
            onNavigateToLocal = onNavigateToLocal,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPluginList = onNavigateToPluginList,
            onNavigateToPermissions = onNavigateToPermissions,
            onNavigateToTopList = onNavigateToTopList,
            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
            onNavigateToStarredSheet = onNavigateToStarredSheet,
            onNavigateToStarredAlbum = onNavigateToStarredAlbum,
        )
    }
}
