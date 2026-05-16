# QuickJS 引擎迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将插件引擎从 `wang.harlon.quickjs:wrapper-android:3.2.3` 迁移到 `io.github.dokar3:quickjs-kt-android:1.0.5`，同时修复嵌套数组转换崩溃、cheerio 缺失、getMediaSource 无 fallback 三个 bug。

**Architecture:** `:plugin` 模块内部重构。JsEngine 重写为 quickjs-kt 的轻量 wrapper；AxiosShim 改为 asyncFunction 实现真正异步 HTTP；RequireShim 适配新 API 并添加 cheerio；LoadedPlugin 利用原生 Promise + JsObject-as-Map 大幅简化。对外接口（PluginApi、PluginManager 公共 API）不变。

**Tech Stack:** quickjs-kt 1.0.5, Kotlin Coroutines, OkHttp 4.12.0, Hilt

**设计文档:** `docs/superpowers/specs/2026-04-19-quickjs-kt-migration-design.md`

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `gradle/libs.versions.toml` | 修改 | 替换 quickjs 依赖声明 |
| `plugin/build.gradle.kts` | 修改 | 替换依赖引用 |
| `plugin/.../engine/JsEngine.kt` | 重写 | quickjs-kt wrapper，生命周期 + evaluate |
| `plugin/.../engine/AxiosShim.kt` | 重写 | asyncFunction 异步 HTTP |
| `plugin/.../engine/RequireShim.kt` | 重写 | 适配新 API + 添加 cheerio |
| `plugin/.../manager/LoadedPlugin.kt` | 重写 | 去掉 JSON.stringify 往返，原生 Promise |
| `plugin/.../manager/PluginManager.kt` | 修改 | 适配新的 JsEngine / shim 注册方式 |
| `plugin/src/main/assets/jslibs/cheerio.min.js` | 新增 | cheerio standalone bundle |

路径前缀：`plugin/src/main/java/com/hank/musicfree/plugin/`

---

### Task 1: 依赖替换

**Files:**
- Modify: `gradle/libs.versions.toml:25,82`
- Modify: `plugin/build.gradle.kts:38`

- [ ] **Step 1: 替换 libs.versions.toml 中的 QuickJS 版本和库声明**

在 `[versions]` 段，将第 25 行：
```
quickjsWrapper = "3.2.3"
```
替换为：
```
quickjsKt = "1.0.5"
```

在 `[libraries]` 段，将第 82 行：
```
quickjs-wrapper-android = { group = "wang.harlon.quickjs", name = "wrapper-android", version.ref = "quickjsWrapper" }
```
替换为：
```
quickjs-kt-android = { group = "io.github.dokar3", name = "quickjs-kt-android", version.ref = "quickjsKt" }
```

- [ ] **Step 2: 替换 plugin/build.gradle.kts 中的依赖引用**

将第 38 行：
```kotlin
implementation(libs.quickjs.wrapper.android)
```
替换为：
```kotlin
implementation(libs.quickjs.kt.android)
```

- [ ] **Step 3: Gradle Sync 验证依赖解析**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && ./gradlew :plugin:dependencies --configuration debugRuntimeClasspath 2>&1 | grep -E "dokar3|quickjs"`

Expected: 输出包含 `io.github.dokar3:quickjs-kt-android:1.0.5`，不再包含 `wang.harlon.quickjs`。

注意：此时编译会失败（旧 import 未替换），这是预期的。

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml plugin/build.gradle.kts
git commit -m "build: replace quickjs-wrapper-android with quickjs-kt-android 1.0.5"
```

---

### Task 2: 添加 cheerio asset

**Files:**
- Create: `plugin/src/main/assets/jslibs/cheerio.min.js`

- [ ] **Step 1: 下载 cheerio standalone browser bundle**

```bash
cd /tmp && npm pack cheerio@1.0.0 && tar -xzf cheerio-1.0.0.tgz
cp package/dist/browser/cheerio.min.js /Users/zili/code/android/MusicFreeAndroid/plugin/src/main/assets/jslibs/cheerio.min.js
rm -rf package cheerio-1.0.0.tgz
```

如果 `cheerio@1.0.0` 没有 `dist/browser/cheerio.min.js`，则改用 CDN UMD bundle：

```bash
curl -L "https://cdn.jsdelivr.net/npm/cheerio@1.0.0/dist/browser/cheerio.min.js" \
  -o /Users/zili/code/android/MusicFreeAndroid/plugin/src/main/assets/jslibs/cheerio.min.js
```

- [ ] **Step 2: 验证文件存在且可读**

```bash
wc -c /Users/zili/code/android/MusicFreeAndroid/plugin/src/main/assets/jslibs/cheerio.min.js
```

Expected: 文件大小在 50KB-200KB 之间（minified cheerio 的合理范围）。

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/assets/jslibs/cheerio.min.js
git commit -m "feat(plugin): add cheerio standalone bundle to assets"
```

---

### Task 3: 重写 JsEngine

**Files:**
- Rewrite: `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsEngine.kt`

- [ ] **Step 1: 完整重写 JsEngine.kt**

```kotlin
package com.hank.musicfree.plugin.engine

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers

/**
 * Wrapper around [QuickJs] providing lifecycle management.
 *
 * All [evaluate] calls are suspend and internally dispatched by quickjs-kt.
 * The old [evaluateAsync] hack, [jsObjectToMap], [jsArrayToList], [setGlobalMap]
 * and manual thread management are no longer needed.
 */
class JsEngine private constructor(
    val quickJs: QuickJs,
) {

    companion object {
        private const val TAG = "JsEngine"

        /**
         * Create a new JsEngine backed by a fresh QuickJs context.
         * Uses a single-thread dispatcher for JS thread affinity.
         */
        fun create(): JsEngine {
            val qjs = QuickJs.create(Dispatchers.IO.limitedParallelism(1))
            return JsEngine(qjs)
        }
    }

    /**
     * Evaluate JavaScript code and return the result.
     * Supports top-level `await` — Promises are automatically resolved.
     */
    suspend inline fun <reified T> evaluate(code: String): T {
        return quickJs.evaluate<T>(code)
    }

    /**
     * Evaluate JavaScript code, returning the result as a nullable type.
     * Returns null for JS `undefined` and `null`.
     */
    suspend fun evaluateOrNull(code: String): Any? {
        return try {
            quickJs.evaluate<Any?>(code)
        } catch (e: Exception) {
            Log.w(TAG, "evaluateOrNull failed", e)
            null
        }
    }

    /**
     * Register a synchronous global function callable from JS.
     */
    inline fun <reified R : Any?> function(
        name: String,
        crossinline block: (args: Array<Any?>) -> R,
    ) {
        quickJs.function(name) { args: Array<Any?> -> block(args) }
    }

    /**
     * Register an async global function callable from JS via `await`.
     * The [block] is a suspend function — JS thread is released during execution.
     */
    inline fun <reified R> asyncFunction(
        name: String,
        crossinline block: suspend (args: Array<Any?>) -> R,
    ) {
        quickJs.asyncFunction(name) { args: Array<Any?> -> block(args) }
    }

    /**
     * Define a named JS object with nested functions/properties.
     */
    fun define(name: String, block: com.dokar.quickjs.binding.ObjectBindingScope.() -> Unit) {
        quickJs.define(name, block)
    }

    /** Close the QuickJs context and release all resources. */
    fun close() {
        try {
            quickJs.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing QuickJs context", e)
        }
    }

    val isClosed: Boolean get() = quickJs.isClosed
}
```

- [ ] **Step 2: 验证文件编译（会因 LoadedPlugin/PluginManager 依赖旧 API 而失败，预期中）**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && ./gradlew :plugin:compileDebugKotlin 2>&1 | tail -20`

Expected: JsEngine.kt 本身无编译错误，错误来自 LoadedPlugin.kt / PluginManager.kt / AxiosShim.kt / RequireShim.kt（引用了旧 API）。

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/JsEngine.kt
git commit -m "refactor(plugin): rewrite JsEngine on quickjs-kt"
```

---

### Task 4: 重写 AxiosShim

**Files:**
- Rewrite: `plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt`

- [ ] **Step 1: 完整重写 AxiosShim.kt**

```kotlin
package com.hank.musicfree.plugin.engine

import android.util.Log
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.toJsObject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Registers an `axios`-like global object in the QuickJs context.
 * HTTP requests are truly async — JS thread is released during network I/O.
 */
object AxiosShim {

    private const val TAG = "AxiosShim"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Register the `axios` global with `get`, `post`, `request`, `default`, and `create` methods.
     */
    suspend fun register(engine: JsEngine) {
        // Bind async functions on the axios object
        engine.define("axios") {
            asyncFunction<Any?>("get") { args ->
                handleGet(args)
            }
            asyncFunction<Any?>("post") { args ->
                handlePost(args)
            }
            asyncFunction<Any?>("request") { args ->
                handleRequest(args)
            }
            function<Any?>("create") { _ ->
                // create() returns axios itself — JS-side alias set below
                null
            }
        }

        // Set up CommonJS interop aliases via JS:
        // axios.default = axios, axios.default.get/post/request, axios.create returns axios
        engine.evaluate<Any?>(
            """
            (function() {
              var ax = globalThis.axios;
              ax.default = ax;
              ax.create = function() { return ax; };
            })();
            """.trimIndent()
        )
    }

    // -- Request handlers --

    private suspend fun handleGet(args: Array<Any?>): Any? {
        return try {
            val url = args.getOrNull(0)?.toString()
                ?: return buildErrorResponse("URL is required")
            val config = args.getOrNull(1) as? Map<*, *>
            performGet(url = url, config = config)
        } catch (e: Exception) {
            Log.e(TAG, "axios.get failed", e)
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private suspend fun handlePost(args: Array<Any?>): Any? {
        return try {
            val url = args.getOrNull(0)?.toString()
                ?: return buildErrorResponse("URL is required")
            val bodyArg = args.getOrNull(1)
            val config = args.getOrNull(2) as? Map<*, *>
            performPost(url = url, bodyArg = bodyArg, config = config)
        } catch (e: Exception) {
            Log.e(TAG, "axios.post failed", e)
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    private suspend fun handleRequest(args: Array<Any?>): Any? {
        val config = args.getOrNull(0) as? Map<*, *>
            ?: return buildErrorResponse("Config object is required")

        val method = config["method"]?.toString()?.lowercase().orEmpty().ifBlank { "get" }
        val url = config["url"]?.toString()
            ?: return buildErrorResponse("Config.url is required")

        return try {
            when (method) {
                "post" -> performPost(url = url, bodyArg = config["data"], config = config)
                "get" -> performGet(url = url, config = config)
                else -> buildErrorResponse("Unsupported method: $method")
            }
        } catch (e: Exception) {
            Log.e(TAG, "axios.request failed", e)
            buildErrorResponse(e.message ?: "Unknown error")
        }
    }

    // -- HTTP execution (async) --

    private suspend fun performGet(url: String, config: Map<*, *>?): Map<String, Any?> {
        val fullUrl = buildUrlWithParams(url, config)
        val requestBuilder = Request.Builder().url(fullUrl).get()
        applyHeaders(requestBuilder, config)

        val response = client.newCall(requestBuilder.build()).await()
        return response.use {
            val body = readResponseBody(it)
            logResponsePreview(method = "GET", url = fullUrl, status = it.code, body = body)
            buildResponse(it.code, body)
        }
    }

    private suspend fun performPost(
        url: String,
        bodyArg: Any?,
        config: Map<*, *>?,
    ): Map<String, Any?> {
        val fullUrl = buildUrlWithParams(url, config)
        val contentType = resolveContentType(config, bodyArg)
        val bodyString = when (bodyArg) {
            is Map<*, *> -> {
                if (contentType.contains("application/x-www-form-urlencoded", ignoreCase = true)) {
                    toFormUrlEncoded(bodyArg)
                } else {
                    jsonStringify(bodyArg)
                }
            }
            is String -> bodyArg
            null -> ""
            else -> bodyArg.toString()
        }
        val requestBody = bodyString.toRequestBody(contentType.toMediaTypeOrNull())

        val requestBuilder = Request.Builder().url(fullUrl).post(requestBody)
        applyHeaders(requestBuilder, config)

        val response = client.newCall(requestBuilder.build()).await()
        return response.use {
            val body = readResponseBody(it)
            logResponsePreview(method = "POST", url = fullUrl, status = it.code, body = body)
            buildResponse(it.code, body)
        }
    }

    // -- OkHttp suspend extension --

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { cancel() }
    }

    // -- Helpers (ported from old AxiosShim, adapted for Map-based config) --

    private fun resolveContentType(config: Map<*, *>?, bodyArg: Any?): String {
        val explicitContentType = getHeaderIgnoreCase(config, "content-type")
        if (!explicitContentType.isNullOrBlank()) {
            return explicitContentType
        }
        return when (bodyArg) {
            is String -> "application/x-www-form-urlencoded; charset=utf-8"
            is Map<*, *> -> "application/json; charset=utf-8"
            else -> "text/plain; charset=utf-8"
        }
    }

    private fun getHeaderIgnoreCase(config: Map<*, *>?, key: String): String? {
        val headers = config?.get("headers") as? Map<*, *> ?: return null
        return headers.entries.firstOrNull { (k, _) ->
            k.toString().equals(key, ignoreCase = true)
        }?.value?.toString()
    }

    private fun toFormUrlEncoded(data: Map<*, *>): String {
        return data.entries.mapNotNull { (k, v) ->
            val key = k?.toString() ?: return@mapNotNull null
            val value = v?.toString() ?: ""
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }.joinToString("&")
    }

    private fun readResponseBody(response: Response): String? {
        val body = response.body ?: return null
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val bytes = body.bytes()
        if (bytes.isEmpty()) return ""

        val encoding = response.header("Content-Encoding")?.lowercase().orEmpty()
        val decodedBytes = try {
            when {
                encoding.contains("gzip") ->
                    GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                encoding.contains("deflate") ->
                    InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                else -> bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode response body with Content-Encoding='$encoding'", e)
            bytes
        }
        return decodedBytes.toString(charset)
    }

    private fun logResponsePreview(method: String, url: String, status: Int, body: String?) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val preview = body?.replace("\n", " ")?.replace("\r", " ")?.take(240) ?: ""
        Log.d(TAG, "$method $url -> $status body=$preview")
    }

    private fun buildUrlWithParams(baseUrl: String, config: Map<*, *>?): String {
        if (config == null) return baseUrl
        val params = config["params"] as? Map<*, *> ?: return baseUrl

        val queryParts = params.entries.mapNotNull { (k, v) ->
            val key = k?.toString() ?: return@mapNotNull null
            val value = v?.toString() ?: return@mapNotNull null
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        if (queryParts.isEmpty()) return baseUrl
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator${queryParts.joinToString("&")}"
    }

    private fun applyHeaders(builder: Request.Builder, config: Map<*, *>?) {
        if (config == null) return
        val headers = config["headers"] as? Map<*, *> ?: return
        for ((key, value) in headers) {
            val k = key?.toString() ?: continue
            val v = value?.toString() ?: continue
            builder.addHeader(k, v)
        }
    }

    private fun buildResponse(status: Int, body: String?): Map<String, Any?> {
        val data: Any? = if (body != null) {
            try {
                parseJsonValue(body)
            } catch (_: Exception) {
                body
            }
        } else {
            ""
        }
        return mapOf("status" to status, "data" to data)
    }

    private fun buildErrorResponse(message: String): Map<String, Any?> {
        return mapOf("status" to -1, "data" to message)
    }

    /**
     * Parse a JSON string into Kotlin types (Map/List/String/Number/Boolean/null).
     * Uses org.json (Android built-in) to avoid re-entering the JS engine.
     */
    private fun parseJsonValue(json: String): Any? {
        val trimmed = json.trim()
        return when {
            trimmed.startsWith("{") -> jsonObjectToMap(JSONObject(trimmed))
            trimmed.startsWith("[") -> jsonArrayToList(JSONArray(trimmed))
            else -> trimmed // raw string
        }
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in obj.keys()) {
            map[key] = jsonElementToKotlin(obj.opt(key))
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any?> {
        return (0 until arr.length()).map { jsonElementToKotlin(arr.opt(it)) }
    }

    private fun jsonElementToKotlin(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        else -> value // String, Number, Boolean
    }

    /**
     * Stringify a Kotlin Map to JSON using org.json.
     */
    private fun jsonStringify(map: Map<*, *>): String {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k?.toString() ?: continue, v)
        }
        return obj.toString()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt
git commit -m "refactor(plugin): rewrite AxiosShim with async HTTP via quickjs-kt"
```

---

### Task 5: 重写 RequireShim

**Files:**
- Rewrite: `plugin/src/main/java/com/hank/musicfree/plugin/engine/RequireShim.kt`

- [ ] **Step 1: 完整重写 RequireShim.kt**

```kotlin
package com.hank.musicfree.plugin.engine

import android.content.Context
import android.util.Log

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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register module '$moduleName'", e)
            }
        }

        // Bind the __require function
        engine.function<Any?>("__require") { args ->
            val moduleName = args.getOrNull(0)?.toString()?.trim().orEmpty()
            // Return value is evaluated from the cache in the same JS context
            // We cannot directly return here since function{} is synchronous and
            // we need to read from JS globals. Instead, we set a temp var and
            // the caller reads it. But actually, quickjs-kt function{} return
            // values ARE passed back to JS. However, we need the actual JS object
            // from __requireCache, not a Kotlin copy.
            //
            // Solution: return a sentinel and use a JS wrapper that reads from cache.
            moduleName // return the module name as sentinel
        }

        // Replace __require with a JS function that reads from cache
        engine.evaluate<Any?>(
            """
            (function() {
              var cache = globalThis.__requireCache;
              globalThis.__require = function(name) {
                if (cache[name] !== undefined) return cache[name];
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read module asset '$assetPath' for '$moduleName'", e)
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
```

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/RequireShim.kt
git commit -m "refactor(plugin): rewrite RequireShim for quickjs-kt, add cheerio module"
```

---

### Task 6: 重写 LoadedPlugin

**Files:**
- Rewrite: `plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt`

- [ ] **Step 1: 完整重写 LoadedPlugin.kt**

```kotlin
package com.hank.musicfree.plugin.manager

import android.util.Log
import com.dokar.quickjs.binding.JsObject
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.plugin.api.AlbumInfoResult
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.ArtistItemBase
import com.hank.musicfree.plugin.api.ArtistWorksResult
import com.hank.musicfree.plugin.api.LyricResult
import com.hank.musicfree.plugin.api.MusicComment
import com.hank.musicfree.plugin.api.MusicSheetGroupItem
import com.hank.musicfree.plugin.api.MusicSheetInfoResult
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.api.PaginationResult
import com.hank.musicfree.plugin.api.PluginApi
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.RecommendSheetTagsResult
import com.hank.musicfree.plugin.api.SearchResult
import com.hank.musicfree.plugin.api.TopListDetailResult
import com.hank.musicfree.plugin.engine.JsBridge
import com.hank.musicfree.plugin.engine.JsEngine
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
 * [JsObject] implements [Map], so it can be passed directly to [JsBridge] parsers.
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
                    fallbackUrl?.let { MediaSourceResult(url = it) }
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
```

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt
git commit -m "refactor(plugin): rewrite LoadedPlugin for quickjs-kt with native Promise and getMediaSource fallback"
```

---

### Task 7: 适配 PluginManager

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`

- [ ] **Step 1: 更新 imports**

移除旧的 quickjs-wrapper imports（文件顶部不再需要这些，它们只在 loadPluginFromFile 中通过 engine/shim 间接使用）。

确保以下 import 存在（大部分已存在）：
```kotlin
import com.hank.musicfree.plugin.engine.AxiosShim
import com.hank.musicfree.plugin.engine.JsEngine
import com.hank.musicfree.plugin.engine.RequireShim
```

- [ ] **Step 2: 重写 loadPluginFromFile 方法（第 765-843 行）**

将整个方法替换为：

```kotlin
    private suspend fun loadPluginFromFile(
        file: File,
        installSource: PluginInstallSource,
    ): LoadedPlugin? {
        val jsCode = file.readText()
        val engine = JsEngine.create()

        // Gather env values
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val lang = java.util.Locale.getDefault().toLanguageTag()

        val info = try {
            // Register shims
            AxiosShim.register(engine)
            RequireShim.register(appContext = context, engine = engine)

            // Inject env object
            engine.evaluate<Any?>("""
                globalThis.__env = {
                    os: 'android',
                    appVersion: '${appVersion.escapeForJsString()}',
                    lang: '${lang.escapeForJsString()}',
                    getUserVariables: function() { return globalThis.__userVariables || {}; }
                };
            """.trimIndent())

            // Wrap plugin code in CommonJS-style module pattern and assign to __plugin
            val wrappedCode = """
                var module = {exports: {}};
                var exports = module.exports;
                (function(require, module, exports, console, env) {
                    $jsCode
                })(globalThis.__require, module, exports, console, globalThis.__env);
                globalThis.__plugin = module.exports;
            """.trimIndent()

            try {
                engine.evaluate<Any?>(wrappedCode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to evaluate plugin code from ${file.name}", e)
                engine.close()
                return null
            }

            // Extract metadata from __plugin
            try {
                extractPluginInfo(engine)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract plugin info from ${file.name}", e)
                engine.close()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin from ${file.name}", e)
            engine.close()
            return null
        }

        // Inject userVariables snapshot for this plugin
        val userVars = pluginMetaStore.getUserVariables(info.platform).first()
        if (userVars.isNotEmpty()) {
            val jsonStr = Json.encodeToString(userVars)
            engine.evaluate<Any?>("globalThis.__userVariables = JSON.parse('${jsonStr.escapeForJsString()}')")
        }

        return LoadedPlugin(
            info = info,
            engine = engine,
            filePath = file.absolutePath,
            installSource = installSource,
        )
    }
```

- [ ] **Step 3: 更新 extractPluginInfo 方法（第 848-898 行）**

`extractPluginInfo` 内部调用 `engine.evaluate()` 返回 `Any?`。由于 quickjs-kt 的 `evaluate` 是 suspend，需要把 `extractPluginInfo` 改为 suspend：

```kotlin
    private suspend fun extractPluginInfo(engine: JsEngine): PluginInfo {
        suspend fun prop(name: String): String? {
            val result = engine.evaluate<Any?>("__plugin.$name")
            val str = result?.toString()
            return if (str == "undefined" || str == "null" || str.isNullOrBlank()) null else str
        }

        val platform = prop("platform")
            ?: throw IllegalStateException("Plugin missing required 'platform' property")

        val supportedSearchTypeStr = prop("supportedSearchType")
        val supportedSearchType = if (!supportedSearchTypeStr.isNullOrBlank()) {
            try {
                val json = engine.evaluate<Any?>("JSON.stringify(__plugin.supportedSearchType)")?.toString()
                if (json != null && json.startsWith("[")) {
                    json.removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }
                        .filter { it.isNotBlank() }
                } else {
                    listOf("music")
                }
            } catch (_: Exception) {
                listOf("music")
            }
        } else {
            listOf("music")
        }

        val hintsJson = try {
            val raw = engine.evaluate<Any?>("JSON.stringify(__plugin.hints)")?.toString()
            if (raw != null && raw != "undefined" && raw != "null" && raw.startsWith("{")) {
                kotlinx.serialization.json.Json.decodeFromString<Map<String, List<String>>>(raw)
            } else null
        } catch (e: Exception) { null }

        return PluginInfo(
            platform = platform,
            version = prop("version"),
            author = prop("author"),
            description = prop("description"),
            srcUrl = prop("srcUrl"),
            supportedSearchType = supportedSearchType,
            appVersion = prop("appVersion"),
            primaryKey = prop("primaryKey"),
            defaultSearchType = prop("defaultSearchType"),
            cacheControl = prop("cacheControl"),
            hints = hintsJson,
        )
    }
```

- [ ] **Step 4: 确认没有其他地方引用旧的 quickjs-wrapper 类型**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && grep -rn "com.whl.quickjs\|QuickJSContext\|QuickJSLoader\|JSCallFunction\|JSArray\|JSObject" plugin/src/main/java/ --include="*.kt"`

Expected: 零匹配。如果有残留引用，清理掉。

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt
git commit -m "refactor(plugin): adapt PluginManager for quickjs-kt engine"
```

---

### Task 8: 编译验证与修复

**Files:**
- 可能需要微调上述所有文件

- [ ] **Step 1: 全量编译**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && ./gradlew :plugin:compileDebugKotlin 2>&1 | tail -40`

Expected: 编译成功。如果有错误，根据错误信息修复（常见问题：import 路径、suspend 标记遗漏、泛型不匹配）。

- [ ] **Step 2: 全项目编译**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && ./gradlew assembleDebug 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 确认无旧库残留**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && grep -rn "com.whl.quickjs\|quickjsWrapper\|wrapper-android" --include="*.kt" --include="*.kts" --include="*.toml" .`

Expected: 零匹配。

- [ ] **Step 4: 运行单元测试**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && ./gradlew :plugin:testDebugUnitTest 2>&1 | tail -20`

Expected: 测试通过（或者如果有依赖旧 API 的测试需要更新）。

- [ ] **Step 5: Commit fix（如有修复）**

```bash
git add -A
git commit -m "fix(plugin): resolve compilation issues from quickjs-kt migration"
```

---

### Task 9: 设备端到端验证

- [ ] **Step 1: 安装到模拟器/设备**

Run: `cd /Users/zili/code/android/MusicFreeAndroid && ./gradlew installDebug`

- [ ] **Step 2: 验证插件加载**

启动 app，观察 logcat：

Run: `adb logcat -s PluginManager LoadedPlugin RequireShim AxiosShim JsEngine`

Expected:
- 6 个插件全部 `Loaded plugin: xxx from yyy.js`
- `require('cheerio')` 不再出现 "not supported" 警告
- 无 `UnsupportedOperationException` 或 `JS async error`

- [ ] **Step 3: 验证 getTopLists（之前崩溃的 API）**

进入榜单页面，观察 logcat。

Expected: 元力KW 的 `getTopLists` 成功返回数据，不再出现 `Array types are not yet supported` 错误。

- [ ] **Step 4: 验证搜索（之前 cheerio 导致失败）**

使用元力MG 插件搜索任意关键词。

Expected: 搜索返回结果，不再出现 `cannot read property 'map' of undefined` 错误。

- [ ] **Step 5: 验证播放（getMediaSource）**

选择一首歌播放。

Expected: 音频正常播放。如果首选插件返回 null，fallback 到 qualities URL。

- [ ] **Step 6: 验证端到端链路：搜索 → 播放 → 歌词**

1. 搜索一首歌
2. 点击播放
3. 进入全屏播放器查看歌词

Expected: 全链路正常工作。

---

### Task 10: 更新文档

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: 更新 AGENTS.md 中的插件系统描述**

在"插件系统"段落，将：
```
`require()` shim 支持 `cheerio`、`crypto-js`、`dayjs`、`axios`、`qs`、`he`、`big-integer`。
```
确认描述与实际一致（现在 cheerio 确实已支持）。

在"技术栈"段落，将：
```
- 插件引擎：QuickJS（`quickjs-android`）
```
更新为：
```
- 插件引擎：QuickJS（`quickjs-kt`）
```

- [ ] **Step 2: Commit**

```bash
git add AGENTS.md
git commit -m "docs: update AGENTS.md to reflect quickjs-kt migration"
```
