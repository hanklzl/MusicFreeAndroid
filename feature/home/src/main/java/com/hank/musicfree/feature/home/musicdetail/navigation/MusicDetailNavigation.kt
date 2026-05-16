package com.hank.musicfree.feature.home.musicdetail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.navigation.MusicDetailRoute
import com.hank.musicfree.feature.home.musicdetail.MusicDetailScreen

fun NavGraphBuilder.musicDetailScreen(
    onBack: () -> Unit,
    onOpenAlbumDetail: (MusicItem) -> Unit,
    onOpenArtistDetail: (MusicItem) -> Unit,
) {
    composable<MusicDetailRoute> {
        MusicDetailScreen(
            onBack = onBack,
            onOpenAlbumDetail = onOpenAlbumDetail,
            onOpenArtistDetail = onOpenArtistDetail,
        )
    }
}
