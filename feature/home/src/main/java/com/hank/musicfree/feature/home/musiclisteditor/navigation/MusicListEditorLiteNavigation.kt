package com.hank.musicfree.feature.home.musiclisteditor.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.MusicListEditorLiteRoute
import com.hank.musicfree.feature.home.musiclisteditor.MusicListEditorLiteScreen

fun NavGraphBuilder.musicListEditorLiteScreen(
    onBack: () -> Unit,
) {
    composable<MusicListEditorLiteRoute> {
        MusicListEditorLiteScreen(onBack = onBack)
    }
}
