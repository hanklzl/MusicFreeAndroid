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

@Serializable
data object TopListRoute

@Serializable
data class TopListDetailRoute(
    val pluginPlatform: String,
    val topListId: String,
)

@Serializable
data object RecommendSheetsRoute

@Serializable
data class PluginSheetDetailRoute(
    val pluginPlatform: String,
    val sheetId: String,
    val title: String? = null,
    val artist: String? = null,
    val coverImg: String? = null,
    val artwork: String? = null,
)
