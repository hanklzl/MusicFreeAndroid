package com.zili.android.musicfreeandroid.plugin.engine

import android.content.Context
import android.util.Log
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext

/**
 * Registers CommonJS-like `require()` support in QuickJS for built-in plugin dependencies.
 *
 * Module sources are loaded from assets once and cached in memory for reuse.
 */
object RequireShim {

    private const val TAG = "RequireShim"

    private val moduleAssetPaths = linkedMapOf(
        "crypto-js" to "jslibs/crypto-js.js",
        "qs" to "jslibs/qs.js",
        "he" to "jslibs/he.js",
        "dayjs" to "jslibs/dayjs.min.js",
        "big-integer" to "jslibs/BigInteger.min.js",
    )

    private val sourceLock = Any()

    @Volatile
    private var cachedModuleSources: Map<String, String>? = null

    fun register(appContext: Context, context: QuickJSContext) {
        val moduleSources = loadModuleSources(appContext)

        context.evaluate("globalThis.__requireCache = globalThis.__requireCache || Object.create(null);")
        context.evaluate(
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

        for ((moduleName, source) in moduleSources) {
            try {
                registerCommonJsModule(context, moduleName, source)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register module '$moduleName'", e)
            }
        }

        context.globalObject.setProperty("__require", JSCallFunction { args ->
            val moduleName = args.getOrNull(0)?.toString()?.trim().orEmpty()
            val moduleExpr = "globalThis.__requireCache[${toJsString(moduleName)}]"
            val module = context.evaluate(moduleExpr)
            if (module != null && module.toString() != "undefined") {
                module
            } else {
                Log.w(TAG, "require('$moduleName') not supported, returning empty object")
                context.createNewJSObject()
            }
        })
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read module asset '$assetPath' for '$moduleName'", e)
                }
            }

            cachedModuleSources = loaded
            return loaded
        }
    }

    private fun registerCommonJsModule(
        context: QuickJSContext,
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

        context.evaluate(script)
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
