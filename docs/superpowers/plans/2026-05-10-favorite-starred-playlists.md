# Favorite Starred Playlists Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the default `我喜欢` playlist whenever it is missing and wire real remote starred playlists through plugin sheet detail, home display, and navigation.

**Architecture:** Keep default playlist recovery in `:data` so UI cannot mask missing persistent state. Store remote starred sheet seeds in `starred_sheets`, expose them through `HomeViewModel`, and navigate starred rows to `PluginSheetDetailRoute` with a `PluginSheetSeedStore` seed.

**Tech Stack:** Kotlin, Room, Flow, Hilt ViewModel, Jetpack Compose Material3, Navigation Compose, Robolectric unit tests.

---

## File Structure

- Modify `data/build.gradle.kts`: add JVM test dependencies needed for Room/Robolectric repository tests.
- Modify `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt`: add optional seed fields.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/db/converter/Converters.kt`: expose raw map JSON helpers.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt`: add `description`, `artwork`, `worksNum`, and `rawJson`.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt`: add `insertPlaylistIgnore`.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDao.kt`: add `observeExists`.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`: bump schema version for destructive dev reset.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/db/SeedFavoriteCallback.kt`: seed in `onOpen`.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt`: map extended fields with `Converters`.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`: ensure default playlist before observations and favorite writes.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepository.kt`: inject `Converters`, expose `observeIsStarred`, and toggle.
- Create `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryFavoriteRecoveryTest.kt`: JVM regression tests for missing default playlist recovery.
- Create `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryJvmTest.kt`: JVM tests for starred sheet toggle and raw round-trip.
- Modify `data/src/androidTest/java/.../StarredSheetRepositoryTest.kt`: update constructor call for `Converters`.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt`: expose starred sheets and update constructor.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`: pass real starred rows and callback.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeMockVisualFactory.kt`: remove `STARRED_ROWS` and accept `starredRows`.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`: add starred navigation callback.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt`: preserve seed fields and convert to `MusicSheetItemBase`.
- Modify `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`: navigate starred home rows to plugin sheet detail.
- Modify home tests for constructor and builder signature changes.
- Create `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapper.kt`: map plugin sheet to `StarredSheet`.
- Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapperTest.kt`: mapping tests.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`: inject starred repository and expose sheet star state/toggle.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`: add heart action to scaffold app bar.

## Task 1: Data Tests

**Files:**
- Modify: `data/build.gradle.kts`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryFavoriteRecoveryTest.kt`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryJvmTest.kt`

- [ ] **Step 1: Add JVM test dependencies**

Add to `data/build.gradle.kts` test dependencies:

```kotlin
testImplementation(libs.robolectric)
testImplementation(libs.androidx.test.core)
testImplementation(libs.androidx.room.testing)
```

- [ ] **Step 2: Write failing favorite recovery tests**

Create tests that delete `favorite`, call `observeAllPlaylists().first()` and `toggleFavorite(item)`, and assert `favorite/我喜欢` is restored.

- [ ] **Step 3: Write failing starred sheet tests**

Create tests that call `repository.toggle(sheet)`, assert `observeIsStarred(id, platform)` changes false → true → false, and assert `description/artwork/worksNum/raw` round-trip through `observeAll().first()`.

- [ ] **Step 4: Run red tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests '*PlaylistRepositoryFavoriteRecoveryTest' --tests '*StarredSheetRepositoryJvmTest'
```

Expected: compile or test failures because repository APIs and entity fields do not exist yet.

## Task 2: Data Implementation

**Files:**
- Modify all `:data` and `:core` files listed in File Structure.

- [ ] **Step 1: Implement default favorite recovery**

Add `SeedFavoriteCallback.onOpen`, `PlaylistDao.insertPlaylistIgnore`, and `PlaylistRepository.ensureFavoritePlaylist()` with `INSERT OR IGNORE` semantics. Call ensure from playlist observations and favorite write paths.

- [ ] **Step 2: Implement starred sheet persistence**

Extend `StarredSheet`, `StarredSheetEntity`, mapper, DAO, and repository. Keep old constructor call sites valid by giving new `StarredSheet` fields default values.

- [ ] **Step 3: Run green data tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests '*PlaylistRepositoryFavoriteRecoveryTest' --tests '*StarredSheetRepositoryJvmTest'
```

Expected: both tests pass.

## Task 3: Home Tests

**Files:**
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeMockVisualFactoryTest.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModelTest.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModelTest.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt`

- [ ] **Step 1: Update tests for real starred rows**

Change `buildHomeVisualUiModel(...)` calls to pass a `starredRows` list. Assert starred tab rows equal that list, not mock ids.

- [ ] **Step 2: Add seed preservation assertions**

In `HomeSheetUiModelTest`, create a `StarredSheet` with `description`, `artwork`, `worksNum`, and `raw`, map it to `HomeSheetUiModel`, then to `MusicSheetItemBase`, and assert fields are preserved.

- [ ] **Step 3: Run red home tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*HomeMockVisualFactoryTest' --tests '*HomeSheetUiModelTest' --tests '*HomeViewModelTest'
```

Expected: failures until production code accepts real starred rows and the new ViewModel dependency.

## Task 4: Home Implementation

**Files:**
- Modify all home/app navigation files listed in File Structure.

- [ ] **Step 1: Wire real starred rows**

Inject `StarredSheetRepository` into `HomeViewModel`, expose `starredSheets`, map them in `HomeScreen`, and remove `STARRED_ROWS`.

- [ ] **Step 2: Wire starred navigation**

Add `onNavigateToStarredSheet` through `HomeScreen` and `homeScreen`, then use `HomeSheetUiModel.toMusicSheetItemBase()` in `AppNavHost` to seed and navigate to `PluginSheetDetailRoute`.

- [ ] **Step 3: Run green home tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*HomeMockVisualFactoryTest' --tests '*HomeSheetUiModelTest' --tests '*HomeViewModelTest' --tests '*HomeSheetsViewModelTest'
```

Expected: listed tests pass.

## Task 5: Plugin Sheet Tests

**Files:**
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapperTest.kt`

- [ ] **Step 1: Write mapper tests**

Assert `MusicSheetItemBase.toStarredSheet()` copies id, platform, title, artist, description, `coverImg`, artwork, worksNum, and raw. Assert `StarredSheet.toMusicSheetItemBase()` restores the same seed fields.

- [ ] **Step 2: Run red mapper test**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*PluginSheetStarredMapperTest'
```

Expected: compile failure because mapper functions do not exist.

## Task 6: Plugin Sheet Implementation

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapper.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`

- [ ] **Step 1: Implement mapper**

Add public/internal mapper functions used by ViewModel tests and app navigation.

- [ ] **Step 2: Add ViewModel star state and toggle**

Inject `StarredSheetRepository`, expose `isSheetStarred`, and call `starredSheetRepository.toggle((currentSheet ?: seedSheet()).toStarredSheet())`.

- [ ] **Step 3: Add app bar heart**

Use `MusicFreeScreenScaffold(actions = { IconButton(...) })` with `R.drawable.ic_heart` or `R.drawable.ic_heart_outline`.

- [ ] **Step 4: Run green mapper and row tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*PluginSheetStarredMapperTest' --tests '*PluginSheetMusicRowTest' --tests '*PluginSheetMusicRowDisplayTest'
```

Expected: tests pass.

## Task 7: Final Verification

**Files:**
- All changed files.

- [ ] **Step 1: Run focused unit tests**

```bash
./gradlew :data:testDebugUnitTest :feature:home:testDebugUnitTest
```

Expected: build successful.

- [ ] **Step 2: Run harness grep guard**

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: all checks pass.

- [ ] **Step 3: Run debug build**

```bash
./gradlew :app:assembleDebug
```

Expected: build successful.
