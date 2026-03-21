package com.zili.android.musicfreeandroid.feature.home.playlist

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PlaylistDetailRoute

fun NavGraphBuilder.playlistDetailScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearchMusicList: (String) -> Unit,
) {
    composable<PlaylistDetailRoute> {
        PlaylistDetailScreen(
            onBack = onBack,
            onNavigateToPlayer = onNavigateToPlayer,
            onNavigateToSearchMusicList = onNavigateToSearchMusicList,
        )
    }
}
