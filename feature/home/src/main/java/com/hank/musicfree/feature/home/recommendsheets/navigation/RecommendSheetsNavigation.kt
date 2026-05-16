package com.hank.musicfree.feature.home.recommendsheets.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.RecommendSheetsRoute
import com.hank.musicfree.feature.home.recommendsheets.RecommendSheetsScreen
import com.hank.musicfree.plugin.api.MusicSheetItemBase

fun NavGraphBuilder.recommendSheetsScreen(
    onBack: () -> Unit,
    onOpenSheetDetail: (pluginPlatform: String, sheet: MusicSheetItemBase) -> Unit,
) {
    composable<RecommendSheetsRoute> {
        RecommendSheetsScreen(
            onBack = onBack,
            onOpenSheetDetail = onOpenSheetDetail,
        )
    }
}
