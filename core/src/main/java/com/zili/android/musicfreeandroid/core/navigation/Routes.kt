package com.zili.android.musicfreeandroid.core.navigation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("playlistId")
    val sourceId: String? = null,
) {
    init {
        require(
            sourceType == SOURCE_TYPE_PLAYLIST ||
                sourceType == SOURCE_TYPE_HISTORY ||
                sourceType == SOURCE_TYPE_LOCAL_LIBRARY ||
                sourceType == SOURCE_TYPE_TRANSIENT,
        ) {
            "Unsupported search music list source type: $sourceType"
        }
        require(sourceType != SOURCE_TYPE_PLAYLIST || !sourceId.isNullOrBlank()) {
            "sourceId is required for playlist search music list routes"
        }
        require(sourceType != SOURCE_TYPE_HISTORY || sourceId == null) {
            "sourceId must be null for history search music list routes"
        }
        require(sourceType != SOURCE_TYPE_LOCAL_LIBRARY || sourceId == null) {
            "sourceId must be null for local-library search music list routes"
        }
        require(sourceType != SOURCE_TYPE_TRANSIENT || !sourceId.isNullOrBlank()) {
            "sourceId is required for transient search music list routes"
        }
    }

    companion object {
        const val SOURCE_TYPE_PLAYLIST = "playlist"
        const val SOURCE_TYPE_HISTORY = "history"
        const val SOURCE_TYPE_LOCAL_LIBRARY = "local-library"
        const val SOURCE_TYPE_TRANSIENT = "transient"

        fun playlist(playlistId: String): SearchMusicListRoute = SearchMusicListRoute(
            sourceType = SOURCE_TYPE_PLAYLIST,
            sourceId = playlistId,
        )

        fun history(): SearchMusicListRoute = SearchMusicListRoute(
            sourceType = SOURCE_TYPE_HISTORY,
        )

        fun localLibrary(): SearchMusicListRoute = SearchMusicListRoute(
            sourceType = SOURCE_TYPE_LOCAL_LIBRARY,
        )

        fun transient(sourceId: String): SearchMusicListRoute = SearchMusicListRoute(
            sourceType = SOURCE_TYPE_TRANSIENT,
            sourceId = sourceId,
        )
    }

    val playlistId: String?
        get() = sourceId?.takeIf { sourceType == SOURCE_TYPE_PLAYLIST }
}

@Serializable
data object SettingsRoute

@Serializable
data object PermissionsRoute

@Serializable
data object FileSelectorRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: String)

@Serializable
data class MusicListEditorLiteRoute(val playlistId: String)

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
    val seedToken: String? = null,
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
