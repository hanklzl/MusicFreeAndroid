# Music Sheet Detail Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align Android music-sheet detail pages with RN so page intro/header content scrolls with the list instead of staying fixed above it.

**Architecture:** Keep each detail Screen on `MusicFreeScreenScaffold`, but ensure sheet metadata headers are `LazyColumn` items. Avoid new shared abstractions; use the existing `MusicSheetPageHeader` and add a focused source-structure regression test for this UI contract.

**Tech Stack:** Kotlin, Jetpack Compose, Compose `LazyColumn`, JUnit4, Gradle Debug unit tests.

---

## File Map

- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
  - Move `PlaylistDetailHeader` and `EmptyState` into one `LazyColumn`.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
  - Add `MusicSheetPageHeader` as the first `LazyColumn` item.
  - Reuse `PlayAllBar` for album actions.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailUiState.kt`
  - Carry the current album item for header cover, count, and description.
- Modify `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt`
  - Persist `detail.albumItem ?: seed` into UI state and expose `playAll()`.
- Modify `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/PlayAllBar.kt`
  - Add an opt-out for the add-to-playlist icon so album detail does not show a dead action.
- Create `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/detail/ScrollableHeaderSourceTest.kt`
  - Guard source structure for playlist, album, plugin sheet, and top list detail pages.
- Modify `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModelTest.kt`
  - Verify resolved album metadata is exposed for the scrollable header.

## Task 1: Add Scrollable Header Regression Test

**Files:**
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/detail/ScrollableHeaderSourceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollableHeaderSourceTest {
    private val repoRoot = File(System.getProperty("user.dir"))

    @Test
    fun `playlist detail header is inside the lazy list`() {
        val source = source("feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt")

        assertFalse(
            source.contains("Column(modifier = Modifier.fillMaxSize().padding(padding))"),
            "PlaylistDetailScreen must not keep PlaylistDetailHeader in an outer fixed Column.",
        )
        assertContainsInOrder(
            source = source,
            first = "LazyColumn(modifier = Modifier.fillMaxSize().padding(padding))",
            second = "item(key = \"header\")",
            third = "PlaylistDetailHeader(",
        )
    }

    @Test
    fun `album detail header is inside the lazy list`() {
        val source = source("feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt")

        assertContainsInOrder(
            source = source,
            first = "LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding))",
            second = "item(key = \"header\")",
            third = "MusicSheetPageHeader(",
        )
    }

    @Test
    fun `plugin sheet and top list keep headers inside lazy lists`() {
        listOf(
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt",
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt",
        ).forEach { path ->
            val source = source(path)
            assertContainsInOrder(
                source = source,
                first = "LazyColumn(",
                second = "item(key = \"header\")",
                third = "MusicSheetPageHeader(",
            )
        }
    }

    private fun source(path: String): String {
        val file = repoRoot.resolve(path)
        assertTrue(file.isFile, "Missing source file: $path")
        return file.readText()
    }

    private fun assertContainsInOrder(source: String, first: String, second: String, third: String) {
        val firstIndex = source.indexOf(first)
        val secondIndex = source.indexOf(second)
        val thirdIndex = source.indexOf(third)
        assertTrue(firstIndex >= 0, "Missing marker: $first")
        assertTrue(secondIndex > firstIndex, "Marker `$second` must appear after `$first`.")
        assertTrue(thirdIndex > secondIndex, "Marker `$third` must appear after `$second`.")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*ScrollableHeaderSourceTest' --no-daemon
```

Expected: FAIL because `PlaylistDetailScreen` still keeps the header in an outer `Column`, and `AlbumDetailScreen` has no `MusicSheetPageHeader`.

## Task 2: Move Local Playlist Header Into LazyColumn

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`

- [ ] **Step 1: Replace the outer `Column` content with a single `LazyColumn`**

Use this structure inside the `MusicFreeScreenScaffold` content after the loading guard:

```kotlin
LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
    item(key = "header") {
        PlaylistDetailHeader(
            playlist = playlist,
            musicCount = items.size,
            onPlayAll = { viewModel.playAll() },
            onSearch = { onNavigateToSearchMusicList(playlist.id) },
        )
    }
    if (items.isEmpty()) {
        item(key = "empty") {
            EmptyState(onSearchAdd = { onNavigateToSearchMusicList(playlist.id) })
        }
    } else {
        itemsIndexed(items = items, key = { _, item -> "${item.platform}::${item.id}" }) { index, item ->
            val isFavorite by viewModel.isFavoriteFlow(item)
                .collectAsStateWithLifecycle(initialValue = false)
            MusicItemRow(
                item = item,
                isFavorite = isFavorite,
                actions = setOf(
                    MusicItemAction.PlayNext,
                    MusicItemAction.ToggleFavorite,
                    MusicItemAction.AddToPlaylist,
                    MusicItemAction.RemoveFromPlaylist,
                ),
                onClick = { viewModel.playAll(startIndex = index) },
                onAction = { action ->
                    when (action) {
                        MusicItemAction.ToggleFavorite -> viewModel.toggleFavorite(item)
                        MusicItemAction.RemoveFromPlaylist -> viewModel.removeFromPlaylist(item)
                        MusicItemAction.PlayNext -> { /* PlayerController.playNext remains unwired here. */ }
                        MusicItemAction.AddToPlaylist -> viewModel.showAddToPlaylistSheet(item)
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 2: Remove now-unused imports**

Remove `androidx.compose.foundation.layout.Column` only if `EmptyState` still needs it then keep it; remove any imports that become unused after the replacement.

- [ ] **Step 3: Run the focused source test**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*ScrollableHeaderSourceTest.playlist detail header is inside the lazy list' --no-daemon
```

Expected: PASS for the playlist test.

## Task 3: Add Album Detail Header Into LazyColumn

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailUiState.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt`
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/PlayAllBar.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModelTest.kt`

- [ ] **Step 1: Add imports**

Add:

```kotlin
import com.zili.android.musicfreeandroid.core.ui.MusicSheetPageHeader
import com.zili.android.musicfreeandroid.core.ui.PlayAllBar
```

- [ ] **Step 2: Move `innerPadding` onto the `LazyColumn` and add header item**

Replace the `Column(...padding(innerPadding)) { when { ... } }` wrapper with the same `when` branches, and make the success branch:

```kotlin
LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    item(key = "header") {
        val album = uiState.albumItem
        MusicSheetPageHeader(
            cover = album?.artwork ?: album?.coverImg,
            title = album?.title ?: uiState.title,
            worksNum = album?.worksNum,
            musicListSize = uiState.musicList.size,
            description = album?.description,
            actions = {
                PlayAllBar(
                    onPlayAll = { viewModel.playAll() },
                    onAddToPlaylist = {},
                    starred = isStarred,
                    onToggleStarred = { viewModel.toggleAlbumStarred() },
                    showAddToPlaylist = false,
                )
            },
        )
    }
    itemsIndexed(
        items = uiState.musicList,
        key = { _, item -> "${item.platform}:${item.id}" },
    ) { index, item ->
        // Keep the existing row body unchanged.
    }
    if (!uiState.isEnd) {
        item(key = "footer") {
            // Keep the existing load-more footer unchanged.
        }
    }
}
```

If `AlbumDetailUiState` does not expose `albumItem`, add `val albumItem: AlbumItemBase? = null` and populate it from the ViewModel:

```kotlin
val resolvedAlbum = detail.albumItem ?: seed
currentAlbum = resolvedAlbum
_uiState.value = AlbumDetailUiState(
    title = resolvedAlbum.title ?: route.title ?: "专辑详情",
    albumItem = resolvedAlbum,
    loading = false,
    musicList = detail.musicList,
    isEnd = detail.isEnd,
    errorMessage = null,
)
```

For `loadMore()`, use `val resolvedAlbum = detail.albumItem ?: album`, update `currentAlbum = resolvedAlbum`, and copy `albumItem = resolvedAlbum` into `_uiState`.

Expose a `playAll()` method that skips empty lists and delegates to `playAt(0)` inside `viewModelScope.launch`, then call `viewModel.playAll()` from `PlayAllBar`.

Update `PlayAllBar` to add `showAddToPlaylist: Boolean = true`, and wrap the trailing folder-plus `IconButton` in `if (showAddToPlaylist)`. Existing plugin sheet and top list callers keep the default behavior; album detail passes `false`.

Add this ViewModel test:

```kotlin
@Test
fun `loadInitial exposes resolved album item for page header`() = runTest(testDispatcher) {
    val plugin = albumPlugin(
        album = AlbumItemBase(
            id = "alb-1",
            platform = "qq",
            title = "Resolved Album",
            date = "2026",
            artist = "Resolved Artist",
            description = "Album intro",
            artwork = "https://example.com/album.jpg",
            worksNum = 12,
            raw = mapOf("coverImg" to "https://example.com/cover.jpg"),
        ),
        musicList = listOf(musicItem("song-1")),
    )
    whenever(pluginManager.getPlugin("qq")).thenReturn(plugin)

    val vm = newViewModel(MutableStateFlow(false))
    advanceUntilIdle()

    assertEquals(false, vm.uiState.value.loading)
    assertEquals("Resolved Album", vm.uiState.value.title)
    assertEquals("Resolved Album", vm.uiState.value.albumItem?.title)
    assertEquals("Album intro", vm.uiState.value.albumItem?.description)
    assertEquals(12, vm.uiState.value.albumItem?.worksNum)
    assertEquals(listOf(musicItem("song-1")), vm.uiState.value.musicList)
}
```

- [ ] **Step 3: Run the focused source test**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*ScrollableHeaderSourceTest.album detail header is inside the lazy list' --no-daemon
```

Expected: PASS for the album test.

## Task 4: Full Verification and Cleanup

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run feature tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run UI harness grep guard**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: PASS with no UI harness violations.

- [ ] **Step 3: Check whitespace**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 4: Run app Debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit the branch**

Run:

```bash
git status --short
git add docs/superpowers/specs/2026-05-16-music-sheet-detail-scroll-design.md \
  docs/superpowers/plans/2026-05-16-music-sheet-detail-scroll.md \
  core/src/main/java/com/zili/android/musicfreeandroid/core/ui/PlayAllBar.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailUiState.kt \
  feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModelTest.kt \
  feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/detail/ScrollableHeaderSourceTest.kt
git commit -m "fix(home): 对齐歌单详情整体滚动"
```

Expected: one branch commit ready for squash merge back to `main`.
