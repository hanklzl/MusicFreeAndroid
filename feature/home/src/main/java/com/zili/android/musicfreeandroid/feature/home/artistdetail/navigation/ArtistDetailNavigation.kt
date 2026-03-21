package com.zili.android.musicfreeandroid.feature.home.artistdetail.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.ArtistDetailRoute
import com.zili.android.musicfreeandroid.feature.home.artistdetail.ArtistDetailScreen

fun NavGraphBuilder.artistDetailScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    composable<ArtistDetailRoute> {
        ArtistDetailScreen(
            onBack = onBack,
            onNavigateToPlayer = onNavigateToPlayer,
        )
    }
}
