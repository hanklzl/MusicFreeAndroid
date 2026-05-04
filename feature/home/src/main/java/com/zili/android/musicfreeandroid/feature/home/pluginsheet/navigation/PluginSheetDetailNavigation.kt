package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.PluginSheetDetailScreen

fun NavGraphBuilder.pluginSheetDetailScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    composable<PluginSheetDetailRoute> {
        PluginSheetDetailScreen(
            onBack = onBack,
            onNavigateToPlayer = onNavigateToPlayer,
        )
    }
}
