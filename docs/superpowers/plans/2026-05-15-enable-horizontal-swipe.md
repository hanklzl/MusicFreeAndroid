# Enable Horizontal Swipe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable left / right swipe gestures for search result tabs, recommend sheet plugin tabs, and top list plugin tabs without changing existing loading or navigation behavior.

**Architecture:** Add one shared Compose modifier in `:core` that converts a horizontal drag above threshold into a tab index change. Feature screens attach that modifier only to content regions below their TabRows, preserving TabRow scrolling, tag scrolling, and vertical list scrolling.

**Tech Stack:** Kotlin, Jetpack Compose Foundation pointer input, Material3 TabRows, JVM unit tests, Gradle Debug build.

---

## File Structure

- Create `core/src/main/java/com/hank/musicfree/core/ui/HorizontalTabSwipe.kt`: shared `Modifier.horizontalSwipeNavigation(...)`, `Modifier.horizontalTabSwipe(...)`, direction enum, and pure target resolver.
- Create `core/src/test/java/com/hank/musicfree/core/ui/HorizontalTabSwipeTest.kt`: threshold, direction, and boundary tests.
- Modify `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`: attach nested-aware media-type/plugin swipe to result content.
- Modify `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`: guard duplicate same-page `loadMore()` requests.
- Modify `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`: cover duplicate `loadMore()` suppression.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`: attach plugin swipe modifier below `PluginCapabilityTabs`, not on tag `LazyRow`.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`: attach plugin swipe modifier below `PluginCapabilityTabs`.
- Modify `docs/DOCS_STATUS.md`: register the new current spec.

## Task 1: Shared Swipe Helper

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/ui/HorizontalTabSwipe.kt`
- Create: `core/src/test/java/com/hank/musicfree/core/ui/HorizontalTabSwipeTest.kt`

- [ ] **Step 1: Write the target resolver test**

```kotlin
@Test
fun `left swipe past threshold moves to next tab`() {
    assertEquals(
        2,
        resolveHorizontalSwipeTarget(
            selectedIndex = 1,
            pageCount = 4,
            dragDistancePx = -80f,
            thresholdPx = 48f,
        ),
    )
}
```

- [ ] **Step 2: Add complete resolver coverage**

Add tests for right swipe, insufficient drag, first-tab right boundary, last-tab left boundary, single-page disabled case, and out-of-range index.

- [ ] **Step 3: Implement the helper**

```kotlin
package com.hank.musicfree.core.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

enum class HorizontalSwipeDirection {
    Previous,
    Next,
}

fun Modifier.horizontalSwipeNavigation(
    enabled: Boolean = true,
    onSwipe: (HorizontalSwipeDirection) -> Unit,
): Modifier = composed {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    if (!enabled) {
        Modifier
    } else {
        Modifier.pointerInput(enabled) {
            var dragDistance = 0f
            val threshold = size.width * HorizontalTabSwipeThresholdFraction
            detectHorizontalDragGestures(
                onDragStart = { dragDistance = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    dragDistance += dragAmount
                    change.consume()
                },
                onDragCancel = { dragDistance = 0f },
                onDragEnd = {
                    resolveHorizontalSwipeDirection(dragDistance, threshold)?.let(currentOnSwipe)
                    dragDistance = 0f
                },
            )
        }
    }
}

fun Modifier.horizontalTabSwipe(
    selectedIndex: Int,
    pageCount: Int,
    enabled: Boolean = true,
    onSelectIndex: (Int) -> Unit,
): Modifier {
    if (!enabled || pageCount <= 1 || selectedIndex !in 0 until pageCount) return this
    return horizontalSwipeNavigation(enabled = true) { direction ->
        val target = when (direction) {
            HorizontalSwipeDirection.Previous -> (selectedIndex - 1).coerceAtLeast(0)
            HorizontalSwipeDirection.Next -> (selectedIndex + 1).coerceAtMost(pageCount - 1)
        }
        if (target != selectedIndex) onSelectIndex(target)
    }
}

internal fun resolveHorizontalSwipeTarget(
    selectedIndex: Int,
    pageCount: Int,
    dragDistancePx: Float,
    thresholdPx: Float,
): Int {
    if (pageCount <= 1 || selectedIndex !in 0 until pageCount) return selectedIndex
    return when (resolveHorizontalSwipeDirection(dragDistancePx, thresholdPx)) {
        HorizontalSwipeDirection.Previous -> (selectedIndex - 1).coerceAtLeast(0)
        HorizontalSwipeDirection.Next -> (selectedIndex + 1).coerceAtMost(pageCount - 1)
        null -> selectedIndex
    }
}

internal fun resolveHorizontalSwipeDirection(
    dragDistancePx: Float,
    thresholdPx: Float,
): HorizontalSwipeDirection? =
    when {
        dragDistancePx > thresholdPx -> HorizontalSwipeDirection.Previous
        dragDistancePx < -thresholdPx -> HorizontalSwipeDirection.Next
        else -> null
    }
```

- [ ] **Step 4: Run targeted core test**

Run: `./gradlew :core:testDebugUnitTest --tests "com.hank.musicfree.core.ui.HorizontalTabSwipeTest" --no-daemon`

Expected: PASS.

## Task 2: Search Result Gestures

**Files:**
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`

- [ ] **Step 1: Import the helper**

Add:

```kotlin
import com.hank.musicfree.core.ui.HorizontalSwipeDirection
import com.hank.musicfree.core.ui.horizontalSwipeNavigation
```

- [ ] **Step 2: Compute selected indexes in `SearchResultPanel`**

Inside `SearchResultPanel`, derive:

```kotlin
val mediaTypes = SearchMediaType.entries
val selectedMediaIndex = mediaTypes.indexOf(selectedMediaType).coerceAtLeast(0)
val selectedPluginIndex = searchablePlugins.indexOfFirst { it.platform == selectedPlatform }
```

- [ ] **Step 3: Attach one nested-aware swipe handler to the result body**

Wrap the result content area below TabRows in a `Column` with one `horizontalSwipeNavigation(...)`. Do not chain two pointer-input modifiers on the same node.

```kotlin
Modifier
    .fillMaxSize()
    .horizontalSwipeNavigation(
        enabled = mediaTypes.size > 1 || searchablePlugins.size > 1,
        onSwipe = { direction ->
            val pluginTarget = targetIndexForDirection(
                selectedIndex = selectedPluginIndex,
                pageCount = searchablePlugins.size,
                direction = direction,
            )
            if (pluginTarget != selectedPluginIndex && pluginTarget in searchablePlugins.indices) {
                onSelectPlatform(searchablePlugins[pluginTarget].platform)
                return@horizontalSwipeNavigation
            }

            val mediaTarget = targetIndexForDirection(
                selectedIndex = selectedMediaIndex,
                pageCount = mediaTypes.size,
                direction = direction,
            )
            if (mediaTarget != selectedMediaIndex && mediaTarget in mediaTypes.indices) {
                onSelectMediaType(mediaTypes[mediaTarget])
            }
        },
    )
```

- [ ] **Step 4: Add a local direction helper in `SearchScreen`**

Add near `SearchResultPanel`:

```kotlin
private fun targetIndexForDirection(
    selectedIndex: Int,
    pageCount: Int,
    direction: HorizontalSwipeDirection,
): Int {
    if (pageCount <= 1 || selectedIndex !in 0 until pageCount) return selectedIndex
    return when (direction) {
        HorizontalSwipeDirection.Previous -> (selectedIndex - 1).coerceAtLeast(0)
        HorizontalSwipeDirection.Next -> (selectedIndex + 1).coerceAtMost(pageCount - 1)
    }
}
```

This mirrors nested RN TabView behavior: plugin pages move first; at plugin boundaries the same swipe can move media type.

- [ ] **Step 5: Compile search module**

Run: `./gradlew :feature:search:compileDebugKotlin --no-daemon`

Expected: PASS.

## Task 3: Recommend Sheets Gestures

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`

- [ ] **Step 1: Import the helper**

Add:

```kotlin
import com.hank.musicfree.core.ui.horizontalTabSwipe
```

- [ ] **Step 2: Derive selected plugin index**

After collecting `plugins` and `selectedPlugin`, add:

```kotlin
val selectedPluginIndex = plugins.indexOfFirst { it.platform == selectedPlugin }
```

- [ ] **Step 3: Attach swipe below the plugin TabRow**

The `when` block that renders loading / error / empty / grid should live inside a `Box` or `Column` with:

```kotlin
Modifier
    .fillMaxSize()
    .horizontalTabSwipe(
        selectedIndex = selectedPluginIndex,
        pageCount = plugins.size,
        enabled = selectedPluginIndex >= 0,
        onSelectIndex = { index -> viewModel.selectPlugin(plugins[index].platform) },
    )
```

Do not attach this modifier to the tag `LazyRow`.

- [ ] **Step 4: Compile home module**

Run: `./gradlew :feature:home:compileDebugKotlin --no-daemon`

Expected: PASS.

## Task 4: Top List Gestures

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`

- [ ] **Step 1: Import the helper**

Add:

```kotlin
import com.hank.musicfree.core.ui.horizontalTabSwipe
```

- [ ] **Step 2: Derive selected plugin index**

After collecting `plugins` and `selectedPlugin`, add:

```kotlin
val selectedPluginIndex = plugins.indexOfFirst { it.platform == selectedPlugin }
```

- [ ] **Step 3: Attach swipe below the plugin TabRow**

Wrap the `when (val state = uiState)` content in:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .horizontalTabSwipe(
            selectedIndex = selectedPluginIndex,
            pageCount = plugins.size,
            enabled = selectedPluginIndex >= 0,
            onSelectIndex = { index -> viewModel.selectPlugin(plugins[index].platform) },
        ),
) {
    // existing state rendering
}
```

- [ ] **Step 4: Compile home module again**

Run: `./gradlew :feature:home:compileDebugKotlin --no-daemon`

Expected: PASS.

## Task 5: Search Load-More Guard

**Files:**
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- Modify: `feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt`

- [ ] **Step 1: Add the in-flight key**

Add a private data class and set:

```kotlin
private data class LoadMoreRequest(
    val generation: Long,
    val query: String,
    val mediaType: SearchMediaType,
    val platform: String,
    val page: Int,
)

private val loadMoreInFlight = mutableSetOf<LoadMoreRequest>()
```

- [ ] **Step 2: Guard `loadMore()`**

Before launching the paging coroutine:

```kotlin
val generation = searchGeneration
val request = LoadMoreRequest(
    generation = generation,
    query = query,
    mediaType = mediaType,
    platform = platform,
    page = nextPage,
)
if (!loadMoreInFlight.add(request)) return
```

Before applying a response, verify the search has not changed:

```kotlin
val latest = _searchResults.value[mediaType]?.get(platform)
if (
    generation != searchGeneration ||
    query != _currentQuery.value ||
    latest !is PluginSearchState.Success ||
    latest.page != current.page
) {
    return@launch
}
```

In the coroutine, remove the key in `finally`:

```kotlin
} finally {
    loadMoreInFlight.remove(request)
}
```

- [ ] **Step 3: Test duplicate suppression**

Add two `SearchViewModelTest` cases:

```kotlin
@Test
fun `load more ignores duplicate request while same page is in flight`() = runTest { /* blocks page 2, calls loadMore twice, verifies one plugin call */ }

@Test
fun `old query load more does not block or overwrite new query load more`() = runTest { /* old page 2 blocked, new query page 2 succeeds, old response ignored */ }
```

- [ ] **Step 4: Run the search ViewModel test**

Run: `./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelTest" --no-daemon`

Expected: PASS.

## Task 6: Docs, Harness, and Merge Readiness

**Files:**
- Modify: `docs/DOCS_STATUS.md`

- [ ] **Step 1: Add the spec row**

Add a `当前规范` row for `docs/superpowers/specs/2026-05-15-enable-horizontal-swipe-design.md`.

- [ ] **Step 2: Run final local gates**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests "com.hank.musicfree.core.ui.HorizontalTabSwipeTest" --no-daemon
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelTest" --no-daemon
./gradlew :feature:search:compileDebugKotlin --no-daemon
./gradlew :feature:home:compileDebugKotlin --no-daemon
python3 scripts/dev-harness/grep-check.py
bash scripts/dev-harness/check.sh
git diff --check
./gradlew :app:assembleDebug --no-daemon
```

Expected: all commands pass.

- [ ] **Step 3: Branch commit**

Run:

```bash
git add docs/DOCS_STATUS.md docs/superpowers/specs/2026-05-15-enable-horizontal-swipe-design.md docs/superpowers/plans/2026-05-15-enable-horizontal-swipe.md core/src/main/java/com/hank/musicfree/core/ui/HorizontalTabSwipe.kt core/src/test/java/com/hank/musicfree/core/ui/HorizontalTabSwipeTest.kt feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt feature/search/src/test/java/com/hank/musicfree/feature/search/SearchViewModelTest.kt feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt
git commit -m "feat(ui): 开启横向 tab 手势"
```

- [ ] **Step 4: Squash merge to `main`**

From the main checkout:

```bash
git merge --squash feat/enable-horizontal-swipe
git commit -m "feat(ui): 开启横向 tab 手势"
```

Then rerun at least `git diff --check`, `python3 scripts/dev-harness/grep-check.py`, and `./gradlew :app:assembleDebug --no-daemon` on `main`.
