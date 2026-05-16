package com.hank.musicfree.feature.home.history.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.HistoryRoute
import com.hank.musicfree.feature.home.history.HistoryScreen

fun NavGraphBuilder.historyScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: () -> Unit,
) {
    composable<HistoryRoute> {
        HistoryScreen(
            onBack = onBack,
            onNavigateToSearchMusicList = onNavigateToSearchMusicList,
        )
    }
}
