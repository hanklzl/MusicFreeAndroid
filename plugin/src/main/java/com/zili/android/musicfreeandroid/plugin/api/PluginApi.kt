package com.zili.android.musicfreeandroid.plugin.api

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem

interface PluginApi {
    val info: PluginInfo
    suspend fun search(query: String, page: Int, type: String = "music"): SearchResult
    suspend fun getMediaSource(musicItem: MusicItem, quality: String = "standard"): MediaSourceResult?
}
