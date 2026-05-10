package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine

data class PlayerLyricsUiState(
    val loadState: LyricLoadState = LyricLoadState.NoTrack,
    val document: LyricDocument? = null,
    val currentLineIndex: Int? = null,
    val showTranslation: Boolean = false,
    val fontSizeLevel: Int = 1,
    val userOffsetMs: Long = 0L,
    val manualSeekPreviewLine: ParsedLyricLine? = null,
) {
    val hasLyrics: Boolean get() = document?.lines?.isNotEmpty() == true
    val hasTranslation: Boolean get() = document?.hasTranslation == true
}
