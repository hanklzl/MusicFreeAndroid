# Android 发布流水线与内置更新检查 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `git push` tag 自动产出已签名 APK + GitHub Release + LLM 总结 + `CHANGELOG.md` 自动追加 + `version.json` 发布到 `gh-pages`；客户端冷启动检测新版，启动 dialog → 模态下载 → 安装。

**Architecture:** 新增独立模块 `:updater`，依赖 `:core`，对外暴露 `UpdateChecker.state` 单 `StateFlow`。CI 流水线 `android-release-apk.yml` 在既有签名 job 后追加 release-notes 生成、CHANGELOG push、`gh-pages` 推送三件事。CI 与本地共用 `scripts/release/*.sh`。版本号经 `version.properties` 单点驱动，tag 与之必须一致。

**Tech Stack:** Kotlin 2.3.21、Compose BOM 2026.04.01、Hilt 2.59.2、OkHttp 5.3.2、kotlinx.serialization 1.11.0、androidx DataStore 1.2.1、Robolectric 4.16.1、MockWebServer、Bash + `gh` CLI + GitHub Actions。

**Spec:** [`docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md`](../specs/2026-05-13-android-release-pipeline-design.md)

---

## 全局约定

- 所有 Kotlin 测试用 JUnit4 + MockK + Turbine（Flow）+ Robolectric（如需 Android Context）。
- 单元测试目录 `src/test/java/...`；仪器测试 `src/androidTest/java/...`。
- 包名前缀：`com.zili.android.musicfreeandroid.updater.*`。
- commit message 用中文 conventional commits（`feat(updater): …`、`fix(release): …`、`docs: …`）。
- 每个 Task 末尾都包含一个独立 commit；不要把多个 Task 的产出合在一个 commit 里。
- 当前在 `main` 分支。**实施开始前先创建 worktree**：

  ```bash
  git worktree add .worktrees/android-release-pipeline -b feat/android-release-pipeline main
  cd .worktrees/android-release-pipeline
  ```

  后续所有命令在该 worktree 内执行。

---

## Task 1：新增 `version.properties` 并接入 `app/build.gradle.kts`

**Files:**
- Create: `version.properties`
- Modify: `app/build.gradle.kts`（`defaultConfig` 块内的 `versionCode = 1` / `versionName = "1.0"`）

- [ ] **Step 1：新增 `version.properties`**

```properties
versionCode=10000
versionName=1.0.0
```

- [ ] **Step 2：修改 `app/build.gradle.kts` 读取 properties**

在文件顶部 `plugins { ... }` 块**之后**、`val releaseSigningEnvironmentVariables` 之前新增：

```kotlin
val versionProps = java.util.Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val appVersionCode: Int = versionProps.getProperty("versionCode")?.toIntOrNull()
    ?: throw org.gradle.api.GradleException("version.properties: versionCode missing or invalid")
val appVersionName: String = versionProps.getProperty("versionName")
    ?: throw org.gradle.api.GradleException("version.properties: versionName missing")
```

把 `defaultConfig` 块内的：

```kotlin
versionCode = 1
versionName = "1.0"
```

替换为：

```kotlin
versionCode = appVersionCode
versionName = appVersionName
```

- [ ] **Step 3：跑 Debug 构建验证**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。APK 内 versionName=1.0.0 / versionCode=10000（通过 `aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep version` 校验）。

- [ ] **Step 4：commit**

```bash
git add version.properties app/build.gradle.kts
git commit -m "feat(release): 通过 version.properties 单点驱动应用版本号"
```

---

## Task 2：搭建 `:updater` 模块骨架

**Files:**
- Create: `updater/build.gradle.kts`
- Create: `updater/src/main/AndroidManifest.xml`
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/.gitkeep`
- Modify: `settings.gradle.kts`（追加 `include(":updater")`）

- [ ] **Step 1：建模块目录**

Run:
```bash
mkdir -p updater/src/main/java/com/zili/android/musicfreeandroid/updater
mkdir -p updater/src/test/java/com/zili/android/musicfreeandroid/updater
mkdir -p updater/src/androidTest/java/com/zili/android/musicfreeandroid/updater
```

- [ ] **Step 2：`updater/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zili.android.musicfreeandroid.updater"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)

    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
```

- [ ] **Step 3：`updater/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
</manifest>
```

> 注：`REQUEST_INSTALL_PACKAGES` 与 FileProvider 注册放在 `:app` 模块的 AndroidManifest（在 Task 13 完成），不放 library，避免库被引入即声明敏感权限。

- [ ] **Step 4：占位文件防止空目录被 git 丢弃**

新建 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/.gitkeep`，内容为空字符串。

- [ ] **Step 5：在 `settings.gradle.kts` 末尾追加**

```kotlin
include(":updater")
```

- [ ] **Step 6：跑模块构建验证**

Run: `./gradlew :updater:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7：commit**

```bash
git add updater settings.gradle.kts
git commit -m "feat(updater): 新增模块骨架"
```

---

## Task 3：`UpdateInfo` 数据契约 + 反序列化测试

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfo.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfoTest.kt`

- [ ] **Step 1：先写失败测试**

`updater/src/test/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfoTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses canonical version json`() {
        val raw = """
            {
              "schemaVersion": 1,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-13T18:00:00Z",
              "download": [
                "https://example.com/a.apk",
                "https://example.com/b.apk"
              ],
              "size": 23456789,
              "sha256": "f3a8c901",
              "changeLog": ["新功能 1", "修复 2"],
              "releaseNotesUrl": "https://example.com/notes"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals(1, info.schemaVersion)
        assertEquals("1.2.3", info.version)
        assertEquals(10203L, info.versionCode)
        assertEquals(2, info.download.size)
        assertEquals(23456789L, info.size)
        assertEquals("f3a8c901", info.sha256)
        assertEquals(listOf("新功能 1", "修复 2"), info.changeLog)
        assertEquals("https://example.com/notes", info.releaseNotesUrl)
    }

    @Test
    fun `ignores unknown fields`() {
        val raw = """
            {
              "schemaVersion": 1,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-13T18:00:00Z",
              "download": ["https://example.com/a.apk"],
              "size": 1,
              "sha256": "x",
              "changeLog": [],
              "releaseNotesUrl": "https://example.com/notes",
              "futureField": "ignored"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals("1.2.3", info.version)
    }

    @Test(expected = Exception::class)
    fun `rejects missing required field`() {
        val raw = """{ "schemaVersion": 1, "version": "1.2.3" }"""
        json.decodeFromString(UpdateInfo.serializer(), raw)
    }

    @Test
    fun `marks schema version greater than supported`() {
        val raw = """
            {
              "schemaVersion": 99,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-13T18:00:00Z",
              "download": ["https://example.com/a.apk"],
              "size": 1,
              "sha256": "x",
              "changeLog": [],
              "releaseNotesUrl": "https://example.com/notes"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertTrue(info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION)
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.UpdateInfoTest" --no-daemon`
Expected: FAIL — `Unresolved reference: UpdateInfo`。

- [ ] **Step 3：写最小实现**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfo.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val schemaVersion: Int,
    val version: String,
    val versionCode: Long,
    val releasedAt: String,
    val download: List<String>,
    val size: Long,
    val sha256: String,
    val changeLog: List<String>,
    val releaseNotesUrl: String,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.UpdateInfoTest" --no-daemon`
Expected: 4 passed。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfo.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfoTest.kt
git commit -m "feat(updater): UpdateInfo 数据契约"
```

---

## Task 4：版本比较 `VersionCompare`

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/VersionCompare.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/VersionCompareTest.kt`

- [ ] **Step 1：先写失败测试**

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `prefers versionCode comparison`() {
        assertEquals(
            VersionCompare.Outcome.NewerAvailable,
            VersionCompare.compare(
                localCode = 10000, localName = "1.0.0",
                remoteCode = 10203, remoteName = "1.2.3",
            ),
        )
    }

    @Test
    fun `equal versionCode is up to date`() {
        assertEquals(
            VersionCompare.Outcome.UpToDate,
            VersionCompare.compare(
                localCode = 10203, localName = "1.2.3",
                remoteCode = 10203, remoteName = "1.2.3",
            ),
        )
    }

    @Test
    fun `remote behind local is up to date`() {
        assertEquals(
            VersionCompare.Outcome.UpToDate,
            VersionCompare.compare(
                localCode = 10204, localName = "1.2.4",
                remoteCode = 10203, remoteName = "1.2.3",
            ),
        )
    }

    @Test
    fun `falls back to semver when remote versionCode is zero`() {
        assertEquals(
            VersionCompare.Outcome.NewerAvailable,
            VersionCompare.compare(
                localCode = 10000, localName = "1.0.0",
                remoteCode = 0, remoteName = "1.0.1",
            ),
        )
    }

    @Test
    fun `unparseable semver triggers unsupported`() {
        assertEquals(
            VersionCompare.Outcome.Unsupported,
            VersionCompare.compare(
                localCode = 0, localName = "x.y.z",
                remoteCode = 0, remoteName = "garbage",
            ),
        )
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.VersionCompareTest" --no-daemon`
Expected: FAIL — `Unresolved reference: VersionCompare`。

- [ ] **Step 3：写实现**

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

object VersionCompare {

    enum class Outcome { NewerAvailable, UpToDate, Unsupported }

    fun compare(
        localCode: Long,
        localName: String,
        remoteCode: Long,
        remoteName: String,
    ): Outcome {
        if (remoteCode > 0L && localCode > 0L) {
            return when {
                remoteCode > localCode -> Outcome.NewerAvailable
                else -> Outcome.UpToDate
            }
        }
        val localParts = parse(localName) ?: return Outcome.Unsupported
        val remoteParts = parse(remoteName) ?: return Outcome.Unsupported
        return if (compareSemver(remoteParts, localParts) > 0) {
            Outcome.NewerAvailable
        } else {
            Outcome.UpToDate
        }
    }

    private fun parse(name: String): IntArray? {
        val parts = name.split('.', limit = 4)
        if (parts.size < 3) return null
        val nums = IntArray(3)
        for (i in 0 until 3) {
            nums[i] = parts[i].toIntOrNull() ?: return null
        }
        return nums
    }

    private fun compareSemver(a: IntArray, b: IntArray): Int {
        for (i in 0 until 3) {
            val diff = a[i] - b[i]
            if (diff != 0) return diff
        }
        return 0
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.VersionCompareTest" --no-daemon`
Expected: 5 passed。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/VersionCompare.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/VersionCompareTest.kt
git commit -m "feat(updater): 版本号比较优先 versionCode，semver 兜底"
```

---

## Task 5：`UpdateState` / `UpdateError` 状态类型

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateState.kt`

- [ ] **Step 1：写实现（纯类型定义，无单测必要）**

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import java.io.File

sealed interface UpdateState {

    data object Idle : UpdateState
    data object Checking : UpdateState

    data class UpToDate(val checkedAtEpochMillis: Long) : UpdateState

    data class Available(
        val info: UpdateInfo,
        val skipped: Boolean,
    ) : UpdateState

    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val bytes: Long,
        val total: Long,
    ) : UpdateState

    data class ReadyToInstall(
        val info: UpdateInfo,
        val apkFile: File,
    ) : UpdateState

    data class Failed(
        val info: UpdateInfo?,
        val cause: UpdateError,
    ) : UpdateState

    val hasUnreadAvailableUpdate: Boolean
        get() = this is Available && !skipped
}

enum class UpdateError {
    Network,
    SchemaUnsupported,
    SizeMismatch,
    Sha256Mismatch,
    Canceled,
    InstallBlocked,
}
```

- [ ] **Step 2：跑模块测试确认仍通过**

Run: `./gradlew :updater:testDebugUnitTest --no-daemon`
Expected: all green。

- [ ] **Step 3：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateState.kt
git commit -m "feat(updater): UpdateState 与 UpdateError 状态类型"
```

---

## Task 6：`UpdatePreferences`（DataStore）

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/store/UpdatePreferences.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/store/UpdatePreferencesTest.kt`

- [ ] **Step 1：先写测试**

```kotlin
package com.zili.android.musicfreeandroid.updater.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdatePreferencesTest {

    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: UpdatePreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("updater_prefs_test") },
        )
        prefs = UpdatePreferences(store)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.preferencesDataStoreFile("updater_prefs_test").delete()
    }

    @Test
    fun `skip version write then read`() = runTest {
        assertNull(prefs.getSkipVersion())
        prefs.setSkipVersion("1.2.3")
        assertEquals("1.2.3", prefs.getSkipVersion())
    }

    @Test
    fun `clear skip version writes null`() = runTest {
        prefs.setSkipVersion("1.2.3")
        prefs.clearSkipVersion()
        assertNull(prefs.getSkipVersion())
    }

    @Test
    fun `last checked at round trip`() = runTest {
        prefs.setLastCheckedAt(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, prefs.getLastCheckedAt())
    }

    @Test
    fun `last seen version round trip`() = runTest {
        prefs.setLastSeenVersion("1.2.3")
        assertEquals("1.2.3", prefs.getLastSeenVersion())
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.UpdatePreferencesTest" --no-daemon`
Expected: FAIL — `Unresolved reference: UpdatePreferences`。

- [ ] **Step 3：写实现**

```kotlin
package com.zili.android.musicfreeandroid.updater.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdatePreferences @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    suspend fun getSkipVersion(): String? = store.data.first()[KEY_SKIP_VERSION]

    suspend fun setSkipVersion(version: String) {
        store.edit { it[KEY_SKIP_VERSION] = version }
    }

    suspend fun clearSkipVersion() {
        store.edit { it.remove(KEY_SKIP_VERSION) }
    }

    suspend fun getLastCheckedAt(): Long = store.data.first()[KEY_LAST_CHECKED_AT] ?: 0L

    suspend fun setLastCheckedAt(epochMillis: Long) {
        store.edit { it[KEY_LAST_CHECKED_AT] = epochMillis }
    }

    suspend fun getLastSeenVersion(): String? = store.data.first()[KEY_LAST_SEEN_VERSION]

    suspend fun setLastSeenVersion(version: String) {
        store.edit { it[KEY_LAST_SEEN_VERSION] = version }
    }

    private companion object {
        val KEY_SKIP_VERSION = stringPreferencesKey("skip_version")
        val KEY_LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        val KEY_LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.UpdatePreferencesTest" --no-daemon`
Expected: 4 passed。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/store/UpdatePreferences.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/store/UpdatePreferencesTest.kt
git commit -m "feat(updater): UpdatePreferences 持久化 skip/lastChecked/lastSeen"
```

---

## Task 7：`UpdateClient` 多镜像顺序拉取

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/api/UpdateClient.kt`
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/api/OkHttpUpdateClient.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/api/OkHttpUpdateClientTest.kt`

- [ ] **Step 1：先写测试**

```kotlin
package com.zili.android.musicfreeandroid.updater.api

import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpUpdateClientTest {

    private lateinit var primary: MockWebServer
    private lateinit var fallback: MockWebServer
    private lateinit var http: OkHttpClient

    @Before
    fun setUp() {
        primary = MockWebServer().apply { start() }
        fallback = MockWebServer().apply { start() }
        http = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        primary.shutdown()
        fallback.shutdown()
    }

    private fun canonicalJson(): String = """
        {
          "schemaVersion": 1,
          "version": "1.2.3",
          "versionCode": 10203,
          "releasedAt": "2026-05-13T18:00:00Z",
          "download": ["https://example.com/a.apk"],
          "size": 1,
          "sha256": "x",
          "changeLog": [],
          "releaseNotesUrl": "https://example.com/notes"
        }
    """.trimIndent()

    @Test
    fun `returns info when primary succeeds`() = runTest {
        primary.enqueue(MockResponse().setBody(canonicalJson()))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        val info: UpdateInfo? = client.fetchLatest()
        assertEquals("1.2.3", info?.version)
        assertEquals(1, primary.requestCount)
        assertEquals(0, fallback.requestCount)
    }

    @Test
    fun `falls back when primary returns 500`() = runTest {
        primary.enqueue(MockResponse().setResponseCode(500))
        fallback.enqueue(MockResponse().setBody(canonicalJson()))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        val info: UpdateInfo? = client.fetchLatest()
        assertEquals("1.2.3", info?.version)
        assertEquals(1, primary.requestCount)
        assertEquals(1, fallback.requestCount)
    }

    @Test
    fun `returns null when all mirrors fail`() = runTest {
        primary.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        fallback.enqueue(MockResponse().setResponseCode(503))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        assertNull(client.fetchLatest())
    }

    @Test
    fun `returns null when body is unparseable`() = runTest {
        primary.enqueue(MockResponse().setBody("garbage{"))
        fallback.enqueue(MockResponse().setBody("also bad"))
        val client = OkHttpUpdateClient(
            http = http,
            mirrors = listOf(primary.url("/v.json").toString(), fallback.url("/v.json").toString()),
        )
        assertNull(client.fetchLatest())
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.OkHttpUpdateClientTest" --no-daemon`
Expected: FAIL — Unresolved references。

- [ ] **Step 3：写接口**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/api/UpdateClient.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.api

import com.zili.android.musicfreeandroid.updater.model.UpdateInfo

interface UpdateClient {
    suspend fun fetchLatest(): UpdateInfo?
}
```

- [ ] **Step 4：写实现**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/api/OkHttpUpdateClient.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.api

import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class OkHttpUpdateClient @Inject constructor(
    private val http: OkHttpClient,
    private val mirrors: List<String>,
) : UpdateClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in mirrors) {
            val info = tryFetch(url) ?: continue
            return@withContext info
        }
        null
    }

    private fun tryFetch(url: String): UpdateInfo? = try {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            json.decodeFromString(UpdateInfo.serializer(), body)
        }
    } catch (t: Throwable) {
        null
    }
}
```

- [ ] **Step 5：跑测试确认通过**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.OkHttpUpdateClientTest" --no-daemon`
Expected: 4 passed。

- [ ] **Step 6：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/api \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/api
git commit -m "feat(updater): UpdateClient 多镜像顺序拉取"
```

---

## Task 8：`UpdateChecker` 状态机

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateChecker.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/UpdateCheckerTest.kt`

- [ ] **Step 1：先写测试**

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import app.cash.turbine.test
import com.zili.android.musicfreeandroid.updater.api.UpdateClient
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import com.zili.android.musicfreeandroid.updater.store.UpdatePreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {

    private fun newInfo(version: String, code: Long) = UpdateInfo(
        schemaVersion = 1,
        version = version,
        versionCode = code,
        releasedAt = "2026-05-13T18:00:00Z",
        download = listOf("https://example.com/a.apk"),
        size = 1,
        sha256 = "x",
        changeLog = emptyList(),
        releaseNotesUrl = "https://example.com/notes",
    )

    private fun mockPrefs(skip: String? = null): UpdatePreferences = mockk(relaxed = true) {
        coEvery { getSkipVersion() } returns skip
    }

    @Test
    fun `up to date when remote not newer`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0")
        checker.checkOnLaunch()
        advanceUntilIdle()
        assertTrue(checker.state.value is UpdateState.UpToDate)
    }

    @Test
    fun `available when remote newer and not skipped`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0")
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(false, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `marks skipped when remote version equals skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val prefs = mockPrefs(skip = "1.2.3")
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0")
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(true, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `manual check ignores skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val prefs = mockPrefs(skip = "1.2.3")
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0")
        checker.checkManually()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(false, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `failed when client returns null`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns null }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0")
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.Network, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `unsupported schema marked failed`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> {
            coEvery { fetchLatest() } returns newInfo("1.2.3", 10203).copy(schemaVersion = 99)
        }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0")
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.SchemaUnsupported, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `state flow emits Checking before result`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0")
        checker.state.test {
            assertTrue(awaitItem() is UpdateState.Idle)
            checker.checkOnLaunch()
            assertTrue(awaitItem() is UpdateState.Checking)
            assertTrue(awaitItem() is UpdateState.UpToDate)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.UpdateCheckerTest" --no-daemon`
Expected: FAIL — Unresolved reference: UpdateChecker。

- [ ] **Step 3：写实现**

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import com.zili.android.musicfreeandroid.updater.api.UpdateClient
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import com.zili.android.musicfreeandroid.updater.store.UpdatePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UpdateChecker(
    private val client: UpdateClient,
    private val prefs: UpdatePreferences,
    private val localCode: Long,
    private val localName: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val mutex = Mutex()

    fun checkOnLaunch() {
        check(respectSkip = true)
    }

    fun checkManually() {
        check(respectSkip = false)
    }

    private fun check(respectSkip: Boolean) {
        scope.launch {
            mutex.withLock {
                _state.value = UpdateState.Checking
                val info: UpdateInfo = client.fetchLatest()
                    ?: run {
                        _state.value = UpdateState.Failed(info = null, cause = UpdateError.Network)
                        return@withLock
                    }
                if (info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION) {
                    _state.value = UpdateState.Failed(info = info, cause = UpdateError.SchemaUnsupported)
                    return@withLock
                }
                val outcome = VersionCompare.compare(
                    localCode = localCode,
                    localName = localName,
                    remoteCode = info.versionCode,
                    remoteName = info.version,
                )
                when (outcome) {
                    VersionCompare.Outcome.UpToDate -> {
                        prefs.clearSkipVersion()
                        prefs.setLastCheckedAt(now())
                        _state.value = UpdateState.UpToDate(now())
                    }
                    VersionCompare.Outcome.Unsupported -> {
                        _state.value = UpdateState.Failed(info = info, cause = UpdateError.SchemaUnsupported)
                    }
                    VersionCompare.Outcome.NewerAvailable -> {
                        prefs.setLastCheckedAt(now())
                        prefs.setLastSeenVersion(info.version)
                        val skip = if (respectSkip) prefs.getSkipVersion() else null
                        val isSkipped = skip != null && skip == info.version
                        _state.value = UpdateState.Available(info = info, skipped = isSkipped)
                    }
                }
            }
        }
    }

    suspend fun markSkipped(info: UpdateInfo) {
        prefs.setSkipVersion(info.version)
        _state.value = UpdateState.Available(info = info, skipped = true)
    }

    fun transitionDownloading(info: UpdateInfo, progress: Float, bytes: Long, total: Long) {
        _state.value = UpdateState.Downloading(info, progress, bytes, total)
    }

    fun transitionReady(info: UpdateInfo, file: java.io.File) {
        _state.value = UpdateState.ReadyToInstall(info, file)
    }

    fun transitionFailed(info: UpdateInfo?, cause: UpdateError) {
        _state.value = UpdateState.Failed(info, cause)
    }

    fun transitionAvailable(info: UpdateInfo, skipped: Boolean) {
        _state.value = UpdateState.Available(info, skipped)
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.UpdateCheckerTest" --no-daemon`
Expected: 7 passed。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateChecker.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/UpdateCheckerTest.kt
git commit -m "feat(updater): UpdateChecker 状态机"
```

---

## Task 9：`ApkDownloader`（含 sha256 校验、取消、size 校验）

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/ApkDownloader.kt`
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloader.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloaderTest.kt`

- [ ] **Step 1：先写测试**

```kotlin
package com.zili.android.musicfreeandroid.updater.downloader

import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OkHttpApkDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private lateinit var cacheDir: File
    private lateinit var downloader: OkHttpApkDownloader

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        cacheDir = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "updates",
        ).apply { mkdirs() }
        downloader = OkHttpApkDownloader(http = http, cacheRoot = { cacheDir })
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun makeInfo(url: String, body: ByteArray, sha: String): UpdateInfo = UpdateInfo(
        schemaVersion = 1,
        version = "1.2.3",
        versionCode = 10203,
        releasedAt = "2026-05-13T18:00:00Z",
        download = listOf(url),
        size = body.size.toLong(),
        sha256 = sha,
        changeLog = emptyList(),
        releaseNotesUrl = "https://example.com/notes",
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `successful download writes file and reports progress`() = runTest {
        val body = ByteArray(4096) { 0x42 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = makeInfo(server.url("/app.apk").toString(), body, sha256Hex(body))

        val progresses = mutableListOf<Float>()
        val result = downloader.download(info) { _, _, fraction -> progresses.add(fraction) }

        assertTrue(result is ApkDownloader.Result.Success)
        val file = (result as ApkDownloader.Result.Success).apkFile
        assertTrue(file.exists())
        assertEquals(body.size.toLong(), file.length())
        assertTrue(progresses.last() in 0.99f..1.0f)
        assertFalse(File(file.parentFile, "${file.name}.part").exists())
    }

    @Test
    fun `sha256 mismatch deletes file and returns mismatch`() = runTest {
        val body = ByteArray(2048) { 0x21 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = makeInfo(server.url("/app.apk").toString(), body, sha = "deadbeef")

        val result = downloader.download(info) { _, _, _ -> }

        assertTrue(result is ApkDownloader.Result.Failure)
        assertEquals(UpdateError.Sha256Mismatch, (result as ApkDownloader.Result.Failure).cause)
        assertFalse(File(cacheDir, "musicfree-${info.versionCode}.apk.part").exists())
        assertFalse(File(cacheDir, "musicfree-${info.versionCode}.apk").exists())
    }

    @Test
    fun `content length mismatch returns size mismatch`() = runTest {
        val body = ByteArray(1024) { 0x33 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = makeInfo(server.url("/app.apk").toString(), body, sha256Hex(body))
            .copy(size = 9999L)

        val result = downloader.download(info) { _, _, _ -> }

        assertEquals(UpdateError.SizeMismatch, (result as ApkDownloader.Result.Failure).cause)
    }

    @Test
    fun `falls back to next mirror when first returns 500`() = runTest {
        val body = ByteArray(512) { 0x11 }
        val sha = sha256Hex(body)
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val info = UpdateInfo(
            schemaVersion = 1, version = "1.2.3", versionCode = 10203,
            releasedAt = "2026-05-13T18:00:00Z",
            download = listOf(
                server.url("/dead.apk").toString(),
                server.url("/live.apk").toString(),
            ),
            size = body.size.toLong(), sha256 = sha,
            changeLog = emptyList(), releaseNotesUrl = "https://example.com/notes",
        )

        val result = downloader.download(info) { _, _, _ -> }
        assertTrue(result is ApkDownloader.Result.Success)
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.OkHttpApkDownloaderTest" --no-daemon`
Expected: FAIL — Unresolved reference: ApkDownloader / OkHttpApkDownloader。

- [ ] **Step 3：定义接口**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/ApkDownloader.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.downloader

import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import java.io.File

interface ApkDownloader {

    sealed interface Result {
        data class Success(val apkFile: File) : Result
        data class Failure(val cause: UpdateError) : Result
    }

    /**
     * progress 回调签名 (bytes, total, fraction)；fraction ∈ [0,1]，total≤0 时 fraction 取 0。
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (bytes: Long, total: Long, fraction: Float) -> Unit,
    ): Result

    fun cancel()
}
```

- [ ] **Step 4：写实现**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloader.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.downloader

import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class OkHttpApkDownloader @Inject constructor(
    private val http: OkHttpClient,
    private val cacheRoot: () -> File,
) : ApkDownloader {

    private val currentCall = AtomicReference<Call?>(null)

    override suspend fun download(
        info: UpdateInfo,
        onProgress: (Long, Long, Float) -> Unit,
    ): ApkDownloader.Result = withContext(Dispatchers.IO) {
        val dir = cacheRoot().apply { mkdirs() }
        val finalFile = File(dir, "musicfree-${info.versionCode}.apk")
        val partFile = File(dir, "musicfree-${info.versionCode}.apk.part")
        partFile.delete()
        finalFile.delete()

        for ((index, url) in info.download.withIndex()) {
            val outcome = tryDownload(info = info, url = url, target = partFile, onProgress = onProgress)
            when (outcome) {
                is StepOutcome.Ok -> {
                    if (!partFile.renameTo(finalFile)) {
                        partFile.delete()
                        return@withContext ApkDownloader.Result.Failure(UpdateError.Network)
                    }
                    return@withContext ApkDownloader.Result.Success(finalFile)
                }
                is StepOutcome.HardFail -> {
                    partFile.delete()
                    return@withContext ApkDownloader.Result.Failure(outcome.cause)
                }
                is StepOutcome.SoftFail -> {
                    partFile.delete()
                    if (index == info.download.lastIndex) {
                        return@withContext ApkDownloader.Result.Failure(UpdateError.Network)
                    }
                    // try next mirror
                }
                is StepOutcome.Canceled -> {
                    partFile.delete()
                    return@withContext ApkDownloader.Result.Failure(UpdateError.Canceled)
                }
            }
        }
        ApkDownloader.Result.Failure(UpdateError.Network)
    }

    override fun cancel() {
        currentCall.getAndSet(null)?.cancel()
    }

    private fun tryDownload(
        info: UpdateInfo,
        url: String,
        target: File,
        onProgress: (Long, Long, Float) -> Unit,
    ): StepOutcome {
        val request = Request.Builder().url(url).get().build()
        val call = http.newCall(request)
        currentCall.set(call)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return StepOutcome.SoftFail
                val body = response.body ?: return StepOutcome.SoftFail
                val advertised = body.contentLength()
                if (advertised >= 0 && advertised != info.size) {
                    return StepOutcome.HardFail(UpdateError.SizeMismatch)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            written += n
                            val fraction = if (info.size > 0) (written.toFloat() / info.size) else 0f
                            onProgress(written, info.size, fraction.coerceIn(0f, 1f))
                        }
                    }
                }
                if (written != info.size) return StepOutcome.HardFail(UpdateError.SizeMismatch)
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    return StepOutcome.HardFail(UpdateError.Sha256Mismatch)
                }
                StepOutcome.Ok
            }
        } catch (t: java.io.IOException) {
            if (call.isCanceled()) StepOutcome.Canceled else StepOutcome.SoftFail
        } finally {
            currentCall.compareAndSet(call, null)
        }
    }

    private sealed interface StepOutcome {
        data object Ok : StepOutcome
        data object SoftFail : StepOutcome
        data class HardFail(val cause: UpdateError) : StepOutcome
        data object Canceled : StepOutcome
    }
}
```

- [ ] **Step 5：跑测试确认通过**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.OkHttpApkDownloaderTest" --no-daemon`
Expected: 4 passed。

- [ ] **Step 6：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/downloader
git commit -m "feat(updater): OkHttpApkDownloader 下载 + sha256 + size 校验 + 多镜像兜底"
```

---

## Task 10：`ApkInstaller`（FileProvider 安装意图）

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/installer/ApkInstaller.kt`
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/installer/InstallIntents.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/installer/InstallIntentsTest.kt`

> 真正的 `startActivity` / `canRequestPackageInstalls` 不便在单测里跑；本任务用纯函数 `InstallIntents` 暴露 intent 构造逻辑，单测验证 URI 与 flags 拼装；`ApkInstaller` 自身只负责调用系统 API，留给 §3.4.4 集成路径覆盖。

- [ ] **Step 1：写纯函数测试**

```kotlin
package com.zili.android.musicfreeandroid.updater.installer

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstallIntentsTest {

    @Test
    fun `build install intent has correct action data flags`() {
        val uri = Uri.parse("content://com.zili.android.musicfreeandroid.updater-files/updates/x.apk")
        val intent = InstallIntents.installApk(uri)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `build unknown sources intent points to package settings`() {
        val intent = InstallIntents.manageUnknownAppSources("com.zili.android.musicfreeandroid")
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals(Uri.parse("package:com.zili.android.musicfreeandroid"), intent.data)
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.InstallIntentsTest" --no-daemon`
Expected: FAIL — Unresolved reference: InstallIntents。

- [ ] **Step 3：写实现**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/installer/InstallIntents.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.installer

import android.content.Intent
import android.net.Uri
import android.provider.Settings

object InstallIntents {

    fun installApk(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun manageUnknownAppSources(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
```

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/installer/ApkInstaller.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.installer

import android.content.Context
import androidx.core.content.FileProvider
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstaller @Inject constructor(
    private val context: Context,
) {
    sealed interface InstallResult {
        data object Started : InstallResult
        data class Blocked(val cause: UpdateError) : InstallResult
    }

    fun install(apkFile: File): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                InstallIntents.manageUnknownAppSources(context.packageName),
            )
            return InstallResult.Blocked(UpdateError.InstallBlocked)
        }
        val authority = "${context.packageName}.updater-files"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        context.startActivity(InstallIntents.installApk(uri))
        return InstallResult.Started
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run: `./gradlew :updater:testDebugUnitTest --tests "*.InstallIntentsTest" --no-daemon`
Expected: 2 passed。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/installer \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/installer
git commit -m "feat(updater): ApkInstaller 与 InstallIntents 拼装"
```

---

## Task 11：`UpdaterModule`（Hilt DI）

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterModule.kt`
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterMirrors.kt`

- [ ] **Step 1：常量**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterMirrors.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.di

object UpdaterMirrors {

    const val GITHUB_OWNER = "hanklzl"
    const val GITHUB_REPO = "MusicFreeAndroid"

    val VERSION_JSON_MIRRORS: List<String> = listOf(
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/gh-pages/release/version.json",
        "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@gh-pages/release/version.json",
    )
}
```

- [ ] **Step 2：Hilt module**

```kotlin
package com.zili.android.musicfreeandroid.updater.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.zili.android.musicfreeandroid.updater.api.OkHttpUpdateClient
import com.zili.android.musicfreeandroid.updater.api.UpdateClient
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.downloader.OkHttpApkDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdaterDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdaterHttp

private val Context.updaterDataStore: DataStore<Preferences> by preferencesDataStore(name = "updater_prefs")

@Module
@InstallIn(SingletonComponent::class)
object UpdaterModule {

    @Provides
    @Singleton
    @UpdaterDataStore
    fun provideUpdaterDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.updaterDataStore

    @Provides
    @Singleton
    @UpdaterHttp
    fun provideUpdaterOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideUpdateClient(@UpdaterHttp http: OkHttpClient): UpdateClient =
        OkHttpUpdateClient(http = http, mirrors = UpdaterMirrors.VERSION_JSON_MIRRORS)

    @Provides
    @Singleton
    fun provideApkDownloader(
        @UpdaterHttp http: OkHttpClient,
        @ApplicationContext context: Context,
    ): ApkDownloader =
        OkHttpApkDownloader(http = http, cacheRoot = { File(context.cacheDir, "updates") })
}
```

> 注：本 module 暴露 `UpdateClient` / `ApkDownloader` / `@UpdaterDataStore DataStore<Preferences>` 三个依赖。`UpdatePreferences` 已是 `@Singleton class … @Inject constructor(DataStore<Preferences>)`，需要 Hilt 知道注入哪一份 DataStore。因此 `UpdatePreferences` 的构造参数下一步加 `@UpdaterDataStore` 标注。

- [ ] **Step 3：给 `UpdatePreferences` 构造参数加 qualifier**

修改 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/store/UpdatePreferences.kt`，把构造函数从：

```kotlin
class UpdatePreferences @Inject constructor(
    private val store: DataStore<Preferences>,
)
```

改为：

```kotlin
import com.zili.android.musicfreeandroid.updater.di.UpdaterDataStore
//...
class UpdatePreferences @Inject constructor(
    @UpdaterDataStore private val store: DataStore<Preferences>,
)
```

`UpdatePreferencesTest` 不受影响（绕过 Hilt，直接构造）。

- [ ] **Step 4：编译验证**

Run: `./gradlew :updater:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

Run: `./gradlew :updater:testDebugUnitTest --no-daemon`
Expected: all green。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/di \
        updater/src/main/java/com/zili/android/musicfreeandroid/updater/store/UpdatePreferences.kt
git commit -m "feat(updater): Hilt 装配 UpdateClient/ApkDownloader/DataStore"
```

---

## Task 12：UpdateChecker 工厂与生命周期（Hilt + Application Scope）

**Files:**
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterModule.kt`
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/bootstrap/UpdateCheckCoordinator.kt`

> `UpdateChecker` 当前无法被 Hilt 直接 `@Inject`（含 `localCode/localName` 业务参数）。本任务在 `UpdaterModule` 里以 `@Provides @Singleton` 工厂方式构造，参数从 `BuildConfig.VERSION_CODE / VERSION_NAME` 经 `:app` 注入的 `LocalAppVersion` 接口传入；并新增 `UpdateCheckCoordinator` 暴露 `.start()`，与既有 `*Coordinator` 风格对齐。

- [ ] **Step 1：定义本地版本号 SPI**

新增 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/bootstrap/LocalAppVersion.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.bootstrap

interface LocalAppVersion {
    val versionCode: Long
    val versionName: String
    val isDebugBuild: Boolean
}
```

- [ ] **Step 2：UpdateCheckCoordinator**

`updater/src/main/java/com/zili/android/musicfreeandroid/updater/bootstrap/UpdateCheckCoordinator.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.bootstrap

import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckCoordinator @Inject constructor(
    private val checker: UpdateChecker,
    private val localAppVersion: LocalAppVersion,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (localAppVersion.isDebugBuild) return
        scope.launch { checker.checkOnLaunch() }
    }
}
```

- [ ] **Step 3：扩展 `UpdaterModule` 提供 UpdateChecker**

在 `UpdaterModule` 末尾追加：

```kotlin
import com.zili.android.musicfreeandroid.updater.bootstrap.LocalAppVersion
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.store.UpdatePreferences

@Provides
@Singleton
fun provideUpdateChecker(
    client: UpdateClient,
    prefs: UpdatePreferences,
    localAppVersion: LocalAppVersion,
): UpdateChecker = UpdateChecker(
    client = client,
    prefs = prefs,
    localCode = localAppVersion.versionCode,
    localName = localAppVersion.versionName,
)
```

- [ ] **Step 4：编译验证**

Run: `./gradlew :updater:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/bootstrap \
        updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterModule.kt
git commit -m "feat(updater): UpdateCheckCoordinator 启动入口与本地版本号 SPI"
```

---

## Task 13：在 `:app` 接入 `:updater` —— manifest / 权限 / FileProvider

**Files:**
- Modify: `app/build.gradle.kts`（在 modules 依赖块添加 `implementation(project(":updater"))`）
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/updater_file_paths.xml`

- [ ] **Step 1：依赖 `:updater`**

在 `app/build.gradle.kts` 的 `dependencies { ... }` 块的 "// Modules" 节追加：

```kotlin
implementation(project(":updater"))
```

- [ ] **Step 2：AndroidManifest 添加权限与 FileProvider**

修改 `app/src/main/AndroidManifest.xml`。

`<manifest>` 内部顶部、与现有 `<uses-permission>` 同级处追加：

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

在 `<application>` 内、紧邻现有 `<provider … android:authorities="${applicationId}.feedback-files" …>` 之后追加：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.updater-files"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/updater_file_paths" />
</provider>
```

- [ ] **Step 3：`updater_file_paths.xml`**

`app/src/main/res/xml/updater_file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path
        name="updates"
        path="updates/" />
</paths>
```

- [ ] **Step 4：编译验证（Debug）**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。manifest merger report 包含新 FileProvider。

- [ ] **Step 5：commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml/updater_file_paths.xml
git commit -m "feat(app): 接入 updater 模块 + FileProvider + 安装权限"
```

---

## Task 14：在 `MusicFreeApplication` 拉起 UpdateCheckCoordinator + 注入 LocalAppVersion

**Files:**
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/AppLocalAppVersion.kt`
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/di/AppUpdaterBindingModule.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt`

- [ ] **Step 1：本地版本号实现**

`AppLocalAppVersion.kt`：

```kotlin
package com.zili.android.musicfreeandroid.bootstrap

import com.zili.android.musicfreeandroid.BuildConfig
import com.zili.android.musicfreeandroid.updater.bootstrap.LocalAppVersion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalAppVersion @Inject constructor() : LocalAppVersion {
    override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()
    override val versionName: String = BuildConfig.VERSION_NAME
    override val isDebugBuild: Boolean = BuildConfig.DEBUG
}
```

- [ ] **Step 2：Hilt 绑定**

`AppUpdaterBindingModule.kt`：

```kotlin
package com.zili.android.musicfreeandroid.di

import com.zili.android.musicfreeandroid.bootstrap.AppLocalAppVersion
import com.zili.android.musicfreeandroid.updater.bootstrap.LocalAppVersion
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdaterBindingModule {

    @Binds
    @Singleton
    abstract fun bindLocalAppVersion(impl: AppLocalAppVersion): LocalAppVersion
}
```

- [ ] **Step 3：Application onCreate 注入 + 启动**

修改 `MusicFreeApplication.kt`，在 `@Inject lateinit var playbackStartupCoordinator…` 下追加：

```kotlin
@Inject lateinit var updateCheckCoordinator:
    com.zili.android.musicfreeandroid.updater.bootstrap.UpdateCheckCoordinator
```

并在 `onCreate()` 内、`playbackStartupCoordinator.start()` 之后追加：

```kotlin
updateCheckCoordinator.start()
```

- [ ] **Step 4：编译验证（Debug）**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5：commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/AppLocalAppVersion.kt \
        app/src/main/java/com/zili/android/musicfreeandroid/di/AppUpdaterBindingModule.kt \
        app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt
git commit -m "feat(app): MusicFreeApplication 启动 UpdateCheckCoordinator"
```

---

## Task 15：启动 Dialog + 下载进度 Dialog（Compose）

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogs.kt`
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogHost.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`（接入 `UpdateDialogHost`）

> 测试：Compose UI 测试在该项目较少，且本节核心是 wire-up；不强制写 UI 测试。`UpdateChecker.transitionDownloading/Ready/Failed/Available` 已有单测覆盖（来自 Task 8 的状态转移），dialog 行为通过运行态验收。

- [ ] **Step 1：dialog Composable**

`UpdateDialogs.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo

@Composable
fun AvailableUpdateDialog(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                info.changeLog.take(8).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("下载并安装") } },
        dismissButton = {
            Column {
                TextButton(onClick = onSkip) { Text("跳过此版本") }
                TextButton(onClick = onDismiss) { Text("稍后再说") }
            }
        },
    )
}

@Composable
fun DownloadingDialog(
    info: UpdateInfo,
    bytes: Long,
    total: Long,
    fraction: Float,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在下载 v${info.version}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "${bytes / 1024} KB / ${total / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
fun ReadyToInstallDialog(
    info: UpdateInfo,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("下载完成 v${info.version}") },
        text = { Text("立即安装新版本？") },
        confirmButton = { TextButton(onClick = onInstall) { Text("立即安装") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("稍后") } },
    )
}

@Composable
fun InstallBlockedDialog(
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无法安装") },
        text = { Text("系统未允许本应用安装未知来源应用。请在系统设置中授权后重试。") },
        confirmButton = { TextButton(onClick = onGoSettings) { Text("前往设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

- [ ] **Step 2：UpdateDialogHost 串联状态机与三个 dialog**

`UpdateDialogHost.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.checker.UpdateState
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.installer.InstallIntents
import kotlinx.coroutines.launch

@Composable
fun UpdateDialogHost(
    checker: UpdateChecker,
    downloader: ApkDownloader,
    installer: ApkInstaller,
) {
    val context = LocalContext.current
    val state by checker.state.collectAsState()
    val scope = rememberCoroutineScope()

    // 当首次进入 Available(skipped=false) 时显示 dialog；用户关闭后再次切换到 Available 不重弹（同一会话）
    var dismissedAvailable by remember { mutableStateOf(false) }

    when (val s = state) {
        is UpdateState.Available -> {
            if (!s.skipped && !dismissedAvailable) {
                AvailableUpdateDialog(
                    info = s.info,
                    onDownload = {
                        dismissedAvailable = true
                        scope.launch {
                            checker.transitionDownloading(s.info, 0f, 0L, s.info.size)
                            val result = downloader.download(s.info) { bytes, total, fraction ->
                                checker.transitionDownloading(s.info, fraction, bytes, total)
                            }
                            when (result) {
                                is ApkDownloader.Result.Success -> checker.transitionReady(s.info, result.apkFile)
                                is ApkDownloader.Result.Failure -> {
                                    if (result.cause == UpdateError.Canceled) {
                                        checker.transitionAvailable(s.info, skipped = false)
                                    } else {
                                        checker.transitionFailed(s.info, result.cause)
                                    }
                                }
                            }
                        }
                    },
                    onSkip = {
                        dismissedAvailable = true
                        scope.launch { checker.markSkipped(s.info) }
                    },
                    onDismiss = { dismissedAvailable = true },
                )
            }
        }
        is UpdateState.Downloading -> {
            DownloadingDialog(
                info = s.info,
                bytes = s.bytes,
                total = s.total,
                fraction = s.progress,
                onCancel = {
                    downloader.cancel()
                    checker.transitionAvailable(s.info, skipped = false)
                },
            )
        }
        is UpdateState.ReadyToInstall -> {
            ReadyToInstallDialog(
                info = s.info,
                onInstall = {
                    val result = installer.install(s.apkFile)
                    if (result is ApkInstaller.InstallResult.Blocked) {
                        checker.transitionFailed(s.info, result.cause)
                    }
                },
                onCancel = { checker.transitionAvailable(s.info, skipped = false) },
            )
        }
        is UpdateState.Failed -> {
            if (s.cause == UpdateError.InstallBlocked) {
                val info = s.info
                InstallBlockedDialog(
                    onGoSettings = {
                        context.startActivity(InstallIntents.manageUnknownAppSources(context.packageName))
                    },
                    onDismiss = {
                        if (info != null) checker.transitionAvailable(info, skipped = false)
                    },
                )
            }
        }
        else -> Unit
    }

    LaunchedEffect(state) {
        // 退出 Available 后允许下次再弹（用于设置页主动检查路径）
        if (state !is UpdateState.Available) dismissedAvailable = false
    }
}
```

- [ ] **Step 3：在 MainActivity 接入**

修改 `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`，在其根 Composable（通常是 `setContent { MusicFreeAndroidTheme { … } }` 内部）顶层调用 `UpdateDialogHost`：

```kotlin
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.ui.UpdateDialogHost
import javax.inject.Inject

// 类字段（位于 @AndroidEntryPoint 标注的 Activity 内）：
@Inject lateinit var updateChecker: UpdateChecker
@Inject lateinit var apkDownloader: ApkDownloader
@Inject lateinit var apkInstaller: ApkInstaller

// setContent 内、AppNavHost 等之外、最外层 Box / Column 内追加：
UpdateDialogHost(checker = updateChecker, downloader = apkDownloader, installer = apkInstaller)
```

- [ ] **Step 4：编译验证**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5：commit**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui \
        app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt
git commit -m "feat(updater): 启动/下载/安装 dialog 接入 MainActivity"
```

---

## Task 16：设置页"检查更新"行 + 红点

**Files:**
- Modify: `feature/settings/src/main/java/.../<SettingsScreen>.kt`（路径在 Step 1 探明）

> 实施前先 `find feature/settings/src/main -name "*Screen*.kt" -o -name "*Settings*.kt"` 找到具体文件名；项目惯例是 "AboutSection / CheckUpdateRow" 这种细粒度 Composable。

- [ ] **Step 1：定位现有"关于" / 设置入口**

Run:
```bash
grep -rln "检查更新\|CheckUpdate\|关于\|VersionName" feature/settings/src/main --include="*.kt"
```

如果存在"关于"分节但未含"检查更新"行：在该 Composable 内新增一行。如果完全没有"检查更新"行：在最贴近"关于"分节的位置插入。

- [ ] **Step 2：注入 `UpdateChecker` 并写"检查更新"行**

在选定的 Composable 文件最上方 `import`：

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.checker.UpdateState
```

新增轻量 ViewModel `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/CheckUpdateViewModel.kt`：

```kotlin
package com.zili.android.musicfreeandroid.feature.settings

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CheckUpdateViewModel @Inject constructor(
    val checker: UpdateChecker,
) : ViewModel() {
    fun checkNow() {
        checker.checkManually()
    }
}
```

> 注：`:feature:settings/build.gradle.kts` 已依赖 `:updater`？若未依赖，本 step 同步加 `implementation(project(":updater"))`。

并在设置 Composable 内（"关于"分节附近）新增：

```kotlin
@Composable
fun CheckUpdateRow(viewModel: CheckUpdateViewModel = hiltViewModel()) {
    val state by viewModel.checker.state.collectAsState()
    val hasRedDot = state.hasUnreadAvailableUpdate
    val (title, subtitle) = when (state) {
        is UpdateState.Available -> "检查更新" to "v${(state as UpdateState.Available).info.version} 可用"
        is UpdateState.Checking -> "检查更新" to "检查中…"
        is UpdateState.UpToDate -> "检查更新" to "已是最新版本"
        is UpdateState.Failed -> "检查更新" to "检查失败，点击重试"
        else -> "检查更新" to "当前版本 ${com.zili.android.musicfreeandroid.BuildConfig.VERSION_NAME}"
    }
    // 复用现有 SettingsRow / ListItem；红点用 Material Badge / 自绘小圆点
    SettingsRow(
        title = title,
        subtitle = subtitle,
        trailing = if (hasRedDot) { { RedDot() } } else null,
        onClick = { viewModel.checkNow() },
    )
}
```

如果文件中已有 `SettingsRow` / `RedDot` 等约定 Composable，沿用其签名；否则添加最小自绘红点：

```kotlin
@Composable
private fun RedDot() {
    androidx.compose.foundation.Canvas(modifier = androidx.compose.ui.Modifier.size(8.dp)) {
        drawCircle(color = androidx.compose.ui.graphics.Color(0xFFE53935))
    }
}
```

- [ ] **Step 3：在主设置 Composable 引用 `CheckUpdateRow`**

在原"关于"分节附近调用：

```kotlin
CheckUpdateRow()
```

- [ ] **Step 4：模块依赖检查**

```bash
grep "project(\":updater\")" feature/settings/build.gradle.kts || \
    echo "需要在 feature/settings/build.gradle.kts 的 dependencies 加 implementation(project(\":updater\"))"
```

按提示补依赖。

- [ ] **Step 5：编译验证**

Run: `./gradlew :feature:settings:assembleDebug :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6：commit**

```bash
git add feature/settings
git commit -m "feat(settings): 接入更新检查行与红点"
```

---

## Task 17：抽屉「设置」入口红点

**Files:**
- Modify: `feature/home/src/main/.../HomeDrawer.kt`（或类似文件，Step 1 探明）

- [ ] **Step 1：定位 HomeDrawer**

```bash
grep -rln "Drawer\|抽屉" feature/home/src/main --include="*.kt"
```

- [ ] **Step 2：注入更新状态并在"设置"行末加红点**

在 HomeDrawer Composable 文件追加 import：

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.zili.android.musicfreeandroid.feature.settings.CheckUpdateViewModel
```

并在 Drawer 顶层（与"设置"行同级）：

```kotlin
val updateVm: CheckUpdateViewModel = hiltViewModel()
val updateState by updateVm.checker.state.collectAsState()
val hasUpdateRedDot = updateState.hasUnreadAvailableUpdate
```

在"设置"行的 trailing 槽位接入 `if (hasUpdateRedDot) RedDot()`。

> `:feature:home` 需要新增依赖 `implementation(project(":feature:settings"))` 与 `implementation(project(":updater"))` —— 如已依赖 `:feature:settings` 则只补 `:updater`。检查并补依赖：

```bash
grep "project(\":feature:settings\")\|project(\":updater\")" feature/home/build.gradle.kts
```

如缺，加到 `dependencies { ... }`。

> 备选方案（更轻）：把 `hasUnreadAvailableUpdate` 抽到 `:updater` 一个共享 SPI（如 `UpdateBadgeProvider`），让 `:feature:home` 直接依赖 `:updater` 而无需依赖 `:feature:settings`。本任务采用 `:feature:home → :updater` 直连模式，避免引入新的 SPI 类。

- [ ] **Step 3：编译验证**

Run: `./gradlew :feature:home:assembleDebug :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4：commit**

```bash
git add feature/home
git commit -m "feat(home): 抽屉设置入口接入更新红点"
```

---

## Task 18：`scripts/release/generate-notes.sh`

**Files:**
- Create: `scripts/release/generate-notes.sh`
- Create: `scripts/release/lib/commit-classify.awk`

- [ ] **Step 1：commit 分类 awk 脚本**

`scripts/release/lib/commit-classify.awk`：

```awk
# Input lines: "<full-sha>\t<commit subject>"
# Output blocks per category, separated by category headers
BEGIN {
    FS = "\t"
    cats[1] = "feat:#### 新功能"
    cats[2] = "fix:#### 修复"
    cats[3] = "perf:#### 性能"
    cats[4] = "refactor:#### 重构"
    cats[5] = "docs:#### 文档"
    cats[6] = "test:#### 测试"
    cats[7] = "chore:#### 杂项"
    cats[8] = "merge:#### 合并"
    n = 8
}
{
    matched = 0
    for (i = 1; i <= n; i++) {
        split(cats[i], pair, ":")
        prefix = pair[1]
        if (match($2, "^" prefix "(\\(|:)")) {
            short = substr($1, 1, 7)
            bucket[i] = bucket[i] "- " $2 " (" short ")\n"
            matched = 1
            break
        }
    }
    if (!matched) {
        short = substr($1, 1, 7)
        bucket[0] = bucket[0] "- " $2 " (" short ")\n"
    }
}
END {
    for (i = 1; i <= n; i++) {
        if (bucket[i] != "") {
            split(cats[i], pair, ":")
            print pair[2]
            printf "%s", bucket[i]
            print ""
        }
    }
    if (bucket[0] != "") {
        print "#### 其它"
        printf "%s", bucket[0]
    }
}
```

- [ ] **Step 2：主脚本**

`scripts/release/generate-notes.sh`：

```bash
#!/usr/bin/env bash
# Usage: generate-notes.sh <prev-ref> <head-ref>
# Output: markdown release notes on stdout
# Behavior:
#  - reads commits in <prev-ref>..<head-ref>
#  - classifies by conventional commit prefix
#  - if ANTHROPIC_API_KEY is set, queries Claude for a Chinese summary (<=200 chars)
#  - on any LLM failure, omits the summary section and continues (exit 0)
set -euo pipefail

PREV=${1:?"prev ref required"}
HEAD_REF=${2:?"head ref required"}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
LIB="$ROOT/scripts/release/lib"

# Date heading: head ref's commit date, ISO date
RELEASE_DATE=$(git -C "$ROOT" show -s --format=%cs "$HEAD_REF")
TAG_LABEL=$HEAD_REF
if [[ "$HEAD_REF" == "HEAD" ]]; then
    TAG_LABEL="HEAD"
fi

# Collect commit lines
COMMITS=$(git -C "$ROOT" log --pretty=format:'%H%x09%s' "$PREV..$HEAD_REF" || true)

CLASSIFIED=""
if [[ -n "$COMMITS" ]]; then
    CLASSIFIED=$(printf '%s\n' "$COMMITS" | awk -f "$LIB/commit-classify.awk")
fi

SUMMARY=""
if [[ -n "${ANTHROPIC_API_KEY:-}" && -n "$COMMITS" ]]; then
    # Compose user prompt
    USER_PROMPT="基于以下 commit 列表，生成不超过 200 字的中文版本亮点摘要，只点出对用户有感知的变化，不要逐条复述：\n\n${COMMITS}"
    PAYLOAD=$(jq -n --arg model "claude-haiku-4-5" --arg prompt "$USER_PROMPT" '
        {
            model: $model,
            max_tokens: 600,
            system: "你是技术 release notes 编辑。基于给定 commit 列表写一段不超过 200 字的中文版本亮点摘要，不重复列出每条 commit，只点出对用户最有感知的变化。",
            messages: [ { role: "user", content: $prompt } ]
        }
    ')
    if RESPONSE=$(curl -sS --max-time 30 \
        -H "x-api-key: $ANTHROPIC_API_KEY" \
        -H "anthropic-version: 2023-06-01" \
        -H "content-type: application/json" \
        --data "$PAYLOAD" \
        https://api.anthropic.com/v1/messages); then
        SUMMARY=$(printf '%s' "$RESPONSE" | jq -r '.content[0].text // ""')
    else
        echo "::warning::Claude API call failed" >&2
    fi
fi

# Emit markdown
printf '## [%s] - %s\n\n' "$TAG_LABEL" "$RELEASE_DATE"
if [[ -n "$SUMMARY" ]]; then
    printf '%s\n\n' "$SUMMARY"
fi
printf '### 变更详情\n\n'
if [[ -n "$CLASSIFIED" ]]; then
    printf '%s\n' "$CLASSIFIED"
else
    printf '_本次发布无新提交。_\n'
fi
```

- [ ] **Step 3：chmod + 干跑**

```bash
chmod +x scripts/release/generate-notes.sh
# 找近 5 个 commit 范围干跑（不调 LLM）
unset ANTHROPIC_API_KEY
bash scripts/release/generate-notes.sh HEAD~5 HEAD | tee /tmp/notes-dry.md
```

期望：`/tmp/notes-dry.md` 包含 markdown 标题、`### 变更详情` 与至少一类 commit。

- [ ] **Step 4：commit**

```bash
git add scripts/release/generate-notes.sh scripts/release/lib/commit-classify.awk
git commit -m "feat(release): generate-notes.sh commit 分类 + Claude 摘要 + fallback"
```

---

## Task 19：`scripts/release/prepend-changelog.sh`

**Files:**
- Create: `scripts/release/prepend-changelog.sh`

- [ ] **Step 1：脚本**

`scripts/release/prepend-changelog.sh`：

```bash
#!/usr/bin/env bash
# Usage: prepend-changelog.sh <notes-file> <tag> [--dry-run]
# Inserts notes content into CHANGELOG.md right after the "<!-- next-release -->" marker.
# With --dry-run, writes resulting file to stdout instead of modifying CHANGELOG.md.
set -euo pipefail

NOTES_FILE=${1:?"notes file required"}
TAG=${2:?"tag required"}
MODE=${3:-write}

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CHANGELOG="$ROOT/CHANGELOG.md"
MARKER="<!-- next-release -->"

if [[ ! -f "$CHANGELOG" ]]; then
    echo "::error::CHANGELOG.md not found at $CHANGELOG" >&2
    exit 1
fi
if ! grep -Fq "$MARKER" "$CHANGELOG"; then
    echo "::error::Marker '$MARKER' not present in CHANGELOG.md" >&2
    exit 1
fi
if [[ ! -f "$NOTES_FILE" ]]; then
    echo "::error::Notes file $NOTES_FILE not found" >&2
    exit 1
fi

NOTES_CONTENT=$(cat "$NOTES_FILE")

OUTPUT=$(awk -v marker="$MARKER" -v notes="$NOTES_CONTENT" '
    BEGIN { inserted = 0 }
    {
        print
        if (!inserted && index($0, marker)) {
            print ""
            print notes
            inserted = 1
        }
    }
' "$CHANGELOG")

if [[ "$MODE" == "--dry-run" ]]; then
    printf '%s\n' "$OUTPUT"
else
    printf '%s\n' "$OUTPUT" > "$CHANGELOG"
fi
```

- [ ] **Step 2：chmod + 单元干跑**

```bash
chmod +x scripts/release/prepend-changelog.sh
# 干跑校验脚本本身（CHANGELOG.md 在 Task 22 才存在；先用 fixture）
mkdir -p /tmp/release-test && cd /tmp/release-test
cat > CHANGELOG.md <<'EOF'
# Changelog
<!-- next-release -->
## [Unreleased]
EOF
cat > /tmp/notes.md <<'EOF'
## [v0.0.1] - 2026-05-14

测试摘要。

### 变更详情
#### 新功能
- feat: dummy (deadbee)
EOF
bash ROOT/scripts/release/prepend-changelog.sh /tmp/notes.md v0.0.1 --dry-run | head -10
cd -
```

`ROOT` 处替换为仓库绝对路径。Expected：输出顺序为 `# Changelog` → 空行 → marker → 空行 → notes 内容 → `## [Unreleased]`。

- [ ] **Step 3：commit**

```bash
git add scripts/release/prepend-changelog.sh
git commit -m "feat(release): prepend-changelog.sh marker 之后插入新版本片段"
```

---

## Task 20：`scripts/release/build-version-json.sh`

**Files:**
- Create: `scripts/release/build-version-json.sh`

- [ ] **Step 1：脚本**

`scripts/release/build-version-json.sh`：

```bash
#!/usr/bin/env bash
# Usage: build-version-json.sh --version <semver> --version-code <int> --tag <vX.Y.Z>
#                              [--apk <path> | --sha256 <hex> --size <bytes>]
#                              --apk-name <filename> --notes <notes-md-file>
# Output: version.json on stdout
set -euo pipefail

VERSION="" VCODE="" TAG="" APK="" SHA="" SIZE="" APK_NAME="" NOTES=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --version)       VERSION=$2; shift 2 ;;
        --version-code)  VCODE=$2; shift 2 ;;
        --tag)           TAG=$2; shift 2 ;;
        --apk)           APK=$2; shift 2 ;;
        --sha256)        SHA=$2; shift 2 ;;
        --size)          SIZE=$2; shift 2 ;;
        --apk-name)      APK_NAME=$2; shift 2 ;;
        --notes)         NOTES=$2; shift 2 ;;
        *) echo "::error::unknown arg $1" >&2; exit 1 ;;
    esac
done

: "${VERSION:?version required}"
: "${VCODE:?version-code required}"
: "${TAG:?tag required}"
: "${APK_NAME:?apk-name required}"
: "${NOTES:?notes file required}"

if [[ -n "$APK" ]]; then
    SHA=$(sha256sum "$APK" | awk '{print $1}')
    SIZE=$(wc -c < "$APK")
fi
: "${SHA:?sha256 missing (provide --apk or --sha256)}"
: "${SIZE:?size missing (provide --apk or --size)}"

OWNER="hanklzl"
REPO="MusicFreeAndroid"
RELEASED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

DOWNLOAD_GH="https://github.com/$OWNER/$REPO/releases/download/$TAG/$APK_NAME"
DOWNLOAD_JSDELIVR="https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$TAG/release/$APK_NAME"
RELEASE_NOTES_URL="https://github.com/$OWNER/$REPO/releases/tag/$TAG"

# Extract changeLog lines: prefer the LLM summary block (everything between title and "### 变更详情")
CHANGELOG_LINES=$(awk '
    /^## \[/ { inSummary = 1; next }
    /^### 变更详情/ { inSummary = 0 }
    inSummary { print }
' "$NOTES" | sed -E 's/^\s+|\s+$//g' | awk 'NF' | head -n 8)

if [[ -z "$CHANGELOG_LINES" ]]; then
    # fallback: take first 8 commit subject lines
    CHANGELOG_LINES=$(grep -E '^- ' "$NOTES" | head -n 8 | sed -E 's/^- //; s/ \([a-f0-9]+\)$//')
fi

jq -n \
    --argjson schemaVersion 1 \
    --arg version "$VERSION" \
    --argjson versionCode "$VCODE" \
    --arg releasedAt "$RELEASED_AT" \
    --arg gh "$DOWNLOAD_GH" \
    --arg jd "$DOWNLOAD_JSDELIVR" \
    --argjson size "$SIZE" \
    --arg sha "$SHA" \
    --arg notes_url "$RELEASE_NOTES_URL" \
    --rawfile changelog <(printf '%s' "$CHANGELOG_LINES") \
    '
    {
        schemaVersion: $schemaVersion,
        version: $version,
        versionCode: $versionCode,
        releasedAt: $releasedAt,
        download: [$gh, $jd],
        size: $size,
        sha256: $sha,
        changeLog: ($changelog | split("\n") | map(select(length > 0))),
        releaseNotesUrl: $notes_url
    }'
```

- [ ] **Step 2：chmod + 干跑**

```bash
chmod +x scripts/release/build-version-json.sh
# 准备 fixture
echo "dummy" > /tmp/fake.apk
cat > /tmp/notes.md <<'EOF'
## [v1.2.3] - 2026-05-14

修复了播放器与插件的几处问题。

### 变更详情
#### 修复
- fix: x (abc1234)
EOF
bash scripts/release/build-version-json.sh \
    --version 1.2.3 \
    --version-code 10203 \
    --tag v1.2.3 \
    --apk /tmp/fake.apk \
    --apk-name MusicFreeAndroid-v1.2.3.apk \
    --notes /tmp/notes.md | jq .
```

Expected：合法 JSON，`changeLog` 包含一行"修复了播放器与插件的几处问题。"，`download` 两条 URL，`sha256` 与 `size` 与 `/tmp/fake.apk` 匹配。

- [ ] **Step 3：commit**

```bash
git add scripts/release/build-version-json.sh
git commit -m "feat(release): build-version-json.sh 拼装 gh-pages 元数据"
```

---

## Task 21：`scripts/release/preflight.sh`

**Files:**
- Create: `scripts/release/preflight.sh`

- [ ] **Step 1：脚本**

`scripts/release/preflight.sh`：

```bash
#!/usr/bin/env bash
# Usage: preflight.sh <vX.Y.Z>
# Local dry-run of CI release pipeline. Required to pass before pushing a real tag.
set -euo pipefail

TAG=${1:?"tag required, e.g. v1.2.3"}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

echo "[dry] Validate version consistency"
EXPECTED="${TAG#v}"
ACTUAL=$(awk -F= '/^versionName/{print $2}' version.properties | tr -d '[:space:]')
[[ "$EXPECTED" == "$ACTUAL" ]] || { echo "::error::tag $TAG vs versionName $ACTUAL mismatch"; exit 1; }
echo "OK: $TAG ↔ versionName=$ACTUAL"

echo "[dry] Build Release APK"
if [[ -z "${ANDROID_RELEASE_KEYSTORE_PATH:-}" ]]; then
    echo "::warning::ANDROID_RELEASE_KEYSTORE_PATH 未设置，跳过 Release 构建"
else
    ./gradlew clean :app:assembleRelease --no-daemon
    APK=app/build/outputs/apk/release/app-release.apk
    [[ -f "$APK" ]] || { echo "::error::APK not produced"; exit 1; }
fi

APK="${APK:-app/build/outputs/apk/release/app-release.apk}"
if [[ -f "$APK" ]]; then
    echo "[dry] Compute APK sha256 + size"
    sha256sum "$APK" | awk '{print "sha256="$1}'
    echo "size=$(wc -c < "$APK")"
fi

echo "[dry] Generate release notes"
PREV=$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD | tail -1)
bash scripts/release/generate-notes.sh "$PREV" HEAD > /tmp/preflight-notes.md
echo "Wrote /tmp/preflight-notes.md"

echo "[dry] Prepend CHANGELOG.md (dry-run)"
bash scripts/release/prepend-changelog.sh /tmp/preflight-notes.md "$TAG" --dry-run \
    | diff CHANGELOG.md - || true

if [[ -f "$APK" ]]; then
    echo "[dry] Build version.json"
    VCODE=$(awk -F= '/^versionCode/{print $2}' version.properties | tr -d '[:space:]')
    bash scripts/release/build-version-json.sh \
        --version "$EXPECTED" \
        --version-code "$VCODE" \
        --tag "$TAG" \
        --apk "$APK" \
        --apk-name "MusicFreeAndroid-$TAG.apk" \
        --notes /tmp/preflight-notes.md \
        > /tmp/preflight-version.json
    jq . /tmp/preflight-version.json > /dev/null
    echo "Wrote /tmp/preflight-version.json"
fi

echo "Preflight OK"
```

- [ ] **Step 2：chmod + 干跑**

```bash
chmod +x scripts/release/preflight.sh
# 不设 keystore env 干跑（会跳过 Build Release APK 与 version.json 步骤）
bash scripts/release/preflight.sh v1.0.0 || true
```

Expected: 输出至少 `Validate version consistency`、`Generate release notes`、`Prepend CHANGELOG.md (dry-run)` 三段；`Preflight OK` 结尾。

- [ ] **Step 3：commit**

```bash
git add scripts/release/preflight.sh
git commit -m "feat(release): preflight.sh 串联所有 dry-run step"
```

---

## Task 22：根目录 `CHANGELOG.md` 与 `RELEASE.md` 初始化 + `.gitignore`

**Files:**
- Create: `CHANGELOG.md`
- Create: `RELEASE.md`
- Modify: `.gitignore`

- [ ] **Step 1：`CHANGELOG.md`**

```markdown
# Changelog

本项目所有显著变更记录于此。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

<!-- next-release -->

## [Unreleased]

未发布的工作收录在 commit 历史中。
```

- [ ] **Step 2：`RELEASE.md`**

```markdown
# 发布流程

本仓库的发布流水线由 `.github/workflows/android-release-apk.yml` 驱动，对外只暴露一个触发点：**推送 `vX.Y.Z` tag 到 GitHub**。

## 一次性配置

### GitHub release environment secrets

在仓库 Settings → Environments → `release` 内配置：

| Secret | 用途 |
|---|---|
| `ANDROID_RELEASE_KEYSTORE_BASE64` | base64 编码的签名 keystore |
| `ANDROID_RELEASE_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_RELEASE_KEY_ALIAS` | key 别名 |
| `ANDROID_RELEASE_KEY_PASSWORD` | key 密码 |
| `LOGAN_AES_KEY` | 16 字节，Logan 日志加密 |
| `LOGAN_AES_IV` | 16 字节，Logan 日志 IV |
| `ANTHROPIC_API_KEY` | Claude API，用于 release notes 摘要；失败时回退到纯 commit 列表，不阻塞 |

### `gh-pages` 分支

首次 tag push 时 CI 自动 `git checkout --orphan gh-pages` 创建分支并写入 `release/version.json`。无需手工初始化。

### 版本号 versionCode 公式

`versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`。例：`v1.2.3` → `10203`。

## 日常发布步骤

1. 决定语义化版本号 vX.Y.Z。
2. 修改 `version.properties`：
   ```properties
   versionCode=10203
   versionName=1.2.3
   ```
3. **本地干跑 preflight**：
   ```bash
   bash scripts/release/preflight.sh v1.2.3
   ```
   通过后再继续；任何报错都不要 push。
4. `git add version.properties && git commit -m "chore(release): bump to v1.2.3"`
5. `git tag v1.2.3`
6. `git push origin main && git push origin v1.2.3`
7. 观察 [GitHub Actions](https://github.com/hanklzl/MusicFreeAndroid/actions) 完成；验证：
   - Release 已创建，notes 完整
   - `main` 上有 `docs(changelog): release v1.2.3 [skip ci]` 自动 commit
   - `gh-pages/release/version.json` 已更新
   - jsdelivr 镜像可拉：
     ```bash
     curl -I https://cdn.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json
     ```
8. 装一台测试机冷启动验证启动 dialog → 下载 → 安装链路（首次 release 时必须做）。

## 本地干跑 CI step

每条 step 与 `.github/workflows/android-release-apk.yml` 内的同名 step 一一对应，命名前缀 `[dry] `。所有命令在仓库根目录执行。

### `[dry] Validate version consistency`

```bash
TAG=v1.2.3 bash -c '
  expected="${TAG#v}"
  actual=$(awk -F= "/^versionName/{print \$2}" version.properties | tr -d "[:space:]")
  [ "$expected" = "$actual" ] || { echo "::error::tag $TAG vs versionName $actual mismatch"; exit 1; }
  echo "OK: $TAG ↔ versionName=$actual"
'
```

### `[dry] Build Release APK`

在本机配置一份**未入库** `.env.release.local`（`.gitignore` 已排除）：

```bash
export ANDROID_RELEASE_KEYSTORE_PATH=/abs/path/release.jks
export ANDROID_RELEASE_STORE_PASSWORD=...
export ANDROID_RELEASE_KEY_ALIAS=...
export ANDROID_RELEASE_KEY_PASSWORD=...
export LOGAN_AES_KEY=0123456789abcdef
export LOGAN_AES_IV=abcdef0123456789
```

```bash
source .env.release.local
./gradlew clean :app:assembleRelease --no-daemon
ls -lh app/build/outputs/apk/release/app-release.apk
```

### `[dry] Compute APK sha256 + size`

```bash
APK=app/build/outputs/apk/release/app-release.apk
sha256sum "$APK" | awk '{print $1}'
wc -c < "$APK"
```

### `[dry] Generate release notes`

```bash
PREV=$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD | tail -1)
CURR=HEAD
bash scripts/release/generate-notes.sh "$PREV" "$CURR" > /tmp/release_notes.md
less /tmp/release_notes.md
```

本地不愿调 LLM：`unset ANTHROPIC_API_KEY`，走 fallback。

### `[dry] Prepend CHANGELOG.md`

```bash
bash scripts/release/prepend-changelog.sh /tmp/release_notes.md vX.Y.Z --dry-run \
    | diff CHANGELOG.md -
```

`--dry-run` 输出"假如执行后的 CHANGELOG.md 全文"，与现状 diff 出新插入段。**本地不要去掉 `--dry-run` 真写文件**——这步只在 CI 内执行，避免与 CI 重复提交。

### `[dry] Build version.json`

```bash
bash scripts/release/build-version-json.sh \
    --version 1.2.3 \
    --version-code 10203 \
    --tag v1.2.3 \
    --apk "$APK" \
    --apk-name "MusicFreeAndroid-v1.2.3.apk" \
    --notes /tmp/release_notes.md \
    > /tmp/version.json
jq . /tmp/version.json   # 语法校验
```

### `[dry] Full pre-flight`

```bash
bash scripts/release/preflight.sh v1.2.3
```

脚本串调上述 6 个 step，任一非 0 即停。**push tag 前跑通 preflight 是硬性约束**。

### 不可本地干跑的 step

| Step | 原因 | 替代验证 |
|---|---|---|
| `gh release create` | 真创建会污染线上 release | 在 fork 上用 `--draft` 跑一次 |
| `git push origin main`（CHANGELOG） | 真 push 污染 main | dry-run diff 已足够 |
| `git push origin gh-pages` | 同上 | 本地切到 gh-pages 看文件结构即可 |

## 回滚

```bash
# 删 tag
git push origin :v1.2.3
git tag -d v1.2.3
# 删 release
gh release delete v1.2.3
# revert CHANGELOG commit
git revert <changelog-commit-sha>
git push origin main
# 删 gh-pages 对应 commit
git push --force-with-lease origin <gh-pages-prev-sha>:gh-pages
```

## 故障排查

| 现象 | 排查 |
|---|---|
| `Validate version consistency` 红色失败 | 校对 `version.properties` 与 tag |
| LLM 摘要为空 | 不阻塞 release；可手工编辑 `CHANGELOG.md` 补摘要 |
| CHANGELOG push 失败 | main 并发推送；按 workflow warning log 手工 cherry-pick |
| 客户端拉不到 `version.json` | 检查 `gh-pages` 分支；jsdelivr 缓存最多 12h；强制刷新 `https://purge.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json` |
| 用户装不上 | 检查 applicationId（release vs debug 不可覆盖）；用户系统设置「允许此应用安装未知来源」未开 |
| 弹窗"安装包校验失败" | sha256 不匹配；通常是 jsdelivr 缓存旧 APK，命令同上强刷 |
```

- [ ] **Step 3：`.gitignore` 追加**

在 `.gitignore` 末尾追加（注意保留尾部空行）：

```gitignore
.env.release.local
```

- [ ] **Step 4：commit**

```bash
git add CHANGELOG.md RELEASE.md .gitignore
git commit -m "docs(release): 根目录新增 RELEASE.md / CHANGELOG.md 与 .env 忽略"
```

---

## Task 23：改造 `.github/workflows/android-release-apk.yml`

**Files:**
- Modify: `.github/workflows/android-release-apk.yml`

- [ ] **Step 1：在 `build-release-apk` job 内加 `Validate version consistency` step**

在 `Checkout` step 之后、`Set up JDK 21` step 之前插入：

```yaml
      - name: Validate version consistency
        if: github.ref_type == 'tag'
        run: |
          expected="${GITHUB_REF_NAME#v}"
          actual=$(awk -F= '/^versionName/{print $2}' version.properties | tr -d '[:space:]')
          [ "$expected" = "$actual" ] || {
            echo "::error::tag $GITHUB_REF_NAME vs versionName $actual mismatch"
            exit 1
          }
          echo "OK: $GITHUB_REF_NAME ↔ versionName=$actual"
```

- [ ] **Step 2：在 `Name APK` step 之后插入 `Compute APK sha256 + size`，并把它声明为 job output**

将 `build-release-apk` 顶部的 `outputs` 块改写为：

```yaml
    outputs:
      apk-name: ${{ steps.name-apk.outputs.apk_name }}
      apk-sha256: ${{ steps.apk-meta.outputs.sha256 }}
      apk-size: ${{ steps.apk-meta.outputs.size }}
```

在 `Name APK` 之后插入：

```yaml
      - name: Compute APK sha256 + size
        id: apk-meta
        run: |
          apk="$RUNNER_TEMP/${{ steps.name-apk.outputs.apk_name }}"
          echo "sha256=$(sha256sum "$apk" | awk '{print $1}')" >> "$GITHUB_OUTPUT"
          echo "size=$(wc -c < "$apk")" >> "$GITHUB_OUTPUT"
```

- [ ] **Step 3：改造 `publish-github-release` job**

把整个 job 替换为：

```yaml
  publish-github-release:
    name: Publish GitHub Release
    needs: build-release-apk
    if: github.event_name == 'push' && github.ref_type == 'tag'
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout source
        uses: actions/checkout@v6
        with:
          fetch-depth: 0

      - name: Download Release APK artifact
        uses: actions/download-artifact@v7
        with:
          name: MusicFreeAndroid-release-apk
          path: release-apk

      - name: Generate release notes
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          prev=$(git describe --tags --abbrev=0 "${GITHUB_REF_NAME}^" 2>/dev/null \
                 || git rev-list --max-parents=0 HEAD | tail -1)
          bash scripts/release/generate-notes.sh "$prev" "$GITHUB_REF_NAME" > release_notes.md
          head -30 release_notes.md

      - name: Upload release notes artifact
        uses: actions/upload-artifact@v7
        with:
          name: release-notes
          path: release_notes.md
          retention-days: 1
          if-no-files-found: error

      - name: Prepend CHANGELOG.md and push main
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git fetch origin main
          git checkout -B main origin/main
          bash scripts/release/prepend-changelog.sh release_notes.md "$GITHUB_REF_NAME"
          git add CHANGELOG.md
          git commit -m "docs(changelog): release $GITHUB_REF_NAME [skip ci]"
          if ! git push origin main; then
            git pull --rebase origin main
            git push origin main || echo "::warning::CHANGELOG push failed, manual sync required"
          fi

      - name: Create or update GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          GH_REPO: ${{ github.repository }}
        run: |
          apk_path="release-apk/${{ needs.build-release-apk.outputs.apk-name }}"
          if gh release view "$GITHUB_REF_NAME" >/dev/null 2>&1; then
            gh release upload "$GITHUB_REF_NAME" "$apk_path" --clobber
            gh release edit "$GITHUB_REF_NAME" --notes-file release_notes.md
          else
            gh release create "$GITHUB_REF_NAME" "$apk_path" \
              --title "$GITHUB_REF_NAME" \
              --notes-file release_notes.md
          fi
```

- [ ] **Step 4：新增 `publish-version-manifest` job**

紧接 `publish-github-release` 之后追加：

```yaml
  publish-version-manifest:
    name: Publish version manifest
    needs: [build-release-apk, publish-github-release]
    if: github.event_name == 'push' && github.ref_type == 'tag'
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Checkout source at tag
        uses: actions/checkout@v6
        with:
          path: source
          ref: ${{ github.ref }}

      - name: Checkout gh-pages (if exists)
        uses: actions/checkout@v6
        continue-on-error: true
        with:
          ref: gh-pages
          path: gh-pages

      - name: Init gh-pages if missing
        run: |
          if [ ! -d gh-pages ]; then
            git clone --branch main "https://x-access-token:${{ github.token }}@github.com/${{ github.repository }}.git" gh-pages
            cd gh-pages
            git checkout --orphan gh-pages
            git rm -rf . 2>/dev/null || true
            echo "Release manifest branch" > README.md
            git config user.name "github-actions[bot]"
            git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
            git add README.md
            git commit -m "chore: init gh-pages"
          fi

      - name: Download release notes artifact
        uses: actions/download-artifact@v7
        with:
          name: release-notes
          path: source

      - name: Build version.json
        run: |
          mkdir -p gh-pages/release
          vcode=$(awk -F= '/^versionCode/{print $2}' source/version.properties | tr -d '[:space:]')
          bash source/scripts/release/build-version-json.sh \
              --version "${GITHUB_REF_NAME#v}" \
              --version-code "$vcode" \
              --tag "$GITHUB_REF_NAME" \
              --sha256 "${{ needs.build-release-apk.outputs.apk-sha256 }}" \
              --size "${{ needs.build-release-apk.outputs.apk-size }}" \
              --apk-name "${{ needs.build-release-apk.outputs.apk-name }}" \
              --notes source/release_notes.md \
              > gh-pages/release/version.json
          jq . gh-pages/release/version.json

      - name: Commit & push gh-pages
        run: |
          cd gh-pages
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add release/version.json
          git commit -m "chore(release): publish ${GITHUB_REF_NAME}"
          git push origin gh-pages
```

- [ ] **Step 5：lint workflow yaml**

```bash
# 用 actionlint（如本机已装）或 GitHub UI 校验语法；没有就跑：
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/android-release-apk.yml'))"
```

Expected: 无 yaml 错误。

- [ ] **Step 6：commit**

```bash
git add .github/workflows/android-release-apk.yml
git commit -m "ci(release): tag 一致性校验 + release notes LLM 摘要 + CHANGELOG 自动追加 + gh-pages 发布"
```

---

## Task 24：harness 文档同步 + AGENTS / CLAUDE 提及发布流程

**Files:**
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `docs/dev-harness/INDEX.md`

- [ ] **Step 1：AGENTS.md / CLAUDE.md 追加一行**

在两份文件的 "## 文档维护" 节末尾追加同一行：

```markdown
- 发布流程详见根目录 `RELEASE.md`；设计 spec 见 `docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md`。
```

- [ ] **Step 2：`docs/dev-harness/INDEX.md` 同步**

在合适位置（通常是"开发守门总入口"列表末尾）追加：

```markdown
- 发布流程：见根目录 `RELEASE.md` 与 `docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md`。
```

- [ ] **Step 3：commit**

```bash
git add AGENTS.md CLAUDE.md docs/dev-harness/INDEX.md
git commit -m "docs(release): AGENTS/CLAUDE/INDEX 指向 RELEASE.md"
```

---

## Task 25：本地全链路自检

> 这是非代码 task；手工执行并把结论写入 commit message 或留在 worktree 的临时笔记。

- [ ] **Step 1：本地 preflight 全跑**

```bash
# 在 worktree 内，本机 keystore 环境就绪
source ~/path/.env.release.local
bash scripts/release/preflight.sh v0.0.1-rc1
```

Expected: 输出 `Preflight OK`；`/tmp/preflight-version.json` 是合法 JSON；diff 显示 CHANGELOG 会被插入新片段。

- [ ] **Step 2：装 Debug 包验证不弹更新 dialog**

```bash
./gradlew :app:installDebug --no-daemon
adb shell am start -n com.zili.android.musicfreeandroid.debug/.MainActivity
```

冷启动观察：不应弹"发现新版本" dialog（受 `isDebugBuild = true` 保护）。

- [ ] **Step 3：装 Release 包 + 临时构造 gh-pages fixture 验证启动 dialog**

a. 把测试机的 hosts 改写 `raw.githubusercontent.com` 指向本地 mock server，或使用 `BuildConfig` debug-only override（开发态下 `UpdaterMirrors.VERSION_JSON_MIRRORS` 可临时改为本地 URL）。
b. mock server 返回比当前 versionCode 大 1 的 `version.json`。
c. `:app:installRelease`，冷启动 → 应弹"发现新版本"。
d. 测点击「跳过此版本」→ 二次冷启动应不弹。
e. 测点击「下载并安装」→ 进度对话框 → 取消应回到 Available 状态。
f. 测下载完成 → 安装器拉起 → 装好。

- [ ] **Step 4：把验证结论记入 commit**

```bash
echo "OK: preflight + dialog + skip + download + install verified locally" \
    > /tmp/verification.note
# 不入库，仅供 reviewer 在 PR 描述里抄录
```

> 本任务不产生 git 改动；仅用于 PR 之前的运行态验收 gate。如果任何步骤 fail，回到对应任务继续修。

---

## 自审清单

按照 writing-plans 自审：

1. **Spec 覆盖**
   - §3.1（模块边界）→ Task 2
   - §3.1.3（UpdateState）→ Task 5
   - §3.2.1（version.properties）→ Task 1
   - §3.2.2（Validate version consistency）→ Task 23 Step 1
   - §3.2.3（sha256 + size 输出）→ Task 23 Step 2
   - §3.2.4（Release notes 生成）→ Task 18 + Task 23 Step 3
   - §3.2.5（CHANGELOG.md 自动追加）→ Task 19 + Task 23 Step 3
   - §3.2.6（gh release create）→ Task 23 Step 3
   - §3.2.7（publish-version-manifest）→ Task 23 Step 4
   - §3.3（version.json 数据契约）→ Task 3 + Task 20
   - §3.4.1（启动检查）→ Task 12 + Task 14
   - §3.4.2（启动 Dialog）→ Task 15
   - §3.4.3（下载进度对话框）→ Task 9 + Task 15
   - §3.4.4（安装）→ Task 10 + Task 13
   - §3.4.5（设置页"检查更新"行）→ Task 16
   - §3.4.6（抽屉「设置」入口红点）→ Task 17
   - §3.5（数据持久化）→ Task 6
   - §3.6（错误处理）→ 测试用例覆盖（Task 8 / Task 9）
   - §3.7（测试策略）→ 各 Task 内嵌
   - §3.8（文档产出）→ Task 18-22
   - §6.3（.gitignore / docs 同步）→ Task 22 + Task 24

2. **Placeholder 扫描**：无 TBD / TODO / "appropriate"。所有 step 含完整代码或命令。

3. **类型一致性**：`UpdateInfo`、`UpdateState`、`UpdateError`、`UpdateChecker.state`、`ApkDownloader.Result`、`ApkInstaller.InstallResult`、`UpdaterMirrors.VERSION_JSON_MIRRORS`、`LocalAppVersion` 在所有引用 task 中名字一致。`UpdateChecker` 的方法：`checkOnLaunch / checkManually / markSkipped / transitionDownloading / transitionReady / transitionFailed / transitionAvailable` 在 Task 8 与 Task 15 一致。

4. **存在但未覆盖的需求**：无。

---
