package com.zili.android.musicfreeandroid.feature.settings.pluginsub.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PluginSubscriptionRoute
import com.zili.android.musicfreeandroid.feature.settings.pluginsub.PluginSubscriptionScreen

fun NavGraphBuilder.pluginSubscriptionScreen(
    onBack: () -> Unit,
) {
    composable<PluginSubscriptionRoute> {
        PluginSubscriptionScreen(onBack = onBack)
    }
}
