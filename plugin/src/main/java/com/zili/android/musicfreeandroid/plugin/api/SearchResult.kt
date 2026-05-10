package com.zili.android.musicfreeandroid.plugin.api

import com.zili.android.musicfreeandroid.core.model.MusicItem

sealed interface PluginSearchItem {
    data class Music(val item: MusicItem) : PluginSearchItem
    data class Album(val item: AlbumItemBase) : PluginSearchItem
    data class Artist(val item: ArtistItemBase) : PluginSearchItem
    data class Sheet(val item: MusicSheetItemBase) : PluginSearchItem
}

data class SearchResult(
    val isEnd: Boolean,
    val data: List<PluginSearchItem>,
)

fun SearchResult.musicItems(): List<MusicItem> =
    data.mapNotNull { (it as? PluginSearchItem.Music)?.item }
