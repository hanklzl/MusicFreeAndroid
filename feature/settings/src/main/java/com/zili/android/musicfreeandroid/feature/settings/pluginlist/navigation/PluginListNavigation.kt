package com.zili.android.musicfreeandroid.feature.settings.pluginlist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PluginListRoute
import com.zili.android.musicfreeandroid.feature.settings.pluginlist.PluginListScreen

fun NavGraphBuilder.pluginListScreen(
    onBack: () -> Unit,
    onNavigateToPluginSort: () -> Unit,
    onNavigateToPluginSubscription: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
) {
    composable<PluginListRoute> {
        PluginListScreen(
            onBack = onBack,
            onNavigateToPluginSort = onNavigateToPluginSort,
            onNavigateToPluginSubscription = onNavigateToPluginSubscription,
            onNavigateToFileSelector = onNavigateToFileSelector,
        )
    }
}
