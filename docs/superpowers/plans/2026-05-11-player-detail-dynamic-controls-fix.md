# Player Detail Dynamic Controls Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make playback detail quality/rate icons and play queue playback mode controls reflect current runtime state.

**Architecture:** Keep `PlaybackMode` as the shared shuffle/single/queue UI model. Add missing RN PNG assets to `:core`, map operation bar state to drawables in `:feature:player-ui`, and update the play queue sheet to consume `PlaybackMode` instead of raw `RepeatMode`.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric Compose tests, Gradle Android modules.

---

## File Map

- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`
  - Add dynamic quality/rate image mapping and use it in `PlayerOperationsBar`.
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerOperationsBarTest.kt`
  - Cover dynamic quality/rate resource mapping.
- Create: `core/src/main/res/drawable/ic_quality_low.png`
- Create: `core/src/main/res/drawable/ic_quality_high.png`
- Create: `core/src/main/res/drawable/ic_quality_super.png`
- Create: `core/src/main/res/drawable/ic_rate_050.png`
- Create: `core/src/main/res/drawable/ic_rate_075.png`
- Create: `core/src/main/res/drawable/ic_rate_125.png`
- Create: `core/src/main/res/drawable/ic_rate_150.png`
- Create: `core/src/main/res/drawable/ic_rate_175.png`
- Create: `core/src/main/res/drawable/ic_rate_200.png`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueUiModel.kt`
  - Replace `repeatMode` UI exposure with `PlaybackMode`.
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`
  - Derive queue `PlaybackMode` from `PlayerState`.
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueSheet.kt`
  - Call `cyclePlaybackMode()`.
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueSheetContent.kt`
  - Render queue mode label/icon from `PlaybackMode`.
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueSheetContentTest.kt`
  - Cover shuffle label and callback.
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelQueueTest.kt`
  - Cover queue model playback mode derivation.

## Task 1: Dynamic Quality And Rate Operation Icons

**Files:**
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerOperationsBarTest.kt`
- Create PNGs under `core/src/main/res/drawable/`

- [ ] **Step 1: Add failing tests**

Add tests that assert:

```kotlin
assertEquals(CoreR.drawable.ic_quality_low, playerQualityImage(PlayQuality.LOW))
assertEquals(CoreR.drawable.ic_quality_standard, playerQualityImage(PlayQuality.STANDARD))
assertEquals(CoreR.drawable.ic_quality_high, playerQualityImage(PlayQuality.HIGH))
assertEquals(CoreR.drawable.ic_quality_super, playerQualityImage(PlayQuality.SUPER))
assertEquals(CoreR.drawable.ic_rate_050, playerRateImage(0.5f))
assertEquals(CoreR.drawable.ic_rate_075, playerRateImage(0.75f))
assertEquals(CoreR.drawable.ic_rate_100, playerRateImage(1.0f))
assertEquals(CoreR.drawable.ic_rate_125, playerRateImage(1.25f))
assertEquals(CoreR.drawable.ic_rate_150, playerRateImage(1.5f))
assertEquals(CoreR.drawable.ic_rate_175, playerRateImage(1.75f))
assertEquals(CoreR.drawable.ic_rate_200, playerRateImage(2.0f))
assertEquals(CoreR.drawable.ic_rate_100, playerRateImage(1.1f))
```

- [ ] **Step 2: Verify red**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerOperationsBarTest' --no-daemon
```

Expected: fails because the helper functions or new drawables do not exist.

- [ ] **Step 3: Add RN PNG assets**

Copy these exact assets:

```text
../../../../MusicFree/src/assets/imgs/low-quality.png -> core/src/main/res/drawable/ic_quality_low.png
../../../../MusicFree/src/assets/imgs/high-quality.png -> core/src/main/res/drawable/ic_quality_high.png
../../../../MusicFree/src/assets/imgs/super-quality.png -> core/src/main/res/drawable/ic_quality_super.png
../../../../MusicFree/src/assets/imgs/50x.png -> core/src/main/res/drawable/ic_rate_050.png
../../../../MusicFree/src/assets/imgs/75x.png -> core/src/main/res/drawable/ic_rate_075.png
../../../../MusicFree/src/assets/imgs/125x.png -> core/src/main/res/drawable/ic_rate_125.png
../../../../MusicFree/src/assets/imgs/150x.png -> core/src/main/res/drawable/ic_rate_150.png
../../../../MusicFree/src/assets/imgs/175x.png -> core/src/main/res/drawable/ic_rate_175.png
../../../../MusicFree/src/assets/imgs/200x.png -> core/src/main/res/drawable/ic_rate_200.png
```

- [ ] **Step 4: Implement mapping and use it**

In `PlayerScreen.kt`, add:

```kotlin
internal fun playerQualityImage(quality: PlayQuality): Int = when (quality) {
    PlayQuality.LOW -> R.drawable.ic_quality_low
    PlayQuality.STANDARD -> R.drawable.ic_quality_standard
    PlayQuality.HIGH -> R.drawable.ic_quality_high
    PlayQuality.SUPER -> R.drawable.ic_quality_super
}

internal fun playerRateImage(speed: Float): Int = when {
    kotlin.math.abs(speed - 0.5f) < 0.001f -> R.drawable.ic_rate_050
    kotlin.math.abs(speed - 0.75f) < 0.001f -> R.drawable.ic_rate_075
    kotlin.math.abs(speed - 1.25f) < 0.001f -> R.drawable.ic_rate_125
    kotlin.math.abs(speed - 1.5f) < 0.001f -> R.drawable.ic_rate_150
    kotlin.math.abs(speed - 1.75f) < 0.001f -> R.drawable.ic_rate_175
    kotlin.math.abs(speed - 2.0f) < 0.001f -> R.drawable.ic_rate_200
    else -> R.drawable.ic_rate_100
}
```

Then replace hard-coded images:

```kotlin
PlayerOperationImage(image = playerQualityImage(currentQuality))
PlayerOperationImage(image = playerRateImage(currentSpeed))
```

- [ ] **Step 5: Verify green**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerOperationsBarTest' --no-daemon
```

Expected: pass.

## Task 2: Queue Sheet Three-State Playback Mode

**Files:**
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueUiModel.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueSheet.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueSheetContent.kt`
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/queue/PlayQueueSheetContentTest.kt`
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelQueueTest.kt`

- [ ] **Step 1: Add failing tests**

Update queue UI tests to expect:

```kotlin
composeRule.onAllNodesWithText("随机播放").onFirst().assertIsDisplayed()
```

Update ViewModel queue test to expect:

```kotlin
playerStateFlow.value = PlayerState.EMPTY.copy(shuffleEnabled = true, repeatMode = RepeatMode.ALL)
advanceUntilIdle()
assertEquals(PlaybackMode.Shuffle, vm.queueUiModel.value.playbackMode)
```

Also verify single and queue:

```kotlin
playerStateFlow.value = PlayerState.EMPTY.copy(shuffleEnabled = false, repeatMode = RepeatMode.ONE)
advanceUntilIdle()
assertEquals(PlaybackMode.Single, vm.queueUiModel.value.playbackMode)
playerStateFlow.value = PlayerState.EMPTY.copy(shuffleEnabled = false, repeatMode = RepeatMode.ALL)
advanceUntilIdle()
assertEquals(PlaybackMode.Queue, vm.queueUiModel.value.playbackMode)
```

- [ ] **Step 2: Verify red**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayQueueSheetContentTest' --tests '*PlayerViewModelQueueTest' --no-daemon
```

Expected: fails because `PlayQueueUiModel` has no playback mode and the sheet cannot render shuffle.

- [ ] **Step 3: Update model and ViewModel**

Change `PlayQueueUiModel`:

```kotlin
data class PlayQueueUiModel(
    val items: List<MusicItem>,
    val currentIndex: Int,
    val playbackMode: PlaybackMode,
)
```

Use `PlaybackMode.Queue` for `EMPTY`.

In `PlayerViewModel.queueUiModel`, derive:

```kotlin
playbackMode = PlaybackMode.from(
    shuffleEnabled = player.shuffleEnabled,
    repeatMode = player.repeatMode,
)
```

- [ ] **Step 4: Update sheet callback and rendering**

In `PlayQueueSheet.kt`, pass:

```kotlin
onCyclePlaybackMode = viewModel::cyclePlaybackMode
```

In `PlayQueueSheetContent.kt`, render:

```kotlin
iconPainter = painterResource(playerModeIcon(playbackMode))
label = playerModeDescription(playbackMode)
```

The existing `FidelityAnchors.Player.Queue.RepeatModeButton` tag stays unchanged.

- [ ] **Step 5: Verify green**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayQueueSheetContentTest' --tests '*PlayerViewModelQueueTest' --tests '*PlayerControlsTest' --no-daemon
```

Expected: pass.

## Task 3: Final Verification

- [ ] **Step 1: Run focused player UI tests**

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerOperationsBarTest' --tests '*PlayQueueSheetContentTest' --tests '*PlayerViewModelQueueTest' --tests '*PlayerControlsTest' --no-daemon
```

Expected: pass.

- [ ] **Step 2: Run full player UI unit tests**

```bash
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 3: Run debug build gate**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: pass.

- [ ] **Step 4: Run dev harness grep check**

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: all checks pass.
