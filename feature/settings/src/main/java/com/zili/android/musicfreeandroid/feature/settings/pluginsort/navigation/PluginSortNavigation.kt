package com.zili.android.musicfreeandroid.feature.settings.pluginsort.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PluginSortRoute
import com.zili.android.musicfreeandroid.feature.settings.pluginsort.PluginSortScreen

fun NavGraphBuilder.pluginSortScreen(
    onBack: () -> Unit,
) {
    composable<PluginSortRoute> {
        PluginSortScreen(onBack = onBack)
    }
}
