package com.hank.musicfree.feature.settings.setcustomtheme.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.SetCustomThemeRoute
import com.hank.musicfree.feature.settings.setcustomtheme.SetCustomThemeScreen

fun NavGraphBuilder.setCustomThemeScreen(onBack: () -> Unit) {
    composable<SetCustomThemeRoute> {
        SetCustomThemeScreen(onBack = onBack)
    }
}
