package com.zili.android.musicfreeandroid.feature.home.collection

import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute

sealed interface CollectionSource {
    data class Playlist(val playlistId: String) : CollectionSource

    data object History : CollectionSource

    data object LocalLibrary : CollectionSource

    data class Transient(val sourceId: String) : CollectionSource
}

fun SearchMusicListRoute.toCollectionSource(): CollectionSource =
    when (sourceType) {
        SearchMusicListRoute.SOURCE_TYPE_HISTORY -> CollectionSource.History
        SearchMusicListRoute.SOURCE_TYPE_PLAYLIST -> CollectionSource.Playlist(
            playlistId = requireNotNull(sourceId),
        )
        SearchMusicListRoute.SOURCE_TYPE_LOCAL_LIBRARY -> CollectionSource.LocalLibrary
        SearchMusicListRoute.SOURCE_TYPE_TRANSIENT -> CollectionSource.Transient(
            sourceId = requireNotNull(sourceId),
        )
        else -> error("Unsupported search music list source type: $sourceType")
    }
