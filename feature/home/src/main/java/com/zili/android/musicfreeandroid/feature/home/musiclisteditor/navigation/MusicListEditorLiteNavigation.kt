package com.zili.android.musicfreeandroid.feature.home.musiclisteditor.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.MusicListEditorLiteRoute
import com.zili.android.musicfreeandroid.feature.home.musiclisteditor.MusicListEditorLiteScreen

fun NavGraphBuilder.musicListEditorLiteScreen(
    onBack: () -> Unit,
) {
    composable<MusicListEditorLiteRoute> {
        MusicListEditorLiteScreen(onBack = onBack)
    }
}
