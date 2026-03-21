package com.zili.android.musicfreeandroid.plugin.manager

import android.util.Log
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
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
import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSObject
import kotlinx.coroutines.withTimeout

/**
 * A loaded JS plugin backed by its own [JsEngine] instance.
 * Implements [PluginApi] by delegating calls to the JS plugin object (`__plugin`).
 */
class LoadedPlugin(
    override val info: PluginInfo,
    private val engine: JsEngine,
    var filePath: String,
) : PluginApi {

    companion object {
        private const val TAG = "LoadedPlugin"
        private const val TIMEOUT_MS = 30_000L
    }

    override suspend fun search(query: String, page: Int, type: String): SearchResult {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("search")) {
                        return@runOnJsThread SearchResult(isEnd = true, data = emptyList())
                    }
                    val asyncExpr =
                        "async function() { var r = await __plugin.search('${escapeJsString(query)}', $page, '${escapeJsString(type)}'); return JSON.stringify(r); }()"
                    val jsonStr = engine.evaluateAsync(asyncExpr)
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        Log.w(TAG, "search returned empty for query='$query' on ${info.platform}")
                        return@runOnJsThread SearchResult(isEnd = true, data = emptyList())
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseSearchResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "search failed for query='$query' on ${info.platform}", e)
                    SearchResult(isEnd = true, data = emptyList())
                }
            }
        }
    }

    override suspend fun getMediaSource(musicItem: MusicItem, quality: String): MediaSourceResult? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getMediaSource")) {
                        return@runOnJsThread null
                    }
                    val itemMap = JsBridge.musicItemToMap(musicItem)
                    engine.setGlobalMap("__musicItem", itemMap)
                    val asyncExpr =
                        "async function() { var r = await __plugin.getMediaSource(__musicItem, '$quality'); return JSON.stringify(r); }()"
                    val jsonStr = engine.evaluateAsync(asyncExpr)
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        Log.w(TAG, "getMediaSource returned empty for ${musicItem.id} on ${info.platform}")
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseMediaSourceResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getMediaSource failed for ${musicItem.id} on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getMusicInfo(musicItem: MusicItem): MusicItem? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getMusicInfo")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__musicBase", JsBridge.musicItemToMap(musicItem))
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getMusicInfo(__musicBase); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseMusicInfoResult(musicItem, parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getMusicInfo failed for ${musicItem.id} on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getLyric(musicItem: MusicItem): LyricResult? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getLyric")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__musicBase", JsBridge.musicItemToMap(musicItem))
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getLyric(__musicBase); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseLyricResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getLyric failed for ${musicItem.id} on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getAlbumInfo(albumItem: AlbumItemBase, page: Int): AlbumInfoResult? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getAlbumInfo")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__albumItem", JsBridge.albumItemToMap(albumItem))
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getAlbumInfo(__albumItem, $page); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseAlbumInfoResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getAlbumInfo failed for ${albumItem.id} on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getArtistWorks(
        artistItem: ArtistItemBase,
        page: Int,
        type: String,
    ): ArtistWorksResult? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getArtistWorks")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__artistItem", JsBridge.artistItemToMap(artistItem))
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getArtistWorks(__artistItem, $page, '${escapeJsString(type)}'); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseArtistWorksResult(parsed, type)
                } catch (e: Exception) {
                    Log.e(TAG, "getArtistWorks failed for ${artistItem.id} on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun importMusicSheet(urlLike: String): List<MusicItem>? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("importMusicSheet")) {
                        return@runOnJsThread null
                    }
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.importMusicSheet('${escapeJsString(urlLike)}'); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToAny(jsonStr)
                    JsBridge.parseImportMusicSheetResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "importMusicSheet failed on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun importMusicItem(urlLike: String): MusicItem? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("importMusicItem")) {
                        return@runOnJsThread null
                    }
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.importMusicItem('${escapeJsString(urlLike)}'); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseImportMusicItemResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "importMusicItem failed on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getTopLists(): List<MusicSheetGroupItem> {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getTopLists")) {
                        return@runOnJsThread emptyList()
                    }
                    val jsonStr =
                        engine.evaluateAsync(
                            "async function() { var r = await __plugin.getTopLists(); return JSON.stringify(r); }()",
                        )
                    val parsed = parseJsonToAny(jsonStr) as? List<*> ?: return@runOnJsThread emptyList()
                    JsBridge.parseTopListGroups(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getTopLists failed on ${info.platform}", e)
                    emptyList()
                }
            }
        }
    }

    override suspend fun getTopListDetail(
        topListItem: MusicSheetItemBase,
        page: Int,
    ): TopListDetailResult? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getTopListDetail")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__topListItem", JsBridge.musicSheetItemToMap(topListItem))
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getTopListDetail(__topListItem, $page); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseTopListDetailResult(
                        parsed,
                        fallbackPlatform = info.platform,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "getTopListDetail failed on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getMusicSheetInfo(
        sheetItem: MusicSheetItemBase,
        page: Int,
    ): MusicSheetInfoResult? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getMusicSheetInfo")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__sheetItem", JsBridge.musicSheetItemToMap(sheetItem))
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getMusicSheetInfo(__sheetItem, $page); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseMusicSheetInfoResult(
                        parsed,
                        fallbackPlatform = info.platform,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "getMusicSheetInfo failed on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getRecommendSheetTags(): RecommendSheetTagsResult? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getRecommendSheetTags")) {
                        return@runOnJsThread null
                    }
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getRecommendSheetTags(); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseRecommendSheetTagsResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getRecommendSheetTags failed on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getRecommendSheetsByTag(
        tag: Map<String, Any?>,
        page: Int,
    ): PaginationResult<MusicSheetItemBase>? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getRecommendSheetsByTag")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__recommendTag", tag)
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getRecommendSheetsByTag(__recommendTag, $page); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseRecommendSheetsByTagResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getRecommendSheetsByTag failed on ${info.platform}", e)
                    null
                }
            }
        }
    }

    override suspend fun getMusicComments(
        musicItem: MusicItem,
        page: Int,
    ): PaginationResult<MusicComment>? {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    if (!hasMethod("getMusicComments")) {
                        return@runOnJsThread null
                    }
                    engine.setGlobalMap("__musicItem", JsBridge.musicItemToMap(musicItem))
                    val jsonStr = engine.evaluateAsync(
                        "async function() { var r = await __plugin.getMusicComments(__musicItem, $page); return JSON.stringify(r); }()",
                    )
                    if (jsonStr.isNullOrBlank() || jsonStr == "undefined" || jsonStr == "null") {
                        return@runOnJsThread null
                    }
                    val parsed = parseJsonToMap(jsonStr)
                    JsBridge.parseMusicCommentsResult(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "getMusicComments failed for ${musicItem.id} on ${info.platform}", e)
                    null
                }
            }
        }
    }

    fun destroy() {
        engine.destroy()
    }

    private fun hasMethod(name: String): Boolean {
        val result = engine.evaluate("typeof __plugin.$name === 'function'")
        return result as? Boolean ?: false
    }

    private fun parseJsonToAny(json: String?): Any? {
        if (json.isNullOrBlank() || json == "undefined" || json == "null") {
            return null
        }
        val ctx = engine.context
            ?: throw IllegalStateException("Engine context is null")
        val parsed = ctx.parse(json)
        return when (parsed) {
            is JSObject -> engine.jsObjectToMap(parsed)
            is JSArray -> engine.jsArrayToList(parsed)
            else -> parsed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonToMap(json: String): Map<String, Any?> {
        return parseJsonToAny(json) as? Map<String, Any?> ?: emptyMap()
    }

    private fun escapeJsString(value: String): String {
        return value.replace("\\", "\\\\").replace("'", "\\'")
    }
}
