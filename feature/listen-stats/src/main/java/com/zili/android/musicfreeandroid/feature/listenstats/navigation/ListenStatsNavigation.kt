package com.zili.android.musicfreeandroid.feature.listenstats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.ListenDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.ListenStatsRoute
import com.zili.android.musicfreeandroid.feature.listenstats.ListenDetailScreen
import com.zili.android.musicfreeandroid.feature.listenstats.ListenStatsScreen

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
