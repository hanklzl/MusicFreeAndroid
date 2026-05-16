package com.zili.android.musicfreeandroid.core.theme.runtime

import com.zili.android.musicfreeandroid.core.theme.MusicFreeColors

/**
 * Snapshot consumed by [com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme].
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
