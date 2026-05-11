package com.zili.android.musicfreeandroid.plugin.manager

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.timedSuspend
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
 * Uses quickjs-kt's native Promise support; JsObject implements Map, so it can be
 * passed directly to JsBridge parsers.
 */
class LoadedPlugin(
    override val info: PluginInfo,
    private val engine: JsEngine,
    var filePath: String,
    val installSource: PluginInstallSource = PluginInstallSource(PluginInstallSourceType.LOCAL_FILE),
) : PluginApi {

    companion object {
        private const val TIMEOUT_MS = 30_000L
        private const val MEDIA_SOURCE_RETRY_DELAY_MS = 150L
    }

    private suspend fun <T> executeApiCall(
        method: String,
        inputFields: Map<String, Any?> = emptyMap(),
        onFailure: () -> T,
        resultFields: (T) -> Map<String, Any?> = { emptyMap() },
        block: suspend () -> T,
    ): T {
        val baseFields = apiBaseFields(method)
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_api_call_start",
            fields = baseFields + inputFields,
        )

        return withTimeout(TIMEOUT_MS) {
            val startedAt = System.nanoTime()
            try {
                val (result, durationMs) = timedSuspend { block() }
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "plugin_api_call_success",
                    fields = baseFields + mapOf(
                        "status" to "success",
                        "result" to LogFields.Result.SUCCESS,
                        "durationMs" to durationMs,
                    ) + countField(result) + inputFields + resultFields(result),
                )
                result
            } catch (error: Exception) {
                rethrowIfExternalCancellation(error)
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_api_call_failed",
                    throwable = error,
                    fields = baseFields + mapOf(
                        "status" to "failed",
                        "errorClass" to error::class.java.name,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                    ) + inputFields,
                )
                onFailure()
            }
        }
    }

    override suspend fun search(query: String, page: Int, type: String): SearchResult {
        if (!hasMethod("search")) {
            logApiCallSkipped(
                method = "search",
                inputFields = mapOf(
                    "query" to query,
                    "page" to page,
                    "type" to type,
                ),
            )
            return SearchResult(isEnd = true, data = emptyList())
        }

        return executeApiCall(
            method = "search",
            inputFields = mapOf(
                "query" to query,
                "page" to page,
                "type" to type,
            ),
            onFailure = { SearchResult(isEnd = true, data = emptyList()) },
            resultFields = { result ->
                mapOf("resultCount" to result.data.size, "isEnd" to result.isEnd)
            },
        ) {
            val result = engine.evaluate<Any?>(
                "await __plugin.search('${escapeJsString(query)}', $page, '${escapeJsString(type)}')",
            )
            val map = toMap(result) ?: return@executeApiCall SearchResult(isEnd = true, data = emptyList())
            JsBridge.parseSearchResult(map, fallbackPlatform = info.platform, type = type)
        }
    }

    override suspend fun getMediaSource(musicItem: MusicItem, quality: String): MediaSourceResult? {
        return executeApiCall(
            method = "getMediaSource",
            inputFields = mapOf(
                "musicItemId" to musicItem.id,
                "quality" to quality,
            ),
            onFailure = { null },
            resultFields = { result ->
                mapOf(
                    "hasUrl" to (result?.url?.isNotBlank() == true),
                    "quality" to (result?.quality?.name ?: quality),
                )
            },
        ) {
            getMediaSourceWithRetry(musicItem, quality)
        }
    }

    private suspend fun getMediaSourceWithRetry(musicItem: MusicItem, quality: String): MediaSourceResult? {
        if (!hasMethod("getMediaSource")) {
            logApiCallSkipped(
                method = "getMediaSource",
                inputFields = mapOf(
                    "musicItemId" to musicItem.id,
                    "quality" to quality,
                ),
            )
            return fallbackQuality(musicItem, quality)
        }

        return try {
            doGetMediaSource(musicItem, quality) ?: fallbackQuality(musicItem, quality)
        } catch (firstError: Exception) {
            rethrowIfExternalCancellation(firstError)
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_api_call_failed",
                throwable = firstError,
                fields = mapOf(
                    "platform" to info.platform,
                    "pluginVersion" to info.version,
                    "method" to "getMediaSource",
                    "operation" to "plugin_api_call",
                    "musicItemId" to musicItem.id,
                    "quality" to quality,
                    "status" to "failed",
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "first_attempt_failed",
                ),
            )
            delay(MEDIA_SOURCE_RETRY_DELAY_MS)
            try {
                doGetMediaSource(musicItem, quality) ?: fallbackQuality(musicItem, quality)
            } catch (secondError: Exception) {
                rethrowIfExternalCancellation(secondError)
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "plugin_api_call_failed",
                    throwable = secondError,
                        fields = mapOf(
                            "platform" to info.platform,
                            "pluginVersion" to info.version,
                            "method" to "getMediaSource",
                            "operation" to "plugin_api_call",
                            "musicItemId" to musicItem.id,
                            "quality" to quality,
                            "status" to "failed",
                            "result" to LogFields.Result.FAILURE,
                            "reason" to "retry_failed",
                        ),
                    )
                fallbackQuality(musicItem, quality)
            }
        }
    }

    private suspend fun doGetMediaSource(musicItem: MusicItem, quality: String): MediaSourceResult? {
        injectGlobalMap("__musicItem", JsBridge.musicItemToMap(musicItem))
        val result = engine.evaluate<Any?>(
            "await __plugin.getMediaSource(__musicItem, '${escapeJsString(quality)}')",
        )
        val map = toMap(result) ?: return null
        val parsed = JsBridge.parseMediaSourceResult(map) ?: return null
        return parsed.takeIf { it.url.isNotBlank() }
    }

    private fun fallbackQuality(musicItem: MusicItem, quality: String): MediaSourceResult? {
        val qualityEnum = runCatching { PlayQuality.valueOf(quality.uppercase()) }.getOrNull() ?: return null
        val fallbackUrl = musicItem.qualities?.get(qualityEnum)?.url ?: return null
        return MediaSourceResult(
            url = fallbackUrl,
            headers = null,
            userAgent = null,
            quality = qualityEnum,
        )
    }

    override suspend fun getMusicInfo(musicItem: MusicItem): MusicItem? {
        if (!hasMethod("getMusicInfo")) {
            logApiCallSkipped("getMusicInfo", mapOf("musicItemId" to musicItem.id))
            return null
        }
        return executeApiCall(
            method = "getMusicInfo",
            inputFields = mapOf("musicItemId" to musicItem.id),
            onFailure = { null },
        ) {
            injectGlobalMap("__musicBase", JsBridge.musicItemToMap(musicItem))
            val result = engine.evaluate<Any?>("await __plugin.getMusicInfo(__musicBase)")
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseMusicInfoResult(musicItem, map)
        }
    }

    override suspend fun getLyric(musicItem: MusicItem): LyricResult? {
        if (!hasMethod("getLyric")) {
            logApiCallSkipped("getLyric", mapOf("musicItemId" to musicItem.id))
            return null
        }
        return executeApiCall(
            method = "getLyric",
            inputFields = mapOf("musicItemId" to musicItem.id),
            onFailure = { null },
        ) {
            injectGlobalMap("__musicBase", JsBridge.musicItemToMap(musicItem))
            val result = engine.evaluate<Any?>("await __plugin.getLyric(__musicBase)")
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseLyricResult(map)
        }
    }

    override suspend fun getAlbumInfo(albumItem: AlbumItemBase, page: Int): AlbumInfoResult? {
        if (!hasMethod("getAlbumInfo")) {
            logApiCallSkipped(
                "getAlbumInfo",
                mapOf(
                    "albumItemId" to albumItem.id,
                    "page" to page,
                ),
            )
            return null
        }
        return executeApiCall(
            method = "getAlbumInfo",
            inputFields = mapOf(
                "albumItemId" to albumItem.id,
                "page" to page,
            ),
            onFailure = { null },
        ) {
            injectGlobalMap("__albumItem", JsBridge.albumItemToMap(albumItem))
            val result = engine.evaluate<Any?>("await __plugin.getAlbumInfo(__albumItem, $page)")
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseAlbumInfoResult(map, fallbackPlatform = info.platform)
        }
    }

    override suspend fun getArtistWorks(
        artistItem: ArtistItemBase,
        page: Int,
        type: String,
    ): ArtistWorksResult? {
        if (!hasMethod("getArtistWorks")) {
            logApiCallSkipped(
                "getArtistWorks",
                mapOf(
                    "artistItemId" to artistItem.id,
                    "page" to page,
                    "type" to type,
                ),
            )
            return null
        }
        return executeApiCall(
            method = "getArtistWorks",
            inputFields = mapOf(
                "artistItemId" to artistItem.id,
                "page" to page,
                "type" to type,
            ),
            onFailure = { null },
        ) {
            injectGlobalMap("__artistItem", JsBridge.artistItemToMap(artistItem))
            val result = engine.evaluate<Any?>(
                "await __plugin.getArtistWorks(__artistItem, $page, '${escapeJsString(type)}')",
            )
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseArtistWorksResult(map, type, fallbackPlatform = info.platform)
        }
    }

    override suspend fun importMusicSheet(urlLike: String): List<MusicItem>? {
        if (!hasMethod("importMusicSheet")) {
            logApiCallSkipped("importMusicSheet", mapOf("urlLike" to urlLike))
            return null
        }
        return executeApiCall(
            method = "importMusicSheet",
            inputFields = mapOf("urlLike" to urlLike),
            onFailure = { null },
            resultFields = { result ->
                mapOf("resultCount" to (result?.size ?: 0))
            },
        ) {
            val result = engine.evaluate<Any?>(
                "await __plugin.importMusicSheet('${escapeJsString(urlLike)}')",
            )
            JsBridge.parseImportMusicSheetResult(result, fallbackPlatform = info.platform)
        }
    }

    override suspend fun importMusicItem(urlLike: String): MusicItem? {
        if (!hasMethod("importMusicItem")) {
            logApiCallSkipped("importMusicItem", mapOf("urlLike" to urlLike))
            return null
        }
        return executeApiCall(
            method = "importMusicItem",
            inputFields = mapOf("urlLike" to urlLike),
            onFailure = { null },
        ) {
            val result = engine.evaluate<Any?>(
                "await __plugin.importMusicItem('${escapeJsString(urlLike)}')",
            )
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseImportMusicItemResult(map, fallbackPlatform = info.platform)
        }
    }

    override suspend fun getTopLists(): List<MusicSheetGroupItem> {
        if (!hasMethod("getTopLists")) {
            logApiCallSkipped("getTopLists")
            return emptyList()
        }
        return executeApiCall(
            method = "getTopLists",
            onFailure = { emptyList() },
            resultFields = { result -> mapOf("resultCount" to result.size) },
        ) {
            val result = engine.evaluate<Any?>("await __plugin.getTopLists()")
            val list = result as? List<*> ?: return@executeApiCall emptyList()
            JsBridge.parseTopListGroups(list)
        }
    }

    override suspend fun getTopListDetail(
        topListItem: MusicSheetItemBase,
        page: Int,
    ): TopListDetailResult? {
        if (!hasMethod("getTopListDetail")) {
            logApiCallSkipped(
                "getTopListDetail",
                mapOf(
                    "musicSheetId" to topListItem.id,
                    "page" to page,
                ),
            )
            return null
        }
        return executeApiCall(
            method = "getTopListDetail",
            inputFields = mapOf(
                "musicSheetId" to topListItem.id,
                "page" to page,
            ),
            onFailure = { null },
            resultFields = { result ->
                mapOf("isEnd" to (result?.isEnd ?: true))
            },
        ) {
            injectGlobalMap("__topListItem", JsBridge.musicSheetItemToMap(topListItem))
            val result = engine.evaluate<Any?>(
                "await __plugin.getTopListDetail(__topListItem, $page)",
            )
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseTopListDetailResult(map, fallbackPlatform = info.platform)
        }
    }

    override suspend fun getMusicSheetInfo(
        sheetItem: MusicSheetItemBase,
        page: Int,
    ): MusicSheetInfoResult? {
        if (!hasMethod("getMusicSheetInfo")) {
            logApiCallSkipped(
                "getMusicSheetInfo",
                mapOf(
                    "musicSheetId" to sheetItem.id,
                    "page" to page,
                ),
            )
            return null
        }
        return executeApiCall(
            method = "getMusicSheetInfo",
            inputFields = mapOf(
                "musicSheetId" to sheetItem.id,
                "page" to page,
            ),
            onFailure = { null },
            resultFields = { result ->
                mapOf(
                    "resultCount" to (result?.musicList?.size ?: 0),
                    "isEnd" to (result?.isEnd ?: true),
                )
            },
        ) {
            injectGlobalMap("__sheetItem", JsBridge.musicSheetItemToMap(sheetItem))
            val result = engine.evaluate<Any?>(
                "await __plugin.getMusicSheetInfo(__sheetItem, $page)",
            )
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseMusicSheetInfoResult(map, fallbackPlatform = info.platform)
        }
    }

    override suspend fun getRecommendSheetTags(): RecommendSheetTagsResult? {
        if (!hasMethod("getRecommendSheetTags")) {
            logApiCallSkipped("getRecommendSheetTags")
            return null
        }
        return executeApiCall(
            method = "getRecommendSheetTags",
            onFailure = { null },
            resultFields = { result ->
                mapOf(
                    "pinnedCount" to (result?.pinned?.size ?: 0),
                    "dataGroupCount" to (result?.data?.size ?: 0),
                    "hasData" to (result != null),
                )
            },
        ) {
            val result = engine.evaluate<Any?>("await __plugin.getRecommendSheetTags()")
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseRecommendSheetTagsResult(map)
        }
    }

    override suspend fun getRecommendSheetsByTag(
        tag: Map<String, Any?>,
        page: Int,
    ): PaginationResult<MusicSheetItemBase>? {
        if (!hasMethod("getRecommendSheetsByTag")) {
            logApiCallSkipped(
                "getRecommendSheetsByTag",
                mapOf("page" to page),
            )
            return null
        }
        return executeApiCall(
            method = "getRecommendSheetsByTag",
            inputFields = mapOf("page" to page),
            onFailure = { null },
            resultFields = { result ->
                mapOf(
                    "resultCount" to (result?.data?.size ?: 0),
                    "isEnd" to (result?.isEnd ?: true),
                )
            },
        ) {
            injectGlobalMap("__recommendTag", tag)
            val result = engine.evaluate<Any?>(
                "await __plugin.getRecommendSheetsByTag(__recommendTag, $page)",
            )
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseRecommendSheetsByTagResult(map)
        }
    }

    override suspend fun getMusicComments(
        musicItem: MusicItem,
        page: Int,
    ): PaginationResult<MusicComment>? {
        if (!hasMethod("getMusicComments")) {
            logApiCallSkipped(
                "getMusicComments",
                mapOf(
                    "musicItemId" to musicItem.id,
                    "page" to page,
                ),
            )
            return null
        }
        return executeApiCall(
            method = "getMusicComments",
            inputFields = mapOf(
                "musicItemId" to musicItem.id,
                "page" to page,
            ),
            onFailure = { null },
            resultFields = { result ->
                mapOf(
                    "resultCount" to (result?.data?.size ?: 0),
                    "isEnd" to (result?.isEnd ?: true),
                )
            },
        ) {
            injectGlobalMap("__musicItem", JsBridge.musicItemToMap(musicItem))
            val result = engine.evaluate<Any?>(
                "await __plugin.getMusicComments(__musicItem, $page)",
            )
            val map = toMap(result) ?: return@executeApiCall null
            JsBridge.parseMusicCommentsResult(map)
        }
    }

    suspend fun destroy() {
        engine.close()
    }

    suspend fun updateUserVariables(values: Map<String, String>) {
        val jsonStr = kotlinx.serialization.json.Json.encodeToString(values)
        engine.evaluate<Any?>("globalThis.__userVariables = JSON.parse('${escapeJsString(jsonStr)}')")
    }

    // -- Internal helpers --

    private suspend fun hasMethod(name: String): Boolean {
        return try {
            engine.evaluate<Boolean>("typeof __plugin.$name === 'function'")
        } catch (error: Exception) {
            rethrowIfExternalCancellation(error)
            false
        }
    }

    private fun logApiCallSkipped(
        method: String,
        inputFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_api_call_skipped",
            fields = apiBaseFields(method) + mapOf(
                "result" to LogFields.Result.SKIPPED,
                "reason" to LogFields.Reason.UNSUPPORTED,
            ) + inputFields,
        )
    }

    private fun apiBaseFields(method: String): Map<String, Any?> =
        mapOf(
            "platform" to info.platform,
            "pluginVersion" to info.version.orEmpty(),
            "method" to method,
            "operation" to "plugin_api_call",
        )

    private fun countField(result: Any?): Map<String, Any?> {
        val count = when (result) {
            is List<*> -> result.size
            is SearchResult -> result.data.size
            is PaginationResult<*> -> result.data.size
            is TopListDetailResult -> result.musicList.size
            is MusicSheetInfoResult -> result.musicList.size
            is AlbumInfoResult -> result.musicList.size
            is ArtistWorksResult -> result.musicList.size
            is RecommendSheetTagsResult -> result.pinned.size + result.data.sumOf { it.data.size }
            is LyricResult -> result.lines.size
            else -> null
        }
        return count?.let { mapOf("count" to it) }.orEmpty()
    }

    private fun rethrowIfExternalCancellation(error: Exception) {
        // Keep existing timeout fallbacks, but never turn parent-job cancellation into plugin errors.
        if (error is CancellationException && error !is TimeoutCancellationException) {
            throw error
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

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
