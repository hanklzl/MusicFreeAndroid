package com.hank.musicfree.core.runtime

import kotlinx.serialization.Serializable

@Serializable
data class UiRuntimeSnapshot(
    val homeTab: String? = null,
    val searchTab: String? = null,
    val playerView: String? = null,
)

enum class PlayerView {
    COVER,
    LYRIC,
}

data class UiRuntimeState(
    val homeTab: String? = null,
    val searchTab: String? = null,
    val playerView: PlayerView = PlayerView.COVER,
    val restoreAttempted: Boolean = false,
    val restored: Boolean = false,
    val lastFailureReason: String? = null,
)
