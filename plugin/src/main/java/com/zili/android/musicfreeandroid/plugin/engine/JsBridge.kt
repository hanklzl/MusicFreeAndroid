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
import com.zili.android.musicfreeandroid.plugin.api.PluginSearchItem
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.api.TopListDetailResult
import java.math.BigDecimal

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

    /**
     * Keys reserved for cross-process / internal bridge state that MUST NOT
     * round-trip through the JS plugin sandbox or be derived from JS-returned
     * maps.
     *
     * - `"$"` mirrors the RN MusicFree internal marker some legacy plugin
     *   payloads still emit; allowing it through would leak persisted Android
     *   state (localPath, downloaded flag, lyricOffset, …) into JS or, worse,
     *   let a malicious plugin masquerade as having those fields.
     * - `"internal"` is reserved for future use by [MusicItemBridgeProjector].
     *
     * See `docs/dev-harness/plugin/rules.md` (MUST: no `$` key in bridge map).
     */
    internal val BRIDGE_RESERVED_KEYS: Set<String> = setOf("\$", "internal")

    fun toMusicItem(map: Map<String, Any?>, fallbackPlatform: String? = null): MusicItem {
        // Phase F: defensively drop internal keys before parsing so a JS plugin
        // can't smuggle its own `"$"` field back through the bridge and
        // override the projector-supplied DownloadedTrack / LyricCache state
        // (or claim fields it never had access to).
        val filtered = map.filterKeys { it !in BRIDGE_RESERVED_KEYS }
        val durationRaw = (filtered["duration"] as? Number)?.toDouble() ?: 0.0
        return MusicItem(
            id = normalizedId(filtered["id"]),
            platform = normalizedPlatform(
                rawPlatform = filtered["platform"],
                fallbackPlatform = fallbackPlatform,
            ),
            title = filtered["title"]?.toString() ?: "",
            artist = filtered["artist"]?.toString() ?: "",
            album = filtered["album"]?.toString(),
            duration = (durationRaw * 1000).toLong(),
            url = filtered["url"]?.toString(),
            artwork = firstImageUrl(filtered, MusicImageFieldKeys),
            qualities = null,
            raw = filtered.toMap(),
        )
    }

    /**
     * Serialize a [MusicItem] to a JSON-friendly bridge map. Internal/reserved
     * keys (see [BRIDGE_RESERVED_KEYS]) are stripped from [MusicItem.raw] before
     * merging so they never reach the JS sandbox even if a previous bridge
     * round-trip somehow re-introduced them. Use [MusicItemBridgeProjector] for
     * the JsLoadedPlugin call path; this helper is the lower-level primitive.
     */
    fun musicItemToMap(item: MusicItem): Map<String, Any?> {
        val sanitizedRaw = item.raw.filterKeys { it !in BRIDGE_RESERVED_KEYS }
        return sanitizedRaw + mapOf(
            "id" to item.id,
            "platform" to item.platform,
            "title" to item.title,
            "artist" to item.artist,
            "album" to item.album,
            "duration" to (item.duration / 1000.0),
            "url" to item.url,
            "artwork" to item.artwork,
        )
    }

    fun parseSearchResult(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
        type: String = "music",
    ): SearchResult {
        val isEnd = map["isEnd"] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val dataList = (map["data"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let { itemMap ->
                when (type.lowercase()) {
                    "music", "lyric" -> PluginSearchItem.Music(
                        toMusicItem(itemMap, fallbackPlatform = fallbackPlatform),
                    )
                    "album" -> PluginSearchItem.Album(
                        toAlbumItemBase(itemMap, fallbackPlatform = fallbackPlatform),
                    )
                    "artist" -> PluginSearchItem.Artist(
                        toArtistItemBase(itemMap, fallbackPlatform = fallbackPlatform),
                    )
                    "sheet" -> PluginSearchItem.Sheet(
                        toMusicSheetItemBase(itemMap, fallbackPlatform = fallbackPlatform),
                    )
                    else -> null
                }
            }
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
            // Phase F: optional MIME hint from the plugin. Blank string is
            // treated as "no hint" so we never propagate empty-string content
            // types into downstream ExoPlayer / cache logic.
            contentType = map["contentType"]?.toString()?.takeIf { it.isNotBlank() },
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

    @Suppress("UNCHECKED_CAST")
    fun parseImportMusicSheetResult(
        payload: Any?,
        fallbackPlatform: String? = null,
    ): List<MusicItem> {
        val list = payload as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let {
                toMusicItem(it, fallbackPlatform = fallbackPlatform)
            }
        }
    }

    fun parseImportMusicItemResult(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): MusicItem {
        return toMusicItem(map, fallbackPlatform = fallbackPlatform)
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
            id = normalizedId(map["id"]),
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

    fun toAlbumItemBase(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): AlbumItemBase {
        return AlbumItemBase(
            id = normalizedId(map["id"]),
            platform = normalizedPlatform(
                rawPlatform = map["platform"],
                fallbackPlatform = fallbackPlatform,
            ),
            title = map["title"]?.toString(),
            date = map["date"]?.toString(),
            artist = map["artist"]?.toString(),
            description = map["description"]?.toString(),
            artwork = firstImageUrl(map, MusicImageFieldKeys),
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

    fun toArtistItemBase(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): ArtistItemBase {
        return ArtistItemBase(
            id = normalizedId(map["id"]),
            platform = normalizedPlatform(
                rawPlatform = map["platform"],
                fallbackPlatform = fallbackPlatform,
            ),
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
    fun parseTopListGroups(
        list: List<*>,
        fallbackPlatform: String? = null,
    ): List<MusicSheetGroupItem> {
        return list.mapNotNull { group ->
            val groupMap = group as? Map<String, Any?> ?: return@mapNotNull null
            val data = (groupMap["data"] as? List<*>)?.mapNotNull { entry ->
                (entry as? Map<String, Any?>)?.let {
                    toMusicSheetItemBase(it, fallbackPlatform = fallbackPlatform)
                }
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
    fun parseAlbumInfoResult(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): AlbumInfoResult {
        val isEnd = map["isEnd"] as? Boolean ?: true
        val albumMap =
            (map["albumItem"] as? Map<String, Any?>)
                ?: (map["sheetItem"] as? Map<String, Any?>)
        val albumItem = albumMap?.let { toAlbumItemBase(it, fallbackPlatform) }
        val musicList = (map["musicList"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let {
                toMusicItem(it, fallbackPlatform = fallbackPlatform)
            }
        } ?: emptyList()
        return AlbumInfoResult(
            isEnd = isEnd,
            albumItem = albumItem,
            musicList = musicList,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseArtistWorksResult(
        map: Map<String, Any?>,
        type: String,
        fallbackPlatform: String? = null,
    ): ArtistWorksResult {
        val isEnd = map["isEnd"] as? Boolean ?: false
        val rawData = (map["data"] as? List<*>)?.mapNotNull { entry ->
            entry as? Map<String, Any?>
        } ?: emptyList()
        val musicList = if (type == "music") {
            rawData.map { toMusicItem(it, fallbackPlatform = fallbackPlatform) }
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
            id = normalizedNullableId(map["id"]),
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
    fun parseRecommendSheetTagsResult(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): RecommendSheetTagsResult {
        val pinned = (map["pinned"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let {
                toMusicSheetItemBase(it, fallbackPlatform = fallbackPlatform)
            }
        } ?: emptyList()
        val groups = (map["data"] as? List<*>)?.let {
            parseTopListGroups(it, fallbackPlatform = fallbackPlatform)
        } ?: emptyList()
        return RecommendSheetTagsResult(
            pinned = pinned,
            data = groups,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseRecommendSheetsByTagResult(
        map: Map<String, Any?>,
        fallbackPlatform: String? = null,
    ): PaginationResult<MusicSheetItemBase> {
        val isEnd = map["isEnd"] as? Boolean ?: false
        val data = (map["data"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<String, Any?>)?.let {
                toMusicSheetItemBase(it, fallbackPlatform = fallbackPlatform)
            }
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

    private fun normalizedId(rawId: Any?): String =
        normalizedNullableId(rawId).orEmpty()

    private fun normalizedNullableId(rawId: Any?): String? {
        return when (rawId) {
            null -> null
            is String -> rawId
            is Byte,
            is Short,
            is Int,
            is Long -> rawId.toString()
            is Float,
            is Double -> {
                val value = rawId.toDouble()
                if (value.isFinite()) {
                    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
                } else {
                    rawId.toString()
                }
            }
            is BigDecimal -> rawId.stripTrailingZeros().toPlainString()
            is Number -> rawId.toString()
            else -> rawId.toString()
        }
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
