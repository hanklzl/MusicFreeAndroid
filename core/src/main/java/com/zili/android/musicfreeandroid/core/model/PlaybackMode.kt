package com.zili.android.musicfreeandroid.core.model

enum class PlaybackMode {
    Shuffle,
    Single,
    Queue;

    fun next(): PlaybackMode = when (this) {
        Shuffle -> Single
        Single -> Queue
        Queue -> Shuffle
    }

    companion object {
        fun from(
            shuffleEnabled: Boolean,
            repeatMode: RepeatMode,
        ): PlaybackMode = when {
            shuffleEnabled -> Shuffle
            repeatMode == RepeatMode.ONE -> Single
            else -> Queue
        }
    }
}
