# Homepage UI Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Android home chrome and drawer so the golden-device homepage matches the RN structure, iconography, drawer information architecture, and critical interactions defined by the April 11 homepage fidelity spec.

**Architecture:** Keep `feature/home` as the ownership boundary, but split the current Hilt-backed `HomeScreen` container from a pure `HomeScreenContent` composable so the drawer shell, transient surfaces, and system actions can be tested without app-level navigation or DI. Replace the current Material-default drawer/list treatment with an explicit `HomeDrawerUiModel`, custom drawer surface, RN-derived vector assets, and a small app-side `HomeSystemActionHandler` bridge for “back to desktop / exit app”; reuse existing `SettingsRoute` and `PermissionsRoute` where possible, and add only the controlled placeholder surfaces the spec explicitly requires.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 primitives, Navigation Compose, Hilt, Media3 `PlayerController`, Android VectorDrawable resources, adb/`uiautomator dump`

---

## Scope Check

This spec is still one subsystem: homepage chrome fidelity. Do not split this plan into separate projects. It touches docs, `feature/home`, `feature/settings`, `player`, and app navigation, but all of that work serves one executable outcome: the homepage and drawer fidelity closure.

Do **not** use this plan to:

- redesign non-home pages
- introduce a generic app-wide drawer framework
- rebuild the full language/update/timing-close business logic beyond the controlled surfaces the spec requires
- broaden settings architecture beyond the anchors and fallback destinations needed by the drawer matrix

## Preconditions

- Current `git status` shows `D docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md`.
- The plan below assumes that file remains the authoritative manifest referenced by the spec.
- If that deletion is intentional user work, stop before restoring or rewriting the manifest during execution and get human direction instead of silently reverting it.

## Implementation Notes

- Use `@jetpack-compose` when implementing the drawer shell, state hoisting, and interaction feedback.
- Use `@verification-before-completion` before claiming the homepage fidelity work is closed.

## File Structure

### Docs and tooling

| File | Responsibility |
|---|---|
| `docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md` | Versioned homepage golden-state manifest referenced by the spec |
| `docs/home-fidelity/homepage/README.md` | Canonical evidence-pack layout, naming rules, and RN/Android capture checklist |
| `tools/home-fidelity/capture-homepage-android.sh` | Repeatable adb capture helper for screenshot, dump, and optional screenrecord output |

### Core and shared anchors

| File | Responsibility |
|---|---|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt` | Canonical anchor keys for expanded drawer matrix, dialogs, panels, and settings entries |

### Home screen production code

| File | Responsibility |
|---|---|
| `feature/home/build.gradle.kts` | Add any missing Compose/runtime dependency needed for `BackHandler` or tested pure UI content |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt` | Hilt-backed container only; collects viewmodels and delegates to pure content |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt` | Pure homepage shell with scroll content, drawer layer, dialogs, and action callbacks |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenState.kt` | Page-level drawer/dialog state and coordination helpers |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt` | Drawer action model, action dispatch, and destination-building helpers |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt` | Custom drawer layout that mirrors RN section/group/bottom-action structure |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt` | Controlled timing-close panel, language dialog, and update-check dialog surfaces |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIcons.kt` | Single home-only icon mapping surface backed by RN-derived drawables |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeInteractionStyle.kt` | Shared press/selection feedback tokens and helper modifiers for homepage controls |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeNavBar.kt` | RN-like menu/search chrome using imported assets and shared press feedback |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeOperations.kt` | RN-like four-card shortcuts using imported assets and shared press feedback |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsHeader.kt` | Tab row, counts, create/import actions, and selected-state visuals |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsList.kt` | Stable list-row rendering for mine/starred sheet datasets |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt` | Lazy list wiring for `HomeSheetsHeader` + `HomeSheetsList` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeSystemActionHandler.kt` | UI-facing contract for `backToDesktop` and `exitApp` |

### Settings, app, and player integration

| File | Responsibility |
|---|---|
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt` | Add missing fallback entries and fidelity anchors for drawer routes landing on settings root |
| `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt` | Inject `PlayerController`, construct app-side `HomeSystemActionHandler`, and pass it into navigation |
| `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt` | Thread `HomeSystemActionHandler` into the home destination |
| `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AndroidHomeSystemActionHandler.kt` | Production implementation of back-to-desktop / exit-app behavior |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt` | Accept and forward `HomeSystemActionHandler` |
| `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt` | Add explicit reset API that mirrors RN exit semantics closely enough for app exit |

### Tests

| File | Responsibility |
|---|---|
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt` | Expanded anchor uniqueness contract |
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenStateTest.kt` | Drawer/dialog state transitions without Compose runtime |
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerUiModelTest.kt` | Drawer section order, anchor mapping, and trailing-text rules |
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIconMappingTest.kt` | Contract that all required RN icons are mapped in `HomeIcons` |
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt` | Existing tab-switch stability coverage, adjusted if header/list split changes expectations |
| `player/src/androidTest/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerTest.kt` | Add reset/clear-state coverage |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt` | Assert single-scroll root and drawer shell behavior after refactor |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt` | Expanded drawer destination matrix and settings-anchor fallback assertions |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeDrawerBehaviorTest.kt` | Direct `HomeScreenContent` tests for dialogs, scrim close, and fake system-action callbacks |

### RN-derived drawables

Create these files under `feature/home/src/main/res/drawable/`:

- `ic_home_alarm_outline.xml`
- `ic_home_arrow_path.xml`
- `ic_home_bars_3.xml`
- `ic_home_circle_stack.xml`
- `ic_home_clock_outline.xml`
- `ic_home_cog_8_tooth.xml`
- `ic_home_fire.xml`
- `ic_home_folder_music_outline.xml`
- `ic_home_home_outline.xml`
- `ic_home_inbox_arrow_down.xml`
- `ic_home_information_circle.xml`
- `ic_home_javascript.xml`
- `ic_home_language.xml`
- `ic_home_magnifying_glass.xml`
- `ic_home_plus.xml`
- `ic_home_power_outline.xml`
- `ic_home_shield_keyhole_outline.xml`
- `ic_home_t_shirt_outline.xml`
- `ic_home_trophy.xml`

Each drawable must be converted from the matching RN SVG under `/Users/zili/code/android/MusicFree/src/assets/icons/` with no stylistic redesign.

---

### Task 1: Restore the Manifest Surface and Evidence Tooling

**Files:**
- Modify: `docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md`
- Create: `docs/home-fidelity/homepage/README.md`
- Create: `tools/home-fidelity/capture-homepage-android.sh`

- [ ] **Step 1: Verify the manifest precondition before touching docs**

Run:

```bash
git status --short docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md
```

Expected: either empty output or a single `D` entry. If the file is intentionally deleted by the human, stop here and resolve that before restoring it.

- [ ] **Step 2: Recreate the manifest file exactly as the approved homepage source of truth**

Recover the exact committed manifest body first:

```bash
git show HEAD:docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md \
  > docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md
```

Then verify the restored file contains the approved structure:

```markdown
# 首页 UI Fidelity 黄金数据态 Manifest
## 版本
- Manifest ID: `home-ui-fidelity-2026-04-11-v1`
## 首页全局状态
| 项目 | 值 |
|------|----|
| Drawer 初始状态 | Closed |
| 当前选中 tab | `我的歌单` |
| 迷你播放器 | Visible |
| 迷你播放器标题 | `In the End` |
| 迷你播放器歌手 | `Linkin Park` |
## 我的歌单
## 收藏歌单
## 首页可见片段顺序
## 采集要求
## 恢复契约
## 变更规则
```

If `git show HEAD:...` fails because the file is absent from `HEAD`, stop and get human direction. Do not invent a new manifest version in this task.

- [ ] **Step 3: Add the evidence-pack README**

Write `docs/home-fidelity/homepage/README.md` with these required sections:

```markdown
# Homepage Fidelity Evidence
## Directory Layout
- `android/`
- `rn/`
- `diff/`
- `manifest/`

## Naming Rules
- screenshot: `<state>-<fragment>.png`
- dump: `<state>-<fragment>.xml`
- recording: `<state>-<fragment>.mp4`
- diff note: `<state>-<fragment>.md`

## Required States
- `home-top`
- `home-sheets`
- `drawer-open`

## Capture Order
1. Restore manifest state
2. Open target state
3. Capture screenshot
4. Capture `uiautomator dump`
5. Capture recording if required
```

- [ ] **Step 4: Add the Android capture helper**

Write `tools/home-fidelity/capture-homepage-android.sh` with a small, explicit interface:

```bash
#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:?device required}"
STATE="${2:?state required}"
FRAGMENT="${3:?fragment required}"
OUT_DIR="docs/home-fidelity/homepage/android"

mkdir -p "$OUT_DIR"
adb -s "$DEVICE" exec-out screencap -p > "$OUT_DIR/${STATE}-${FRAGMENT}.png"
adb -s "$DEVICE" shell uiautomator dump "/sdcard/${STATE}-${FRAGMENT}.xml" >/dev/null
adb -s "$DEVICE" pull "/sdcard/${STATE}-${FRAGMENT}.xml" "$OUT_DIR/${STATE}-${FRAGMENT}.xml" >/dev/null
adb -s "$DEVICE" shell rm "/sdcard/${STATE}-${FRAGMENT}.xml"
```

Keep the first version narrow. Do not build a generalized capture framework in this task.

- [ ] **Step 5: Verify the docs/tooling surface**

Run:

```bash
test -f docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md
test -f docs/home-fidelity/homepage/README.md
test -f tools/home-fidelity/capture-homepage-android.sh
rg -n "^## Directory Layout$|^## Naming Rules$|^## Capture Order$" docs/home-fidelity/homepage/README.md
bash tools/home-fidelity/capture-homepage-android.sh >/dev/null 2>&1 && exit 1 || echo "usage check passed"
```

Expected: file checks succeed, `rg` prints all required headings, and the final command prints `usage check passed`.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md \
  docs/home-fidelity/homepage/README.md \
  tools/home-fidelity/capture-homepage-android.sh
git commit -m "docs(home): restore homepage manifest and capture tooling"
```

---

### Task 2: Expand Canonical Anchors and Settings Fallback Targets

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: Write the failing anchor-contract test**

Extend `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt` with the new required anchors:

```kotlin
@Test
fun `expanded homepage fidelity anchors stay unique and non blank`() {
    val anchors = listOf(
        FidelityAnchors.Home.DrawerSettingsBasic,
        FidelityAnchors.Home.DrawerSettingsPlugin,
        FidelityAnchors.Home.DrawerSettingsTheme,
        FidelityAnchors.Home.DrawerOtherScheduleClose,
        FidelityAnchors.Home.DrawerOtherBackup,
        FidelityAnchors.Home.DrawerOtherPermissions,
        FidelityAnchors.Home.DrawerSoftwareLanguage,
        FidelityAnchors.Home.DrawerSoftwareCheckUpdate,
        FidelityAnchors.Home.DrawerSoftwareAbout,
        FidelityAnchors.Home.DrawerActionBackToDesktop,
        FidelityAnchors.Home.DrawerActionExitApp,
        FidelityAnchors.Panel.TimingCloseRoot,
        FidelityAnchors.Dialog.LanguageRoot,
        FidelityAnchors.Dialog.UpdateCheckRoot,
        FidelityAnchors.Settings.PluginManagementEntry,
        FidelityAnchors.Settings.ThemeEntry,
        FidelityAnchors.Settings.BackupEntry,
        FidelityAnchors.Settings.AboutEntry,
    )

    assertEquals(anchors.size, anchors.toSet().size)
    assertTrue(anchors.all { it.isNotBlank() })
}
```

- [ ] **Step 2: Run the anchor test and confirm it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeAnchorContractTest"
```

Expected: FAIL because the new constants do not exist yet.

- [ ] **Step 3: Add the new anchor constants and settings entry tags**

Implement the missing anchor keys in `FidelityAnchors.kt`:

```kotlin
object Home {
    const val DrawerSettingsBasic = "home.drawer.settings.basic"
    const val DrawerSettingsPlugin = "home.drawer.settings.plugin"
    const val DrawerSettingsTheme = "home.drawer.settings.theme"
    const val DrawerOtherScheduleClose = "home.drawer.other.scheduleClose"
    const val DrawerOtherBackup = "home.drawer.other.backup"
    const val DrawerOtherPermissions = "home.drawer.other.permissions"
    const val DrawerSoftwareLanguage = "home.drawer.software.language"
    const val DrawerSoftwareCheckUpdate = "home.drawer.software.checkUpdate"
    const val DrawerSoftwareAbout = "home.drawer.software.about"
    const val DrawerActionBackToDesktop = "home.drawer.action.backToDesktop"
    const val DrawerActionExitApp = "home.drawer.action.exitApp"
}

object Panel {
    const val TimingCloseRoot = "panel.timingClose.root"
}

object Dialog {
    const val LanguageRoot = "dialog.language.root"
    const val UpdateCheckRoot = "dialog.updateCheck.root"
}

object Settings {
    const val PluginManagementEntry = "settings.pluginManagement.entry"
    const val ThemeEntry = "settings.theme.entry"
    const val BackupEntry = "settings.backup.entry"
    const val AboutEntry = "settings.about.entry"
}
```

Keep the existing legacy constants (`DrawerSettings`, `DrawerPluginManagement`, `DrawerPermissions`) in place until all production and test call sites have been migrated in later tasks. Remove aliases only when the final app/androidTest sweep is green.

Then update `SettingsScreen.kt` so the settings root visibly contains fallback entries tagged with:

- `settings.pluginManagement.entry`
- `settings.theme.entry`
- `settings.backup.entry`
- `settings.about.entry`

Add a `modifier: Modifier = Modifier` parameter to `SettingsEntryCard` if needed so each card can receive `testTag(...)`.

- [ ] **Step 4: Run the anchor test again**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeAnchorContractTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt
git commit -m "feat(home): add expanded homepage fidelity anchors"
```

---

### Task 3: Import RN Assets and Add a Single `HomeIcons` Mapping Surface

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIcons.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIconMappingTest.kt`
- Create: `feature/home/src/main/res/drawable/ic_home_alarm_outline.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_arrow_path.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_bars_3.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_circle_stack.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_clock_outline.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_cog_8_tooth.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_fire.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_folder_music_outline.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_home_outline.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_inbox_arrow_down.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_information_circle.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_javascript.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_language.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_magnifying_glass.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_plus.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_power_outline.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_shield_keyhole_outline.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_t_shirt_outline.xml`
- Create: `feature/home/src/main/res/drawable/ic_home_trophy.xml`

- [ ] **Step 1: Write the failing icon-mapping contract test**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIconMappingTest.kt`:

```kotlin
class HomeIconMappingTest {

    @Test
    fun `home icon mapping covers every required RN icon`() {
        val ids = listOf(
            HomeIcons.NavMenu,
            HomeIcons.NavSearch,
            HomeIcons.OperationRecommend,
            HomeIcons.OperationTopList,
            HomeIcons.OperationHistory,
            HomeIcons.OperationLocal,
            HomeIcons.SheetsCreate,
            HomeIcons.SheetsImport,
            HomeIcons.DrawerSettings,
            HomeIcons.DrawerPluginManagement,
            HomeIcons.DrawerTheme,
            HomeIcons.DrawerScheduleClose,
            HomeIcons.DrawerBackup,
            HomeIcons.DrawerPermissions,
            HomeIcons.DrawerLanguage,
            HomeIcons.DrawerCheckUpdate,
            HomeIcons.DrawerAbout,
            HomeIcons.DrawerBackToDesktop,
            HomeIcons.DrawerExitApp,
        )

        assertEquals(19, ids.size)
        assertTrue(ids.all { it != 0 })
    }
}
```

- [ ] **Step 2: Run the icon test and confirm it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.component.HomeIconMappingTest"
```

Expected: FAIL because `HomeIcons` and the drawable IDs do not exist yet.

- [ ] **Step 3: Convert the RN SVGs and add `HomeIcons.kt`**

Convert the SVGs from `/Users/zili/code/android/MusicFree/src/assets/icons/` into the 19 vector drawables listed above, then create `HomeIcons.kt`:

```kotlin
object HomeIcons {
    @DrawableRes val NavMenu = R.drawable.ic_home_bars_3
    @DrawableRes val NavSearch = R.drawable.ic_home_magnifying_glass
    @DrawableRes val OperationRecommend = R.drawable.ic_home_fire
    @DrawableRes val OperationTopList = R.drawable.ic_home_trophy
    @DrawableRes val OperationHistory = R.drawable.ic_home_clock_outline
    @DrawableRes val OperationLocal = R.drawable.ic_home_folder_music_outline
    @DrawableRes val SheetsCreate = R.drawable.ic_home_plus
    @DrawableRes val SheetsImport = R.drawable.ic_home_inbox_arrow_down
    @DrawableRes val DrawerSettings = R.drawable.ic_home_cog_8_tooth
    @DrawableRes val DrawerPluginManagement = R.drawable.ic_home_javascript
    @DrawableRes val DrawerTheme = R.drawable.ic_home_t_shirt_outline
    @DrawableRes val DrawerScheduleClose = R.drawable.ic_home_alarm_outline
    @DrawableRes val DrawerBackup = R.drawable.ic_home_circle_stack
    @DrawableRes val DrawerPermissions = R.drawable.ic_home_shield_keyhole_outline
    @DrawableRes val DrawerLanguage = R.drawable.ic_home_language
    @DrawableRes val DrawerCheckUpdate = R.drawable.ic_home_arrow_path
    @DrawableRes val DrawerAbout = R.drawable.ic_home_information_circle
    @DrawableRes val DrawerBackToDesktop = R.drawable.ic_home_home_outline
    @DrawableRes val DrawerExitApp = R.drawable.ic_home_power_outline
}
```

- [ ] **Step 4: Run the icon test again**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.component.HomeIconMappingTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIcons.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/component/HomeIconMappingTest.kt \
  feature/home/src/main/res/drawable/*.xml
git commit -m "feat(home): import RN homepage icon assets"
```

---

### Task 4: Split `HomeScreen` Into a Container and a Pure Tested Content Shell

**Files:**
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenState.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenStateTest.kt`

- [ ] **Step 1: Write the failing `HomeScreenState` test**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenStateTest.kt`:

```kotlin
class HomeScreenStateTest {

    @Test
    fun `back action closes drawer before dismissing transient surfaces`() {
        val state = HomeScreenState()

        state.openDrawer()
        assertTrue(state.onBackPressedConsumed())
        assertFalse(state.isDrawerOpen)

        state.showLanguageDialog()
        assertTrue(state.onBackPressedConsumed())
        assertFalse(state.isLanguageDialogVisible)
    }
}
```

- [ ] **Step 2: Run the state test and confirm it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeScreenStateTest"
```

Expected: FAIL because `HomeScreenState` does not exist yet.

- [ ] **Step 3: Implement the pure state holder and split the screen**

Create `HomeScreenState.kt` with explicit page state:

```kotlin
class HomeScreenState {
    var isDrawerOpen by mutableStateOf(false)
        private set
    var isTimingCloseVisible by mutableStateOf(false)
        private set
    var isLanguageDialogVisible by mutableStateOf(false)
        private set
    var isUpdateCheckVisible by mutableStateOf(false)
        private set

    fun openDrawer() { isDrawerOpen = true }
    fun closeDrawer() { isDrawerOpen = false }
    fun showLanguageDialog() { isLanguageDialogVisible = true }
    fun onBackPressedConsumed(): Boolean = when {
        isDrawerOpen -> { closeDrawer(); true }
        isTimingCloseVisible -> { isTimingCloseVisible = false; true }
        isLanguageDialogVisible -> { isLanguageDialogVisible = false; true }
        isUpdateCheckVisible -> { isUpdateCheckVisible = false; true }
        else -> false
    }
}
```

Create `HomeScreenContent.kt` with a pure signature similar to:

```kotlin
@Composable
fun HomeScreenContent(
    state: HomeScreenState,
    sheetsUiState: HomeSheetsUiState,
    drawerUiModel: HomeDrawerUiModel,
    onDrawerEntryClick: (HomeDrawerAction) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateSheet: (String) -> Unit,
    onImportSheet: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
)
```

Then reduce `HomeScreen.kt` to a container that:

- collects `HomeSheetsViewModel`
- builds `HomeDrawerUiModel`
- owns `remember { HomeScreenState() }`
- delegates all rendering to `HomeScreenContent`

If `BackHandler` is required, add `implementation(libs.androidx.activity.compose)` to `feature/home/build.gradle.kts`.

- [ ] **Step 4: Run the state test again**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeScreenStateTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/build.gradle.kts \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenState.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenStateTest.kt
git commit -m "refactor(home): split home container from content shell"
```

---

### Task 5: Build the Drawer UI Model, Custom Drawer Layout, and Controlled Surfaces

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerUiModelTest.kt`

- [ ] **Step 1: Write the failing drawer-model test**

Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerUiModelTest.kt`:

```kotlin
class HomeDrawerUiModelTest {

    @Test
    fun `drawer ui model preserves spec section order and trailing text`() {
        val model = buildHomeDrawerUiModel(
            currentLanguage = "English",
            currentVersion = "1.0",
            scheduleCloseSummary = "",
        )

        assertEquals(listOf("setting", "other", "software"), model.sections.map { it.sectionKey })
        assertEquals("English", model.sections[2].items.first { it.anchorTag == FidelityAnchors.Home.DrawerSoftwareLanguage }.trailingText)
        assertEquals("1.0", model.sections[2].items.first { it.anchorTag == FidelityAnchors.Home.DrawerSoftwareCheckUpdate }.trailingText)
        assertEquals(
            listOf(
                FidelityAnchors.Home.DrawerActionBackToDesktop,
                FidelityAnchors.Home.DrawerActionExitApp,
            ),
            model.footerActions.map { it.anchorTag },
        )
    }
}
```

- [ ] **Step 2: Run the drawer-model test and confirm it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeDrawerUiModelTest"
```

Expected: FAIL because the drawer UI model does not exist yet.

- [ ] **Step 3: Implement the UI model and replace the default drawer layout**

Update `HomeDrawerNavigation.kt` so it owns an explicit model:

```kotlin
data class HomeDrawerUiModel(
    val sections: List<HomeDrawerSectionUiModel>,
    val footerActions: List<HomeDrawerItemUiModel>,
)

data class HomeDrawerSectionUiModel(
    val sectionKey: String,
    val title: String,
    val items: List<HomeDrawerItemUiModel>,
)

data class HomeDrawerItemUiModel(
    val title: String,
    @DrawableRes val iconRes: Int,
    val anchorTag: String,
    val trailingText: String? = null,
    val action: HomeDrawerAction,
)
```

Define `HomeDrawerAction` so it can represent:

- settings root
- plugin management
- theme settings
- schedule close panel
- backup
- permissions
- language dialog
- update-check dialog
- about
- back to desktop
- exit app

Then rebuild `HomeDrawerContent.kt` as a custom layout:

- no `ModalDrawerSheet`
- no `NavigationDrawerItem`
- section headers rendered explicitly
- footer actions rendered after a divider
- rows use `HomeInteractionStyle`
- root tagged with `home.drawer.root`

Add `HomeDrawerDialogs.kt` with the three controlled surfaces tagged:

- `panel.timingClose.root`
- `dialog.language.root`
- `dialog.updateCheck.root`

The first version only needs controlled visibility and predictable content, not full scheduling/update/localization business logic.

- [ ] **Step 4: Run the drawer-model test again**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeDrawerUiModelTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerDialogs.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerUiModelTest.kt
git commit -m "feat(home): rebuild homepage drawer model and surfaces"
```

---

### Task 6: Rebuild Nav Bar, Operations, and Sheets Chrome Around RN Assets

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeInteractionStyle.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeNavBar.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeOperations.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsHeader.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsList.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt`

- [ ] **Step 1: Preserve the existing sheets-viewmodel test as the failing guardrail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModelTest"
```

Expected: PASS before the refactor. This is the guardrail that the header/list split must not break tab switching.

- [ ] **Step 2: Implement shared interaction style and RN-backed chrome**

Create `HomeInteractionStyle.kt`:

```kotlin
@Immutable
data class HomeInteractionStyle(
    val pressedScale: Float = 0.97f,
    val pressedAlpha: Float = 0.72f,
)
```

Add a helper modifier/composable that:

- disables default Material ripple
- uses a shared `MutableInteractionSource`
- animates scale/alpha lightly on press

Then update `HomeNavBar.kt` and `HomeOperations.kt` to use `HomeIcons`:

```kotlin
Icon(
    painter = painterResource(HomeIcons.NavMenu),
    contentDescription = null,
    tint = MusicFreeTheme.colors.text,
)
```

Replace every `Icons.Default.*` usage in:

- `HomeNavBar.kt`
- `HomeOperations.kt`
- the sheets create/import actions

with `HomeIcons`.

- [ ] **Step 3: Split sheets header/list responsibilities**

Move the current combined code into:

- `HomeSheetsHeader.kt` for tabs + counts + create/import buttons
- `HomeSheetsList.kt` for row rendering only

Keep `HomeSheetsSection.kt` as the `LazyListScope` coordinator so the page remains a single vertical scroll context.

The selected tab visual should mirror RN more closely:

- selected title bold
- selected title uses a bottom accent/highlight treatment
- tab switching does not rebuild the entire page shell

- [ ] **Step 4: Re-run the sheets-viewmodel test**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModelTest"
```

Expected: PASS.

- [ ] **Step 5: Verify Material-default home icons are gone from homepage files**

Run:

```bash
rg -n "Icons\\.Default\\.(Menu|Search|Whatshot|EmojiEvents|History|LibraryMusic|Add|FileDownload)" \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home
```

Expected: no matches in the homepage chrome implementation.

- [ ] **Step 6: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeInteractionStyle.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeNavBar.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeOperations.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsHeader.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsList.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt
git commit -m "feat(home): align homepage chrome with RN assets"
```

---

### Task 7: Wire App Navigation, System Actions, and Player Reset Semantics

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeSystemActionHandler.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AndroidHomeSystemActionHandler.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Modify: `player/src/androidTest/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerTest.kt`

- [ ] **Step 1: Write the failing player reset test**

Add this test to `player/src/androidTest/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerTest.kt`:

```kotlin
@Test
fun resetClearsQueueAndCurrentItem() {
    runOnAppThread {
        controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 0)
    }
    waitUntil("controller starts first item") {
        controller.playerState.value.currentItem?.id == "1"
    }

    runOnAppThread {
        controller.reset()
    }

    waitUntil("controller clears playback state") {
        controller.playerState.value.currentItem == null && !controller.playerState.value.hasMedia
    }
    assertTrue(controller.playQueue.items.isEmpty())
}
```

- [ ] **Step 2: Run the player controller test and confirm it fails**

Run:

```bash
./gradlew :player:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.player.controller.PlayerControllerTest
```

Expected: FAIL because `reset()` does not exist yet.

- [ ] **Step 3: Add the reset API and production system-action bridge**

Add `HomeSystemActionHandler.kt`:

```kotlin
interface HomeSystemActionHandler {
    fun backToDesktop()
    suspend fun exitApp()
}
```

Implement `AndroidHomeSystemActionHandler.kt`:

```kotlin
class AndroidHomeSystemActionHandler(
    private val activity: ComponentActivity,
    private val playerController: PlayerController,
) : HomeSystemActionHandler {
    override fun backToDesktop() {
        activity.moveTaskToBack(true)
    }

    override suspend fun exitApp() {
        playerController.reset()
        activity.finishAffinity()
    }
}
```

Add `fun reset()` to `PlayerController.kt` that:

- stops playback
- clears media items
- clears `playQueue`
- emits an empty `PlayerState`

Then thread the handler through:

- `MainActivity.kt`
- `AppNavHost.kt`
- `HomeNavigation.kt`
- `HomeScreen` / `HomeScreenContent`

so drawer footer actions call the handler rather than hard-coding app behavior inside the composables.

- [ ] **Step 4: Run the player controller test again**

Run:

```bash
./gradlew :player:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.player.controller.PlayerControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeSystemActionHandler.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt \
  app/src/main/java/com/zili/android/musicfreeandroid/navigation/AndroidHomeSystemActionHandler.kt \
  app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt \
  app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt \
  player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
  player/src/androidTest/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerTest.kt
git commit -m "feat(home): wire homepage system actions through app and player reset"
```

---

### Task 8: Add Integration Tests and Run the Homepage Acceptance Loop

**Files:**
- Modify: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt`
- Modify: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt`
- Create: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeDrawerBehaviorTest.kt`

- [ ] **Step 1: Write the failing direct-content behavior tests**

Create `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeDrawerBehaviorTest.kt` against `HomeScreenContent` with fake callbacks and a fake `HomeSystemActionHandler`:

```kotlin
@Test
fun language_entry_opens_controlled_dialog() { /* click home.drawer.software.language -> dialog.language.root */ }

@Test
fun update_entry_opens_controlled_dialog() { /* click home.drawer.software.checkUpdate -> dialog.updateCheck.root */ }

@Test
fun schedule_close_entry_opens_panel() { /* click home.drawer.other.scheduleClose -> panel.timingClose.root */ }

@Test
fun back_to_desktop_invokes_fake_handler() { /* click footer action -> fake handler count increments */ }

@Test
fun scrim_click_closes_drawer() { /* open drawer -> tap scrim -> drawer root hidden */ }
```

- [ ] **Step 2: Expand the app navigation test matrix**

Add or update tests in `HomeEntryNavigationTest.kt` so the drawer matrix covers:

- `home.drawer.settings.basic` -> `screen.settings.root`
- `home.drawer.settings.plugin` -> `screen.settings.root` + `settings.pluginManagement.entry`
- `home.drawer.settings.theme` -> `screen.settings.root` + `settings.theme.entry`
- `home.drawer.other.scheduleClose` -> `panel.timingClose.root`
- `home.drawer.other.backup` -> `screen.settings.root` + `settings.backup.entry`
- `home.drawer.other.permissions` -> `screen.permissions.root`
- `home.drawer.software.language` -> `dialog.language.root`
- `home.drawer.software.checkUpdate` -> `dialog.updateCheck.root`
- `home.drawer.software.about` -> `screen.settings.root` + `settings.about.entry`

- [ ] **Step 3: Run the failing app tests**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeFidelityHomeStructureTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeEntryNavigationTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeDrawerBehaviorTest
```

Expected: at least one of these suites FAILS before the implementation is complete.

- [ ] **Step 4: Make the tests pass**

Update `HomeFidelityHomeStructureTest.kt` so it also verifies:

- `screen.home.root`
- `home.navBar.root`
- `home.operations.root`
- `home.sheets.root`
- drawer opens from `home.navBar.menu`

and make the new/expanded tests pass by completing any remaining content-shell wiring, tags, or settings fallbacks.

- [ ] **Step 5: Run the full local verification set**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.zili.android.musicfreeandroid.feature.home.HomeAnchorContractTest" \
  --tests "com.zili.android.musicfreeandroid.feature.home.HomeScreenStateTest" \
  --tests "com.zili.android.musicfreeandroid.feature.home.HomeDrawerUiModelTest" \
  --tests "com.zili.android.musicfreeandroid.feature.home.component.HomeIconMappingTest" \
  --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModelTest"

./gradlew :feature:settings:testDebugUnitTest

./gradlew :player:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.player.controller.PlayerControllerTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeFidelityHomeStructureTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeEntryNavigationTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeDrawerBehaviorTest

./gradlew :app:assembleDebug
```

Expected: every command PASSes.

- [ ] **Step 6: Capture the required evidence on the locked golden device**

Run:

```bash
bash tools/home-fidelity/capture-homepage-android.sh emulator-5554 home-top home-scroll
bash tools/home-fidelity/capture-homepage-android.sh emulator-5554 home-sheets sheets-list
bash tools/home-fidelity/capture-homepage-android.sh emulator-5554 drawer-open home-drawer
adb -s emulator-5554 shell screenrecord /sdcard/homepage-drawer-open.mp4
```

Stop the recording manually after capturing:

- drawer open
- drawer close
- `我的歌单 -> 收藏歌单`
- one operation-card press

Then pull the recording into `docs/home-fidelity/homepage/android/`.

- [ ] **Step 7: Commit**

```bash
git add app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt \
  app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt \
  app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeDrawerBehaviorTest.kt
git commit -m "test(home): cover homepage fidelity interactions and drawer matrix"
```

---

## Final Acceptance Checklist

- [ ] Manifest file exists and matches the April 11 spec references
- [ ] Homepage no longer uses Material default icons for navbar, operations, sheets actions, or drawer entries
- [ ] Drawer is grouped into `setting / other / software` plus bottom actions
- [ ] Drawer entries expose the canonical anchors from the April 11 spec
- [ ] `backToDesktop` and `exitApp` are abstracted behind `HomeSystemActionHandler`
- [ ] `exitApp` resets the player before app exit behavior
- [ ] Settings root exposes fallback anchors for plugin/theme/backup/about
- [ ] Controlled surfaces exist for timing close, language, and update check
- [ ] Unit tests, instrumentation tests, build, screenshot, dump, and recording evidence all pass

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-11-homepage-ui-fidelity-design.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
