package com.zili.android.musicfreeandroid.feature.home.downloading.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.DownloadingRoute
import com.zili.android.musicfreeandroid.feature.home.downloading.DownloadingScreen

fun NavGraphBuilder.downloadingScreen(onBack: () -> Unit) {
    composable<DownloadingRoute> {
        DownloadingScreen(onBack = onBack)
    }
}
