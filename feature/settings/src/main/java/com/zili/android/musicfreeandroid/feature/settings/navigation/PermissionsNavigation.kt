package com.zili.android.musicfreeandroid.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PermissionsRoute
import com.zili.android.musicfreeandroid.feature.settings.PermissionsScreen

fun NavGraphBuilder.permissionsScreen(
    onBack: () -> Unit,
) {
    composable<PermissionsRoute> {
        PermissionsScreen(onBack = onBack)
    }
}
