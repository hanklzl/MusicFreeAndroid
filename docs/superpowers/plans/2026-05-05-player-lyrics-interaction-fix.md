# Player Lyrics Interaction Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix playback lyric page flicker, centered auto-follow, smooth highlighting, tap-to-cover, current-progress entry positioning, and RN-aligned manual seek overlay.

**Architecture:** Keep lyric loading in `PlayerLyricLoader`, presentation combination in `PlayerViewModel`, and gesture/scroll state in `PlayerLyricsContent`. Add small pure interaction helpers so scroll gates, click gates, and overlay eligibility are unit-testable without Compose internals.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Kotlin Flow, Coroutines test, Robolectric Compose tests, Gradle.

---

## File Structure

- Create `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteraction.kt`
  - Pure helpers and test tags for lyric scroll gates, center-line targeting, no-lyric/search nodes, and overlay eligibility.
- Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteractionTest.kt`
  - JVM tests for the pure helpers.
- Modify `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt`
  - Use interaction helpers, split no-lyric UI, add centered programmatic scroll, distinguish user scroll from programmatic scroll, update overlay layout, add highlight animation.
- Modify `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
  - Pass `state.isPlaying` into `PlayerLyricsContent`.
- Modify `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
  - Stabilize presentation state so same-track transient loading does not erase displayed lyrics.
- Modify `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContentTest.kt`
  - Compose tests for split no-lyric UI, tap behavior, manual overlay, and no overlay during programmatic scroll.
- Modify `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoaderTest.kt`
  - Add loader regression coverage for no-lyric finality and ready-state stability.

## Task 1: Pure Lyric Interaction Helpers

**Files:**
- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteraction.kt`
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteractionTest.kt`

- [ ] **Step 1: Write failing helper tests**

Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteractionTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLyricsInteractionTest {

    private val lines = listOf(
        ParsedLyricLine(index = 0, timeMs = 1_000L, text = "A"),
        ParsedLyricLine(index = 1, timeMs = 2_000L, text = "B"),
        ParsedLyricLine(index = 2, timeMs = 4_000L, text = "C"),
    )

    @Test
    fun initialScrollFallsBackToFirstLineBeforeFirstTimestamp() {
        assertEquals(0, initialLyricScrollIndex(currentLineIndex = null, lineCount = 3))
    }

    @Test
    fun initialScrollUsesCurrentLineWhenPresent() {
        assertEquals(2, initialLyricScrollIndex(currentLineIndex = 2, lineCount = 3))
    }

    @Test
    fun initialScrollReturnsNullForEmptyLyrics() {
        assertNull(initialLyricScrollIndex(currentLineIndex = null, lineCount = 0))
    }

    @Test
    fun autoFollowRequiresPlaybackAndNoUserInteraction() {
        assertTrue(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = false,
                isUserScrolling = false,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = false,
                isProgrammaticScroll = false,
                isUserScrolling = false,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = true,
                isUserScrolling = false,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = false,
                isUserScrolling = true,
                seekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isPlaying = true,
                isProgrammaticScroll = false,
                isUserScrolling = false,
                seekOverlayVisible = true,
            ),
        )
    }

    @Test
    fun centerVisibleLineChoosesItemClosestToViewportCenter() {
        val visible = listOf(
            VisibleLyricListItem(index = 0, offset = 0, size = 80),
            VisibleLyricListItem(index = 1, offset = 90, size = 80),
            VisibleLyricListItem(index = 2, offset = 180, size = 80),
        )

        assertEquals(
            lines[1],
            centerVisibleLyricLine(
                lines = lines,
                visibleItems = visible,
                viewportStartOffset = 0,
                viewportHeight = 260,
            ),
        )
    }

    @Test
    fun seekOverlayOnlyShowsForManualTimedLyricsWithTargetLine() {
        assertTrue(
            shouldShowSeekOverlay(
                isUserScrolling = true,
                isTimedDocument = true,
                targetLine = lines[0],
            ),
        )
        assertFalse(
            shouldShowSeekOverlay(
                isUserScrolling = false,
                isTimedDocument = true,
                targetLine = lines[0],
            ),
        )
        assertFalse(
            shouldShowSeekOverlay(
                isUserScrolling = true,
                isTimedDocument = false,
                targetLine = lines[0],
            ),
        )
        assertFalse(
            shouldShowSeekOverlay(
                isUserScrolling = true,
                isTimedDocument = true,
                targetLine = null,
            ),
        )
    }

    @Test
    fun overlayBlocksBlankTapToCover() {
        assertFalse(
            shouldHandleLyricBackTap(
                tapY = 8f,
                visibleItemBounds = emptyList(),
                dragSeekOverlayVisible = true,
            ),
        )
    }

    @Test
    fun blankTapOnlyTriggersOutsideVisibleLyricBounds() {
        val bounds = listOf(
            VisibleLyricItemBounds(top = 100, bottom = 180),
            VisibleLyricItemBounds(top = 220, bottom = 300),
        )

        assertFalse(shouldHandleLyricBackTap(120f, bounds, dragSeekOverlayVisible = false))
        assertTrue(shouldHandleLyricBackTap(40f, bounds, dragSeekOverlayVisible = false))
        assertTrue(shouldHandleLyricBackTap(340f, bounds, dragSeekOverlayVisible = false))
    }

    @Test
    fun centerScrollOffsetPlacesItemNearViewportCenter() {
        assertEquals(-90, centeredItemScrollOffset(viewportHeight = 260, itemHeight = 80))
        assertEquals(0, centeredItemScrollOffset(viewportHeight = 0, itemHeight = 80))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricsInteractionTest'
```

Expected: FAIL with unresolved references such as `initialLyricScrollIndex`, `VisibleLyricListItem`, and `centerVisibleLyricLine`.

- [ ] **Step 3: Add helper implementation**

Create `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteraction.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import kotlin.math.abs

internal const val PlayerLyricsContentTestTag = "player.lyrics.content"
internal const val PlayerLyricsNoLyricTextTestTag = "player.lyrics.noLyric"
internal const val PlayerLyricsSearchTextTestTag = "player.lyrics.search"
internal const val PlayerLyricsSeekOverlayTestTag = "player.lyrics.seekOverlay"
internal const val PlayerLyricsSeekButtonTestTag = "player.lyrics.seekButton"

internal data class VisibleLyricItemBounds(
    val top: Int,
    val bottom: Int,
)

internal data class VisibleLyricListItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

internal fun initialLyricScrollIndex(
    currentLineIndex: Int?,
    lineCount: Int,
): Int? {
    if (lineCount <= 0) return null
    return currentLineIndex
        ?.takeIf { it in 0 until lineCount }
        ?: 0
}

internal fun shouldAutoFollowLyricLine(
    isPlaying: Boolean,
    isProgrammaticScroll: Boolean,
    isUserScrolling: Boolean,
    seekOverlayVisible: Boolean,
): Boolean =
    isPlaying &&
        !isProgrammaticScroll &&
        !isUserScrolling &&
        !seekOverlayVisible

internal fun centerVisibleLyricLine(
    lines: List<ParsedLyricLine>,
    visibleItems: List<VisibleLyricListItem>,
    viewportStartOffset: Int,
    viewportHeight: Int,
): ParsedLyricLine? {
    if (lines.isEmpty() || visibleItems.isEmpty() || viewportHeight <= 0) return null
    val centerY = viewportStartOffset + viewportHeight / 2f
    val centerItemIndex = visibleItems.minByOrNull { item ->
        val itemCenterY = item.offset + item.size / 2f
        abs(itemCenterY - centerY)
    }?.index ?: return null
    return lines.getOrNull(centerItemIndex)
}

internal fun shouldShowSeekOverlay(
    isUserScrolling: Boolean,
    isTimedDocument: Boolean,
    targetLine: ParsedLyricLine?,
): Boolean = isUserScrolling && isTimedDocument && targetLine != null

internal fun shouldHandleLyricBackTap(
    tapY: Float,
    visibleItemBounds: List<VisibleLyricItemBounds>,
    dragSeekOverlayVisible: Boolean,
): Boolean {
    if (dragSeekOverlayVisible) return false
    return visibleItemBounds.none { bounds ->
        tapY >= bounds.top && tapY <= bounds.bottom
    }
}

internal fun centeredItemScrollOffset(
    viewportHeight: Int,
    itemHeight: Int,
): Int {
    if (viewportHeight <= 0 || itemHeight <= 0) return 0
    return -((viewportHeight - itemHeight) / 2)
}
```

- [ ] **Step 4: Remove duplicate helper declarations from `PlayerLyricsContent.kt`**

Delete the existing definitions at the bottom of `PlayerLyricsContent.kt`:

```kotlin
internal const val PlayerLyricsContentTestTag = "player.lyrics.content"

internal data class VisibleLyricItemBounds(
    val top: Int,
    val bottom: Int,
)

internal fun shouldAutoFollowLyricLine(
    isScrollInProgress: Boolean,
    dragSeekOverlayVisible: Boolean,
): Boolean = !isScrollInProgress && !dragSeekOverlayVisible

internal fun shouldHandleLyricBackTap(
    tapY: Float,
    visibleItemBounds: List<VisibleLyricItemBounds>,
    dragSeekOverlayVisible: Boolean,
): Boolean {
    if (dragSeekOverlayVisible) return false
    return visibleItemBounds.none { bounds ->
        tapY >= bounds.top && tapY <= bounds.bottom
    }
}
```

- [ ] **Step 5: Run helper tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricsInteractionTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteraction.kt \
  feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsInteractionTest.kt \
  feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt
git commit -m "test(player): add lyric interaction helpers"
```

## Task 2: Stabilize Lyric Presentation State

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoaderTest.kt`

- [ ] **Step 1: Add loader regression tests**

Append these tests inside `PlayerLyricLoaderTest` before the closing brace:

```kotlin
    @Test
    fun autoSearchInProgressDoesNotEmitNoLyricBeforeFinalFailure() = runTest {
        val loader = loader()
        val music = music("no-early-empty", "demo")
        val currentPlugin = plugin(platform = "demo", lyric = null)
        val lyricPlugin = plugin(
            platform = "lyric",
            search = SearchResult(
                isEnd = true,
                data = listOf(music("candidate", "lyric", title = "Song", artist = "Artist")),
            ),
            lyric = lyricResult("Found Later"),
        )

        whenever(pluginManager.getPlugin("demo")).thenReturn(currentPlugin)
        whenever(pluginManager.getPlugin("lyric")).thenReturn(lyricPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))
        lyricPlugins.value = listOf(lyricPlugin)

        val states = loader.observeLyrics(music).toList()

        assertEquals(LyricLoadState.Loading(music), states.first())
        assertTrue(states.last() is LyricLoadState.Ready)
        assertFalse(states.dropLast(1).any { it is LyricLoadState.NoLyric })
    }

    @Test
    fun cacheReemissionAfterReadyDoesNotEmitNoLyricBetweenReadyStates() = runTest {
        val loader = loader()
        val music = music("cache-reemit-ready", "demo")
        val cacheFlow = MutableStateFlow<LyricCache?>(null)
        val demoPlugin = plugin(platform = "demo", lyric = lyricResult("Remote Lyric"))

        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(cacheFlow)
        whenever(lyricRepository.saveRemoteLyric(eq(music), any(), any())).thenAnswer {
            cacheFlow.value = lyricCache(
                music = music,
                remotePayload = RawLyricPayload(rawLrc = "[00:01.00]Remote Lyric"),
                remoteSourcePlatform = "demo",
                remoteSourceMusicId = music.id,
            )
            Unit
        }

        val states = mutableListOf<LyricLoadState>()
        val job = launch {
            loader.observeLyrics(music).toList(states)
        }
        advanceUntilIdle()
        job.cancel()

        assertTrue(states.filterIsInstance<LyricLoadState.Ready>().size >= 1)
        assertFalse(states.any { it is LyricLoadState.NoLyric })
    }
```

Also add this import if it is missing:

```kotlin
import org.junit.Assert.assertFalse
```

- [ ] **Step 2: Run loader tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricLoaderTest'
```

Expected: Existing implementation may pass the first test and may fail the cache re-emission test if it emits `NoLyric` between ready states. If both pass, keep the tests as regression coverage and continue to Step 4.

- [ ] **Step 3: Keep observe cache emissions stable**

If Step 2 fails, modify `PlayerLyricLoader.observeLyrics()` so it remembers the last ready state for the current song during cache re-emission and never emits `NoLyric` between two ready states for the same song.

Replace the body of `observeLyrics()` with:

```kotlin
    fun observeLyrics(music: MusicItem?): Flow<LyricLoadState> = flow {
        if (music == null) {
            emit(LyricLoadState.NoTrack)
            return@flow
        }

        var firstEmission = true
        var lastReady: LyricLoadState.Ready? = null
        lyricRepository.observeCache(music).collect { cache ->
            if (firstEmission) {
                emit(LyricLoadState.Loading(music))
            }

            val resolved = resolveLyrics(music, cache)
            if (resolved is LyricLoadState.Ready) {
                lastReady = resolved
                emit(resolved)
            } else if (
                resolved is LyricLoadState.NoLyric &&
                lastReady?.music?.sameMusicKey(music) == true &&
                firstEmission.not()
            ) {
                emit(lastReady!!)
            } else {
                emit(resolved)
            }
            firstEmission = false
        }
    }.catch { e ->
        if (e is CancellationException || (e is IllegalStateException && e.message?.contains("Flow exception transparency is violated") == true)) {
            throw e
        }
        val fallbackMusic = music ?: return@catch
        emit(LyricLoadState.Error(fallbackMusic, e.message ?: "歌词加载失败"))
    }
```

Add this helper near the bottom of `PlayerLyricLoader.kt`:

```kotlin
private fun MusicItem.sameMusicKey(other: MusicItem): Boolean =
    platform == other.platform && id == other.id
```

- [ ] **Step 4: Stabilize `PlayerViewModel` presentation for same-track loading**

In `PlayerViewModel.kt`, add imports:

```kotlin
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.filterNotNull
```

Replace the `lyricLoadState` chain with a raw load state and a presentation load state:

```kotlin
    private val rawLyricLoadState: StateFlow<LyricLoadState> = playerState
        .map { it.currentItem }
        .distinctUntilChangedBy { item -> item?.let { it.platform to it.id } }
        .flatMapLatest { item ->
            playerLyricLoader.observeLyrics(item)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricLoadState.NoTrack)

    private val lyricLoadState: StateFlow<LyricLoadState> = rawLyricLoadState
        .scan<LyricLoadState, LyricLoadState?>(null) { previous, next ->
            val previousReady = previous as? LyricLoadState.Ready
            val nextLoading = next as? LyricLoadState.Loading
            if (previousReady != null && nextLoading != null && previousReady.music.sameMusicKey(nextLoading.music)) {
                previousReady
            } else {
                next
            }
        }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricLoadState.NoTrack)
```

Add this helper at the bottom of `PlayerViewModel.kt` after the class:

```kotlin
private fun MusicItem.sameMusicKey(other: MusicItem): Boolean =
    platform == other.platform && id == other.id
```

- [ ] **Step 5: Run loader tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricLoaderTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt \
  feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoader.kt \
  feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoaderTest.kt
git commit -m "fix(player): stabilize lyric loading presentation"
```

## Task 3: Split No-Lyric Status and Search Action

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContentTest.kt`

- [ ] **Step 1: Add Compose tests for no-lyric split**

In `PlayerLyricsContentTest.kt`, add imports:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
```

Add these tests inside `PlayerLyricsContentTest`:

```kotlin
    @Test
    fun noLyricStateShowsSeparateStatusAndSearchAction() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PlayerLyricsContent(
                        state = readyState().copy(
                            loadState = LyricLoadState.NoLyric(readyState().music()),
                            document = null,
                            currentLineIndex = null,
                        ),
                        durationMs = 10_000L,
                        onBackToCover = {},
                        onSeekToLine = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PlayerLyricsNoLyricTextTestTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerLyricsSearchTextTestTag).assertIsDisplayed()
        composeRule.onNodeWithText("暂无歌词\n搜索歌词").assertDoesNotExist()
    }

    @Test
    fun searchActionDoesNotTriggerBackToCover() {
        var backToCoverClicks = 0

        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PlayerLyricsContent(
                        state = readyState().copy(
                            loadState = LyricLoadState.NoLyric(readyState().music()),
                            document = null,
                            currentLineIndex = null,
                        ),
                        durationMs = 10_000L,
                        onBackToCover = { backToCoverClicks++ },
                        onSeekToLine = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PlayerLyricsSearchTextTestTag).performClick()

        composeRule.runOnIdle {
            assertEquals(0, backToCoverClicks)
        }
    }
```

Add this helper inside `PlayerLyricsContentTest`:

```kotlin
    private fun PlayerLyricsUiState.music(): MusicItem =
        (loadState as LyricLoadState.Ready).music
```

- [ ] **Step 2: Run content tests to verify they fail**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricsContentTest'
```

Expected: FAIL because the new no-lyric test tags do not exist and the UI still renders `"暂无歌词\n搜索歌词"` as one text node.

- [ ] **Step 3: Replace the no-lyric UI with separate nodes**

In `PlayerLyricsContent.kt`, replace:

```kotlin
            is LyricLoadState.NoLyric -> {
                CenterText("暂无歌词\n搜索歌词")
            }
```

with:

```kotlin
            is LyricLoadState.NoLyric -> {
                NoLyricContent()
            }
```

Add this composable below `CenterText`:

```kotlin
@Composable
private fun NoLyricContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "暂无歌词",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = FontSizes.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(PlayerLyricsNoLyricTextTestTag),
        )
        Text(
            text = "搜索歌词",
            color = Color(0xFF66EEFF),
            fontSize = FontSizes.title,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = rpx(14))
                .testTag(PlayerLyricsSearchTextTestTag)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
    }
}
```

- [ ] **Step 4: Run content tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricsContentTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt \
  feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContentTest.kt
git commit -m "fix(player): split lyric empty state actions"
```

## Task 4: Centered Auto-Follow, Manual Overlay, and Smooth Highlight

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsUiState.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContentTest.kt`

- [ ] **Step 1: Add Compose tests for overlay gates and seek button**

In `PlayerLyricsContentTest.kt`, add imports:

```kotlin
import androidx.compose.ui.test.onNodeWithContentDescription
```

Add these tests inside `PlayerLyricsContentTest`:

```kotlin
    @Test
    fun seekOverlayIsHiddenBeforeManualScroll() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PlayerLyricsContent(
                        state = readyState(),
                        durationMs = 10_000L,
                        isPlaying = true,
                        onBackToCover = {},
                        onSeekToLine = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerLyricsSeekOverlayTestTag).assertDoesNotExist()
    }

    @Test
    fun seekButtonTriggersSeekAndDoesNotBackToCover() {
        var backToCoverClicks = 0
        var seekTarget = -1L

        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PlayerLyricsContent(
                        state = readyState().copy(manualSeekPreviewLine = readyState().document!!.lines[1]),
                        durationMs = 10_000L,
                        isPlaying = true,
                        onBackToCover = { backToCoverClicks++ },
                        onSeekToLine = { seekTarget = it },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("播放该行").performClick()

        composeRule.runOnIdle {
            assertEquals(0, backToCoverClicks)
            assertEquals(2_000L, seekTarget)
        }
    }
```

Update all existing `PlayerLyricsContent` calls in this test file to include `isPlaying = true`.

- [ ] **Step 2: Run content tests to verify they fail**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricsContentTest'
```

Expected: FAIL because `PlayerLyricsContent` has no `isPlaying` parameter and `PlayerLyricsUiState` has no `manualSeekPreviewLine`.

- [ ] **Step 3: Extend `PlayerLyricsUiState` for testable manual overlay preview**

Modify `PlayerLyricsUiState.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine

data class PlayerLyricsUiState(
    val loadState: LyricLoadState = LyricLoadState.NoTrack,
    val document: LyricDocument? = null,
    val currentLineIndex: Int? = null,
    val showTranslation: Boolean = false,
    val fontSizeLevel: Int = 1,
    val userOffsetMs: Long = 0L,
    val manualSeekPreviewLine: ParsedLyricLine? = null,
) {
    val hasLyrics: Boolean get() = document?.lines?.isNotEmpty() == true
    val hasTranslation: Boolean get() = document?.hasTranslation == true
}
```

- [ ] **Step 4: Update `PlayerLyricsContent` signature and call site**

In `PlayerLyricsContent.kt`, change the function signature:

```kotlin
fun PlayerLyricsContent(
    state: PlayerLyricsUiState,
    durationMs: Long,
    isPlaying: Boolean,
    onBackToCover: () -> Unit,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
)
```

In `PlayerScreen.kt`, update the call:

```kotlin
                    PlayerLyricsContent(
                        state = lyricsUiState,
                        durationMs = state.duration,
                        isPlaying = state.isPlaying,
                        onBackToCover = { contentPage = PlayerContentPage.Cover },
                        onSeekToLine = viewModel::seekToLyricLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
```

- [ ] **Step 5: Add imports needed by the rework**

In `PlayerLyricsContent.kt`, add imports:

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
```

Remove unused imports after the implementation compiles.

- [ ] **Step 6: Replace derived center-line calculation with helper-backed calculation**

In `PlayerLyricsContent.kt`, replace the current `centerVisibleLine` block with:

```kotlin
    val centerVisibleLine by remember(document) {
        derivedStateOf {
            val doc = document ?: return@derivedStateOf null
            centerVisibleLyricLine(
                lines = doc.lines,
                visibleItems = listState.layoutInfo.visibleItemsInfo.map {
                    VisibleLyricListItem(index = it.index, offset = it.offset, size = it.size)
                },
                viewportStartOffset = listState.layoutInfo.viewportStartOffset,
                viewportHeight = listState.layoutInfo.viewportSize.height,
            )
        }
    }
```

- [ ] **Step 7: Add explicit programmatic and user scroll state**

Replace:

```kotlin
    var dragSeekLine by remember(document) { mutableStateOf<ParsedLyricLine?>(null) }
    var showDragSeekOverlay by remember(document) { mutableStateOf(false) }
```

with:

```kotlin
    var dragSeekLine by remember(document) { mutableStateOf<ParsedLyricLine?>(state.manualSeekPreviewLine) }
    var showDragSeekOverlay by remember(document, state.manualSeekPreviewLine) {
        mutableStateOf(state.manualSeekPreviewLine != null)
    }
    var isUserScrollingLyrics by remember(document) { mutableStateOf(false) }
    var programmaticScrollCount by remember(document) { mutableIntStateOf(0) }
    val isProgrammaticScroll = programmaticScrollCount > 0
```

Add this connection after those state declarations:

```kotlin
    val lyricNestedScrollConnection = remember(document) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val doc = document ?: return Offset.Zero
                if (source == NestedScrollSource.UserInput && doc.isTimed && doc.lines.isNotEmpty()) {
                    isUserScrollingLyrics = true
                    dragSeekLine = centerVisibleLine ?: doc.lines.first()
                    showDragSeekOverlay = true
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isUserScrollingLyrics = false
                return Velocity.Zero
            }
        }
    }
```

- [ ] **Step 8: Update overlay visibility effect**

Replace the current `LaunchedEffect(centerVisibleLine, listState.isScrollInProgress)` with:

```kotlin
    LaunchedEffect(centerVisibleLine, isUserScrollingLyrics, document) {
        val doc = document ?: return@LaunchedEffect
        if (shouldShowSeekOverlay(isUserScrollingLyrics, doc.isTimed, centerVisibleLine)) {
            dragSeekLine = centerVisibleLine
            showDragSeekOverlay = true
        } else if (showDragSeekOverlay && state.manualSeekPreviewLine == null) {
            delay(2_000L)
            showDragSeekOverlay = false
            dragSeekLine = null
        }
    }
```

- [ ] **Step 9: Add centered scroll helper inside `PlayerLyricsContent`**

Add this local suspend function before auto-follow effects:

```kotlin
    suspend fun scrollToLyricIndex(index: Int, animated: Boolean) {
        val itemHeight = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.size
            ?: listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size
            ?: 0
        val offset = centeredItemScrollOffset(
            viewportHeight = listState.layoutInfo.viewportSize.height,
            itemHeight = itemHeight,
        )
        programmaticScrollCount += 1
        try {
            if (animated) {
                listState.animateScrollToItem(index = index, scrollOffset = offset)
            } else {
                listState.scrollToItem(index = index, scrollOffset = offset)
            }
        } finally {
            programmaticScrollCount -= 1
        }
    }
```

- [ ] **Step 10: Add initial positioning effect**

Add this effect before the auto-follow effect:

```kotlin
    LaunchedEffect(document, state.fontSizeLevel, state.userOffsetMs) {
        val doc = document ?: return@LaunchedEffect
        val targetIndex = initialLyricScrollIndex(currentLineIndex, doc.lines.size) ?: return@LaunchedEffect
        delay(120L)
        scrollToLyricIndex(targetIndex, animated = false)
    }
```

- [ ] **Step 11: Replace auto-follow effect**

Replace the existing auto-follow `LaunchedEffect(...)` with:

```kotlin
    LaunchedEffect(
        currentLineIndex,
        document,
        state.fontSizeLevel,
        state.userOffsetMs,
        showDragSeekOverlay,
        isPlaying,
    ) {
        val currentLineIndexFinal = currentLineIndex ?: return@LaunchedEffect
        val doc = loadState as? LyricLoadState.Ready ?: return@LaunchedEffect
        if (!doc.document.isTimed) return@LaunchedEffect
        if (currentLineIndexFinal !in doc.document.lines.indices) return@LaunchedEffect
        if (
            !shouldAutoFollowLyricLine(
                isPlaying = isPlaying,
                isProgrammaticScroll = isProgrammaticScroll,
                isUserScrolling = isUserScrollingLyrics,
                seekOverlayVisible = showDragSeekOverlay,
            )
        ) {
            return@LaunchedEffect
        }
        scrollToLyricIndex(currentLineIndexFinal, animated = true)
    }
```

- [ ] **Step 12: Attach nested scroll to the lyric list only**

Change the `LyricsList` call:

```kotlin
                    LyricsList(
                        load.document.lines,
                        listState = listState,
                        currentLineIndex = currentLineIndex,
                        showTranslation = state.showTranslation,
                        fontSizeLevel = state.fontSizeLevel,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(lyricNestedScrollConnection),
                    )
```

- [ ] **Step 13: Animate highlight color**

In `LyricsList`, replace:

```kotlin
            val color = if (isCurrentLine) {
                MusicFreeTheme.colors.primary
            } else {
                Color.White.copy(alpha = 0.65f)
            }
```

with:

```kotlin
            val targetColor = if (isCurrentLine) {
                MusicFreeTheme.colors.primary
            } else {
                Color.White.copy(alpha = 0.65f)
            }
            val color by animateColorAsState(
                targetValue = targetColor,
                label = "lyric-line-color",
            )
```

- [ ] **Step 14: Replace overlay layout with RN-aligned left time, center line, right button**

Replace `DragSeekOverlay` with:

```kotlin
@Composable
private fun DragSeekOverlay(
    line: ParsedLyricLine,
    durationMs: Long,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(92))
            .padding(horizontal = rpx(18))
            .testTag(PlayerLyricsSeekOverlayTestTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatMsToMinuteSecond(line.timeMs, durationMs),
            color = Color(0xFFDDDDDD),
            fontSize = FontSizes.description,
            modifier = Modifier
                .padding(end = rpx(14))
                .background(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(rpx(22)),
                )
                .padding(horizontal = rpx(14), vertical = rpx(8)),
        )

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.4f),
        )

        IconButton(
            onClick = { onSeekToLine(line.timeMs) },
            modifier = Modifier
                .size(rpx(64))
                .padding(start = rpx(14))
                .testTag(PlayerLyricsSeekButtonTestTag),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_play),
                contentDescription = "播放该行",
                tint = Color.White,
                modifier = Modifier.size(rpx(32)),
            )
        }
    }
}
```

- [ ] **Step 15: Hide overlay after seek**

Keep the existing call site but ensure the lambda clears both overlay fields:

```kotlin
                            onSeekToLine = {
                                onSeekToLine(it)
                                showDragSeekOverlay = false
                                dragSeekLine = null
                                isUserScrollingLyrics = false
                            },
```

- [ ] **Step 16: Remove stale imports**

Run:

```bash
./gradlew :feature:player-ui:compileDebugKotlin
```

Expected: If Kotlin reports unused or unresolved imports, remove only imports reported by the compiler or IDE. Re-run until compile passes.

- [ ] **Step 17: Run content and helper tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricsContentTest' --tests '*PlayerLyricsInteractionTest'
```

Expected: PASS.

- [ ] **Step 18: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt \
  feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsUiState.kt \
  feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt \
  feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContentTest.kt
git commit -m "fix(player): align lyric scroll and seek overlay"
```

## Task 5: Full Verification and Runtime Checklist

**Files:**
- Modify only if verification exposes a defect in files changed by Tasks 1-4.

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Run debug build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: PASS and a debug APK is produced.

- [ ] **Step 3: Check worktree status**

Run:

```bash
git status --short
```

Expected: no unstaged source changes. If Gradle generated local output outside ignored directories, do not add it.

- [ ] **Step 4: Optional device runtime verification**

Run only if a device or emulator is already available:

```bash
adb devices
```

Expected: at least one device in `device` state.

Manual runtime checklist:

1. Install or launch the debug app from `.worktrees/feat-player-lyrics-interaction-fix`.
2. Play a song with timed lyrics.
3. Wait until the song is around the middle.
4. Enter the player detail page and switch to lyrics.
5. Confirm the list positions near the current lyric instead of the first line.
6. Continue playback and confirm line changes scroll smoothly and highlight transitions smoothly.
7. Manually drag the lyric list and confirm the time/line/play overlay appears only after dragging.
8. Confirm the play icon is on the right side of the overlay.
9. Tap the overlay play icon and confirm playback seeks to that lyric and continues playing.
10. Tap blank lyric space after overlay hides and confirm it switches back to the cover.

- [ ] **Step 5: Final commit if verification required fixes**

If Step 1 or Step 2 required fixes, commit them:

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui \
  feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics
git commit -m "fix(player): finish lyric interaction verification"
```

If no fixes were needed, do not create an empty commit.

## Self-Review Notes

- Spec coverage:
  - Loading/no-lyric flicker: Task 2 and Task 3.
  - Smooth scroll and highlight: Task 4.
  - Tap lyric blank area back to cover: Task 1 helper coverage and existing Task 3 tests.
  - Entering lyrics at current progress: Task 4 initial positioning.
  - Manual-only seek overlay and right-side play button: Task 1 helper coverage, Task 3 tests, Task 4 implementation.
- Type consistency:
  - `PlayerLyricsContent` gets `isPlaying: Boolean`.
  - `PlayerLyricsUiState.manualSeekPreviewLine` is nullable and defaults to `null`.
  - Test tags live in `PlayerLyricsInteraction.kt` and are reused by tests and UI.
- Scope check:
  - Plan stays inside `:feature:player-ui` unless existing `:core` timing tests fail.
  - No desktop lyrics, horizontal visual redesign, plugin API rewrite, or cache-clearing setting is included.
