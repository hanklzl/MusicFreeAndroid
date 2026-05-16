package com.zili.android.musicfreeandroid.core.theme.runtime

import kotlinx.coroutines.flow.Flow

/**
 * Authoritative source of theme state. The concrete implementation lives in
 * `:data` so `:core` stays free of DataStore / Hilt dependencies.
 */
interface ThemeRepository {
    val state: Flow<ThemeUiState>

    suspend fun selectTheme(theme: SelectedTheme)

    /**
     * Toggles "follow system" mode. When [enabled] is true the implementation
     * must also persist a [SelectedTheme] consistent with [currentSystemDark]
     * so a relaunch into followSystem=true keeps colours stable.
     */
    suspend fun setFollowSystem(enabled: Boolean, currentSystemDark: Boolean)

    /**
     * Patches background image fields. Null parameters are treated as
     * "no change" so callers can update one knob at a time (e.g. blur only).
     */
    suspend fun setBackground(url: String?, blur: Float?, opacity: Float?)

    /** Merges [patch] into the persisted custom colour map. */
    suspend fun patchCustomColors(patch: Map<String, String>)

    /** Replaces the entire custom colour map (used after palette extraction). */
    suspend fun replaceCustomColors(colors: Map<String, String>)
}
