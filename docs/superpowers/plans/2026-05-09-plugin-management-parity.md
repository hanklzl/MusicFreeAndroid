# 插件管理 RN 完整对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐插件管理页 RN 操作、音源重定向、用户变量、插件卡片导入能力，并让所有插件音源解析入口使用同一套规则。

**Architecture:** `:core` 定义跨模块 `MediaSourceResolver` 接口和返回模型，`:plugin` 实现插件版解析服务并由 Hilt 绑定，`:player`、`:downloader`、`:feature:home`、`:feature:search` 只依赖接口。插件管理 UI 保持 RN 直译卡片，`PluginListViewModel` 统一承载操作状态、导入 flow、用户变量和音源重定向配置。

**Tech Stack:** Kotlin、Jetpack Compose、Material3、Hilt、DataStore Preferences、QuickJS、Coroutines Flow、JUnit、Mockito/MockK、Robolectric、Gradle。

---

## 文件结构

### 新增文件

- `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt`：跨模块音源解析接口、解析结果模型、默认空实现。
- `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginUserVariable.kt`：插件声明的用户变量模型。
- `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`：插件音源解析实现，处理重定向、目标失效和回退。
- `plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceTest.kt`：共享解析服务单元测试。
- `plugin/src/test/java/com/hank/musicfree/plugin/manager/PluginManagerUserVariablesTest.kt`：用户变量声明解析和运行时刷新测试。
- `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListActionStateTest.kt`：插件列表 ViewModel 操作状态、按钮模型、导入状态测试。

### 修改文件

- `core/src/main/java/com/hank/musicfree/core/model/MediaSourceResult.kt`：保持现有模型，不新增插件依赖。
- `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginInfo.kt`：新增 `userVariables` 字段。
- `plugin/src/main/java/com/hank/musicfree/plugin/meta/PluginMetaStore.kt`：新增音源重定向 CRUD。
- `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`：解析 `userVariables`，支持 `.json` URL 展开，提供 `setUserVariables()` 和重定向候选查询。
- `plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt`：开放运行时用户变量刷新方法，确保保存后立即生效。
- `plugin/src/main/java/com/hank/musicfree/plugin/di/PluginModule.kt`：将 `PluginMediaSourceService` 绑定为 `MediaSourceResolver`。
- `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`：播放前异步解析无 URL 插件歌曲，覆盖队列切歌和通知切歌。
- `downloader/src/main/java/com/hank/musicfree/downloader/quality/PluginMediaSourceResolver.kt`：改为委托 `MediaSourceResolver`。
- `downloader/build.gradle.kts`：若下载模块不再直接引用插件实现，则移除 `implementation(project(":plugin"))`。
- `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`：移除写死 `WY_FALLBACK_PLATFORM` 的 fallback，委托 `MediaSourceResolver`。
- `feature/home/src/main/java/com/hank/musicfree/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`：插件歌单播放前委托 `MediaSourceResolver`。
- `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListDetailViewModel.kt`：榜单播放前委托 `MediaSourceResolver`。
- `feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/AlbumDetailViewModel.kt`：专辑播放前委托 `MediaSourceResolver`。
- `feature/home/src/main/java/com/hank/musicfree/feature/home/artistdetail/ArtistDetailViewModel.kt`：歌手详情播放前委托 `MediaSourceResolver`。
- `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt`：结构化操作状态、音源重定向、用户变量、导入单曲/歌单。
- `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`：RN 直译卡片操作、对话框和面板。
- `plugin/src/test/java/com/hank/musicfree/plugin/meta/PluginMetaStoreTest.kt`：补音源重定向持久化测试。
- `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModelTest.kt`：更新初始状态测试。
- `player/src/androidTest/java/com/hank/musicfree/player/controller/PlayerControllerTest.kt`、`player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerNotificationControlsTest.kt`：用默认空 resolver 或测试 resolver 覆盖播放前解析。

---

### Task 1: Core 音源解析接口

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt`
- Create: `core/src/test/java/com/hank/musicfree/core/media/MediaSourceResolverTest.kt`

- [ ] **Step 1: 写失败测试**

Create `core/src/test/java/com/hank/musicfree/core/media/MediaSourceResolverTest.kt`:

```kotlin
package com.hank.musicfree.core.media

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSourceResolverTest {
    @Test
    fun `empty resolver returns null`() = runTest {
        val result = EmptyMediaSourceResolver.resolve(item("1"))
        assertNull(result)
    }

    @Test
    fun `resolution preserves original identity and resolver metadata`() {
        val original = item("1")
        val source = MediaSourceResult(
            url = "https://cdn.example.com/1.mp3",
            headers = mapOf("referer" to "https://example.com"),
            userAgent = "ua",
            quality = null,
        )
        val resolution = MediaSourceResolution(
            item = original.copy(url = source.url),
            source = source,
            requestedPlatform = "source",
            resolverPlatform = "target",
            redirected = true,
        )

        assertEquals("1", resolution.item.id)
        assertEquals("source", resolution.item.platform)
        assertEquals("target", resolution.resolverPlatform)
        assertFalse(resolution.item.platform == resolution.resolverPlatform)
    }

    private fun item(id: String) = MusicItem(
        id = id,
        platform = "source",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = null,
        url = null,
        artwork = null,
        qualities = null,
    )
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :core:test --tests 'com.hank.musicfree.core.media.MediaSourceResolverTest'
```

Expected: FAIL，原因是 `MediaSourceResolver`、`EmptyMediaSourceResolver`、`MediaSourceResolution` 不存在。

- [ ] **Step 3: 写最小实现**

Create `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt`:

```kotlin
package com.hank.musicfree.core.media

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem

interface MediaSourceResolver {
    suspend fun resolve(
        item: MusicItem,
        quality: String = "standard",
    ): MediaSourceResolution?
}

data class MediaSourceResolution(
    val item: MusicItem,
    val source: MediaSourceResult,
    val requestedPlatform: String,
    val resolverPlatform: String,
    val redirected: Boolean,
)

object EmptyMediaSourceResolver : MediaSourceResolver {
    override suspend fun resolve(
        item: MusicItem,
        quality: String,
    ): MediaSourceResolution? = null
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
./gradlew :core:test --tests 'com.hank.musicfree.core.media.MediaSourceResolverTest'
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt core/src/test/java/com/hank/musicfree/core/media/MediaSourceResolverTest.kt
git commit -m "feat(core): add media source resolver contract"
```

---

### Task 2: PluginMetaStore 音源重定向持久化

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/meta/PluginMetaStore.kt`
- Modify: `plugin/src/test/java/com/hank/musicfree/plugin/meta/PluginMetaStoreTest.kt`

- [ ] **Step 1: 写失败测试**

Append to `PluginMetaStoreTest`:

```kotlin
@Test
fun `alternative plugin defaults to empty`() = runBlocking {
    assertTrue(store.alternativePlugins.first().isEmpty())
    assertNull(store.getAlternativePlugin("source").first())
}

@Test
fun `set and clear alternative plugin`() = runBlocking {
    store.setAlternativePlugin("source", "target")
    assertEquals("target", store.getAlternativePlugin("source").first())
    assertEquals(mapOf("source" to "target"), store.alternativePlugins.first())

    store.setAlternativePlugin("source", null)
    assertNull(store.getAlternativePlugin("source").first())
    assertTrue(store.alternativePlugins.first().isEmpty())
}

@Test
fun `self alternative plugin is stored as cleared`() = runBlocking {
    store.setAlternativePlugin("source", "source")
    assertNull(store.getAlternativePlugin("source").first())
    assertTrue(store.alternativePlugins.first().isEmpty())
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :plugin:test --tests 'com.hank.musicfree.plugin.meta.PluginMetaStoreTest'
```

Expected: FAIL，原因是 `alternativePlugins`、`getAlternativePlugin()`、`setAlternativePlugin()` 不存在。

- [ ] **Step 3: 实现持久化 API**

Modify `PluginMetaStore.kt`:

```kotlin
val alternativePlugins: Flow<Map<String, String>> = dataStore.data.map { prefs ->
    prefs[KEY_ALTERNATIVE_PLUGINS]?.let { jsonStr ->
        runCatching { json.decodeFromString<Map<String, String>>(jsonStr) }
            .onFailure { Log.w(TAG, "Failed to decode alternative_plugins, resetting", it) }
            .getOrDefault(emptyMap())
            .filterValues { it.isNotBlank() }
    } ?: emptyMap()
}

fun getAlternativePlugin(platform: String): Flow<String?> =
    alternativePlugins.map { alternatives -> alternatives[platform] }

suspend fun setAlternativePlugin(sourcePlatform: String, targetPlatform: String?) {
    dataStore.edit { prefs ->
        val current = currentAlternativePlugins(prefs).toMutableMap()
        val normalizedTarget = targetPlatform?.trim().orEmpty()
        if (normalizedTarget.isBlank() || normalizedTarget == sourcePlatform) {
            current.remove(sourcePlatform)
        } else {
            current[sourcePlatform] = normalizedTarget
        }
        if (current.isEmpty()) {
            prefs.remove(KEY_ALTERNATIVE_PLUGINS)
        } else {
            prefs[KEY_ALTERNATIVE_PLUGINS] = json.encodeToString(current)
        }
    }
}

private fun currentAlternativePlugins(prefs: Preferences): Map<String, String> =
    prefs[KEY_ALTERNATIVE_PLUGINS]?.let { jsonStr ->
        runCatching { json.decodeFromString<Map<String, String>>(jsonStr) }
            .onFailure { Log.w(TAG, "Failed to decode alternative_plugins, resetting", it) }
            .getOrDefault(emptyMap())
            .filterValues { it.isNotBlank() }
    } ?: emptyMap()
```

Add to companion object:

```kotlin
val KEY_ALTERNATIVE_PLUGINS = stringPreferencesKey("alternative_plugins")
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
./gradlew :plugin:test --tests 'com.hank.musicfree.plugin.meta.PluginMetaStoreTest'
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/meta/PluginMetaStore.kt plugin/src/test/java/com/hank/musicfree/plugin/meta/PluginMetaStoreTest.kt
git commit -m "feat(plugin): persist alternative plugin mapping"
```

---

### Task 3: 插件用户变量声明与运行时刷新

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginUserVariable.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginInfo.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt`
- Create: `plugin/src/test/java/com/hank/musicfree/plugin/manager/PluginManagerUserVariablesTest.kt`

- [ ] **Step 1: 写失败测试**

Create `PluginManagerUserVariablesTest.kt`:

```kotlin
package com.hank.musicfree.plugin.manager

import android.content.Context
import com.hank.musicfree.plugin.meta.PluginMetaStore
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PluginManagerUserVariablesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `loads user variable declarations and filters missing keys`() = runTest {
        val manager = managerWithVariables(emptyMap())
        val pluginFile = tempFolder.newFile("vars.js")
        pluginFile.writeText(pluginScript())

        val plugin = manager.installFromFile(pluginFile)

        assertEquals("vars", plugin!!.info.platform)
        assertEquals(1, plugin.info.userVariables.size)
        assertEquals("cookie", plugin.info.userVariables.first().key)
        assertEquals("Cookie", plugin.info.userVariables.first().name)
        assertEquals("输入 Cookie", plugin.info.userVariables.first().hint)
    }

    @Test
    fun `setUserVariables persists and refreshes loaded runtime`() = runTest {
        val metaStore = mock<PluginMetaStore>()
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(metaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        whenever(metaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        val manager = manager(metaStore)
        val pluginFile = tempFolder.newFile("vars.js")
        pluginFile.writeText(pluginScript())
        val plugin = manager.installFromFile(pluginFile)!!

        manager.setUserVariables("vars", mapOf("cookie" to "abc"))

        verify(metaStore).setUserVariables("vars", mapOf("cookie" to "abc"))
        val imported = plugin.importMusicItem("https://example.com/a")
        assertEquals("abc", imported!!.raw["cookie"])
    }

    private fun managerWithVariables(values: Map<String, String>): PluginManager {
        val metaStore = mock<PluginMetaStore>()
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(metaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        whenever(metaStore.getUserVariables(any())).thenReturn(flowOf(values))
        return manager(metaStore)
    }

    private fun manager(metaStore: PluginMetaStore): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        whenever(context.packageName).thenReturn("com.test")
        return PluginManager(context, metaStore)
    }

    private fun pluginScript() = """
        module.exports = {
          platform: 'vars',
          version: '1.0.0',
          userVariables: [
            { key: 'cookie', name: 'Cookie', hint: '输入 Cookie' },
            { name: 'No Key' }
          ],
          async importMusicItem(urlLike) {
            const vars = env.getUserVariables();
            return { id: '1', title: 'Song', artist: 'Artist', platform: 'vars', raw: { cookie: vars.cookie || '' } };
          }
        };
    """.trimIndent()
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :plugin:test --tests 'com.hank.musicfree.plugin.manager.PluginManagerUserVariablesTest'
```

Expected: FAIL，原因是 `PluginInfo.userVariables` 和 `PluginManager.setUserVariables()` 不存在。

- [ ] **Step 3: 新增用户变量模型**

Create `PluginUserVariable.kt`:

```kotlin
package com.hank.musicfree.plugin.api

data class PluginUserVariable(
    val key: String,
    val name: String? = null,
    val hint: String? = null,
)
```

Modify `PluginInfo.kt`:

```kotlin
val supportedMethods: Set<String> = emptySet(),
val userVariables: List<PluginUserVariable> = emptyList(),
```

- [ ] **Step 4: 解析插件声明**

In `PluginManager.extractPluginInfo()`, add:

```kotlin
suspend fun userVariables(): List<PluginUserVariable> {
    val raw = engine.evaluate<Any?>("JSON.stringify(__plugin.userVariables)")?.toString()
    if (raw == null || raw == "undefined" || raw == "null" || !raw.startsWith("[")) {
        return emptyList()
    }
    val array = org.json.JSONArray(raw)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("key").trim()
            if (key.isBlank()) continue
            add(
                com.hank.musicfree.plugin.api.PluginUserVariable(
                    key = key,
                    name = item.optString("name").takeIf { it.isNotBlank() },
                    hint = item.optString("hint").takeIf { it.isNotBlank() },
                ),
            )
        }
    }
}
```

Pass the parsed declaration list into the existing `PluginInfo` constructor:

```kotlin
userVariables = userVariables(),
```

- [ ] **Step 5: 支持运行时刷新**

Add to `LoadedPlugin`:

```kotlin
fun updateUserVariables(values: Map<String, String>) {
    val jsonStr = kotlinx.serialization.json.Json.encodeToString(values)
    engine.evaluate<Any?>("globalThis.__userVariables = JSON.parse('${jsonStr.escapeJsString()}')")
}
```

If `escapeJsString` is private in `LoadedPlugin`, reuse the existing private method inside the class. If the method is not visible at the new call site, move it inside `companion object` as a private function and keep all existing callers unchanged.

Add to `PluginManager`:

```kotlin
suspend fun setUserVariables(platform: String, variables: Map<String, String>) {
    pluginMetaStore.setUserVariables(platform, variables)
    _plugins.value.firstOrNull { it.info.platform == platform }?.updateUserVariables(variables)
}
```

- [ ] **Step 6: 运行测试确认通过**

Run:

```bash
./gradlew :plugin:test --tests 'com.hank.musicfree.plugin.manager.PluginManagerUserVariablesTest'
```

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/api/PluginUserVariable.kt plugin/src/main/java/com/hank/musicfree/plugin/api/PluginInfo.kt plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt plugin/src/test/java/com/hank/musicfree/plugin/manager/PluginManagerUserVariablesTest.kt
git commit -m "feat(plugin): support user variable declarations"
```

---

### Task 4: 插件音源解析服务与 Hilt 绑定

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/di/PluginModule.kt`
- Create: `plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceTest.kt`

- [ ] **Step 1: 写失败测试**

Create `PluginMediaSourceServiceTest.kt`:

```kotlin
package com.hank.musicfree.plugin.media

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluginMediaSourceServiceTest {
    @Test
    fun `uses alternative plugin and preserves original identity`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "https://source.example/1.mp3")
        val target = plugin("target", supportsMedia = true, url = "https://target.example/1.mp3")
        val service = service(
            plugins = listOf(source, target),
            alternatives = mapOf("source" to "target"),
        )

        val result = service.resolve(item("source"))!!

        assertEquals("source", result.requestedPlatform)
        assertEquals("target", result.resolverPlatform)
        assertTrue(result.redirected)
        assertEquals("source", result.item.platform)
        assertEquals("https://target.example/1.mp3", result.item.url)
    }

    @Test
    fun `falls back to source when alternative has no url`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "https://source.example/1.mp3")
        val target = plugin("target", supportsMedia = true, url = "")
        val service = service(
            plugins = listOf(source, target),
            alternatives = mapOf("source" to "target"),
        )

        val result = service.resolve(item("source"))!!

        assertEquals("source", result.resolverPlatform)
        assertFalse(result.redirected)
        assertEquals("https://source.example/1.mp3", result.item.url)
    }

    @Test
    fun `ignores disabled alternative plugin`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "https://source.example/1.mp3")
        val target = plugin("target", supportsMedia = true, url = "https://target.example/1.mp3")
        val service = service(
            plugins = listOf(source, target),
            alternatives = mapOf("source" to "target"),
            disabled = setOf("target"),
        )

        val result = service.resolve(item("source"))!!

        assertEquals("source", result.resolverPlatform)
        assertFalse(result.redirected)
    }

    private fun service(
        plugins: List<LoadedPlugin>,
        alternatives: Map<String, String>,
        disabled: Set<String> = emptySet(),
    ): PluginMediaSourceService {
        val manager = mock<PluginManager>()
        val metaStore = mock<PluginMetaStore>()
        whenever(manager.plugins).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(plugins))
        plugins.forEach { plugin ->
            whenever(manager.getPlugin(plugin.info.platform)).thenReturn(plugin)
        }
        whenever(metaStore.alternativePlugins).thenReturn(flowOf(alternatives))
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(disabled))
        whenever(manager.pluginMetaStore).thenReturn(metaStore)
        return PluginMediaSourceService(manager)
    }

    private fun plugin(platform: String, supportsMedia: Boolean, url: String): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = emptyList(),
                supportedMethods = if (supportsMedia) setOf("getMediaSource") else emptySet(),
            ),
        )
        whenever(plugin.getMediaSource(any(), any())).thenReturn(
            MediaSourceResult(url = url, headers = null, userAgent = null, quality = null),
        )
        return plugin
    }

    private fun item(platform: String) = MusicItem(
        id = "1",
        platform = platform,
        title = "Song",
        artist = "Artist",
        album = null,
        duration = null,
        url = null,
        artwork = null,
        qualities = null,
    )
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :plugin:test --tests 'com.hank.musicfree.plugin.media.PluginMediaSourceServiceTest'
```

Expected: FAIL，原因是 `PluginMediaSourceService` 不存在。

- [ ] **Step 3: 实现解析服务**

Create `PluginMediaSourceService.kt`:

```kotlin
package com.hank.musicfree.plugin.media

import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginMediaSourceService @Inject constructor(
    private val pluginManager: PluginManager,
) : MediaSourceResolver {

    override suspend fun resolve(
        item: MusicItem,
        quality: String,
    ): MediaSourceResolution? {
        if (!item.url.isNullOrBlank()) {
            return null
        }
        val sourcePlugin = pluginManager.getPlugin(item.platform) ?: return null
        val disabled = pluginManager.pluginMetaStore.disabledPlugins.first()
        val alternatives = pluginManager.pluginMetaStore.alternativePlugins.first()
        val alternativePlatform = alternatives[item.platform]
            ?.takeUnless { it == item.platform }
            ?.takeUnless { it in disabled }
        val alternativePlugin = alternativePlatform
            ?.let { pluginManager.getPlugin(it) }
            ?.takeIf { it.supportsMediaSource() }

        alternativePlugin?.resolveWith(item, quality, requestedPlatform = item.platform, redirected = true)
            ?.let { return it }

        return sourcePlugin.resolveWith(item, quality, requestedPlatform = item.platform, redirected = false)
    }

    private suspend fun LoadedPlugin.resolveWith(
        item: MusicItem,
        quality: String,
        requestedPlatform: String,
        redirected: Boolean,
    ): MediaSourceResolution? {
        if (!supportsMediaSource()) return null
        val source = runCatching { getMediaSource(item, quality) }.getOrNull()
            ?.takeIf { it.url.isNotBlank() }
            ?: return null
        return MediaSourceResolution(
            item = item.copy(url = source.url),
            source = source,
            requestedPlatform = requestedPlatform,
            resolverPlatform = info.platform,
            redirected = redirected,
        )
    }

    private fun LoadedPlugin.supportsMediaSource(): Boolean =
        "getMediaSource" in info.supportedMethods
}
```

- [ ] **Step 4: 添加 Hilt 绑定**

Modify `PluginModule.kt`:

```kotlin
package com.hank.musicfree.plugin.di

import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.plugin.media.PluginMediaSourceService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginModule {
    @Binds
    @Singleton
    abstract fun bindMediaSourceResolver(
        impl: PluginMediaSourceService,
    ): MediaSourceResolver
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```bash
./gradlew :plugin:test --tests 'com.hank.musicfree.plugin.media.PluginMediaSourceServiceTest'
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt plugin/src/main/java/com/hank/musicfree/plugin/di/PluginModule.kt plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceTest.kt
git commit -m "feat(plugin): resolve media source with redirects"
```

---

### Task 5: 播放器队列全局解析

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Modify: `player/src/androidTest/java/com/hank/musicfree/player/controller/PlayerControllerTest.kt`
- Modify: `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerNotificationControlsTest.kt`

- [ ] **Step 1: 写失败测试**

Append to `PlayerControllerNotificationControlsTest`:

```kotlin
@Test
fun `notification next resolves item without url before playback`() {
    val resolver = RecordingResolver(
        resolvedUrl = "https://cdn.example.test/2.mp3",
    )
    val controller = PlayerController(context, resolver)

    try {
        controller.playQueue(
            listOf(
                testItem("1"),
                testItem("2").copy(url = null),
            ),
            startIndex = 0,
        )

        PlaybackNotificationCommandHandler.skipToNext()

        assertEquals("2", controller.playQueue.currentItem?.id)
        assertEquals("https://cdn.example.test/2.mp3", controller.playQueue.currentItem?.url)
        assertEquals(listOf("2"), resolver.requestedIds)
    } finally {
        controller.release()
    }
}

private class RecordingResolver(
    private val resolvedUrl: String,
) : com.hank.musicfree.core.media.MediaSourceResolver {
    val requestedIds = mutableListOf<String>()

    override suspend fun resolve(
        item: MusicItem,
        quality: String,
    ): com.hank.musicfree.core.media.MediaSourceResolution? {
        requestedIds += item.id
        val source = com.hank.musicfree.core.model.MediaSourceResult(
            url = resolvedUrl,
            headers = null,
            userAgent = null,
            quality = null,
        )
        return com.hank.musicfree.core.media.MediaSourceResolution(
            item = item.copy(url = resolvedUrl),
            source = source,
            requestedPlatform = item.platform,
            resolverPlatform = item.platform,
            redirected = false,
        )
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests 'com.hank.musicfree.player.controller.PlayerControllerNotificationControlsTest'
```

Expected: FAIL，原因是 `PlayerController(context, resolver)` 构造参数不存在，且队列切歌不解析无 URL 歌曲。

- [ ] **Step 3: 修改 PlayerController 构造与解析入口**

Modify constructor:

```kotlin
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaSourceResolver: MediaSourceResolver = EmptyMediaSourceResolver,
) : PlaybackNotificationQueueControls {
```

Add imports:

```kotlin
import com.hank.musicfree.core.media.EmptyMediaSourceResolver
import com.hank.musicfree.core.media.MediaSourceResolver
```

Replace `setMediaItemAndPlay(item)` with an async-safe flow:

```kotlin
private fun setMediaItemAndPlay(item: MusicItem) {
    withConnectedControllerAsync { controller ->
        val playable = resolvePlayableItem(item)
        if (playable == null) {
            _errorEvents.tryEmit("播放失败: 无法解析音源")
            return@withConnectedControllerAsync
        }
        recordHistory(playable)
        playQueue.replaceCurrent(playable)
        val mediaItem = playable.toMediaItem(defaultArtworkUri)
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }
}

private suspend fun resolvePlayableItem(item: MusicItem): MusicItem? {
    if (!item.url.isNullOrBlank()) return item
    return mediaSourceResolver.resolve(item)?.item
}
```

If `PlayQueue` lacks `replaceCurrent`, add a focused method to `player/src/main/java/com/hank/musicfree/player/queue/PlayQueue.kt`:

```kotlin
fun replaceCurrent(item: MusicItem) {
    val index = currentIndex
    if (index in _items.indices) {
        val previous = _items[index]
        _items[index] = item
        originalOrder = originalOrder?.map { existing ->
            if (existing.id == previous.id && existing.platform == previous.platform) {
                item
            } else {
                existing
            }
        }
    }
}
```

Add async helper:

```kotlin
private fun withConnectedControllerAsync(action: suspend (MediaController) -> Unit) {
    attachNotificationControls()
    mediaController?.let { controller ->
        scope.launch {
            action(controller)
            runOnControllerThread { emitState() }
        }
        return
    }
    val existingConnectJob = connectJob
    if (existingConnectJob?.isActive == true) {
        scope.launch {
            existingConnectJob.join()
            mediaController?.let { controller ->
                action(controller)
                runOnControllerThread { emitState() }
            }
        }
        return
    }
    connectJob = scope.launch {
        runCatching { connect() }.onFailure { e ->
            _errorEvents.emit("播放服务连接失败: ${e.message}")
            return@launch
        }
        mediaController?.let { controller ->
            action(controller)
            runOnControllerThread { emitState() }
        }
    }
}
```

- [ ] **Step 4: 更新手动构造测试**

Existing tests that call `PlayerController(context)` continue to compile because the resolver has default value. For assertions that depend on immediate state after `skipToNext()`, keep the existing `waitUntil(description, condition)` helper because resolution is now asynchronous.

- [ ] **Step 5: 运行测试确认通过**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests 'com.hank.musicfree.player.controller.PlayerControllerNotificationControlsTest'
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt player/src/main/java/com/hank/musicfree/player/queue/PlayQueue.kt player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerNotificationControlsTest.kt player/src/androidTest/java/com/hank/musicfree/player/controller/PlayerControllerTest.kt
git commit -m "feat(player): resolve queued plugin media before playback"
```

---

### Task 6: 下载与详情页接入共享解析

**Files:**
- Modify: `downloader/src/main/java/com/hank/musicfree/downloader/quality/PluginMediaSourceResolver.kt`
- Modify: `downloader/src/main/java/com/hank/musicfree/downloader/di/DownloaderModule.kt`
- Modify: `downloader/build.gradle.kts`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/AlbumDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/artistdetail/ArtistDetailViewModel.kt`

- [ ] **Step 1: 修改下载 resolver**

Replace `PluginMediaSourceResolver` body:

```kotlin
package com.hank.musicfree.downloader.quality

import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginMediaSourceResolver @Inject constructor(
    private val mediaSourceResolver: MediaSourceResolver,
) {
    suspend fun resolve(item: MusicItem, qualityWire: String): MediaSourceResult? {
        return mediaSourceResolver.resolve(item, qualityWire)?.source
    }
}
```

If no downloader source imports `com.hank.musicfree.plugin.*` after this change, remove this line from `downloader/build.gradle.kts`:

```kotlin
implementation(project(":plugin"))
```

- [ ] **Step 2: 修改详情页 ViewModel 构造**

For each detail ViewModel, inject:

```kotlin
private val mediaSourceResolver: com.hank.musicfree.core.media.MediaSourceResolver,
```

Replace local `plugin.getMediaSource(clicked)` blocks with:

```kotlin
val resolved = if (clicked.url.isNullOrBlank()) {
    mediaSourceResolver.resolve(clicked)?.item ?: return false
} else {
    clicked
}
```

Keep detail loading methods (`getMusicSheetInfo`、`getTopListDetail`、`getAlbumInfo`、`getArtistWorks`) on the original route plugin; only media URL resolution moves to the shared resolver.

- [ ] **Step 3: 运行相关测试**

Run:

```bash
./gradlew :downloader:test :feature:home:testDebugUnitTest
```

Expected: PASS. If constructor changes break existing ViewModel tests, update test setup to pass `EmptyMediaSourceResolver`.

- [ ] **Step 4: 提交**

```bash
git add downloader/src/main/java/com/hank/musicfree/downloader/quality/PluginMediaSourceResolver.kt downloader/src/main/java/com/hank/musicfree/downloader/di/DownloaderModule.kt downloader/build.gradle.kts feature/home/src/main/java/com/hank/musicfree/feature/home/pluginsheet/PluginSheetDetailViewModel.kt feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListDetailViewModel.kt feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/AlbumDetailViewModel.kt feature/home/src/main/java/com/hank/musicfree/feature/home/artistdetail/ArtistDetailViewModel.kt
git commit -m "feat(media): route detail and download resolution through shared resolver"
```

---

### Task 7: 搜索播放移除写死 fallback

**Files:**
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

In `SearchViewModelTest`, add a test that injects a fake `MediaSourceResolver`, calls `resolveAndPlay(item, queue)` with an item that has no URL, and verifies the queue sent to `PlayerController.playQueue()` contains the resolver URL while preserving original platform.

Use this assertion shape:

```kotlin
argumentCaptor<List<MusicItem>>().apply {
    verify(playerController).playQueue(capture(), eq(0))
    assertEquals("source", firstValue.first().platform)
    assertEquals("https://resolver.example/1.mp3", firstValue.first().url)
}
```

- [ ] **Step 2: 修改 ViewModel 构造**

Inject:

```kotlin
private val mediaSourceResolver: MediaSourceResolver,
```

Remove `WY_FALLBACK_PLATFORM` and the `resolveMediaSourceWithFallback` helper.

Replace call site in `resolveAndPlay`:

```kotlin
val resolvedItem = if (item.url.isNullOrBlank()) {
    mediaSourceResolver.resolve(item)?.item
} else {
    item
}
```

If `resolvedItem == null`, keep existing `PlayEvent.Failed("播放失败，请重试")` behavior.

- [ ] **Step 3: 更新测试构造**

Where tests instantiate `SearchViewModel`, pass `EmptyMediaSourceResolver` unless the test needs a fake resolver.

- [ ] **Step 4: 运行测试**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt
git commit -m "feat(search): use shared media source resolver"
```

---

### Task 8: 插件操作结果与 `.json` 网络安装

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginOperationResult.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt`
- Create: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListActionStateTest.kt`

- [ ] **Step 1: 写失败测试**

Create `PluginListActionStateTest.kt`:

```kotlin
package com.hank.musicfree.feature.settings.pluginlist

import com.hank.musicfree.feature.settings.MainDispatcherRule
import com.hank.musicfree.plugin.manager.PluginOperationErrorCode
import com.hank.musicfree.plugin.manager.PluginOperationFailure
import com.hank.musicfree.plugin.manager.PluginOperationResult
import com.hank.musicfree.plugin.manager.PluginOperationType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginListActionStateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `partial failures expose detail rows`() {
        val state = PluginOperationUiState.fromResult(
            successMessage = "更新完成",
            partialMessage = "部分插件更新失败",
            failureMessage = "全部插件更新失败",
            result = PluginOperationResult(
                operationType = PluginOperationType.UPDATE_ALL,
                targetPlugins = listOf("a", "b"),
                successCount = 1,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = "b",
                        sourceRef = "https://example.com/b.js",
                        errorCode = PluginOperationErrorCode.SOURCE_UNREACHABLE,
                        message = "下载失败",
                    ),
                ),
                startedAtEpochMs = 1,
                finishedAtEpochMs = 2,
            ),
        )

        val partial = state as PluginOperationUiState.PartialFailure
        assertEquals("部分插件更新失败", partial.message)
        assertEquals("https://example.com/b.js", partial.failures.first().source)
        assertEquals("下载失败", partial.failures.first().message)
    }
}
```

- [ ] **Step 2: 实现 UI 状态模型**

In `PluginListViewModel.kt`, replace `InstallState` with:

```kotlin
data class FailureDetail(
    val source: String?,
    val pluginName: String?,
    val message: String,
)

sealed interface PluginOperationUiState {
    data object Idle : PluginOperationUiState
    data class Loading(val label: String) : PluginOperationUiState
    data class Success(val message: String) : PluginOperationUiState
    data class PartialFailure(
        val message: String,
        val failures: List<FailureDetail>,
    ) : PluginOperationUiState
    data class Failure(
        val message: String,
        val failures: List<FailureDetail> = emptyList(),
    ) : PluginOperationUiState

    companion object {
        fun fromResult(
            successMessage: String,
            partialMessage: String,
            failureMessage: String,
            result: PluginOperationResult,
        ): PluginOperationUiState {
            val details = result.failures.map {
                FailureDetail(
                    source = it.sourceRef,
                    pluginName = it.targetPlugin,
                    message = it.message,
                )
            }
            return when {
                result.failureCount == 0 -> Success(successMessage)
                result.successCount > 0 -> PartialFailure(partialMessage, details)
                else -> Failure(failureMessage, details)
            }
        }
    }
}
```

Expose:

```kotlin
private val _operationState = MutableStateFlow<PluginOperationUiState>(PluginOperationUiState.Idle)
val operationState: StateFlow<PluginOperationUiState> = _operationState.asStateFlow()
```

- [ ] **Step 3: 支持 `.json` URL 安装**

Add to `PluginManager`:

```kotlin
suspend fun installFromNetworkUrl(url: String): PluginOperationResult = mutex.withLock {
    withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return@withContext PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = emptyList(),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = url,
                        errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                        message = "URL 不能为空",
                    ),
                ),
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = System.currentTimeMillis(),
            )
        }
        if (trimmed.endsWith(".json", ignoreCase = true)) {
            val rawJson = downloadUrlBytes(trimmed)?.toString(StandardCharsets.UTF_8)
            val parsed = rawJson?.let { SubscriptionParser.parse(it) }
            if (parsed == null || parsed.isMalformed) {
                return@withContext PluginOperationResult(
                    operationType = PluginOperationType.ADD,
                    targetPlugins = listOf(trimmed),
                    successCount = 0,
                    failureCount = 1,
                    failures = listOf(
                        PluginOperationFailure(
                            sourceRef = trimmed,
                            errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                            message = "订阅格式无效",
                        ),
                    ),
                    startedAtEpochMs = startedAt,
                    finishedAtEpochMs = System.currentTimeMillis(),
                )
            }
            return@withContext updateSubscriptionEntriesLocked(
                subscriptionUrl = trimmed,
                entries = parsed.installableEntries,
                startedAt = startedAt,
                targets = parsed.installableEntries.map { it.url },
            )
        }
        val plugin = installFromUrlLocked(trimmed, trimmed.substringAfterLast('/').ifBlank { "plugin.js" })
        PluginOperationResult(
            operationType = PluginOperationType.ADD,
            targetPlugins = listOf(trimmed),
            successCount = if (plugin != null) 1 else 0,
            failureCount = if (plugin != null) 0 else 1,
            failures = if (plugin != null) emptyList() else listOf(
                PluginOperationFailure(
                    sourceRef = trimmed,
                    errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                    message = "插件安装失败",
                ),
            ),
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = System.currentTimeMillis(),
        )
    }
}
```

In `PluginListViewModel.installFromUrl(url)`, call `pluginManager.installFromNetworkUrl(url)` and convert the returned `PluginOperationResult` with `PluginOperationUiState.fromResult`.

- [ ] **Step 4: 更新旧测试**

Update `PluginListViewModelTest` assertions:

```kotlin
assertEquals(PluginOperationUiState.Idle, viewModel.operationState.value)
```

Remove old `InstallState` references.

- [ ] **Step 5: 运行测试**

Run:

```bash
./gradlew :plugin:test :feature:settings:testDebugUnitTest
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginOperationResult.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListActionStateTest.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModelTest.kt
git commit -m "feat(settings): structure plugin operation feedback"
```

---

### Task 9: PluginListViewModel 补齐重定向、用户变量、导入能力

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListActionStateTest.kt`

- [ ] **Step 1: 扩展 UI item**

Update `PluginUiItem`:

```kotlin
data class PluginUiItem(
    val info: PluginInfo,
    val enabled: Boolean,
    val alternativePlatform: String?,
    val alternativeInvalid: Boolean,
    val canUpdate: Boolean,
    val canImportMusicItem: Boolean,
    val canImportMusicSheet: Boolean,
    val canEditUserVariables: Boolean,
)
```

Update `pluginItems` combine to include `metaStore.alternativePlugins` and compute flags:

```kotlin
val installedPlatforms = allInfos.map { it.platform }.toSet()
val alternative = alternatives[info.platform]
PluginUiItem(
    info = info,
    enabled = info.platform !in disabled,
    alternativePlatform = alternative,
    alternativeInvalid = alternative != null && alternative !in installedPlatforms,
    canUpdate = !info.srcUrl.isNullOrBlank(),
    canImportMusicItem = "importMusicItem" in info.supportedMethods,
    canImportMusicSheet = "importMusicSheet" in info.supportedMethods,
    canEditUserVariables = info.userVariables.isNotEmpty(),
)
```

- [ ] **Step 2: 新增 ViewModel 方法**

Add:

```kotlin
fun setAlternativePlugin(sourcePlatform: String, targetPlatform: String?) {
    viewModelScope.launch {
        metaStore.setAlternativePlugin(sourcePlatform, targetPlatform)
    }
}

fun saveUserVariables(platform: String, values: Map<String, String>) {
    performOperation("保存用户变量中") {
        pluginManager.setUserVariables(platform, values)
        PluginOperationUiState.Success("设置成功")
    }
}

private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

fun importMusicItem(platform: String, urlLike: String) {
    performOperation("导入单曲中") {
        val trimmed = urlLike.trim()
        if (trimmed.isBlank()) return@performOperation PluginOperationUiState.Failure("链接有误或目标为空")
        val item = pluginManager.getPlugin(platform)?.importMusicItem(trimmed)
            ?: return@performOperation PluginOperationUiState.Failure("导入单曲失败")
        _sheetState.value = AddToPlaylistSheetState.single(item)
        PluginOperationUiState.Success("解析成功")
    }
}

fun importMusicSheet(platform: String, urlLike: String) {
    performOperation("导入歌单中") {
        val trimmed = urlLike.trim()
        if (trimmed.isBlank()) return@performOperation PluginOperationUiState.Failure("链接有误或目标为空")
        val items = pluginManager.getPlugin(platform)?.importMusicSheet(trimmed).orEmpty()
        if (items.isEmpty()) return@performOperation PluginOperationUiState.Failure("链接有误或目标歌单为空")
        _sheetState.value = AddToPlaylistSheetState.batch(items)
        PluginOperationUiState.Success("发现 ${items.size} 首歌曲")
    }
}
```

Change `performOperation` signature:

```kotlin
private fun performOperation(
    loadingLabel: String,
    operation: suspend () -> PluginOperationUiState,
)
```

- [ ] **Step 3: 写/更新测试**

Add assertions in `PluginListActionStateTest`:

```kotlin
@Test
fun `plugin item exposes capability flags`() = runTest {
    val plugins = MutableStateFlow(
        listOf(
            loadedPlugin(
                platform = "source",
                methods = setOf("getMediaSource", "importMusicItem", "importMusicSheet"),
                userVariableCount = 1,
                srcUrl = "https://example.com/source.js",
            ),
        ),
    )
    val metaStore = fakeMetaStore()
    val manager = fakeManager(plugins, metaStore)
    val viewModel = PluginListViewModel(manager)

    val item = viewModel.pluginItems.value.first()

    assertTrue(item.canUpdate)
    assertTrue(item.canImportMusicItem)
    assertTrue(item.canImportMusicSheet)
    assertTrue(item.canEditUserVariables)
}
```

Use local fake helpers in the test file. The helpers should return `MutableStateFlow` for `disabledPlugins`、`pluginOrder`、`alternativePlugins`、`subscriptions` and mock `LoadedPlugin.info`.

- [ ] **Step 4: 运行测试**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListActionStateTest.kt
git commit -m "feat(settings): model plugin card parity actions"
```

---

### Task 10: PluginListScreen RN 直译卡片 UI

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`

- [ ] **Step 1: 替换状态渲染**

Replace `installState` collection with:

```kotlin
val operationState by viewModel.operationState.collectAsStateWithLifecycle()
```

Render:

```kotlin
when (val state = operationState) {
    is PluginOperationUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    is PluginOperationUiState.Success -> OperationMessage(state.message)
    is PluginOperationUiState.PartialFailure -> OperationMessage(
        message = state.message,
        isError = true,
        onViewDetail = { failureDialog = state.failures },
    )
    is PluginOperationUiState.Failure -> OperationMessage(
        message = state.message,
        isError = true,
        onViewDetail = state.failures.takeIf { it.isNotEmpty() }?.let { failures ->
            { failureDialog = failures }
        },
    )
    PluginOperationUiState.Idle -> {}
}
```

Add:

```kotlin
@Composable
private fun OperationMessage(
    message: String,
    isError: Boolean = false,
    onViewDetail: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (onViewDetail != null) {
            TextButton(onClick = onViewDetail) { Text("查看详情") }
        }
    }
}
```

- [ ] **Step 2: 扩展卡片按钮**

Update `PluginCard` parameters:

```kotlin
onSetAlternative: () -> Unit,
onImportMusicItem: () -> Unit,
onImportMusicSheet: () -> Unit,
onEditUserVariables: () -> Unit,
```

Render buttons in the action row:

```kotlin
if (item.canUpdate) {
    AssistChip(onClick = onUpdate, label = { Text("更新") })
    AssistChip(onClick = onShare, label = { Text("分享") })
}
AssistChip(onClick = onUninstall, label = { Text("卸载") })
AssistChip(onClick = onSetAlternative, label = { Text("音源重定向") })
if (item.canImportMusicItem) {
    AssistChip(onClick = onImportMusicItem, label = { Text("导入单曲") })
}
if (item.canImportMusicSheet) {
    AssistChip(onClick = onImportMusicSheet, label = { Text("导入歌单") })
}
if (item.canEditUserVariables) {
    AssistChip(onClick = onEditUserVariables, label = { Text("用户变量") })
}
```

Below version/author, render:

```kotlin
item.alternativePlatform?.let { target ->
    Text(
        text = if (item.alternativeInvalid) {
            "音源重定向目标不可用：$target"
        } else {
            "该插件实际使用「$target」插件解析音乐的音源"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (item.alternativeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = rpx(6)),
    )
}
```

- [ ] **Step 3: 添加对话框状态**

At screen level add:

```kotlin
var alternativeTarget by remember { mutableStateOf<PluginUiItem?>(null) }
var importMusicItemTarget by remember { mutableStateOf<PluginUiItem?>(null) }
var importMusicSheetTarget by remember { mutableStateOf<PluginUiItem?>(null) }
var userVariablesTarget by remember { mutableStateOf<PluginUiItem?>(null) }
var failureDialog by remember { mutableStateOf<List<FailureDetail>?>(null) }
```

Add `FailureDetailDialog`, `AlternativePluginDialog`, `ImportUrlDialog`, and `UserVariablesDialog` composables in the same file. Keep them private and focused.

- [ ] **Step 4: 实现音源重定向对话框**

Candidate list:

```kotlin
val mediaSourceCandidates = pluginItems
        .filter { it.enabled && "getMediaSource" in it.info.supportedMethods }
```

Dialog saves:

```kotlin
viewModel.setAlternativePlugin(source.info.platform, selectedPlatform)
```

Use `null` for `无音源重定向`.

- [ ] **Step 5: 实现导入对话框**

For single item:

```kotlin
viewModel.importMusicItem(target.info.platform, url)
```

For sheet:

```kotlin
viewModel.importMusicSheet(target.info.platform, url)
```

`feature:settings` already depends on `:data`, so导入结果的添加到歌单状态保存在 `PluginListViewModel` 内，不新增 route-level 回调。

- [ ] **Step 6: 实现用户变量对话框**

Use `item.info.userVariables` to render fields and initial values from a ViewModel method:

```kotlin
viewModel.saveUserVariables(item.info.platform, values)
```

Do not display entries whose `key` is blank; the parser already filters them.

- [ ] **Step 7: 运行构建**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest :app:assembleDebug
```

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt
git commit -m "feat(settings): show RN parity plugin card actions"
```

---

### Task 11: 设置页导入结果接入添加到歌单能力

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListActionStateTest.kt`
- Reference: `core/src/main/java/com/hank/musicfree/core/ui/AddToPlaylistBottomSheetContent.kt`
- Reference: `core/src/main/java/com/hank/musicfree/core/ui/AddToPlaylistSheetState.kt`
- Reference: `feature/home/src/main/java/com/hank/musicfree/feature/home/playlistimport/PlaylistImportViewModel.kt`

- [ ] **Step 1: 扩展 ViewModel 歌单状态**

Inject `PlaylistRepository` into `PluginListViewModel`:

```kotlin
private val playlistRepository: PlaylistRepository,
```

Add imports:

```kotlin
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import com.hank.musicfree.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
```

Add playlist list state. `_sheetState` and `sheetState` already exist from Task 9; keep those names unchanged:

```kotlin
val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )
```

- [ ] **Step 2: 将导入解析结果转为待添加状态**

Verify the import methods added in Task 9 keep this exact ViewModel-owned shape:

```kotlin
fun importMusicItem(platform: String, urlLike: String) {
    performOperation("导入单曲中") {
        val trimmed = urlLike.trim()
        if (trimmed.isBlank()) return@performOperation PluginOperationUiState.Failure("链接有误或目标为空")
        val item = pluginManager.getPlugin(platform)?.importMusicItem(trimmed)
            ?: return@performOperation PluginOperationUiState.Failure("导入单曲失败")
        _sheetState.value = AddToPlaylistSheetState.single(item)
        PluginOperationUiState.Success("解析成功")
    }
}

fun importMusicSheet(platform: String, urlLike: String) {
    performOperation("导入歌单中") {
        val trimmed = urlLike.trim()
        if (trimmed.isBlank()) return@performOperation PluginOperationUiState.Failure("链接有误或目标为空")
        val items = pluginManager.getPlugin(platform)?.importMusicSheet(trimmed).orEmpty()
        if (items.isEmpty()) return@performOperation PluginOperationUiState.Failure("链接有误或目标歌单为空")
        _sheetState.value = AddToPlaylistSheetState.batch(items)
        PluginOperationUiState.Success("发现 ${items.size} 首歌曲")
    }
}
```

Add target methods:

```kotlin
fun hideAddToPlaylistSheet() {
    _sheetState.value = AddToPlaylistSheetState()
}

fun addImportedItemsToPlaylist(targetPlaylistId: String) {
    val items = _sheetState.value.pendingItems
    if (items.isEmpty()) return
    performOperation("添加到歌单中") {
        val added = playlistRepository.addMusicsToPlaylist(targetPlaylistId, items)
        val skipped = items.size - added
        _sheetState.value = AddToPlaylistSheetState()
        PluginOperationUiState.Success(importResultMessage(added, skipped))
    }
}

fun createPlaylistAndImport(name: String) {
    val playlistName = name.trim()
    val items = _sheetState.value.pendingItems
    if (playlistName.isBlank() || items.isEmpty()) return
    performOperation("创建歌单中") {
        val playlistId = UUID.randomUUID().toString()
        playlistRepository.createPlaylist(
            Playlist(id = playlistId, name = playlistName, coverUri = null),
        )
        val added = playlistRepository.addMusicsToPlaylist(playlistId, items)
        val skipped = items.size - added
        _sheetState.value = AddToPlaylistSheetState()
        PluginOperationUiState.Success(importResultMessage(added, skipped))
    }
}

private fun importResultMessage(added: Int, skipped: Int): String =
    if (skipped > 0) "已导入 $added 首，跳过 $skipped 首重复歌曲" else "已导入 $added 首"
```

- [ ] **Step 3: 在 PluginListScreen 显示添加到歌单 bottom sheet**

Collect:

```kotlin
val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
```

Render:

```kotlin
if (sheetState.visible) {
    ModalBottomSheet(
        onDismissRequest = { viewModel.hideAddToPlaylistSheet() },
    ) {
        AddToPlaylistBottomSheetContent(
            playlists = playlists,
            onSelect = { playlist -> viewModel.addImportedItemsToPlaylist(playlist.id) },
            onCreateNew = { showCreatePlaylistDialog = true },
            folderPlusIcon = painterResource(id = com.hank.musicfree.core.R.drawable.ic_folder_plus),
            favoriteCoverIcon = painterResource(id = com.hank.musicfree.core.R.drawable.ic_playlist_favorite_cover),
        )
    }
}
```

These drawable names match `feature/home/src/main/java/com/hank/musicfree/feature/home/playlistimport/PlaylistImportHost.kt`.

- [ ] **Step 4: 添加新建歌单对话框**

Use the same `AlertDialog + OutlinedTextField` pattern already used by `PluginListScreen.InstallUrlDialog`. On confirm:

```kotlin
viewModel.createPlaylistAndImport(playlistName)
```

- [ ] **Step 5: 运行测试和 app 构建**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest :app:assembleDebug
```

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListActionStateTest.kt
git commit -m "feat(settings): add imported plugin items to playlists"
```

---

### Task 12: 最终验证与文档收口

**Files:**
- Modify if needed: `docs/DOCS_STATUS.md`
- Modify if needed: `docs/superpowers/specs/2026-05-09-plugin-management-parity-design.md`

- [ ] **Step 1: 全量静态验证**

Run:

```bash
./gradlew :core:test :plugin:test :feature:settings:testDebugUnitTest :feature:search:testDebugUnitTest :feature:home:testDebugUnitTest :downloader:test :player:testDebugUnitTest :app:assembleDebug
```

Expected: PASS。

- [ ] **Step 2: 设备可用时运行运行态验收**

Run:

```bash
adb devices
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Manual checks:

1. 安装两个支持 `getMediaSource` 的插件。
2. 在插件管理页为插件 A 设置音源重定向到插件 B。
3. 搜索插件 A 的歌曲并播放，确认可以播放。
4. 将多首无 URL 插件歌曲加入队列，确认下一首和通知切歌可以播放。
5. 从插件歌单、榜单、专辑、歌手详情页点击播放，确认可以播放。
6. 下载插件歌曲，确认下载成功。
7. 编辑插件用户变量，保存后再次调用插件能力，确认新值生效。
8. 使用插件卡片导入单曲和导入歌单，确认能打开添加到歌单并写入。
9. 使用 `.json` URL 批量安装，制造至少一个失败 URL，确认“查看详情”展示失败源和原因。

- [ ] **Step 3: 记录验证结果**

If all verification passes, append a short verification note to the implementation PR description or final handoff. If a command fails, capture the command and first actionable error line in the final handoff.

- [ ] **Step 4: 最终提交**

If Step 1 or Step 2 required documentation adjustments:

```bash
git add docs/DOCS_STATUS.md docs/superpowers/specs/2026-05-09-plugin-management-parity-design.md
git commit -m "docs(plugin): record management parity verification"
```

If no documentation changed, do not create an empty commit.

---

## 自检记录

- Spec 覆盖：插件卡片 RN 直译、音源重定向、用户变量、导入单曲/歌单、批量安装/更新反馈、全局播放/下载解析入口均有对应任务。
- 依赖边界：`:core` 只定义接口和模型，`:plugin` 实现绑定，`:player` 和 `:downloader` 依赖 core contract。
- 测试策略：每个关键模型或行为先写失败测试，再实现，再运行对应 Gradle 任务。
- 工作流：每个任务独立提交，最终使用 Debug 构建收尾，不要求 Release 签名。
