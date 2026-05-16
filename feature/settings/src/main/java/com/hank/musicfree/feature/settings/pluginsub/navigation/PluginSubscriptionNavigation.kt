package com.hank.musicfree.feature.settings.pluginsub.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PluginSubscriptionRoute
import com.hank.musicfree.feature.settings.pluginsub.PluginSubscriptionScreen

fun NavGraphBuilder.pluginSubscriptionScreen(
    onBack: () -> Unit,
) {
    composable<PluginSubscriptionRoute> {
        PluginSubscriptionScreen(onBack = onBack)
    }
}
