package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.model.LyricDocument
import com.hank.musicfree.core.model.ParsedLyricLine

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
