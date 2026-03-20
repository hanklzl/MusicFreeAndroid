package com.zili.android.musicfreeandroid.plugin.engine

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetGroupItem
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PaginationResult
import com.zili.android.musicfreeandroid.plugin.api.RecommendSheetTagsResult
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.api.TopListDetailResult

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

    fun toMusicSheetItemBase(map: Map<String, Any?>): MusicSheetItemBase {
        return MusicSheetItemBase(
            id = map["id"]?.toString() ?: "",
            platform = map["platform"]?.toString() ?: "",
            title = map["title"]?.toString(),
            artist = map["artist"]?.toString(),
            description = map["description"]?.toString(),
            coverImg = map["coverImg"]?.toString(),
            artwork = map["artwork"]?.toString(),
            worksNum = (map["worksNum"] as? Number)?.toInt(),
            raw = map.toMap(),
        )
    }

    fun musicSheetItemToMap(item: MusicSheetItemBase): Map<String, Any?> {
        return item.raw + mapOf(
            "id" to item.id,
            "platform" to item.platform,
            "title" to item.title,
            "artist" to item.artist,
            "description" to item.description,
            "coverImg" to item.coverImg,
            "artwork" to item.artwork,
            "worksNum" to item.worksNum,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseTopListGroups(list: List<*>): List<MusicSheetGroupItem> {
        return list.mapNotNull { group ->
            val groupMap = group as? Map<String, Any?> ?: return@mapNotNull null
            val data = (groupMap["data"] as? List<*>)?.mapNotNull { entry ->
                (entry as? Map<String, Any?>)?.let(::toMusicSheetItemBase)
            } ?: emptyList()

            MusicSheetGroupItem(
                title = groupMap["title"]?.toString(),
                data = data,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun parseTopListDetailResult(map: Map<String, Any?>): TopListDetailResult {
        val isEnd = map["isEnd"] as? Boolean ?: true
        val topListItem = (map["topListItem"] as? Map<String, Any?>)?.let(::toMusicSheetItemBase)
        val musicList = (map["musicList"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let(::toMusicItem)
        } ?: emptyList()
        return TopListDetailResult(
            isEnd = isEnd,
            topListItem = topListItem,
            musicList = musicList,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseRecommendSheetTagsResult(map: Map<String, Any?>): RecommendSheetTagsResult {
        val pinned = (map["pinned"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let(::toMusicSheetItemBase)
        } ?: emptyList()
        val groups = (map["data"] as? List<*>)?.let(::parseTopListGroups) ?: emptyList()
        return RecommendSheetTagsResult(
            pinned = pinned,
            data = groups,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseRecommendSheetsByTagResult(map: Map<String, Any?>): PaginationResult<MusicSheetItemBase> {
        val isEnd = map["isEnd"] as? Boolean ?: false
        val data = (map["data"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let(::toMusicSheetItemBase)
        } ?: emptyList()
        return PaginationResult(
            isEnd = isEnd,
            data = data,
        )
    }
}
