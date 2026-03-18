package com.zili.android.musicfreeandroid.feature.playerui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.feature.playerui.PlayerScreen

fun NavGraphBuilder.playerScreen(
    onBack: () -> Unit,
) {
    composable<PlayerRoute> {
        PlayerScreen(onBack = onBack)
    }
}
