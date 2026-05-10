package com.zili.android.musicfreeandroid.player.model

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlaybackSpeeds
import com.zili.android.musicfreeandroid.core.model.RepeatMode

data class PlayerState(
    val currentItem: MusicItem? = null,
    val isPlaying: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val duration: Long = 0L,
    val position: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeeds.DEFAULT,
) {
    val hasMedia: Boolean get() = currentItem != null

    companion object {
        val EMPTY = PlayerState()
    }
}
