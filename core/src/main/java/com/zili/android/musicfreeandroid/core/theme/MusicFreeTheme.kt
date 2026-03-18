package com.zili.android.musicfreeandroid.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = LightMusicFreeColors.primary,
    background = LightMusicFreeColors.pageBackground,
    surface = LightMusicFreeColors.pageBackground,
    onPrimary = LightMusicFreeColors.appBarText,
    onBackground = LightMusicFreeColors.text,
    onSurface = LightMusicFreeColors.text,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkMusicFreeColors.primary,
    background = DarkMusicFreeColors.pageBackground,
    surface = DarkMusicFreeColors.pageBackground,
    onPrimary = DarkMusicFreeColors.appBarText,
    onBackground = DarkMusicFreeColors.text,
    onSurface = DarkMusicFreeColors.text,
)

@Composable
fun MusicFreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val musicFreeColors = if (darkTheme) DarkMusicFreeColors else LightMusicFreeColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalMusicFreeColors provides musicFreeColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
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
