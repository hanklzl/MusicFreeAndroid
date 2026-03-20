package com.zili.android.musicfreeandroid.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.feature.home.HomeScreen

fun NavGraphBuilder.homeScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateToPlayer = onNavigateToPlayer,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToTopList = onNavigateToTopList,
            onNavigateToPlaylistDetail = onNavigateToPlaylistDetail,
        )
    }
}
