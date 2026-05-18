package com.hank.musicfree.feature.settings.traffic.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.TrafficStatsRoute
import com.hank.musicfree.feature.settings.traffic.TrafficStatsScreen

fun NavGraphBuilder.trafficStatsRoute(onBack: () -> Unit) {
    composable<TrafficStatsRoute> { TrafficStatsScreen(onBack = onBack) }
}
