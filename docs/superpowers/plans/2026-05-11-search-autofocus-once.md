# Search Autofocus Once Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make search input autofocus run only once for each search Screen back stack entry, while preserving first-entry autofocus.

**Architecture:** Store the one-shot autofocus consumption flag in each search ViewModel instance. The Screen keeps the existing `LaunchedEffect(Unit)` and frame delay, but only requests focus when the ViewModel consumes the initial autofocus request.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose back stack scoped Hilt ViewModels, Robolectric Compose tests, Gradle.

---

## File Map

- Modify: `docs/DOCS_STATUS.md`
  - Registers the new current spec.
- Create: `docs/superpowers/specs/2026-05-11-search-autofocus-once-design.md`
  - Defines the behavior and validation scope.
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
  - Owns main search initial autofocus consumption state.
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
  - Gates the existing focus request through the ViewModel.
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`
  - Adds unit coverage for main search one-shot consumption.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListViewModel.kt`
  - Owns search-music-list initial autofocus consumption state.
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt`
  - Gates the existing focus request through the ViewModel.
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListViewModelTest.kt`
  - Adds unit coverage for search-music-list one-shot consumption.
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt`
  - Adds UI regression coverage that a reused ViewModel does not refocus on re-entry.

## Task 1: Write Failing Focus Consumption Tests

**Files:**
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListViewModelTest.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt`

- [ ] **Step 1: Add SearchViewModel consumption test**

Add this test near the top of `SearchViewModelTest`, after `createViewModel()`:

```kotlin
@Test
fun `initial autofocus request is consumed once per view model instance`() = runTest {
    whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit)
    val viewModel = createViewModel()

    assertTrue(viewModel.consumeInitialAutofocusRequest())
    assertFalse(viewModel.consumeInitialAutofocusRequest())
}
```

Also add the import:

```kotlin
import org.junit.Assert.assertFalse
```

- [ ] **Step 2: Add SearchMusicListViewModel consumption test**

Add this test near the top of `SearchMusicListViewModelTest`:

```kotlin
@Test
fun `initial autofocus request is consumed once per view model instance`() {
    val viewModel = SearchMusicListViewModel(
        route = SearchMusicListRoute.localLibrary(),
        sourceLoader = SearchMusicListSourceLoader(playlistRepository, playerController, musicRepository),
        playerController = playerController,
    )

    assertTrue(viewModel.consumeInitialAutofocusRequest())
    assertFalse(viewModel.consumeInitialAutofocusRequest())
}
```

Also add the import:

```kotlin
import org.junit.Assert.assertFalse
```

- [ ] **Step 3: Refactor SearchMusicListScreenFocusTest fixture**

In `SearchMusicListScreenFocusTest`, add these imports:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotFocused
```

Replace the inline ViewModel setup with a helper:

```kotlin
private fun createViewModel(): SearchMusicListViewModel {
    val playerController = mock<PlayerController>()
    val musicRepository = mock<MusicRepository>()
    whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
        .thenReturn(MutableStateFlow(emptyList()))
    return SearchMusicListViewModel(
        route = SearchMusicListRoute.localLibrary(),
        sourceLoader = SearchMusicListSourceLoader(
            playlistRepository = mock<PlaylistRepository>(),
            playerController = playerController,
            musicRepository = musicRepository,
        ),
        playerController = playerController,
    )
}
```

Update `search music list input is focused on entry` to use:

```kotlin
val viewModel = createViewModel()
```

- [ ] **Step 4: Add UI regression test for reused ViewModel**

Add this test to `SearchMusicListScreenFocusTest`:

```kotlin
@Test
fun `search music list input does not refocus when screen re-enters with same view model`() {
    val viewModel = createViewModel()
    var showScreen by mutableStateOf(true)

    composeRule.setContent {
        MusicFreeTheme {
            if (showScreen) {
                SearchMusicListScreen(
                    onBack = {},
                    onNavigateToPlayer = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    waitUntilFocused(FidelityAnchors.SearchMusicList.Input)
    composeRule.runOnIdle {
        showScreen = false
    }
    composeRule.waitForIdle()
    composeRule.runOnIdle {
        showScreen = true
    }
    composeRule.waitForIdle()

    composeRule.onNodeWithTag(FidelityAnchors.SearchMusicList.Input, useUnmergedTree = true)
        .assertIsNotFocused()
}
```

- [ ] **Step 5: Run tests and verify expected failures**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelTest.initial autofocus request is consumed once per view model instance" --no-daemon
```

Expected: FAIL because `consumeInitialAutofocusRequest()` does not exist.

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.searchmusiclist.SearchMusicListViewModelTest.initial autofocus request is consumed once per view model instance" --no-daemon
```

Expected: FAIL because `consumeInitialAutofocusRequest()` does not exist.

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.searchmusiclist.SearchMusicListScreenFocusTest.search music list input does not refocus when screen re-enters with same view model" --no-daemon
```

Expected before implementation: FAIL because the current Screen requests focus every time it re-enters composition.

## Task 2: Implement One-Shot Autofocus Consumption

**Files:**
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt`

- [ ] **Step 1: Add SearchViewModel gate**

In `SearchViewModel`, add this property near the page state fields:

```kotlin
private var initialAutofocusConsumed = false
```

Add this method near the other simple UI action methods:

```kotlin
fun consumeInitialAutofocusRequest(): Boolean {
    if (initialAutofocusConsumed) return false
    initialAutofocusConsumed = true
    return true
}
```

- [ ] **Step 2: Gate SearchScreen focus request**

Replace the autofocus effect in `SearchScreen.kt`:

```kotlin
LaunchedEffect(Unit) {
    withFrameNanos { }
    searchFocusRequester.requestFocus()
    keyboardController?.show()
}
```

with:

```kotlin
LaunchedEffect(Unit) {
    if (!viewModel.consumeInitialAutofocusRequest()) return@LaunchedEffect
    withFrameNanos { }
    searchFocusRequester.requestFocus()
    keyboardController?.show()
}
```

- [ ] **Step 3: Add SearchMusicListViewModel gate**

In `SearchMusicListViewModel`, add:

```kotlin
private var initialAutofocusConsumed = false

fun consumeInitialAutofocusRequest(): Boolean {
    if (initialAutofocusConsumed) return false
    initialAutofocusConsumed = true
    return true
}
```

Place it after `uiState` and before `updateQuery`.

- [ ] **Step 4: Gate SearchMusicListScreen focus request**

Replace the autofocus effect in `SearchMusicListScreen.kt`:

```kotlin
LaunchedEffect(Unit) {
    withFrameNanos { }
    searchFocusRequester.requestFocus()
    keyboardController?.show()
}
```

with:

```kotlin
LaunchedEffect(Unit) {
    if (!viewModel.consumeInitialAutofocusRequest()) return@LaunchedEffect
    withFrameNanos { }
    searchFocusRequester.requestFocus()
    keyboardController?.show()
}
```

- [ ] **Step 5: Run targeted tests**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelTest" --no-daemon
```

Expected: PASS.

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.searchmusiclist.SearchMusicListViewModelTest" --tests "com.hank.musicfree.feature.home.searchmusiclist.SearchMusicListScreenFocusTest" --no-daemon
```

Expected: PASS.

## Task 3: Final Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run dev harness grep gate**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: no violations.

- [ ] **Step 3: Review diff**

Run:

```bash
git diff -- docs/DOCS_STATUS.md docs/superpowers/specs/2026-05-11-search-autofocus-once-design.md docs/superpowers/plans/2026-05-11-search-autofocus-once.md feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListViewModel.kt feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListViewModelTest.kt feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt
```

Expected: only the spec, plan, DOCS_STATUS registration, focus gate, and related tests are changed.
