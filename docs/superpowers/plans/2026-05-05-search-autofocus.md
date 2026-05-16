# Search Autofocus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align Android search input behavior with RN by focusing and showing the keyboard when entering the main search page or the search-music-list page.

**Architecture:** Keep autofocus local to the two affected Compose screens. Add stable fidelity anchors for the input fields, then attach `FocusRequester` and `LocalSoftwareKeyboardController` in each screen with a one-shot `LaunchedEffect(Unit)` that runs after the first frame.

**Tech Stack:** Kotlin, Jetpack Compose `BasicTextField`, Compose focus APIs, Compose UI tests, Hilt-backed Android instrumentation, Robolectric.

---

## File Structure

- Modify `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
  - Add stable input anchors for main search and search-music-list tests.
- Modify `app/src/androidTest/java/com/hank/musicfree/HomeEntryNavigationTest.kt`
  - Extend the existing home search entry test to assert the main search input is focused.
- Modify `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
  - Add main search input autofocus and test tag.
- Create `feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt`
  - Add a focused Compose test for the search-music-list input.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt`
  - Add search-music-list input autofocus and test tag.

## Task 1: Main Search Input Anchor And Failing Instrumentation Test

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
- Modify: `app/src/androidTest/java/com/hank/musicfree/HomeEntryNavigationTest.kt`

- [ ] **Step 1: Add search input anchors**

In `FidelityAnchors.kt`, add these objects after `object Home` and before `object Player`:

```kotlin
    object Search {
        const val Input = "search.input"
    }

    object SearchMusicList {
        const val Input = "searchMusicList.input"
    }
```

- [ ] **Step 2: Extend the existing home search navigation test**

In `HomeEntryNavigationTest.kt`, add imports:

```kotlin
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
```

Replace `searchEntry_opensSearchRoot` with:

```kotlin
    @Test
    fun searchEntry_opensSearchRootAndFocusesInput() {
        waitForHomeEntry(FidelityAnchors.Home.NavBarSearch)
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarSearch).performClick()
        assertTagExists(FidelityAnchors.Screen.SearchRoot)
        waitUntilFocused(FidelityAnchors.Search.Input)
        composeRule.onNodeWithTag(FidelityAnchors.Search.Input, useUnmergedTree = true)
            .assertIsFocused()
    }
```

Add this helper near the existing private helpers:

```kotlin
    private fun waitUntilFocused(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.getOrNull(SemanticsProperties.Focused) == true
                }
        }
    }
```

- [ ] **Step 3: Run the targeted instrumentation test and verify it fails**

Run with an attached emulator/device:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.HomeEntryNavigationTest
```

Expected: failure in `searchEntry_opensSearchRootAndFocusesInput`, because `FidelityAnchors.Search.Input` has not been applied to `SearchScreen` yet.

- [ ] **Step 4: Commit the failing test**

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt app/src/androidTest/java/com/hank/musicfree/HomeEntryNavigationTest.kt
git commit -m "test(search): assert search entry focuses input"
```

## Task 2: Main Search Screen Autofocus

**Files:**
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`

- [ ] **Step 1: Add focus imports**

Add these imports:

```kotlin
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
```

- [ ] **Step 2: Create the one-shot focus request**

After `val context = LocalContext.current`, add:

```kotlin
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
```

After the existing query initialization `LaunchedEffect(Unit)`, add:

```kotlin
    LaunchedEffect(Unit) {
        withFrameNanos { }
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }
```

- [ ] **Step 3: Attach the focus requester and test tag**

Update the `BasicTextField` modifier:

```kotlin
                    modifier = Modifier
                        .weight(1f)
                        .height(rpx(64))
                        .focusRequester(searchFocusRequester)
                        .testTag(FidelityAnchors.Search.Input)
                        .background(colors.pageBackground, RoundedCornerShape(rpx(64))),
```

- [ ] **Step 4: Run the targeted instrumentation test and verify it passes**

Run with an attached emulator/device:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.HomeEntryNavigationTest
```

Expected: `searchEntry_opensSearchRootAndFocusesInput` passes.

- [ ] **Step 5: Commit the implementation**

```bash
git add feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt
git commit -m "fix(search): focus main search input on entry"
```

## Task 3: Search Music List Failing Focus Test

**Files:**
- Create: `feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt`

- [ ] **Step 1: Add the failing Robolectric Compose test**

Create `SearchMusicListScreenFocusTest.kt`:

```kotlin
package com.hank.musicfree.feature.home.searchmusiclist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.hank.musicfree.core.navigation.SearchMusicListRoute
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.player.controller.PlayerController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SearchMusicListScreenFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search music list input is focused on entry`() {
        val playerController = mock<PlayerController>()
        val viewModel = SearchMusicListViewModel(
            route = SearchMusicListRoute.localLibrary(),
            sourceLoader = SearchMusicListSourceLoader(
                playlistRepository = mock<PlaylistRepository>(),
                playerController = playerController,
            ),
            playerController = playerController,
        )

        composeRule.setContent {
            MusicFreeTheme {
                SearchMusicListScreen(
                    onBack = {},
                    onNavigateToPlayer = {},
                    viewModel = viewModel,
                )
            }
        }

        waitUntilFocused(FidelityAnchors.SearchMusicList.Input)
        composeRule.onNodeWithTag(FidelityAnchors.SearchMusicList.Input, useUnmergedTree = true)
            .assertIsFocused()
    }

    private fun waitUntilFocused(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.getOrNull(SemanticsProperties.Focused) == true
                }
        }
    }
}
```

- [ ] **Step 2: Run the targeted unit test and verify it fails**

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.searchmusiclist.SearchMusicListScreenFocusTest"
```

Expected: failure because `FidelityAnchors.SearchMusicList.Input` has not been applied to `SearchMusicListScreen` yet.

- [ ] **Step 3: Commit the failing test**

```bash
git add feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreenFocusTest.kt
git commit -m "test(home): assert search music list input focus"
```

## Task 4: Search Music List Autofocus

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt`

- [ ] **Step 1: Add focus imports**

Add these imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import com.hank.musicfree.core.ui.FidelityAnchors
```

- [ ] **Step 2: Create the one-shot focus request**

After `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`, add:

```kotlin
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        withFrameNanos { }
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }
```

- [ ] **Step 3: Attach the focus requester and test tag**

Update the `BasicTextField` modifier:

```kotlin
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .testTag(FidelityAnchors.SearchMusicList.Input),
```

- [ ] **Step 4: Run the targeted unit test and verify it passes**

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.hank.musicfree.feature.home.searchmusiclist.SearchMusicListScreenFocusTest"
```

Expected: the new focus test passes.

- [ ] **Step 5: Commit the implementation**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListScreen.kt
git commit -m "fix(home): focus search music list input on entry"
```

## Task 5: Final Verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run relevant instrumentation test**

Run with an attached emulator/device:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.HomeEntryNavigationTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Runtime device verification**

Use an emulator or attached device:

```bash
./gradlew :app:installDebug
```

Manual checks:

1. Launch the app.
2. Tap the home search bar.
3. Confirm the main search input is focused and the software keyboard appears.
4. Open a playlist or history route that exposes the search-music-list entry.
5. Enter search-music-list.
6. Confirm the input is focused and the software keyboard appears.

- [ ] **Step 4: Check git status**

```bash
git status --short
```

Expected: no uncommitted changes.
