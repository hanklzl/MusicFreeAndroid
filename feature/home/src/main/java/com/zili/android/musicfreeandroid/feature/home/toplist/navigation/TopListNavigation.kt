package com.zili.android.musicfreeandroid.feature.home.toplist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.TopListRoute
import com.zili.android.musicfreeandroid.feature.home.toplist.TopListScreen
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

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
