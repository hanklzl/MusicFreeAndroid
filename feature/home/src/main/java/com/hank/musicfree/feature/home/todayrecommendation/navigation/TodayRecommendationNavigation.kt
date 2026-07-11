package com.hank.musicfree.feature.home.todayrecommendation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.TodayRecommendationRoute
import com.hank.musicfree.feature.home.todayrecommendation.RecommendedSheet
import com.hank.musicfree.feature.home.todayrecommendation.TodayRecommendationScreen

fun NavGraphBuilder.todayRecommendationScreen(
    onBack: () -> Unit,
    onOpenSheetDetail: (RecommendedSheet) -> Unit,
    onOpenPluginList: () -> Unit,
) {
    composable<TodayRecommendationRoute> {
        TodayRecommendationScreen(
            onBack = onBack,
            onOpenSheetDetail = onOpenSheetDetail,
            onOpenPluginList = onOpenPluginList,
        )
    }
}
