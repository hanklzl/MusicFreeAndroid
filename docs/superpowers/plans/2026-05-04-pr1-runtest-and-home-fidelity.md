# PR 1: runTest 迁移 + HomeFidelity 断言改写 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group C 的 3 个 hang 用例改造为确定性 `runTest` 模式；Group A 的 2 个 ignore 用例反应化（1 个改写为真实 cold-launch 断言、1 个删除）。方法级 `@Ignore` 从 5 降到 0。

**Architecture:** 纯测试侧改动，零生产代码触动。在隔离 worktree 内分多个原子 commit 完成，每次 commit 都跑相应模块的 `testDebugUnitTest`/`connectedAndroidTest` 验证。

**Tech Stack:** Kotlin、JUnit 4、`kotlinx.coroutines.test`（`runTest`、`advanceUntilIdle`）、Compose UI Test、Hilt androidTest（仅消费现有 `HiltAndroidRule`）。

**Spec:** [`../specs/2026-05-04-test-suite-rehabilitation-design.md`](../specs/2026-05-04-test-suite-rehabilitation-design.md)（PR 1 = §2 + §3）

---

## 文件结构

| 路径 | 操作 | 责任 |
|---|---|---|
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt` | Modify | 删 `@Ignore` 与上方 TODO 注释；将 1 个用例迁到 `runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle` |
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt` | Modify | 删 2 处 `@Ignore` 与上方 TODO；同样迁移 2 个用例 |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt` | Modify | 删 2 处 `@Ignore` 与上方 TODO；用例 1 改写、用例 2 删除 |

---

## Task 1：创建 PR 1 worktree

**Files:** 无（仅 git/shell 操作）

- [ ] **Step 1：从仓库根目录创建 worktree + 分支**

```bash
git worktree add .worktrees/test/runtest-and-home-fidelity -b test/runtest-and-home-fidelity
cd .worktrees/test/runtest-and-home-fidelity
```

- [ ] **Step 2：确认分支干净 + 当前 `@Ignore` 数为 5**

```bash
git status
grep -rn "@Ignore" --include="*.kt" 2>/dev/null | grep -v build/ | grep -v .worktrees/ | wc -l
```

预期：`git status` 显示 `nothing to commit, working tree clean`；`grep | wc -l` 输出 `7`（5 个方法级 + 1 类级 + 1 行 TODO 引用注释）。注：grep 同时命中类级和注释，整数计数仅做哨兵；以下 task 通过修改对应文件后再次 grep 验证。

- [ ] **Step 3：跑一次 baseline `:feature:settings:testDebugUnitTest`**

```bash
./gradlew :feature:settings:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`，3 个用例 `SKIPPED`（"hangs in full settings test run" 这类）。

---

## Task 2：迁移 `SettingsViewModelTest.set storage directory persists selected tree uri`

**Files:**
- Modify: `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt`

- [ ] **Step 1：修改文件**

将文件顶部 import 区块改为（**只新增 2 行 import，删 1 行**）：

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
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
./gradlew :feature:settings:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.settings.SettingsViewModelTest"
```

预期：`SettingsViewModelTest > set storage directory persists selected tree uri PASSED`，`@Ignore` 不再出现在 SettingsViewModelTest 中。

如果失败：检查 `mainDispatcherRule.dispatcher` 是否仍为 `UnconfinedTestDispatcher()`（`MainDispatcherRule.kt` 默认值）；按 spec §5.4 fallback：改为 `runTest(mainDispatcherRule.dispatcher) { ... ; advanceUntilIdle() ; ... }` 显式包裹。

- [ ] **Step 3：commit**

```bash
git add feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt
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

## Task 3：迁移 `FileSelectorLiteViewModelTest.onDirectorySelected persists selected tree uri`

**Files:**
- Modify: `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt`

- [ ] **Step 1：修改文件 imports**

将文件顶部 imports 改为（新增 `runTest` 与 `advanceUntilIdle`；删除 `org.junit.Ignore` 和 `kotlinx.coroutines.runBlocking`）：

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.feature.settings.MainDispatcherRule
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
./gradlew :feature:settings:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.settings.fileselector.FileSelectorLiteViewModelTest.onDirectorySelected persists selected tree uri"
```

预期：测试 PASSED。

- [ ] **Step 4：commit**

```bash
git add feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt
git commit -m "$(cat <<'EOF'
test(settings): migrate FileSelectorLiteViewModelTest persists case to runTest

Removes one of two @Ignore annotations by adopting the runTest +
advanceUntilIdle idiom; the second case is migrated in a follow-up commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：迁移 `FileSelectorLiteViewModelTest.onDirectorySelected replaces previous selected directory`

**Files:**
- Modify: `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt`

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
./gradlew :feature:settings:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.settings.fileselector.FileSelectorLiteViewModelTest"
```

预期：3 个用例全部 PASSED；该文件中 `@Ignore` 计数 = 0。

- [ ] **Step 3：commit**

```bash
git add feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/FileSelectorLiteViewModelTest.kt
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

## Task 5：Hang 不回归验证（连跑两轮）

**Files:** 无

- [ ] **Step 1：跑 `:feature:settings:testDebugUnitTest` 两次（第 2 次复现 Robolectric/ByteBuddy 已预热条件）**

```bash
./gradlew :feature:settings:testDebugUnitTest && ./gradlew :feature:settings:testDebugUnitTest
```

预期：两轮均 `BUILD SUCCESSFUL`，无任何用例 hang 或 timeout。

如果 hang：按 spec §5.4 fallback——`runTest(mainDispatcherRule.dispatcher)` 改为 `runBlocking(Dispatchers.Default)` 兜底，并在该用例上方追加 `// TODO: investigate root cause of dispatcher races` 注释。**不要**回到 `@Ignore`。

- [ ] **Step 2：grep 验证 `:feature:settings` 已无 `@Ignore`**

```bash
grep -rn "@Ignore" feature/settings/src/test/ 2>/dev/null
```

预期：空输出。

---

## Task 6：HomeFidelity 用例 1 — 改写为 cold launch 真实断言

**Files:**
- Modify: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt`

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
./gradlew :app:connectedAndroidTest --tests "com.zili.android.musicfreeandroid.HomeFidelityHomeStructureTest.home_cold_launch_exposes_navbar_operations_sheets_and_drawer"
```

预期：测试 PASSED。

如无设备：跳过 Step 3，记录 "Verified on CI / next emulator boot"，继续。改写本身已编译验证由后续 `:app:assembleDebug` 兜底。

- [ ] **Step 4：commit**

```bash
git add app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt
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

## Task 7：HomeFidelity 用例 2 — 删除

**Files:**
- Modify: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt`

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
git add app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt
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

## Task 8：跨模块回归验证

**Files:** 无

- [ ] **Step 1：跑 `:app:testDebugUnitTest` 防止跨模块 import 影响**

```bash
./gradlew :app:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 2：跑 `:feature:search:testDebugUnitTest` 验证两份 `MainDispatcherRule` 仍各自能用**

```bash
./gradlew :feature:search:testDebugUnitTest
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 3：`:app:assembleDebug` 编译保护**

```bash
./gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`。

---

## Task 9：终态指标确认 + push

**Files:** 无

- [ ] **Step 1：grep 验证 PR 1 范围内 `@Ignore` 归零**

```bash
grep -rn "@Ignore" feature/settings/src/test/ app/src/androidTest/ 2>/dev/null
```

预期：空输出。

- [ ] **Step 2：（如有设备）端到端 `connectedAndroidTest`**

```bash
./gradlew connectedAndroidTest
```

预期：`BUILD SUCCESSFUL`，`HomeFidelityHomeStructureTest` 1 个用例 PASSED。

如无设备：跳过；CI 完成验收。

- [ ] **Step 3：push 分支**

```bash
git push -u origin test/runtest-and-home-fidelity
```

- [ ] **Step 4：用 spec 对照写 PR 描述并创建 PR**

```bash
gh pr create --title "test: runTest migration + HomeFidelity cold-launch rewrite" --body "$(cat <<'EOF'
## Summary

- Migrates 3 hang-prone Settings/FileSelector ViewModel tests from `runBlocking + Flow.first { predicate }` to `runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle + Flow.first()`.
- Rewrites HomeFidelity structure test to assert only cold-launch reality (real MiniPlayer is absent without media).
- Deletes the obsolete HomeFidelity mini-player-overlap test (premise no longer holds; coverage already in MiniPlayerContentTest).
- Net: 5 method-level `@Ignore` → 0; 1 dead test removed; 0 production code changed.

Spec: `docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md` §2 + §3.

## Test plan

- [ ] `./gradlew :feature:settings:testDebugUnitTest` (run twice for hang-regression check)
- [ ] `./gradlew :feature:search:testDebugUnitTest`
- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew connectedAndroidTest` (HomeFidelity case green on emulator)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

预期：PR URL 输出。

---

## 验收闸门（PR 1 总体）

合并前必须满足：

- `:feature:settings:testDebugUnitTest` 两轮 PASS
- `:feature:search:testDebugUnitTest`、`:app:testDebugUnitTest`、`:app:assembleDebug` PASS
- `connectedAndroidTest` 在至少一台设备/模拟器上 PASS（HomeFidelity 用例 1 PASSED）
- `grep -rn "@Ignore" feature/settings/src/test/ app/src/androidTest/` 输出为空
- 无新引入的 `@Ignore`

合并后：删除 worktree（`git worktree remove .worktrees/test/runtest-and-home-fidelity`）。
