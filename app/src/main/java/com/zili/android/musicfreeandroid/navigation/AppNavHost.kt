package com.zili.android.musicfreeandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.PlaylistDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.feature.home.navigation.homeScreen
import com.zili.android.musicfreeandroid.feature.home.playlist.playlistDetailScreen
import com.zili.android.musicfreeandroid.feature.playerui.navigation.playerScreen
import com.zili.android.musicfreeandroid.feature.search.navigation.searchScreen
import com.zili.android.musicfreeandroid.feature.settings.navigation.settingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        homeScreen(
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onNavigateToSearch = { navController.navigate(SearchRoute) },
            onNavigateToSettings = { navController.navigate(SettingsRoute) },
            onNavigateToPlaylistDetail = { playlistId ->
                navController.navigate(PlaylistDetailRoute(playlistId))
            },
        )
        playerScreen(
            onBack = { navController.popBackStack() },
        )
        playlistDetailScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
        )
        searchScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
        )
        settingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
