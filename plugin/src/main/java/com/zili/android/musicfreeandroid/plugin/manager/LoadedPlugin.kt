package com.zili.android.musicfreeandroid.plugin.manager

import android.util.Log
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
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
import com.zili.android.musicfreeandroid.plugin.api.PluginApi
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.RecommendSheetTagsResult
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.api.TopListDetailResult
import com.zili.android.musicfreeandroid.plugin.engine.JsBridge
import com.zili.android.musicfreeandroid.plugin.engine.JsEngine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

enum class PluginInstallSourceType {
    LOCAL_FILE,
    PLUGIN_URL,
    SUBSCRIPTION_URL,
    UPDATE_SINGLE,
    UPDATE_ALL,
    UPDATE_SUBSCRIPTION,
}

data class PluginInstallSource(
    val type: PluginInstallSourceType,
    val value: String? = null,
)

/**
 * A loaded JS plugin backed by its own [JsEngine] instance.
 * Implements [PluginApi] by delegating calls to the JS plugin object (`__plugin`).
 *
 * Uses quickjs-kt's native Promise support — no JSON.stringify round-trip needed.
 * JsObject implements Map, so it can be passed directly to JsBridge parsers.
 */
class LoadedPlugin(
    override val info: PluginInfo,
    private val engine: JsEngine,
    var filePath: String,
    val installSource: PluginInstallSource = PluginInstallSource(PluginInstallSourceType.LOCAL_FILE),
) : PluginApi {

    companion object {
        private const val TAG = "LoadedPlugin"
        private const val TIMEOUT_MS = 30_000L
    }

    override suspend fun search(query: String, page: Int, type: String): SearchResult {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("search")) {
                    return@withTimeout SearchResult(isEnd = true, data = emptyList())
                }
                val result = engine.evaluate<Any?>(
                    "await __plugin.search('${escapeJsString(query)}', $page, '${escapeJsString(type)}')"
                )
                val map = toMap(result) ?: return@withTimeout SearchResult(isEnd = true, data = emptyList())
                JsBridge.parseSearchResult(map)
            } catch (e: Exception) {
                Log.e(TAG, "search failed for query='$query' on ${info.platform}", e)
                SearchResult(isEnd = true, data = emptyList())
            }
        }
    }

    override suspend fun getMediaSource(musicItem: MusicItem, quality: String): MediaSourceResult? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getMediaSource")) {
                    return@withTimeout null
                }
                injectGlobalMap("__musicItem", JsBridge.musicItemToMap(musicItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getMediaSource(__musicItem, '$quality')"
                )
                val map = toMap(result)
                if (map != null) {
                    JsBridge.parseMediaSourceResult(map)
                } else {
                    // Fallback: try qualities from musicItem (aligns with RN behavior)
                    val qualityEnum = runCatching {
                        PlayQuality.valueOf(quality.uppercase())
                    }.getOrNull()
                    val fallbackUrl = qualityEnum?.let { musicItem.qualities?.get(it)?.url }
                    fallbackUrl?.let {
                        MediaSourceResult(
                            url = it,
                            headers = null,
                            userAgent = null,
                            quality = qualityEnum,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getMediaSource failed for ${musicItem.id} on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getMusicInfo(musicItem: MusicItem): MusicItem? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getMusicInfo")) return@withTimeout null
                injectGlobalMap("__musicBase", JsBridge.musicItemToMap(musicItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getMusicInfo(__musicBase)"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseMusicInfoResult(musicItem, map)
            } catch (e: Exception) {
                Log.e(TAG, "getMusicInfo failed for ${musicItem.id} on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getLyric(musicItem: MusicItem): LyricResult? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getLyric")) return@withTimeout null
                injectGlobalMap("__musicBase", JsBridge.musicItemToMap(musicItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getLyric(__musicBase)"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseLyricResult(map)
            } catch (e: Exception) {
                Log.e(TAG, "getLyric failed for ${musicItem.id} on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getAlbumInfo(albumItem: AlbumItemBase, page: Int): AlbumInfoResult? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getAlbumInfo")) return@withTimeout null
                injectGlobalMap("__albumItem", JsBridge.albumItemToMap(albumItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getAlbumInfo(__albumItem, $page)"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseAlbumInfoResult(map)
            } catch (e: Exception) {
                Log.e(TAG, "getAlbumInfo failed for ${albumItem.id} on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getArtistWorks(
        artistItem: ArtistItemBase,
        page: Int,
        type: String,
    ): ArtistWorksResult? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getArtistWorks")) return@withTimeout null
                injectGlobalMap("__artistItem", JsBridge.artistItemToMap(artistItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getArtistWorks(__artistItem, $page, '${escapeJsString(type)}')"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseArtistWorksResult(map, type)
            } catch (e: Exception) {
                Log.e(TAG, "getArtistWorks failed for ${artistItem.id} on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun importMusicSheet(urlLike: String): List<MusicItem>? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("importMusicSheet")) return@withTimeout null
                val result = engine.evaluate<Any?>(
                    "await __plugin.importMusicSheet('${escapeJsString(urlLike)}')"
                )
                JsBridge.parseImportMusicSheetResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "importMusicSheet failed on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun importMusicItem(urlLike: String): MusicItem? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("importMusicItem")) return@withTimeout null
                val result = engine.evaluate<Any?>(
                    "await __plugin.importMusicItem('${escapeJsString(urlLike)}')"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseImportMusicItemResult(map)
            } catch (e: Exception) {
                Log.e(TAG, "importMusicItem failed on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getTopLists(): List<MusicSheetGroupItem> {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getTopLists")) return@withTimeout emptyList()
                val result = engine.evaluate<Any?>(
                    "await __plugin.getTopLists()"
                )
                val list = result as? List<*> ?: return@withTimeout emptyList()
                JsBridge.parseTopListGroups(list)
            } catch (e: Exception) {
                Log.e(TAG, "getTopLists failed on ${info.platform}", e)
                emptyList()
            }
        }
    }

    override suspend fun getTopListDetail(
        topListItem: MusicSheetItemBase,
        page: Int,
    ): TopListDetailResult? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getTopListDetail")) return@withTimeout null
                injectGlobalMap("__topListItem", JsBridge.musicSheetItemToMap(topListItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getTopListDetail(__topListItem, $page)"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseTopListDetailResult(map, fallbackPlatform = info.platform)
            } catch (e: Exception) {
                Log.e(TAG, "getTopListDetail failed on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getMusicSheetInfo(
        sheetItem: MusicSheetItemBase,
        page: Int,
    ): MusicSheetInfoResult? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getMusicSheetInfo")) return@withTimeout null
                injectGlobalMap("__sheetItem", JsBridge.musicSheetItemToMap(sheetItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getMusicSheetInfo(__sheetItem, $page)"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseMusicSheetInfoResult(map, fallbackPlatform = info.platform)
            } catch (e: Exception) {
                Log.e(TAG, "getMusicSheetInfo failed on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getRecommendSheetTags(): RecommendSheetTagsResult? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getRecommendSheetTags")) return@withTimeout null
                val result = engine.evaluate<Any?>(
                    "await __plugin.getRecommendSheetTags()"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseRecommendSheetTagsResult(map)
            } catch (e: Exception) {
                Log.e(TAG, "getRecommendSheetTags failed on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getRecommendSheetsByTag(
        tag: Map<String, Any?>,
        page: Int,
    ): PaginationResult<MusicSheetItemBase>? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getRecommendSheetsByTag")) return@withTimeout null
                injectGlobalMap("__recommendTag", tag)
                val result = engine.evaluate<Any?>(
                    "await __plugin.getRecommendSheetsByTag(__recommendTag, $page)"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseRecommendSheetsByTagResult(map)
            } catch (e: Exception) {
                Log.e(TAG, "getRecommendSheetsByTag failed on ${info.platform}", e)
                null
            }
        }
    }

    override suspend fun getMusicComments(
        musicItem: MusicItem,
        page: Int,
    ): PaginationResult<MusicComment>? {
        return withTimeout(TIMEOUT_MS) {
            try {
                if (!hasMethod("getMusicComments")) return@withTimeout null
                injectGlobalMap("__musicItem", JsBridge.musicItemToMap(musicItem))
                val result = engine.evaluate<Any?>(
                    "await __plugin.getMusicComments(__musicItem, $page)"
                )
                val map = toMap(result) ?: return@withTimeout null
                JsBridge.parseMusicCommentsResult(map)
            } catch (e: Exception) {
                Log.e(TAG, "getMusicComments failed for ${musicItem.id} on ${info.platform}", e)
                null
            }
        }
    }

    suspend fun destroy() {
        engine.close()
    }

    // -- Internal helpers --

    private suspend fun hasMethod(name: String): Boolean {
        return try {
            engine.evaluate<Boolean>("typeof __plugin.$name === 'function'")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Inject a Kotlin Map as a JS global variable via JSON.parse.
     * Uses org.json.JSONObject for serialization (handles nested maps/lists/primitives).
     */
    private suspend fun injectGlobalMap(name: String, map: Map<String, Any?>) {
        val jsonStr = JSONObject(map).toString()
        engine.evaluate<Any?>("globalThis.$name = JSON.parse('${escapeJsString(jsonStr)}')")
    }

    @Suppress("UNCHECKED_CAST")
    private fun toMap(result: Any?): Map<String, Any?>? {
        return result as? Map<String, Any?>
    }

    private fun escapeJsString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
