# PR 1: feature runner 基线 + runTest 迁移 + HomeFidelity 断言改写 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 先修复全量 `connectedAndroidTest` 在 feature 模块缺 runner 依赖时崩溃的问题；补齐 full androidTest 所需的 Gradle heap 基线，消除 `:plugin:mergeExtDexDebugAndroidTest` D8 OOM；修复 `PlayerControllerTest` setUp 主线程死锁；再将 Group C 的 3 个 hang 用例改造为确定性 `runTest` 模式；最后将 Group A 的 2 个 ignore 用例反应化（1 个改写为真实 cold-launch 断言、1 个删除）。方法级 `@Ignore` 从 5 降到 0，feature 模块 connected runner 崩溃归零，全量 connected 不再因为 2 GiB heap 或 player setUp 死锁无期限卡住。

**Architecture:** 只触碰 Gradle 测试依赖、Gradle daemon heap 配置与测试代码，零生产代码触动。在隔离 worktree 内分多个原子 commit 完成，每次 commit 都跑相应模块的 `testDebugUnitTest`/`connectedAndroidTest` 验证。

**Tech Stack:** Kotlin、JUnit 4、`kotlinx.coroutines.test`（`runTest`、`advanceUntilIdle`）、Compose UI Test、Hilt androidTest（仅消费现有 `HiltAndroidRule`）。

**Spec:** [`../specs/2026-05-04-test-suite-rehabilitation-design.md`](../specs/2026-05-04-test-suite-rehabilitation-design.md)（PR 1 = §2 + §3 + §4）

---

## 文件结构

| 路径 | 操作 | 责任 |
|---|---|---|
| `feature/home/build.gradle.kts` | Modify | 新增 androidTest runner / junit / espresso 基础依赖，修复空 feature 模块 connected runner crash |
| `feature/player-ui/build.gradle.kts` | Modify | 同上 |
| `feature/search/build.gradle.kts` | Modify | 同上 |
| `feature/settings/build.gradle.kts` | Modify | 同上 |
| `gradle.properties` | Modify | 将 Gradle daemon heap 从 2 GiB 提升到 4 GiB，支撑 full androidTest dex 合并 |
| `player/src/androidTest/java/com/hank/musicfree/player/controller/PlayerControllerTest.kt` | Modify | 修复 setUp 中主线程 `runBlocking { controller.connect() }` 死锁；给 `runOnAppThread` helper 加 bounded await |
| `player/src/androidTest/java/com/hank/musicfree/player/service/PlaybackServiceTest.kt` | Modify（可选） | 如需要，为 MediaController 连接加 bounded timeout，防止连接失败时永久挂起 |
| `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt` | Modify | 删 `@Ignore` 与上方 TODO 注释；将 1 个用例迁到 `runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle` |
| `feature/settings/src/test/java/com/hank/musicfree/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt` | Modify | 删 2 处 `@Ignore` 与上方 TODO；同样迁移 2 个用例 |
| `app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt` | Modify | 删 2 处 `@Ignore` 与上方 TODO；用例 1 改写、用例 2 删除 |

---

## Task 1：创建 PR 1 worktree

**Files:** 无（仅 git/shell 操作）

- [ ] **Step 1：从仓库根目录进入或创建 worktree + 分支**

```bash
git worktree list
```

如果列表里已经有 `.worktrees/test/runtest-and-home-fidelity`，直接进入：

```bash
cd .worktrees/test/runtest-and-home-fidelity
```

如果不存在，再创建：

```bash
git worktree add .worktrees/test/runtest-and-home-fidelity -b test/runtest-and-home-fidelity
cd .worktrees/test/runtest-and-home-fidelity
```

- [ ] **Step 2：确认分支、工作区和当前 `@Ignore` 数**

```bash
git status
git branch --show-current
grep -rn "@Ignore" --include="*.kt" 2>/dev/null | grep -v build/ | grep -v .worktrees/ | wc -l
```

预期：`git status` 显示 `nothing to commit, working tree clean`；当前分支是 `test/runtest-and-home-fidelity`；`grep | wc -l` 输出约 `7`（5 个方法级 + 1 类级 + 1 行历史注释引用）。注：grep 同时命中类级和注释，整数计数仅做哨兵；以下 task 通过修改对应文件后再次 grep 验证。

- [ ] **Step 3：跑一次 baseline `:feature:settings:testDebugUnitTest`**

```bash
./gradlew :feature:settings:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`，3 个用例 `SKIPPED`（"hangs in full settings test run" 这类）。

---

## Task 2：修复 feature 模块 androidTest runner 基线

**Files:**
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/player-ui/build.gradle.kts`
- Modify: `feature/search/build.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`

- [ ] **Step 1：确认当前失败签名（可选，但建议先跑）**

```bash
./gradlew :feature:home:connectedDebugAndroidTest --no-daemon
```

当前已复现的失败签名：

```text
Test run failed to complete. Instrumentation run failed due to Process crashed.
Caused by: java.lang.ClassNotFoundException:
Didn't find class "androidx.test.runner.AndroidJUnitRunner" on path: DexPathList...
```

如果该命令在你的环境已通过，仍执行本 task：其它 feature 模块存在同类配置风险，补齐基线是低成本防回归。

- [ ] **Step 2：给 4 个 feature 模块补 androidTest 基础依赖**

在以下 4 个文件的 `dependencies` 块末尾追加同一组依赖：

```kotlin
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
```

目标文件：

```text
feature/home/build.gradle.kts
feature/player-ui/build.gradle.kts
feature/search/build.gradle.kts
feature/settings/build.gradle.kts
```

不要添加 Compose UI test androidTest 依赖；当前问题只需要 runner 基线，Compose v2 迁移也不在本 PR 范围内。

- [ ] **Step 3：逐模块 connected smoke，确认空/无测试模块不再 crash**

```bash
./gradlew :feature:home:connectedDebugAndroidTest --no-daemon
./gradlew :feature:player-ui:connectedDebugAndroidTest --no-daemon
./gradlew :feature:search:connectedDebugAndroidTest --no-daemon
./gradlew :feature:settings:connectedDebugAndroidTest --no-daemon
```

预期：全部 `BUILD SUCCESSFUL`。对于无 androidTest 源的模块，输出可能显示 `Starting 0 tests` / `Finished 0 tests`，这是可接受结果；关键是不再出现 runner `ClassNotFoundException`。

- [ ] **Step 4：commit**

```bash
git add feature/home/build.gradle.kts \
        feature/player-ui/build.gradle.kts \
        feature/search/build.gradle.kts \
        feature/settings/build.gradle.kts
git commit -m "test(feature): add androidTest runner baseline dependencies"
```

---

## Task 2.5：修复 full androidTest D8 OOM 的 Gradle heap 基线

**Files:**
- Modify: `gradle.properties`

- [ ] **Step 1：确认失败签名**

在 Task 2 runner baseline 修复后，full connected 会越过 `:feature:home`，继续执行到 `:plugin:mergeExtDexDebugAndroidTest` 并可能失败：

```text
> Task :plugin:mergeExtDexDebugAndroidTest FAILED
ERROR: D8: java.lang.OutOfMemoryError: Java heap space
```

同一命令若临时加 `-Dorg.gradle.jvmargs=-Xmx4096m` 可越过该失败点，说明根因是仓库级 `-Xmx2048m` 对 full androidTest dex merging 不足。

- [ ] **Step 2：修改 `gradle.properties`**

将：

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

改为：

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

不要要求执行者手动在命令行追加 `-Dorg.gradle.jvmargs`；full `connectedAndroidTest` 是仓库验收门，内存基线应写入仓库配置。

- [ ] **Step 3：最小验证**

```bash
./gradlew :plugin:mergeExtDexDebugAndroidTest --no-daemon
```

预期：`BUILD SUCCESSFUL`。如果该 task 被认为 up-to-date，可继续依赖 Task 9/10 的 full `connectedAndroidTest` 作为最终验证。

- [ ] **Step 4：commit**

```bash
git add gradle.properties
git commit -m "test(gradle): raise daemon heap for full androidTest dexing"
```

---

## Task 2.6：修复 `PlayerControllerTest` instrumentation setUp 死锁

**Files:**
- Modify: `player/src/androidTest/java/com/hank/musicfree/player/controller/PlayerControllerTest.kt`
- Modify（可选）: `player/src/androidTest/java/com/hank/musicfree/player/service/PlaybackServiceTest.kt`

- [ ] **Step 1：确认失败签名**

```bash
./gradlew :player:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.player.controller.PlayerControllerTest
```

当前失败表现为：

```text
Starting 9 tests on Pixel_10_Pro(AVD) - 17
Pixel_10_Pro(AVD) - 17 Tests 0/9 completed. (0 skipped) (0 failed)
```

即卡在第一个 test 的 `@Before`，不是某个断言失败。

- [ ] **Step 2：修复 `setUp()`**

删除 `runOnAppThread { runBlocking { controller.connect() } }` 形态。不要在主线程 executor 内执行 `runBlocking`。

建议替换为：

```kotlin
    @Before
    fun setUp() = runBlocking {
        controller = PlayerController(context)
        withTimeout(5_000L) {
            controller.connect()
        }
    }
```

需要新增 imports：

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
```

- [ ] **Step 3：给 `runOnAppThread` helper 加 bounded await 与异常回传**

把无界 `latch.await()` 改成 5 秒 timeout，并把 block 内异常回传到测试线程。

建议形态：

```kotlin
    private fun runOnAppThread(
        description: String = "main thread action",
        block: () -> Unit,
    ) {
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        context.mainExecutor.execute {
            try {
                block()
            } catch (t: Throwable) {
                failure.set(t)
            } finally {
                latch.countDown()
            }
        }
        assertTrue("Timed out waiting for $description", latch.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw it }
    }
```

需要新增 imports：

```kotlin
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
```

调用点可保留默认 description，也可对容易诊断的路径补描述，例如 `runOnAppThread("release PlayerController") { ... }`。

- [ ] **Step 4（可选）：给 `PlaybackServiceTest` 连接加 timeout**

如果 `PlaybackServiceTest` 也存在连接无限等待风险，可在 `setUp()` 或 `connectController()` 周围加 `withTimeout(5_000L)`。保持改动局限于测试代码。

- [ ] **Step 5：验证**

```bash
./gradlew :player:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.player.controller.PlayerControllerTest
./gradlew :player:connectedDebugAndroidTest --no-daemon
```

预期：两条命令均 `BUILD SUCCESSFUL`。如果失败，必须是有明确 assertion / timeout message 的失败，不能再停在 `Tests 0/N completed` 无输出状态。

- [ ] **Step 6：commit**

```bash
git add player/src/androidTest/java/com/hank/musicfree/player/controller/PlayerControllerTest.kt
git add player/src/androidTest/java/com/hank/musicfree/player/service/PlaybackServiceTest.kt 2>/dev/null || true
git commit -m "test(player): avoid main-thread runBlocking in controller instrumentation setup"
```

---

## Task 3：迁移 `SettingsViewModelTest.set storage directory persists selected tree uri`

**Files:**
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt`

- [ ] **Step 1：修改文件**

将文件顶部 import 区块改为（**只新增 2 行 import，删 1 行**）：

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hank.musicfree.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
```

（关键：新增 `kotlinx.coroutines.test.advanceUntilIdle` 和 `kotlinx.coroutines.test.runTest`；删除 `org.junit.Ignore` 和 `kotlinx.coroutines.runBlocking`。）

将整个 hang 用例及其上方 TODO 注释替换为：

```kotlin
    @Test
    fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = createViewModel(appPreferences)
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        viewModel.setStorageDirectory(treeUri)
        advanceUntilIdle()

        assertEquals(treeUri, appPreferences.storageDirectoryUri.first())
    }
```

将上面 `@Test fun \`storage access state is unconfigured by default\`() = runBlocking {` 也改为 `runTest(mainDispatcherRule.dispatcher)` 模式（只是为了一致性，并不改变行为）：

```kotlin
    @Test
    fun `storage access state is unconfigured by default`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(createAppPreferences())

        val state = viewModel.storageAccessState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }
```

- [ ] **Step 2：跑测试，确认通过**

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.SettingsViewModelTest"
```

预期：`SettingsViewModelTest > set storage directory persists selected tree uri PASSED`，`@Ignore` 不再出现在 SettingsViewModelTest 中。

如果失败：检查 `mainDispatcherRule.dispatcher` 是否仍为 `UnconfinedTestDispatcher()`（`MainDispatcherRule.kt` 默认值）；按 spec §6.4 fallback：改为 `runTest(mainDispatcherRule.dispatcher) { ... ; advanceUntilIdle() ; ... }` 显式包裹。

- [ ] **Step 3：commit**

```bash
git add feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(settings): migrate SettingsViewModelTest to runTest + advanceUntilIdle

Removes @Ignore on storage-directory persistence test by switching from
the racy `runBlocking + Flow.first { predicate }` pattern to
`runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle + Flow.first()`.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：迁移 `FileSelectorLiteViewModelTest.onDirectorySelected persists selected tree uri`

**Files:**
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt`

- [ ] **Step 1：修改文件 imports**

将文件顶部 imports 改为（新增 `runTest` 与 `advanceUntilIdle`；删除 `org.junit.Ignore` 和 `kotlinx.coroutines.runBlocking`）：

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.feature.settings.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
```

- [ ] **Step 2：用 `runTest` 重写第 1 个 hang 用例 + 删除上方 TODO 注释**

将 `@Test fun \`onDirectorySelected persists selected tree uri\`()` 段落（含上方 `// TODO(deps-bump-2026-05): ...` 注释块和 `@Ignore`）整段替换为：

```kotlin
    @Test
    fun `onDirectorySelected persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = FileSelectorLiteViewModel(appPreferences)
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        viewModel.onDirectorySelected(treeUri)
        advanceUntilIdle()

        assertEquals(treeUri, appPreferences.storageDirectoryUri.first())
    }
```

同步把 `\`ui state is unconfigured by default\`` 也从 `runBlocking` 改为 `runTest(mainDispatcherRule.dispatcher)`（为一致性）：

```kotlin
    @Test
    fun `ui state is unconfigured by default`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = FileSelectorLiteViewModel(createAppPreferences())

        val state = viewModel.uiState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }
```

- [ ] **Step 3：跑测试，确认通过**

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.fileselector.FileSelectorLiteViewModelTest.onDirectorySelected persists selected tree uri"
```

预期：测试 PASSED。

- [ ] **Step 4：commit**

```bash
git add feature/settings/src/test/java/com/hank/musicfree/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(settings): migrate FileSelectorLiteViewModelTest persists case to runTest

Removes one of two @Ignore annotations by adopting the runTest +
advanceUntilIdle idiom; the second case is migrated in a follow-up commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：迁移 `FileSelectorLiteViewModelTest.onDirectorySelected replaces previous selected directory`

**Files:**
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt`

- [ ] **Step 1：修改文件 — 替换第 2 个 hang 用例**

将 `@Test fun \`onDirectorySelected replaces previous selected directory\`()`（含 `@Ignore`）段落替换为：

```kotlin
    @Test
    fun `onDirectorySelected replaces previous selected directory`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = FileSelectorLiteViewModel(appPreferences)
        val firstTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"
        val secondTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

        viewModel.onDirectorySelected(firstTreeUri)
        advanceUntilIdle()
        viewModel.onDirectorySelected(secondTreeUri)
        advanceUntilIdle()

        assertEquals(secondTreeUri, appPreferences.storageDirectoryUri.first())
    }
```

- [ ] **Step 2：跑测试，确认通过**

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "com.hank.musicfree.feature.settings.fileselector.FileSelectorLiteViewModelTest"
```

预期：3 个用例全部 PASSED；该文件中 `@Ignore` 计数 = 0。

- [ ] **Step 3：commit**

```bash
git add feature/settings/src/test/java/com/hank/musicfree/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(settings): migrate FileSelectorLiteViewModelTest replace case to runTest

Removes the final @Ignore in this file by adopting the runTest +
advanceUntilIdle idiom for the "replaces previous selected directory"
case. After this commit, FileSelectorLiteViewModelTest has 0 ignored
tests.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：Hang 不回归验证（连跑两轮）

**Files:** 无

- [ ] **Step 1：跑 `:feature:settings:testDebugUnitTest` 两次（第 2 次复现 Robolectric/ByteBuddy 已预热条件）**

```bash
./gradlew :feature:settings:testDebugUnitTest && ./gradlew :feature:settings:testDebugUnitTest
```

预期：两轮均 `BUILD SUCCESSFUL`，无任何用例 hang 或 timeout。

如果 hang：按 spec §6.4 fallback——`runTest(mainDispatcherRule.dispatcher)` 改为 `runBlocking(Dispatchers.Default)` 兜底，并在该用例上方追加 `// TODO: investigate root cause of dispatcher races` 注释。**不要**回到 `@Ignore`。

- [ ] **Step 2：grep 验证 `:feature:settings` 已无 `@Ignore`**

```bash
grep -rn "@Ignore" feature/settings/src/test/ 2>/dev/null
```

预期：空输出。

---

## Task 7：HomeFidelity 用例 1 — 改写为 cold launch 真实断言

**Files:**
- Modify: `app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt`

- [ ] **Step 1：修改文件 imports**

删除 `import org.junit.Ignore`。其它 imports 保持。

- [ ] **Step 2：删除上方 TODO 注释块 + `@Ignore` + 改写第 1 个用例**

将原第 1 个用例（连同上方 8 行 `// TODO(deps-bump-2026-05): ...` 注释和 `@Ignore`）整段替换为：

```kotlin
    @Test
    fun home_cold_launch_exposes_navbar_operations_sheets_and_drawer() {
        assertTagDisplayed(FidelityAnchors.Screen.HomeRoot)
        assertTagDisplayed(FidelityAnchors.Home.NavBarRoot)
        assertTagDisplayed(FidelityAnchors.Home.NavBarMenu)
        assertTagDisplayed(FidelityAnchors.Home.OperationsRoot)
        assertTagDisplayed(FidelityAnchors.Home.SheetsRoot)

        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()

        assertTagDisplayed(FidelityAnchors.Home.DrawerRoot)
    }
```

注意：**删除** 3 行 `MiniRoot` / `MiniPlayPause` / `MiniQueue` 断言（cold launch 时 MiniPlayer 早 return，节点不存在）。

- [ ] **Step 3（如有连接的 Android 设备/模拟器）：跑 androidTest 验证**

```bash
./gradlew :app:connectedAndroidTest --tests "com.hank.musicfree.HomeFidelityHomeStructureTest.home_cold_launch_exposes_navbar_operations_sheets_and_drawer"
```

预期：测试 PASSED。

如无设备：跳过 Step 3，记录 "Verified on CI / next emulator boot"，继续。改写本身已编译验证由后续 `:app:assembleDebug` 兜底。

- [ ] **Step 4：commit**

```bash
git add app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt
git commit -m "$(cat <<'EOF'
test(app): rewrite HomeFidelity structure case for cold-launch reality

Removes the @Ignore on the home structure test by dropping assertions
that depended on the removed mock MiniPlayer (which was always-visible
with hardcoded "In the End"/"Linkin Park" content). The real Hilt
MiniPlayer early-returns when PlayerState.hasMedia is false, so on
cold launch the MiniPlayer DOM is absent. Test now asserts only what
is true at cold launch: HomeRoot, NavBar, Operations, Sheets, and
that menu click reveals Drawer.

MiniPlayer-with-media rendering is already covered by the unit-level
MiniPlayerContentTest.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8：HomeFidelity 用例 2 — 删除

**Files:**
- Modify: `app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt`

- [ ] **Step 1：删除整个 `home_content_remains_visible_above_existingMiniPlayer` 用例及其上方的 `@Ignore`**

删除以下整段（包括 `@Test`、`@Ignore("...")` 和方法体）：

```kotlin
    @Test
    @Ignore("Pre-existing stale fixture; assertions match removed mock MiniPlayer (see TODO above)")
    fun home_content_remains_visible_above_existingMiniPlayer() {
        assertTagDisplayed(FidelityAnchors.Screen.HomeRoot)
        assertTagDisplayed(FidelityAnchors.Home.SheetsRoot)
        assertTagDisplayed(FidelityAnchors.Player.MiniRoot)
        assertTagDisplayed(FidelityAnchors.Player.MiniPlayPause)
        assertTagDisplayed(FidelityAnchors.Player.MiniQueue)
        assertTextDisplayed("In the End")
        assertTextDisplayed("Linkin Park")
    }
```

- [ ] **Step 2：grep 验证 `:app/src/androidTest` 已无 `@Ignore`**

```bash
grep -rn "@Ignore" app/src/androidTest/ 2>/dev/null
```

预期：空输出。

- [ ] **Step 3：编译验证**

```bash
./gradlew :app:assembleDebugAndroidTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 4：commit**

```bash
git add app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt
git commit -m "$(cat <<'EOF'
test(app): drop dead HomeFidelity mini-player overlap case

home_content_remains_visible_above_existingMiniPlayer asserted that
the home content stayed visible "above" an always-visible MiniPlayer.
That premise no longer holds — the real MiniPlayer is absent at cold
launch. The remaining "Sheets root visible" check is duplicated by the
sibling cold-launch case, and the "In the End"/"Linkin Park" text
assertions reference the removed mock fixture. Coverage of MiniPlayer
rendering with media lives in MiniPlayerContentTest.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9：跨模块回归验证

**Files:** 无

- [ ] **Step 1：跑 player focused connected task，确认 instrumentation setUp 不再挂在 0/N**

```bash
./gradlew :player:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.player.controller.PlayerControllerTest
./gradlew :player:connectedDebugAndroidTest --no-daemon
```

预期：两条命令均 `BUILD SUCCESSFUL`。输出可以包含 `Starting 9 tests` / `Starting 15 tests`，但必须推进到 `Finished ... tests`，不能停在 `Tests 0/N completed`。

- [ ] **Step 2：跑 4 个 feature focused connected task，确认 runner baseline 仍有效**

```bash
./gradlew :feature:home:connectedDebugAndroidTest --no-daemon
./gradlew :feature:player-ui:connectedDebugAndroidTest --no-daemon
./gradlew :feature:search:connectedDebugAndroidTest --no-daemon
./gradlew :feature:settings:connectedDebugAndroidTest --no-daemon
```

预期：全部 `BUILD SUCCESSFUL`，无 `AndroidJUnitRunner` `ClassNotFoundException`。

- [ ] **Step 3：跑 `:app:testDebugUnitTest` 防止跨模块 import 影响**

```bash
./gradlew :app:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 4：跑 `:feature:search:testDebugUnitTest` 验证两份 `MainDispatcherRule` 仍各自能用**

```bash
./gradlew :feature:search:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 5：`:app:assembleDebug` 编译保护**

```bash
./gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`。

---

## Task 10：终态指标确认 + push

**Files:** 无

- [ ] **Step 1：grep 验证 PR 1 范围内 `@Ignore` 归零**

```bash
grep -rn "@Ignore" feature/settings/src/test/ app/src/androidTest/ 2>/dev/null
```

预期：空输出。

- [ ] **Step 2：（如有设备）端到端 `connectedAndroidTest`**

```bash
./gradlew connectedAndroidTest --no-daemon
```

预期：`BUILD SUCCESSFUL`，`HomeFidelityHomeStructureTest` 1 个用例 PASSED；`feature/*` 空测试模块不再 runner crash；`:plugin:mergeExtDexDebugAndroidTest` 不再 D8 OOM；`:player:connectedDebugAndroidTest` 不再停在 `Tests 0/N completed`。

如无设备：跳过；CI 完成验收。

- [ ] **Step 3：push 分支**

```bash
git push -u origin test/runtest-and-home-fidelity
```

- [ ] **Step 4：用 spec 对照写 PR 描述并创建 PR**

```bash
gh pr create --title "test: stabilize connected runner + runTest migrations" --body "$(cat <<'EOF'
## Summary

- Adds androidTest runner / junit / espresso baseline dependencies to 4 feature modules so full `connectedAndroidTest` no longer crashes while instantiating `AndroidJUnitRunner`.
- Raises Gradle daemon heap to 4 GiB so full androidTest dex merging no longer OOMs at `:plugin:mergeExtDexDebugAndroidTest`.
- Fixes `PlayerControllerTest` setup so it no longer calls `runBlocking { controller.connect() }` from the app main thread; main-thread test helper now has bounded awaits.
- Migrates 3 hang-prone Settings/FileSelector ViewModel tests from `runBlocking + Flow.first { predicate }` to `runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle + Flow.first()`.
- Rewrites HomeFidelity structure test to assert only cold-launch reality (real MiniPlayer is absent without media).
- Deletes the obsolete HomeFidelity mini-player-overlap test (premise no longer holds; coverage already in MiniPlayerContentTest).
- Net: 5 method-level `@Ignore` → 0; 1 dead test removed; 4 feature connected runner crashes prevented; 0 production code changed.

Spec: `docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md` §2 + §3 + §4.

## Test plan

- [ ] `./gradlew :feature:home:connectedDebugAndroidTest`
- [ ] `./gradlew :feature:player-ui:connectedDebugAndroidTest`
- [ ] `./gradlew :feature:search:connectedDebugAndroidTest`
- [ ] `./gradlew :feature:settings:connectedDebugAndroidTest`
- [ ] `./gradlew :player:connectedDebugAndroidTest`
- [ ] `./gradlew :feature:settings:testDebugUnitTest` (run twice for hang-regression check)
- [ ] `./gradlew :feature:search:testDebugUnitTest`
- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew connectedAndroidTest --no-daemon` (HomeFidelity case green on emulator; no D8 OOM; no player 0/N hang)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

预期：PR URL 输出。

---

## 验收闸门（PR 1 总体）

合并前必须满足：

- 4 个 feature focused `connectedDebugAndroidTest` PASS，且无 runner crash
- `:player:connectedDebugAndroidTest` PASS，且不再停在 `Tests 0/N completed`
- `:feature:settings:testDebugUnitTest` 两轮 PASS
- `:feature:search:testDebugUnitTest`、`:app:testDebugUnitTest`、`:app:assembleDebug` PASS
- `connectedAndroidTest --no-daemon` 在至少一台设备/模拟器上 PASS（HomeFidelity 用例 1 PASSED；`:plugin:mergeExtDexDebugAndroidTest` 无 D8 OOM；`:player:connectedDebugAndroidTest` 无 0/N hang）
- `grep -rn "@Ignore" feature/settings/src/test/ app/src/androidTest/` 输出为空
- 无新引入的 `@Ignore`

合并后：删除 worktree（`git worktree remove .worktrees/test/runtest-and-home-fidelity`）。
