package com.zili.android.musicfreeandroid.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object PlayerRoute

@Serializable
data object SearchRoute

@Serializable
data object SettingsRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: String)

@Serializable
data object PlayQueueRoute
