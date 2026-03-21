package com.zili.android.musicfreeandroid.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object PlayerRoute

@Serializable
data object SearchRoute

@Serializable
data object HistoryRoute

@Serializable
data class SearchMusicListRoute(
    val sourceType: String,
    val playlistId: String? = null,
) {
    init {
        require(sourceType == SOURCE_TYPE_PLAYLIST || sourceType == SOURCE_TYPE_HISTORY) {
            "Unsupported search music list source type: $sourceType"
        }
        require(sourceType != SOURCE_TYPE_PLAYLIST || !playlistId.isNullOrBlank()) {
            "playlistId is required for playlist search music list routes"
        }
        require(sourceType != SOURCE_TYPE_HISTORY || playlistId == null) {
            "playlistId must be null for history search music list routes"
        }
    }

    companion object {
        const val SOURCE_TYPE_PLAYLIST = "playlist"
        const val SOURCE_TYPE_HISTORY = "history"

        fun playlist(playlistId: String): SearchMusicListRoute = SearchMusicListRoute(
            sourceType = SOURCE_TYPE_PLAYLIST,
            playlistId = playlistId,
        )

        fun history(): SearchMusicListRoute = SearchMusicListRoute(
            sourceType = SOURCE_TYPE_HISTORY,
        )
    }
}

@Serializable
sealed interface SearchMusicListSource {
    @Serializable
    data class Playlist(val playlistId: String) : SearchMusicListSource

    @Serializable
    data object History : SearchMusicListSource
}

@Serializable
data object SettingsRoute

@Serializable
data object PermissionsRoute

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

@Serializable
data class MusicDetailRoute(
    val pluginPlatform: String,
    val musicId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String? = null,
    val durationMs: Long = 0L,
)

@Serializable
data class AlbumDetailRoute(
    val pluginPlatform: String,
    val albumId: String,
    val title: String? = null,
    val artist: String? = null,
    val artwork: String? = null,
)

@Serializable
data class ArtistDetailRoute(
    val pluginPlatform: String,
    val artistId: String,
    val name: String,
    val avatar: String? = null,
)
