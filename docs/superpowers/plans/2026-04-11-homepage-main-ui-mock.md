# Homepage Main UI Mock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the visible homepage main UI and bottom mini player into a fixed mock-driven golden state that matches the RN homepage structure and visual hierarchy before reconnecting real functionality.

**Architecture:** Keep `feature/home` as the homepage ownership boundary and keep the existing drawer shell intact, but replace the current homepage body with a pure mock-driven `HomeVisualUiModel` path. Extract a pure `MiniPlayerContent` presentation layer in `feature/player-ui`, then let `MainActivity` render a stable home-only mock mini player while preserving the real player-backed `MiniPlayer` container for the rest of the app.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 primitives, Hilt, Navigation Compose, Robolectric Compose tests, Android instrumented tests, adb screenshot capture

---

> 文档状态：历史记录（执行前计划）
> 适用范围：首页主界面与底部 mini player 的第一阶段 mock UI 实施计划。
> 直接执行：否
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md) ｜ [Spec](../specs/2026-04-11-homepage-main-ui-mock-design.md)
> 备注：仅服务于首页第一阶段 mock UI 收敛；不包含 Drawer 重做与真实功能接线。
> 最后校验：2026-04-11

## Scope Check

This plan still covers one subsystem: homepage first-stage mock UI closure. Do not split it into separate projects. The work touches `feature/home`, `feature/player-ui`, `core`, and `app`, but all changes serve one result: a stable home-route golden state that visually matches the RN homepage screenshot.

Do **not** use this plan to:

- redesign Drawer internals
- reconnect real playlist CRUD or playlist detail flows
- reconnect real player queue or full player navigation from the mini player
- clean up unrelated pages while touching home files
- generalize a reusable mock-data framework for the whole app

## Implementation Notes

- Use `@jetpack-compose` when extracting pure composables, hoisting local state, and adding Compose tests.
- Use `@verification-before-completion` before claiming the homepage mock UI is done.
- Keep `HomeSheetsViewModel` and playlist repositories in the codebase. This phase bypasses them for rendering but does not delete them.
- Keep Drawer behavior intact. Any accidental Drawer regressions are out-of-scope failures.
- Prefer `rememberSaveable` for the home-route mock tab selection and mock play/pause state because they are pure UI state, not domain state.

## File Structure

### Core anchors

| File | Responsibility |
|---|---|
| `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt` | Add canonical mini player anchor constants without breaking existing home anchors |

### Home production code

| File | Responsibility |
|---|---|
| `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeVisualUiModel.kt` | New homepage-first-stage display model definitions |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeMockVisualFactory.kt` | Stable mock data factory for nav, operations, tabs, and playlist rows |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt` | Container that owns local mock tab state and feeds mock UI models into content |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreenContent.kt` | Pure homepage shell that consumes `HomeVisualUiModel` instead of repository-backed state |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeNavBar.kt` | Tighten top chrome spacing and accept the display placeholder text from the UI model |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeOperations.kt` | Render operation cards from display-model entries and tighten card density |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsHeader.kt` | Keep tab/header visuals aligned with target screenshot |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsList.kt` | Render fixed mock rows and remove empty-state dependence from the home golden path |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsSection.kt` | Remove embedded real create dialog behavior so header actions become pure callbacks |

### Player UI production code

| File | Responsibility |
|---|---|
| `feature/player-ui/build.gradle.kts` | Add missing Compose unit-test dependencies for pure mini player content tests |
| `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayerUiModel.kt` | Pure display-model for the redesigned mini player bar |
| `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContent.kt` | New pure mini player composable with home-target structure and anchors |
| `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayer.kt` | Real player-backed container that maps `PlayerState` into `MiniPlayerUiModel` |

### App integration

| File | Responsibility |
|---|---|
| `app/src/main/java/com/hank/musicfree/MainActivity.kt` | Render the mock home mini player on `HomeRoute`, keep the real mini player elsewhere, and host the home-only mock play state |

### Tests

| File | Responsibility |
|---|---|
| `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeMockVisualFactoryTest.kt` | Lock the fixed homepage mock state contract |
| `feature/home/src/test/java/com/hank/musicfree/feature/home/sheets/HomeSheetsSectionTest.kt` | Ensure sheet header actions are pure callbacks and no longer open the real create dialog |
| `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeScreenMockContentTest.kt` | Validate the homepage golden state renders rows instead of the empty state |
| `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt` | Extend anchor uniqueness checks to include mini player anchors |
| `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContentTest.kt` | Validate mini player anchor wiring, callback behavior, and the absence of the old next-track control |
| `app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt` | Assert the home route still exposes the root structure and now also exposes the mock mini player anchors |

---

### Task 1: Add the Homepage Mock Display Model Surface

**Files:**
- Create: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeVisualUiModel.kt`
- Create: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeMockVisualFactory.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeMockVisualFactoryTest.kt`

- [ ] **Step 1: Write the failing mock-factory test**

```kotlin
class HomeMockVisualFactoryTest {
    @Test
    fun `mine tab mock state exposes four stable playlist rows`() {
        val uiModel = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Mine)

        assertEquals(HomeSheetTab.Mine, uiModel.playlistSection.selectedTab)
        assertEquals(4, uiModel.playlistSection.rows.size)
        assertTrue(uiModel.playlistSection.rows.none { it.title.contains("mock", ignoreCase = true) })
        assertTrue(uiModel.playlistSection.rows.all { it.subtitle.isNotBlank() })
    }

    @Test
    fun `starred tab mock state swaps row set without changing header counts`() {
        val mine = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Mine)
        val starred = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Starred)

        assertEquals(mine.playlistSection.mineCount, starred.playlistSection.mineCount)
        assertEquals(mine.playlistSection.starredCount, starred.playlistSection.starredCount)
        assertNotEquals(mine.playlistSection.rows.map { it.id }, starred.playlistSection.rows.map { it.id })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.home.HomeMockVisualFactoryTest"
```

Expected: FAIL because `HomeVisualUiModel` and `buildHomeVisualUiModel` do not exist yet.

- [ ] **Step 3: Add the minimal display-model implementation**

Write focused model files. Keep them small and explicit.

```kotlin
data class HomeVisualUiModel(
    val searchPlaceholder: String,
    val operations: List<HomeOperationUiModel>,
    val playlistSection: HomePlaylistSectionUiModel,
)

data class HomePlaylistSectionUiModel(
    val selectedTab: HomeSheetTab,
    val mineCount: Int,
    val starredCount: Int,
    val rows: List<HomeSheetUiModel>,
)

fun buildHomeVisualUiModel(selectedTab: HomeSheetTab): HomeVisualUiModel = ...
```

Implementation constraints:

- Use stable fake IDs such as `mock-mine-liked`, `mock-mine-cloud`, `mock-starred-neo`.
- Reuse `HomeSheetUiModel` for row rendering instead of inventing a second row model.
- Keep the four shortcut items and the search placeholder in the same factory so the entire home golden state comes from one source.
- Do not read any repository, ViewModel, or player state in this factory.

- [ ] **Step 4: Run the test to verify it passes**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/HomeVisualUiModel.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/HomeMockVisualFactory.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/HomeMockVisualFactoryTest.kt
git commit -m "feat(home): add homepage mock visual model"
```

---

### Task 2: Remove Embedded Real Sheet Actions From the Home Header

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsSection.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/feature/home/sheets/HomeSheetsSectionTest.kt`

- [ ] **Step 1: Write the failing sheet-header interaction test**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeSheetsSectionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `header actions are pure callbacks and do not open create playlist dialog`() {
        var createClicks = 0
        var importClicks = 0
        var selectedTab: HomeSheetTab? = null

        composeRule.setContent {
            MusicFreeTheme {
                LazyColumn {
                    homeSheetsSection(
                        uiState = sampleHomeSheetsUiState(),
                        onSelectTab = { selectedTab = it },
                        onCreateClick = { createClicks++ },
                        onImportClick = { importClicks++ },
                        onOpenMineSheet = {},
                        onOpenStarredSheet = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsCreate).performClick()
        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsImport).performClick()
        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsStarredTab).performClick()

        assertEquals(1, createClicks)
        assertEquals(1, importClicks)
        assertEquals(HomeSheetTab.Starred, selectedTab)
        composeRule.onNodeWithText("新建播放列表").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.home.sheets.HomeSheetsSectionTest"
```

Expected: FAIL because `homeSheetsSection` still owns `CreatePlaylistDialog` state and does not expose pure action callbacks.

- [ ] **Step 3: Refactor the section wiring to pure callbacks**

Replace the current embedded dialog state with direct callbacks:

```kotlin
fun LazyListScope.homeSheetsSection(
    uiState: HomeSheetsUiState,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
) { ... }
```

Implementation constraints:

- Delete the `remember { mutableStateOf(false) }` / `CreatePlaylistDialog` ownership from `HomeSheetsSection.kt`.
- Update `HomeScreen.kt` call sites to compile with the new pure callback names.
- Do not route create/import into the real playlist flow during this phase. Use no-op or controlled callbacks from the container.

- [ ] **Step 4: Run the test to verify it passes**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsSection.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/sheets/HomeSheetsSectionTest.kt
git commit -m "refactor(home): make sheet header actions mock-safe"
```

---

### Task 3: Rewire the Homepage Body to the Mock Golden State and Tighten Layout

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreenContent.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeNavBar.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeOperations.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsHeader.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsList.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeScreenMockContentTest.kt`

- [ ] **Step 1: Write the failing homepage-content golden-state test**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeScreenMockContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `home golden state renders playlist rows instead of empty state`() {
        composeRule.setContent {
            MusicFreeTheme {
                HomeScreenContent(
                    state = HomeScreenState(),
                    visualUiModel = buildHomeVisualUiModel(HomeSheetTab.Mine),
                    drawerUiModel = buildHomeDrawerUiModel("中文", "1.0.0", ""),
                    currentLanguage = "中文",
                    currentVersion = "1.0.0",
                    scheduleCloseSummary = "",
                    onDrawerEntryClick = {},
                    onOpenSearch = {},
                    onOperationClick = {},
                    onSelectTab = {},
                    onCreateClick = {},
                    onImportClick = {},
                    onPlaylistClick = {},
                )
            }
        }

        composeRule.onNodeWithText("暂无歌单").assertDoesNotExist()
        composeRule.onNodeWithTag("home.sheets.item.mine.mock-mine-liked").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.home.HomeScreenMockContentTest"
```

Expected: FAIL because `HomeScreenContent` does not accept `visualUiModel` and the home route still renders repository-backed sheet state.

- [ ] **Step 3: Implement the minimal mock-driven home body**

Refactor the container and content in three parts.

Container state in `HomeScreen.kt`:

```kotlin
var selectedTab by rememberSaveable { mutableStateOf(HomeSheetTab.Mine) }
val visualUiModel = remember(selectedTab) {
    buildHomeVisualUiModel(selectedTab = selectedTab)
}
```

Content signature in `HomeScreenContent.kt`:

```kotlin
fun HomeScreenContent(
    state: HomeScreenState,
    visualUiModel: HomeVisualUiModel,
    drawerUiModel: HomeDrawerUiModel,
    ...
)
```

Rendering constraints:

- `HomeScreen.kt` stops collecting `HomeSheetsViewModel` for the first-stage home route.
- Keep drawer wiring and existing navigation callbacks intact.
- `HomeNavBar` should use a tighter menu/search relationship so the search pill fills most of the row.
- `HomeOperations` should keep four equal cards but reduce the airy spacing that makes the current screen look sparse.
- `HomeSheetsHeader` and `HomeSheetsList` should render the fixed mock rows and their counts with no default-empty-state path on the home route.
- Do not introduce a new ViewModel for mock state.

- [ ] **Step 4: Run focused home tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.home.HomeMockVisualFactoryTest" \
  --tests "com.hank.musicfree.feature.home.sheets.HomeSheetsSectionTest" \
  --tests "com.hank.musicfree.feature.home.HomeScreenMockContentTest" \
  --tests "com.hank.musicfree.feature.home.HomeScreenContentTest" \
  --tests "com.hank.musicfree.feature.home.HomeIconButtonAccessibilityTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreenContent.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeNavBar.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeOperations.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsHeader.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsList.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/HomeScreenMockContentTest.kt
git commit -m "feat(home): switch homepage body to mock golden state"
```

---

### Task 4: Extract Pure Mini Player Content and Canonical Anchors

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt`
- Modify: `feature/player-ui/build.gradle.kts`
- Create: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayerUiModel.kt`
- Create: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContent.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayer.kt`
- Test: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContentTest.kt`

- [ ] **Step 1: Write the failing mini-player content test**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MiniPlayerContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `mini player exposes canonical anchors and no next-track action`() {
        var playClicks = 0
        var queueClicks = 0

        composeRule.setContent {
            MusicFreeTheme {
                MiniPlayerContent(
                    uiModel = MiniPlayerUiModel(
                        title = "半兽人",
                        subtitle = "周杰伦",
                        coverUri = null,
                        isPlaying = true,
                        showQueueButton = true,
                    ),
                    onOpenPlayer = {},
                    onTogglePlayPause = { playClicks++ },
                    onOpenQueue = { queueClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniPlayPause).performClick()
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniQueue).performClick()
        composeRule.onNodeWithContentDescription("下一曲").assertDoesNotExist()
        assertEquals(1, playClicks)
        assertEquals(1, queueClicks)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.playerui.component.MiniPlayerContentTest"
```

Expected: FAIL because `MiniPlayerContent`, `MiniPlayerUiModel`, and `FidelityAnchors.Player.*` do not exist yet, and the module lacks Compose UI unit-test dependencies.

- [ ] **Step 3: Add the pure mini player surface**

First add missing test dependencies in `feature/player-ui/build.gradle.kts`:

```kotlin
testImplementation(libs.robolectric)
testImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

Then implement the new pure UI surface:

```kotlin
data class MiniPlayerUiModel(
    val coverUri: String?,
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val showQueueButton: Boolean,
)
```

```kotlin
@Composable
fun MiniPlayerContent(
    uiModel: MiniPlayerUiModel,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenQueue: () -> Unit,
) { ... }
```

Implementation constraints:

- Add `FidelityAnchors.Player.MiniRoot`, `MiniPlayPause`, and `MiniQueue` in `FidelityAnchors.kt`.
- Extend `HomeAnchorContractTest` so those anchors are covered by the uniqueness contract.
- Remove the current linear progress bar and next-track button from `MiniPlayerContent` and the real `MiniPlayer` container path.
- Keep `MiniPlayer.kt` as the real-player container, but map `PlayerState` into `MiniPlayerUiModel` and delegate rendering to `MiniPlayerContent`.
- Leave `PlayerViewModel.skipToNext()` intact for the full player screen; only the mini player UI should stop exposing it.

- [ ] **Step 4: Run the player/home anchor tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.playerui.component.MiniPlayerContentTest" \
  --tests "com.hank.musicfree.feature.playerui.PlayerViewModelTest" && \
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.home.HomeAnchorContractTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt \
  feature/player-ui/build.gradle.kts \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayerUiModel.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContent.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayer.kt \
  feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContentTest.kt
git commit -m "feat(player-ui): extract mock-ready mini player content"
```

---

### Task 5: Host the Home-Only Mock Mini Player and Re-verify the Home Route

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt`

- [ ] **Step 1: Write the failing instrumented home-structure test update**

Extend the existing test with the new mini player expectations:

```kotlin
@Test
fun home_content_remains_visible_above_mockMiniPlayer() {
    assertTagDisplayed(FidelityAnchors.Screen.HomeRoot)
    assertTagDisplayed(FidelityAnchors.Home.SheetsRoot)
    assertTagDisplayed(FidelityAnchors.Player.MiniRoot)
    assertTagDisplayed(FidelityAnchors.Player.MiniPlayPause)
    assertTagDisplayed(FidelityAnchors.Player.MiniQueue)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run on the connected emulator/device:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.HomeFidelityHomeStructureTest
```

Expected: FAIL because the home route still renders the old player-backed bottom bar behavior and does not expose the new mini player anchors.

- [ ] **Step 3: Implement the app-side mock mini player host**

Update `MainActivity.kt` so the bottom bar differentiates the home route from all other routes:

```kotlin
var homeMockIsPlaying by rememberSaveable { mutableStateOf(true) }

bottomBar = {
    when {
        currentRoute?.contains(homeRouteName) == true -> {
            MiniPlayerContent(
                uiModel = MiniPlayerUiModel(
                    coverUri = null,
                    title = "半兽人",
                    subtitle = "周杰伦",
                    isPlaying = homeMockIsPlaying,
                    showQueueButton = true,
                ),
                onOpenPlayer = {},
                onTogglePlayPause = { homeMockIsPlaying = !homeMockIsPlaying },
                onOpenQueue = {},
            )
        }
        showMiniPlayer -> {
            MiniPlayer(onNavigateToPlayer = { navController.navigate(PlayerRoute) })
        }
    }
}
```

Implementation constraints:

- Keep the home route top inset logic untouched.
- Keep the real `MiniPlayer` for non-home, non-player routes.
- Do not let the home mock mini player navigate into the full player yet.
- Ensure the bottom bar remains visible on first launch without requiring real playback state.

- [ ] **Step 4: Run the full verification set**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.home.HomeMockVisualFactoryTest" \
  --tests "com.hank.musicfree.feature.home.sheets.HomeSheetsSectionTest" \
  --tests "com.hank.musicfree.feature.home.HomeScreenMockContentTest" \
  --tests "com.hank.musicfree.feature.home.HomeAnchorContractTest" && \
./gradlew :feature:player-ui:testDebugUnitTest \
  --tests "com.hank.musicfree.feature.playerui.component.MiniPlayerContentTest" \
  --tests "com.hank.musicfree.feature.playerui.PlayerViewModelTest" && \
./gradlew :app:assembleDebug && \
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.HomeFidelityHomeStructureTest
```

Expected: all commands PASS.

- [ ] **Step 5: Capture runtime evidence on the emulator**

Run:

```bash
adb start-server
adb -s emulator-5554 shell am start -S -n com.hank.musicfree/.MainActivity >/dev/null
sleep 3
bash tools/home-fidelity/capture-homepage-android.sh emulator-5554 home-top home-scroll
bash tools/home-fidelity/capture-homepage-android.sh emulator-5554 home-sheets sheets-list
```

Expected:

- `docs/home-fidelity/homepage/android/home-top-home-scroll.png` exists
- `docs/home-fidelity/homepage/android/home-sheets-sheets-list.png` exists
- screenshots show playlist rows and the new mini player instead of the old empty-state homepage

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/MainActivity.kt \
  app/src/androidTest/java/com/hank/musicfree/HomeFidelityHomeStructureTest.kt \
  docs/home-fidelity/homepage/android/home-top-home-scroll.png \
  docs/home-fidelity/homepage/android/home-top-home-scroll.xml \
  docs/home-fidelity/homepage/android/home-sheets-sheets-list.png \
  docs/home-fidelity/homepage/android/home-sheets-sheets-list.xml
git commit -m "feat(app): host homepage mock mini player"
```

---

## Completion Checklist

- [ ] Home route renders fixed mock rows on first launch
- [ ] Home route no longer shows `暂无歌单`
- [ ] Header create/import actions are pure callbacks, not real dialogs
- [ ] Mini player exposes `player.mini.*` anchors
- [ ] Home route shows the mock mini player even with no real playback state
- [ ] Old mini player progress bar and next-track action are gone from the mini player surface
- [ ] Home structure androidTest passes on a connected device/emulator
- [ ] Fresh screenshots exist under `docs/home-fidelity/homepage/android/`
