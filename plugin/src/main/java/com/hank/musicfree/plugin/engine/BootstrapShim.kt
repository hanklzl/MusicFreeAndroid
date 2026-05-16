package com.hank.musicfree.plugin.engine

import android.content.Context
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog

/**
 * Plugin runtime bootstrap helpers — globals that must exist BEFORE any
 * `require()` shim runs or user code executes.
 *
 * Currently this is just the `URL` constructor polyfill (loaded from
 * `assets/jslibs/url-polyfill.js`). QuickJS-kt does not ship `URL` because
 * the WHATWG URL spec is not part of ECMAScript; RN MusicFree plugins use it
 * heavily, so we install a minimal implementation at boot.
 *
 * The polyfill itself is idempotent and self-bailing: if a future QuickJS-kt
 * release adds `URL` natively, the JS-side `typeof global.URL === 'function'`
 * check shortcircuits before reinstalling.
 *
 * Design source: plugin-engine-alignment design §8.3.
 */
object BootstrapShim {

    private const val URL_POLYFILL_ASSET = "jslibs/url-polyfill.js"

    @Volatile
    private var cachedUrlPolyfill: String? = null

    /**
     * Evaluate runtime polyfills on the given engine. Must be called BEFORE
     * [RequireShim.register] (so polyfills are visible to any `require()`-loaded
     * module) and BEFORE user plugin code is evaluated.
     */
    suspend fun register(appContext: Context, engine: JsEngine) {
        val polyfill = loadUrlPolyfill(appContext) ?: return
        try {
            engine.evaluate<Any?>(polyfill)
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "bootstrap_polyfill_registered",
                fields = mapOf("polyfill" to "url", "status" to "success"),
            )
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "bootstrap_polyfill_failed",
                throwable = e,
                fields = mapOf("polyfill" to "url", "status" to "failed"),
            )
            // Intentionally do not rethrow: the polyfill failing should not abort
            // plugin load. The downstream `typeof URL === 'function'` probe in
            // plugin code will surface the issue clearly.
        }
    }

    private fun loadUrlPolyfill(appContext: Context): String? {
        cachedUrlPolyfill?.let { return it }
        return try {
            val source = appContext.assets
                .open(URL_POLYFILL_ASSET)
                .bufferedReader()
                .use { it.readText() }
            cachedUrlPolyfill = source
            source
        } catch (e: Exception) {
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "bootstrap_polyfill_asset_read_failed",
                throwable = e,
                fields = mapOf(
                    "polyfill" to "url",
                    "assetPath" to URL_POLYFILL_ASSET,
                    "status" to "failed",
                ),
            )
            null
        }
    }
}
