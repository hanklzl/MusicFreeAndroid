package com.zili.android.musicfreeandroid.feature.home.recommendsheets.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.RecommendSheetsRoute
import com.zili.android.musicfreeandroid.feature.home.recommendsheets.RecommendSheetsScreen
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

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
