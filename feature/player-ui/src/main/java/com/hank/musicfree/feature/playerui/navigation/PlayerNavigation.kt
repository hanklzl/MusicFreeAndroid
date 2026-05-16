package com.hank.musicfree.feature.playerui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PlayerRoute
import com.hank.musicfree.feature.playerui.PlayerScreen

fun NavGraphBuilder.playerScreen(
    onBack: () -> Unit,
) {
    composable<PlayerRoute> {
        PlayerScreen(onBack = onBack)
    }
}
