# Milestone 6: Plugin Engine & Search Implementation Plan

> 文档状态：历史记录（执行快照）
> 适用范围：当时阶段的实施计划与执行上下文。
> 直接执行：否
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 备注：仅用于回溯，不代表当前仓库可直接执行。
> 最后校验：2026-04-11


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate QuickJS engine to load existing MusicFree JS plugins, enabling plugin-driven search and online music playback.

**Architecture:** The `:plugin` module wraps QuickJS (`wang.harlon.quickjs:wrapper-android:3.2.3`) with a `JsEngine` class for JS execution, `JsBridge` for Kotlin↔JS type conversion, and `PluginManager` for plugin lifecycle. `PluginApi` exposes `search()` and `getMediaSource()` to upper layers. `:feature:search` provides the search UI with plugin selection, and `:feature:settings` provides plugin install/view/uninstall management.

**Tech Stack:** QuickJS (wang.harlon.quickjs:wrapper-android), OkHttp (for axios shim), Kotlin Coroutines, Hilt DI, Jetpack Compose, Room (plugin metadata persistence via DataStore)

---

## File Structure

### `:plugin` module — new files
| File | Responsibility |
|------|---------------|
| `plugin/build.gradle.kts` | Add QuickJS + OkHttp dependencies |
| `plugin/src/main/java/.../plugin/engine/JsEngine.kt` | QuickJS context wrapper: evaluate, call functions, module loader |
| `plugin/src/main/java/.../plugin/engine/JsBridge.kt` | Kotlin↔JS type conversion (JSObject↔Map, JSArray↔List, etc.) |
| `plugin/src/main/java/.../plugin/engine/AxiosShim.kt` | JS `require('axios')` implementation using OkHttp |
| `plugin/src/main/java/.../plugin/api/PluginApi.kt` | Interface: search(), getMediaSource() |
| `plugin/src/main/java/.../plugin/api/PluginInfo.kt` | Data class: plugin metadata (platform, version, author, etc.) |
| `plugin/src/main/java/.../plugin/api/SearchResult.kt` | Data class: search result (isEnd, data) |
| `plugin/src/main/java/.../plugin/manager/PluginManager.kt` | Plugin lifecycle: install, load, unload, list |
| `plugin/src/main/java/.../plugin/di/PluginModule.kt` | Hilt DI bindings |

### `:feature:search` module — modify/create
| File | Responsibility |
|------|---------------|
| `feature/search/build.gradle.kts` | Add :plugin, :player, :data deps |
| `feature/search/src/main/java/.../search/SearchViewModel.kt` | Search state management, plugin selection |
| `feature/search/src/main/java/.../search/SearchScreen.kt` | Replace placeholder with real search UI |
| `feature/search/src/main/java/.../search/SearchUiState.kt` | Sealed interface for search states |

### `:feature:settings` module — modify/create
| File | Responsibility |
|------|---------------|
| `feature/settings/build.gradle.kts` | Add :plugin deps |
| `feature/settings/src/main/java/.../settings/SettingsViewModel.kt` | Plugin list, install/uninstall actions |
| `feature/settings/src/main/java/.../settings/SettingsScreen.kt` | Replace placeholder with real settings UI |

### `:app` module — modify
| File | Responsibility |
|------|---------------|
| `app/build.gradle.kts` | Add :plugin dependency |
| `app/src/main/java/.../navigation/AppNavHost.kt` | Wire search/settings navigation params |

### Shared modifications
| File | Responsibility |
|------|---------------|
| `gradle/libs.versions.toml` | Add quickjs-wrapper, okhttp versions+libraries |

---

## Task 1: Add QuickJS and OkHttp Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `plugin/build.gradle.kts`

- [ ] **Step 1: Add library entries to version catalog**

In `gradle/libs.versions.toml`, add:
```toml
# Under [versions]
quickjsWrapper = "3.2.3"
okhttp = "4.12.0"

# Under [libraries]
quickjs-wrapper-android = { group = "wang.harlon.quickjs", name = "wrapper-android", version.ref = "quickjsWrapper" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

- [ ] **Step 2: Update plugin/build.gradle.kts**

Add dependencies:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.quickjs.wrapper.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
}
```

Also add `kotlin-serialization` plugin and `testOptions` with ByteBuddy experimental flag.

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :plugin:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml plugin/build.gradle.kts
git commit -m "feat(plugin): add QuickJS wrapper and OkHttp dependencies"
```

---

## Task 2: Plugin Data Models

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginInfo.kt`
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/api/SearchResult.kt`
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginApi.kt`

- [ ] **Step 1: Create PluginInfo data class**

```kotlin
package com.hank.musicfree.plugin.api

data class PluginInfo(
    val platform: String,
    val version: String?,
    val author: String?,
    val description: String?,
    val srcUrl: String?,
    val supportedSearchType: List<String>,
)
```

- [ ] **Step 2: Create SearchResult data class**

```kotlin
package com.hank.musicfree.plugin.api

import com.hank.musicfree.core.model.MusicItem

data class SearchResult(
    val isEnd: Boolean,
    val data: List<MusicItem>,
)
```

- [ ] **Step 3: Create PluginApi interface**

```kotlin
package com.hank.musicfree.plugin.api

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem

interface PluginApi {
    val info: PluginInfo
    suspend fun search(query: String, page: Int, type: String = "music"): SearchResult
    suspend fun getMediaSource(musicItem: MusicItem, quality: String = "standard"): MediaSourceResult?
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :plugin:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/api/
git commit -m "feat(plugin): add PluginInfo, SearchResult, and PluginApi interface"
```

---

## Task 3: JsBridge — Kotlin↔JS Type Conversion

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsBridge.kt`

- [ ] **Step 1: Write unit tests for JsBridge**

Create `plugin/src/test/java/com/hank/musicfree/plugin/engine/JsBridgeTest.kt`:

```kotlin
package com.hank.musicfree.plugin.engine

import org.junit.Assert.*
import org.junit.Test

class JsBridgeTest {
    @Test
    fun `toMusicItem parses map correctly`() {
        val map = mapOf(
            "id" to "123",
            "platform" to "test",
            "title" to "Song",
            "artist" to "Artist",
            "album" to "Album",
            "duration" to 180.0,
            "url" to "http://example.com/song.mp3",
            "artwork" to "http://example.com/cover.jpg",
        )
        val item = JsBridge.toMusicItem(map)
        assertEquals("123", item.id)
        assertEquals("test", item.platform)
        assertEquals("Song", item.title)
        assertEquals("Artist", item.artist)
        assertEquals(180000L, item.duration) // seconds → ms
    }

    @Test
    fun `toMusicItem handles missing optional fields`() {
        val map = mapOf(
            "id" to "1",
            "platform" to "test",
            "title" to "Song",
            "artist" to "Artist",
        )
        val item = JsBridge.toMusicItem(map)
        assertNull(item.url)
        assertNull(item.artwork)
        assertEquals(0L, item.duration)
    }

    @Test
    fun `musicItemToMap converts correctly`() {
        val item = com.hank.musicfree.core.model.MusicItem(
            id = "1", platform = "test", title = "Song", artist = "Artist",
            album = "Album", duration = 180000L, url = null, artwork = null, qualities = null,
        )
        val map = JsBridge.musicItemToMap(item)
        assertEquals("1", map["id"])
        assertEquals(180.0, map["duration"]) // ms → seconds
    }

    @Test
    fun `parseSearchResult parses correctly`() {
        val map = mapOf(
            "isEnd" to true,
            "data" to listOf(
                mapOf("id" to "1", "platform" to "test", "title" to "Song", "artist" to "A"),
            ),
        )
        val result = JsBridge.parseSearchResult(map)
        assertTrue(result.isEnd)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `parseMediaSourceResult parses correctly`() {
        val map = mapOf(
            "url" to "http://example.com/song.mp3",
            "headers" to mapOf("User-Agent" to "test"),
        )
        val result = JsBridge.parseMediaSourceResult(map)
        assertNotNull(result)
        assertEquals("http://example.com/song.mp3", result!!.url)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :plugin:testDebugUnitTest --tests "*.JsBridgeTest"`
Expected: FAIL (JsBridge not yet implemented)

- [ ] **Step 3: Implement JsBridge**

```kotlin
package com.hank.musicfree.plugin.engine

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.plugin.api.SearchResult

object JsBridge {
    fun toMusicItem(map: Map<String, Any?>): MusicItem {
        val durationRaw = (map["duration"] as? Number)?.toDouble() ?: 0.0
        return MusicItem(
            id = map["id"]?.toString() ?: "",
            platform = map["platform"]?.toString() ?: "",
            title = map["title"]?.toString() ?: "",
            artist = map["artist"]?.toString() ?: "",
            album = map["album"]?.toString(),
            duration = (durationRaw * 1000).toLong(), // RN uses seconds, we use ms
            url = map["url"]?.toString(),
            artwork = map["artwork"]?.toString(),
            qualities = null,
        )
    }

    fun musicItemToMap(item: MusicItem): Map<String, Any?> = mapOf(
        "id" to item.id,
        "platform" to item.platform,
        "title" to item.title,
        "artist" to item.artist,
        "album" to item.album,
        "duration" to (item.duration / 1000.0), // ms → seconds
        "url" to item.url,
        "artwork" to item.artwork,
    )

    fun parseSearchResult(map: Map<String, Any?>): SearchResult {
        val isEnd = map["isEnd"] as? Boolean ?: false
        val dataList = (map["data"] as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let { toMusicItem(it as Map<String, Any?>) }
        } ?: emptyList()
        return SearchResult(isEnd = isEnd, data = dataList)
    }

    fun parseMediaSourceResult(map: Map<String, Any?>): MediaSourceResult? {
        val url = map["url"]?.toString() ?: return null
        val headers = (map["headers"] as? Map<*, *>)
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap()
        return MediaSourceResult(
            url = url,
            headers = headers,
            userAgent = map["userAgent"]?.toString(),
            quality = map["quality"]?.toString()?.let { runCatching { PlayQuality.valueOf(it.uppercase()) }.getOrNull() },
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :plugin:testDebugUnitTest --tests "*.JsBridgeTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/JsBridge.kt \
       plugin/src/test/java/com/hank/musicfree/plugin/engine/JsBridgeTest.kt
git commit -m "feat(plugin): add JsBridge for Kotlin↔JS type conversion with tests"
```

---

## Task 4: JsEngine — QuickJS Context Wrapper

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsEngine.kt`
- Create: `plugin/src/test/java/com/hank/musicfree/plugin/engine/JsEngineTest.kt`

- [ ] **Step 1: Write JsEngine unit tests**

```kotlin
package com.hank.musicfree.plugin.engine

import org.junit.Assert.*
import org.junit.Test

class JsEngineTest {
    // Note: QuickJS native library may not load in unit test env.
    // These are basic structure tests; integration tests will cover real JS execution.

    @Test
    fun `JsEngine can be instantiated`() {
        // Verify the class exists and compiles
        assertNotNull(JsEngine::class)
    }
}
```

- [ ] **Step 2: Implement JsEngine**

```kotlin
package com.hank.musicfree.plugin.engine

import com.nicholasgasior.quickjs.QuickJSContext
import com.nicholasgasior.quickjs.JSObject
import com.nicholasgasior.quickjs.JSArray
import com.nicholasgasior.quickjs.JSFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Wrapper around QuickJSContext providing:
 * - JS code evaluation
 * - Function calling with Kotlin type conversion
 * - Global object injection (for require shim, console, env)
 * - Thread-safe execution on IO dispatcher
 */
class JsEngine {
    private var context: QuickJSContext? = null

    fun create() {
        context = QuickJSContext.create()
    }

    fun destroy() {
        context?.destroy()
        context = null
    }

    fun evaluate(code: String): Any? {
        return context?.evaluate(code)
    }

    fun getGlobalObject(): JSObject? = context?.globalObject

    /**
     * Convert JSObject to Kotlin Map recursively.
     */
    fun jsObjectToMap(obj: JSObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val names = obj.getNames()
        for (name in names) {
            result[name] = jsValueToKotlin(obj.getProperty(name))
        }
        return result
    }

    /**
     * Convert JSArray to Kotlin List recursively.
     */
    fun jsArrayToList(arr: JSArray): List<Any?> {
        val result = mutableListOf<Any?>()
        for (i in 0 until arr.length()) {
            result.add(jsValueToKotlin(arr.get(i)))
        }
        return result
    }

    private fun jsValueToKotlin(value: Any?): Any? = when (value) {
        is JSObject -> if (value is JSArray) jsArrayToList(value) else jsObjectToMap(value)
        is JSFunction -> null // Skip functions
        else -> value // String, Number, Boolean, null
    }

    /**
     * Set a Kotlin Map as a JS global object property.
     */
    fun setGlobalMap(name: String, map: Map<String, Any?>) {
        val ctx = context ?: return
        val obj = ctx.createNewJSObject()
        for ((k, v) in map) {
            when (v) {
                is String -> obj.setProperty(k, v)
                is Number -> obj.setProperty(k, v.toDouble())
                is Boolean -> obj.setProperty(k, v)
                else -> {} // skip complex types for now
            }
        }
        ctx.globalObject.setProperty(name, obj)
        obj.release()
    }
}
```

- [ ] **Step 3: Run test to verify compilation**

Run: `./gradlew :plugin:testDebugUnitTest --tests "*.JsEngineTest"`
Expected: PASS (basic compilation test)

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/JsEngine.kt \
       plugin/src/test/java/com/hank/musicfree/plugin/engine/JsEngineTest.kt
git commit -m "feat(plugin): add JsEngine QuickJS wrapper"
```

---

## Task 5: AxiosShim — HTTP Requests from JS

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt`

- [ ] **Step 1: Implement AxiosShim**

This provides `axios.get(url, config)` and `axios.post(url, data, config)` to JS plugins via OkHttp:

```kotlin
package com.hank.musicfree.plugin.engine

import com.nicholasgasior.quickjs.QuickJSContext
import com.nicholasgasior.quickjs.JSCallFunction
import com.nicholasgasior.quickjs.JSObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Registers an `axios`-like global object in the JS context.
 * Provides synchronous HTTP via OkHttp (plugins run on background thread).
 * Supports: axios.get(url, {params, headers}), axios(config)
 */
object AxiosShim {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun register(context: QuickJSContext, engine: JsEngine) {
        val axios = context.createNewJSObject()

        // axios.get(url, config?)
        axios.setProperty("get", JSCallFunction { args ->
            val url = args.getOrNull(0)?.toString() ?: return@JSCallFunction null
            val config = args.getOrNull(1)
            val configMap = if (config is JSObject) engine.jsObjectToMap(config) else emptyMap()
            executeGet(url, configMap, context, engine)
        })

        // axios.post(url, data?, config?)
        axios.setProperty("post", JSCallFunction { args ->
            val url = args.getOrNull(0)?.toString() ?: return@JSCallFunction null
            val body = args.getOrNull(1)?.toString() ?: ""
            val config = args.getOrNull(2)
            val configMap = if (config is JSObject) engine.jsObjectToMap(config) else emptyMap()
            executePost(url, body, configMap, context, engine)
        })

        // Set defaults
        val defaults = context.createNewJSObject()
        defaults.setProperty("timeout", 2000.0)
        axios.setProperty("defaults", defaults)
        defaults.release()

        context.globalObject.setProperty("axios", axios)
        axios.release()
    }

    private fun executeGet(
        url: String,
        config: Map<String, Any?>,
        context: QuickJSContext,
        engine: JsEngine,
    ): JSObject? {
        val fullUrl = buildUrl(url, config["params"] as? Map<*, *>)
        val headers = config["headers"] as? Map<*, *>

        val requestBuilder = Request.Builder().url(fullUrl).get()
        headers?.forEach { (k, v) ->
            if (k is String && v is String) requestBuilder.addHeader(k, v)
        }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            val bodyStr = response.body?.string() ?: ""
            wrapResponse(response.code, bodyStr, context)
        } catch (e: Exception) {
            null
        }
    }

    private fun executePost(
        url: String,
        body: String,
        config: Map<String, Any?>,
        context: QuickJSContext,
        engine: JsEngine,
    ): JSObject? {
        val headers = config["headers"] as? Map<*, *>
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.toRequestBody(mediaType)

        val requestBuilder = Request.Builder().url(url).post(requestBody)
        headers?.forEach { (k, v) ->
            if (k is String && v is String) requestBuilder.addHeader(k, v)
        }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            val bodyStr = response.body?.string() ?: ""
            wrapResponse(response.code, bodyStr, context)
        } catch (e: Exception) {
            null
        }
    }

    private fun wrapResponse(code: Int, body: String, context: QuickJSContext): JSObject {
        // Parse JSON body, fallback to string
        val responseObj = context.createNewJSObject()
        responseObj.setProperty("status", code.toDouble())

        // Try to parse body as JSON via JS
        try {
            val parsed = context.evaluate("JSON.parse(${escapeForJs(body)})")
            if (parsed is JSObject) {
                responseObj.setProperty("data", parsed)
            } else {
                responseObj.setProperty("data", body)
            }
        } catch (_: Exception) {
            responseObj.setProperty("data", body)
        }

        return responseObj
    }

    private fun buildUrl(base: String, params: Map<*, *>?): String {
        if (params.isNullOrEmpty()) return base
        val query = params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k.toString(), "UTF-8")}=${java.net.URLEncoder.encode(v.toString(), "UTF-8")}"
        }
        val separator = if ("?" in base) "&" else "?"
        return "$base$separator$query"
    }

    private fun escapeForJs(s: String): String {
        return "'" + s
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "'"
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :plugin:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt
git commit -m "feat(plugin): add AxiosShim for JS HTTP requests via OkHttp"
```

---

## Task 6: PluginManager — Plugin Lifecycle

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt`
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/di/PluginModule.kt`

- [ ] **Step 1: Create LoadedPlugin — a loaded plugin instance**

```kotlin
package com.hank.musicfree.plugin.manager

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.PluginApi
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.SearchResult
import com.hank.musicfree.plugin.engine.AxiosShim
import com.hank.musicfree.plugin.engine.JsBridge
import com.hank.musicfree.plugin.engine.JsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Represents a loaded JS plugin with its own JsEngine.
 * Each plugin gets an isolated QuickJS context.
 */
class LoadedPlugin(
    override val info: PluginInfo,
    private val engine: JsEngine,
    val filePath: String,
) : PluginApi {

    override suspend fun search(query: String, page: Int, type: String): SearchResult {
        return withContext(Dispatchers.IO) {
            withTimeout(30_000L) {
                val escapedQuery = query.replace("'", "\\'")
                val result = engine.evaluate(
                    "JSON.stringify(await __plugin.search('$escapedQuery', $page, '$type'))"
                )
                val jsonStr = result?.toString() ?: return@withTimeout SearchResult(true, emptyList())
                val map = parseJsonToMap(jsonStr)
                JsBridge.parseSearchResult(map)
            }
        }
    }

    override suspend fun getMediaSource(musicItem: MusicItem, quality: String): MediaSourceResult? {
        return withContext(Dispatchers.IO) {
            withTimeout(30_000L) {
                val itemMap = JsBridge.musicItemToMap(musicItem)
                val itemJson = mapToJson(itemMap)
                val result = engine.evaluate(
                    "JSON.stringify(await __plugin.getMediaSource($itemJson, '$quality'))"
                )
                val jsonStr = result?.toString() ?: return@withTimeout null
                val map = parseJsonToMap(jsonStr)
                JsBridge.parseMediaSourceResult(map)
            }
        }
    }

    fun destroy() {
        engine.destroy()
    }

    private fun parseJsonToMap(json: String): Map<String, Any?> {
        // Use JS JSON.parse for reliable parsing
        val obj = engine.evaluate("JSON.parse('${json.replace("'", "\\'").replace("\n", "\\n")}')")
        return if (obj is com.nicholasgasior.quickjs.JSObject) {
            engine.jsObjectToMap(obj)
        } else emptyMap()
    }

    private fun mapToJson(map: Map<String, Any?>): String {
        return buildString {
            append("{")
            map.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) append(",")
                append("\"$k\":")
                when (v) {
                    null -> append("null")
                    is String -> append("\"${v.replace("\"", "\\\"")}\"")
                    is Number -> append(v)
                    is Boolean -> append(v)
                    else -> append("null")
                }
            }
            append("}")
        }
    }
}
```

- [ ] **Step 2: Create PluginManager**

```kotlin
package com.hank.musicfree.plugin.manager

import android.content.Context
import com.nicholasgasior.quickjs.QuickJSContext
import com.hank.musicfree.plugin.api.PluginApi
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.engine.AxiosShim
import com.hank.musicfree.plugin.engine.JsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pluginsDir: File by lazy {
        File(context.filesDir, "plugins").also { it.mkdirs() }
    }

    private val _plugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedPlugin>> = _plugins

    private val mutex = Mutex()

    /**
     * Load all .js plugins from the plugins directory.
     */
    suspend fun loadAllPlugins() = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Destroy existing plugins
            _plugins.value.forEach { it.destroy() }

            val loaded = mutableListOf<LoadedPlugin>()
            val jsFiles = pluginsDir.listFiles { f -> f.extension == "js" } ?: emptyArray()

            for (file in jsFiles) {
                try {
                    val plugin = loadPluginFromFile(file)
                    if (plugin != null) loaded.add(plugin)
                } catch (e: Exception) {
                    // Skip failed plugins
                    e.printStackTrace()
                }
            }
            _plugins.value = loaded
        }
    }

    /**
     * Install a plugin from a local file path. Copies to plugins dir and loads.
     */
    suspend fun installFromFile(sourceFile: File): PluginApi? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val destFile = File(pluginsDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            val plugin = loadPluginFromFile(destFile)
            if (plugin != null) {
                _plugins.value = _plugins.value + plugin
            }
            plugin
        }
    }

    /**
     * Install a plugin from a URL. Downloads to plugins dir and loads.
     */
    suspend fun installFromUrl(url: String, fileName: String): PluginApi? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val destFile = File(pluginsDir, fileName)
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.body?.let { body ->
                destFile.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
            }
            val plugin = loadPluginFromFile(destFile)
            if (plugin != null) {
                _plugins.value = _plugins.value + plugin
            }
            plugin
        }
    }

    /**
     * Uninstall a plugin by platform name.
     */
    suspend fun uninstall(platform: String) = mutex.withLock {
        val plugin = _plugins.value.find { it.info.platform == platform }
        if (plugin != null) {
            plugin.destroy()
            File(plugin.filePath).delete()
            _plugins.value = _plugins.value.filter { it.info.platform != platform }
        }
    }

    fun getPlugin(platform: String): PluginApi? =
        _plugins.value.find { it.info.platform == platform }

    private fun loadPluginFromFile(file: File): LoadedPlugin? {
        val code = file.readText()
        val engine = JsEngine()
        engine.create()

        val ctx = engine.getGlobalObject()?.let {
            // The context is available via engine
        }

        // Register require shim
        registerRequireShim(engine)

        // Register console
        registerConsole(engine)

        // Wrap plugin code: evaluate as module, capture exports
        val wrappedCode = """
            var module = { exports: {} };
            var exports = module.exports;
            (function(require, module, exports, console, env) {
                $code
            })(globalThis.__require, module, exports, console, { os: 'android' });
            globalThis.__plugin = module.exports;
        """.trimIndent()

        try {
            engine.evaluate(wrappedCode)
        } catch (e: Exception) {
            engine.destroy()
            throw e
        }

        // Extract plugin metadata
        val info = extractPluginInfo(engine) ?: run {
            engine.destroy()
            return null
        }

        return LoadedPlugin(info, engine, file.absolutePath)
    }

    private fun registerRequireShim(engine: JsEngine) {
        // Register built-in modules via __require global
        val ctx = (engine.evaluate("this") as? com.nicholasgasior.quickjs.JSObject)
            ?: return

        // We register a simple require that supports 'axios' (other shims can be added later)
        val context = try {
            val field = engine.javaClass.getDeclaredField("context")
            field.isAccessible = true
            field.get(engine) as? QuickJSContext
        } catch (_: Exception) { null } ?: return

        // Register axios as a global
        AxiosShim.register(context, engine)

        // Register __require function
        context.globalObject.setProperty("__require", com.nicholasgasior.quickjs.JSCallFunction { args ->
            val moduleName = args.getOrNull(0)?.toString() ?: ""
            when (moduleName) {
                "axios" -> context.globalObject.getProperty("axios")
                else -> {
                    // Return empty object for unsupported modules
                    context.createNewJSObject()
                }
            }
        })
    }

    private fun registerConsole(engine: JsEngine) {
        engine.evaluate("""
            globalThis.console = {
                log: function() {},
                warn: function() {},
                error: function() {},
                info: function() {},
            };
        """.trimIndent())
    }

    private fun extractPluginInfo(engine: JsEngine): PluginInfo? {
        val platform = engine.evaluate("__plugin.platform")?.toString() ?: return null
        val version = engine.evaluate("__plugin.version")?.toString()
        val author = engine.evaluate("__plugin.author")?.toString()
        val description = engine.evaluate("__plugin.description")?.toString()
        val srcUrl = engine.evaluate("__plugin.srcUrl")?.toString()

        // Extract supportedSearchType array
        val searchTypes = try {
            val arr = engine.evaluate(
                "JSON.stringify(__plugin.supportedSearchType || ['music'])"
            )?.toString() ?: "[\"music\"]"
            arr.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (_: Exception) {
            listOf("music")
        }

        return PluginInfo(
            platform = platform,
            version = version,
            author = author,
            description = description,
            srcUrl = srcUrl,
            supportedSearchType = searchTypes,
        )
    }
}
```

- [ ] **Step 3: Create PluginModule for DI**

```kotlin
package com.hank.musicfree.plugin.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PluginModule
// PluginManager is @Singleton @Inject, so no explicit @Provides needed
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :plugin:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/ \
       plugin/src/main/java/com/hank/musicfree/plugin/di/
git commit -m "feat(plugin): add PluginManager with install/load/unload lifecycle"
```

---

## Task 7: SearchScreen UI

**Files:**
- Modify: `feature/search/build.gradle.kts`
- Create: `feature/search/src/main/java/.../search/SearchUiState.kt`
- Create: `feature/search/src/main/java/.../search/SearchViewModel.kt`
- Modify: `feature/search/src/main/java/.../search/SearchScreen.kt`
- Modify: `feature/search/src/main/java/.../search/navigation/SearchNavigation.kt`

- [ ] **Step 1: Update feature/search/build.gradle.kts**

Add dependencies on :plugin, :player, :data, coil, hilt-navigation, lifecycle:

```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":plugin"))
    implementation(project(":player"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
}
```

- [ ] **Step 2: Create SearchUiState**

```kotlin
package com.hank.musicfree.feature.search

import com.hank.musicfree.core.model.MusicItem

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val items: List<MusicItem>,
        val isEnd: Boolean,
        val query: String,
        val page: Int,
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
```

- [ ] **Step 3: Create SearchViewModel**

```kotlin
package com.hank.musicfree.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
) : ViewModel() {

    val availablePlugins: StateFlow<List<PluginInfo>> = pluginManager.plugins
        .map { plugins -> plugins.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private var currentQuery = ""
    private var currentPage = 1
    private var accumulatedItems = mutableListOf<MusicItem>()

    fun selectPlugin(platform: String) {
        _selectedPlugin.value = platform
        _uiState.value = SearchUiState.Idle
        accumulatedItems.clear()
    }

    fun search(query: String) {
        val platform = _selectedPlugin.value ?: return
        currentQuery = query
        currentPage = 1
        accumulatedItems.clear()
        doSearch(platform, query, 1)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state is SearchUiState.Success && !state.isEnd) {
            val platform = _selectedPlugin.value ?: return
            currentPage++
            doSearch(platform, currentQuery, currentPage)
        }
    }

    fun playItem(item: MusicItem, queue: List<MusicItem>) {
        val index = queue.indexOf(item)
        playerController.playQueue(queue, if (index >= 0) index else 0)
    }

    /**
     * Resolve media source for a plugin-sourced item and update its URL before playback.
     */
    suspend fun resolveAndPlay(item: MusicItem, queue: List<MusicItem>) {
        val plugin = pluginManager.getPlugin(item.platform) ?: return
        val source = plugin.getMediaSource(item) ?: return
        val resolved = item.copy(url = source.url)
        val resolvedQueue = queue.map { if (it.id == item.id && it.platform == item.platform) resolved else it }
        playItem(resolved, resolvedQueue)
    }

    private fun doSearch(platform: String, query: String, page: Int) {
        _uiState.value = if (page == 1) SearchUiState.Loading else _uiState.value
        viewModelScope.launch {
            try {
                val plugin = pluginManager.getPlugin(platform) ?: run {
                    _uiState.value = SearchUiState.Error("插件未找到")
                    return@launch
                }
                val result = plugin.search(query, page)
                if (page == 1) accumulatedItems.clear()
                accumulatedItems.addAll(result.data)
                _uiState.value = SearchUiState.Success(
                    items = accumulatedItems.toList(),
                    isEnd = result.isEnd,
                    query = query,
                    page = page,
                )
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "搜索失败")
            }
        }
    }
}
```

- [ ] **Step 4: Implement SearchScreen**

```kotlin
package com.hank.musicfree.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.plugin.api.PluginInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val plugins by viewModel.availablePlugins.collectAsStateWithLifecycle()
    val selectedPlugin by viewModel.selectedPlugin.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with back + search field
        TopAppBar(
            title = {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索音乐...", color = MusicFreeTheme.colors.textSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        viewModel.search(query)
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MusicFreeTheme.colors.pageBackground,
                        unfocusedContainerColor = MusicFreeTheme.colors.pageBackground,
                        focusedTextColor = MusicFreeTheme.colors.text,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MusicFreeTheme.colors.appBarText)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MusicFreeTheme.colors.appBar),
        )

        // Plugin selector chips
        if (plugins.isNotEmpty()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                plugins.forEachIndexed { index, plugin ->
                    SegmentedButton(
                        selected = selectedPlugin == plugin.platform,
                        onClick = { viewModel.selectPlugin(plugin.platform) },
                        shape = SegmentedButtonDefaults.itemShape(index, plugins.size),
                    ) {
                        Text(plugin.platform, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Results
        when (val state = uiState) {
            is SearchUiState.Idle -> {
                if (plugins.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("请先在设置中安装插件", color = MusicFreeTheme.colors.textSecondary)
                    }
                }
            }
            is SearchUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                }
            }
            is SearchUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MusicFreeTheme.colors.danger)
                }
            }
            is SearchUiState.Success -> {
                if (state.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有找到结果", color = MusicFreeTheme.colors.textSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.items, key = { _, item -> "${item.platform}:${item.id}" }) { index, item ->
                            SearchResultItem(
                                item = item,
                                onClick = {
                                    scope.launch {
                                        viewModel.resolveAndPlay(item, state.items)
                                        onNavigateToPlayer()
                                    }
                                },
                            )
                            if (index < state.items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp),
                                    color = MusicFreeTheme.colors.divider,
                                )
                            }
                        }
                        if (!state.isEnd) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text("加载更多", color = MusicFreeTheme.colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: MusicItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(uri = item.artwork, size = 48.dp, cornerRadius = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else "",
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Update SearchNavigation to pass navigation callbacks**

```kotlin
// Update to pass onNavigateToPlayer
fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(
            onBack = onBack,
            onNavigateToPlayer = onNavigateToPlayer,
        )
    }
}
```

- [ ] **Step 6: Update AppNavHost for new search params**

In `app/src/main/java/.../navigation/AppNavHost.kt`, update:
```kotlin
searchScreen(
    onBack = { navController.popBackStack() },
    onNavigateToPlayer = { navController.navigate(PlayerRoute) },
)
```

- [ ] **Step 7: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add feature/search/ app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt
git commit -m "feat(search): implement search UI with plugin selection and results"
```

---

## Task 8: SettingsScreen — Plugin Management UI

**Files:**
- Modify: `feature/settings/build.gradle.kts`
- Create: `feature/settings/src/main/java/.../settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/java/.../settings/SettingsScreen.kt`

- [ ] **Step 1: Update feature/settings/build.gradle.kts**

```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":plugin"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
}
```

- [ ] **Step 2: Create SettingsViewModel**

```kotlin
package com.hank.musicfree.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    val plugins: StateFlow<List<PluginInfo>> = pluginManager.plugins
        .map { plugins -> plugins.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    init {
        viewModelScope.launch {
            pluginManager.loadAllPlugins()
        }
    }

    fun installFromUrl(url: String) {
        viewModelScope.launch {
            _installState.value = InstallState.Loading
            try {
                val fileName = url.substringAfterLast("/").ifEmpty { "plugin.js" }
                pluginManager.installFromUrl(url, fileName)
                _installState.value = InstallState.Success
            } catch (e: Exception) {
                _installState.value = InstallState.Error(e.message ?: "安装失败")
            }
        }
    }

    fun uninstallPlugin(platform: String) {
        viewModelScope.launch {
            pluginManager.uninstall(platform)
        }
    }

    fun resetInstallState() {
        _installState.value = InstallState.Idle
    }
}

sealed interface InstallState {
    data object Idle : InstallState
    data object Loading : InstallState
    data object Success : InstallState
    data class Error(val message: String) : InstallState
}
```

- [ ] **Step 3: Implement SettingsScreen**

```kotlin
package com.hank.musicfree.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.plugin.api.PluginInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    var showInstallDialog by remember { mutableStateOf(false) }

    // Install dialog
    if (showInstallDialog) {
        InstallPluginDialog(
            installState = installState,
            onDismiss = {
                showInstallDialog = false
                viewModel.resetInstallState()
            },
            onInstall = { url -> viewModel.installFromUrl(url) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text("设置", color = MusicFreeTheme.colors.appBarText, fontSize = FontSizes.appBar)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MusicFreeTheme.colors.appBarText)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MusicFreeTheme.colors.appBar),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Plugin management section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "插件管理",
                        color = MusicFreeTheme.colors.text,
                        fontSize = FontSizes.subTitle,
                    )
                    IconButton(onClick = { showInstallDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "安装插件", tint = MusicFreeTheme.colors.primary)
                    }
                }
            }

            if (plugins.isEmpty()) {
                item {
                    Text(
                        "暂无已安装插件",
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.description,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            items(plugins, key = { it.platform }) { plugin ->
                PluginListItem(
                    plugin = plugin,
                    onUninstall = { viewModel.uninstallPlugin(plugin.platform) },
                )
            }
        }
    }
}

@Composable
private fun PluginListItem(
    plugin: PluginInfo,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MusicFreeTheme.colors.card),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plugin.platform,
                    color = MusicFreeTheme.colors.text,
                    fontSize = FontSizes.content,
                )
                if (plugin.author != null || plugin.version != null) {
                    Text(
                        listOfNotNull(plugin.author, plugin.version).joinToString(" · "),
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.description,
                    )
                }
            }
            IconButton(onClick = onUninstall) {
                Icon(Icons.Default.Delete, contentDescription = "卸载", tint = MusicFreeTheme.colors.danger)
            }
        }
    }
}

@Composable
private fun InstallPluginDialog(
    installState: InstallState,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装插件") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("插件 URL") },
                    placeholder = { Text("https://example.com/plugin.js") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = installState !is InstallState.Loading,
                )
                when (installState) {
                    is InstallState.Loading -> {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is InstallState.Error -> {
                        Spacer(Modifier.height(8.dp))
                        Text(installState.message, color = MusicFreeTheme.colors.danger)
                    }
                    is InstallState.Success -> {
                        Spacer(Modifier.height(8.dp))
                        Text("安装成功！", color = MusicFreeTheme.colors.primary)
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInstall(url) },
                enabled = url.isNotBlank() && installState !is InstallState.Loading,
            ) {
                Text("安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add feature/settings/
git commit -m "feat(settings): implement settings screen with plugin management"
```

---

## Task 9: Wire App Module and Final Integration

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add :plugin dependency to app**

In `app/build.gradle.kts`, ensure `implementation(project(":plugin"))` is present.

- [ ] **Step 2: Verify full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all unit tests**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "feat(app): wire plugin module dependency"
```

---

## Task 10: Integration Testing and Bug Fixes

- [ ] **Step 1: Install APK on emulator**

Run: `./gradlew installDebug`

- [ ] **Step 2: Manual verification**

Verify:
1. Settings page opens, shows "插件管理" with install button
2. Can enter a plugin URL and attempt install
3. Search page shows "请先在设置中安装插件" when no plugins
4. After installing a plugin, search page shows plugin selector
5. Search returns results from plugin
6. Clicking a result triggers playback via getMediaSource

- [ ] **Step 3: Fix any bugs found**

Address compilation errors, runtime crashes, or UI issues.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "fix(milestone6): address integration issues"
```

- [ ] **Step 5: Merge to master**

```bash
git checkout master
git merge feature/milestone6-plugin-engine-search
```
