package com.zili.android.musicfreeandroid.feature.home.local.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.LocalRoute
import com.zili.android.musicfreeandroid.feature.home.local.LocalScreen

fun NavGraphBuilder.localScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
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
            onNavigateToPlayer = onNavigateToPlayer,
        )
    }
}
