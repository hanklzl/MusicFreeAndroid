package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.model.LyricDocument
import com.hank.musicfree.core.model.MusicItem

sealed interface LyricLoadState {
    data object NoTrack : LyricLoadState

    data class Loading(val music: MusicItem) : LyricLoadState

    data class Ready(
        val music: MusicItem,
        val document: LyricDocument,
        val userOffsetMs: Long,
    ) : LyricLoadState

    data class NoLyric(val music: MusicItem) : LyricLoadState

    data class Error(val music: MusicItem, val message: String) : LyricLoadState
}
