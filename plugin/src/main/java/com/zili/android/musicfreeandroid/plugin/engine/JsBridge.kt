package com.zili.android.musicfreeandroid.plugin.engine

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.plugin.api.SearchResult

object JsBridge {
    fun toMusicItem(map: Map<String, Any?>): MusicItem {
        val durationRaw = (map["duration"] as? Number)?.toDouble() ?: 0.0
        return MusicItem(
            id = map["id"]?.toString() ?: "",
            platform = map["platform"]?.toString() ?: "",
            title = map["title"]?.toString() ?: "",
            artist = map["artist"]?.toString() ?: "",
            album = map["album"]?.toString(),
            duration = (durationRaw * 1000).toLong(),
            url = map["url"]?.toString(),
            artwork = map["artwork"]?.toString(),
            qualities = null,
        )
    }

    fun musicItemToMap(item: MusicItem): Map<String, Any?> = mapOf(
        "id" to item.id,
        "platform" to item.platform,
        "title" to item.title,
        "artist" to item.artist,
        "album" to item.album,
        "duration" to (item.duration / 1000.0),
        "url" to item.url,
        "artwork" to item.artwork,
    )

    fun parseSearchResult(map: Map<String, Any?>): SearchResult {
        val isEnd = map["isEnd"] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val dataList = (map["data"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let { toMusicItem(it) }
        } ?: emptyList()
        return SearchResult(isEnd = isEnd, data = dataList)
    }

    fun parseMediaSourceResult(map: Map<String, Any?>): MediaSourceResult? {
        val url = map["url"]?.toString() ?: return null
        @Suppress("UNCHECKED_CAST")
        val headers = (map["headers"] as? Map<String, String>)
        return MediaSourceResult(
            url = url,
            headers = headers,
            userAgent = map["userAgent"]?.toString(),
            quality = map["quality"]?.toString()?.let {
                runCatching { PlayQuality.valueOf(it.uppercase()) }.getOrNull()
            },
        )
    }
}
