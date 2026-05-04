package com.zili.android.musicfreeandroid.plugin.engine

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.LyricLine
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.plugin.api.AlbumInfoResult
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistWorksResult
import com.zili.android.musicfreeandroid.plugin.api.LyricResult
import com.zili.android.musicfreeandroid.plugin.api.MusicComment
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetGroupItem
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetInfoResult
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PaginationResult
import com.zili.android.musicfreeandroid.plugin.api.RecommendSheetTagsResult
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.api.TopListDetailResult

object JsBridge {
    private val LrcRegex = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]([^\\n]*)")
    private val MusicImageFieldKeys = listOf(
        "artwork",
        "coverimg",
        "cover_img",
        "cover",
        "coverurl",
        "cover_url",
        "pic",
        "picurl",
        "pic_url",
        "img",
        "imgurl",
        "img_url",
        "image",
        "imageurl",
        "image_url",
        "avatar",
        "thumbnail",
        "poster",
        "albumart",
        "albumpic",
    )
    private val SheetImageFieldKeys = listOf(
        "coverimg",
        "cover_img",
        "cover",
        "coverurl",
        "cover_url",
        "artwork",
        "pic",
        "picurl",
        "pic_url",
        "img",
        "imgurl",
        "img_url",
        "image",
        "imageurl",
        "image_url",
        "avatar",
        "thumbnail",
        "poster",
    )
    private val AllImageFieldKeys = (MusicImageFieldKeys + SheetImageFieldKeys).toSet()

    fun toMusicItem(map: Map<String, Any?>, fallbackPlatform: String? = null): MusicItem {
        val durationRaw = (map["duration"] as? Number)?.toDouble() ?: 0.0
        return MusicItem(
            id = map["id"]?.toString() ?: "",
            platform = normalizedPlatform(
                rawPlatform = map["platform"],
                fallbackPlatform = fallbackPlatform,
            ),
            title = map["title"]?.toString() ?: "",
            artist = map["artist"]?.toString() ?: "",
            album = map["album"]?.toString(),
            duration = (durationRaw * 1000).toLong(),
            url = map["url"]?.toString(),
            artwork = firstImageUrl(map, MusicImageFieldKeys),
            qualities = null,
            raw = map.toMap(),
        )
    }

    fun musicItemToMap(item: MusicItem): Map<String, Any?> = item.raw + mapOf(
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

    fun parseMusicInfoResult(base: MusicItem, map: Map<String, Any?>): MusicItem {
        val merged = musicItemToMap(base) + map
        if (hasExplicitBlankImageField(map) && !hasExplicitNonBlankImageField(map)) {
            val mergedWithoutImageAliases = merged.filterKeys { key ->
                key.lowercase() !in AllImageFieldKeys
            }
            return toMusicItem(mergedWithoutImageAliases, fallbackPlatform = base.platform)
        }
        return toMusicItem(merged, fallbackPlatform = base.platform)
    }

    fun parseImportMusicSheetResult(payload: Any?): List<MusicItem> {
        val list = payload as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let(::toMusicItem)
        }
    }

    fun parseImportMusicItemResult(map: Map<String, Any?>): MusicItem {
        return toMusicItem(map)
    }

    fun parseLyricResult(map: Map<String, Any?>): LyricResult {
        val rawLrc = map["rawLrc"]?.toString()
            ?: map["lrc"]?.toString()
            ?: map["lyric"]?.toString()
        val rawLrcTxt = map["rawLrcTxt"]?.toString()
            ?: map["txt"]?.toString()
        val translation = map["translation"]?.toString()
            ?: map["trans"]?.toString()
        val source = rawLrc ?: rawLrcTxt
        return LyricResult(
            rawLrc = rawLrc,
            rawLrcTxt = rawLrcTxt,
            translation = translation,
            lines = parseLrcLines(source.orEmpty()),
        )
    }

    private fun parseLrcLines(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            LrcRegex.findAll(line).forEach { m ->
                val minute = m.groupValues[1].toLongOrNull() ?: return@forEach
                val second = m.groupValues[2].toLongOrNull() ?: return@forEach
                val fractionRaw = m.groupValues[3]
                val fractionMs = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                lines += LyricLine(
                    timeMs = (minute * 60_000L) + (second * 1000L) + fractionMs,
                    text = m.groupValues[4].trim(),
                )
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun toMusicSheetItemBase(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): MusicSheetItemBase {
        val resolvedArtwork = firstImageUrl(map, MusicImageFieldKeys)
        val resolvedCover = firstImageUrl(map, SheetImageFieldKeys) ?: resolvedArtwork
        return MusicSheetItemBase(
            id = map["id"]?.toString() ?: "",
            platform = normalizedPlatform(
                rawPlatform = map["platform"],
                fallbackPlatform = fallbackPlatform,
            ),
            title = map["title"]?.toString(),
            artist = map["artist"]?.toString(),
            description = map["description"]?.toString(),
            coverImg = resolvedCover,
            artwork = resolvedArtwork ?: resolvedCover,
            worksNum = (map["worksNum"] as? Number)?.toInt(),
            raw = map.toMap(),
        )
    }

    fun toAlbumItemBase(map: Map<String, Any?>): AlbumItemBase {
        return AlbumItemBase(
            id = map["id"]?.toString() ?: "",
            platform = map["platform"]?.toString() ?: "",
            title = map["title"]?.toString(),
            date = map["date"]?.toString(),
            artist = map["artist"]?.toString(),
            description = map["description"]?.toString(),
            artwork = map["artwork"]?.toString(),
            worksNum = (map["worksNum"] as? Number)?.toInt(),
            raw = map.toMap(),
        )
    }

    fun albumItemToMap(item: AlbumItemBase): Map<String, Any?> {
        return item.raw + mapOf(
            "id" to item.id,
            "platform" to item.platform,
            "title" to item.title,
            "date" to item.date,
            "artist" to item.artist,
            "description" to item.description,
            "artwork" to item.artwork,
            "worksNum" to item.worksNum,
        )
    }

    fun toArtistItemBase(map: Map<String, Any?>): ArtistItemBase {
        return ArtistItemBase(
            id = map["id"]?.toString() ?: "",
            platform = map["platform"]?.toString() ?: "",
            name = map["name"]?.toString(),
            avatar = map["avatar"]?.toString(),
            fans = (map["fans"] as? Number)?.toInt(),
            description = map["description"]?.toString(),
            worksNum = (map["worksNum"] as? Number)?.toInt(),
            raw = map.toMap(),
        )
    }

    fun artistItemToMap(item: ArtistItemBase): Map<String, Any?> {
        return item.raw + mapOf(
            "id" to item.id,
            "platform" to item.platform,
            "name" to item.name,
            "avatar" to item.avatar,
            "fans" to item.fans,
            "description" to item.description,
            "worksNum" to item.worksNum,
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
    fun parseTopListDetailResult(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): TopListDetailResult {
        val isEnd = map["isEnd"] as? Boolean ?: true
        val topListItem = (map["topListItem"] as? Map<String, Any?>)?.let {
            toMusicSheetItemBase(it, fallbackPlatform = fallbackPlatform)
        }
        val musicList = (map["musicList"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let {
                toMusicItem(it, fallbackPlatform = fallbackPlatform)
            }
        } ?: emptyList()
        return TopListDetailResult(
            isEnd = isEnd,
            topListItem = topListItem,
            musicList = musicList,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseMusicSheetInfoResult(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): MusicSheetInfoResult {
        val isEnd = map["isEnd"] as? Boolean ?: true
        val sheetItem = (map["sheetItem"] as? Map<String, Any?>)?.let {
            toMusicSheetItemBase(it, fallbackPlatform = fallbackPlatform)
        }
        val musicList = (map["musicList"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let {
                toMusicItem(it, fallbackPlatform = fallbackPlatform)
            }
        } ?: emptyList()
        return MusicSheetInfoResult(
            isEnd = isEnd,
            sheetItem = sheetItem,
            musicList = musicList,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseAlbumInfoResult(map: Map<String, Any?>): AlbumInfoResult {
        val isEnd = map["isEnd"] as? Boolean ?: true
        val albumMap =
            (map["albumItem"] as? Map<String, Any?>)
                ?: (map["sheetItem"] as? Map<String, Any?>)
        val albumItem = albumMap?.let(::toAlbumItemBase)
        val musicList = (map["musicList"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let(::toMusicItem)
        } ?: emptyList()
        return AlbumInfoResult(
            isEnd = isEnd,
            albumItem = albumItem,
            musicList = musicList,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseArtistWorksResult(map: Map<String, Any?>, type: String): ArtistWorksResult {
        val isEnd = map["isEnd"] as? Boolean ?: false
        val rawData = (map["data"] as? List<*>)?.mapNotNull { entry ->
            entry as? Map<String, Any?>
        } ?: emptyList()
        val musicList = if (type == "music") {
            rawData.map(::toMusicItem)
        } else {
            emptyList()
        }
        return ArtistWorksResult(
            isEnd = isEnd,
            type = type,
            musicList = musicList,
            rawData = rawData,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseMusicCommentsResult(map: Map<String, Any?>): PaginationResult<MusicComment> {
        val isEnd = map["isEnd"] as? Boolean ?: false
        val data = (map["data"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let(::toMusicComment)
        } ?: emptyList()
        return PaginationResult(
            isEnd = isEnd,
            data = data,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun toMusicComment(map: Map<String, Any?>): MusicComment {
        val replies = (map["replies"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let(::toMusicComment)
        } ?: emptyList()
        return MusicComment(
            id = map["id"]?.toString(),
            nickName = map["nickName"]?.toString() ?: map["name"]?.toString().orEmpty(),
            avatar = map["avatar"]?.toString(),
            comment = map["comment"]?.toString() ?: map["content"]?.toString().orEmpty(),
            likeCount = (map["like"] as? Number)?.toInt(),
            createAt = (map["createAt"] as? Number)?.toLong(),
            location = map["location"]?.toString(),
            replies = replies,
            raw = map.toMap(),
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

    private fun normalizedPlatform(rawPlatform: Any?, fallbackPlatform: String?): String {
        return rawPlatform
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackPlatform.orEmpty()
    }

    private fun firstImageUrl(map: Map<String, Any?>, keys: List<String>): String? {
        val normalizedEntries = map.entries.associate { it.key.lowercase() to it.value }
        for (key in keys) {
            normalizedImageUrl(normalizedEntries[key])?.let { return it }
        }
        return null
    }

    private fun normalizedImageUrl(raw: Any?): String? {
        val value = raw?.toString()?.trim().orEmpty()
        if (value.isBlank()) return null
        return if (value.startsWith("//")) {
            "https:$value"
        } else {
            value
        }
    }

    private fun hasExplicitBlankImageField(map: Map<String, Any?>): Boolean {
        return map.any { (key, value) ->
            key.lowercase() in AllImageFieldKeys && value?.toString()?.trim().orEmpty().isBlank()
        }
    }

    private fun hasExplicitNonBlankImageField(map: Map<String, Any?>): Boolean {
        return map.any { (key, value) ->
            key.lowercase() in AllImageFieldKeys && normalizedImageUrl(value) != null
        }
    }
}
