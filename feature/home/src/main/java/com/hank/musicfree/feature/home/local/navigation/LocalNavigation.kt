package com.hank.musicfree.feature.home.local.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.LocalRoute
import com.hank.musicfree.feature.home.local.LocalScreen

fun NavGraphBuilder.localScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: () -> Unit,
    onNavigateToMusicListEditor: () -> Unit,
    onNavigateToDownloading: () -> Unit,
) {
    composable<LocalRoute> {
        LocalScreen(
            onBack = onBack,
            onNavigateToSearchMusicList = onNavigateToSearchMusicList,
            onNavigateToMusicListEditor = onNavigateToMusicListEditor,
            onNavigateToDownloading = onNavigateToDownloading,
        )
    }
}
