package com.zili.android.musicfreeandroid.feature.home.musicdetail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.feature.home.musicdetail.MusicDetailScreen

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
