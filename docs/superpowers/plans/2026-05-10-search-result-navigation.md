# Search Result Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android search result clicks match RN for music, album, artist, and sheet results.

**Architecture:** Search results become typed plugin items at the plugin boundary, then `feature:search` renders and routes by item type. Detail pages receive complete in-process seeds with typed route fallback fields.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose typed routes, Hilt ViewModels, Gradle JVM unit tests.

---

## Files

- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/api/SearchResult.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsBridge.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- Modify: `plugin/src/test/java/com/hank/musicfree/plugin/engine/JsBridgeTest.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchUiState.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/navigation/SearchNavigation.kt`
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
- Create: `feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/navigation/AlbumDetailSeedStore.kt`
- Create: `feature/home/src/main/java/com/hank/musicfree/feature/home/artistdetail/navigation/ArtistDetailSeedStore.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/AlbumDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/artistdetail/ArtistDetailViewModel.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/feature/home/albumdetail/navigation/AlbumDetailSeedStoreTest.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/feature/home/artistdetail/navigation/ArtistDetailSeedStoreTest.kt`
- Modify route tests under `app/src/test/java/com/hank/musicfree/`

## Task 1: Typed Plugin Search Results

- [ ] Write failing `JsBridgeTest` cases for `parseSearchResult(..., type = "album")`, `type = "artist"`, and `type = "sheet"`.
- [ ] Change `SearchResult.data` to `List<PluginSearchItem>` and add sealed item classes in `SearchResult.kt`.
- [ ] Update `JsBridge.parseSearchResult` to accept `type` and parse each item with `toMusicItem`, `toAlbumItemBase`, `toArtistItemBase`, or `toMusicSheetItemBase`.
- [ ] Update `LoadedPlugin.search` to call `JsBridge.parseSearchResult(map, fallbackPlatform = info.platform, type = type)`.
- [ ] Update plugin tests and run `./gradlew :plugin:testDebugUnitTest --no-daemon`.

## Task 2: Type-Aware Search ViewModel

- [ ] Write failing `SearchViewModelTest` for a plugin that supports only `album`, then selecting `ALBUM` should expose that plugin and call `plugin.search("hello", 1, "album")`.
- [ ] Change `PluginManager.getSearchablePlugins(type: String = "music")` to filter by the requested type.
- [ ] Update `SearchViewModel` to observe sorted enabled plugins, derive current media type plugins, and keep `selectedPlatform` valid for the current type.
- [ ] Update search result state to store `PluginSearchItem` values.
- [ ] Keep `resolveAndPlay` music-only and run `./gradlew :feature:search:testDebugUnitTest --no-daemon`.

## Task 3: Detail Seeds And Routes

- [ ] Add failing seed store tests for album and artist stores: `put`, first `take`, second `take`, blank token.
- [ ] Add `AlbumDetailSeedStore` and `ArtistDetailSeedStore`.
- [ ] Extend `AlbumDetailRoute` with `date`, `description`, `worksNum`, `seedToken`.
- [ ] Extend `ArtistDetailRoute` with `description`, `fans`, `worksNum`, `seedToken`.
- [ ] Update detail ViewModels to prefer seed stores before route fallback.
- [ ] Update route serialization tests and run `./gradlew :feature:home:testDebugUnitTest --no-daemon`.

## Task 4: Search UI Navigation

- [ ] Update `SearchNavigation.searchScreen` to accept `onOpenAlbumDetail`, `onOpenArtistDetail`, and `onOpenSheetDetail` callbacks.
- [ ] Update `AppNavHost` to store seeds and navigate to `AlbumDetailRoute`, `ArtistDetailRoute`, and `PluginSheetDetailRoute`.
- [ ] Update `SearchScreen` result rendering so music uses the existing row, album/artist use big list rows, and sheet uses a grid of cover cards.
- [ ] Ensure long-click menu and add-to-playlist actions appear only for music items.
- [ ] Run `./gradlew :feature:search:testDebugUnitTest --no-daemon`.

## Task 5: Final Verification

- [ ] Run `./gradlew :plugin:testDebugUnitTest --no-daemon`.
- [ ] Run `./gradlew :feature:search:testDebugUnitTest --no-daemon`.
- [ ] Run `./gradlew :feature:home:testDebugUnitTest --no-daemon`.
- [ ] Run `./gradlew :app:testDebugUnitTest --no-daemon`.
- [ ] Run `python3 scripts/dev-harness/grep-check.py`.
- [ ] Run `./gradlew :app:assembleDebug --no-daemon`.
- [ ] Review `git diff --check` and `git status --short`.
