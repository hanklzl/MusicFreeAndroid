package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode

data class PlayQueueUiModel(
    val items: List<MusicItem>,
    val currentIndex: Int,
    val repeatMode: RepeatMode,
) {
    val count: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val EMPTY = PlayQueueUiModel(
            items = emptyList(),
            currentIndex = -1,
            repeatMode = RepeatMode.OFF,
        )
    }
}
