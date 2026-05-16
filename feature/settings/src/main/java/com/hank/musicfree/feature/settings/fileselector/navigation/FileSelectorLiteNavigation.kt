package com.hank.musicfree.feature.settings.fileselector.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.FileSelectorRoute
import com.hank.musicfree.feature.settings.fileselector.FileSelectorLiteScreen

fun NavGraphBuilder.fileSelectorLiteScreen(
    onBack: () -> Unit,
) {
    composable<FileSelectorRoute> {
        FileSelectorLiteScreen(onBack = onBack)
    }
}
