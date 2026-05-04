# PR 2: `:plugin` 集成测试拆分 + MockWebServer + `-Pintegration` — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `PluginRuntimeIntegrationTest`（类级 `@Ignore`，覆盖 7 个用例）拆分为本地（3 个）与网络（4 个）两套，网络套通过 `Assume.assumeTrue` 在缺少 `-Pintegration` Gradle 属性时跳过；同时新增 1 个 `PluginManagerHttpLifecycleTest`（2 个用例）覆盖 `installFromUrl + updatePlugin` 编排路径。CI 默认通道执行 5 个用例（3 本地 + 2 MockWebServer），按需启用 `-Pintegration` 跑全 9 个。

**Architecture:** 拆分原文件为 3 个 androidTest 文件；引入 `mockwebserver` 依赖；在 `:plugin/build.gradle.kts` 通过 `testInstrumentationRunnerArguments` 把 Gradle property 转成 instrumentation arg。无生产代码触动。

**Tech Stack:** Kotlin、JUnit 4（`Assume`）、AndroidJUnit4、OkHttp `mockwebserver`、AndroidX Datastore（用于构造 `PluginMetaStore` 的 in-memory 实例，沿用原 IntegrationTest 模式）。

**Spec:** [`../specs/2026-05-04-test-suite-rehabilitation-design.md`](../specs/2026-05-04-test-suite-rehabilitation-design.md)（PR 2 = §5）

**Prerequisite:** PR 1 的 feature androidTest runner 基线应先合入主线。PR 2 自身 focused `:plugin:*` 验证不依赖 PR 1，但最终 `./gradlew connectedAndroidTest` 全仓库验证必须基于包含 PR 1 的主线重跑，否则仍可能被 feature 模块 runner crash 阻塞。

---

## 文件结构

| 路径 | 操作 | 责任 |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | 新增 `okhttp-mockwebserver` 库条目 |
| `plugin/build.gradle.kts` | Modify | 新增 `testInstrumentationRunnerArguments["pluginNetworkTests"]` 桥接；`androidTestImplementation(libs.okhttp.mockwebserver)` |
| `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt` | Delete | 拆分到下面 3 个文件 |
| `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeLocalIntegrationTest.kt` | Create | 3 个无网络依赖的 runtime shim 用例 + 共享 helpers |
| `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeNetworkIntegrationTest.kt` | Create | 4 个 kstore.vip 网络用例；类不带 `@Ignore`，`@Before` 做 `Assume` 门控 |
| `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerHttpLifecycleTest.kt` | Create | 2 个 MockWebServer 用例覆盖 `installFromUrl + updatePlugin` 编排 |

---

## Task 1：创建 PR 2 worktree

**Files:** 无

- [ ] **Step 1：从仓库根目录进入或创建 worktree + 分支**

```bash
git worktree list
```

如果列表里已经有 `.worktrees/test/plugin-it-split`，直接进入：

```bash
cd .worktrees/test/plugin-it-split
```

如果不存在，再创建：

```bash
git worktree add .worktrees/test/plugin-it-split -b test/plugin-it-split
cd .worktrees/test/plugin-it-split
```

- [ ] **Step 2：确认分支干净**

```bash
git status
git branch --show-current
```

预期：`git status` 显示 `nothing to commit, working tree clean`；当前分支是 `test/plugin-it-split`。

- [ ] **Step 3：跑一次 baseline `:plugin:testDebugUnitTest`**

```bash
./gradlew :plugin:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`，`:plugin` 单测全绿。

- [ ] **Step 4：（如有连接的设备）跑 baseline `:plugin:connectedAndroidTest`**

```bash
./gradlew :plugin:connectedAndroidTest
```

预期：`BUILD SUCCESSFUL`，`PluginRuntimeIntegrationTest`（7 个 @Test）整类 SKIPPED。如无设备：跳过此 step，下游 task 改在 CI 验收。

---

## Task 2：在 `gradle/libs.versions.toml` 新增 `okhttp-mockwebserver` 库

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1：在 `# OkHttp` 行下方追加**

定位到 `okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }`，在它**下面一行**插入：

```toml
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

复用现有 `okhttp = "5.3.2"` 版本号，避免版本不一致。

- [ ] **Step 2：commit**

```bash
git add gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
build(deps): add okhttp-mockwebserver library entry

Reuses the existing okhttp 5.3.2 version. Wired up in :plugin's
androidTestImplementation in a follow-up commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：在 `:plugin/build.gradle.kts` 接入 `-Pintegration` 桥接 + MockWebServer 依赖

**Files:**
- Modify: `plugin/build.gradle.kts`

- [ ] **Step 1：修改 `defaultConfig` 块**

将 `defaultConfig` 块（当前内容为 `minSdk = 29` 与 `testInstrumentationRunner = "..."`）替换为：

```kotlin
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["pluginNetworkTests"] =
            if (project.hasProperty("integration")) "true" else "false"
    }
```

- [ ] **Step 2：在 `dependencies` 块末尾追加 androidTest 依赖**

定位到现有的 `androidTestImplementation(libs.androidx.espresso.core)`，**在它下面追加 1 行**：

```kotlin
    androidTestImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3：sync + 编译保护**

```bash
./gradlew :plugin:assembleDebugAndroidTest
```

预期：`BUILD SUCCESSFUL`，无 unresolved reference。

- [ ] **Step 4：commit**

```bash
git add plugin/build.gradle.kts
git commit -m "$(cat <<'EOF'
build(plugin): wire -Pintegration to instrumentation arg + add MockWebServer

Bridges the Gradle property `-Pintegration` to the instrumentation
arg `pluginNetworkTests=true` so PluginRuntimeNetworkIntegrationTest
can gate itself via Assume.assumeTrue. Also adds the mockwebserver
androidTest dependency consumed by the new lifecycle test in a later
commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：新增 `PluginRuntimeLocalIntegrationTest.kt`（3 个本地用例）

**Files:**
- Create: `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeLocalIntegrationTest.kt`

- [ ] **Step 1：写入文件**

完整文件内容（包含 3 个本地用例 + 共享 helpers，**不带任何 `@Ignore`**）：

```kotlin
package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Local-only integration tests for PluginManager runtime shims.
 * No network access required — all plugins are loaded from temporary files.
 * Always runs in :plugin:connectedAndroidTest (CI default channel).
 */
@RunWith(AndroidJUnit4::class)
class PluginRuntimeLocalIntegrationTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(appContext.cacheDir, "plugin-runtime-local-it.preferences_pb") },
        )
        pluginManager = PluginManager(appContext, PluginMetaStore(dataStore))
        clearPluginStorage()
    }

    @Test
    fun localRuntimeShimPlugin_search_executesWithoutNotFunctionErrors() = runBlocking {
        val pluginFile = File.createTempFile("runtime-shim-it-", ".js", appContext.cacheDir)
        pluginFile.writeText(runtimeShimScript)

        val plugin = pluginManager.installFromFile(pluginFile)
        assertNotNull("Local runtime shim plugin should install", plugin)

        val result = plugin!!.search(query = "in the end", page = 1)
        assertTrue(
            "Search should return at least one item when runtime shims are valid",
            result.data.isNotEmpty(),
        )

        val title = result.data.first().title
        assertTrue("Title should contain decoded HTML marker", title.contains("&"))
        assertTrue("Title should contain dayjs formatted date", title.contains("2026-03-21"))

        val source = plugin.getMediaSource(result.data.first(), quality = "standard")
        assertNotNull("Media source should resolve when songmid is preserved", source)
        assertTrue(
            "Resolved source should include songmid from search payload",
            source!!.url.contains("song-mid-it-1"),
        )
    }

    @Test
    fun updatePlugin_withoutSource_returnsMissingSource_andKeepsPluginUsable() = runBlocking {
        val pluginFile = File.createTempFile("runtime-no-src-", ".js", appContext.cacheDir)
        pluginFile.writeText(runtimeShimScript)

        val plugin = pluginManager.installFromFile(pluginFile)
        assertNotNull("Runtime test plugin should install", plugin)

        val update = pluginManager.updatePlugin(plugin!!.info.platform)
        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)
        assertEquals(0, update.successCount)
        assertEquals(1, update.failureCount)
        assertEquals(
            PluginOperationErrorCode.MISSING_UPDATE_SOURCE,
            update.failures.first().errorCode,
        )

        val search = plugin.search(query = "in the end", page = 1)
        assertTrue(
            "Plugin should remain usable after update failure",
            search.data.isNotEmpty(),
        )
    }

    @Test
    fun updateAllPlugins_withoutSources_returnsFailureSummary() = runBlocking {
        val pluginFile1 = File.createTempFile("runtime-update-all-1-", ".js", appContext.cacheDir)
        pluginFile1.writeText(runtimeShimScript)
        val pluginFile2 = File.createTempFile("runtime-update-all-2-", ".js", appContext.cacheDir)
        pluginFile2.writeText(
            runtimeShimScript.replace(
                "runtime-shim-it",
                "runtime-shim-it-2",
            ),
        )

        val first = pluginManager.installFromFile(pluginFile1)
        val second = pluginManager.installFromFile(pluginFile2)
        assertNotNull("First runtime plugin should install", first)
        assertNotNull("Second runtime plugin should install", second)

        val result = pluginManager.updateAllPlugins()
        assertEquals(PluginOperationType.UPDATE_ALL, result.operationType)
        assertEquals(0, result.successCount)
        assertEquals(2, result.failureCount)
        assertTrue(
            "All failures should be missing source for local runtime plugins",
            result.failures.all { it.errorCode == PluginOperationErrorCode.MISSING_UPDATE_SOURCE },
        )
    }

    private fun clearPluginStorage() = runBlocking {
        val pluginsDir = File(appContext.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { it.delete() }
        }
        pluginManager.loadAllPlugins()
    }

    private val runtimeShimScript = """
        const axios = require('axios');
        const CryptoJS = require('crypto-js');
        const qs = require('qs');
        const he = require('he');
        const dayjs = require('dayjs');
        const bigInt = require('big-integer');
        
        module.exports = {
          platform: 'runtime-shim-it',
          version: '1.0.0',
          supportedSearchType: ['music'],
          async search(query, page, type) {
            const req = axios.default({ method: 'noop', url: 'https://example.com' });
            const q = qs.stringify({ q: query, page: page });
            const decoded = he.decode('&amp;');
            const date = dayjs('2026-03-21').format('YYYY-MM-DD');
            const mod = bigInt('2', 10).modPow(bigInt('5', 10), bigInt('13', 10)).toString(10);
            const encrypted = CryptoJS.AES.encrypt(
              CryptoJS.enc.Utf8.parse('abc'),
              CryptoJS.enc.Utf8.parse('0123456789abcdef'),
              {
                iv: CryptoJS.enc.Utf8.parse('0102030405060708'),
                mode: CryptoJS.mode.CBC
              }
            ).toString();
            return {
              isEnd: true,
              data: [{
                id: 'it-1',
                platform: 'runtime-shim-it',
                songmid: 'song-mid-it-1',
                title: decoded + '|' + q + '|' + date + '|' + mod + '|' + req.status + '|' + typeof encrypted,
                artist: 'integration',
                album: 'integration',
                duration: 1,
                url: 'https://example.com'
              }]
            };
          },
          async getMediaSource(musicItem, quality) {
            if (!musicItem.songmid) {
              throw new Error('songmid missing');
            }
            return {
              url: 'https://example.com/play/' + musicItem.songmid + '?q=' + quality
            };
          }
        };
    """.trimIndent()
}
```

- [ ] **Step 2：（如有设备）跑 androidTest 验证**

```bash
./gradlew :plugin:connectedAndroidTest --tests "com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeLocalIntegrationTest"
```

预期：3 个用例 PASSED。

如无设备：跳过此 step，CI 验收。

- [ ] **Step 3：commit**

```bash
git add plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeLocalIntegrationTest.kt
git commit -m "$(cat <<'EOF'
test(plugin): extract local runtime-shim integration cases

Pulls the 3 network-free PluginManager integration cases out of the
class-ignored PluginRuntimeIntegrationTest into a standalone test
class with no @Ignore. Runs always in :plugin:connectedAndroidTest.

Cases extracted:
- localRuntimeShimPlugin_search_executesWithoutNotFunctionErrors
- updatePlugin_withoutSource_returnsMissingSource_andKeepsPluginUsable
- updateAllPlugins_withoutSources_returnsFailureSummary

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：新增 `PluginRuntimeNetworkIntegrationTest.kt`（4 个网络用例 + Assume 门控）

**Files:**
- Create: `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeNetworkIntegrationTest.kt`

- [ ] **Step 1：写入文件**

完整文件内容（4 个网络用例 + `@Before` 中的 `Assume.assumeTrue` 门控，**不带类级 `@Ignore`**）：

```kotlin
package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Live-network integration tests for PluginManager. Depends on
 * `https://13413.kstore.vip/yuanli/...` being reachable. CI default
 * channel SKIPS these via Assume.assumeTrue; pass `-Pintegration` to
 * Gradle to enable.
 */
@RunWith(AndroidJUnit4::class)
class PluginRuntimeNetworkIntegrationTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager

    @Before
    fun setUp() {
        val arg = InstrumentationRegistry.getArguments().getString("pluginNetworkTests")
        Assume.assumeTrue(
            "Skipping plugin network integration tests; pass -Pintegration to enable.",
            arg == "true",
        )

        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(appContext.cacheDir, "plugin-runtime-network-it.preferences_pb") },
        )
        pluginManager = PluginManager(appContext, PluginMetaStore(dataStore))
        clearPluginStorage()
    }

    @Test
    fun yuanliWy_searchAndMediaSource_returnsPlayableUrl() = runBlocking {
        val wy = pluginManager.installFromUrl(
            url = "https://13413.kstore.vip/yuanli/wy.js",
            fileName = "wy-it.js",
        )
        assertNotNull("WY plugin should install", wy)

        val wySearch = wy!!.search(query = "in the end", page = 1)
        assertTrue(
            "WY search should return at least one result",
            wySearch.data.isNotEmpty(),
        )

        var mediaSourceUrl: String? = null
        for (item in wySearch.data.take(5)) {
            val source = runCatching {
                wy.getMediaSource(
                    musicItem = item,
                    quality = "standard",
                )
            }.getOrNull()
            if (source != null && source.url.isNotBlank()) {
                mediaSourceUrl = source.url
                break
            }
        }
        assertTrue(
            "WY getMediaSource should return playable url",
            !mediaSourceUrl.isNullOrBlank(),
        )
    }

    @Test
    fun defaultSubscription_installAndWyPlaybackChain_succeeds() = runBlocking {
        val install = pluginManager.installFromSubscriptionUrl(
            subscriptionUrl = "https://13413.kstore.vip/yuanli/yuanli.json",
        )
        assertTrue(
            "Default subscription should install at least one plugin",
            install.successfulInstalls > 0,
        )

        val wy = pluginManager.plugins.value.firstOrNull { loaded ->
            loaded.info.platform.contains("WY", ignoreCase = true) ||
                loaded.info.platform.contains("网易")
        }
        assertNotNull("Default subscription should contain WY plugin", wy)

        val search = wy!!.search(query = "in the end", page = 1)
        assertTrue(
            "WY search from default subscription should return at least one result",
            search.data.isNotEmpty(),
        )

        var mediaSourceUrl: String? = null
        for (item in search.data.take(5)) {
            val source = runCatching {
                wy.getMediaSource(
                    musicItem = item,
                    quality = "standard",
                )
            }.getOrNull()
            if (source != null && source.url.isNotBlank()) {
                mediaSourceUrl = source.url
                break
            }
        }
        assertTrue(
            "WY getMediaSource from default subscription should return playable url",
            !mediaSourceUrl.isNullOrBlank(),
        )
    }

    @Test
    fun updatePlugin_thenSearchStillWorks_returnsPlayableResults() = runBlocking {
        val wy = pluginManager.installFromUrl(
            url = "https://13413.kstore.vip/yuanli/wy.js",
            fileName = "wy-update-search.js",
        )
        assertNotNull("WY plugin should install", wy)
        val platform = wy!!.info.platform

        val update = pluginManager.updatePlugin(platform)
        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)

        val updated = pluginManager.getPlugin(platform)
        assertNotNull("Updated plugin should remain selectable by platform", updated)
        assertEquals(
            "Update should not create duplicate plugin entries for same platform",
            1,
            pluginManager.plugins.value.count { it.info.platform == platform },
        )

        val search = updated!!.search(query = "in the end", page = 1)
        assertTrue(
            "Search should still work after plugin update",
            search.data.isNotEmpty(),
        )
        var playableUrl: String? = null
        for (item in search.data.take(5)) {
            val source = runCatching {
                updated.getMediaSource(item, quality = "standard")
            }.getOrNull()
            if (source != null && source.url.isNotBlank()) {
                playableUrl = source.url
                break
            }
        }
        assertTrue(!playableUrl.isNullOrBlank())
    }

    @Test
    fun updatePlugin_afterSearchRegression_keepsSearchablePluginUsable() = runBlocking {
        val wy = pluginManager.installFromUrl(
            url = "https://13413.kstore.vip/yuanli/wy.js",
            fileName = "wy-post-update-search.js",
        )
        assertNotNull("WY plugin should install", wy)

        val beforeUpdateSearch = wy!!.search(query = "in the end", page = 1)
        assertTrue(beforeUpdateSearch.data.isNotEmpty())

        val update = pluginManager.updatePlugin(wy.info.platform)
        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)

        val selectedAfterUpdate = pluginManager.getPlugin(wy.info.platform)
        assertNotNull("Updated plugin should remain selectable by platform", selectedAfterUpdate)

        val afterUpdateSearch = selectedAfterUpdate!!.search(query = "in the end", page = 1)
        assertTrue(
            "Search should still work after plugin update and keep the selected plugin usable",
            afterUpdateSearch.data.isNotEmpty(),
        )
    }

    private fun clearPluginStorage() = runBlocking {
        val pluginsDir = File(appContext.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { it.delete() }
        }
        pluginManager.loadAllPlugins()
    }
}
```

- [ ] **Step 2：（无设备时）跳过；（有设备无 `-Pintegration`）验证 SKIP；（有设备 + `-Pintegration`）验证 PASS**

无 `-Pintegration`（CI 默认）：

```bash
./gradlew :plugin:connectedAndroidTest --tests "com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeNetworkIntegrationTest"
```

预期：4 个用例 SKIPPED（assumption violated）。

有 `-Pintegration`（按需）：

```bash
./gradlew :plugin:connectedAndroidTest --tests "com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeNetworkIntegrationTest" -Pintegration
```

预期：4 个用例 PASSED（需 kstore.vip 可达）。如真机 PASS，建议把 stdout 头部贴入 PR 描述作为 baseline。

- [ ] **Step 3：commit**

```bash
git add plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeNetworkIntegrationTest.kt
git commit -m "$(cat <<'EOF'
test(plugin): extract live-network integration cases with -Pintegration gate

Pulls the 4 kstore.vip-dependent PluginManager integration cases out of
the class-ignored PluginRuntimeIntegrationTest into a standalone class.
Runtime gating via Assume.assumeTrue on the `pluginNetworkTests`
instrumentation arg (set true only when -Pintegration is passed),
which means: CI default channel SKIPS; manual `-Pintegration` runs
exercise live network.

Cases extracted:
- yuanliWy_searchAndMediaSource_returnsPlayableUrl
- defaultSubscription_installAndWyPlaybackChain_succeeds
- updatePlugin_thenSearchStillWorks_returnsPlayableResults
- updatePlugin_afterSearchRegression_keepsSearchablePluginUsable

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：新增 `PluginManagerHttpLifecycleTest.kt`（2 个 MockWebServer 用例）

**Files:**
- Create: `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerHttpLifecycleTest.kt`

- [ ] **Step 1：写入文件**

完整文件内容（2 个用例 + MockWebServer 前后置）：

```kotlin
package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * MockWebServer-backed lifecycle tests for PluginManager.installFromUrl
 * and updatePlugin. Covers the orchestration paths (HTTP fetch, disk
 * write, state-flow registration, refetch + replace) without depending
 * on real plugin scripts or real network. Always runs in
 * :plugin:connectedAndroidTest (CI default channel).
 */
@RunWith(AndroidJUnit4::class)
class PluginManagerHttpLifecycleTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(appContext.cacheDir, "plugin-http-lifecycle-it.preferences_pb") },
        )
        pluginManager = PluginManager(appContext, PluginMetaStore(dataStore))
        clearPluginStorage()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun installFromUrl_writesPluginAndLoadsMeta() = runBlocking {
        server.enqueue(MockResponse().setBody(scriptForVersion("1.0.0")))

        val plugin = pluginManager.installFromUrl(
            url = server.url("/mockws.js").toString(),
            fileName = "mockws-lifecycle.js",
        )

        assertNotNull("MockWebServer-served plugin should install", plugin)
        assertEquals(PLATFORM, plugin!!.info.platform)
        assertEquals("1.0.0", plugin.info.version)

        assertTrue(
            "Installed plugin file should be on disk",
            File(plugin.filePath).exists(),
        )
        assertNotNull(
            "Manager should expose the plugin via getPlugin(platform)",
            pluginManager.getPlugin(PLATFORM),
        )
        assertEquals(
            "plugins state-flow should contain exactly one entry for this platform",
            1,
            pluginManager.plugins.value.count { it.info.platform == PLATFORM },
        )
    }

    @Test
    fun updatePlugin_refetchesAndReplaces() = runBlocking {
        server.enqueue(MockResponse().setBody(scriptForVersion("1.0.0")))
        val installed = pluginManager.installFromUrl(
            url = server.url("/mockws.js").toString(),
            fileName = "mockws-update.js",
        )
        assertNotNull("Initial install should succeed", installed)
        assertEquals("1.0.0", installed!!.info.version)

        server.enqueue(MockResponse().setBody(scriptForVersion("1.0.1")))
        val update = pluginManager.updatePlugin(PLATFORM)

        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)
        assertEquals(
            "Update should report exactly one success",
            1,
            update.successCount,
        )

        val updated = pluginManager.getPlugin(PLATFORM)
        assertNotNull("Updated plugin should remain selectable by platform", updated)
        assertEquals(
            "Updated plugin should report the new version",
            "1.0.1",
            updated!!.info.version,
        )
        assertEquals(
            "Update should not create duplicate plugin entries",
            1,
            pluginManager.plugins.value.count { it.info.platform == PLATFORM },
        )
    }

    private fun clearPluginStorage() = runBlocking {
        val pluginsDir = File(appContext.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { it.delete() }
        }
        pluginManager.loadAllPlugins()
    }

    private fun scriptForVersion(version: String): String = """
        module.exports = {
          platform: '$PLATFORM',
          version: '$version',
          supportedSearchType: ['music'],
          async search(query, page) {
            return { isEnd: true, data: [] };
          },
          async getMediaSource(musicItem, quality) {
            return null;
          }
        };
    """.trimIndent()

    private companion object {
        const val PLATFORM = "mockws-lifecycle"
    }
}
```

- [ ] **Step 2：（如有设备）跑 androidTest 验证**

```bash
./gradlew :plugin:connectedAndroidTest --tests "com.zili.android.musicfreeandroid.plugin.manager.PluginManagerHttpLifecycleTest"
```

预期：2 个用例 PASSED。

如果 `installFromUrl_writesPluginAndLoadsMeta` 失败、报 `version == null`：检查 `PluginInfo` 解析 JS `version` 字段的路径是否正常（这是已实现行为，理论应通过——若失败属新发现 bug，按 spec §6.4 处理）。

如果端口冲突：MockWebServer 默认随机端口；如发生冲突，重跑或在 `@Before` 中显式 `server.start(0)`。

- [ ] **Step 3：commit**

```bash
git add plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerHttpLifecycleTest.kt
git commit -m "$(cat <<'EOF'
test(plugin): add MockWebServer lifecycle test for installFromUrl + updatePlugin

Covers the HTTP-fetch + disk-write + state-flow + refetch-and-replace
orchestration in PluginManager without depending on real plugin scripts
or real network. Two cases:

- installFromUrl_writesPluginAndLoadsMeta — verifies install path:
  on-disk file, getPlugin(), plugins state-flow registration.
- updatePlugin_refetchesAndReplaces — verifies update path: refetches
  via stored sourceUrl, swaps content, no duplicate entries, version
  bump observable in info.

Always runs in :plugin:connectedAndroidTest; complements the live-
network suite gated by -Pintegration.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7：删除原 `PluginRuntimeIntegrationTest.kt`

**Files:**
- Delete: `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt`

- [ ] **Step 1：删除文件**

```bash
git rm plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt
```

- [ ] **Step 2：grep 验证 `:plugin/src/androidTest` 已无 `@Ignore`**

```bash
grep -rn "@Ignore" plugin/src/androidTest/ 2>/dev/null
```

预期：空输出。

- [ ] **Step 3：编译保护**

```bash
./gradlew :plugin:assembleDebugAndroidTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 4：commit**

```bash
git commit -m "$(cat <<'EOF'
test(plugin): remove class-ignored PluginRuntimeIntegrationTest after split

All 7 cases were redistributed:
- 3 local cases → PluginRuntimeLocalIntegrationTest
- 4 network cases → PluginRuntimeNetworkIntegrationTest (Assume-gated)
- Plus 2 new MockWebServer cases in PluginManagerHttpLifecycleTest

After this commit, :plugin/src/androidTest has 0 @Ignore.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8：默认通道运行验证（无 `-Pintegration`）

**Files:** 无

- [ ] **Step 1：跑 `:plugin:connectedAndroidTest`（默认通道）**

```bash
./gradlew :plugin:connectedAndroidTest
```

预期：

- `PluginRuntimeLocalIntegrationTest`：3 个 PASSED
- `PluginManagerHttpLifecycleTest`：2 个 PASSED
- `PluginRuntimeNetworkIntegrationTest`：4 个 SKIPPED（assumption violated）

如无设备：跳过；CI 验收。

- [ ] **Step 2：跑 `:plugin:testDebugUnitTest` 单测回归**

```bash
./gradlew :plugin:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

---

## Task 9：按需通道运行验证（带 `-Pintegration`）

**Files:** 无

- [ ] **Step 1：（如有设备 + 网络）跑 `:plugin:connectedAndroidTest -Pintegration`**

```bash
./gradlew :plugin:connectedAndroidTest -Pintegration
```

预期：9 个用例全部 PASSED（3 local + 4 network + 2 MockWebServer）。需 `kstore.vip` 可达。

如真机 PASS：截 1 段 stdout（包含每个用例的 PASSED 行），贴入 PR 描述作为 baseline。

如 `kstore.vip` 不可达：不阻塞 PR——这是设计上"按需手动通道"，单独排查。

---

## Task 10：跨模块回归 + push + PR

**Files:** 无

- [ ] **Step 1：整体编译 + lint 保护**

```bash
./gradlew assembleDebug
./gradlew lint
```

预期：均 `BUILD SUCCESSFUL`。

- [ ] **Step 2：grep 验证全仓库 `@Ignore` 归零**

```bash
grep -rn "@Ignore" --include="*.kt" 2>/dev/null | grep -v build/ | grep -v .worktrees/
```

预期：空输出。

如有残留：查 spec §6.3 终态指标表，确定残留是否在本 PR 范围；如不在本范围，记录 follow-up；如在本范围，回到对应 task 修复。

- [ ] **Step 3：push 分支**

```bash
git push -u origin test/plugin-it-split
```

- [ ] **Step 4：创建 PR**

```bash
gh pr create --title "test(plugin): split runtime IT into local/network + MockWebServer lifecycle" --body "$(cat <<'EOF'
## Summary

- Splits the class-ignored `PluginRuntimeIntegrationTest` into 3 files: 3 local cases (always-on), 4 network cases gated by `Assume.assumeTrue` on the `pluginNetworkTests` instrumentation arg, and 2 new `MockWebServer` lifecycle cases (`installFromUrl` + `updatePlugin` orchestration).
- Wires `-Pintegration` Gradle property → `pluginNetworkTests=true` instrumentation arg in `:plugin/build.gradle.kts`.
- Adds `okhttp-mockwebserver` library entry (reuses existing OkHttp 5.3.2).
- Net: class-level `@Ignore` eliminated; CI default channel runs 5 cases (was 0); `-Pintegration` channel runs all 9.

Spec: `docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md` §5.

## Test plan

- [ ] `./gradlew :plugin:testDebugUnitTest`
- [ ] `./gradlew :plugin:connectedAndroidTest` — 5 PASSED, 4 SKIPPED
- [ ] `./gradlew :plugin:connectedAndroidTest -Pintegration` — 9 PASSED (requires `kstore.vip` reachable; not blocking if intermittent)
- [ ] `./gradlew assembleDebug && ./gradlew lint`
- [ ] `./gradlew connectedAndroidTest` after PR 1 is merged/rebased into this branch

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

预期：PR URL 输出。

---

## 验收闸门（PR 2 总体）

合并前必须满足：

- `:plugin:testDebugUnitTest` PASS
- `:plugin:connectedAndroidTest`（无 `-Pintegration`）：5 PASSED + 4 SKIPPED
- `assembleDebug`、`lint` PASS
- `grep -rn "@Ignore" plugin/src/androidTest/` 输出为空
- `:plugin:connectedAndroidTest -Pintegration` 至少在本地真机 PASS 一次（贴 baseline 至 PR 描述）
- 基于包含 PR 1 的主线重跑 `./gradlew connectedAndroidTest` PASS
- 无新引入的 `@Ignore`

合并后：

- 删除 worktree（`git worktree remove .worktrees/test/plugin-it-split`）。
- **PR 2 是 spec 的最后一块**。一旦 PR 1 + PR 2 都合入 main，按 spec §8 把 [`docs/DOCS_STATUS.md`](../../DOCS_STATUS.md) 中本 spec 行的状态从 `当前规范（Android 测试稳定性专项）` 降为 `当前参考`，并把 `最后校验` 字段更新到验收日期。该收尾通过单独的 docs commit 完成，不在本 PR 范围内。
