package com.hank.musicfree.feature.home.albumdetail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.AlbumDetailRoute
import com.hank.musicfree.feature.home.albumdetail.AlbumDetailScreen

fun NavGraphBuilder.albumDetailScreen(
    onBack: () -> Unit,
) {
    composable<AlbumDetailRoute> {
        AlbumDetailScreen(
            onBack = onBack,
        )
    }
}
