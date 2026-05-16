package com.hank.musicfree.feature.playerui.component.queue

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlaybackMode

data class PlayQueueUiModel(
    val items: List<MusicItem>,
    val currentIndex: Int,
    val playbackMode: PlaybackMode,
) {
    val count: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val EMPTY = PlayQueueUiModel(
            items = emptyList(),
            currentIndex = -1,
            playbackMode = PlaybackMode.Queue,
        )
    }
}
