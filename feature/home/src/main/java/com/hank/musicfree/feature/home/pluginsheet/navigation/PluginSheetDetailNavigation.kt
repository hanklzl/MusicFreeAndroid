package com.hank.musicfree.feature.home.pluginsheet.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PluginSheetDetailRoute
import com.hank.musicfree.feature.home.pluginsheet.PluginSheetDetailScreen

fun NavGraphBuilder.pluginSheetDetailScreen(
    onBack: () -> Unit,
) {
    composable<PluginSheetDetailRoute> {
        PluginSheetDetailScreen(
            onBack = onBack,
        )
    }
}
