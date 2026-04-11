package com.zili.android.musicfreeandroid.feature.playerui.component

data class MiniPlayerUiModel(
    val coverUri: String?,
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val showQueueButton: Boolean,
)
