package com.hank.musicfree.plugin.engine

import android.content.Context
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields

/**
 * Registers CommonJS-like `require()` support in QuickJs for built-in plugin dependencies.
 *
 * Module sources are loaded from assets once and cached in memory for reuse.
 */
object RequireShim {

    private const val TAG = "RequireShim"

    private val moduleAssetPaths = linkedMapOf(
        "cheerio" to "jslibs/cheerio.min.js",
        "crypto-js" to "jslibs/crypto-js.js",
        "qs" to "jslibs/qs.js",
        "he" to "jslibs/he.js",
        "dayjs" to "jslibs/dayjs.min.js",
        "big-integer" to "jslibs/BigInteger.min.js",
        // Minimal `webdav` shim — only createClient / getFileContents / putFileContents.
        // Backed by WebDavShim.register's `__webdav_get` / `__webdav_put` globals.
        "webdav" to "jslibs/webdav.js",
    )

    private val sourceLock = Any()

    @Volatile
    private var cachedModuleSources: Map<String, String>? = null

    /**
     * Register the `__require` global function and pre-load all built-in modules.
     * Must be called after [AxiosShim.register] so that axios is available in the require cache.
     */
    suspend fun register(appContext: Context, engine: JsEngine) {
        val moduleSources = loadModuleSources(appContext)

        // Initialize require cache and add axios if available
        engine.evaluate<Any?>(
            "globalThis.__requireCache = globalThis.__requireCache || Object.create(null);"
        )
        engine.evaluate<Any?>(
            """
            (function() {
              if (typeof globalThis.axios !== "undefined") {
                if (globalThis.axios.default === undefined) {
                  globalThis.axios.default = globalThis.axios;
                }
                globalThis.__requireCache["axios"] = globalThis.axios;
              }
            })();
            """.trimIndent()
        )

        // Register each module by evaluating its source in a CommonJS wrapper
        for ((moduleName, source) in moduleSources) {
            try {
                registerCommonJsModule(engine, moduleName, source)
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "require_module_register_success",
                    fields = mapOf(
                        "module" to moduleName,
                        "moduleName" to moduleName,
                        "assetPath" to moduleAssetPaths[moduleName].orEmpty(),
                        "status" to "success",
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
            } catch (e: Exception) {
                MfLog.error(
                    category = LogCategory.PLUGIN,
                    event = "require_module_register_failed",
                    throwable = e,
                    fields = mapOf(
                        "module" to moduleName,
                        "moduleName" to moduleName,
                        "assetPath" to moduleAssetPaths[moduleName].orEmpty(),
                        "status" to "failed",
                        "result" to LogFields.Result.FAILURE,
                        "reason" to "register_failed",
                    ),
                )
            }
        }

        engine.function<Unit>("__log_require_missing") { args ->
            val moduleName = args.getOrNull(0)?.toString().orEmpty()
            MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "require_module_missing",
                fields = mapOf(
                    "module" to moduleName,
                    "moduleName" to moduleName,
                    "result" to LogFields.Result.SKIPPED,
                    "reason" to LogFields.Reason.UNSUPPORTED,
                ),
            )
        }

        // Define __require as a pure JS function that reads from cache
        // (We can't use engine.function{} for this because we need to return
        // the actual JS object from __requireCache, not a Kotlin copy)
        engine.evaluate<Any?>(
            """
            (function() {
              var cache = globalThis.__requireCache;
              globalThis.__require = function(name) {
                if (cache[name] !== undefined) return cache[name];
                try {
                  if (typeof globalThis.__log_require_missing === "function") {
                    globalThis.__log_require_missing(name);
                  }
                } catch (_ignored) {}
                console.warn("require('" + name + "') not supported, returning empty object");
                return {};
              };
            })();
            """.trimIndent()
        )
    }

    private fun loadModuleSources(appContext: Context): Map<String, String> {
        cachedModuleSources?.let { return it }

        synchronized(sourceLock) {
            cachedModuleSources?.let { return it }

            val loaded = LinkedHashMap<String, String>()
            for ((moduleName, assetPath) in moduleAssetPaths) {
                try {
                    val source = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
                    loaded[moduleName] = source
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "require_module_asset_read_success",
                        fields = mapOf(
                            "module" to moduleName,
                            "moduleName" to moduleName,
                            "assetPath" to assetPath,
                            "status" to "success",
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                } catch (e: Exception) {
                    MfLog.error(
                        category = LogCategory.PLUGIN,
                        event = "require_module_asset_read_failed",
                        throwable = e,
                        fields = mapOf(
                            "module" to moduleName,
                            "moduleName" to moduleName,
                            "assetPath" to assetPath,
                            "status" to "failed",
                            "result" to LogFields.Result.FAILURE,
                            "reason" to "asset_read_failed",
                        ),
                    )
                }
            }

            cachedModuleSources = loaded
            return loaded
        }
    }

    private suspend fun registerCommonJsModule(
        engine: JsEngine,
        moduleName: String,
        source: String,
    ) {
        val script = buildString(source.length + 512) {
            append("(function(){")
            append("var __prevModule = globalThis.module;")
            append("var __prevExports = globalThis.exports;")
            append("var __module = { exports: {} };")
            append("globalThis.module = __module;")
            append("globalThis.exports = __module.exports;")
            append("try {")
            append(source)
            append("\n;")
            append("if (__module.exports && __module.exports.default === undefined) {")
            append("__module.exports.default = __module.exports;")
            append("}")
            append("globalThis.__requireCache[")
            append(toJsString(moduleName))
            append("] = __module.exports;")
            append("} finally {")
            append("if (__prevModule === undefined) {")
            append("try { delete globalThis.module; } catch (_ignored) { globalThis.module = undefined; }")
            append("} else { globalThis.module = __prevModule; }")
            append("if (__prevExports === undefined) {")
            append("try { delete globalThis.exports; } catch (_ignored2) { globalThis.exports = undefined; }")
            append("} else { globalThis.exports = __prevExports; }")
            append("}")
            append("})();")
        }

        engine.evaluate<Any?>(script)
    }

    private fun toJsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "'$escaped'"
    }
}
