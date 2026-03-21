package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import com.zili.android.musicfreeandroid.core.model.MusicItem
import java.util.Locale

data class SearchMusicListUiState(
    val query: String = "",
    val sourceItems: List<MusicItem> = emptyList(),
) {
    val filteredItems: List<MusicItem>
        get() = filterSearchMusicList(query = query, sourceItems = sourceItems)
}

internal fun filterSearchMusicList(
    query: String,
    sourceItems: List<MusicItem>,
): List<MusicItem> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) return sourceItems
    return sourceItems.filter { item ->
        buildString {
            append(item.title)
            append(' ')
            append(item.artist)
            append(' ')
            append(item.album.orEmpty())
            append(' ')
            append(item.platform)
        }.lowercase(Locale.ROOT).contains(normalizedQuery)
    }
}
