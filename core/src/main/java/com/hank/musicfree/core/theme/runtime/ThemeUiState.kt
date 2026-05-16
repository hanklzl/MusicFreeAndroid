package com.hank.musicfree.core.theme.runtime

import com.hank.musicfree.core.theme.MusicFreeColors

/**
 * Snapshot consumed by [com.hank.musicfree.core.theme.MusicFreeTheme].
 * UI never inspects raw preference values — it reads [effectiveColors] which has
 * `followSystem` + `customColors` already merged.
 */
data class ThemeUiState(
    val selected: SelectedTheme,
    val effectiveColors: MusicFreeColors,
    val background: BackgroundInfo?,
    val followSystem: Boolean,
    val isLoading: Boolean,
)

data class BackgroundInfo(
    val url: String?,
    val blur: Float,
    val opacity: Float,
)
