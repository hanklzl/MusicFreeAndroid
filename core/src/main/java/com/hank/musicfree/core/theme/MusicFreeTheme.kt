package com.hank.musicfree.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.core.theme.runtime.ThemeUiState
import com.hank.musicfree.core.ui.findActivity

@Composable
fun MusicFreeTheme(
    themeState: ThemeUiState,
    content: @Composable () -> Unit,
) {
    val isDark = themeState.selected != SelectedTheme.P_LIGHT
    val colors = themeState.effectiveColors
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.pageBackground,
            surface = colors.pageBackground,
            onPrimary = colors.appBarText,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.pageBackground,
            surface = colors.pageBackground,
            onPrimary = colors.appBarText,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    }

    // Drive system bar icon brightness from the app's own theme rather than the
    // device night-mode setting (enableEdgeToEdge auto). Without this, a dark app
    // theme on a light-mode device keeps dark status/nav icons that vanish against
    // the dark appBar/background. Reactive so runtime theme switches apply instantly.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            SideEffect {
                val controller = WindowInsetsControllerCompat(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalMusicFreeColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

// Kept for @Preview and call sites that have not migrated to ThemeUiState yet.
@Composable
fun MusicFreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkMusicFreeColors else LightMusicFreeColors
    MusicFreeTheme(
        themeState = ThemeUiState(
            selected = if (darkTheme) SelectedTheme.P_DARK else SelectedTheme.P_LIGHT,
            effectiveColors = colors,
            background = null,
            followSystem = false,
            isLoading = false,
        ),
        content = content,
    )
}

/**
 * Access custom MusicFree colors from any composable.
 * Usage: MusicFreeTheme.colors.primary
 */
object MusicFreeTheme {
    val colors: MusicFreeColors
        @Composable
        get() = LocalMusicFreeColors.current
}
