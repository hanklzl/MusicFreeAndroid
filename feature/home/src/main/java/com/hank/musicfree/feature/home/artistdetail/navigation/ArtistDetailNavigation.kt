package com.hank.musicfree.feature.home.artistdetail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.ArtistDetailRoute
import com.hank.musicfree.feature.home.artistdetail.ArtistDetailScreen

fun NavGraphBuilder.artistDetailScreen(
    onBack: () -> Unit,
) {
    composable<ArtistDetailRoute> {
        ArtistDetailScreen(
            onBack = onBack,
        )
    }
}
