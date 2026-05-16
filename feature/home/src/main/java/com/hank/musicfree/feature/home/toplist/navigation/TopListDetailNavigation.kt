package com.hank.musicfree.feature.home.toplist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.TopListDetailRoute
import com.hank.musicfree.feature.home.toplist.TopListDetailScreen

fun NavGraphBuilder.topListDetailScreen(
    onBack: () -> Unit,
) {
    composable<TopListDetailRoute> {
        TopListDetailScreen(
            onBack = onBack,
        )
    }
}
