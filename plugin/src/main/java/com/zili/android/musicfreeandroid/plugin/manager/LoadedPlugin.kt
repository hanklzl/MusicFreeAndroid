package com.zili.android.musicfreeandroid.plugin.manager

import android.util.Log
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.PluginApi
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.engine.JsBridge
import com.zili.android.musicfreeandroid.plugin.engine.JsEngine
import kotlinx.coroutines.withTimeout

/**
 * A loaded JS plugin backed by its own [JsEngine] instance.
 * Implements [PluginApi] by delegating calls to the JS plugin object (`__plugin`).
 */
class LoadedPlugin(
    override val info: PluginInfo,
    private val engine: JsEngine,
    val filePath: String,
) : PluginApi {

    companion object {
        private const val TAG = "LoadedPlugin"
        private const val TIMEOUT_MS = 30_000L
    }

    override suspend fun search(query: String, page: Int, type: String): SearchResult {
        return withTimeout(TIMEOUT_MS) {
            engine.runOnJsThread {
                try {
                    val escapedQuery = query.replace("\\", "\\\\").replace("'", "\\'")
                    val asyncExpr =
                        "async function() { var r = await __plugin.search('$escapedQuery', $page, '$type'); return JSON.stringify(r); }()"
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

    fun destroy() {
        engine.destroy()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonToMap(json: String): Map<String, Any?> {
        // Use kotlinx.serialization or a simple JS-side parse.
        // Leverage the engine itself to parse JSON back to a map.
        val ctx = engine.context
            ?: throw IllegalStateException("Engine context is null")
        val parsed = ctx.parse(json)
        return if (parsed is com.whl.quickjs.wrapper.JSObject) {
            engine.jsObjectToMap(parsed)
        } else {
            emptyMap()
        }
    }
}
