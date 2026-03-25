package com.zili.android.musicfreeandroid.feature.home.local.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.LocalRoute
import com.zili.android.musicfreeandroid.feature.home.local.LocalScreen

fun NavGraphBuilder.localScreen(
    onNavigateToPlayer: () -> Unit,
) {
    composable<LocalRoute> {
        LocalScreen(onNavigateToPlayer = onNavigateToPlayer)
    }
}
