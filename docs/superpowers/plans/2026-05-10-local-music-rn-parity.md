# Local Music RN Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android local music match RN’s themed AppBar and core local-library actions: search, scan/import, batch edit, download list, and persistent local data.

**Architecture:** Introduce a dedicated `LocalMusicViewModel` for the local page and move the local list source to persisted `MusicRepository.observeByPlatform("local")`. Reuse the existing UI harness `MusicFreeScreenScaffold`, existing `LocalMusicScanner`, `SearchMusicListRoute.localLibrary()`, and extend `MusicListEditorLiteRoute` so local-library editing uses `MusicRepository.delete()` instead of playlist membership operations.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt ViewModel, Room repository Flow, Navigation Compose serializable routes, JUnit/Mockito coroutine tests, Gradle debug unit tests.

---

## Files

- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicViewModel.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicViewModelTest.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicContent.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/navigation/LocalNavigation.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListSourceLoaderTest.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModelTest.kt`
- Modify: `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`

## Task 1: Local-Library Routes and Search Source

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt`
- Test: `app/src/test/java/com/zili/android/musicfreeandroid/RoutesTest.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListSourceLoaderTest.kt`

- [ ] **Step 1: Write failing route tests**

Add a route test that constructs `MusicListEditorLiteRoute.localLibrary()`, JSON encodes/decodes it, and asserts equality. Keep the existing playlist route test with `MusicListEditorLiteRoute(playlistId = "playlist-42")`.

- [ ] **Step 2: Write failing source loader test**

Change `local library source returns empty list in minimal foundation implementation` to expect `musicRepository.observeByPlatform("local")` output. The test fixture must add a `MusicRepository` mock and verify `observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)`.

- [ ] **Step 3: Run red tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.zili.android.musicfreeandroid.RoutesTest
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.searchmusiclist.SearchMusicListSourceLoaderTest
```

Expected: route local-library editor test fails because factory/properties do not exist; source loader test fails because local source returns an empty list.

- [ ] **Step 4: Implement route/source**

Update `MusicListEditorLiteRoute` to:

```kotlin
@Serializable
data class MusicListEditorLiteRoute(
    val sourceId: String,
    val sourceType: String = SOURCE_TYPE_PLAYLIST,
) {
    constructor(playlistId: String) : this(
        sourceId = playlistId,
        sourceType = SOURCE_TYPE_PLAYLIST,
    )

    init {
        require(sourceType == SOURCE_TYPE_PLAYLIST || sourceType == SOURCE_TYPE_LOCAL_LIBRARY)
        require(sourceType != SOURCE_TYPE_PLAYLIST || sourceId.isNotBlank())
        require(sourceType != SOURCE_TYPE_LOCAL_LIBRARY || sourceId == LOCAL_LIBRARY_SOURCE_ID)
    }

    val playlistId: String
        get() = sourceId

    companion object {
        const val SOURCE_TYPE_PLAYLIST = "playlist"
        const val SOURCE_TYPE_LOCAL_LIBRARY = "local-library"
        const val LOCAL_LIBRARY_SOURCE_ID = "local"

        fun localLibrary(): MusicListEditorLiteRoute = MusicListEditorLiteRoute(
            sourceId = LOCAL_LIBRARY_SOURCE_ID,
            sourceType = SOURCE_TYPE_LOCAL_LIBRARY,
        )
    }
}
```

Update `SearchMusicListSourceLoader` constructor to inject `MusicRepository`, and return `musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)` for `CollectionSource.LocalLibrary`.

- [ ] **Step 5: Run green tests**

Run the same two commands. Expected: both pass.

## Task 2: Persistent Local Music ViewModel

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicViewModel.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

Cover these behaviors:

- `items come from persisted local repository`: `musicRepository.observeByPlatform("local")` emits one item and `uiState` becomes `Success`.
- `scanLocalMusic persists scanned items`: mock `scanner.scan(treeUri)` to emit two items, call `scanLocalMusic(treeUri)`, verify `musicRepository.insertAll(items)`.
- `removeFromLocalLibrary deletes persisted item`: call `removeFromLocalLibrary(item)`, verify `musicRepository.delete(item)`.
- `playItem plays selected item in current list`: call `playItem(item, list)`, verify `playerController.playQueue(list, index)`.
- `download enqueues selected item with requested quality`: verify `downloader.enqueue(listOf(item), PlayQuality.HIGH)`.

- [ ] **Step 2: Run red tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.local.LocalMusicViewModelTest
```

Expected: fails because `LocalMusicViewModel` does not exist.

- [ ] **Step 3: Implement ViewModel**

Create `LocalMusicViewModel` with injected `LocalMusicScanner`, `PlayerController`, `MusicRepository`, `AppPreferences`, and `Downloader`. Expose:

```kotlin
val uiState: StateFlow<LocalMusicUiState>
val downloadActiveCount: StateFlow<Int>
val downloadedKeys: StateFlow<Set<MediaKey>>
val defaultDownloadQuality: Flow<PlayQuality>
fun scanLocalMusic(storageDirectoryUri: String? = null)
fun playItem(item: MusicItem, queue: List<MusicItem>)
fun removeFromLocalLibrary(item: MusicItem)
fun download(item: MusicItem, quality: PlayQuality)
```

`uiState` maps repository emissions to `LocalMusicUiState.Success(items)` and starts as `Loading`. `scanLocalMusic` sets `Loading`, collects scanner output, calls `musicRepository.insertAll(items)`, then lets repository Flow update the UI. Catch exceptions into `LocalMusicUiState.Error`.

- [ ] **Step 4: Run green tests**

Run the same test command. Expected: pass.

## Task 3: Themed Local AppBar and RN Actions

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/navigation/LocalNavigation.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

- [ ] **Step 1: Write failing compile-facing changes**

Change `LocalScreen` signature to require:

```kotlin
onBack: () -> Unit
onNavigateToSearchMusicList: () -> Unit
onNavigateToMusicListEditor: () -> Unit
onNavigateToDownloading: () -> Unit
```

Update navigation call sites only enough for compilation to fail at missing implementation details.

- [ ] **Step 2: Implement screen chrome**

Replace the top-level `Column` + `MusicFreeStatusBarChrome(pageBackground)` + `LocalHeaderRow` with `MusicFreeScreenScaffold(title = "本地音乐", onBack = onBack, actions = { ... })`. The actions slot includes:

- search icon: calls `onNavigateToSearchMusicList`
- overflow icon: opens `DropdownMenu`
- menu item `扫描本地音乐`: launches `ActivityResultContracts.OpenDocumentTree`, persists permission with `DocumentTreeStorageAccess`, stores URI with `AppPreferences.setStorageDirectoryUri`, then calls `viewModel.scanLocalMusic(uri.toString())`
- menu item `批量编辑`: calls `onNavigateToMusicListEditor`
- menu item `下载列表`: calls `onNavigateToDownloading` and shows active badge when active downloads exist

Keep permission handling before scan: if audio permission is denied, show `LocalMusicUiState.Error("未授予音频读取权限，请授权后重试")` and retry requests permission.

- [ ] **Step 3: Wire navigation**

Update `localScreen(...)` and `AppNavHost`:

```kotlin
localScreen(
    onBack = { navController.popBackStack() },
    onNavigateToPlayer = { navController.navigate(PlayerRoute) },
    onNavigateToSearchMusicList = { navController.navigate(SearchMusicListRoute.localLibrary()) },
    onNavigateToMusicListEditor = { navController.navigate(MusicListEditorLiteRoute.localLibrary()) },
    onNavigateToDownloading = { navController.navigate(DownloadingRoute) },
)
```

- [ ] **Step 4: Compile check**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest
```

Expected: pass after adapting imports and callbacks.

## Task 4: Local-Library Batch Edit

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModelTest.kt`

- [ ] **Step 1: Write failing local editor tests**

Add tests that instantiate the ViewModel with:

```kotlin
SavedStateHandle(
    mapOf(
        "sourceType" to MusicListEditorLiteRoute.SOURCE_TYPE_LOCAL_LIBRARY,
        "sourceId" to MusicListEditorLiteRoute.LOCAL_LIBRARY_SOURCE_ID,
    )
)
```

Verify local source uses `musicRepository.observeByPlatform("local")`, `playlistName` is `本地音乐`, and `saveChanges()` deletes removed items through `musicRepository.delete(item)`.

- [ ] **Step 2: Run red tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.musiclisteditor.MusicListEditorLiteViewModelTest
```

Expected: fails because the ViewModel does not accept `MusicRepository` and always uses playlist repository.

- [ ] **Step 3: Implement source abstraction**

Inject `MusicRepository`. Resolve route source as playlist or local-library. For playlist:

- title comes from `playlistRepository.getPlaylistById(playlistId)?.name.orEmpty()`
- items come from `playlistRepository.observeMusicInPlaylist(playlistId)`
- save removes via `playlistRepository.removeMusicFromPlaylist(playlistId, item)`
- target playlists exclude current playlist

For local-library:

- title is `本地音乐`
- items come from `musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)`
- save removes via `musicRepository.delete(item)`
- target playlists are all playlists

Keep selection, next queue, add to playlist, and download behavior unchanged.

- [ ] **Step 4: Run green tests**

Run the same test command. Expected: pass.

## Task 5: Local Row Remove Action and Verification

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicContent.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicViewModelTest.kt`

- [ ] **Step 1: Add local removal to options**

When long-pressing a local row, the options sheet must offer removing the row from the persisted local library. If using the shared `MusicItemOptionsSheet`, extend it with optional `onRemoveFromLocalLibrary` callback and only render the option when provided.

- [ ] **Step 2: Run focused tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.home.local.LocalMusicViewModelTest
```

Expected: pass.

- [ ] **Step 3: Run module verification**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Expected: all commands exit 0.

## Task 6: Final Review and Documentation Status

**Files:**
- Modify if needed: `docs/DOCS_STATUS.md`

- [ ] **Step 1: Check whether DOCS_STATUS needs an entry**

If this spec should be a current implementation input, add `docs/superpowers/specs/2026-05-10-local-music-rn-parity-design.md` to the current-spec list with relative links.

- [ ] **Step 2: Review diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: changes are limited to local music parity implementation, tests, and docs.

- [ ] **Step 3: Final code review**

Dispatch a code-review subagent with the spec path, this plan path, and the branch diff. Fix Critical and Important findings before final verification.
