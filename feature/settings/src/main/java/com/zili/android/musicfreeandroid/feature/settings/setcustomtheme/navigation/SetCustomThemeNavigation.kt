package com.zili.android.musicfreeandroid.feature.settings.setcustomtheme.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.SetCustomThemeRoute
import com.zili.android.musicfreeandroid.feature.settings.setcustomtheme.SetCustomThemeScreen

fun NavGraphBuilder.setCustomThemeScreen(onBack: () -> Unit) {
    composable<SetCustomThemeRoute> {
        SetCustomThemeScreen(onBack = onBack)
    }
}
