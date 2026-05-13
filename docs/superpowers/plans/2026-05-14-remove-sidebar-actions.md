# Remove Sidebar Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 Android 首页侧栏中的“语言设置”“回到桌面”“退出软件”入口，并同步收紧相关 action、状态和测试契约。

**Architecture:** 侧栏入口从 `buildHomeDrawerUiModel()` 单一数据源移除，Compose UI 继续按 model 渲染。入口删除后，点击分发和临时状态层移除语言弹窗路径，测试从“入口存在并可点击”改为“入口不再暴露”。

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Compose UI Test, Gradle, Dev Harness.

---

## File Structure

- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`
  - 删除 `ShowLanguageDialog`、`BackToDesktop`、`ExitApp` action。
  - 从 drawer model 删除语言设置条目，并把 `footerActions` 改为空。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt`
  - 删除语言弹窗的点击分发、BackHandler 条件和 dialog 参数。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenState.kt`
  - 删除语言弹窗状态与方法。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt`
  - 删除语言弹窗参数与渲染分支。
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIcons.kt`
  - 删除已无侧栏入口使用的三个 icon mapping。
- Delete:
  - `feature/home/src/main/res/drawable/ic_home_language.xml`
  - `feature/home/src/main/res/drawable/ic_home_home_outline.xml`
  - `feature/home/src/main/res/drawable/ic_home_power_outline.xml`
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`
  - 删除已不再暴露的三个侧栏入口 anchor 与 `Dialog.LanguageRoot`。
- Modify tests:
  - `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerUiModelTest.kt`
  - `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContentTest.kt`
  - `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenStateTest.kt`
  - `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt`
  - `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIconMappingTest.kt`
  - `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeDrawerBehaviorTest.kt`

## Task 1: Lock Removed Entries With Failing Tests

**Files:**
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerUiModelTest.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContentTest.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenStateTest.kt`

- [ ] **Step 1: Update drawer model test first**

Change `drawer ui model preserves spec section order and trailing text` so it expects no language entry and no footer actions:

```kotlin
assertEquals(listOf("setting", "other", "software"), model.sections.map { it.sectionKey })
assertEquals(
    listOf(
        FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
        FidelityAnchors.Home.DrawerSoftwareAbout,
    ),
    model.sections[2].items.map { it.anchorTag },
)
assertEquals(
    "1.0",
    model.sections[2].items.first {
        it.anchorTag == FidelityAnchors.Home.DrawerSoftwareCheckUpdate
    }.trailingText,
)
assertEquals(emptyList<String>(), model.footerActions.map { it.anchorTag })
```

- [ ] **Step 2: Update state and click tests for removed language path**

Remove language-dialog expectations from `HomeScreenStateTest`, and keep only drawer-close behavior. Add a `HomeScreenContentTest` assertion that schedule close still opens locally without delegating so transient non-language behavior remains covered.

- [ ] **Step 3: Run red test**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.home.HomeDrawerUiModelTest' --no-daemon
```

Expected before production changes: fail because the drawer model still exposes `home.drawer.software.language`, `home.drawer.action.backToDesktop`, and `home.drawer.action.exitApp`.

## Task 2: Remove Drawer Entries And Local Language Dialog State

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenState.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt`

- [ ] **Step 1: Remove actions and model entries**

Delete `ShowLanguageDialog`, `BackToDesktop`, and `ExitApp` from `HomeDrawerAction`. Remove the language `HomeDrawerItemUiModel` from the software section. Set `footerActions = emptyList()`.

- [ ] **Step 2: Remove language click and back handling**

Delete `HomeDrawerAction.ShowLanguageDialog -> state.showLanguageDialog()` from `handleDrawerEntryClick()`. Remove `state.isLanguageDialogVisible` from `BackHandler(enabled = ...)`.

- [ ] **Step 3: Remove language state**

Delete `isLanguageDialogVisible`, `showLanguageDialog()`, `dismissLanguageDialog()`, and the language branch in `onBackPressedConsumed()`.

- [ ] **Step 4: Remove language dialog rendering**

Remove `isLanguageDialogVisible`, `currentLanguage`, and `onDismissLanguage` parameters from `HomeDrawerDialogs()`. Delete the `LanguageRoot` dialog block.

- [ ] **Step 5: Run green test**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.home.HomeDrawerUiModelTest' --no-daemon
```

Expected after implementation: pass.

## Task 3: Tighten Anchors, Icons, And Instrumentation References

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIcons.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIconMappingTest.kt`
- Modify: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeDrawerBehaviorTest.kt`

- [ ] **Step 1: Remove unused anchors and icon mappings**

Delete `DrawerSoftwareLanguage`, `DrawerActionBackToDesktop`, `DrawerActionExitApp`, and `Dialog.LanguageRoot` from `FidelityAnchors`. Delete `DrawerLanguage`, `DrawerBackToDesktop`, and `DrawerExitApp` from `HomeIcons`. Delete the now-unused drawable resources `ic_home_language.xml`, `ic_home_home_outline.xml`, and `ic_home_power_outline.xml`.

- [ ] **Step 2: Update contract tests**

Remove the deleted anchors from `HomeAnchorContractTest`. In `HomeIconMappingTest`, remove the three deleted icon ids and expected drawable assertions, then update the count from `19` to `16`.

- [ ] **Step 3: Update instrumentation test compile references**

Delete `language_entry_opens_dialog()` and `backToDesktop_action_invokes_fake_handler()` from `HomeDrawerBehaviorTest`. Remove the fake system handler and removed actions from setup. Add missing callbacks already required by `HomeScreenContent`: `onOpenStarredAlbum = {}` and `onTrashClick = {}`.

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.home.*' --no-daemon
```

Expected: home module JVM tests pass.

## Task 4: Final Verification And Merge Back

**Files:**
- Verify git diff only.
- No additional planned production files.

- [ ] **Step 1: Run dev harness grep check**

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: no UI harness or test harness grep violations.

- [ ] **Step 2: Run debug build**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: build succeeds. Release build is not required for this ordinary UI feature cleanup.

- [ ] **Step 3: Squash merge to main**

From the main checkout:

```bash
git merge --squash remove-sidebar-actions
git commit -m "fix(home): 删除侧栏语言与退出入口"
```

Expected: one conventional Chinese commit on `main`, then remove `.worktrees/remove-sidebar-actions` and delete the feature branch.

## Self-Review

- Spec coverage: plan covers model, UI state, dialog rendering, anchors/icons, focused tests, harness check, Debug build, squash merge.
- Placeholder scan: no placeholder markers or ambiguous “add tests” steps remain.
- Type consistency: file paths and symbols match current code inspection in the worktree.
