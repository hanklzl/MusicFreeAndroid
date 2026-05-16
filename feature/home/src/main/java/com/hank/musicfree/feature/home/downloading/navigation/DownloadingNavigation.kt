package com.hank.musicfree.feature.home.downloading.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.DownloadingRoute
import com.hank.musicfree.feature.home.downloading.DownloadingScreen

fun NavGraphBuilder.downloadingScreen(onBack: () -> Unit) {
    composable<DownloadingRoute> {
        DownloadingScreen(onBack = onBack)
    }
}
