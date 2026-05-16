package com.zili.android.musicfreeandroid.data.repository.theme

import com.zili.android.musicfreeandroid.core.theme.DarkMusicFreeColors
import com.zili.android.musicfreeandroid.core.theme.LightMusicFreeColors
import com.zili.android.musicfreeandroid.core.theme.runtime.BackgroundInfo
import com.zili.android.musicfreeandroid.core.theme.runtime.SelectedTheme
import com.zili.android.musicfreeandroid.core.theme.runtime.ThemeRepository
import com.zili.android.musicfreeandroid.core.theme.runtime.ThemeUiState
import com.zili.android.musicfreeandroid.core.theme.runtime.applyOverrides
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [ThemeRepository] backed by [AppPreferences]. The combined [state]
 * Flow is the single source of truth consumed by `MusicFreeTheme` and the
 * settings UI.
 *
 * Notes:
 * - When `selected == CUSTOM`, the custom color map is overlaid on top of the
 *   dark palette (the RN parity baseline). When light/dark is selected the
 *   override map is ignored so toggling back to a preset always restores it.
 * - File-existence for `background.url` is *not* validated here — the UI uses
 *   `AsyncImage` whose error fallback handles missing files. Validating here
 *   would touch IO inside the Flow collector and slow recomposition.
 */
@Singleton
class DefaultThemeRepository @Inject constructor(
    private val prefs: AppPreferences,
) : ThemeRepository {

    override val state: Flow<ThemeUiState> = combine(
        prefs.selectedTheme,
        prefs.customColorsJson.map { ThemeColorsJson.decode(it) },
        prefs.themeBackgroundUrl,
        prefs.themeBackgroundBlur,
        prefs.themeBackgroundOpacity,
        prefs.themeFollowSystem,
    ) { values ->
        val selected = values[0] as SelectedTheme
        @Suppress("UNCHECKED_CAST")
        val customColors = values[1] as Map<String, String>
        val bgUrl = values[2] as String?
        val bgBlur = values[3] as Float
        val bgOpacity = values[4] as Float
        val followSystem = values[5] as Boolean

        val base = when (selected) {
            SelectedTheme.P_LIGHT -> LightMusicFreeColors
            SelectedTheme.P_DARK -> DarkMusicFreeColors
            SelectedTheme.CUSTOM -> DarkMusicFreeColors
        }
        val effective = if (selected == SelectedTheme.CUSTOM) {
            applyOverrides(base, customColors)
        } else {
            base
        }
        val background = if (selected == SelectedTheme.CUSTOM) {
            BackgroundInfo(url = bgUrl, blur = bgBlur, opacity = bgOpacity)
        } else {
            null
        }
        ThemeUiState(
            selected = selected,
            effectiveColors = effective,
            background = background,
            followSystem = followSystem,
            isLoading = false,
        )
    }

    override suspend fun selectTheme(theme: SelectedTheme) {
        prefs.setSelectedTheme(theme)
    }

    override suspend fun setFollowSystem(enabled: Boolean, currentSystemDark: Boolean) {
        prefs.setThemeFollowSystem(enabled)
        if (enabled) {
            prefs.setSelectedTheme(
                if (currentSystemDark) SelectedTheme.P_DARK else SelectedTheme.P_LIGHT
            )
        }
    }

    override suspend fun setBackground(url: String?, blur: Float?, opacity: Float?) {
        if (url != null) prefs.setThemeBackgroundUrl(url)
        if (blur != null) prefs.setThemeBackgroundBlur(blur)
        if (opacity != null) prefs.setThemeBackgroundOpacity(opacity)
    }

    override suspend fun patchCustomColors(patch: Map<String, String>) {
        if (patch.isEmpty()) return
        val current = ThemeColorsJson.decode(prefs.customColorsJson.first()).toMutableMap()
        current.putAll(patch)
        prefs.setCustomColorsJson(ThemeColorsJson.encode(current))
    }

    override suspend fun replaceCustomColors(colors: Map<String, String>) {
        prefs.setCustomColorsJson(ThemeColorsJson.encode(colors))
    }
}
