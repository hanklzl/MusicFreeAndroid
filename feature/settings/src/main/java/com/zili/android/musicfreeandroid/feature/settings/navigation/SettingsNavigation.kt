package com.zili.android.musicfreeandroid.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.feature.settings.SettingsScreen

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = onBack,
            onNavigateToPermissions = onNavigateToPermissions,
            onNavigateToFileSelector = onNavigateToFileSelector,
        )
    }
}
