package com.hank.musicfree.feature.home.playlist

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PlaylistDetailRoute

fun NavGraphBuilder.playlistDetailScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: (String) -> Unit,
    onNavigateToMusicListEditorLite: (String) -> Unit,
) {
    composable<PlaylistDetailRoute> {
        PlaylistDetailScreen(
            onBack = onBack,
            onNavigateToSearchMusicList = onNavigateToSearchMusicList,
            onNavigateToMusicListEditorLite = onNavigateToMusicListEditorLite,
        )
    }
}
