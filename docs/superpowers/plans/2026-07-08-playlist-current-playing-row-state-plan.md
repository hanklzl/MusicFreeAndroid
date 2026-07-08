# Playlist Current Playing Row State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a visible current-song state to playlist detail rows, with an animated wave while playing and the same static wave while paused.

**Architecture:** Extend the shared `MusicItemRow` component with a small row playback-state enum so the visual state can be reused beyond playlist detail later. `PlaylistDetailViewModel` derives current item plus `isPlaying` from `PlayerController.playerState`, and `PlaylistDetailScreen` maps that runtime state to each row using the existing `id + platform` media identity check.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, StateFlow, Robolectric Compose tests, Android instrumentation Compose tests.

---

## File Structure

- Modify `core/src/main/java/com/hank/musicfree/core/ui/MusicItemRow.kt`
  - Add `MusicItemRowPlaybackState`.
  - Add the row background, left indicator, title color, and playback wave.
  - Add semantics for current playing and current paused.
- Modify `core/src/androidTest/java/com/hank/musicfree/core/ui/MusicItemRowTest.kt`
  - Cover current playing and current paused component states.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailViewModel.kt`
  - Add `CurrentPlaybackItemState`.
  - Expose `currentPlaybackItemState` from `PlayerController.playerState`.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt`
  - Collect `currentPlaybackItemState`.
  - Map each row to `CurrentPlaying`, `CurrentPaused`, or `None`.
  - Keep the scroll-to-current FAB using the same media identity.
- Modify `feature/home/src/test/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreenTest.kt`
  - Cover playing row, paused row, absent current item, and existing FAB behavior.

## Preconditions

- [ ] **Step 1: Start from the main checkout and confirm `.worktrees/` is ignored**

Run:

```bash
git status --short --branch
rg -n '^\.worktrees/' .gitignore
```

Expected:

```text
## main...origin/main [ahead 3]
12:.worktrees/
```

- [ ] **Step 2: Create an isolated worktree**

Run:

```bash
git worktree add .worktrees/playlist-current-row-state -b codex/playlist-current-row-state
cd .worktrees/playlist-current-row-state
```

Expected:

```text
Preparing worktree (new branch 'codex/playlist-current-row-state')
```

The command exits `0`, and the shell is now inside `.worktrees/playlist-current-row-state`.

- [ ] **Step 3: Re-read mandatory implementation gates in the worktree**

Run:

```bash
sed -n '1,260p' docs/dev-harness/ui/rules.md
sed -n '1,260p' docs/dev-harness/test/rules.md
sed -n '1,260p' docs/dev-harness/player/rules.md
```

Expected: each file prints its current rules. Use these rules during the tasks below.

### Task 1: Add Current Row State To `MusicItemRow`

**Files:**
- Modify: `core/src/androidTest/java/com/hank/musicfree/core/ui/MusicItemRowTest.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/MusicItemRow.kt`

- [ ] **Step 1: Write failing component tests**

Add these imports to `MusicItemRowTest.kt`:

```kotlin
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onAllNodesWithTag
```

Add these tests inside `MusicItemRowTest`:

```kotlin
@Test fun currentPlayingShowsWaveAndStateDescription() {
    rule.setContent {
        MusicFreeTheme {
            MusicItemRow(
                item = item(),
                isFavorite = false,
                actions = emptySet(),
                onClick = {},
                onAction = {},
                playbackState = MusicItemRowPlaybackState.CurrentPlaying,
            )
        }
    }

    rule.onNodeWithTag("MusicItemRow_current_playing_wave").assertIsDisplayed()
    rule.onNodeWithTag("MusicItemRow_current_paused_wave").assertDoesNotExist()
    rule.onNodeWithTag("MusicItemRow_root").assert(
        SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            "当前歌曲，播放中",
        ),
    )
}

@Test fun currentPausedShowsSameWaveShapeAsStaticState() {
    rule.setContent {
        MusicFreeTheme {
            MusicItemRow(
                item = item(),
                isFavorite = false,
                actions = emptySet(),
                onClick = {},
                onAction = {},
                playbackState = MusicItemRowPlaybackState.CurrentPaused,
            )
        }
    }

    rule.onNodeWithTag("MusicItemRow_current_paused_wave").assertIsDisplayed()
    rule.onNodeWithTag("MusicItemRow_current_playing_wave").assertDoesNotExist()
    rule.onNodeWithTag("MusicItemRow_root").assert(
        SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            "当前歌曲，已暂停",
        ),
    )
}

@Test fun normalRowDoesNotExposeCurrentPlaybackWave() {
    rule.setContent {
        MusicFreeTheme {
            MusicItemRow(
                item = item(),
                isFavorite = false,
                actions = emptySet(),
                onClick = {},
                onAction = {},
            )
        }
    }

    rule.onAllNodesWithTag("MusicItemRow_current_playing_wave").assertCountEquals(0)
    rule.onAllNodesWithTag("MusicItemRow_current_paused_wave").assertCountEquals(0)
}
```

- [ ] **Step 2: Run compile to verify the tests fail**

Run:

```bash
./gradlew :core:compileDebugAndroidTestKotlin --no-daemon
```

Expected: FAIL with unresolved references for `MusicItemRowPlaybackState` and the new `playbackState` parameter.

- [ ] **Step 3: Implement the row playback state**

In `MusicItemRow.kt`, add these imports:

```kotlin
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
```

Add the enum above `MusicItemRow`:

```kotlin
enum class MusicItemRowPlaybackState {
    None,
    CurrentPlaying,
    CurrentPaused,
}
```

Update the function signature:

```kotlin
fun MusicItemRow(
    item: MusicItem,
    isFavorite: Boolean,
    actions: Set<MusicItemAction>,
    onClick: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    playbackState: MusicItemRowPlaybackState = MusicItemRowPlaybackState.None,
)
```

Replace the root `Row(...)` modifier with current-state styling:

```kotlin
val isCurrent = playbackState != MusicItemRowPlaybackState.None
val rowBackground = if (isCurrent) {
    MusicFreeTheme.colors.primary.copy(alpha = 0.10f)
} else {
    Color.Transparent
}
val stateDescriptionText = when (playbackState) {
    MusicItemRowPlaybackState.CurrentPlaying -> "当前歌曲，播放中"
    MusicItemRowPlaybackState.CurrentPaused -> "当前歌曲，已暂停"
    MusicItemRowPlaybackState.None -> null
}
val horizontalPadding = if (isCurrent) {
    PaddingValues(start = 18.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
} else {
    PaddingValues(horizontal = 16.dp, vertical = 8.dp)
}

Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
        .testTag("MusicItemRow_root")
        .fillMaxWidth()
        .then(
            if (stateDescriptionText != null) {
                Modifier.semantics { stateDescription = stateDescriptionText }
            } else {
                Modifier
            },
        )
        .background(rowBackground)
        .drawBehind {
            if (isCurrent) {
                val widthPx = 3.dp.toPx()
                val verticalInset = 10.dp.toPx()
                drawRoundRect(
                    color = MusicFreeTheme.colors.primary,
                    topLeft = Offset(0f, verticalInset),
                    size = Size(widthPx, size.height - verticalInset * 2),
                    cornerRadius = CornerRadius(widthPx, widthPx),
                )
            }
        }
        .clickable(onClick = onClick)
        .padding(horizontalPadding),
) {
```

Inside the title `Row`, insert the wave before the `Text`:

```kotlin
if (isCurrent) {
    CurrentPlaybackWave(
        isAnimating = playbackState == MusicItemRowPlaybackState.CurrentPlaying,
        modifier = Modifier.testTag(
            if (playbackState == MusicItemRowPlaybackState.CurrentPlaying) {
                "MusicItemRow_current_playing_wave"
            } else {
                "MusicItemRow_current_paused_wave"
            },
        ),
    )
}
```

Change the title text color to primary only for current rows:

```kotlin
Text(
    text = item.title,
    style = MaterialTheme.typography.bodyLarge,
    color = if (isCurrent) MusicFreeTheme.colors.primary else Color.Unspecified,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(1f, fill = false),
)
```

Add this private composable at the bottom of `MusicItemRow.kt`:

```kotlin
@Composable
private fun CurrentPlaybackWave(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "current_playback_wave")
    val animatedMiddleHeight by transition.animateFloat(
        initialValue = 14f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "current_playback_wave_middle",
    )
    val middleHeight = if (isAnimating) animatedMiddleHeight else 14f

    Row(
        modifier = modifier.size(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        WaveBar(heightDp = 8f, alpha = if (isAnimating) 1f else 0.66f)
        WaveBar(heightDp = middleHeight, alpha = if (isAnimating) 1f else 0.66f)
        WaveBar(heightDp = 10f, alpha = if (isAnimating) 1f else 0.66f)
    }
}

@Composable
private fun WaveBar(
    heightDp: Float,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(heightDp.dp)
            .background(
                color = MusicFreeTheme.colors.primary.copy(alpha = alpha),
                shape = RoundedCornerShape(2.dp),
            ),
    )
}
```

- [ ] **Step 4: Run compile and component tests**

Run:

```bash
./gradlew :core:compileDebugAndroidTestKotlin --no-daemon
```

Expected: PASS.

If an Android device or emulator is available, run:

```bash
./gradlew :core:connectedDebugAndroidTest --no-daemon
```

Expected: PASS for `MusicItemRowTest`. If no device is available, record that instrumentation execution was not run and keep `compileDebugAndroidTestKotlin` as the local compile gate.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/MusicItemRow.kt \
  core/src/androidTest/java/com/hank/musicfree/core/ui/MusicItemRowTest.kt
git commit -m "feat(playlist): 增加歌曲行当前播放状态"
```

Expected: commit succeeds.

### Task 2: Wire Current Playback State Into Playlist Detail

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreenTest.kt`

- [ ] **Step 1: Write failing playlist detail tests**

Add these imports to `PlaylistDetailScreenTest.kt`:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onAllNodesWithTag
```

Add these tests inside `PlaylistDetailScreenTest`:

```kotlin
@Test
fun `playlist detail marks current playing row when media identity matches`() {
    val songs = listOf(
        musicItem(id = "song-1", title = "First Song"),
        musicItem(id = "song-2", title = "Target Song"),
    )
    val currentState = CurrentPlaybackItemState(
        item = songs[1].copy(title = "Player cached title"),
        isPlaying = true,
    )

    composeRule.setContent {
        MusicFreeTheme {
            PlaylistDetailContent(
                state = PlaylistDetailUiState(
                    playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                    musics = songs,
                    isLoading = false,
                ),
                sheetState = AddToPlaylistSheetState(),
                allPlaylists = emptyList(),
                favoriteResolver = { flowOf(false) },
                currentPlaybackItemState = currentState,
                onBack = {},
                onNavigateToSearchMusicList = {},
                onNavigateToMusicListEditorLite = {},
                actions = PlaylistDetailActions.Noop,
            )
        }
    }

    composeRule.onNodeWithTag("MusicItemRow_current_playing_wave").assertIsDisplayed()
    composeRule.onAllNodesWithTag("MusicItemRow_current_paused_wave").assertCountEquals(0)
}

@Test
fun `playlist detail keeps current row marked while paused`() {
    val songs = listOf(
        musicItem(id = "song-1", title = "First Song"),
        musicItem(id = "song-2", title = "Target Song"),
    )
    val currentState = CurrentPlaybackItemState(
        item = songs[1].copy(title = "Player cached title"),
        isPlaying = false,
    )

    composeRule.setContent {
        MusicFreeTheme {
            PlaylistDetailContent(
                state = PlaylistDetailUiState(
                    playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                    musics = songs,
                    isLoading = false,
                ),
                sheetState = AddToPlaylistSheetState(),
                allPlaylists = emptyList(),
                favoriteResolver = { flowOf(false) },
                currentPlaybackItemState = currentState,
                onBack = {},
                onNavigateToSearchMusicList = {},
                onNavigateToMusicListEditorLite = {},
                actions = PlaylistDetailActions.Noop,
            )
        }
    }

    composeRule.onNodeWithTag("MusicItemRow_current_paused_wave").assertIsDisplayed()
    composeRule.onAllNodesWithTag("MusicItemRow_current_playing_wave").assertCountEquals(0)
}

@Test
fun `playlist detail does not mark row when current song is absent`() {
    val songs = listOf(
        musicItem(id = "song-1", title = "First Song"),
        musicItem(id = "song-2", title = "Second Song"),
    )
    val currentState = CurrentPlaybackItemState(
        item = musicItem(id = "outside-song", title = "Outside Song"),
        isPlaying = true,
    )

    composeRule.setContent {
        MusicFreeTheme {
            PlaylistDetailContent(
                state = PlaylistDetailUiState(
                    playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                    musics = songs,
                    isLoading = false,
                ),
                sheetState = AddToPlaylistSheetState(),
                allPlaylists = emptyList(),
                favoriteResolver = { flowOf(false) },
                currentPlaybackItemState = currentState,
                onBack = {},
                onNavigateToSearchMusicList = {},
                onNavigateToMusicListEditorLite = {},
                actions = PlaylistDetailActions.Noop,
            )
        }
    }

    composeRule.onAllNodesWithTag("MusicItemRow_current_playing_wave").assertCountEquals(0)
    composeRule.onAllNodesWithTag("MusicItemRow_current_paused_wave").assertCountEquals(0)
    composeRule.onNode(hasContentDescription("定位到当前播放歌曲") and hasClickAction())
        .assertDoesNotExist()
}
```

- [ ] **Step 2: Run the playlist tests to verify they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*PlaylistDetailScreenTest' --no-daemon
```

Expected: FAIL with unresolved references for `CurrentPlaybackItemState` and `currentPlaybackItemState`.

- [ ] **Step 3: Add playback state to the ViewModel**

In `PlaylistDetailViewModel.kt`, add this data class below `PlaylistDetailUiState`:

```kotlin
data class CurrentPlaybackItemState(
    val item: MusicItem? = null,
    val isPlaying: Boolean = false,
)
```

Replace `currentPlayingItem` with:

```kotlin
val currentPlaybackItemState: StateFlow<CurrentPlaybackItemState> = playerController.playerState
    .map { state ->
        CurrentPlaybackItemState(
            item = state.currentItem,
            isPlaying = state.isPlaying,
        )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CurrentPlaybackItemState())
```

- [ ] **Step 4: Wire the state into `PlaylistDetailScreen`**

In `PlaylistDetailScreen.kt`, add this import:

```kotlin
import com.hank.musicfree.core.ui.MusicItemRowPlaybackState
```

Replace the collected current item:

```kotlin
val currentPlaybackItemState by viewModel.currentPlaybackItemState.collectAsStateWithLifecycle()
```

Pass it to `PlaylistDetailContent`:

```kotlin
currentPlaybackItemState = currentPlaybackItemState,
```

Change the `PlaylistDetailContent` parameter:

```kotlin
currentPlaybackItemState: CurrentPlaybackItemState = CurrentPlaybackItemState(),
```

Update the current index:

```kotlin
val currentPlayingIndex = remember(items, currentPlaybackItemState.item) {
    items.indexOfFirst { it.hasSameMediaIdentity(currentPlaybackItemState.item) }
}
```

Before calling `MusicItemRow`, derive the row state:

```kotlin
val rowPlaybackState = when {
    !item.hasSameMediaIdentity(currentPlaybackItemState.item) -> MusicItemRowPlaybackState.None
    currentPlaybackItemState.isPlaying -> MusicItemRowPlaybackState.CurrentPlaying
    else -> MusicItemRowPlaybackState.CurrentPaused
}
```

Pass it to `MusicItemRow`:

```kotlin
playbackState = rowPlaybackState,
```

- [ ] **Step 5: Update the existing FAB test parameter**

In `playlist detail floating action button scrolls to current playing music`, replace:

```kotlin
currentPlayingItem = currentPlayingItem,
```

with:

```kotlin
currentPlaybackItemState = CurrentPlaybackItemState(
    item = currentPlayingItem,
    isPlaying = true,
),
```

- [ ] **Step 6: Run playlist tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*PlaylistDetailScreenTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit Task 2**

Run:

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailViewModel.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreenTest.kt
git commit -m "feat(playlist): 标记当前播放歌曲行"
```

Expected: commit succeeds.

### Task 3: Verification And Main Merge

**Files:**
- Read: `docs/superpowers/specs/2026-07-08-playlist-current-playing-row-state-design.md`
- Verify: all files changed in Tasks 1 and 2

- [ ] **Step 1: Run targeted tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*PlaylistDetailScreenTest' --no-daemon
./gradlew :core:compileDebugAndroidTestKotlin --no-daemon
```

Expected: both commands PASS.

- [ ] **Step 2: Run dev harness and debug build**

Run:

```bash
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

Expected: both commands PASS.

- [ ] **Step 3: Record the verified worktree tree hash**

Run:

```bash
branch_tree="$(git rev-parse 'HEAD^{tree}')"
printf '%s\n' "$branch_tree"
```

Expected: prints a non-empty tree hash.

- [ ] **Step 4: Squash merge back to main**

Run from `.worktrees/playlist-current-row-state` back to the main checkout:

```bash
cd ../..
git status --short --branch
git merge --squash codex/playlist-current-row-state
git commit -m "feat(playlist): 标记当前播放歌曲行"
```

Expected: squash merge applies cleanly and commit succeeds. If conflicts occur, resolve them without reverting unrelated user changes, then rerun Step 1 and Step 2 on `main`.

- [ ] **Step 5: Compare tracked trees**

Run:

```bash
main_tree="$(git rev-parse 'HEAD^{tree}')"
test "$main_tree" = "$branch_tree"
```

Expected: exit code `0`. If it exits non-zero, run:

```bash
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

Expected: both commands PASS on `main`.

- [ ] **Step 6: Clean up the worktree after successful main verification**

Run:

```bash
git worktree remove .worktrees/playlist-current-row-state
git worktree prune
git branch -D codex/playlist-current-row-state
```

Expected: worktree and local feature branch are removed.

## Self-Review

- Spec coverage: Task 1 implements the shared row state, wave, paused static wave, and accessibility semantics. Task 2 wires `PlayerState.currentItem` plus `isPlaying` into playlist detail using `id + platform`. Task 3 covers targeted tests, harness check, debug build, squash merge, tree comparison, and cleanup.
- Placeholder scan: this plan contains no placeholder markers, no open-ended "add tests" instruction, and no unspecified code paths.
- Type consistency: `MusicItemRowPlaybackState`, `CurrentPlaybackItemState`, `currentPlaybackItemState`, and the test tags are introduced before later tasks use them.
