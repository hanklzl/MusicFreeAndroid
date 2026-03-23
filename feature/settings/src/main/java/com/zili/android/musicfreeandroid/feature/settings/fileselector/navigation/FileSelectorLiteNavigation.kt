package com.zili.android.musicfreeandroid.feature.settings.fileselector.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.FileSelectorRoute
import com.zili.android.musicfreeandroid.feature.settings.fileselector.FileSelectorLiteScreen

fun NavGraphBuilder.fileSelectorLiteScreen(
    onBack: () -> Unit,
) {
    composable<FileSelectorRoute> {
        FileSelectorLiteScreen(onBack = onBack)
    }
}
