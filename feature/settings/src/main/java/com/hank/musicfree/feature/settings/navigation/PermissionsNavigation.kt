package com.hank.musicfree.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PermissionsRoute
import com.hank.musicfree.feature.settings.PermissionsScreen

fun NavGraphBuilder.permissionsScreen(
    onBack: () -> Unit,
) {
    composable<PermissionsRoute> {
        PermissionsScreen(onBack = onBack)
    }
}
