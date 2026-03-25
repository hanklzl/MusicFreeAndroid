# Home Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved home-screen fidelity scope: single-scroll vertical home, RN-matching drawer and entry affordances, canonical anchor contract, reproducible evidence capture, and a minimal standalone `LocalRoute` surface on the locked golden device.

**Architecture:** Introduce a platform-neutral canonical anchor contract, add minimal `StarredSheet` persistence so `我的歌单 / 收藏歌单` can be non-empty in a reproducible golden state, split the existing `HomeScreen` into focused Compose units, and wire a minimal `LocalRoute` shell instead of using in-page pager navigation. Verification is part of the feature: Android and RN both get stable anchors, restore/capture scripts, manifests, and artifact directories under `docs/convergence/home-fidelity/`.

**Tech Stack:** Jetpack Compose, Hilt, Room, Kotlin Coroutines/Flow, Compose UI Test, Android instrumentation, adb/`uiautomator dump`, React Native Android reference app at `/Users/zili/code/android/MusicFree`

---

## File Structure

### New files in this repo

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt` | Minimal core model for home-screen starred sheets |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt` | Canonical anchor keys shared by home, target screens, and tests |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt` | Room entity for starred sheets |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDao.kt` | Observe/upsert/delete starred sheets |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/Migrations.kt` | Room migration from DB v1 to v2 |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt` | Entity ↔ core model mapping |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepository.kt` | Feature-facing API for home starred sheets |
| `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDaoTest.kt` | DAO coverage for ordering and CRUD |
| `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/AppDatabaseMigrationTest.kt` | Migration coverage for new starred sheet table |
| `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryTest.kt` | Repository coverage for observe/upsert/delete |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetTab.kt` | `Mine` / `Starred` tab enum |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt` | UI-ready model for home sheet rows |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsUiState.kt` | Screen state for tab + counts + rows |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModel.kt` | Home-only adapter over playlists + starred sheets |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt` | RN-style sheet tabs, actions, list |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicContent.kt` | Extracted local music list content, reusable by home and `LocalRoute` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt` | Minimal standalone local music page exposing `screen.local.root` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/navigation/LocalNavigation.kt` | `LocalRoute` nav graph registration |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeNavBar.kt` | Menu + search capsule |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeOperations.kt` | Four shortcut cards |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt` | Drawer title, sections, items, anchors |
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt` | Canonical anchor contract uniqueness / required-key coverage |
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt` | Tab switching and data adaptation coverage |
| `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModelTest.kt` | Mapping rules for mine/starred rows |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt` | Home root, drawer, anchors, single-scroll structure |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt` | Search / recommend / toplist / history / settings / permissions / local entry navigation |
| `scripts/convergence/home-fidelity/restore-android-home-state.sh` | Restore Android app to golden home state |
| `scripts/convergence/home-fidelity/restore-rn-home-state.sh` | Restore RN app to golden home state |
| `scripts/convergence/home-fidelity/capture-android-home.sh` | Android screenshot + dump capture |
| `scripts/convergence/home-fidelity/capture-rn-home.sh` | RN screenshot + dump capture |
| `scripts/convergence/home-fidelity/assert_capture_state.py` | Parse XML dumps and fail when a requested capture state is not actually reached |
| `docs/convergence/home-fidelity/fixtures/android/musicfree.db` | Golden Android Room fixture for restore script |
| `docs/convergence/home-fidelity/fixtures/android/musicfree.db-wal` | Android WAL sidecar for deterministic restore |
| `docs/convergence/home-fidelity/fixtures/android/musicfree.db-shm` | Android shared-memory sidecar for deterministic restore |
| `docs/convergence/home-fidelity/fixtures/rn/RKStorage` | Golden RN storage fixture for restore script |
| `docs/convergence/home-fidelity/fixtures/rn/RKStorage-journal` | Optional RN journal sidecar when present |
| `docs/convergence/home-fidelity/manifests/golden-data-state.md` | Golden device + data-state manifest |
| `docs/convergence/home-fidelity/manifests/rn-anchor-map.md` | Canonical anchor ↔ RN node mapping |
| `docs/convergence/home-fidelity/diffs/README.md` | Diff artifact template and required fields |

### Modified files in this repo

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt` | Add `LocalRoute` |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt` | Register starred sheet entity + DB version bump |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt` | Provide DAO + migration-enabled DB builder |
| `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt` | Wire `LocalRoute` and home navigation callbacks |
| `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt` | Route serialization coverage for `LocalRoute` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt` | Thin container over drawer + single-scroll home content |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt` | Local music + add-to-playlist stays here; remove sheet-tab concerns |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt` | Accept `onNavigateToLocal` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistViewModel.kt` | Keep playlist CRUD as source for `我的歌单` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt` | Add `screen.history.root` anchor |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt` | Add `screen.recommendSheets.root` anchor |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt` | Add `screen.topList.root` anchor |
| `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt` | Add `screen.search.root` anchor |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt` | Add `screen.settings.root` and `settings.pluginManagement.entry` |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsScreen.kt` | Add `screen.permissions.root` anchor |

### Modified files in the RN reference repo

| File | Responsibility |
|------|---------------|
| `/Users/zili/code/android/MusicFree/src/pages/home/components/navBar.tsx` | Add canonical `testID`/accessible hooks for nav bar |
| `/Users/zili/code/android/MusicFree/src/pages/home/components/homeBody/operations.tsx` | Add canonical `testID` hooks for four shortcut cards |
| `/Users/zili/code/android/MusicFree/src/pages/home/components/homeBody/sheets.tsx` | Add canonical `testID` hooks for tabs, actions, list root |
| `/Users/zili/code/android/MusicFree/src/pages/home/components/drawer/index.tsx` | Add canonical `testID` hooks for drawer root and entries |
| `/Users/zili/code/android/MusicFree/src/pages/searchPage/index.tsx` | Add `screen.search.root` |
| `/Users/zili/code/android/MusicFree/src/pages/recommendSheets/index.tsx` | Add `screen.recommendSheets.root` |
| `/Users/zili/code/android/MusicFree/src/pages/topList/index.tsx` | Add `screen.topList.root` |
| `/Users/zili/code/android/MusicFree/src/pages/history/index.tsx` | Add `screen.history.root` |
| `/Users/zili/code/android/MusicFree/src/pages/setting/index.tsx` | Add `screen.settings.root` and plugin-management entry hook |
| `/Users/zili/code/android/MusicFree/src/pages/localMusic/index.tsx` | Add `screen.local.root` |

---

## Task 1: Canonical Anchors and `LocalRoute`

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- Modify: `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt`

- [ ] **Step 1: Write the failing route and anchor-contract tests**

Add these tests first:

```kotlin
// app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt
@Test
fun `LocalRoute is serializable`() {
    val json = Json.encodeToString(LocalRoute)
    val decoded = Json.decodeFromString<LocalRoute>(json)
    assertEquals(LocalRoute, decoded)
}
```

```kotlin
// feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt
class HomeAnchorContractTest {
    @Test
    fun `required home anchors are unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Screen.HomeRoot,
            FidelityAnchors.Home.NavBarRoot,
            FidelityAnchors.Home.NavBarMenu,
            FidelityAnchors.Home.NavBarSearch,
            FidelityAnchors.Home.OperationsRoot,
            FidelityAnchors.Home.OperationsRecommendSheets,
            FidelityAnchors.Home.OperationsTopList,
            FidelityAnchors.Home.OperationsHistory,
            FidelityAnchors.Home.OperationsLocalMusic,
            FidelityAnchors.Home.SheetsRoot,
            FidelityAnchors.Home.SheetsMineTab,
            FidelityAnchors.Home.SheetsStarredTab,
            FidelityAnchors.Home.SheetsCreate,
            FidelityAnchors.Home.SheetsImport,
            FidelityAnchors.Home.DrawerRoot,
            FidelityAnchors.Home.DrawerSettings,
            FidelityAnchors.Home.DrawerPluginManagement,
            FidelityAnchors.Home.DrawerPermissions,
        )
        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest" \
  :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeAnchorContractTest"
```

Expected: FAIL because `LocalRoute` and `FidelityAnchors` do not exist yet.

- [ ] **Step 3: Add `LocalRoute` and the canonical anchor registry**

Create `FidelityAnchors.kt` with grouped string constants:

```kotlin
object FidelityAnchors {
    object Screen {
        const val HomeRoot = "screen.home.root"
        const val SearchRoot = "screen.search.root"
        const val RecommendSheetsRoot = "screen.recommendSheets.root"
        const val TopListRoot = "screen.topList.root"
        const val HistoryRoot = "screen.history.root"
        const val SettingsRoot = "screen.settings.root"
        const val PermissionsRoot = "screen.permissions.root"
        const val LocalRoot = "screen.local.root"
    }

    object Home {
        const val NavBarRoot = "home.navBar.root"
        const val NavBarMenu = "home.navBar.menu"
        const val NavBarSearch = "home.navBar.search"
        const val OperationsRoot = "home.operations.root"
        const val OperationsRecommendSheets = "home.operations.recommendSheets"
        const val OperationsTopList = "home.operations.topList"
        const val OperationsHistory = "home.operations.history"
        const val OperationsLocalMusic = "home.operations.localMusic"
        const val SheetsRoot = "home.sheets.root"
        const val SheetsMineTab = "home.sheets.tab.mine"
        const val SheetsStarredTab = "home.sheets.tab.starred"
        const val SheetsCreate = "home.sheets.action.create"
        const val SheetsImport = "home.sheets.action.import"
        const val DrawerRoot = "home.drawer.root"
        const val DrawerSettings = "home.drawer.settings"
        const val DrawerPluginManagement = "home.drawer.pluginManagement"
        const val DrawerPermissions = "home.drawer.permissions"
    }

    object Settings {
        const val PluginManagementEntry = "settings.pluginManagement.entry"
    }
}
```

Also define row-level anchor rules in the same file:

```kotlin
object FidelityAnchorPatterns {
    fun mineSheetItem(playlistId: String) = "home.sheets.item.mine.$playlistId"
    fun starredSheetItem(sheetId: String) = "home.sheets.item.starred.$sheetId"
    fun drawerSection(sectionKey: String) = "home.drawer.section.$sectionKey"
}
```

Use these patterns instead of ad-hoc row tags anywhere the dump/diff flow needs per-row evidence.

Also add:

```kotlin
@Serializable
data object LocalRoute
```

to `Routes.kt`.

- [ ] **Step 4: Re-run the tests**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt \
  core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt \
  app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeAnchorContractTest.kt
git commit -m "feat(home): add local route and canonical fidelity anchors"
```

---

## Task 2: Minimal `StarredSheet` Persistence and DB Migration

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDao.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/Migrations.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDaoTest.kt`
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/AppDatabaseMigrationTest.kt`
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryTest.kt`

- [ ] **Step 1: Write the failing mapper and DAO tests**

Start with:

```kotlin
// data/src/androidTest/java/.../StarredSheetDaoTest.kt
@Test
fun observeAllStarredSheets_returnsUpdatedAtDescending() = runTest {
    dao.upsert(
        StarredSheetEntity(
            id = "sheet-2",
            platform = "demo",
            title = "Later",
            artist = "B",
            coverUri = "https://example.com/2.jpg",
            sourceUrl = null,
            createdAt = 2L,
            updatedAt = 2L,
        )
    )
    dao.upsert(
        StarredSheetEntity(
            id = "sheet-1",
            platform = "demo",
            title = "Earlier",
            artist = "A",
            coverUri = "https://example.com/1.jpg",
            sourceUrl = null,
            createdAt = 1L,
            updatedAt = 1L,
        )
    )

    assertEquals(listOf("sheet-2", "sheet-1"), dao.observeAll().first().map { it.id })
}
```

```kotlin
// data/src/androidTest/java/.../StarredSheetRepositoryTest.kt
@Test
fun upsertStarredSheet_exposesMappedModel() = runTest {
    val repository = StarredSheetRepository(dao)

    repository.upsert(
        StarredSheet(
            id = "sheet-1",
            platform = "demo",
            title = "Daily Mix",
            artist = "Demo",
            coverUri = "https://example.com/cover.jpg",
            sourceUrl = "https://example.com/sheet",
        )
    )

    val result = repository.observeAll().first()
    assertEquals("sheet-1", result.single().id)
}
```

- [ ] **Step 2: Run the new tests and confirm they fail**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDaoTest
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.StarredSheetRepositoryTest
```

Expected: FAIL because the starred-sheet model/DAO/repository do not exist yet.

- [ ] **Step 3: Implement the minimal model, entity, DAO, repository, and migration**

Use this model shape:

```kotlin
data class StarredSheet(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
)
```

Use this Room entity shape:

```kotlin
@Entity(tableName = "starred_sheets")
data class StarredSheetEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

Add DAO APIs:
- `fun observeAll(): Flow<List<StarredSheetEntity>>`
- `@Upsert suspend fun upsert(entity: StarredSheetEntity)`
- `@Query("DELETE FROM starred_sheets WHERE id = :id") suspend fun deleteById(id: String)`

Add `MIGRATION_1_2` that creates `starred_sheets` and bump DB version to `2`.

- [ ] **Step 4: Add the migration test**

Write a failing migration test that opens a version-1 DB, runs `MIGRATION_1_2`, and asserts that `starred_sheets` exists.

Run:

```bash
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.AppDatabaseMigrationTest
```

Expected: FAIL before migration is registered, PASS after registration in `DataModule`.

- [ ] **Step 5: Re-run the data tests**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDaoTest
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.StarredSheetRepositoryTest
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.AppDatabaseMigrationTest
```

Expected: PASS on all three runs.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDao.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/Migrations.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepository.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt \
  data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDaoTest.kt \
  data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/AppDatabaseMigrationTest.kt \
  data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryTest.kt
git commit -m "feat(home): add starred sheet persistence for home fidelity"
```

---

## Task 3: `HomeSheetsViewModel` and UI Models

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetTab.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsUiState.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModel.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModelTest.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt`

- [ ] **Step 1: Write the failing mapper test**

```kotlin
@Test
fun `playlist maps to mine sheet row`() {
    val playlist = Playlist(id = "pl-1", name = "Night Drive", coverUri = "file:///cover.jpg")

    val row = HomeSheetUiModel.fromPlaylist(playlist, musicCount = 12)

    assertEquals(HomeSheetTab.Mine, row.tab)
    assertEquals("Night Drive", row.title)
    assertEquals("12 首歌曲", row.subtitle)
}
```

- [ ] **Step 2: Write the failing viewmodel test**

```kotlin
@Test
fun `switchTab exposes starred rows without mutating mine rows`() = runTest {
    whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(listOf(
        Playlist(id = "pl-1", name = "Mine A", coverUri = null),
    )))
    whenever(starredSheetRepository.observeAll()).thenReturn(flowOf(listOf(
        StarredSheet(id = "sheet-1", platform = "demo", title = "Starred A", artist = "Demo", coverUri = null, sourceUrl = null),
    )))

    val viewModel = HomeSheetsViewModel(playlistRepository, starredSheetRepository)
    assertEquals(HomeSheetTab.Mine, viewModel.uiState.value.selectedTab)

    viewModel.selectTab(HomeSheetTab.Starred)

    assertEquals(listOf("Starred A"), viewModel.uiState.value.items.map { it.title })
}
```

- [ ] **Step 3: Run the tests and confirm they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModelTest" \
  --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModelTest"
```

Expected: FAIL because the `sheets` package does not exist yet.

- [ ] **Step 4: Implement the minimal mapper + viewmodel**

Use:

```kotlin
enum class HomeSheetTab { Mine, Starred }

data class HomeSheetUiModel(
    val id: String,
    val tab: HomeSheetTab,
    val title: String,
    val subtitle: String,
    val coverUri: String?,
)
```

`HomeSheetsViewModel` should:
- observe `PlaylistRepository.observeAllPlaylists()`
- observe `StarredSheetRepository.observeAll()`
- expose counts for both tabs
- keep `selectedTab` local to the viewmodel
- default to `HomeSheetTab.Mine`

- [ ] **Step 5: Re-run the tests**

Run the same command from Step 3.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets
git commit -m "feat(home): add home sheets viewmodel and ui models"
```

---

## Task 4: Extract Local Music into a Standalone `LocalRoute`

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicContent.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/navigation/LocalNavigation.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt`

- [ ] **Step 1: Move the existing local music list UI into `LocalMusicContent.kt`**

Extract the current `LocalMusicPage` and `MusicList` functionality from `HomeScreen.kt` into a reusable public composable:

```kotlin
@Composable
fun LocalMusicContent(
    uiState: HomeUiState,
    onItemClick: (MusicItem, List<MusicItem>) -> Unit,
    onItemLongClick: (MusicItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Create a minimal `LocalScreen` shell**

`LocalScreen.kt` should:
- expose `screen.local.root`
- reuse `LocalMusicContent`
- keep the current play / long-press / retry behavior
- own the top-level local-library page container

- [ ] **Step 3: Register `LocalRoute`**

Wire `LocalRoute` end to end:
- `LocalNavigation.kt` registers `composable<LocalRoute> { ... }`
- `AppNavHost.kt` adds `localScreen(...)`
- `HomeNavigation.kt` accepts `onNavigateToLocal: () -> Unit`

- [ ] **Step 4: Verify route wiring and compile**

Run:

```bash
./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin \
  :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt \
  app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt
git commit -m "feat(home): add standalone local route shell"
```

---

## Task 5: Rewrite the Home Screen to RN Structure

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeNavBar.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeOperations.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`
- Create: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt`

- [ ] **Step 1: Write the failing home-structure instrumentation test**

Create `HomeFidelityHomeStructureTest.kt` with at least these checks:

```kotlin
@Test
fun home_exposes_singleScrollAnchors_andDrawerOpens() {
    composeRule.onNodeWithTag(FidelityAnchors.Screen.HomeRoot).assertExists()
    composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarRoot).assertExists()
    composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsRoot).assertExists()
    composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsRoot).assertExists()

    composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
    composeRule.onNodeWithTag(FidelityAnchors.Home.DrawerRoot).assertExists()
}

@Test
fun home_content_remains_visible_above_existingMiniPlayer() {
    composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsRoot).assertIsDisplayed()
}
```

- [ ] **Step 2: Run the instrumentation test to verify it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeFidelityHomeStructureTest
```

Expected: FAIL because the current home screen still uses pager tabs and has no canonical anchors.

- [ ] **Step 3: Split the home UI into focused components**

Implement:
- `HomeNavBar.kt` for menu + search
- `HomeOperations.kt` for the four shortcut cards
- `HomeDrawerContent.kt` for drawer title/items
- `HomeSheetsSection.kt` for `我的歌单 / 收藏歌单`, counts, create/import buttons, and list rows

Use `Modifier.semantics { testTagsAsResourceId = true }` at the screen root and `Modifier.testTag(...)` for canonical anchors so `uiautomator dump` sees them as stable ids.

- [ ] **Step 4: Rewrite `HomeScreen.kt` to a single vertical scroll surface**

Replace the current `TabRow + HorizontalPager` structure with:

```kotlin
ModalNavigationDrawer {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.HomeRoot)
            .semantics { testTagsAsResourceId = true }
    ) {
        item { HomeNavBar(...) }
        item { HomeOperations(...) }
        item { HomeSheetsSection(...) }
    }
}
```

The local-music shortcut must call `onNavigateToLocal`, not scroll the existing page.

- [ ] **Step 5: Re-run the home-structure test**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt \
  app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeFidelityHomeStructureTest.kt
git commit -m "feat(home): rebuild home screen as RN-style scroll surface"
```

---

## Task 6: Root Anchors and Entry Navigation Coverage

**Files:**
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
- Create: `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt`

- [ ] **Step 1: Write the failing entry-navigation test**

Create tests like:

```kotlin
@Test
fun searchEntry_opensSearchRoot() {
    composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarSearch).performClick()
    composeRule.onNodeWithTag(FidelityAnchors.Screen.SearchRoot).assertExists()
}

@Test
fun pluginManagementEntry_exposesSettingsPluginAnchor() {
    composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
    composeRule.onNodeWithTag(FidelityAnchors.Home.DrawerPluginManagement).performClick()
    composeRule.onNodeWithTag(FidelityAnchors.Screen.SettingsRoot).assertExists()
    composeRule.onNodeWithTag(FidelityAnchors.Settings.PluginManagementEntry).assertExists()
}
```

- [ ] **Step 2: Run the instrumentation test and confirm it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeEntryNavigationTest
```

Expected: FAIL because the destination roots and settings plugin-management entry tags do not exist yet.

- [ ] **Step 3: Add root anchors to all entry target screens**

At the top-level container of each target screen, add:

```kotlin
Modifier
    .fillMaxSize()
    .testTag(FidelityAnchors.Screen.SearchRoot)
    .semantics { testTagsAsResourceId = true }
```

Use the matching anchor constant for each route.

- [ ] **Step 4: Add the plugin-management entry anchor inside `SettingsScreen.kt`**

The section header row already exists; tag the visible plugin-management entry point with:

```kotlin
Modifier.testTag(FidelityAnchors.Settings.PluginManagementEntry)
```

so the drawer acceptance rule has a concrete target.

- [ ] **Step 5: Re-run the entry-navigation test**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt \
  app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt
git commit -m "test(home): add root anchors and entry navigation coverage"
```

---

## Task 7: RN Anchor Keys and Canonical Mapping

**Files:**
- Modify: `/Users/zili/code/android/MusicFree/src/pages/home/components/navBar.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/home/components/homeBody/operations.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/home/components/homeBody/sheets.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/home/components/drawer/index.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/searchPage/index.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/recommendSheets/index.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/topList/index.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/history/index.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/setting/index.tsx`
- Modify: `/Users/zili/code/android/MusicFree/src/pages/localMusic/index.tsx`
- Create: `docs/convergence/home-fidelity/manifests/rn-anchor-map.md`

- [ ] **Step 1: Add canonical `testID` values to RN home and target screens**

Add `testID` values matching the canonical anchor keys, for example:

```tsx
<Pressable testID="home.navBar.search" ... />
<View testID="home.operations.root" ... />
<TouchableWithoutFeedback testID="home.sheets.tab.mine" ... />
<DrawerContentScrollView testID="home.drawer.root" ... />
<SafeAreaView testID="screen.search.root" ... />
```

- [ ] **Step 2: Write the RN anchor-map manifest**

For each canonical key used in the home-spec acceptance path, record:
- canonical key
- RN file path
- node type (`testID`, visible text, or `accessibilityLabel`)
- page state required for capture

- [ ] **Step 3: Build and launch the RN app**

Run:

```bash
cd /Users/zili/code/android/MusicFree/android
./gradlew installDebug
adb -s emulator-5554 shell am start -S -n fun.upup.musicfree/.MainActivity
```

Expected: the RN app installs and launches on the same golden emulator.

- [ ] **Step 4: Verify at least one canonical RN anchor appears in dump**

Run:

```bash
adb -s emulator-5554 shell uiautomator dump /sdcard/rn-home.xml
adb -s emulator-5554 pull /sdcard/rn-home.xml /tmp/rn-home.xml
rg "home\\.navBar\\.search|home\\.drawer\\.root|screen\\.search\\.root" /tmp/rn-home.xml
```

Expected: at least one injected canonical anchor is visible in the dumped XML.

- [ ] **Step 5: Commit the RN-side anchor work**

```bash
cd /Users/zili/code/android/MusicFree
git add src/pages/home/components/navBar.tsx \
  src/pages/home/components/homeBody/operations.tsx \
  src/pages/home/components/homeBody/sheets.tsx \
  src/pages/home/components/drawer/index.tsx \
  src/pages/searchPage/index.tsx \
  src/pages/recommendSheets/index.tsx \
  src/pages/topList/index.tsx \
  src/pages/history/index.tsx \
  src/pages/setting/index.tsx \
  src/pages/localMusic/index.tsx
git commit -m "test(home): add canonical anchor ids for fidelity capture"
```

- [ ] **Step 6: Commit the anchor map in this repo**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git add docs/convergence/home-fidelity/manifests/rn-anchor-map.md
git commit -m "docs(home): add RN anchor mapping for fidelity capture"
```

---

## Task 8: Golden-State Manifests, Restore Scripts, and Capture Scripts

**Files:**
- Create: `scripts/convergence/home-fidelity/restore-android-home-state.sh`
- Create: `scripts/convergence/home-fidelity/restore-rn-home-state.sh`
- Create: `scripts/convergence/home-fidelity/capture-android-home.sh`
- Create: `scripts/convergence/home-fidelity/capture-rn-home.sh`
- Create: `scripts/convergence/home-fidelity/assert_capture_state.py`
- Create: `docs/convergence/home-fidelity/fixtures/android/musicfree.db`
- Create: `docs/convergence/home-fidelity/fixtures/android/musicfree.db-wal`
- Create: `docs/convergence/home-fidelity/fixtures/android/musicfree.db-shm`
- Create: `docs/convergence/home-fidelity/fixtures/rn/RKStorage`
- Create: `docs/convergence/home-fidelity/fixtures/rn/RKStorage-journal`
- Create: `docs/convergence/home-fidelity/manifests/golden-data-state.md`
- Create: `docs/convergence/home-fidelity/diffs/README.md`

- [ ] **Step 1: Write the golden-data manifest**

Document:
- exact emulator id + display settings
- selected tab
- `我的歌单` rows
- `收藏歌单` rows
- `miniPlayerVisibility: hidden` as the default golden baseline for this plan
- Android build commit
- RN reference commit

The manifest must also include this checklist:
- `我的歌单 >= 2`
- `收藏歌单 >= 2`
- fixed row order for both tabs
- fixed title + subtitle for every row
- fixed cover provenance for every row (`copied asset`, `fixture url`, `local file`, etc.)
- explicit `controlled-empty` table for any fragment that is allowed to stay empty during capture

For this plan, do not define the baseline as “mini-player visible”. If a future iteration needs a visible-mini-player capture lane, create a second named capture profile instead of overloading the default golden state.

- [ ] **Step 2: Export the RN golden-state fixture**

After manually or scriptably preparing the RN app into the approved golden state once, export the storage files into versioned fixtures:

```bash
adb -s emulator-5554 shell am force-stop fun.upup.musicfree
adb -s emulator-5554 exec-out run-as fun.upup.musicfree cat databases/RKStorage > \
  docs/convergence/home-fidelity/fixtures/rn/RKStorage
adb -s emulator-5554 exec-out run-as fun.upup.musicfree cat databases/RKStorage-journal > \
  docs/convergence/home-fidelity/fixtures/rn/RKStorage-journal || true
```

Expected: the fixture directory now contains the canonical RN database image.

- [ ] **Step 3: Implement `restore-rn-home-state.sh`**

Base it on the discovered RN storage location:
- app package: `fun.upup.musicfree`
- DB file: `databases/RKStorage`

The script should:
1. stop the RN app
2. push the curated `RKStorage` fixture and `RKStorage-journal` when present to `/data/local/tmp/`
3. copy them into the app sandbox with `run-as`
4. relaunch the app on home

Expected command pattern:

```bash
adb -s "$DEVICE" push "$FIXTURE_DIR/RKStorage" /data/local/tmp/home-fidelity-RKStorage
adb -s "$DEVICE" push "$FIXTURE_DIR/RKStorage-journal" /data/local/tmp/home-fidelity-RKStorage-journal || true
adb -s "$DEVICE" shell run-as fun.upup.musicfree cp /data/local/tmp/home-fidelity-RKStorage databases/RKStorage
adb -s "$DEVICE" shell run-as fun.upup.musicfree cp /data/local/tmp/home-fidelity-RKStorage-journal databases/RKStorage-journal || true
adb -s "$DEVICE" shell am start -S -n fun.upup.musicfree/.MainActivity
```

- [ ] **Step 4: Export the Android golden-state fixture**

After the Android app reaches the approved golden state once, export the Room database and sidecars:

```bash
adb -s emulator-5554 shell am force-stop com.zili.android.musicfreeandroid
adb -s emulator-5554 exec-out run-as com.zili.android.musicfreeandroid cat databases/musicfree.db > \
  docs/convergence/home-fidelity/fixtures/android/musicfree.db
adb -s emulator-5554 exec-out run-as com.zili.android.musicfreeandroid cat databases/musicfree.db-wal > \
  docs/convergence/home-fidelity/fixtures/android/musicfree.db-wal || true
adb -s emulator-5554 exec-out run-as com.zili.android.musicfreeandroid cat databases/musicfree.db-shm > \
  docs/convergence/home-fidelity/fixtures/android/musicfree.db-shm || true
```

Expected: `musicfree.db` exists; sidecars are exported when present.

- [ ] **Step 5: Implement `restore-android-home-state.sh`**

Use the same fixture-copy strategy as RN, with the Android Room DB file:
- app package: `com.zili.android.musicfreeandroid`
- DB file: `musicfree.db`

Expected command pattern:

```bash
adb -s "$DEVICE" push "$FIXTURE_DIR/musicfree.db" /data/local/tmp/home-fidelity-musicfree.db
adb -s "$DEVICE" push "$FIXTURE_DIR/musicfree.db-wal" /data/local/tmp/home-fidelity-musicfree.db-wal || true
adb -s "$DEVICE" push "$FIXTURE_DIR/musicfree.db-shm" /data/local/tmp/home-fidelity-musicfree.db-shm || true
adb -s "$DEVICE" shell am force-stop com.zili.android.musicfreeandroid
adb -s "$DEVICE" shell run-as com.zili.android.musicfreeandroid cp /data/local/tmp/home-fidelity-musicfree.db databases/musicfree.db
adb -s "$DEVICE" shell run-as com.zili.android.musicfreeandroid cp /data/local/tmp/home-fidelity-musicfree.db-wal databases/musicfree.db-wal || true
adb -s "$DEVICE" shell run-as com.zili.android.musicfreeandroid cp /data/local/tmp/home-fidelity-musicfree.db-shm databases/musicfree.db-shm || true
adb -s "$DEVICE" shell am start -S -n com.zili.android.musicfreeandroid/.MainActivity
```

- [ ] **Step 6: Implement both capture scripts**

Each capture script must emit:
- raw screenshot
- `uiautomator dump`
- cropped content-area screenshot

Use a deterministic crop rectangle stored in `golden-data-state.md`:
- `crop.leftPx`
- `crop.topPx`
- `crop.rightPx`
- `crop.bottomPx`

Host requirement for this plan: macOS with built-in `sips` available on `PATH`, plus `python3` for dump parsing and post-condition checks.

Use macOS `sips` for the crop step and always emit:
- `raw/<state>-<fragment>.png`
- `cropped/<state>-<fragment>.png`
- `dumps/<state>-<fragment>.xml`

```bash
WIDTH=$((RIGHT_PX - LEFT_PX))
HEIGHT=$((BOTTOM_PX - TOP_PX))
sips --cropOffset "$TOP_PX" "$LEFT_PX" -c "$HEIGHT" "$WIDTH" "$RAW_PNG" --out "$CROPPED_PNG"
```

into `docs/convergence/home-fidelity/` using the naming scheme from the spec.

Both capture scripts must also own deterministic state transitions:

- `home-top`: restore app state, wait until dump contains `screen.home.root`, `home.navBar.root`, and `home.operations.root`
- `home-sheets`: restore app state, perform scripted vertical swipes until dump contains `home.sheets.root` and at least one `home.sheets.item.*` anchor
- `home-scroll`: restore app state, scroll until the full home stack is represented by `screen.home.root` plus visible sheet-item anchors
- `drawer-open`: restore app state, parse `home.navBar.menu` bounds from dump with `python3`, tap that anchor, then re-dump until `home.drawer.root` appears

Required post-condition check before saving artifacts:

```bash
python3 scripts/convergence/home-fidelity/assert_capture_state.py \
  --xml "$DUMP_XML" \
  --state "$STATE" \
  --fragment "$FRAGMENT"
```

The helper should fail when the required anchors for the requested state are missing, instead of silently saving unusable artifacts.

- [ ] **Step 7: Lint the scripts and dry-run them**

Run:

```bash
bash -n scripts/convergence/home-fidelity/*.sh
```

Then dry-run:

```bash
scripts/convergence/home-fidelity/restore-rn-home-state.sh emulator-5554
scripts/convergence/home-fidelity/restore-android-home-state.sh emulator-5554
scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-top nav-bar
scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-top nav-bar
```

Expected: scripts finish without shell errors and create files under `docs/convergence/home-fidelity/`.

- [ ] **Step 8: Commit**

```bash
git add scripts/convergence/home-fidelity \
  docs/convergence/home-fidelity/fixtures/android/musicfree.db \
  docs/convergence/home-fidelity/fixtures/android/musicfree.db-wal \
  docs/convergence/home-fidelity/fixtures/android/musicfree.db-shm \
  docs/convergence/home-fidelity/fixtures/rn/RKStorage \
  docs/convergence/home-fidelity/fixtures/rn/RKStorage-journal \
  docs/convergence/home-fidelity/manifests/golden-data-state.md \
  docs/convergence/home-fidelity/diffs/README.md
git commit -m "chore(home): add fidelity restore and capture tooling"
```

---

## Task 9: Full Verification Bundle and Baseline Artifacts

**Files:**
- Modify: `docs/convergence/home-fidelity/manifests/golden-data-state.md`
- Create or update: `docs/convergence/home-fidelity/diffs/home-top-nav-bar.md`
- Create or update: `docs/convergence/home-fidelity/diffs/home-top-operations.md`
- Create or update: `docs/convergence/home-fidelity/diffs/home-sheets-sheets-header.md`
- Create or update: `docs/convergence/home-fidelity/diffs/home-sheets-sheets-list.md`
- Create or update: `docs/convergence/home-fidelity/diffs/home-scroll-home-scroll.md`
- Create or update: `docs/convergence/home-fidelity/diffs/drawer-open-drawer.md`

- [ ] **Step 1: Run the unit + instrumentation regression bundle**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest" \
  :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeAnchorContractTest" \
  --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModelTest" \
  --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModelTest"

./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDaoTest
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.StarredSheetRepositoryTest
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.AppDatabaseMigrationTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.MainActivityStartupTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeFidelityHomeStructureTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeEntryNavigationTest
```

Expected: all listed suites PASS.

- [ ] **Step 2: Restore both apps to the golden state**

Run:

```bash
scripts/convergence/home-fidelity/restore-rn-home-state.sh emulator-5554
scripts/convergence/home-fidelity/restore-android-home-state.sh emulator-5554
```

Expected: both apps return to their documented home baseline.

- [ ] **Step 3: Capture the baseline artifacts**

Run:

```bash
scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-top nav-bar
scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-top operations
scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-sheets sheets-header
scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-sheets sheets-list
scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-scroll home-scroll
scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 drawer-open drawer

scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-top nav-bar
scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-top operations
scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-sheets sheets-header
scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-sheets sheets-list
scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-scroll home-scroll
scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 drawer-open drawer
```

Expected: paired RN/Android screenshots and XML dumps exist for `nav-bar`, `operations`, `sheets-header`, `sheets-list`, `home-scroll`, and `drawer`.

- [ ] **Step 4: Fill the diff templates**

For each fragment diff file, record:
- canonical anchor order
- visible text
- clickability
- size / spacing / radius / font / color evidence source
- closed vs open status

- [ ] **Step 5: Commit**

```bash
git add docs/convergence/home-fidelity
git commit -m "docs(home): record baseline fidelity artifacts and diffs"
```

---

## Notes for Implementers

- Prefer `Modifier.testTag(...)` plus `semantics { testTagsAsResourceId = true }` on Android; that is the least invasive path for `uiautomator dump`.
- Do not keep the current `HorizontalPager` home structure alive “temporarily”. The home shortcut contract changes as soon as `LocalRoute` exists.
- Keep `HomeViewModel` focused on local music + playback integration. Put `我的歌单 / 收藏歌单` tab state in `HomeSheetsViewModel`.
- Avoid inventing a full starred-sheet feature. This plan only needs minimal persistence plus golden-state reproducibility.
- Any UI asset needed for fidelity should be copied from `/Users/zili/code/android/MusicFree` instead of approximated.
