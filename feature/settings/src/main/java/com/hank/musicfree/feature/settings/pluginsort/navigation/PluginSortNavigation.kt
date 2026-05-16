package com.hank.musicfree.feature.settings.pluginsort.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PluginSortRoute
import com.hank.musicfree.feature.settings.pluginsort.PluginSortScreen

fun NavGraphBuilder.pluginSortScreen(
    onBack: () -> Unit,
) {
    composable<PluginSortRoute> {
        PluginSortScreen(onBack = onBack)
    }
}
