# 插件管理链路实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 版插件管理能力对齐 RN 原版——引擎状态管理（启用/禁用、排序、userVariables、env 注入、PluginInfo 补全）+ 三个独立 UI 页面（插件列表、排序、订阅管理）。

**Architecture:** 分层重构——新增 PluginMetaStore（DataStore）持久化插件元数据，PluginManager 整合该存储并暴露排序/过滤 Flow，三个独立 Compose 页面各自对应 ViewModel，通过 type-safe Navigation 路由串联。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, DataStore Preferences, Hilt, Navigation Compose 2.9.0, Kotlin Serialization, sh.calvin.reorderable

**设计规格:** `docs/superpowers/specs/2026-04-14-plugin-management-design.md`

---

## 前置知识

### 项目结构

```
:app → :feature:* → :data, :player, :plugin → :core
```

- `:core` — 路由定义（`core/src/main/java/.../core/navigation/Routes.kt`）、基础模型
- `:plugin` — QuickJS 引擎、JS 桥接、插件管理
- `:feature:settings` — 设置页面（当前包含插件管理 UI，需要迁出）
- `:app` — NavHost（`app/src/main/java/.../navigation/AppNavHost.kt`）

### 包名约定

所有代码位于 `com.hank.musicfree` 下，模块名对应子包：
- `:plugin` → `.plugin.*`
- `:feature:settings` → `.feature.settings.*`
- `:core` → `.core.*`

### 现有模式

**路由定义**：`@Serializable data object/class` 在 Routes.kt  
**导航注册**：feature 模块提供 `NavGraphBuilder.xxxScreen()` 扩展函数  
**AppNavHost**：调用这些扩展函数，传入导航回调 lambda  
**ViewModel**：`@HiltViewModel` + `hiltViewModel()` + `StateFlow`  
**DataStore**：`AppPreferences` 注入 `DataStore<Preferences>`，由 `DataModule` 提供

### 关键发现

- `MediaSourceResult` 已包含完整字段（url/headers/userAgent/quality），`JsBridge.parseMediaSourceResult()` 也已完整实现。**无需修改。**
- `PluginInfo` 当前仅 6 个字段，缺少 appVersion/primaryKey/defaultSearchType/cacheControl/hints。
- env 注入仅 `{os: 'android'}`，缺少 appVersion/lang/getUserVariables。
- 插件管理 UI 全部耦合在 `SettingsScreen.kt`（523 行），无独立页面。

---

## 文件清单

### 新建文件

| 文件 | 职责 |
|------|------|
| `plugin/src/main/java/.../plugin/meta/PluginMetaStore.kt` | DataStore 持久化插件元数据 |
| `plugin/src/main/java/.../plugin/di/PluginMetaModule.kt` | Hilt 提供 PluginMetaStore 的 DataStore 实例 |
| `plugin/src/test/.../plugin/meta/PluginMetaStoreTest.kt` | PluginMetaStore 单元测试 |
| `feature/settings/src/main/java/.../settings/pluginlist/PluginListScreen.kt` | 插件列表页面 |
| `feature/settings/src/main/java/.../settings/pluginlist/PluginListViewModel.kt` | 插件列表 ViewModel |
| `feature/settings/src/main/java/.../settings/pluginlist/navigation/PluginListNavigation.kt` | 导航注册 |
| `feature/settings/src/main/java/.../settings/pluginsort/PluginSortScreen.kt` | 插件排序页面 |
| `feature/settings/src/main/java/.../settings/pluginsort/PluginSortViewModel.kt` | 插件排序 ViewModel |
| `feature/settings/src/main/java/.../settings/pluginsort/navigation/PluginSortNavigation.kt` | 导航注册 |
| `feature/settings/src/main/java/.../settings/pluginsub/PluginSubscriptionScreen.kt` | 订阅管理页面 |
| `feature/settings/src/main/java/.../settings/pluginsub/PluginSubscriptionViewModel.kt` | 订阅管理 ViewModel |
| `feature/settings/src/main/java/.../settings/pluginsub/navigation/PluginSubscriptionNavigation.kt` | 导航注册 |
| `feature/settings/src/test/.../settings/pluginlist/PluginListViewModelTest.kt` | 插件列表 ViewModel 测试 |
| `feature/settings/src/test/.../settings/pluginsub/PluginSubscriptionViewModelTest.kt` | 订阅管理 ViewModel 测试 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `plugin/src/main/java/.../plugin/api/PluginInfo.kt` | 新增 5 个字段 |
| `plugin/src/main/java/.../plugin/manager/PluginManager.kt` | 集成 PluginMetaStore，新增 getSortedEnabledPlugins/getSearchablePlugins/setPluginEnabled/setPluginOrder，修改 extractPluginInfo 和 env 注入 |
| `plugin/build.gradle.kts` | 添加 DataStore 依赖 |
| `core/src/main/java/.../core/navigation/Routes.kt` | 新增 3 个路由 |
| `app/src/main/java/.../navigation/AppNavHost.kt` | 注册 3 个新页面，添加全局 slide 动画 |
| `feature/settings/src/main/java/.../settings/SettingsScreen.kt` | 移除插件管理代码，改为导航入口 |
| `feature/settings/src/main/java/.../settings/SettingsViewModel.kt` | 移除插件操作方法 |
| `feature/settings/src/main/java/.../settings/navigation/SettingsNavigation.kt` | 新增 onNavigateToPluginList 回调 |
| `feature/settings/build.gradle.kts` | 添加 reorderable 依赖 |
| `gradle/libs.versions.toml` | 添加 reorderable 库声明 |

---

## Task 1: PluginMetaStore — DataStore 持久化层

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/meta/PluginMetaStore.kt`
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/di/PluginMetaModule.kt`
- Modify: `plugin/build.gradle.kts`
- Create: `plugin/src/test/java/com/hank/musicfree/plugin/meta/PluginMetaStoreTest.kt`

### 背景

PluginMetaStore 负责持久化插件的启用/禁用状态、排序、用户变量和订阅源列表。使用独立的 DataStore Preferences 文件（`plugin_meta`），与 `AppPreferences` 使用的 `app_preferences` 分开。

DataStore 要求每个文件名在进程中唯一，通过 `preferencesDataStore` 委托创建。PluginMetaStore 需要通过 Hilt 提供其专用 DataStore 实例。

- [ ] **Step 1: 添加 DataStore 依赖到 plugin 模块**

修改 `plugin/build.gradle.kts`，在 `dependencies` 块中添加：

```kotlin
implementation(libs.androidx.datastore.preferences)
```

同时添加测试依赖（如果尚未存在）：

```kotlin
testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 2: 创建 PluginMetaModule 提供 DataStore 实例**

创建 `plugin/src/main/java/com/hank/musicfree/plugin/di/PluginMetaModule.kt`：

```kotlin
package com.hank.musicfree.plugin.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluginMetaDataStore

private val Context.pluginMetaDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "plugin_meta")

@Module
@InstallIn(SingletonComponent::class)
object PluginMetaModule {

    @Provides
    @Singleton
    @PluginMetaDataStore
    fun providePluginMetaDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.pluginMetaDataStore
}
```

注意：使用 `@PluginMetaDataStore` 限定符区分 `DataModule` 提供的 `app_preferences` DataStore。

- [ ] **Step 3: 创建 PluginMetaStore**

创建 `plugin/src/main/java/com/hank/musicfree/plugin/meta/PluginMetaStore.kt`：

```kotlin
package com.hank.musicfree.plugin.meta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.hank.musicfree.plugin.di.PluginMetaDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@kotlinx.serialization.Serializable
data class SubscriptionItem(val name: String, val url: String)

@Singleton
class PluginMetaStore @Inject constructor(
    @PluginMetaDataStore private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── 启用/禁用 ──

    val disabledPlugins: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_DISABLED_PLUGINS] ?: emptySet()
    }

    suspend fun setPluginEnabled(platform: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_PLUGINS] ?: emptySet()
            prefs[KEY_DISABLED_PLUGINS] = if (enabled) {
                current - platform
            } else {
                current + platform
            }
        }
    }

    fun isPluginEnabled(platform: String): Flow<Boolean> = dataStore.data.map { prefs ->
        platform !in (prefs[KEY_DISABLED_PLUGINS] ?: emptySet())
    }

    // ── 排序 ──

    val pluginOrder: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_PLUGIN_ORDER]?.let { jsonStr ->
            runCatching { json.decodeFromString<List<String>>(jsonStr) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun setPluginOrder(order: List<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_PLUGIN_ORDER] = json.encodeToString(order)
        }
    }

    // ── 用户变量 ──

    fun getUserVariables(platform: String): Flow<Map<String, String>> = dataStore.data.map { prefs ->
        val key = stringPreferencesKey("user_variables_$platform")
        prefs[key]?.let { jsonStr ->
            runCatching { json.decodeFromString<Map<String, String>>(jsonStr) }.getOrDefault(emptyMap())
        } ?: emptyMap()
    }

    suspend fun setUserVariables(platform: String, variables: Map<String, String>) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("user_variables_$platform")
            prefs[key] = json.encodeToString(variables)
        }
    }

    // ── 订阅源 ──

    val subscriptions: Flow<List<SubscriptionItem>> = dataStore.data.map { prefs ->
        prefs[KEY_SUBSCRIPTIONS]?.let { jsonStr ->
            runCatching { json.decodeFromString<List<SubscriptionItem>>(jsonStr) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun addSubscription(name: String, url: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SUBSCRIPTIONS]?.let { jsonStr ->
                runCatching { json.decodeFromString<List<SubscriptionItem>>(jsonStr) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(current + SubscriptionItem(name, url))
        }
    }

    suspend fun updateSubscription(index: Int, name: String, url: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SUBSCRIPTIONS]?.let { jsonStr ->
                runCatching { json.decodeFromString<List<SubscriptionItem>>(jsonStr) }.getOrDefault(emptyList())
            } ?: emptyList()
            if (index in current.indices) {
                val updated = current.toMutableList()
                updated[index] = SubscriptionItem(name, url)
                prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(updated)
            }
        }
    }

    suspend fun removeSubscription(index: Int) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SUBSCRIPTIONS]?.let { jsonStr ->
                runCatching { json.decodeFromString<List<SubscriptionItem>>(jsonStr) }.getOrDefault(emptyList())
            } ?: emptyList()
            if (index in current.indices) {
                val updated = current.toMutableList()
                updated.removeAt(index)
                prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(updated)
            }
        }
    }

    private companion object {
        val KEY_DISABLED_PLUGINS = stringSetPreferencesKey("disabled_plugins")
        val KEY_PLUGIN_ORDER = stringPreferencesKey("plugin_order")
        val KEY_SUBSCRIPTIONS = stringPreferencesKey("subscriptions")
    }
}
```

- [ ] **Step 4: 编写 PluginMetaStore 测试**

创建 `plugin/src/test/java/com/hank/musicfree/plugin/meta/PluginMetaStoreTest.kt`：

```kotlin
package com.hank.musicfree.plugin.meta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PluginMetaStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PluginMetaStore

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmpFolder.newFile("test_plugin_meta.preferences_pb")
        }
        store = PluginMetaStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ── 启用/禁用 ──

    @Test
    fun `new plugin is enabled by default`() = runBlocking {
        assertTrue(store.isPluginEnabled("netease").first())
        assertTrue(store.disabledPlugins.first().isEmpty())
    }

    @Test
    fun `disable and re-enable plugin`() = runBlocking {
        store.setPluginEnabled("netease", false)
        assertFalse(store.isPluginEnabled("netease").first())
        assertTrue(store.disabledPlugins.first().contains("netease"))

        store.setPluginEnabled("netease", true)
        assertTrue(store.isPluginEnabled("netease").first())
        assertFalse(store.disabledPlugins.first().contains("netease"))
    }

    @Test
    fun `disable multiple plugins independently`() = runBlocking {
        store.setPluginEnabled("netease", false)
        store.setPluginEnabled("qq", false)
        assertEquals(setOf("netease", "qq"), store.disabledPlugins.first())

        store.setPluginEnabled("netease", true)
        assertEquals(setOf("qq"), store.disabledPlugins.first())
    }

    // ── 排序 ──

    @Test
    fun `plugin order defaults to empty`() = runBlocking {
        assertTrue(store.pluginOrder.first().isEmpty())
    }

    @Test
    fun `set and get plugin order`() = runBlocking {
        val order = listOf("qq", "netease", "kugou")
        store.setPluginOrder(order)
        assertEquals(order, store.pluginOrder.first())
    }

    // ── 用户变量 ──

    @Test
    fun `user variables default to empty`() = runBlocking {
        assertTrue(store.getUserVariables("netease").first().isEmpty())
    }

    @Test
    fun `set and get user variables`() = runBlocking {
        val vars = mapOf("cookie" to "abc123", "token" to "xyz")
        store.setUserVariables("netease", vars)
        assertEquals(vars, store.getUserVariables("netease").first())
    }

    @Test
    fun `user variables are per-plugin`() = runBlocking {
        store.setUserVariables("netease", mapOf("a" to "1"))
        store.setUserVariables("qq", mapOf("b" to "2"))
        assertEquals(mapOf("a" to "1"), store.getUserVariables("netease").first())
        assertEquals(mapOf("b" to "2"), store.getUserVariables("qq").first())
    }

    // ── 订阅源 ──

    @Test
    fun `subscriptions default to empty`() = runBlocking {
        assertTrue(store.subscriptions.first().isEmpty())
    }

    @Test
    fun `add subscription`() = runBlocking {
        store.addSubscription("默认源", "https://example.com/plugins.json")
        val subs = store.subscriptions.first()
        assertEquals(1, subs.size)
        assertEquals("默认源", subs[0].name)
        assertEquals("https://example.com/plugins.json", subs[0].url)
    }

    @Test
    fun `update subscription`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.updateSubscription(0, "源B", "https://b.com/p.json")
        val subs = store.subscriptions.first()
        assertEquals("源B", subs[0].name)
        assertEquals("https://b.com/p.json", subs[0].url)
    }

    @Test
    fun `remove subscription`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.addSubscription("源B", "https://b.com/p.json")
        store.removeSubscription(0)
        val subs = store.subscriptions.first()
        assertEquals(1, subs.size)
        assertEquals("源B", subs[0].name)
    }

    @Test
    fun `update out of range index is no-op`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.updateSubscription(5, "源X", "https://x.com/p.json")
        assertEquals(1, store.subscriptions.first().size)
        assertEquals("源A", store.subscriptions.first()[0].name)
    }

    @Test
    fun `remove out of range index is no-op`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.removeSubscription(5)
        assertEquals(1, store.subscriptions.first().size)
    }
}
```

- [ ] **Step 5: 运行测试验证**

```bash
./gradlew :plugin:testDebugUnitTest --tests "*.PluginMetaStoreTest" -q
```

Expected: 全部 PASS（11 个测试）

- [ ] **Step 6: 提交**

```bash
git add plugin/build.gradle.kts \
  plugin/src/main/java/com/hank/musicfree/plugin/meta/PluginMetaStore.kt \
  plugin/src/main/java/com/hank/musicfree/plugin/di/PluginMetaModule.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/meta/PluginMetaStoreTest.kt
git commit -m "feat(plugin): add PluginMetaStore for plugin metadata persistence"
```

---

## Task 2: PluginInfo 字段补全 + extractPluginInfo 改造

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginInfo.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt` (extractPluginInfo 方法，约第 758-796 行)

### 背景

当前 `PluginInfo` 只有 6 个字段。RN 原版 `__plugin` 对象还包含 appVersion、primaryKey、defaultSearchType、cacheControl、hints。这些字段在 JS 插件中是可选的。

`extractPluginInfo()` 位于 `PluginManager.kt` 第 758 行，通过 `engine.evaluate("__plugin.$name")` 逐个提取属性。需要在这里补提取新字段的逻辑。

- [ ] **Step 1: 修改 PluginInfo 数据类**

修改 `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginInfo.kt`：

```kotlin
package com.hank.musicfree.plugin.api

data class PluginInfo(
    val platform: String,
    val version: String?,
    val author: String?,
    val description: String?,
    val srcUrl: String?,
    val supportedSearchType: List<String>,
    val appVersion: String? = null,
    val primaryKey: String? = null,
    val defaultSearchType: String? = null,
    val cacheControl: String? = null,
    val hints: Map<String, List<String>>? = null,
)
```

所有新字段使用默认值 `null`，保证向后兼容——现有代码中构造 PluginInfo 的地方不需要改。

- [ ] **Step 2: 修改 extractPluginInfo 提取新字段**

在 `PluginManager.kt` 中找到 `extractPluginInfo` 方法（约第 758 行），修改 return 语句。当前代码：

```kotlin
return PluginInfo(
    platform = platform,
    version = prop("version"),
    author = prop("author"),
    description = prop("description"),
    srcUrl = prop("srcUrl"),
    supportedSearchType = supportedSearchType,
)
```

改为：

```kotlin
// Parse hints: { [methodName]: string[] }
val hints: Map<String, List<String>>? = try {
    val hintsJson = engine.evaluate("JSON.stringify(__plugin.hints)")?.toString()
    if (hintsJson != null && hintsJson.startsWith("{")) {
        val hintsMap = mutableMapOf<String, List<String>>()
        // Parse the JSON manually to avoid adding Gson/Moshi dependency
        val parsed = engine.evaluate("""
            (function() {
                var h = __plugin.hints;
                if (!h || typeof h !== 'object') return null;
                var result = {};
                for (var k in h) {
                    if (Array.isArray(h[k])) result[k] = h[k];
                }
                return JSON.stringify(result);
            })()
        """.trimIndent())?.toString()
        if (parsed != null && parsed.startsWith("{")) {
            // Simple JSON parsing using the engine
            val obj = engine.evaluate("JSON.parse('${parsed.replace("'", "\\'")}')")
            if (obj is com.niclas.quickjs.JSObject) {
                val keys = engine.evaluate("Object.keys(JSON.parse('${parsed.replace("'", "\\'")}'))")
                // Fallback: store raw JSON, parse later if needed
            }
        }
        hintsMap.ifEmpty { null }
    } else null
} catch (_: Exception) { null }

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
    hints = hints,
)
```

**简化方案**：hints 解析比较复杂（嵌套 JSON），考虑到大多数插件不使用 hints，先用简单的 JSON 字符串存储方式：

```kotlin
// 在 extractPluginInfo 中，hints 字段的实际解析方式：
val hintsRaw = prop("hints") // 如果 hints 不是简单字符串，这会返回 "[object Object]"

// 更可靠的方式：用 JSON.stringify 提取
val hintsJson = try {
    val raw = engine.evaluate("JSON.stringify(__plugin.hints)")?.toString()
    if (raw != null && raw != "undefined" && raw != "null" && raw.startsWith("{")) {
        kotlinx.serialization.json.Json.decodeFromString<Map<String, List<String>>>(raw)
    } else null
} catch (_: Exception) { null }

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
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew :plugin:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 运行现有测试确认无回归**

```bash
./gradlew :plugin:testDebugUnitTest -q
```

Expected: 全部 PASS

- [ ] **Step 5: 提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/api/PluginInfo.kt \
  plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt
git commit -m "feat(plugin): add appVersion/primaryKey/defaultSearchType/cacheControl/hints to PluginInfo"
```

---

## Task 3: env 注入补全

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt` (loadPluginFromFile 方法，约第 698-753 行)

### 背景

当前 env 注入在 `loadPluginFromFile` 方法中（第 718-724 行），CommonJS 包装器：

```kotlin
val wrappedCode = """
    var module = {exports: {}};
    var exports = module.exports;
    (function(require, module, exports, console, env) {
        $jsCode
    })(globalThis.__require, module, exports, console, {os: 'android'});
    globalThis.__plugin = module.exports;
""".trimIndent()
```

需要将 `{os: 'android'}` 扩展为完整的 env 对象，并注入 `getUserVariables` 回调。

由于 `getUserVariables` 需要读取 PluginMetaStore（异步 Flow），但 JS 调用是同步的，有两种策略：
1. 在加载时预读一次 userVariables，注入为静态值
2. 注入 Kotlin 回调函数

选择策略 1（预读静态值），与 RN 行为一致（RN 也是在插件加载时读取一次 pluginMeta）。`getUserVariables()` 返回的是加载时的快照。

- [ ] **Step 1: 修改 PluginManager 构造函数注入 PluginMetaStore**

在 `PluginManager.kt` 中，当前构造函数：

```kotlin
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
)
```

改为：

```kotlin
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val pluginMetaStore: PluginMetaStore,
)
```

添加 import：
```kotlin
import com.hank.musicfree.plugin.meta.PluginMetaStore
```

- [ ] **Step 2: 修改 loadPluginFromFile 中的 env 注入**

在 `loadPluginFromFile` 方法中（约第 698 行），在 `engine.runOnJsThread` 块内，修改 wrappedCode。需要在调用前获取 userVariables 快照（在挂起函数中可以用 `first()`）。

在 `loadPluginFromFile` 方法开头（读取 jsCode 之后），添加：

```kotlin
// 获取 app 版本号
val appVersion = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
} catch (_: Exception) { "unknown" }

// 获取系统语言
val lang = java.util.Locale.getDefault().toLanguageTag() // e.g., "zh-CN"
```

然后修改 wrappedCode 块。将：

```kotlin
})(globalThis.__require, module, exports, console, {os: 'android'});
```

改为：

```kotlin
})(globalThis.__require, module, exports, console, globalThis.__env);
```

在 evaluate wrappedCode 之前，先注入 env 对象：

```kotlin
// Inject env object before evaluating plugin code
engine.evaluate("""
    globalThis.__env = {
        os: 'android',
        appVersion: '${appVersion.replace("'", "\\'")}',
        lang: '${lang.replace("'", "\\'")}',
        getUserVariables: function() { return globalThis.__userVariables || {}; }
    };
""".trimIndent())
```

注意：`__userVariables` 的注入需要在插件加载后、首次 API 调用前设置。可以在 `loadPluginFromFile` 返回 `LoadedPlugin` 之前，将 userVariables 快照注入到引擎中。

在 `loadPluginFromFile` 方法中，`extractPluginInfo` 成功后（约第 743 行），在 return LoadedPlugin 之前：

```kotlin
}?.let { info ->
    // Inject userVariables snapshot for this plugin
    val userVars = kotlinx.coroutines.flow.firstOrNull()?.let {
        pluginMetaStore.getUserVariables(info.platform).first()
    } ?: emptyMap()
    if (userVars.isNotEmpty()) {
        engine.runOnJsThread {
            val jsonStr = kotlinx.serialization.json.Json.encodeToString(userVars)
            engine.evaluate("globalThis.__userVariables = JSON.parse('${jsonStr.replace("'", "\\'")}')")
        }
    }
    return LoadedPlugin(...)
}
```

更简洁的写法——直接在 `runOnJsThread` 块内注入（因为此时还在加载流程中）：

在 `engine.evaluate(wrappedCode)` 成功后、`extractPluginInfo` 之前，添加 userVariables 注入。但这时还不知道 platform，所以需要分两步：

1. 先 extractPluginInfo 拿到 platform
2. 再注入 userVariables

修改后的完整流程（替换 `loadPluginFromFile` 方法体中 `engine.runOnJsThread` 块的内容）：

```kotlin
engine.runOnJsThread {
    engine.create()
    val ctx = engine.context
        ?: throw IllegalStateException("Failed to create QuickJS context")

    // Register axios shim
    AxiosShim.register(ctx, engine)

    // Register CommonJS require shim with built-in modules from assets.
    RequireShim.register(appContext = context, context = ctx)

    // Inject env object
    engine.evaluate("""
        globalThis.__env = {
            os: 'android',
            appVersion: '${appVersion.replace("'", "\\'")}',
            lang: '${lang.replace("'", "\\'")}',
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
        engine.evaluate(wrappedCode)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to evaluate plugin code from ${file.name}", e)
        engine.destroy()
        return@runOnJsThread null
    }

    // Extract metadata from __plugin
    try {
        extractPluginInfo(engine)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to extract plugin info from ${file.name}", e)
        engine.destroy()
        null
    }
}?.let { info ->
    // Inject userVariables snapshot for this plugin
    val userVars = pluginMetaStore.getUserVariables(info.platform).first()
    if (userVars.isNotEmpty()) {
        engine.runOnJsThread {
            val jsonStr = Json.encodeToString(userVars)
            engine.evaluate(
                "globalThis.__userVariables = JSON.parse(${
                    jsonStr.replace("\\", "\\\\").replace("'", "\\'").let { "'$it'" }
                })"
            )
        }
    }
    return LoadedPlugin(
        info = info,
        engine = engine,
        filePath = file.absolutePath,
        installSource = installSource,
    )
}
```

需要添加的 import：
```kotlin
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew :plugin:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 运行现有测试**

```bash
./gradlew :plugin:testDebugUnitTest -q
```

Expected: 全部 PASS（PluginMetaStore 测试 + 现有测试）

- [ ] **Step 5: 提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt
git commit -m "feat(plugin): inject full env object (appVersion, lang, getUserVariables) into JS plugins"
```

---

## Task 4: PluginManager 整合 PluginMetaStore — 排序/过滤 API

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`

### 背景

PluginManager 已有 `plugins: StateFlow<List<LoadedPlugin>>`。需要新增方法将 plugins 与 PluginMetaStore 的 disabled/order 数据组合，产出排序后的已启用插件列表。

- [ ] **Step 1: 添加排序/过滤方法**

在 `PluginManager.kt` 类体中（建议在 `plugins` StateFlow 定义之后），添加：

```kotlin
/**
 * Sorted list of enabled plugins. Combines loaded plugins with
 * disabled set and order from [PluginMetaStore].
 * Plugins not in the order list are appended at the end.
 */
fun getSortedEnabledPlugins(): Flow<List<LoadedPlugin>> =
    combine(
        plugins,
        pluginMetaStore.disabledPlugins,
        pluginMetaStore.pluginOrder,
    ) { allPlugins, disabled, order ->
        val enabled = allPlugins.filter { it.info.platform !in disabled }
        if (order.isEmpty()) return@combine enabled
        val orderMap = order.withIndex().associate { (i, p) -> p to i }
        enabled.sortedBy { orderMap[it.info.platform] ?: Int.MAX_VALUE }
    }

/**
 * Enabled plugins that support the `search` method, sorted by user-defined order.
 */
fun getSearchablePlugins(): Flow<List<LoadedPlugin>> =
    getSortedEnabledPlugins().map { plugins ->
        plugins.filter { "music" in it.info.supportedSearchType || it.info.supportedSearchType.isEmpty() }
    }

// Convenience delegates to PluginMetaStore
suspend fun setPluginEnabled(platform: String, enabled: Boolean) {
    pluginMetaStore.setPluginEnabled(platform, enabled)
}

suspend fun setPluginOrder(order: List<String>) {
    pluginMetaStore.setPluginOrder(order)
}

suspend fun uninstallAllPlugins() {
    val platforms = _plugins.value.map { it.info.platform }
    for (platform in platforms) {
        uninstall(platform)
    }
}
```

需要添加 import：
```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
```

注意：`combine` 和 `map` 可能已经 import 了部分，检查现有 import 列表避免重复。

- [ ] **Step 2: 编译验证**

```bash
./gradlew :plugin:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行全部测试**

```bash
./gradlew :plugin:testDebugUnitTest -q
```

Expected: 全部 PASS

- [ ] **Step 4: 提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt
git commit -m "feat(plugin): add getSortedEnabledPlugins/getSearchablePlugins/setPluginEnabled/setPluginOrder"
```

---

## Task 5: 路由定义 + reorderable 依赖 + 页面切换动画

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`

### 背景

需要添加 3 个新路由，引入 `sh.calvin.reorderable` 拖拽排序库，并在 NavHost 中配置全局 slide 动画（100ms）对齐 RN 的 `slide_from_right`。

Navigation Compose 2.9.0 支持在 `NavHost` 或每个 `composable` 上设置 `enterTransition`/`exitTransition`。

- [ ] **Step 1: 添加路由定义**

在 `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt` 文件末尾（`ArtistDetailRoute` 之后）追加：

```kotlin
@Serializable
data object PluginListRoute

@Serializable
data object PluginSortRoute

@Serializable
data object PluginSubscriptionRoute
```

- [ ] **Step 2: 添加 reorderable 库到版本目录**

在 `gradle/libs.versions.toml` 中：

`[versions]` 段添加：
```toml
reorderable = "2.4.3"
```

`[libraries]` 段添加：
```toml
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

- [ ] **Step 3: 添加 reorderable 依赖到 settings 模块**

在 `feature/settings/build.gradle.kts` 的 `dependencies` 块中添加：

```kotlin
implementation(libs.reorderable)
```

- [ ] **Step 4: 配置全局页面切换动画**

修改 `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`。

添加 import：
```kotlin
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import com.hank.musicfree.core.navigation.PluginListRoute
import com.hank.musicfree.core.navigation.PluginSortRoute
import com.hank.musicfree.core.navigation.PluginSubscriptionRoute
```

修改 NavHost 配置，从：

```kotlin
NavHost(
    navController = navController,
    startDestination = HomeRoute,
    modifier = modifier,
)
```

改为：

```kotlin
NavHost(
    navController = navController,
    startDestination = HomeRoute,
    modifier = modifier,
    enterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(100),
        )
    },
    exitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(100),
        )
    },
    popEnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(100),
        )
    },
    popExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(100),
        )
    },
)
```

这配置了全局动画：进入时从右滑入，返回时从左滑出，时长 100ms，与 RN `slide_from_right` + `animationDuration: 100` 一致。

- [ ] **Step 5: 编译验证**

```bash
./gradlew :app:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt \
  gradle/libs.versions.toml \
  feature/settings/build.gradle.kts \
  app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt
git commit -m "feat: add plugin management routes, global slide animation, reorderable dependency"
```

---

## Task 6: PluginListScreen + PluginListViewModel

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/navigation/PluginListNavigation.kt`
- Create: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModelTest.kt`

### 背景

这是插件管理的主页面。从 SettingsScreen 导航进入，展示已安装插件列表，每个插件有启用/禁用开关、更新、分享、卸载操作。顶部菜单提供：订阅设置（导航）、排序（导航）、卸载全部。FAB 提供四种安装方式。

### 6a: PluginListViewModel

- [ ] **Step 1: 创建 PluginListViewModel**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModel.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PluginUiItem(
    val info: PluginInfo,
    val enabled: Boolean,
)

sealed interface InstallState {
    data object Idle : InstallState
    data object Loading : InstallState
    data class Success(val message: String) : InstallState
    data class Error(val message: String) : InstallState
}

@HiltViewModel
class PluginListViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val metaStore: PluginMetaStore = pluginManager.pluginMetaStore

    val pluginItems: Flow<List<PluginUiItem>> = combine(
        pluginManager.plugins.map { list -> list.map { it.info } },
        metaStore.disabledPlugins,
        metaStore.pluginOrder,
    ) { allInfos, disabled, order ->
        val items = allInfos.map { info ->
            PluginUiItem(info = info, enabled = info.platform !in disabled)
        }
        if (order.isEmpty()) return@combine items
        val orderMap = order.withIndex().associate { (i, p) -> p to i }
        items.sortedBy { orderMap[it.info.platform] ?: Int.MAX_VALUE }
    }

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginManager.loadAllPlugins()
        }
    }

    fun togglePluginEnabled(platform: String, enabled: Boolean) {
        viewModelScope.launch {
            pluginManager.setPluginEnabled(platform, enabled)
        }
    }

    fun installFromUrl(url: String) {
        performOperation {
            val trimmed = url.trim()
            if (trimmed.isBlank()) {
                return@performOperation InstallState.Error("URL 不能为空")
            }
            val plugin = pluginManager.installFromUrl(trimmed, trimmed.substringAfterLast('/'))
            if (plugin != null) {
                InstallState.Success("安装成功：${plugin.info.platform}")
            } else {
                InstallState.Error("安装失败")
            }
        }
    }

    fun installFromFile(path: String) {
        performOperation {
            val file = java.io.File(path.trim())
            if (!file.exists()) {
                return@performOperation InstallState.Error("文件不存在：$path")
            }
            val plugin = pluginManager.installFromFile(file)
            if (plugin != null) {
                InstallState.Success("安装成功：${plugin.info.platform}")
            } else {
                InstallState.Error("安装失败")
            }
        }
    }

    fun updatePlugin(platform: String) {
        performOperation {
            val result = pluginManager.updatePlugin(platform)
            if (result.failures.isEmpty()) {
                InstallState.Success("更新成功")
            } else {
                InstallState.Error("更新失败：${result.failures.first().message}")
            }
        }
    }

    fun updateAllPlugins() {
        performOperation {
            val result = pluginManager.updateAllPlugins()
            if (result.failures.isEmpty()) {
                InstallState.Success("全部更新成功")
            } else {
                val msg = "成功 ${result.successes.size} 个，失败 ${result.failures.size} 个"
                if (result.successes.isNotEmpty()) InstallState.Success(msg) else InstallState.Error(msg)
            }
        }
    }

    fun updateSubscriptions() {
        performOperation {
            val subs = metaStore.subscriptions.first()
            if (subs.isEmpty()) {
                return@performOperation InstallState.Error("暂无订阅源")
            }
            var totalSuccess = 0
            var totalFail = 0
            var totalEntries = 0
            for (sub in subs) {
                val result = pluginManager.installFromSubscriptionUrl(sub.url)
                totalSuccess += result.successfulInstalls
                totalFail += result.failedInstalls
                totalEntries += result.totalEntries
            }
            if (totalFail == 0) {
                InstallState.Success("订阅更新完成：共 $totalEntries 项，全部成功")
            } else {
                val msg = "订阅更新：共 $totalEntries 项，成功 $totalSuccess，失败 $totalFail"
                if (totalSuccess > 0) InstallState.Success(msg) else InstallState.Error(msg)
            }
        }
    }

    fun uninstallPlugin(platform: String) {
        viewModelScope.launch {
            pluginManager.uninstall(platform)
        }
    }

    fun uninstallAllPlugins() {
        performOperation {
            pluginManager.uninstallAllPlugins()
            InstallState.Success("已卸载全部插件")
        }
    }

    fun resetInstallState() {
        _installState.value = InstallState.Idle
    }

    private fun performOperation(operation: suspend () -> InstallState) {
        if (_installState.value is InstallState.Loading) return
        _installState.value = InstallState.Loading
        viewModelScope.launch {
            try {
                _installState.value = operation()
            } catch (e: Exception) {
                _installState.value = InstallState.Error(e.message ?: "未知错误")
            }
        }
    }
}
```

- [ ] **Step 2: 创建 PluginListScreen**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.rpx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginListScreen(
    onBack: () -> Unit,
    onNavigateToPluginSort: () -> Unit,
    onNavigateToPluginSubscription: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginListViewModel = hiltViewModel(),
) {
    val pluginItems by viewModel.pluginItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showInstallUrlDialog by remember { mutableStateOf(false) }
    var showInstallLocalDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showUninstallAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("订阅设置") },
                                onClick = { showMenu = false; onNavigateToPluginSubscription() },
                            )
                            DropdownMenuItem(
                                text = { Text("排序") },
                                onClick = { showMenu = false; onNavigateToPluginSort() },
                            )
                            DropdownMenuItem(
                                text = { Text("卸载全部") },
                                onClick = { showMenu = false; showUninstallAllConfirm = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "安装插件")
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("从本地安装") },
                        onClick = { showFabMenu = false; showInstallLocalDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("从网络安装") },
                        onClick = { showFabMenu = false; showInstallUrlDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("更新全部插件") },
                        onClick = { showFabMenu = false; viewModel.updateAllPlugins() },
                    )
                    DropdownMenuItem(
                        text = { Text("更新订阅") },
                        onClick = { showFabMenu = false; viewModel.updateSubscriptions() },
                    )
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Install state banner
            when (val state = installState) {
                is InstallState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is InstallState.Success -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(8)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is InstallState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(8)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                InstallState.Idle -> {}
            }

            if (pluginItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无已安装插件", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
                    verticalArrangement = Arrangement.spacedBy(rpx(16)),
                ) {
                    items(pluginItems, key = { it.info.platform }) { item ->
                        PluginCard(
                            item = item,
                            onToggleEnabled = { enabled ->
                                viewModel.togglePluginEnabled(item.info.platform, enabled)
                            },
                            onUpdate = { viewModel.updatePlugin(item.info.platform) },
                            onShare = {
                                item.info.srcUrl?.let { url ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("插件URL", url))
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onUninstall = { viewModel.uninstallPlugin(item.info.platform) },
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showInstallUrlDialog) {
        InstallUrlDialog(
            onDismiss = { showInstallUrlDialog = false },
            onConfirm = { url ->
                showInstallUrlDialog = false
                viewModel.installFromUrl(url)
            },
        )
    }

    if (showInstallLocalDialog) {
        InstallLocalDialog(
            onDismiss = { showInstallLocalDialog = false },
            onConfirm = { path ->
                showInstallLocalDialog = false
                viewModel.installFromFile(path)
            },
            onOpenFileSelector = {
                showInstallLocalDialog = false
                onNavigateToFileSelector()
            },
        )
    }

    if (showUninstallAllConfirm) {
        AlertDialog(
            onDismissRequest = { showUninstallAllConfirm = false },
            title = { Text("确认") },
            text = { Text("确定要卸载所有插件吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showUninstallAllConfirm = false
                    viewModel.uninstallAllPlugins()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallAllConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PluginCard(
    item: PluginUiItem,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onShare: () -> Unit,
    onUninstall: () -> Unit,
) {
    val alpha = if (item.enabled) 1f else 0.5f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(Modifier.graphicsLayer { this.alpha = alpha }),
    ) {
        Column(modifier = Modifier.padding(rpx(16))) {
            // Header: name + switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.info.platform,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            // Info
            Text(
                text = buildString {
                    item.info.version?.let { append("v$it") }
                    item.info.author?.let {
                        if (isNotEmpty()) append(" · ")
                        append("作者: $it")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = rpx(4)),
            )

            // Action buttons
            Row(
                modifier = Modifier.padding(top = rpx(12)),
                horizontalArrangement = Arrangement.spacedBy(rpx(12)),
            ) {
                if (item.info.srcUrl != null) {
                    AssistChip(onClick = onUpdate, label = { Text("更新") })
                    AssistChip(onClick = onShare, label = { Text("分享") })
                }
                AssistChip(
                    onClick = onUninstall,
                    label = { Text("卸载") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
    }
}

@Composable
private fun InstallUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从网络安装") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("插件URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text("安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InstallLocalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onOpenFileSelector: () -> Unit,
) {
    var path by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从本地安装") },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("插件文件路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenFileSelector) {
                    Text("打开文件选择器")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(path) }, enabled = path.isNotBlank()) {
                Text("安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
```

**注意**：`graphicsLayer { this.alpha = alpha }` 需要 import `androidx.compose.ui.graphics.graphicsLayer`。如果该 API 不可用，可以用 `Modifier.alpha(alpha)` 替代（需要 `import androidx.compose.ui.draw.alpha`）。

- [ ] **Step 3: 创建导航注册**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/navigation/PluginListNavigation.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginlist.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PluginListRoute
import com.hank.musicfree.feature.settings.pluginlist.PluginListScreen

fun NavGraphBuilder.pluginListScreen(
    onBack: () -> Unit,
    onNavigateToPluginSort: () -> Unit,
    onNavigateToPluginSubscription: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
) {
    composable<PluginListRoute> {
        PluginListScreen(
            onBack = onBack,
            onNavigateToPluginSort = onNavigateToPluginSort,
            onNavigateToPluginSubscription = onNavigateToPluginSubscription,
            onNavigateToFileSelector = onNavigateToFileSelector,
        )
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew :feature:settings:assembleDebug -q
```

Expected: BUILD SUCCESSFUL（可能需要修复 import 和 API 不匹配问题）

- [ ] **Step 5: 提交**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/
git commit -m "feat(settings): add PluginListScreen with enable/disable, install, update, uninstall"
```

---

## Task 7: PluginSortScreen + PluginSortViewModel

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/PluginSortViewModel.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/PluginSortScreen.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/navigation/PluginSortNavigation.kt`

### 背景

拖拽排序页面。使用 `sh.calvin.reorderable` 库提供 LazyColumn 拖拽。加载时读取当前排序，用户拖拽后点击"完成"保存。

- [ ] **Step 1: 创建 PluginSortViewModel**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/PluginSortViewModel.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginsort

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginSortViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val _sortedPlatforms = MutableStateFlow<List<String>>(emptyList())
    val sortedPlatforms: StateFlow<List<String>> = _sortedPlatforms.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine loaded plugins with saved order to produce initial sorted list
            val allPlatforms = pluginManager.plugins.first().map { it.info.platform }
            val savedOrder = pluginManager.pluginMetaStore.pluginOrder.first()

            _sortedPlatforms.value = if (savedOrder.isEmpty()) {
                allPlatforms
            } else {
                val orderMap = savedOrder.withIndex().associate { (i, p) -> p to i }
                allPlatforms.sortedBy { orderMap[it] ?: Int.MAX_VALUE }
            }
        }
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val current = _sortedPlatforms.value.toMutableList()
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _sortedPlatforms.value = current
    }

    fun saveOrder() {
        viewModelScope.launch {
            pluginManager.setPluginOrder(_sortedPlatforms.value)
        }
    }
}
```

- [ ] **Step 2: 创建 PluginSortScreen**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/PluginSortScreen.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginsort

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.rpx
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSortScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSortViewModel = hiltViewModel(),
) {
    val platforms by viewModel.sortedPlatforms.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onReorder(from.index, to.index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件排序") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.saveOrder()
                        onBack()
                    }) {
                        Text("完成")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
            verticalArrangement = Arrangement.spacedBy(rpx(8)),
            modifier = Modifier.padding(padding),
        ) {
            items(platforms, key = { it }) { platform ->
                ReorderableItem(reorderableState, key = platform) { isDragging ->
                    val elevation = if (isDragging) 4.dp else 0.dp
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = rpx(16), vertical = rpx(14)),
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "拖拽排序",
                                modifier = Modifier.draggableHandle(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(rpx(12)))
                            Text(
                                text = platform,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

**注意**：`sh.calvin.reorderable` 库的 API 可能与上述代码略有差异，取决于具体版本。需在编译时验证并调整。关键 API：`rememberReorderableLazyListState`、`ReorderableItem`、`Modifier.draggableHandle()`。

- [ ] **Step 3: 创建导航注册**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/navigation/PluginSortNavigation.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginsort.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PluginSortRoute
import com.hank.musicfree.feature.settings.pluginsort.PluginSortScreen

fun NavGraphBuilder.pluginSortScreen(
    onBack: () -> Unit,
) {
    composable<PluginSortRoute> {
        PluginSortScreen(onBack = onBack)
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew :feature:settings:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsort/
git commit -m "feat(settings): add PluginSortScreen with drag-to-reorder"
```

---

## Task 8: PluginSubscriptionScreen + PluginSubscriptionViewModel

**Files:**
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/PluginSubscriptionViewModel.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/PluginSubscriptionScreen.kt`
- Create: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/navigation/PluginSubscriptionNavigation.kt`

### 背景

订阅管理页面。展示已保存的订阅源列表，FAB 添加新订阅，点击项编辑/删除，右侧图标复制 URL。

- [ ] **Step 1: 创建 PluginSubscriptionViewModel**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/PluginSubscriptionViewModel.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginsub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.meta.SubscriptionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginSubscriptionViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val metaStore: PluginMetaStore = pluginManager.pluginMetaStore

    val subscriptions: Flow<List<SubscriptionItem>> = metaStore.subscriptions

    fun addSubscription(name: String, url: String) {
        viewModelScope.launch {
            metaStore.addSubscription(name, url)
        }
    }

    fun updateSubscription(index: Int, name: String, url: String) {
        viewModelScope.launch {
            metaStore.updateSubscription(index, name, url)
        }
    }

    fun removeSubscription(index: Int) {
        viewModelScope.launch {
            metaStore.removeSubscription(index)
        }
    }
}
```

- [ ] **Step 2: 创建 PluginSubscriptionScreen**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/PluginSubscriptionScreen.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginsub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.rpx
import com.hank.musicfree.plugin.meta.SubscriptionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSubscriptionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSubscriptionViewModel = hiltViewModel(),
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    // Dialog state
    var showDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var dialogName by remember { mutableStateOf("") }
    var dialogUrl by remember { mutableStateOf("") }

    fun openAddDialog() {
        editingIndex = -1
        dialogName = ""
        dialogUrl = ""
        showDialog = true
    }

    fun openEditDialog(index: Int, item: SubscriptionItem) {
        editingIndex = index
        dialogName = item.name
        dialogUrl = item.url
        showDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订阅设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "添加订阅")
            }
        },
        modifier = modifier,
    ) { padding ->
        if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无订阅源\n点击右下角添加",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
                verticalArrangement = Arrangement.spacedBy(rpx(8)),
                modifier = Modifier.padding(padding),
            ) {
                itemsIndexed(subscriptions) { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openEditDialog(index, item) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(rpx(16)),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = rpx(4)),
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("订阅URL", item.url))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = "复制URL",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showDialog) {
        val isEditing = editingIndex >= 0
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (isEditing) "编辑订阅" else "添加订阅") },
            text = {
                Column {
                    OutlinedTextField(
                        value = dialogName,
                        onValueChange = { dialogName = it },
                        label = { Text("订阅名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(rpx(12)))
                    OutlinedTextField(
                        value = dialogUrl,
                        onValueChange = { dialogUrl = it },
                        label = { Text("订阅URL") },
                        placeholder = { Text("输入 .js 或 .json 地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isEditing) {
                            viewModel.updateSubscription(editingIndex, dialogName.trim(), dialogUrl.trim())
                        } else {
                            viewModel.addSubscription(dialogName.trim(), dialogUrl.trim())
                        }
                        showDialog = false
                    },
                    enabled = dialogName.isNotBlank() && dialogUrl.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                if (isEditing) {
                    TextButton(onClick = {
                        viewModel.removeSubscription(editingIndex)
                        showDialog = false
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            },
        )
    }
}
```

- [ ] **Step 3: 创建导航注册**

创建 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/navigation/PluginSubscriptionNavigation.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginsub.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.PluginSubscriptionRoute
import com.hank.musicfree.feature.settings.pluginsub.PluginSubscriptionScreen

fun NavGraphBuilder.pluginSubscriptionScreen(
    onBack: () -> Unit,
) {
    composable<PluginSubscriptionRoute> {
        PluginSubscriptionScreen(onBack = onBack)
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew :feature:settings:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginsub/
git commit -m "feat(settings): add PluginSubscriptionScreen with CRUD for subscription sources"
```

---

## Task 9: AppNavHost 注册新页面 + SettingsScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation/SettingsNavigation.kt`

### 背景

需要在 AppNavHost 中注册三个新页面，并将 SettingsScreen 中的插件列表/安装逻辑移除，改为一个"插件管理"导航入口。

- [ ] **Step 1: 修改 SettingsNavigation 添加新回调**

修改 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation/SettingsNavigation.kt`：

```kotlin
package com.hank.musicfree.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.feature.settings.SettingsScreen

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToPluginList: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBack = onBack,
            onNavigateToPermissions = onNavigateToPermissions,
            onNavigateToFileSelector = onNavigateToFileSelector,
            onNavigateToPluginList = onNavigateToPluginList,
            onNavigateToLocalFileSelector = onNavigateToLocalFileSelector,
        )
    }
}
```

- [ ] **Step 2: 精简 SettingsScreen**

修改 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`。

核心改动：
1. 添加 `onNavigateToPluginList: () -> Unit` 参数
2. 移除插件列表渲染（约第 206-231 行的 items 块）
3. 移除 PluginCard 组件（约第 349-405 行）
4. 移除 InstallPluginDialog（约第 472-523 行）
5. 移除 InstallLocalPluginDialog（约第 408-470 行）
6. 移除安装相关的对话框状态（showInstallDialog、showLocalInstallDialog）
7. 将"插件管理"区域简化为一个导航入口卡片（点击跳转到 PluginListRoute）
8. 移除"默认订阅导入"卡片（已由订阅管理页面替代）

简化后的"插件管理"区域代码（替换原来的整个插件相关 UI）：

```kotlin
item {
    SettingsEntryCard(
        title = "插件管理",
        onClick = onNavigateToPluginList,
    )
    Spacer(modifier = Modifier.height(rpx(16)))
}
```

保留其余设置项（主题、备份、关于、权限、存储目录）不变。

- [ ] **Step 3: 精简 SettingsViewModel**

修改 `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`。

移除：
- `plugins` StateFlow（第 44-46 行）
- `installState` StateFlow（第 48-49 行）
- `DEFAULT_SUBSCRIPTION_URL` 常量（第 40-42 行）
- `init` 块中的 `loadAllPlugins()`（第 55-59 行）
- `installFromUrl()`（第 61-80 行）
- `installFromFile()`（第 82-105 行）
- `installDefaultSubscription()`（第 107-121 行）
- `updatePlugin()`（第 123-134 行）
- `updateAllPlugins()`（第 136-157 行）
- `performInstallOperation()`（第 159-171 行）
- `uninstallPlugin()`（第 173-177 行）
- `resetInstallState()`（第 185-187 行）
- `InstallState` 密封接口定义（第 20-25 行）
- `PluginManager` 依赖注入

保留 `storageAccessState` 和 `AppPreferences` 相关功能。

- [ ] **Step 4: 在 AppNavHost 中注册新页面**

修改 `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`。

添加 import：
```kotlin
import com.hank.musicfree.feature.settings.pluginlist.navigation.pluginListScreen
import com.hank.musicfree.feature.settings.pluginsort.navigation.pluginSortScreen
import com.hank.musicfree.feature.settings.pluginsub.navigation.pluginSubscriptionScreen
```

修改 `settingsScreen` 调用，添加新回调：
```kotlin
settingsScreen(
    onBack = { navController.popBackStack() },
    onNavigateToPermissions = { navController.navigate(PermissionsRoute) },
    onNavigateToFileSelector = { navController.navigate(FileSelectorRoute) },
    onNavigateToPluginList = { navController.navigate(PluginListRoute) },
)
```

在 NavHost 块内添加三个新页面注册（在 `settingsScreen` 之后）：

```kotlin
pluginListScreen(
    onBack = { navController.popBackStack() },
    onNavigateToPluginSort = { navController.navigate(PluginSortRoute) },
    onNavigateToPluginSubscription = { navController.navigate(PluginSubscriptionRoute) },
    onNavigateToFileSelector = { navController.navigate(FileSelectorRoute) },
)
pluginSortScreen(
    onBack = { navController.popBackStack() },
)
pluginSubscriptionScreen(
    onBack = { navController.popBackStack() },
)
```

- [ ] **Step 5: 编译验证**

```bash
./gradlew :app:assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 运行全部测试**

```bash
./gradlew test -q
```

Expected: PASS（SettingsViewModelTest 可能需要更新以反映移除的方法，如果测试引用了被移除的方法则需要删除对应测试用例）

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/navigation/SettingsNavigation.kt
git commit -m "refactor(settings): migrate plugin management to dedicated pages, simplify SettingsScreen"
```

---

## Task 10: 更新现有测试 + 新增 ViewModel 测试

**Files:**
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt`
- Create: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModelTest.kt`
- Create: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginsub/PluginSubscriptionViewModelTest.kt`

### 背景

SettingsViewModelTest 中测试了被移除的插件操作方法（如 `installDefaultSubscription`），需要删除这些用例。同时为新 ViewModel 添加基本测试。

- [ ] **Step 1: 清理 SettingsViewModelTest**

检查 `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt` 中引用 `installFromUrl`、`installDefaultSubscription`、`updatePlugin`、`uninstallPlugin`、`InstallState` 的测试用例，将它们**删除**。保留与 `storageAccessState` 相关的测试。

如果清理后文件中没有剩余测试，可以保留文件骨架或删除整个文件。

- [ ] **Step 2: 创建 PluginListViewModelTest**

创建 `feature/settings/src/test/java/com/hank/musicfree/feature/settings/pluginlist/PluginListViewModelTest.kt`：

```kotlin
package com.hank.musicfree.feature.settings.pluginlist

import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import com.hank.musicfree.feature.settings.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PluginListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `togglePluginEnabled calls pluginManager`() = runTest {
        val metaStore = mock<PluginMetaStore> {
            on { disabledPlugins } doReturn MutableStateFlow(emptySet())
            on { pluginOrder } doReturn MutableStateFlow(emptyList())
        }
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList<LoadedPlugin>())
            on { pluginMetaStore } doReturn metaStore
        }
        val viewModel = PluginListViewModel(pluginManager)

        viewModel.togglePluginEnabled("netease", false)
        // Verify the call was delegated
        // Note: actual verification may need advanceUntilIdle() depending on dispatcher
    }

    @Test
    fun `install state starts as Idle`() {
        val metaStore = mock<PluginMetaStore> {
            on { disabledPlugins } doReturn MutableStateFlow(emptySet())
            on { pluginOrder } doReturn MutableStateFlow(emptyList())
        }
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList<LoadedPlugin>())
            on { pluginMetaStore } doReturn metaStore
        }
        val viewModel = PluginListViewModel(pluginManager)

        assertEquals(InstallState.Idle, viewModel.installState.value)
    }
}
```

- [ ] **Step 3: 运行全部测试**

```bash
./gradlew test -q
```

Expected: 全部 PASS

- [ ] **Step 4: 提交**

```bash
git add feature/settings/src/test/
git commit -m "test(settings): update tests for plugin management refactor"
```

---

## Task 11: 全量编译 + 运行态验证清单

**Files:** 无新增文件，仅验证。

- [ ] **Step 1: 全量编译**

```bash
./gradlew assembleDebug -q
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 全量测试**

```bash
./gradlew test -q
```

Expected: 全部 PASS

- [ ] **Step 3: Lint 检查**

```bash
./gradlew lint -q
```

Expected: 无新增 error

- [ ] **Step 4: 运行态验证清单（设备/模拟器）**

以下场景需要在设备或模拟器上手动验证：

| # | 场景 | 预期结果 |
|---|------|---------|
| 1 | 设置 → 点击"插件管理" | slide 动画进入 PluginListScreen |
| 2 | 插件列表为空时 | 显示"暂无已安装插件" |
| 3 | FAB → 从网络安装 → 输入有效 URL | 安装成功，列表刷新 |
| 4 | FAB → 从本地安装 | 文件选择器可用 |
| 5 | 插件卡片 Switch 开关 | 切换后卡片透明度变化 |
| 6 | 插件卡片 → 更新 | 更新成功提示 |
| 7 | 插件卡片 → 分享 | srcUrl 复制到剪贴板 |
| 8 | 插件卡片 → 卸载 | 插件从列表消失 |
| 9 | 菜单 → 排序 | slide 动画进入排序页面 |
| 10 | 排序页面拖拽 → 完成 | 返回后列表顺序更新 |
| 11 | 菜单 → 订阅设置 | slide 动画进入订阅页面 |
| 12 | 订阅页面 FAB → 添加 | Dialog 输入 name/url → 列表新增 |
| 13 | 订阅页面点击项 → 编辑 | Dialog 预填值，可修改或删除 |
| 14 | 返回按钮 | 每个页面返回时 slide 动画正确（从右向左退出） |
| 15 | FAB → 更新订阅 | 从所有订阅源批量安装/更新 |
| 16 | 菜单 → 卸载全部 | 确认对话框 → 全部卸载 |
| 17 | 禁用插件后搜索 | 该插件不出现在搜索结果中（验证 getSortedEnabledPlugins 生效） |

- [ ] **Step 5: 最终提交（如有修复）**

```bash
git add -A
git commit -m "fix: address issues found during runtime verification"
```

---

## 验收标准

1. **编译**：`./gradlew assembleDebug` 成功
2. **测试**：`./gradlew test` 全部 PASS（包括新增的 PluginMetaStoreTest、PluginListViewModelTest）
3. **运行态**：Task 11 的 17 个场景全部通过
4. **代码质量**：`./gradlew lint` 无新增 error
5. **动画**：所有页面切换使用 slide_from_right 100ms 动画
6. **数据持久化**：禁用/排序/订阅在 app 重启后保留
