package com.zili.android.musicfreeandroid.feature.home.albumdetail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.AlbumDetailRoute
import com.zili.android.musicfreeandroid.feature.home.albumdetail.AlbumDetailScreen

fun NavGraphBuilder.albumDetailScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    composable<AlbumDetailRoute> {
        AlbumDetailScreen(
            onBack = onBack,
            onNavigateToPlayer = onNavigateToPlayer,
        )
    }
}
