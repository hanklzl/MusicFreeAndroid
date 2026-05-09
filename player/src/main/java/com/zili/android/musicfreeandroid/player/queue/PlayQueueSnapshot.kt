package com.zili.android.musicfreeandroid.player.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem

data class PlayQueueSnapshot(
    val items: List<MusicItem>,
    val currentIndex: Int,
) {
    companion object {
        val EMPTY = PlayQueueSnapshot(items = emptyList(), currentIndex = -1)
    }
}
