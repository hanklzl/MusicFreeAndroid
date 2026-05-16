package com.hank.musicfree.feature.listenstats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.ListenDetailRoute
import com.hank.musicfree.core.navigation.ListenStatsRoute
import com.hank.musicfree.feature.listenstats.ListenDetailScreen
import com.hank.musicfree.feature.listenstats.ListenStatsScreen

fun NavGraphBuilder.listenStatsScreen(navController: NavHostController) {
    composable<ListenStatsRoute> {
        ListenStatsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToDetail = { mode, scope, anchorEpochDay, filterValue ->
                navController.navigate(
                    ListenDetailRoute(
                        mode = mode,
                        scope = scope,
                        anchorEpochDay = anchorEpochDay,
                        filterValue = filterValue,
                    ),
                )
            },
        )
    }
    composable<ListenDetailRoute> {
        ListenDetailScreen(onBack = { navController.popBackStack() })
    }
}
