package com.zili.android.musicfreeandroid.feature.playerui.component

data class MiniPlayerUiModel(
    val coverUri: String?,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val progress: Float,
    // Reserved for swipe gesture (Task 6): used by MiniPlayerContent swipe-to-skip
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val prevTitle: String?,
    val nextTitle: String?,
)
