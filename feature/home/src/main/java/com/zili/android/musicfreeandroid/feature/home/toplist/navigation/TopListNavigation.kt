package com.zili.android.musicfreeandroid.feature.home.toplist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.TopListRoute
import com.zili.android.musicfreeandroid.feature.home.toplist.TopListScreen

fun NavGraphBuilder.topListScreen(
    onBack: () -> Unit,
    onOpenTopListDetail: (pluginPlatform: String, topListId: String) -> Unit,
) {
    composable<TopListRoute> {
        TopListScreen(
            onBack = onBack,
            onOpenTopListDetail = onOpenTopListDetail,
        )
    }
}
