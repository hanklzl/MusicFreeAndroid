# 分 ABI APK 发布与更新链路改造 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把单 universal APK 发布升级为 per-ABI（arm64-v8a / x86_64）发布；客户端按设备 ABI 拉取对应 APK；侧栏「检查更新」真接通并显红点；R8 mapping 永久归档到 GitHub Release asset。

**Architecture:** 改 `:app` 启用 `splits.abi` 出双 APK，启用 R8 行号属性；`:updater` `UpdateInfo` 升 schemaVersion=2 引入 `variants` map + 新建 `AbiResolver` + 新错误码 `UnsupportedAbi`；`UpdateState` 字段从 `UpdateInfo` 替换为 `ResolvedUpdate`；`:feature:home` 抽屉接通 `UpdateChecker.checkManually()` + `ManualUpdateDialog` + 红点；CI 双 APK + mapping zip 三 asset 上传 + version.json v2。

**Tech Stack:** Kotlin 2.3, Gradle 9.4 + AGP 9.2, Jetpack Compose Material3, Hilt, Room/DataStore, kotlinx.serialization, OkHttp 5, MockWebServer, Robolectric, mockk, turbine, JUnit 4, GitHub Actions, bash scripts.

**Reference spec:** [docs/superpowers/specs/2026-05-16-per-abi-release-and-update-design.md](../specs/2026-05-16-per-abi-release-and-update-design.md)

---

## File map

### Build & R8
- Modify: `app/build.gradle.kts` — `splits.abi { include("arm64-v8a", "x86_64") }`、`base.archivesName`
- Modify: `app/proguard-rules.pro` — 启用 `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile`

### Updater 模块
- Modify: `updater/src/main/.../model/UpdateInfo.kt` — schemaVersion=2、`ApkVariant` + `MappingRef` + `variants` map
- Modify: `updater/src/test/.../model/UpdateInfoTest.kt` — 双 variant / 无 variants / schema=99 / size=0 fixtures
- Create: `updater/src/main/.../checker/AbiResolver.kt` — 含 `ResolvedUpdate`
- Create: `updater/src/test/.../checker/AbiResolverTest.kt`
- Modify: `updater/src/main/.../checker/UpdateState.kt` — `UpdateError.UnsupportedAbi`、状态字段换 `ResolvedUpdate`
- Modify: `updater/src/main/.../checker/UpdateChecker.kt` — 调 AbiResolver、transition* 改 ResolvedUpdate
- Modify: `updater/src/test/.../checker/UpdateCheckerTest.kt` — 适配 v2 + 新增 UnsupportedAbi case
- Modify: `updater/src/main/.../downloader/ApkDownloader.kt` — `download(update: ResolvedUpdate, ...)`
- Modify: `updater/src/main/.../downloader/OkHttpApkDownloader.kt` — cacheDir 带 abi 后缀
- Modify: `updater/src/test/.../downloader/OkHttpApkDownloaderTest.kt` — fixtures 改 ResolvedUpdate
- Modify: `updater/src/main/.../ui/UpdateDialogs.kt` — 字段路径 `info.*` → `update.info.*` 等
- Modify: `updater/src/main/.../ui/UpdateDialogHost.kt` — 适配 ResolvedUpdate
- Create: `updater/src/main/.../ui/ManualUpdateDialog.kt`

### Core 公共
- Create: `core/src/main/.../ui/UpdateBadgeDot.kt`

### Settings & Home
- Modify: `feature/settings/.../CheckUpdateRow.kt` — 用 `UpdateBadgeDot`
- Modify: `feature/home/.../HomeDrawerNavigation.kt` — `HomeDrawerAction.TriggerManualUpdateCheck`、`HomeDrawerItemUiModel.hasBadge`、`buildHomeDrawerUiModel` 接受 `updateState` 参数
- Modify: `feature/home/.../component/HomeDrawerContent.kt` — DrawerRow 用 `item.hasBadge`，去除 hasUpdateRedDot 过滤
- Modify: `feature/home/.../component/HomeDrawerDialogs.kt` — 删空 InfoDialog 分支，集成 ManualUpdateDialog
- Modify: 抽屉调用者（HomeScreen 或对应 ViewModel）— 注入 `UpdateBadgeViewModel`，订阅 state 派生 hasBadge / trailingText
- Modify: `feature/home/src/main/AndroidManifest.xml`（若需要权限声明）— 实际无需

### Tests
- Modify: `app/src/androidTest/.../HomeEntryNavigationTest.kt` — `checkUpdateEntry_opensUpdateCheckDialog` 期望改 ManualUpdateDialog
- Modify: `app/src/androidTest/.../HomeDrawerBehaviorTest.kt` — 适配新红点机制（若现有 assertion 受影响）

### CI 流水线
- Modify: `.github/workflows/android-release-apk.yml`
- Modify: `scripts/release/build-version-json.sh`
- Modify: `scripts/release/preflight.sh`
- Modify: `scripts/release/lib/*` （若存在公共 helper）

### 文档
- Modify: `RELEASE.md` — 双 APK + mapping + 反混淆使用
- Modify: `docs/dev-harness/INDEX.md` — 加 spec 链接

---

## Task 1: 启用 splits.abi + R8 mapping 行号属性

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: 修改 `app/build.gradle.kts`，在 `android { ... }` 内补 `splits` 与 `base.archivesName`**

在 `android { ... }` 块内 `buildTypes { ... }` 之后、`compileOptions { ... }` 之前插入：

```kotlin
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "x86_64")
                isUniversalApk = false
            }
        }
```

在 `android { ... }` 块的顶部（紧挨 `namespace = "..."`）插入：

```kotlin
        base.archivesName = "MusicFreeAndroid"
```

注：`base` 是 `BaseExtension` 的引用，AGP 9 默认可在 android block 内直接使用。如编译报 `base` 未解析，改成 `project.base.archivesName = "MusicFreeAndroid"`。

- [ ] **Step 2: 修改 `app/proguard-rules.pro`，启用 mapping 行号属性**

把现有文件末尾（保留原有注释解释）改成：

```proguard
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers so retrace can map stack traces back to source lines.
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name to avoid leaking file paths beyond what retrace needs.
-renamesourcefileattribute SourceFile
```

- [ ] **Step 3: 本地验证构建（仅在签名环境齐备时）**

```bash
./gradlew clean :app:assembleDebug
ls app/build/outputs/apk/debug
```

期望：`MusicFreeAndroid-arm64-v8a-debug.apk` 与 `MusicFreeAndroid-x86_64-debug.apk` 两个文件（debug 也会受 splits 影响）。

> 如果只想 debug 保留 universal、release 才走 splits，可在 `splits.abi { ... }` 里加 `splits.abi.enable = !project.gradle.startParameter.taskNames.any { it.contains("Debug", ignoreCase = true) }`——本次默认两端都拆，避免本地测试与 CI 行为不一致。

- [ ] **Step 4: 提交**

```bash
git add app/build.gradle.kts app/proguard-rules.pro
git commit -m "build(app): 启用 splits.abi 双 ABI 与 R8 mapping 行号"
```

---

## Task 2: UpdateInfo v2 model + 序列化测试

**Files:**
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfo.kt`
- Modify: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfoTest.kt`

- [ ] **Step 1: 写 v2 序列化测试（TDD：先红）**

完全替换 `updater/src/test/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfoTest.kt` 文件内容为：

```kotlin
package com.zili.android.musicfreeandroid.updater.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses canonical v2 manifest with two variants`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": ["新功能 1", "修复 2"],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 23456789,
                  "sha256": "aaaa"
                },
                "x86_64": {
                  "download": ["https://example.com/x64.apk"],
                  "size": 25123456,
                  "sha256": "bbbb"
                }
              },
              "mapping": {
                "url": "https://example.com/mapping.zip",
                "sha256": "cccc"
              }
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals(2, info.schemaVersion)
        assertEquals("1.2.3", info.version)
        assertEquals(10203L, info.versionCode)
        assertEquals(setOf("arm64-v8a", "x86_64"), info.variants.keys)
        val arm = info.variants.getValue("arm64-v8a")
        assertEquals(listOf("https://example.com/arm64.apk"), arm.download)
        assertEquals(23456789L, arm.size)
        assertEquals("aaaa", arm.sha256)
        assertNotNull(info.mapping)
        assertEquals("https://example.com/mapping.zip", info.mapping!!.url)
    }

    @Test
    fun `parses v2 manifest without optional mapping`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 1,
                  "sha256": "x"
                }
              }
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals(1, info.variants.size)
        assertNull(info.mapping)
    }

    @Test
    fun `ignores unknown fields at root and variant level`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 1,
                  "sha256": "x",
                  "future": "ignored"
                }
              },
              "future": "ignored"
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals("1.2.3", info.version)
    }

    @Test(expected = Exception::class)
    fun `rejects missing variants field`() {
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": []
            }
        """.trimIndent()
        json.decodeFromString(UpdateInfo.serializer(), raw)
    }

    @Test
    fun `marks schema version greater than supported`() {
        val raw = """
            {
              "schemaVersion": 99,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {
                "arm64-v8a": {
                  "download": ["https://example.com/arm64.apk"],
                  "size": 1,
                  "sha256": "x"
                }
              }
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertTrue(info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION)
        assertEquals(2, UpdateInfo.SUPPORTED_SCHEMA_VERSION)
    }

    @Test
    fun `accepts empty variants map but consumer must reject`() {
        // 解析层允许 {}；UpdateChecker 负责拒。本测试只确认模型层接受。
        val raw = """
            {
              "schemaVersion": 2,
              "version": "1.2.3",
              "versionCode": 10203,
              "releasedAt": "2026-05-16T18:00:00Z",
              "releaseNotesUrl": "https://example.com/notes",
              "changeLog": [],
              "variants": {}
            }
        """.trimIndent()
        val info = json.decodeFromString(UpdateInfo.serializer(), raw)
        assertTrue(info.variants.isEmpty())
    }
}
```

- [ ] **Step 2: 运行测试，确认失败（红）**

```bash
cd /Users/zili/code/android/MusicFreeAndroid/.worktrees/per-abi-release
./gradlew :updater:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.updater.model.UpdateInfoTest"
```

期望：编译失败或 `UpdateInfo.SUPPORTED_SCHEMA_VERSION` 仍是 1 → 多个 assertion 失败。

- [ ] **Step 3: 实现 v2 model（绿）**

完全替换 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfo.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.updater.model

import kotlinx.serialization.Serializable

@Serializable
data class ApkVariant(
    val download: List<String>,
    val size: Long,
    val sha256: String,
)

@Serializable
data class MappingRef(
    val url: String,
    val sha256: String,
)

@Serializable
data class UpdateInfo(
    val schemaVersion: Int,
    val version: String,
    val versionCode: Long,
    val releasedAt: String,
    val releaseNotesUrl: String,
    val changeLog: List<String>,
    val variants: Map<String, ApkVariant>,
    val mapping: MappingRef? = null,
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 2
    }
}
```

此处 **完全移除** 旧的顶层 `download / size / sha256` 字段——后续 task 会把所有 callsite 都迁移过来。本 commit 之后整个 :updater 编译会大面积红，是预期。

- [ ] **Step 4: 运行模型测试，确认通过**

```bash
./gradlew :updater:compileDebugKotlin   # 只编译 main，跳过其他 callsite
```

> 注：此时 `:updater:testDebugUnitTest` 全量会失败，因为别的测试（UpdateCheckerTest / OkHttpApkDownloaderTest）仍引用旧字段。下面任务会逐个修复。

```bash
./gradlew :updater:compileDebugKotlin
```

期望：`:updater` main 编译通过；其他模块（`:feature:home`、`:feature:settings`、`:app` ui/dialog 部分）尚未编译。

- [ ] **Step 5: 提交**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfo.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/model/UpdateInfoTest.kt
git commit -m "feat(updater): UpdateInfo 升级到 v2 schema 引入 variants"
```

---

## Task 3: AbiResolver + ResolvedUpdate

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/AbiResolver.kt`
- Create: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/AbiResolverTest.kt`
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterModule.kt`

- [ ] **Step 1: 写 AbiResolver 测试**

创建 `updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/AbiResolverTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import com.zili.android.musicfreeandroid.updater.model.ApkVariant
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbiResolverTest {

    private fun makeInfo(abis: List<String>): UpdateInfo = UpdateInfo(
        schemaVersion = 2,
        version = "1.2.3",
        versionCode = 10203,
        releasedAt = "2026-05-16T18:00:00Z",
        releaseNotesUrl = "https://example.com/notes",
        changeLog = emptyList(),
        variants = abis.associateWith {
            ApkVariant(
                download = listOf("https://example.com/$it.apk"),
                size = 1,
                sha256 = it,
            )
        },
    )

    @Test
    fun `selects arm64 when device prefers arm64`() {
        val resolver = AbiResolver { listOf("arm64-v8a", "armeabi-v7a", "armeabi") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        val resolved = resolver.resolve(info)
        assertEquals("arm64-v8a", resolved?.abi)
        assertEquals("arm64-v8a", resolved?.variant?.sha256)
    }

    @Test
    fun `selects x86_64 when device prefers x86_64`() {
        val resolver = AbiResolver { listOf("x86_64", "x86") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        assertEquals("x86_64", resolver.resolve(info)?.abi)
    }

    @Test
    fun `returns null when no supported abi has a variant`() {
        val resolver = AbiResolver { listOf("armeabi-v7a", "armeabi") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        assertNull(resolver.resolve(info))
    }

    @Test
    fun `returns null when variants map empty`() {
        val resolver = AbiResolver { listOf("arm64-v8a") }
        val info = makeInfo(emptyList())
        assertNull(resolver.resolve(info))
    }

    @Test
    fun `returns null when device supported abi list empty`() {
        val resolver = AbiResolver { emptyList() }
        val info = makeInfo(listOf("arm64-v8a"))
        assertNull(resolver.resolve(info))
    }

    @Test
    fun `respects device abi priority order`() {
        // 设备同时支持 arm64 和 x86_64（罕见但可能），按列表顺序优先
        val resolver = AbiResolver { listOf("x86_64", "arm64-v8a") }
        val info = makeInfo(listOf("arm64-v8a", "x86_64"))
        assertEquals("x86_64", resolver.resolve(info)?.abi)
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败**

```bash
./gradlew :updater:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.updater.checker.AbiResolverTest"
```

期望：编译失败，`AbiResolver` / `ResolvedUpdate` 未定义。

- [ ] **Step 3: 实现 AbiResolver**

创建 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/AbiResolver.kt`：

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import android.os.Build
import com.zili.android.musicfreeandroid.updater.model.ApkVariant
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo

data class ResolvedUpdate(
    val info: UpdateInfo,
    val abi: String,
    val variant: ApkVariant,
)

class AbiResolver(
    private val supportedAbis: () -> List<String> = { Build.SUPPORTED_ABIS.toList() },
) {
    fun resolve(info: UpdateInfo): ResolvedUpdate? =
        supportedAbis().firstOrNull { it in info.variants }
            ?.let { abi -> ResolvedUpdate(info = info, abi = abi, variant = info.variants.getValue(abi)) }
}
```

**注意：** 本 task **不修改 `UpdaterModule.kt`**。AbiResolver 的 Hilt provide + UpdateChecker 注入会在 Task 5 一起做（那时 UpdateChecker 构造函数也会加 `abiResolver` 参数，两处改动天然耦合）。本 task 保持 `:updater` 模块的 Hilt graph 编译性稳定，避免引入临时空悬的依赖。

- [ ] **Step 4: 运行测试，确认通过**

```bash
./gradlew :updater:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.updater.checker.AbiResolverTest"
```

期望：6 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/AbiResolver.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/AbiResolverTest.kt
git commit -m "feat(updater): 引入 AbiResolver 按 Build.SUPPORTED_ABIS 选择 variant"
```

---

## Task 4: UpdateState / UpdateError 迁移到 ResolvedUpdate

**Files:**
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateState.kt`

- [ ] **Step 1: 替换 UpdateState.kt 全文**

完全替换 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateState.kt` 内容为：

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import java.io.File

sealed interface UpdateState {

    data object Idle : UpdateState
    data object Checking : UpdateState

    data class UpToDate(val checkedAtEpochMillis: Long) : UpdateState

    data class Available(
        val update: ResolvedUpdate,
        val skipped: Boolean,
    ) : UpdateState

    data class Downloading(
        val update: ResolvedUpdate,
        val progress: Float,
        val bytes: Long,
        val total: Long,
    ) : UpdateState

    data class ReadyToInstall(
        val update: ResolvedUpdate,
        val apkFile: File,
    ) : UpdateState

    data class Failed(
        val update: ResolvedUpdate?,
        val cause: UpdateError,
    ) : UpdateState

    val hasUnreadAvailableUpdate: Boolean
        get() = this is Available && !skipped
}

enum class UpdateError {
    Network,
    SchemaUnsupported,
    UnsupportedAbi,
    SizeMismatch,
    Sha256Mismatch,
    Canceled,
    InstallBlocked,
}
```

要点：
- 所有 `info: UpdateInfo` / `info: UpdateInfo?` 改成 `update: ResolvedUpdate` / `update: ResolvedUpdate?`。
- `UpdateError` 新增 `UnsupportedAbi`，**插在 `SchemaUnsupported` 之后、`SizeMismatch` 之前**（语义近）。
- 删除 `import UpdateInfo`（已不再直接引用）。

- [ ] **Step 2: 验证编译失败点收敛在 UpdateChecker / UpdateDialogHost / UpdateDialogs / OkHttpApkDownloader**

```bash
./gradlew :updater:compileDebugKotlin
```

期望：错误集中在以下文件，提示 `info` 字段不存在或类型不匹配：
- `UpdateChecker.kt`
- `OkHttpApkDownloader.kt`
- `UpdateDialogHost.kt`
- `UpdateDialogs.kt`

下面 task 4 步骤里的 commit **暂不修这些**，记录基线即可（commit message 提示）。

- [ ] **Step 3: 提交**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateState.kt
git commit -m "refactor(updater): UpdateState 字段切换至 ResolvedUpdate 并新增 UnsupportedAbi"
```

---

## Task 5: UpdateChecker 接通 AbiResolver + 测试更新

**Files:**
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateChecker.kt`
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterModule.kt`
- Modify: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/UpdateCheckerTest.kt`

- [ ] **Step 1: 更新 UpdateChecker 实现**

完全替换 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateChecker.kt` 内容为：

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
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
import java.io.File

class UpdateChecker(
    private val client: UpdateClient,
    private val prefs: UpdatePreferences,
    private val abiResolver: AbiResolver,
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
                MfLog.trace(
                    category = LogCategory.UPDATE,
                    event = "update_check_start",
                    fields = mapOf("respectSkip" to respectSkip),
                )
                _state.value = UpdateState.Checking
                val info: UpdateInfo = client.fetchLatest()
                    ?: run {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "update_check_failed",
                            fields = mapOf("cause" to UpdateError.Network.name),
                        )
                        _state.value = UpdateState.Failed(update = null, cause = UpdateError.Network)
                        return@withLock
                    }
                if (info.schemaVersion > UpdateInfo.SUPPORTED_SCHEMA_VERSION || info.variants.isEmpty()) {
                    MfLog.error(
                        category = LogCategory.UPDATE,
                        event = "update_check_failed",
                        fields = mapOf(
                            "cause" to UpdateError.SchemaUnsupported.name,
                            "remoteSchema" to info.schemaVersion,
                            "supportedSchema" to UpdateInfo.SUPPORTED_SCHEMA_VERSION,
                            "variantsCount" to info.variants.size,
                        ),
                    )
                    _state.value = UpdateState.Failed(update = null, cause = UpdateError.SchemaUnsupported)
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
                        MfLog.trace(
                            category = LogCategory.UPDATE,
                            event = "update_check_complete",
                            fields = mapOf(
                                "outcome" to "up_to_date",
                                "versionCode" to localCode,
                                "respectSkip" to respectSkip,
                            ),
                        )
                    }
                    VersionCompare.Outcome.Unsupported -> {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "update_check_failed",
                            fields = mapOf("cause" to UpdateError.SchemaUnsupported.name),
                        )
                        _state.value = UpdateState.Failed(update = null, cause = UpdateError.SchemaUnsupported)
                    }
                    VersionCompare.Outcome.NewerAvailable -> {
                        val resolved = abiResolver.resolve(info)
                            ?: run {
                                MfLog.error(
                                    category = LogCategory.UPDATE,
                                    event = "update_check_failed",
                                    fields = mapOf(
                                        "cause" to UpdateError.UnsupportedAbi.name,
                                        "variants" to info.variants.keys.joinToString(","),
                                    ),
                                )
                                _state.value = UpdateState.Failed(update = null, cause = UpdateError.UnsupportedAbi)
                                return@withLock
                            }
                        prefs.setLastCheckedAt(now())
                        prefs.setLastSeenVersion(info.version)
                        val skip = if (respectSkip) prefs.getSkipVersion() else null
                        val isSkipped = skip != null && skip == info.version
                        _state.value = UpdateState.Available(update = resolved, skipped = isSkipped)
                        MfLog.trace(
                            category = LogCategory.UPDATE,
                            event = "update_check_complete",
                            fields = mapOf(
                                "outcome" to "newer_available",
                                "versionCode" to info.versionCode,
                                "abi" to resolved.abi,
                                "respectSkip" to respectSkip,
                                "result" to if (isSkipped) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
                            ),
                        )
                    }
                }
            }
        }
    }

    suspend fun markSkipped(update: ResolvedUpdate) {
        prefs.setSkipVersion(update.info.version)
        _state.value = UpdateState.Available(update = update, skipped = true)
    }

    fun transitionDownloading(update: ResolvedUpdate, progress: Float, bytes: Long, total: Long) {
        _state.value = UpdateState.Downloading(update, progress, bytes, total)
    }

    fun transitionReady(update: ResolvedUpdate, file: File) {
        _state.value = UpdateState.ReadyToInstall(update, file)
    }

    fun transitionFailed(update: ResolvedUpdate?, cause: UpdateError) {
        _state.value = UpdateState.Failed(update, cause)
    }

    fun transitionAvailable(update: ResolvedUpdate, skipped: Boolean) {
        _state.value = UpdateState.Available(update, skipped)
    }
}
```

变更点：
- 构造函数新增 `abiResolver: AbiResolver`。
- 第一道 schemaVersion / variants empty 判断合并为单一 SchemaUnsupported。
- `NewerAvailable` 分支调 `abiResolver.resolve(info)`，null → `UnsupportedAbi`。
- 所有 `transition*` 方法把 `UpdateInfo` 入参换成 `ResolvedUpdate`。
- `markSkipped` 用 `update.info.version` 作为 skip key。

- [ ] **Step 2: 修改 `UpdaterModule.kt` provide AbiResolver + 注入 UpdateChecker**

修改 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterModule.kt`，在 `import` 区加：

```kotlin
import com.zili.android.musicfreeandroid.updater.checker.AbiResolver
```

在 `provideUpdateChecker` 之前插入：

```kotlin
    @Provides
    @Singleton
    fun provideAbiResolver(): AbiResolver = AbiResolver()
```

把 `provideUpdateChecker` 改成：

```kotlin
    @Provides
    @Singleton
    fun provideUpdateChecker(
        client: UpdateClient,
        prefs: UpdatePreferences,
        abiResolver: AbiResolver,
        localAppVersion: LocalAppVersion,
    ): UpdateChecker = UpdateChecker(
        client = client,
        prefs = prefs,
        abiResolver = abiResolver,
        localCode = localAppVersion.versionCode,
        localName = localAppVersion.versionName,
    )
```

- [ ] **Step 3: 更新 UpdateCheckerTest**

完全替换 `updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/UpdateCheckerTest.kt` 内容为：

```kotlin
package com.zili.android.musicfreeandroid.updater.checker

import app.cash.turbine.test
import com.zili.android.musicfreeandroid.updater.api.UpdateClient
import com.zili.android.musicfreeandroid.updater.model.ApkVariant
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

    private fun newInfo(
        version: String,
        code: Long,
        abis: List<String> = listOf("arm64-v8a", "x86_64"),
    ): UpdateInfo = UpdateInfo(
        schemaVersion = 2,
        version = version,
        versionCode = code,
        releasedAt = "2026-05-16T18:00:00Z",
        releaseNotesUrl = "https://example.com/notes",
        changeLog = emptyList(),
        variants = abis.associateWith {
            ApkVariant(
                download = listOf("https://example.com/$it.apk"),
                size = 1,
                sha256 = it,
            )
        },
    )

    private fun mockPrefs(skip: String? = null): UpdatePreferences = mockk(relaxed = true) {
        coEvery { getSkipVersion() } returns skip
    }

    private fun armResolver() = AbiResolver { listOf("arm64-v8a", "armeabi-v7a") }

    @Test
    fun `up to date when remote not newer`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        assertTrue(checker.state.value is UpdateState.UpToDate)
    }

    @Test
    fun `available when remote newer and not skipped and abi matches`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(false, (state as UpdateState.Available).skipped)
        assertEquals("arm64-v8a", state.update.abi)
    }

    @Test
    fun `marks skipped when remote version equals skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val checker = UpdateChecker(client, mockPrefs(skip = "1.2.3"), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(true, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `manual check ignores skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val checker = UpdateChecker(client, mockPrefs(skip = "1.2.3"), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkManually()
        advanceUntilIdle()
        assertEquals(false, (checker.state.value as UpdateState.Available).skipped)
    }

    @Test
    fun `failed when client returns null`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns null }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
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
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.SchemaUnsupported, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `empty variants marked failed as schema unsupported`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> {
            coEvery { fetchLatest() } returns newInfo("1.2.3", 10203).copy(variants = emptyMap())
        }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        assertEquals(
            UpdateError.SchemaUnsupported,
            (checker.state.value as UpdateState.Failed).cause,
        )
    }

    @Test
    fun `unsupported abi marked failed`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> {
            coEvery { fetchLatest() } returns newInfo("1.2.3", 10203, abis = listOf("x86_64"))
        }
        val checker = UpdateChecker(
            client,
            mockPrefs(),
            abiResolver = AbiResolver { listOf("armeabi-v7a") },
            localCode = 10000L,
            localName = "1.0.0",
            scope = this,
        )
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.UnsupportedAbi, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `state flow emits Checking before result`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
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

- [ ] **Step 4: 运行测试，确认通过**

```bash
./gradlew :updater:testDebugUnitTest \
    --tests "com.zili.android.musicfreeandroid.updater.checker.UpdateCheckerTest" \
    --tests "com.zili.android.musicfreeandroid.updater.checker.AbiResolverTest" \
    --tests "com.zili.android.musicfreeandroid.updater.model.UpdateInfoTest"
```

期望：所有 case PASS。

> `:updater:compileDebugKotlin` 此时仍会因 `OkHttpApkDownloader / UpdateDialogs / UpdateDialogHost` 编译失败而 fail，是预期。

- [ ] **Step 5: 提交**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/checker/UpdateChecker.kt \
        updater/src/main/java/com/zili/android/musicfreeandroid/updater/di/UpdaterModule.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/checker/UpdateCheckerTest.kt
git commit -m "feat(updater): UpdateChecker 接通 AbiResolver 并产出 ResolvedUpdate"
```

---

## Task 6: OkHttpApkDownloader 接 ResolvedUpdate + 测试更新

**Files:**
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/ApkDownloader.kt`
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloader.kt`
- Modify: `updater/src/test/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloaderTest.kt`

- [ ] **Step 1: 改 ApkDownloader 接口**

完全替换 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/ApkDownloader.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.updater.downloader

import com.zili.android.musicfreeandroid.updater.checker.ResolvedUpdate
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
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
        update: ResolvedUpdate,
        onProgress: (bytes: Long, total: Long, fraction: Float) -> Unit,
    ): Result

    fun cancel()
}
```

- [ ] **Step 2: 改 OkHttpApkDownloader 实现**

完全替换 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloader.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.updater.downloader

import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.checker.ResolvedUpdate
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

class OkHttpApkDownloader constructor(
    private val http: OkHttpClient,
    private val cacheRoot: () -> File,
) : ApkDownloader {

    private val currentCall = AtomicReference<Call?>(null)

    override suspend fun download(
        update: ResolvedUpdate,
        onProgress: (Long, Long, Float) -> Unit,
    ): ApkDownloader.Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val versionCode = update.info.versionCode
        val abi = update.abi
        val variant = update.variant
        val dir = cacheRoot().apply { mkdirs() }
        val finalFile = File(dir, "musicfree-${versionCode}-${abi}.apk")
        val partFile = File(dir, "musicfree-${versionCode}-${abi}.apk.part")
        partFile.delete()
        finalFile.delete()

        for ((index, url) in variant.download.withIndex()) {
            val outcome = tryDownload(
                variantSize = variant.size,
                variantSha256 = variant.sha256,
                url = url,
                target = partFile,
                onProgress = onProgress,
            )
            when (outcome) {
                is StepOutcome.Ok -> {
                    if (!partFile.renameTo(finalFile)) {
                        partFile.delete()
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "apk_download_failed",
                            fields = mapOf(
                                "cause" to UpdateError.Network.name,
                                "versionCode" to versionCode,
                                "abi" to abi,
                                "reason" to "rename_failed",
                            ),
                        )
                        return@withContext ApkDownloader.Result.Failure(UpdateError.Network)
                    }
                    MfLog.detail(
                        category = LogCategory.UPDATE,
                        event = "apk_download_complete",
                        fields = mapOf(
                            "versionCode" to versionCode,
                            "abi" to abi,
                            "bytes" to variant.size,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                            "result" to LogFields.Result.SUCCESS,
                        ),
                    )
                    return@withContext ApkDownloader.Result.Success(finalFile)
                }
                is StepOutcome.HardFail -> {
                    partFile.delete()
                    MfLog.error(
                        category = LogCategory.UPDATE,
                        event = "apk_download_failed",
                        fields = mapOf(
                            "cause" to outcome.cause.name,
                            "versionCode" to versionCode,
                            "abi" to abi,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                        ),
                    )
                    return@withContext ApkDownloader.Result.Failure(outcome.cause)
                }
                is StepOutcome.SoftFail -> {
                    partFile.delete()
                    if (index == variant.download.lastIndex) {
                        MfLog.error(
                            category = LogCategory.UPDATE,
                            event = "apk_download_failed",
                            fields = mapOf(
                                "cause" to UpdateError.Network.name,
                                "versionCode" to versionCode,
                                "abi" to abi,
                                "durationMs" to (System.currentTimeMillis() - startedAt),
                                "mirrorsExhausted" to true,
                            ),
                        )
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
        variantSize: Long,
        variantSha256: String,
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
                val body = response.body
                val advertised = body.contentLength()
                if (advertised >= 0 && advertised != variantSize) {
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
                            val fraction = if (variantSize > 0) (written.toFloat() / variantSize) else 0f
                            onProgress(written, variantSize, fraction.coerceIn(0f, 1f))
                        }
                    }
                }
                if (written != variantSize) return StepOutcome.HardFail(UpdateError.SizeMismatch)
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(variantSha256, ignoreCase = true)) {
                    return StepOutcome.HardFail(UpdateError.Sha256Mismatch)
                }
                StepOutcome.Ok
            }
        } catch (t: java.io.IOException) {
            if (call.isCanceled()) {
                StepOutcome.Canceled
            } else {
                MfLog.error(
                    category = LogCategory.UPDATE,
                    event = "apk_download_error",
                    throwable = t,
                    fields = mapOf(
                        "url" to url,
                        "host" to LogFields.host(url),
                        "error" to (t.message ?: t.javaClass.simpleName),
                    ),
                )
                StepOutcome.SoftFail
            }
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

变更点：
- `download(update: ResolvedUpdate, ...)`，从 `update.variant` 取 download / size / sha256。
- 文件名 `musicfree-${versionCode}-${abi}.apk` 与 `.part`。
- 日志字段 `abi` 加入。

- [ ] **Step 3: 更新 OkHttpApkDownloaderTest**

完全替换 `updater/src/test/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloaderTest.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.updater.downloader

import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.updater.checker.ResolvedUpdate
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.model.ApkVariant
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

    private fun makeResolved(url: String, body: ByteArray, sha: String, abi: String = "arm64-v8a"): ResolvedUpdate {
        val variant = ApkVariant(
            download = listOf(url),
            size = body.size.toLong(),
            sha256 = sha,
        )
        val info = UpdateInfo(
            schemaVersion = 2,
            version = "1.2.3",
            versionCode = 10203,
            releasedAt = "2026-05-16T18:00:00Z",
            releaseNotesUrl = "https://example.com/notes",
            changeLog = emptyList(),
            variants = mapOf(abi to variant),
        )
        return ResolvedUpdate(info = info, abi = abi, variant = variant)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `successful download writes abi-scoped file and reports progress`() = runTest {
        val body = ByteArray(4096) { 0x42 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha256Hex(body))

        val progresses = mutableListOf<Float>()
        val result = downloader.download(resolved) { _, _, fraction -> progresses.add(fraction) }

        assertTrue(result is ApkDownloader.Result.Success)
        val file = (result as ApkDownloader.Result.Success).apkFile
        assertTrue(file.exists())
        assertEquals(body.size.toLong(), file.length())
        assertTrue(progresses.last() in 0.99f..1.0f)
        assertFalse(File(file.parentFile, "${file.name}.part").exists())
        assertEquals("musicfree-10203-arm64-v8a.apk", file.name)
    }

    @Test
    fun `sha256 mismatch deletes file and returns mismatch`() = runTest {
        val body = ByteArray(2048) { 0x21 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha = "deadbeef")

        val result = downloader.download(resolved) { _, _, _ -> }

        assertTrue(result is ApkDownloader.Result.Failure)
        assertEquals(UpdateError.Sha256Mismatch, (result as ApkDownloader.Result.Failure).cause)
        val abi = resolved.abi
        assertFalse(File(cacheDir, "musicfree-${resolved.info.versionCode}-${abi}.apk.part").exists())
        assertFalse(File(cacheDir, "musicfree-${resolved.info.versionCode}-${abi}.apk").exists())
    }

    @Test
    fun `content length mismatch returns size mismatch`() = runTest {
        val body = ByteArray(1024) { 0x33 }
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))
        val resolved = makeResolved(server.url("/app.apk").toString(), body, sha256Hex(body))
        // 重新构造一个 variant.size = 9999 的 ResolvedUpdate
        val tampered = resolved.copy(variant = resolved.variant.copy(size = 9999L))

        val result = downloader.download(tampered) { _, _, _ -> }

        assertEquals(UpdateError.SizeMismatch, (result as ApkDownloader.Result.Failure).cause)
    }

    @Test
    fun `falls back to next mirror when first returns 500`() = runTest {
        val body = ByteArray(512) { 0x11 }
        val sha = sha256Hex(body)
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(Buffer().apply { write(body) }))

        val variant = ApkVariant(
            download = listOf(
                server.url("/dead.apk").toString(),
                server.url("/live.apk").toString(),
            ),
            size = body.size.toLong(),
            sha256 = sha,
        )
        val info = UpdateInfo(
            schemaVersion = 2,
            version = "1.2.3",
            versionCode = 10203,
            releasedAt = "2026-05-16T18:00:00Z",
            releaseNotesUrl = "https://example.com/notes",
            changeLog = emptyList(),
            variants = mapOf("x86_64" to variant),
        )
        val resolved = ResolvedUpdate(info = info, abi = "x86_64", variant = variant)

        val result = downloader.download(resolved) { _, _, _ -> }
        assertTrue(result is ApkDownloader.Result.Success)
        assertEquals("musicfree-10203-x86_64.apk", (result as ApkDownloader.Result.Success).apkFile.name)
    }
}
```

- [ ] **Step 4: 跑下载器测试**

```bash
./gradlew :updater:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.updater.downloader.OkHttpApkDownloaderTest"
```

期望：全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/ApkDownloader.kt \
        updater/src/main/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloader.kt \
        updater/src/test/java/com/zili/android/musicfreeandroid/updater/downloader/OkHttpApkDownloaderTest.kt
git commit -m "feat(updater): OkHttpApkDownloader 接 ResolvedUpdate 写 abi 后缀缓存"
```

---

## Task 7: UpdateDialogs.kt 与 UpdateDialogHost.kt 字段路径迁移

**Files:**
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogs.kt`
- Modify: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogHost.kt`

- [ ] **Step 1: 改 UpdateDialogs.kt — 所有 `info: UpdateInfo` 入参改成 `update: ResolvedUpdate`，体内 `info.xxx` 改 `update.info.xxx`**

完全替换 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogs.kt` 为：

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
import com.zili.android.musicfreeandroid.updater.checker.ResolvedUpdate

@Composable
fun AvailableUpdateDialog(
    update: ResolvedUpdate,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${update.info.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                update.info.changeLog.take(8).forEach { line ->
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
    update: ResolvedUpdate,
    bytes: Long,
    total: Long,
    fraction: Float,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在下载 v${update.info.version}") },
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
    update: ResolvedUpdate,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("下载完成 v${update.info.version}") },
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

@Composable
fun SchemaUnsupportedDialog(
    version: String?,
    releaseNotesUrl: String,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (version != null) "发现新版本 v$version" else "发现新版本") },
        text = { Text("当前客户端无法理解新版本元数据，请前往 GitHub 下载新版。") },
        confirmButton = { TextButton(onClick = onOpenReleasePage) { Text("打开下载页") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
    )
}

@Composable
fun UnsupportedAbiDialog(
    currentAbi: String?,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设备架构不受支持") },
        text = {
            Text(
                "您的设备架构${if (currentAbi != null) "（$currentAbi）" else ""}未在本次发布的 APK 列表中。" +
                    "请前往 GitHub Release 手动确认设备适配后下载。"
            )
        },
        confirmButton = { TextButton(onClick = onOpenReleasePage) { Text("打开下载页") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
    )
}

@Composable
fun CheckingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("正在检查更新") },
        text = {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun UpToDateDialog(localVersion: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已是最新版本") },
        text = { Text("当前版本 v$localVersion 已是最新。") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好的") } },
        dismissButton = {},
    )
}

@Composable
fun NetworkFailedDialog(onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("检查更新失败") },
        text = { Text("网络异常，请稍后重试。") },
        confirmButton = { TextButton(onClick = onRetry) { Text("重试") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
```

- [ ] **Step 2: 改 UpdateDialogHost.kt — `s.info` → `s.update.info`、`downloader.download(s.update, ...)` 等**

完全替换 `updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogHost.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.updater.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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

    var dismissedAvailable by remember { mutableStateOf(false) }

    when (val s = state) {
        is UpdateState.Available -> {
            if (!s.skipped && !dismissedAvailable) {
                AvailableUpdateDialog(
                    update = s.update,
                    onDownload = {
                        dismissedAvailable = true
                        scope.launch {
                            checker.transitionDownloading(s.update, 0f, 0L, s.update.variant.size)
                            val result = downloader.download(s.update) { bytes, total, fraction ->
                                checker.transitionDownloading(s.update, fraction, bytes, total)
                            }
                            when (result) {
                                is ApkDownloader.Result.Success -> checker.transitionReady(s.update, result.apkFile)
                                is ApkDownloader.Result.Failure -> {
                                    if (result.cause == UpdateError.Canceled) {
                                        checker.transitionAvailable(s.update, skipped = false)
                                    } else {
                                        checker.transitionFailed(s.update, result.cause)
                                    }
                                }
                            }
                        }
                    },
                    onSkip = {
                        dismissedAvailable = true
                        scope.launch { checker.markSkipped(s.update) }
                    },
                    onDismiss = { dismissedAvailable = true },
                )
            }
        }
        is UpdateState.Downloading -> {
            DownloadingDialog(
                update = s.update,
                bytes = s.bytes,
                total = s.total,
                fraction = s.progress,
                onCancel = { downloader.cancel() },
            )
        }
        is UpdateState.ReadyToInstall -> {
            ReadyToInstallDialog(
                update = s.update,
                onInstall = {
                    val result = installer.install(s.apkFile)
                    if (result is ApkInstaller.InstallResult.Blocked) {
                        checker.transitionFailed(s.update, result.cause)
                    }
                },
                onCancel = { checker.transitionAvailable(s.update, skipped = false) },
            )
        }
        is UpdateState.Failed -> {
            val update = s.update
            when (s.cause) {
                UpdateError.InstallBlocked -> {
                    InstallBlockedDialog(
                        onGoSettings = {
                            context.startActivity(InstallIntents.manageUnknownAppSources(context.packageName))
                        },
                        onDismiss = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                        },
                    )
                }
                UpdateError.SchemaUnsupported -> {
                    val notesUrl = update?.info?.releaseNotesUrl ?: GITHUB_RELEASES_LATEST
                    SchemaUnsupportedDialog(
                        version = update?.info?.version,
                        releaseNotesUrl = notesUrl,
                        onOpenReleasePage = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notesUrl)))
                        },
                        onDismiss = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                        },
                    )
                }
                UpdateError.UnsupportedAbi -> {
                    // 启动 dialog 不弹此分支；手动检查走 ManualUpdateDialog
                }
                UpdateError.Network,
                UpdateError.SizeMismatch,
                UpdateError.Sha256Mismatch -> {
                    // Toast + transition back handled by LaunchedEffect below
                }
                UpdateError.Canceled -> Unit
            }
        }
        else -> Unit
    }

    LaunchedEffect(state) {
        if (state !is UpdateState.Available) dismissedAvailable = false
        val s = state
        if (s is UpdateState.Failed && s.update != null) {
            val msg = when (s.cause) {
                UpdateError.Network -> "网络错误，稍后重试"
                UpdateError.SizeMismatch -> "安装包大小异常"
                UpdateError.Sha256Mismatch -> "安装包校验失败，请稍后重试"
                else -> null
            }
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                checker.transitionAvailable(s.update, skipped = false)
            }
        }
    }
}

internal const val GITHUB_RELEASES_LATEST: String =
    "https://github.com/hanklzl/MusicFreeAndroid/releases/latest"
```

- [ ] **Step 3: 编译 :updater 主代码**

```bash
./gradlew :updater:compileDebugKotlin
```

期望：通过。

- [ ] **Step 4: 跑全部 updater 测试**

```bash
./gradlew :updater:testDebugUnitTest
```

期望：全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogs.kt \
        updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/UpdateDialogHost.kt
git commit -m "refactor(updater): UpdateDialogs/Host 字段路径迁移至 ResolvedUpdate"
```

---

## Task 8: 新建 ManualUpdateDialog

**Files:**
- Create: `updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/ManualUpdateDialog.kt`

- [ ] **Step 1: 创建 ManualUpdateDialog.kt**

```kotlin
package com.zili.android.musicfreeandroid.updater.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.checker.UpdateState
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.installer.InstallIntents
import kotlinx.coroutines.launch

/**
 * 用户从侧栏 / 设置主动点「检查更新」时打开的 dialog。
 * 与 [UpdateDialogHost] 共享同一 [UpdateChecker] state，但渲染所有状态（含 Checking / UpToDate / Failed）。
 *
 * 关闭只把 [onDismiss] 触发，不重置 [UpdateChecker.state]——红点 / 启动 dialog 由 state 自主驱动。
 */
@Composable
fun ManualUpdateDialog(
    checker: UpdateChecker,
    downloader: ApkDownloader,
    installer: ApkInstaller,
    localVersionName: String,
    onDismiss: () -> Unit,
    deviceAbiProvider: () -> String? = { Build.SUPPORTED_ABIS.firstOrNull() },
) {
    val context = LocalContext.current
    val state by checker.state.collectAsState()
    val scope = rememberCoroutineScope()

    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> {
            CheckingDialog(onDismiss = onDismiss)
        }
        is UpdateState.UpToDate -> {
            UpToDateDialog(localVersion = localVersionName, onDismiss = onDismiss)
        }
        is UpdateState.Available -> {
            // 主动检查忽略 skipped
            AvailableUpdateDialog(
                update = s.update,
                onDownload = {
                    scope.launch {
                        checker.transitionDownloading(s.update, 0f, 0L, s.update.variant.size)
                        val result = downloader.download(s.update) { bytes, total, fraction ->
                            checker.transitionDownloading(s.update, fraction, bytes, total)
                        }
                        when (result) {
                            is ApkDownloader.Result.Success -> checker.transitionReady(s.update, result.apkFile)
                            is ApkDownloader.Result.Failure -> {
                                if (result.cause == UpdateError.Canceled) {
                                    checker.transitionAvailable(s.update, skipped = false)
                                } else {
                                    checker.transitionFailed(s.update, result.cause)
                                }
                            }
                        }
                    }
                },
                onSkip = {
                    scope.launch { checker.markSkipped(s.update) }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        is UpdateState.Downloading -> {
            DownloadingDialog(
                update = s.update,
                bytes = s.bytes,
                total = s.total,
                fraction = s.progress,
                onCancel = { downloader.cancel() },
            )
        }
        is UpdateState.ReadyToInstall -> {
            ReadyToInstallDialog(
                update = s.update,
                onInstall = {
                    val result = installer.install(s.apkFile)
                    if (result is ApkInstaller.InstallResult.Blocked) {
                        checker.transitionFailed(s.update, result.cause)
                    }
                },
                onCancel = { checker.transitionAvailable(s.update, skipped = false) },
            )
        }
        is UpdateState.Failed -> {
            val update = s.update
            when (s.cause) {
                UpdateError.Network -> {
                    NetworkFailedDialog(
                        onRetry = { checker.checkManually() },
                        onDismiss = onDismiss,
                    )
                }
                UpdateError.SchemaUnsupported -> {
                    val notesUrl = update?.info?.releaseNotesUrl ?: GITHUB_RELEASES_LATEST
                    SchemaUnsupportedDialog(
                        version = update?.info?.version,
                        releaseNotesUrl = notesUrl,
                        onOpenReleasePage = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notesUrl)))
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                    )
                }
                UpdateError.UnsupportedAbi -> {
                    val notesUrl = update?.info?.releaseNotesUrl ?: GITHUB_RELEASES_LATEST
                    UnsupportedAbiDialog(
                        currentAbi = deviceAbiProvider(),
                        onOpenReleasePage = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notesUrl)))
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                    )
                }
                UpdateError.InstallBlocked -> {
                    InstallBlockedDialog(
                        onGoSettings = {
                            context.startActivity(InstallIntents.manageUnknownAppSources(context.packageName))
                        },
                        onDismiss = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                            onDismiss()
                        },
                    )
                }
                UpdateError.SizeMismatch,
                UpdateError.Sha256Mismatch -> {
                    // toast by LaunchedEffect below
                }
                UpdateError.Canceled -> Unit
            }
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.Failed && s.update != null) {
            val msg = when (s.cause) {
                UpdateError.SizeMismatch -> "安装包大小异常"
                UpdateError.Sha256Mismatch -> "安装包校验失败，请稍后重试"
                else -> null
            }
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                checker.transitionAvailable(s.update, skipped = false)
            }
        }
    }
}
```

- [ ] **Step 2: 编译 :updater**

```bash
./gradlew :updater:assembleDebug
```

期望：通过。

- [ ] **Step 3: 提交**

```bash
git add updater/src/main/java/com/zili/android/musicfreeandroid/updater/ui/ManualUpdateDialog.kt
git commit -m "feat(updater): 新增 ManualUpdateDialog 渲染主动检查全状态"
```

---

## Task 9: 提取 UpdateBadgeDot 到 :core 并替换 settings 调用

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/UpdateBadgeDot.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/CheckUpdateRow.kt`

- [ ] **Step 1: 创建 UpdateBadgeDot**

```kotlin
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun UpdateBadgeDot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(8.dp)) {
        drawCircle(color = Color(0xFFE53935))
    }
}
```

- [ ] **Step 2: 改 CheckUpdateRow 用公共 UpdateBadgeDot**

完全替换 `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/CheckUpdateRow.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.UpdateBadgeDot
import com.zili.android.musicfreeandroid.updater.checker.UpdateState

@Composable
fun CheckUpdateRow(viewModel: CheckUpdateViewModel = hiltViewModel()) {
    val state by viewModel.checker.state.collectAsState()
    val hasRedDot = state.hasUnreadAvailableUpdate
    val trailingText = when (state) {
        is UpdateState.Available -> "v${(state as UpdateState.Available).update.info.version} 可用"
        is UpdateState.Checking -> "检查中…"
        is UpdateState.UpToDate -> "已是最新版本"
        is UpdateState.Failed -> "检查失败，点击重试"
        else -> "前往检查"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(96))
            .clickable { viewModel.checkNow() }
            .padding(horizontal = rpx(24)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "检查更新",
            fontSize = FontSizes.content,
            color = MusicFreeTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        if (hasRedDot) {
            UpdateBadgeDot()
        } else {
            Text(
                text = trailingText,
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.textSecondary,
            )
        }
    }
}
```

- [ ] **Step 3: 编译 :core 与 :feature:settings**

```bash
./gradlew :core:compileDebugKotlin :feature:settings:compileDebugKotlin
```

期望：通过。

- [ ] **Step 4: 提交**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/UpdateBadgeDot.kt \
        feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/CheckUpdateRow.kt
git commit -m "refactor(core): 提取 UpdateBadgeDot 并迁移 settings 检查更新行"
```

---

## Task 10: HomeDrawerItemUiModel 加 hasBadge + DrawerRow 渲染

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt`

- [ ] **Step 1: 改 `HomeDrawerItemUiModel` 加 `hasBadge` 字段、改 action 名、`buildHomeDrawerUiModel` 接受 update state 派生 trailing/badge**

修改 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`：

把 `sealed interface HomeDrawerAction { ... }` 块改成（删除 `ShowUpdateCheckDialog`，新增 `TriggerManualUpdateCheck`）：

```kotlin
sealed interface HomeDrawerAction {
    data object OpenListenStats : HomeDrawerAction
    data object OpenSettingsRoot : HomeDrawerAction
    data object OpenPluginManagement : HomeDrawerAction
    data object OpenThemeSettings : HomeDrawerAction
    data object ShowScheduleClosePanel : HomeDrawerAction
    data object OpenBackup : HomeDrawerAction
    data object OpenPermissions : HomeDrawerAction
    data object TriggerManualUpdateCheck : HomeDrawerAction
    data object OpenAbout : HomeDrawerAction
}
```

把 `data class HomeDrawerItemUiModel(...)` 增字段：

```kotlin
data class HomeDrawerItemUiModel(
    val title: String,
    @param:DrawableRes
    @field:DrawableRes
    val iconRes: Int,
    val anchorTag: String,
    val trailingText: String? = null,
    val hasBadge: Boolean = false,
    val action: HomeDrawerAction,
)
```

把 `buildHomeDrawerUiModel` 函数签名扩成接受 update state 信息（trailing + badge），完全替换函数体为：

```kotlin
fun buildHomeDrawerUiModel(
    currentVersion: String,
    scheduleCloseSummary: String,
    updateTrailingText: String = currentVersion,
    hasUpdateBadge: Boolean = false,
): HomeDrawerUiModel = HomeDrawerUiModel(
    sections = listOf(
        HomeDrawerSectionUiModel(
            sectionKey = "me",
            title = "我的",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "听歌足迹",
                    iconRes = HomeIcons.DrawerListenStats,
                    anchorTag = FidelityAnchors.Home.DrawerMeListenStats,
                    action = HomeDrawerAction.OpenListenStats,
                ),
            ),
        ),
        HomeDrawerSectionUiModel(
            sectionKey = "setting",
            title = "设置",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "基础设置",
                    iconRes = HomeIcons.DrawerSettings,
                    anchorTag = FidelityAnchors.Home.DrawerSettingsBasic,
                    action = HomeDrawerAction.OpenSettingsRoot,
                ),
                HomeDrawerItemUiModel(
                    title = "插件管理",
                    iconRes = HomeIcons.DrawerPluginManagement,
                    anchorTag = FidelityAnchors.Home.DrawerSettingsPlugin,
                    action = HomeDrawerAction.OpenPluginManagement,
                ),
                HomeDrawerItemUiModel(
                    title = "主题设置",
                    iconRes = HomeIcons.DrawerThemeSettings,
                    anchorTag = FidelityAnchors.Home.DrawerSettingsTheme,
                    action = HomeDrawerAction.OpenThemeSettings,
                ),
            ),
        ),
        HomeDrawerSectionUiModel(
            sectionKey = "other",
            title = "其他",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "定时关闭",
                    iconRes = HomeIcons.DrawerScheduleClose,
                    anchorTag = FidelityAnchors.Home.DrawerOtherScheduleClose,
                    trailingText = scheduleCloseSummary.ifBlank { null },
                    action = HomeDrawerAction.ShowScheduleClosePanel,
                ),
                HomeDrawerItemUiModel(
                    title = "备份与恢复",
                    iconRes = HomeIcons.DrawerBackup,
                    anchorTag = FidelityAnchors.Home.DrawerOtherBackup,
                    action = HomeDrawerAction.OpenBackup,
                ),
                HomeDrawerItemUiModel(
                    title = "权限管理",
                    iconRes = HomeIcons.DrawerPermissions,
                    anchorTag = FidelityAnchors.Home.DrawerOtherPermissions,
                    action = HomeDrawerAction.OpenPermissions,
                ),
            ),
        ),
        HomeDrawerSectionUiModel(
            sectionKey = "software",
            title = "软件",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "检查更新",
                    iconRes = HomeIcons.DrawerCheckUpdate,
                    anchorTag = FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
                    trailingText = updateTrailingText,
                    hasBadge = hasUpdateBadge,
                    action = HomeDrawerAction.TriggerManualUpdateCheck,
                ),
                HomeDrawerItemUiModel(
                    title = "关于 MusicFree",
                    iconRes = HomeIcons.DrawerAbout,
                    anchorTag = FidelityAnchors.Home.DrawerSoftwareAbout,
                    action = HomeDrawerAction.OpenAbout,
                ),
            ),
        ),
    ),
    footerActions = emptyList(),
)
```

注意：保持除「检查更新」外的所有其他 items 与原文件完全一致（你可在改之前 `git diff` 对照确认；这里上面已展示全量）。

- [ ] **Step 2: 改 `HomeDrawerContent.kt` 让 `DrawerRow` 用 `item.hasBadge`，去掉 `hasUpdateRedDot` 链**

修改 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt`：

去掉 `HomeDrawerContent` 的 `hasUpdateRedDot: Boolean = false` 参数：

```kotlin
@Composable
fun HomeDrawerContent(
    uiModel: HomeDrawerUiModel,
    onEntryClick: (HomeDrawerAction) -> Unit,
    modifier: Modifier = Modifier,
    statusBarTopPadding: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
) {
```

并把对 `DrawerSection` 的调用同步去掉 `hasUpdateRedDot = hasUpdateRedDot`。

把 `DrawerSection` 签名改为：

```kotlin
@Composable
private fun DrawerSection(
    section: HomeDrawerSectionUiModel,
    onEntryClick: (HomeDrawerAction) -> Unit,
) {
```

体内 `DrawerRow` 调用改成：

```kotlin
        section.items.forEach { item ->
            DrawerRow(
                item = item,
                onClick = { onEntryClick(item.action) },
            )
        }
```

把 `DrawerRow` 签名改为：

```kotlin
@Composable
private fun DrawerRow(
    item: HomeDrawerItemUiModel,
    onClick: () -> Unit,
) {
```

体内 `showRedDot` 判定改成：

```kotlin
        if (item.hasBadge) {
            DrawerRedDot()
        } else {
            item.trailingText?.takeIf { it.isNotBlank() }?.let { trailingText ->
                Text(
                    text = trailingText,
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.subTitle,
                )
            }
        }
```

`DrawerRedDot` 私有 composable 保持原样（依旧本地，避免和 `:core` 跨模块依赖循环；如果想要复用 `UpdateBadgeDot`，看 `HomeDrawerContent` 当前 import 链是否已经依赖 `:core`——已经依赖（`MusicFreeTheme`）。可直接用 `com.zili.android.musicfreeandroid.core.ui.UpdateBadgeDot`，把 `DrawerRedDot()` 删除并改调用 `UpdateBadgeDot()`）。本 task 选择复用——删 `DrawerRedDot` 定义，导入 `UpdateBadgeDot`：

```kotlin
import com.zili.android.musicfreeandroid.core.ui.UpdateBadgeDot
```

并把 `if (item.hasBadge) { UpdateBadgeDot() } else { ... }`。

- [ ] **Step 3: 编译 :feature:home**

```bash
./gradlew :feature:home:compileDebugKotlin
```

期望：通过。注意：`buildHomeDrawerUiModel` 调用方（HomeScreen 或 ViewModel）可能因签名变更失败——这在 Task 11 解决，本 task 只确保 module 级 compile 自洽。如失败，先 `git stash` 调用点改动，本 task commit 通过；调用点放 Task 11 一起搞。

实际上 `buildHomeDrawerUiModel` 新增的两个参数都有默认值，调用方不传也能编译。所以本 task 应能直接通过。

- [ ] **Step 4: 提交**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt
git commit -m "feat(home): 抽屉 item 支持 hasBadge 并切换检查更新 action"
```

---

## Task 11: 把 UpdateBadgeViewModel state 接入抽屉 + 替换检查更新 dialog

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt`
- Modify: 抽屉调用方（按下面 Step 1 探查结果决定，可能是 `feature/home/HomeScreen.kt` 或 `HomeRoute.kt` 或 NavHost 内入口）

- [ ] **Step 1: 探查 buildHomeDrawerUiModel 的调用方**

```bash
grep -rn "buildHomeDrawerUiModel" feature/ app/ 2>/dev/null
```

预期：1-2 个调用点，通常在 `HomeScreen.kt` 或 `HomeRoute.kt` 内（取决于现状）。后续 step 假设调用点是 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeRoute.kt`（若不是，按实际文件做同等改动）。

记下文件路径，下面 step 用 `<CALLER>` 表示。

- [ ] **Step 2: 改 `HomeDrawerDialogs.kt`，把 isUpdateCheckVisible 分支替换为 ManualUpdateDialog**

完全替换 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.window.Dialog
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.ui.ManualUpdateDialog

@Composable
fun HomeDrawerDialogs(
    isTimingCloseVisible: Boolean,
    isUpdateCheckVisible: Boolean,
    currentVersion: String,
    scheduleCloseSummary: String,
    checker: UpdateChecker,
    downloader: ApkDownloader,
    installer: ApkInstaller,
    onDismissTimingClose: () -> Unit,
    onDismissUpdateCheck: () -> Unit,
) {
    if (isTimingCloseVisible) {
        TimingClosePanel(
            scheduleCloseSummary = scheduleCloseSummary,
            onDismiss = onDismissTimingClose,
        )
    }

    if (isUpdateCheckVisible) {
        Box(
            modifier = Modifier
                .testTag(FidelityAnchors.Dialog.UpdateCheckRoot)
                .semantics { testTagsAsResourceId = true },
        ) {
            ManualUpdateDialog(
                checker = checker,
                downloader = downloader,
                installer = installer,
                localVersionName = currentVersion,
                onDismiss = onDismissUpdateCheck,
            )
        }
    }
}

@Composable
private fun TimingClosePanel(
    scheduleCloseSummary: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MusicFreeTheme.colors.mask),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(20), vertical = rpx(24))
                    .testTag(FidelityAnchors.Panel.TimingCloseRoot)
                    .semantics { testTagsAsResourceId = true },
                shape = RoundedCornerShape(rpx(24)),
                color = MusicFreeTheme.colors.pageBackground,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(20)),
                    verticalArrangement = Arrangement.spacedBy(rpx(16)),
                ) {
                    Text(
                        text = "定时关闭",
                        color = MusicFreeTheme.colors.text,
                        fontSize = FontSizes.title,
                    )
                    Text(
                        text = scheduleCloseSummary.ifBlank { "暂未设置倒计时" },
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.subTitle,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}
```

要点：
- `HomeDrawerDialogs` 签名新增 `checker / downloader / installer`，调用方需在 step 4 注入。
- 删除原 `InfoDialog` 调用，整个 `isUpdateCheckVisible` 改为 `ManualUpdateDialog`。
- 保留 `FidelityAnchors.Dialog.UpdateCheckRoot` testTag（外层 Box 上），保仪器测试可定位。

- [ ] **Step 3: 改 buildHomeDrawerUiModel 调用方注入 updateTrailingText / hasUpdateBadge / 注入 ManualUpdateDialog 依赖**

修改 `<CALLER>`（前面 grep 找到的文件）：

1. 注入 `UpdateBadgeViewModel`：用 `hiltViewModel<UpdateBadgeViewModel>()` 拿到 viewModel。
2. 订阅 `updateBadgeViewModel.checker.state.collectAsState()` 拿 `UpdateState`。
3. 派生 `updateTrailingText` 与 `hasUpdateBadge`：

```kotlin
val updateState by updateBadgeViewModel.checker.state.collectAsState()
val updateTrailingText = when (val s = updateState) {
    is UpdateState.Available -> "v${s.update.info.version} 可用"
    is UpdateState.Checking -> "检查中…"
    is UpdateState.UpToDate -> currentVersion
    is UpdateState.Failed -> "检查失败"
    else -> currentVersion
}
val hasUpdateBadge = updateState.hasUnreadAvailableUpdate
```

4. 把 `buildHomeDrawerUiModel(...)` 调用扩成传 `updateTrailingText` 与 `hasUpdateBadge`：

```kotlin
val drawerUiModel = buildHomeDrawerUiModel(
    currentVersion = currentVersion,
    scheduleCloseSummary = scheduleCloseSummary,
    updateTrailingText = updateTrailingText,
    hasUpdateBadge = hasUpdateBadge,
)
```

5. 给 `HomeDrawerDialogs(...)` 注入 `checker / downloader / installer`：

```kotlin
HomeDrawerDialogs(
    isTimingCloseVisible = isTimingCloseVisible,
    isUpdateCheckVisible = isUpdateCheckVisible,
    currentVersion = currentVersion,
    scheduleCloseSummary = scheduleCloseSummary,
    checker = updateBadgeViewModel.checker,
    downloader = updateBadgeViewModel.downloader,
    installer = updateBadgeViewModel.installer,
    onDismissTimingClose = onDismissTimingClose,
    onDismissUpdateCheck = onDismissUpdateCheck,
)
```

6. 把抽屉 action 处理代码里 `HomeDrawerAction.ShowUpdateCheckDialog` 改成 `HomeDrawerAction.TriggerManualUpdateCheck`，handler 先 `updateBadgeViewModel.checker.checkManually()`，再切 `isUpdateCheckVisible = true`：

```kotlin
HomeDrawerAction.TriggerManualUpdateCheck -> {
    updateBadgeViewModel.checker.checkManually()
    isUpdateCheckVisible = true
}
```

- [ ] **Step 4: 给 `UpdateBadgeViewModel` 暴露 downloader 与 installer（保持 :feature:home 不直接依赖 :updater downloader API）**

修改 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/UpdateBadgeViewModel.kt` 为：

```kotlin
package com.zili.android.musicfreeandroid.feature.home

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpdateBadgeViewModel @Inject constructor(
    val checker: UpdateChecker,
    val downloader: ApkDownloader,
    val installer: ApkInstaller,
) : ViewModel()
```

依赖 module gradle 已经引入 `:updater`（通过 `feature/home/build.gradle.kts`）；若 `ApkDownloader / ApkInstaller` 不在 home 模块 classpath，需要确认 home 模块的 `implementation(project(":updater"))`。一般已存在，否则补一条。

- [ ] **Step 5: 改 HomeEntryNavigationTest 期望 ManualUpdateDialog**

修改 `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt`：

```bash
grep -n "checkUpdateEntry_opensUpdateCheckDialog" app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt
```

读 124 行附近 test，把断言改成：

- 点击「检查更新」入口（`FidelityAnchors.Home.DrawerSoftwareCheckUpdate`）后，期望 `FidelityAnchors.Dialog.UpdateCheckRoot` 可见，且 dialog 内能找到「正在检查更新」或「已是最新」文案（任一）。
- 因为 ManualUpdateDialog 是 state-driven，可在测试里临时把 UpdateChecker 状态先置 `Idle`，点击后能看到 `Checking` 或 `UpToDate` dialog 文本。
- 具体 assertion 改动如下：

```kotlin
@Test
fun checkUpdateEntry_opensManualUpdateDialog() {
    openDrawerDestination(FidelityAnchors.Home.DrawerSoftwareCheckUpdate)
    composeTestRule.onNode(hasTestTag(FidelityAnchors.Dialog.UpdateCheckRoot))
        .assertExists()
    // dialog 内至少应能 render 一个状态文案（Checking / UpToDate / Failed / Available 等）
    composeTestRule.onNode(
        hasText("正在检查更新", substring = true)
            .or(hasText("已是最新版本", substring = true))
            .or(hasText("发现新版本", substring = true))
            .or(hasText("检查更新失败", substring = true))
    ).assertExists()
}
```

若原测试方法名是 `checkUpdateEntry_opensUpdateCheckDialog`，**保留方法名**，只改 body。

- [ ] **Step 6: 编译 + 跑 :app + :feature:home 单测**

```bash
./gradlew :app:assembleDebug :feature:home:testDebugUnitTest
```

期望：通过。

> `HomeEntryNavigationTest` 是 instrumentation，本机若无设备/模拟器可跳过；CI 端 connectedAndroidTest 才会跑。

- [ ] **Step 7: 提交**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/UpdateBadgeViewModel.kt \
        app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt \
        <CALLER>   # 实际文件路径
git commit -m "feat(home): 侧栏检查更新接通 UpdateChecker 并显红点"
```

---

## Task 12: CI workflow — build-release-apk job 改造（双 APK + sha256/size + mapping）

**Files:**
- Modify: `.github/workflows/android-release-apk.yml`

- [ ] **Step 1: 改 build-release-apk job outputs 与所有 ABI 相关 step**

修改 `.github/workflows/android-release-apk.yml`，把 `build-release-apk` job 的 outputs 块、`Name APK` step、`Compute APK sha256 + size` step、`Upload Release APK artifact` step 全部替换。新版 job 完整内容（替换从 `build-release-apk:` 到下一个 job `publish-github-release:` 之前的全部内容）为：

```yaml
  build-release-apk:
    name: Build Release APK
    runs-on: ubuntu-latest
    environment: release
    permissions:
      contents: read
    outputs:
      apk-arm64-name: ${{ steps.name-apk.outputs.apk_arm64_v8a_name }}
      apk-arm64-sha256: ${{ steps.apk-meta.outputs.sha256_arm64_v8a }}
      apk-arm64-size: ${{ steps.apk-meta.outputs.size_arm64_v8a }}
      apk-x86-64-name: ${{ steps.name-apk.outputs.apk_x86_64_name }}
      apk-x86-64-sha256: ${{ steps.apk-meta.outputs.sha256_x86_64 }}
      apk-x86-64-size: ${{ steps.apk-meta.outputs.size_x86_64 }}
      mapping-name: ${{ steps.pack-mapping.outputs.mapping_name }}
      mapping-sha256: ${{ steps.pack-mapping.outputs.mapping_sha256 }}

    steps:
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0

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

      - name: Check for new commits (nightly only)
        id: nightly-guard
        if: github.event_name == 'schedule'
        run: |
          new_commits=$(git log --since="24 hours ago" --oneline origin/main | wc -l | tr -d ' ')
          echo "new_commits=$new_commits" >> "$GITHUB_OUTPUT"
          if [ "$new_commits" -eq 0 ]; then
            echo "::notice::No new commits in last 24h on main; skipping nightly build."
          fi

      - name: Set up JDK 21
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Gradle
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        uses: gradle/actions/setup-gradle@v5

      - name: Set up Android SDK
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        uses: android-actions/setup-android@v4.0.1

      - name: Validate release secrets
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        env:
          ANDROID_RELEASE_KEYSTORE_BASE64: ${{ secrets.ANDROID_RELEASE_KEYSTORE_BASE64 }}
          ANDROID_RELEASE_STORE_PASSWORD: ${{ secrets.ANDROID_RELEASE_STORE_PASSWORD }}
          ANDROID_RELEASE_KEY_ALIAS: ${{ secrets.ANDROID_RELEASE_KEY_ALIAS }}
          ANDROID_RELEASE_KEY_PASSWORD: ${{ secrets.ANDROID_RELEASE_KEY_PASSWORD }}
          LOGAN_AES_KEY: ${{ secrets.LOGAN_AES_KEY }}
          LOGAN_AES_IV: ${{ secrets.LOGAN_AES_IV }}
        run: |
          missing=0
          for name in \
            ANDROID_RELEASE_KEYSTORE_BASE64 \
            ANDROID_RELEASE_STORE_PASSWORD \
            ANDROID_RELEASE_KEY_ALIAS \
            ANDROID_RELEASE_KEY_PASSWORD \
            LOGAN_AES_KEY \
            LOGAN_AES_IV
          do
            if [ -z "${!name}" ]; then
              echo "::error::$name is not configured in the GitHub release Environment"
              missing=1
            fi
          done
          exit "$missing"

      - name: Decode release keystore
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        env:
          ANDROID_RELEASE_KEYSTORE_BASE64: ${{ secrets.ANDROID_RELEASE_KEYSTORE_BASE64 }}
        run: |
          printf '%s' "$ANDROID_RELEASE_KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/release.jks"
          chmod 600 "$RUNNER_TEMP/release.jks"

      - name: Build Release APK
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        env:
          ANDROID_RELEASE_KEYSTORE_PATH: ${{ runner.temp }}/release.jks
          ANDROID_RELEASE_STORE_PASSWORD: ${{ secrets.ANDROID_RELEASE_STORE_PASSWORD }}
          ANDROID_RELEASE_KEY_ALIAS: ${{ secrets.ANDROID_RELEASE_KEY_ALIAS }}
          ANDROID_RELEASE_KEY_PASSWORD: ${{ secrets.ANDROID_RELEASE_KEY_PASSWORD }}
          LOGAN_AES_KEY: ${{ secrets.LOGAN_AES_KEY }}
          LOGAN_AES_IV: ${{ secrets.LOGAN_AES_IV }}
        run: ./gradlew :app:assembleRelease --no-daemon

      - name: Name APKs
        id: name-apk
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        run: |
          tag_or_label=""
          if [ "$GITHUB_REF_TYPE" = "tag" ]; then
            tag_or_label="$GITHUB_REF_NAME"
            suffix=""
          elif [ "$GITHUB_EVENT_NAME" = "schedule" ]; then
            tag_or_label="nightly-$(date -u +%Y%m%d)-$(git rev-parse --short HEAD)"
            suffix=""
          else
            tag_or_label="manual-${GITHUB_RUN_NUMBER}"
            suffix=""
          fi
          arm_src="app/build/outputs/apk/release/MusicFreeAndroid-arm64-v8a-release.apk"
          x64_src="app/build/outputs/apk/release/MusicFreeAndroid-x86_64-release.apk"
          if [ ! -f "$arm_src" ] || [ ! -f "$x64_src" ]; then
            echo "::error::expected per-ABI APKs not produced; saw:"
            ls -la app/build/outputs/apk/release/
            exit 1
          fi
          arm_name="MusicFreeAndroid-${tag_or_label}-arm64-v8a.apk"
          x64_name="MusicFreeAndroid-${tag_or_label}-x86_64.apk"
          cp "$arm_src" "$RUNNER_TEMP/$arm_name"
          cp "$x64_src" "$RUNNER_TEMP/$x64_name"
          {
            echo "apk_arm64_v8a_name=$arm_name"
            echo "apk_x86_64_name=$x64_name"
          } >> "$GITHUB_OUTPUT"

      - name: Compute APK sha256 + size
        id: apk-meta
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        run: |
          arm_apk="$RUNNER_TEMP/${{ steps.name-apk.outputs.apk_arm64_v8a_name }}"
          x64_apk="$RUNNER_TEMP/${{ steps.name-apk.outputs.apk_x86_64_name }}"
          {
            echo "sha256_arm64_v8a=$(sha256sum "$arm_apk" | awk '{print $1}')"
            echo "size_arm64_v8a=$(wc -c < "$arm_apk")"
            echo "sha256_x86_64=$(sha256sum "$x64_apk" | awk '{print $1}')"
            echo "size_x86_64=$(wc -c < "$x64_apk")"
          } >> "$GITHUB_OUTPUT"

      - name: Pack mapping
        id: pack-mapping
        if: github.ref_type == 'tag'
        run: |
          mapping_src="app/build/outputs/mapping/release/mapping.txt"
          if [ ! -f "$mapping_src" ]; then
            echo "::error::expected mapping.txt not produced at $mapping_src"
            exit 1
          fi
          mkdir -p "$RUNNER_TEMP/mapping"
          cp "$mapping_src" "$RUNNER_TEMP/mapping/"
          mapping_name="mapping-${GITHUB_REF_NAME}.zip"
          (cd "$RUNNER_TEMP" && zip -9q "$mapping_name" mapping/mapping.txt)
          sha=$(sha256sum "$RUNNER_TEMP/$mapping_name" | awk '{print $1}')
          {
            echo "mapping_name=$mapping_name"
            echo "mapping_sha256=$sha"
          } >> "$GITHUB_OUTPUT"

      - name: Upload Release APK artifact (arm64-v8a)
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        uses: actions/upload-artifact@v7
        with:
          name: MusicFreeAndroid-release-apk-arm64-v8a
          path: ${{ runner.temp }}/${{ steps.name-apk.outputs.apk_arm64_v8a_name }}
          if-no-files-found: error
          retention-days: 14

      - name: Upload Release APK artifact (x86_64)
        if: github.event_name != 'schedule' || steps.nightly-guard.outputs.new_commits != '0'
        uses: actions/upload-artifact@v7
        with:
          name: MusicFreeAndroid-release-apk-x86_64
          path: ${{ runner.temp }}/${{ steps.name-apk.outputs.apk_x86_64_name }}
          if-no-files-found: error
          retention-days: 14

      - name: Upload mapping artifact
        if: github.ref_type == 'tag'
        uses: actions/upload-artifact@v7
        with:
          name: MusicFreeAndroid-release-mapping
          path: ${{ runner.temp }}/${{ steps.pack-mapping.outputs.mapping_name }}
          if-no-files-found: error
          retention-days: 90
```

要点：
- job outputs 暴露 6 + 2 个键。
- `Name APKs` 严格检查 splits 输出，缺失 fail。
- `Pack mapping` 仅 tag 路径。
- 三个独立 artifact，retention 14d/14d/90d。

- [ ] **Step 2: 本地 lint workflow（可选）**

```bash
# 通过 actionlint 检查（如未装：brew install actionlint）
actionlint .github/workflows/android-release-apk.yml || echo "actionlint not installed; skipping"
```

- [ ] **Step 3: 提交**

```bash
git add .github/workflows/android-release-apk.yml
git commit -m "ci(release): build-release-apk 产出双 ABI + mapping 三 artifact"
```

---

## Task 13: CI workflow — publish-github-release & build-version-json.sh v2

**Files:**
- Modify: `.github/workflows/android-release-apk.yml` (publish-github-release + publish-version-manifest jobs)
- Modify: `scripts/release/build-version-json.sh`

- [ ] **Step 1: 改 publish-github-release job — 下载三个 artifact 并上传 3 个 asset**

替换 workflow 中 `publish-github-release` job 内 `Download Release APK artifact` 与 `Create or update GitHub Release` 两个 step 之间的所有内容（保留前面的 `Generate release notes` / `Upload release notes artifact` / `Prepend CHANGELOG.md and push main`）。新 job 全文：

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

      - name: Download Release APK artifact (arm64-v8a)
        uses: actions/download-artifact@v7
        with:
          name: MusicFreeAndroid-release-apk-arm64-v8a
          path: release-apk-arm64

      - name: Download Release APK artifact (x86_64)
        uses: actions/download-artifact@v7
        with:
          name: MusicFreeAndroid-release-apk-x86_64
          path: release-apk-x86_64

      - name: Download mapping artifact
        uses: actions/download-artifact@v7
        with:
          name: MusicFreeAndroid-release-mapping
          path: release-mapping

      - name: Generate release notes
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          prev=$(git describe --tags --abbrev=0 "${GITHUB_REF_NAME}^" 2>/dev/null \
                 || git rev-list --max-parents=0 HEAD | tail -1)
          bash scripts/release/generate-notes.sh "$prev" "$GITHUB_REF_NAME" > release_notes.md
          # 追加构建产物矩阵
          arm_apk="${{ needs.build-release-apk.outputs.apk-arm64-name }}"
          x64_apk="${{ needs.build-release-apk.outputs.apk-x86-64-name }}"
          map_zip="${{ needs.build-release-apk.outputs.mapping-name }}"
          arm_size_human=$(numfmt --to=iec --suffix=B "${{ needs.build-release-apk.outputs.apk-arm64-size }}")
          x64_size_human=$(numfmt --to=iec --suffix=B "${{ needs.build-release-apk.outputs.apk-x86-64-size }}")
          arm_sha_short=$(echo "${{ needs.build-release-apk.outputs.apk-arm64-sha256 }}" | cut -c1-12)
          x64_sha_short=$(echo "${{ needs.build-release-apk.outputs.apk-x86-64-sha256 }}" | cut -c1-12)
          map_sha_short=$(echo "${{ needs.build-release-apk.outputs.mapping-sha256 }}" | cut -c1-12)
          {
            echo ""
            echo "### 构建产物"
            echo ""
            echo "- arm64-v8a: \`${arm_apk}\` · ${arm_size_human} · sha256 \`${arm_sha_short}\`"
            echo "- x86_64: \`${x64_apk}\` · ${x64_size_human} · sha256 \`${x64_sha_short}\`"
            echo "- mapping: \`${map_zip}\` · sha256 \`${map_sha_short}\` (R8 反混淆用)"
            echo ""
            echo "> 老版本（v1.0.x）客户端首次升级时无法自动识别新 manifest，请前往本页面手动下载与设备架构对应的 APK。"
          } >> release_notes.md
          head -60 release_notes.md

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
          arm_path="release-apk-arm64/${{ needs.build-release-apk.outputs.apk-arm64-name }}"
          x64_path="release-apk-x86_64/${{ needs.build-release-apk.outputs.apk-x86-64-name }}"
          map_path="release-mapping/${{ needs.build-release-apk.outputs.mapping-name }}"
          if gh release view "$GITHUB_REF_NAME" >/dev/null 2>&1; then
            gh release upload "$GITHUB_REF_NAME" "$arm_path" "$x64_path" "$map_path" --clobber
            gh release edit "$GITHUB_REF_NAME" --notes-file release_notes.md
          else
            gh release create "$GITHUB_REF_NAME" "$arm_path" "$x64_path" "$map_path" \
              --title "$GITHUB_REF_NAME" \
              --notes-file release_notes.md
          fi
```

- [ ] **Step 2: 改 publish-version-manifest job 用 --variant 多参**

替换 workflow 中 `publish-version-manifest` job 内 `Build version.json` step 为：

```yaml
      - name: Build version.json
        run: |
          mkdir -p gh-pages/release
          vcode=$(awk -F= '/^versionCode/{print $2}' source/version.properties | tr -d '[:space:]')
          bash source/scripts/release/build-version-json.sh \
              --version "${GITHUB_REF_NAME#v}" \
              --version-code "$vcode" \
              --tag "$GITHUB_REF_NAME" \
              --variant "arm64-v8a=${{ needs.build-release-apk.outputs.apk-arm64-name }},${{ needs.build-release-apk.outputs.apk-arm64-sha256 }},${{ needs.build-release-apk.outputs.apk-arm64-size }}" \
              --variant "x86_64=${{ needs.build-release-apk.outputs.apk-x86-64-name }},${{ needs.build-release-apk.outputs.apk-x86-64-sha256 }},${{ needs.build-release-apk.outputs.apk-x86-64-size }}" \
              --mapping-name "${{ needs.build-release-apk.outputs.mapping-name }}" \
              --mapping-sha256 "${{ needs.build-release-apk.outputs.mapping-sha256 }}" \
              --notes source/release_notes.md \
              > gh-pages/release/version.json
          jq . gh-pages/release/version.json
```

- [ ] **Step 3: 改 build-version-json.sh 支持 v2 schema**

先读现状：

```bash
cat scripts/release/build-version-json.sh
```

完全替换 `scripts/release/build-version-json.sh` 为以下版本（保留原 helper 风格）：

```bash
#!/usr/bin/env bash
# Build version.json (schemaVersion = 2) for release/manifest publishing.
#
# Usage:
#   build-version-json.sh \
#     --version 1.2.3 \
#     --version-code 10203 \
#     --tag v1.2.3 \
#     --variant "arm64-v8a=MusicFreeAndroid-v1.2.3-arm64-v8a.apk,abc...,12345" \
#     --variant "x86_64=MusicFreeAndroid-v1.2.3-x86_64.apk,def...,12346" \
#     --mapping-name "mapping-v1.2.3.zip" \
#     --mapping-sha256 "9c4e..." \
#     --notes /tmp/release_notes.md \
#     [--no-jsdelivr]   # 默认带 jsdelivr 镜像；本 flag 关闭
#
# Output: 一个完整的 JSON 到 stdout。
set -euo pipefail

repo="${GITHUB_REPOSITORY:-hanklzl/MusicFreeAndroid}"
include_jsdelivr=1
version=""
version_code=""
tag=""
mapping_name=""
mapping_sha256=""
notes_file=""
declare -a variant_args=()

while [ $# -gt 0 ]; do
    case "$1" in
        --version)         version="$2"; shift 2 ;;
        --version-code)    version_code="$2"; shift 2 ;;
        --tag)             tag="$2"; shift 2 ;;
        --variant)         variant_args+=("$2"); shift 2 ;;
        --mapping-name)    mapping_name="$2"; shift 2 ;;
        --mapping-sha256)  mapping_sha256="$2"; shift 2 ;;
        --notes)           notes_file="$2"; shift 2 ;;
        --no-jsdelivr)     include_jsdelivr=0; shift ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

[ -n "$version" ] || { echo "--version required" >&2; exit 1; }
[ -n "$version_code" ] || { echo "--version-code required" >&2; exit 1; }
[ -n "$tag" ] || { echo "--tag required" >&2; exit 1; }
[ "${#variant_args[@]}" -gt 0 ] || { echo "at least one --variant required" >&2; exit 1; }

released_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
release_notes_url="https://github.com/${repo}/releases/tag/${tag}"

# changeLog: 从 notes 文件抓首段 H2 以下的列表项，最多 8 行
declare -a change_log
if [ -n "$notes_file" ] && [ -f "$notes_file" ]; then
    while IFS= read -r line; do
        case "$line" in
            "- "*)
                clean="${line:2}"
                change_log+=("$clean")
                if [ "${#change_log[@]}" -ge 8 ]; then break; fi
                ;;
        esac
    done < "$notes_file"
fi

# 构造 variants JSON
variants_json="{}"
for v in "${variant_args[@]}"; do
    abi="${v%%=*}"
    rest="${v#*=}"
    IFS=',' read -r apk_name sha256 size <<< "$rest"
    gh_url="https://github.com/${repo}/releases/download/${tag}/${apk_name}"
    download_list=$(jq -n --arg gh "$gh_url" '[$gh]')
    if [ "$include_jsdelivr" = "1" ]; then
        jd_url="https://cdn.jsdelivr.net/gh/${repo}@${tag}/release/${apk_name}"
        download_list=$(jq -n --arg gh "$gh_url" --arg jd "$jd_url" '[$gh, $jd]')
    fi
    variant=$(jq -n \
        --argjson download "$download_list" \
        --argjson size "$size" \
        --arg sha "$sha256" \
        '{download: $download, size: $size, sha256: $sha}')
    variants_json=$(jq --arg key "$abi" --argjson v "$variant" '. + {($key): $v}' <<< "$variants_json")
done

# 构造 mapping
mapping_json="null"
if [ -n "$mapping_name" ] && [ -n "$mapping_sha256" ]; then
    mapping_url="https://github.com/${repo}/releases/download/${tag}/${mapping_name}"
    mapping_json=$(jq -n --arg url "$mapping_url" --arg sha "$mapping_sha256" '{url: $url, sha256: $sha}')
fi

# 序列化 changeLog
change_log_json=$(printf '%s\n' "${change_log[@]:-}" | jq -R . | jq -s 'map(select(length > 0))')

jq -n \
    --argjson schemaVersion 2 \
    --arg version "$version" \
    --argjson versionCode "$version_code" \
    --arg releasedAt "$released_at" \
    --arg releaseNotesUrl "$release_notes_url" \
    --argjson changeLog "$change_log_json" \
    --argjson variants "$variants_json" \
    --argjson mapping "$mapping_json" \
    '{
        schemaVersion: $schemaVersion,
        version: $version,
        versionCode: $versionCode,
        releasedAt: $releasedAt,
        releaseNotesUrl: $releaseNotesUrl,
        changeLog: $changeLog,
        variants: $variants
    } + (if $mapping == null then {} else {mapping: $mapping} end)'
```

- [ ] **Step 4: 本地 dry-run 验证脚本**

```bash
# 用假数据跑一次，看输出 JSON 是否合法
echo "- 新增功能 A" > /tmp/notes.md
echo "- 修复 B" >> /tmp/notes.md
GITHUB_REPOSITORY=hanklzl/MusicFreeAndroid \
  bash scripts/release/build-version-json.sh \
    --version 1.2.3 \
    --version-code 10203 \
    --tag v1.2.3 \
    --variant "arm64-v8a=MusicFreeAndroid-v1.2.3-arm64-v8a.apk,aaaa,1000" \
    --variant "x86_64=MusicFreeAndroid-v1.2.3-x86_64.apk,bbbb,2000" \
    --mapping-name "mapping-v1.2.3.zip" \
    --mapping-sha256 "cccc" \
    --notes /tmp/notes.md \
  | jq .
```

期望：输出含 `"schemaVersion": 2`、`variants.arm64-v8a.download[0]` 指向 github releases、`mapping.url` 指向 release asset。

- [ ] **Step 5: 提交**

```bash
git add .github/workflows/android-release-apk.yml \
        scripts/release/build-version-json.sh
git commit -m "ci(release): publish 上传双 APK + mapping 并切到 v2 manifest"
```

---

## Task 14: 改 preflight.sh 适配双 APK + mapping

**Files:**
- Modify: `scripts/release/preflight.sh`

- [ ] **Step 1: 读现状**

```bash
cat scripts/release/preflight.sh
```

- [ ] **Step 2: 重写 preflight.sh**

完全替换 `scripts/release/preflight.sh` 为：

```bash
#!/usr/bin/env bash
# 本地干跑：模拟 CI 的关键 step，验证 release tag 推送前的所有条件就绪。
# 用法：bash scripts/release/preflight.sh vX.Y.Z
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 vX.Y.Z" >&2
    exit 1
fi
tag="$1"
expected="${tag#v}"

cd "$(git rev-parse --show-toplevel)"

echo "[dry] Validate version consistency"
actual=$(awk -F= '/^versionName/{print $2}' version.properties | tr -d '[:space:]')
[ "$expected" = "$actual" ] || {
    echo "::error::tag $tag vs versionName $actual mismatch" >&2
    exit 1
}
echo "  OK: $tag ↔ versionName=$actual"

echo "[dry] Build Release APK"
if [ -n "${ANDROID_RELEASE_KEYSTORE_PATH:-}" ] && [ -f "${ANDROID_RELEASE_KEYSTORE_PATH}" ]; then
    ./gradlew clean :app:assembleRelease --no-daemon
else
    echo "  WARN: ANDROID_RELEASE_KEYSTORE_PATH not set; skipping real Release build" >&2
    echo "  (CI 必跑；本地若无签名 env 跳过)"
fi
arm_apk="app/build/outputs/apk/release/MusicFreeAndroid-arm64-v8a-release.apk"
x64_apk="app/build/outputs/apk/release/MusicFreeAndroid-x86_64-release.apk"
mapping_src="app/build/outputs/mapping/release/mapping.txt"

if [ ! -f "$arm_apk" ] || [ ! -f "$x64_apk" ]; then
    echo "  WARN: per-ABI Release APK not present; downstream steps using stub data" >&2
    arm_apk=""; x64_apk=""; mapping_src=""
fi

echo "[dry] Compute APK sha256 + size"
sha_arm=""; size_arm=""; sha_x64=""; size_x64=""
if [ -n "$arm_apk" ]; then
    sha_arm=$(sha256sum "$arm_apk" | awk '{print $1}')
    size_arm=$(wc -c < "$arm_apk")
    echo "  arm64-v8a: sha256=$sha_arm size=$size_arm"
fi
if [ -n "$x64_apk" ]; then
    sha_x64=$(sha256sum "$x64_apk" | awk '{print $1}')
    size_x64=$(wc -c < "$x64_apk")
    echo "  x86_64:    sha256=$sha_x64 size=$size_x64"
fi

echo "[dry] Pack mapping"
mapping_name=""; mapping_sha256=""
if [ -n "$mapping_src" ] && [ -f "$mapping_src" ]; then
    mkdir -p "/tmp/mf-mapping/mapping"
    cp "$mapping_src" "/tmp/mf-mapping/mapping/"
    mapping_name="mapping-${tag}.zip"
    (cd /tmp/mf-mapping && zip -9q "$mapping_name" mapping/mapping.txt)
    mapping_sha256=$(sha256sum "/tmp/mf-mapping/$mapping_name" | awk '{print $1}')
    echo "  mapping zip: /tmp/mf-mapping/${mapping_name}  sha256=$mapping_sha256"
else
    echo "  WARN: mapping.txt not present; skipping mapping pack" >&2
fi

echo "[dry] Generate release notes"
prev=$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD | tail -1)
notes="/tmp/release_notes-${tag}.md"
bash scripts/release/generate-notes.sh "$prev" HEAD > "$notes"
echo "  notes -> $notes"

echo "[dry] Prepend CHANGELOG.md (dry-run)"
bash scripts/release/prepend-changelog.sh "$notes" "$tag" --dry-run > /tmp/changelog-dry.md
diff CHANGELOG.md /tmp/changelog-dry.md || true

echo "[dry] Build version.json"
if [ -n "$sha_arm" ] && [ -n "$sha_x64" ]; then
    out="/tmp/version-${tag}.json"
    bash scripts/release/build-version-json.sh \
        --version "$expected" \
        --version-code "$(awk -F= '/^versionCode/{print $2}' version.properties | tr -d '[:space:]')" \
        --tag "$tag" \
        --variant "arm64-v8a=MusicFreeAndroid-${tag}-arm64-v8a.apk,${sha_arm},${size_arm}" \
        --variant "x86_64=MusicFreeAndroid-${tag}-x86_64.apk,${sha_x64},${size_x64}" \
        ${mapping_name:+--mapping-name "$mapping_name"} \
        ${mapping_sha256:+--mapping-sha256 "$mapping_sha256"} \
        --notes "$notes" > "$out"
    jq . "$out"
    echo "  version.json -> $out"
else
    echo "  SKIP: no per-ABI hash to fill; rerun preflight after a successful Release build" >&2
fi

echo "Preflight passed."
```

- [ ] **Step 3: 本地跑一次**

```bash
cd /Users/zili/code/android/MusicFreeAndroid/.worktrees/per-abi-release
bash scripts/release/preflight.sh v0.0.0-test || true
```

期望：在没有签名 env 时也能跑完所有 step（警告而非 exit 1），最终输出 "Preflight passed."。

- [ ] **Step 4: 提交**

```bash
git add scripts/release/preflight.sh
git commit -m "ci(release): preflight 适配双 APK 与 mapping zip"
```

---

## Task 15: 更新 RELEASE.md + docs/dev-harness/INDEX.md

**Files:**
- Modify: `RELEASE.md`
- Modify: `docs/dev-harness/INDEX.md`

- [ ] **Step 1: 改 RELEASE.md**

读现状：

```bash
sed -n '1,100p' RELEASE.md
```

把「日常发布步骤」章节的「Release 已创建，notes 完整」改成（保留前后文）：

```markdown
7. 观察 [GitHub Actions](https://github.com/hanklzl/MusicFreeAndroid/actions) 完成；验证：
   - Release 已创建并含 3 个 asset：`MusicFreeAndroid-v1.2.3-arm64-v8a.apk`、`MusicFreeAndroid-v1.2.3-x86_64.apk`、`mapping-v1.2.3.zip`
   - notes 末尾「构建产物」矩阵列全
   - `main` 上有 `docs(changelog): release v1.2.3 [skip ci]` 自动 commit
   - `gh-pages/release/version.json` schemaVersion=2、`variants` 双 key、`mapping.url` 指向 release asset
   - jsdelivr 镜像可拉：
     ```bash
     curl -I https://cdn.jsdelivr.net/gh/hanklzl/MusicFreeAndroid@gh-pages/release/version.json
     ```
8. 装一台测试机冷启动验证启动 dialog → 下载 → 安装链路（arm64 与 x86_64 模拟器各一次）。
```

在「故障排查」表格末尾追加行：

```markdown
| 「设备架构不受支持」对话框 | 设备 ABI 不在 `arm64-v8a / x86_64` 内（如 32-bit only）；引导用户手动到 GitHub Release 页确认 |
| 老 v1.0.x 客户端见「请前往 GitHub 下载新版」 | schemaVersion=2 兼容路径，预期；引导手动下载对应 ABI APK |
| Release 缺少 mapping zip | 检查 build-release-apk job 的 `Pack mapping` step 是否在 tag 路径触发；mapping.txt 必须先由 R8 生成 |
```

把「本地干跑 CI step」一节里 `[dry] Build Release APK` 之后的 `ls -lh ...` 改成：

```bash
ls -lh app/build/outputs/apk/release/MusicFreeAndroid-arm64-v8a-release.apk \
       app/build/outputs/apk/release/MusicFreeAndroid-x86_64-release.apk
```

把 `[dry] Compute APK sha256 + size` 整段替换为：

```bash
for abi in arm64-v8a x86_64; do
  APK="app/build/outputs/apk/release/MusicFreeAndroid-${abi}-release.apk"
  sha256sum "$APK" | awk '{print $1}'
  wc -c < "$APK"
done
```

在 `[dry] Compute APK sha256 + size` 之后插入新段落：

```markdown
### `[dry] Pack mapping`

```bash
mkdir -p /tmp/mf-mapping/mapping
cp app/build/outputs/mapping/release/mapping.txt /tmp/mf-mapping/mapping/
(cd /tmp/mf-mapping && zip -9q "mapping-v1.2.3.zip" mapping/mapping.txt)
sha256sum /tmp/mf-mapping/mapping-v1.2.3.zip
```
```

把 `[dry] Build version.json` 调用整段替换为：

```bash
bash scripts/release/build-version-json.sh \
    --version 1.2.3 \
    --version-code 10203 \
    --tag v1.2.3 \
    --variant "arm64-v8a=MusicFreeAndroid-v1.2.3-arm64-v8a.apk,<sha_arm>,<size_arm>" \
    --variant "x86_64=MusicFreeAndroid-v1.2.3-x86_64.apk,<sha_x64>,<size_x64>" \
    --mapping-name "mapping-v1.2.3.zip" \
    --mapping-sha256 "<sha_mapping>" \
    --notes /tmp/release_notes.md \
    > /tmp/version.json
jq . /tmp/version.json
```

在「故障排查」之前（或文末）追加章节：

```markdown
## 线上崩溃反混淆

线上某次崩溃需要恢复行号 / 类名，用对应 release tag 的 mapping zip：

```bash
gh release download v1.2.3 --pattern 'mapping-*.zip' --dir /tmp/mf-retrace/
unzip /tmp/mf-retrace/mapping-v1.2.3.zip -d /tmp/mf-retrace/v1.2.3/

# CLI retrace
~/Library/Android/sdk/tools/proguard/bin/retrace.sh \
    /tmp/mf-retrace/v1.2.3/mapping/mapping.txt \
    crash.txt
# 或者 IDEA: Tools → ReTrace → 选 mapping.txt + 贴堆栈
```

mapping zip 永久存在 GitHub Release asset 上，按 tag 一一对应。
```

- [ ] **Step 2: 改 docs/dev-harness/INDEX.md**

```bash
grep -n "发布流程\|RELEASE.md\|release-pipeline-design" docs/dev-harness/INDEX.md
```

在「发布流程：见根目录 RELEASE.md 与 docs/superpowers/specs/2026-05-13-android-release-pipeline-design.md」一条之后或附近插入：

```markdown
- 分 ABI 发布与更新链路（双 APK + mapping 归档 + 侧栏检查更新）：详见 [docs/superpowers/specs/2026-05-16-per-abi-release-and-update-design.md](../superpowers/specs/2026-05-16-per-abi-release-and-update-design.md)。
```

如果原文未明示「发布流程」分块，直接在文件末尾追加上面那一行加一行上下文说明（"补充：分 ABI 发布相关"）。

- [ ] **Step 3: 提交**

```bash
git add RELEASE.md docs/dev-harness/INDEX.md
git commit -m "docs(release): RELEASE.md 与 dev-harness 索引同步 per-ABI 发布"
```

---

## Task 16: 全量编译 + 测试 + 实机/模拟器验证（人工 / CI）

**Files:** (无修改，仅验证)

- [ ] **Step 1: 全量编译**

```bash
cd /Users/zili/code/android/MusicFreeAndroid/.worktrees/per-abi-release
./gradlew :app:assembleDebug
```

期望：两个 APK 在 `app/build/outputs/apk/debug/` 下：
- `MusicFreeAndroid-arm64-v8a-debug.apk`
- `MusicFreeAndroid-x86_64-debug.apk`

- [ ] **Step 2: 全量 unit 测试**

```bash
./gradlew :updater:testDebugUnitTest :core:testDebugUnitTest :feature:home:testDebugUnitTest :feature:settings:testDebugUnitTest
```

期望：全部 PASS。

- [ ] **Step 3: 准备本地 release dry-run（可选，需签名 env）**

如果本机配了 `.env.release.local`：

```bash
source .env.release.local
./gradlew clean :app:assembleRelease --no-daemon
ls -lh app/build/outputs/apk/release/
ls -lh app/build/outputs/mapping/release/mapping.txt
```

期望：双 release APK + mapping.txt 都存在。

- [ ] **Step 4: 跑 preflight**

```bash
bash scripts/release/preflight.sh v0.0.0-test
```

期望：所有 step 通过或显式 WARN（未配签名 env 时），最终 "Preflight passed."。

- [ ] **Step 5: 实机验证（人工）**

- arm64 设备装 `MusicFreeAndroid-arm64-v8a-release.apk`（或本地 debug），冷启动正常；设置 → 检查更新 → 状态对话框正常；侧栏 → 检查更新 → ManualUpdateDialog 正常。
- x86_64 模拟器装 `MusicFreeAndroid-x86_64-release.apk`（或 debug），重复上述。
- 临时把 `gh-pages/release/version.json` 内 versionCode 改大，模拟「Available」状态，验证启动 dialog 弹出 + 侧栏红点 + 下载安装链路。

- [ ] **Step 6: 不需提交**

本 task 只验证，不修改代码。

---

## 自检（Self-Review）

完成所有 task 后：

1. **Spec 覆盖**：每个 spec §3.x 子节都至少有一个 task 覆盖：
   - §3.1（架构与改动面）→ Task 1–11
   - §3.2（构建侧）→ Task 1
   - §3.3（manifest schema v2）→ Task 2 + Task 13
   - §3.4（客户端 :updater 改造）→ Task 2–8
   - §3.5（CI 流水线）→ Task 12–14
   - §3.6（抽屉 UX & 红点）→ Task 9–11
   - §3.7（mapping 反混淆使用）→ Task 15
   - §3.10（测试策略）→ 嵌入 Task 2/3/5/6 + Task 16
   - §6.3（回滚）→ 走 commit 顺序逆向 revert，文档在 spec

2. **placeholder 扫描**：plan 内无 TBD / TODO / 「Similar to Task N」。每个 step 都给出实际代码。

3. **类型一致性**：
   - `ResolvedUpdate(info, abi, variant)` 在 model / checker / downloader / dialogs 一致
   - `UpdateInfo.SUPPORTED_SCHEMA_VERSION = 2`、`UpdateError.UnsupportedAbi` 一致
   - `HomeDrawerItemUiModel.hasBadge`、`HomeDrawerAction.TriggerManualUpdateCheck` 一致
   - `build-version-json.sh --variant abi=name,sha256,size` 与 workflow 引用一致

4. **commit 顺序**：Task 1–11 是客户端 + 构建，Task 12–14 是 CI，Task 15 是文档，Task 16 是验证。完成后在主仓库 `git merge --squash` 合 `feat/per-abi-release` 到 `main`。
