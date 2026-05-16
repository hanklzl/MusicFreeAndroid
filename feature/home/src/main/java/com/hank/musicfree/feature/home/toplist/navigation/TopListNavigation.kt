package com.hank.musicfree.feature.home.toplist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.TopListRoute
import com.hank.musicfree.feature.home.toplist.TopListScreen
import com.hank.musicfree.plugin.api.MusicSheetItemBase

fun NavGraphBuilder.topListScreen(
    onBack: () -> Unit,
    onOpenTopListDetail: (pluginPlatform: String, topList: MusicSheetItemBase) -> Unit,
) {
    composable<TopListRoute> {
        TopListScreen(
            onBack = onBack,
            onOpenTopListDetail = onOpenTopListDetail,
        )
    }
}
