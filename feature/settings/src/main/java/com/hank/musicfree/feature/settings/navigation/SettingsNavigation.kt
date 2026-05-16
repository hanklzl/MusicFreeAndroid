package com.hank.musicfree.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.feature.settings.SettingsScreen

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToSetCustomTheme: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
) {
    composable<SettingsRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SettingsRoute>()
        SettingsScreen(
            type = route.type,
            onBack = onBack,
            onNavigateToPermissions = onNavigateToPermissions,
            onNavigateToFileSelector = onNavigateToFileSelector,
            onNavigateToLocalFileSelector = onNavigateToLocalFileSelector,
            onNavigateToSetCustomTheme = onNavigateToSetCustomTheme,
        )
    }
}
