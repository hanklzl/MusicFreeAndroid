package com.hank.musicfree.player.queue

import com.hank.musicfree.core.model.MusicItem

data class PlayQueueSnapshot(
    val items: List<MusicItem>,
    val currentIndex: Int,
) {
    companion object {
        val EMPTY = PlayQueueSnapshot(items = emptyList(), currentIndex = -1)
    }
}
