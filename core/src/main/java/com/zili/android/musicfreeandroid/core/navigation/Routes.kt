package com.zili.android.musicfreeandroid.core.navigation

import androidx.annotation.Keep
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
data object LocalRoute

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
@Keep
enum class SettingsType {
    Basic,
    Theme,
    Backup,
    About,
}

@Serializable
data class SettingsRoute(
    val type: SettingsType = SettingsType.Basic,
)

@Serializable
data object PermissionsRoute

@Serializable
data object FileSelectorRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: String)

@Serializable
data class MusicListEditorLiteRoute(
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("playlistId")
    val sourceId: String,
    val sourceType: String = SOURCE_TYPE_PLAYLIST,
) {
    constructor(playlistId: String) : this(
        sourceId = playlistId,
        sourceType = SOURCE_TYPE_PLAYLIST,
    )

    init {
        require(sourceType == SOURCE_TYPE_PLAYLIST || sourceType == SOURCE_TYPE_LOCAL_LIBRARY) {
            "Unsupported music list editor source type: $sourceType"
        }
        require(sourceType != SOURCE_TYPE_PLAYLIST || sourceId.isNotBlank()) {
            "sourceId is required for playlist editor routes"
        }
        require(sourceType != SOURCE_TYPE_LOCAL_LIBRARY || sourceId == LOCAL_LIBRARY_SOURCE_ID) {
            "sourceId must be $LOCAL_LIBRARY_SOURCE_ID for local-library editor routes"
        }
    }

    val playlistId: String
        get() = sourceId

    companion object {
        const val SOURCE_TYPE_PLAYLIST = "playlist"
        const val SOURCE_TYPE_LOCAL_LIBRARY = "local-library"
        const val LOCAL_LIBRARY_SOURCE_ID = "local"

        fun localLibrary(): MusicListEditorLiteRoute = MusicListEditorLiteRoute(
            sourceId = LOCAL_LIBRARY_SOURCE_ID,
            sourceType = SOURCE_TYPE_LOCAL_LIBRARY,
        )
    }
}

@Serializable
data object PlayQueueRoute

@Serializable
data object TopListRoute

@Serializable
data class TopListDetailRoute(
    val pluginPlatform: String,
    val topListId: String,
    val title: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverImg: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val seedToken: String? = null,
)

@Serializable
data object RecommendSheetsRoute

@Serializable
data class PluginSheetDetailRoute(
    val pluginPlatform: String,
    val sheetId: String,
    val title: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverImg: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val seedToken: String? = null,
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
    val date: String? = null,
    val description: String? = null,
    val worksNum: Int? = null,
    val seedToken: String? = null,
)

@Serializable
data class ArtistDetailRoute(
    val pluginPlatform: String,
    val artistId: String,
    val name: String,
    val avatar: String? = null,
    val description: String? = null,
    val fans: Int? = null,
    val worksNum: Int? = null,
    val seedToken: String? = null,
)

@Serializable
data object PluginListRoute

@Serializable
data object PluginSortRoute

@Serializable
data object PluginSubscriptionRoute

@Serializable
data object DownloadingRoute

@Serializable
data class ListenStatsRoute(
    val scope: String = "WEEK",
    val anchorEpochDay: Long = -1L,
)

@Serializable
data class ListenDetailRoute(
    val mode: String,
    val scope: String,
    val anchorEpochDay: Long,
    val filterValue: String? = null,
)
