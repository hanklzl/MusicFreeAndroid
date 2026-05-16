# Player Detail Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让播放详情页封面页功能栏贴近进度条，并让底部播放模式按钮按 RN 原版支持随机、单曲循环、列表循环三态。

**Architecture:** 新增一个不依赖 Compose 的 `PlaybackMode` 领域 helper，把 `shuffleEnabled + RepeatMode` 映射为 RN 三态；`PlayerController` 负责真实状态迁移和队列 shuffle/unshuffle；`PlayerScreen` 只负责布局、图标和点击转发。封面页布局拆成 focused composable，使封面区域吃掉剩余空间，功能栏、进度条、控制区成为底部连续块。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt ViewModel, Robolectric, Compose UI tests, Gradle.

---

## 文件结构

- Create: `core/src/main/java/com/hank/musicfree/core/model/PlaybackMode.kt`
  - 负责 RN 风格播放模式三态：`Shuffle`、`Single`、`Queue`。
  - 提供 `from(shuffleEnabled, repeatMode)` 和 `next()`，供 `:player` 和 `:feature:player-ui` 共用。
- Create: `core/src/test/java/com/hank/musicfree/core/model/PlaybackModeTest.kt`
  - 覆盖模式映射和循环顺序。
- Create: `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerPlaybackModeTest.kt`
  - 覆盖 `PlayerController.cyclePlaybackMode()` 的真实状态迁移和队列随机标记。
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
  - 新增 `cyclePlaybackMode()`。
  - 保留 `cycleRepeatMode()` 和 `toggleShuffle()`，避免影响其他现有调用。
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`
  - 新增 `cyclePlaybackMode()` 委托。
  - 保留旧方法直到确认没有其他调用依赖。
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt`
  - 新增 ViewModel 委托测试。
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`
  - 调整封面页布局。
  - 新增 test tag 常量。
  - `PlayerControls` 改为基于 `PlaybackMode` 显示图标和 contentDescription。
  - 模式按钮点击改为调用 `viewModel.cyclePlaybackMode()`。
- Create: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerControlsTest.kt`
  - 覆盖三态图标语义和点击回调。
- Create: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerCoverLayoutTest.kt`
  - 覆盖功能栏和进度条之间只有固定小间距。
- Existing verification: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsContentTest.kt`
  - 保持歌词点击返回封面测试不回退。

---

### Task 0: 准备隔离 worktree

**Files:**
- Verify: `.gitignore`
- Worktree: `.worktrees/player-detail-controls`

- [ ] **Step 1: 确认主工作区状态**

Run:

```bash
git status --short --branch
```

Expected: 输出当前分支和状态。若存在非本任务改动，记录文件名，不要回退用户改动。

- [ ] **Step 2: 确认 `.worktrees/` 被忽略**

Run:

```bash
rg -n "^\\.worktrees/?$|^\\.worktrees" .gitignore
```

Expected: 至少出现一行 `.worktrees` 或 `.worktrees/`。

- [ ] **Step 3: 创建功能 worktree**

Run:

```bash
git worktree add .worktrees/player-detail-controls -b feat/player-detail-controls
```

Expected: 命令成功，输出包含 `Preparing worktree` 和新分支名。

- [ ] **Step 4: 后续命令切换到 worktree**

Run:

```bash
git -C .worktrees/player-detail-controls status --short --branch
```

Expected: 输出 `## feat/player-detail-controls`，工作区干净。

---

### Task 1: 新增 PlaybackMode 三态 helper

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/model/PlaybackMode.kt`
- Create: `core/src/test/java/com/hank/musicfree/core/model/PlaybackModeTest.kt`

- [ ] **Step 1: 写失败测试**

Create `core/src/test/java/com/hank/musicfree/core/model/PlaybackModeTest.kt`:

```kotlin
package com.hank.musicfree.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModeTest {

    @Test
    fun `from maps shuffle enabled to shuffle mode`() {
        assertEquals(
            PlaybackMode.Shuffle,
            PlaybackMode.from(shuffleEnabled = true, repeatMode = RepeatMode.ALL),
        )
        assertEquals(
            PlaybackMode.Shuffle,
            PlaybackMode.from(shuffleEnabled = true, repeatMode = RepeatMode.ONE),
        )
        assertEquals(
            PlaybackMode.Shuffle,
            PlaybackMode.from(shuffleEnabled = true, repeatMode = RepeatMode.OFF),
        )
    }

    @Test
    fun `from maps repeat one to single mode when shuffle is disabled`() {
        assertEquals(
            PlaybackMode.Single,
            PlaybackMode.from(shuffleEnabled = false, repeatMode = RepeatMode.ONE),
        )
    }

    @Test
    fun `from maps repeat all and off to queue mode when shuffle is disabled`() {
        assertEquals(
            PlaybackMode.Queue,
            PlaybackMode.from(shuffleEnabled = false, repeatMode = RepeatMode.ALL),
        )
        assertEquals(
            PlaybackMode.Queue,
            PlaybackMode.from(shuffleEnabled = false, repeatMode = RepeatMode.OFF),
        )
    }

    @Test
    fun `next follows RN repeat mode order`() {
        assertEquals(PlaybackMode.Single, PlaybackMode.Shuffle.next())
        assertEquals(PlaybackMode.Queue, PlaybackMode.Single.next())
        assertEquals(PlaybackMode.Shuffle, PlaybackMode.Queue.next())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.model.PlaybackModeTest
```

Expected: FAIL，包含 `Unresolved reference: PlaybackMode`。

- [ ] **Step 3: 写最小实现**

Create `core/src/main/java/com/hank/musicfree/core/model/PlaybackMode.kt`:

```kotlin
package com.hank.musicfree.core.model

enum class PlaybackMode {
    Shuffle,
    Single,
    Queue;

    fun next(): PlaybackMode = when (this) {
        Shuffle -> Single
        Single -> Queue
        Queue -> Shuffle
    }

    companion object {
        fun from(
            shuffleEnabled: Boolean,
            repeatMode: RepeatMode,
        ): PlaybackMode = when {
            shuffleEnabled -> Shuffle
            repeatMode == RepeatMode.ONE -> Single
            else -> Queue
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests com.hank.musicfree.core.model.PlaybackModeTest
```

Expected: PASS。

- [ ] **Step 5: 提交**

Run:

```bash
git add core/src/main/java/com/hank/musicfree/core/model/PlaybackMode.kt core/src/test/java/com/hank/musicfree/core/model/PlaybackModeTest.kt
git commit -m "feat(core): add playback mode mapping"
```

Expected: commit 成功，包含 2 个文件。

---

### Task 2: 在 PlayerController 中封装播放模式三态迁移

**Files:**
- Create: `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerPlaybackModeTest.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`

- [ ] **Step 1: 写失败测试**

Create `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerPlaybackModeTest.kt`:

```kotlin
package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RepeatMode
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerPlaybackModeTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `cyclePlaybackMode enters shuffle from queue mode`() {
        val controller = PlayerController(context)

        try {
            controller.playQueue.setQueue(testItems(), startIndex = 1)
            controller.setRepeatMode(RepeatMode.ALL)

            controller.cyclePlaybackMode()

            assertTrue(controller.playerState.value.shuffleEnabled)
            assertEquals(RepeatMode.ALL, controller.playerState.value.repeatMode)
            assertTrue(controller.playQueue.isShuffled)
            assertEquals("2", controller.playQueue.currentItem?.id)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `cyclePlaybackMode exits shuffle and enters single mode`() {
        val controller = PlayerController(context)
        val originalItems = testItems()

        try {
            controller.playQueue.setQueue(originalItems, startIndex = 1)
            controller.setRepeatMode(RepeatMode.ALL)
            controller.toggleShuffle()
            assertTrue(controller.playerState.value.shuffleEnabled)
            assertTrue(controller.playQueue.isShuffled)

            controller.cyclePlaybackMode()

            assertFalse(controller.playerState.value.shuffleEnabled)
            assertEquals(RepeatMode.ONE, controller.playerState.value.repeatMode)
            assertFalse(controller.playQueue.isShuffled)
            assertEquals(originalItems, controller.playQueue.items)
            assertEquals("2", controller.playQueue.currentItem?.id)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `cyclePlaybackMode enters queue mode from single mode`() {
        val controller = PlayerController(context)

        try {
            controller.playQueue.setQueue(testItems(), startIndex = 0)
            controller.setRepeatMode(RepeatMode.ONE)

            controller.cyclePlaybackMode()

            assertFalse(controller.playerState.value.shuffleEnabled)
            assertEquals(RepeatMode.ALL, controller.playerState.value.repeatMode)
            assertFalse(controller.playQueue.isShuffled)
        } finally {
            controller.release()
        }
    }

    private fun testItems(): List<MusicItem> = listOf(
        testItem("1"),
        testItem("2"),
        testItem("3"),
        testItem("4"),
    )

    private fun testItem(id: String) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = "https://example.test/$id.mp3",
        artwork = null,
        qualities = null,
    )
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests com.hank.musicfree.player.controller.PlayerControllerPlaybackModeTest
```

Expected: FAIL，包含 `Unresolved reference: cyclePlaybackMode`。

- [ ] **Step 3: 写最小实现**

Modify `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt` imports:

```kotlin
import com.hank.musicfree.core.model.PlaybackMode
import com.hank.musicfree.core.model.RepeatMode
```

Add this method after `cycleRepeatMode()`:

```kotlin
    fun cyclePlaybackMode() {
        when (PlaybackMode.from(shuffleEnabled, repeatMode)) {
            PlaybackMode.Shuffle -> {
                shuffleEnabled = false
                playQueue.unshuffle()
                repeatMode = RepeatMode.ONE
            }
            PlaybackMode.Single -> {
                if (shuffleEnabled) {
                    shuffleEnabled = false
                    playQueue.unshuffle()
                }
                repeatMode = RepeatMode.ALL
            }
            PlaybackMode.Queue -> {
                repeatMode = RepeatMode.ALL
                if (!shuffleEnabled) {
                    shuffleEnabled = true
                    playQueue.shuffle()
                }
            }
        }
        runOnControllerThread {
            emitState()
        }
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests com.hank.musicfree.player.controller.PlayerControllerPlaybackModeTest
```

Expected: PASS。

- [ ] **Step 5: 运行现有队列测试，确认未破坏 shuffle/unshuffle**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests com.hank.musicfree.player.queue.PlayQueueTest
```

Expected: PASS。

- [ ] **Step 6: 提交**

Run:

```bash
git add player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerPlaybackModeTest.kt
git commit -m "feat(player): cycle playback modes"
```

Expected: commit 成功，包含 controller 和测试。

---

### Task 3: 在 PlayerViewModel 中暴露三态播放模式入口

**Files:**
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

Modify `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt` near the existing `cycleRepeatMode calls controller` test:

```kotlin
    @Test
    fun `cyclePlaybackMode calls controller`() {
        val viewModel = createViewModel()
        viewModel.cyclePlaybackMode()
        verify(playerController).cyclePlaybackMode()
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerViewModelTest
```

Expected: FAIL，包含 `Unresolved reference: cyclePlaybackMode`。

- [ ] **Step 3: 写最小实现**

Modify `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`, add after `fun cycleRepeatMode()`:

```kotlin
    fun cyclePlaybackMode() = playerController.cyclePlaybackMode()
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerViewModelTest
```

Expected: PASS。

- [ ] **Step 5: 提交**

Run:

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt
git commit -m "feat(player-ui): expose playback mode cycling"
```

Expected: commit 成功。

---

### Task 4: 更新 PlayerControls 为 RN 三态按钮

**Files:**
- Create: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerControlsTest.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`

- [ ] **Step 1: 写失败测试**

Create `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerControlsTest.kt`:

```kotlin
package com.hank.musicfree.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.PlaybackMode
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `mode button shows queue mode semantics`() {
        setControls(mode = PlaybackMode.Queue)

        composeRule.onNodeWithContentDescription("列表循环").assertExists()
    }

    @Test
    fun `mode button shows shuffle mode semantics`() {
        setControls(mode = PlaybackMode.Shuffle)

        composeRule.onNodeWithContentDescription("随机播放").assertExists()
    }

    @Test
    fun `mode button shows single mode semantics`() {
        setControls(mode = PlaybackMode.Single)

        composeRule.onNodeWithContentDescription("单曲循环").assertExists()
    }

    @Test
    fun `mode button click invokes playback mode cycle callback`() {
        var clicks = 0
        setControls(
            mode = PlaybackMode.Queue,
            onCyclePlaybackMode = { clicks++ },
        )

        composeRule.onNodeWithTag(PlayerModeButtonTestTag).performClick()

        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    private fun setControls(
        mode: PlaybackMode,
        onCyclePlaybackMode: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                    PlayerControls(
                        isPlaying = false,
                        playbackMode = mode,
                        onTogglePlayPause = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onCyclePlaybackMode = onCyclePlaybackMode,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerControlsTest
```

Expected: FAIL，原因包含 `Cannot access 'PlayerControls': it is private` 或参数不匹配。

- [ ] **Step 3: 更新 `PlayerControls` 签名和 test tag**

Modify `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`.

Add imports:

```kotlin
import androidx.compose.ui.platform.testTag
import com.hank.musicfree.core.model.PlaybackMode
```

Add constants near `PlayerContentPage`:

```kotlin
internal const val PlayerModeButtonTestTag = "player.controls.mode"
internal const val PlayerCoverBottomClusterTestTag = "player.cover.bottomCluster"
internal const val PlayerOperationsBarTestTag = "player.operations.bar"
internal const val PlayerSeekBarTestTag = "player.seekBar"
```

Change `PlayerControls` from `private` to `internal` and replace its signature:

```kotlin
@Composable
internal fun PlayerControls(
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
) {
```

Replace the mode icon block inside `PlayerControls`:

```kotlin
        val modeIcon = when (playbackMode) {
            PlaybackMode.Shuffle -> R.drawable.ic_shuffle
            PlaybackMode.Single -> R.drawable.ic_repeat_song
            PlaybackMode.Queue -> R.drawable.ic_repeat_song_1
        }
        val modeDescription = when (playbackMode) {
            PlaybackMode.Shuffle -> "随机播放"
            PlaybackMode.Single -> "单曲循环"
            PlaybackMode.Queue -> "列表循环"
        }
        IconButton(
            onClick = onCyclePlaybackMode,
            modifier = Modifier.testTag(PlayerModeButtonTestTag),
        ) {
            Icon(
                painter = painterResource(modeIcon),
                contentDescription = modeDescription,
                tint = Color.White,
                modifier = Modifier.size(rpx(56)),
            )
        }
```

- [ ] **Step 4: 更新 `PlayerScreen` 调用**

Modify the existing `PlayerControls` call:

```kotlin
            PlayerControls(
                isPlaying = state.isPlaying,
                playbackMode = PlaybackMode.from(
                    shuffleEnabled = state.shuffleEnabled,
                    repeatMode = state.repeatMode,
                ),
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onSkipNext = { viewModel.skipToNext() },
                onCyclePlaybackMode = { viewModel.cyclePlaybackMode() },
            )
```

Remove now-unused `repeatMode`, `shuffleEnabled`, `onCycleRepeatMode`, and `onToggleShuffle` parameters from `PlayerControls`.

- [ ] **Step 5: 运行测试确认通过**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerControlsTest
```

Expected: PASS。

- [ ] **Step 6: 运行 ViewModel 测试确认 UI wiring 依赖仍可编译**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerViewModelTest
```

Expected: PASS。

- [ ] **Step 7: 提交**

Run:

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerControlsTest.kt
git commit -m "feat(player-ui): show playback mode tri-state"
```

Expected: commit 成功。

---

### Task 5: 调整封面页布局，让功能栏贴近进度条

**Files:**
- Create: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerCoverLayoutTest.kt`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`

- [ ] **Step 1: 写失败测试**

Create `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerCoverLayoutTest.kt`:

```kotlin
package com.hank.musicfree.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.PlaybackMode
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerCoverLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `operations bar stays close to seek bar`() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        PlayerCoverPageContent(
                            artworkUrl = null,
                            isFav = false,
                            hasCurrentItem = true,
                            onToggleFav = {},
                            onAddToPlaylist = {},
                            onToggleLyrics = {},
                            modifier = Modifier.weight(1f),
                        )
                        PlayerSeekBar(
                            position = 0L,
                            duration = 180_000L,
                            onSeek = {},
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .testTag(PlayerSeekBarTestTag),
                        )
                        PlayerControls(
                            isPlaying = false,
                            playbackMode = PlaybackMode.Queue,
                            onTogglePlayPause = {},
                            onSkipPrevious = {},
                            onSkipNext = {},
                            onCyclePlaybackMode = {},
                        )
                    }
                }
            }
        }

        val operationsBounds = composeRule.onNodeWithTag(PlayerOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val seekBounds = composeRule.onNodeWithTag(PlayerSeekBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot

        val gapDp = with(composeRule.density) {
            (seekBounds.top - operationsBounds.bottom).toDp()
        }
        assertTrue(
            "Expected operations and seek bar gap <= 20.dp, was $gapDp",
            gapDp <= 20.dp,
        )
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerCoverLayoutTest
```

Expected: FAIL，原因包含 `Unresolved reference: PlayerCoverPageContent` 或找不到 test tag。

- [ ] **Step 3: 新增封面页 focused composable**

Modify `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`.

Add this composable before `PlayerCoverArt`:

```kotlin
@Composable
internal fun PlayerCoverPageContent(
    artworkUrl: String?,
    isFav: Boolean,
    hasCurrentItem: Boolean,
    onToggleFav: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayerCoverArt(
                artworkUrl = artworkUrl,
                modifier = Modifier
                    .size(rpx(500))
                    .clickable { onToggleLyrics() },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PlayerCoverBottomClusterTestTag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerOperationsBar(
                isFav = isFav,
                hasCurrentItem = hasCurrentItem,
                onToggleFav = onToggleFav,
                onAddToPlaylist = onAddToPlaylist,
                onToggleLyrics = onToggleLyrics,
            )
            Spacer(Modifier.height(rpx(24)))
        }
    }
}
```

Change `PlayerOperationsBar` and `PlayerSeekBar` visibility from `private` to `internal`:

```kotlin
@Composable
internal fun PlayerOperationsBar(
```

```kotlin
@Composable
internal fun PlayerSeekBar(
```

Add the operations test tag to the `Row` inside `PlayerOperationsBar`:

```kotlin
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(80))
            .padding(horizontal = rpx(48))
            .testTag(PlayerOperationsBarTestTag),
```

- [ ] **Step 4: 替换封面页分支布局**

Modify the `PlayerContentPage.Cover` branch in `PlayerScreen`:

```kotlin
                PlayerContentPage.Cover -> {
                    PlayerCoverPageContent(
                        artworkUrl = artworkUrl,
                        isFav = isFav,
                        hasCurrentItem = currentItem != null,
                        onToggleFav = { viewModel.toggleCurrentFavorite() },
                        onAddToPlaylist = { viewModel.showAddToPlaylistSheet() },
                        onToggleLyrics = { contentPage = PlayerContentPage.Lyrics },
                        modifier = Modifier.weight(1f),
                    )
                }
```

Remove the old cover branch spacers around `PlayerCoverArt` and `PlayerOperationsBar`.

- [ ] **Step 5: 给真实 seek bar 添加 test tag**

Modify the common `PlayerSeekBar` call in `PlayerScreen`:

```kotlin
            PlayerSeekBar(
                position = state.position,
                duration = state.duration,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier
                    .padding(horizontal = rpx(48))
                    .testTag(PlayerSeekBarTestTag),
            )
```

- [ ] **Step 6: 运行布局测试确认通过**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerCoverLayoutTest
```

Expected: PASS。

- [ ] **Step 7: 运行播放器 UI 相关测试**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.hank.musicfree.feature.playerui.PlayerControlsTest --tests com.hank.musicfree.feature.playerui.PlayerScreenInsetsTest --tests com.hank.musicfree.feature.playerui.lyrics.PlayerLyricsContentTest
```

Expected: PASS。

- [ ] **Step 8: 提交**

Run:

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerCoverLayoutTest.kt
git commit -m "fix(player-ui): align detail controls with seek bar"
```

Expected: commit 成功。

---

### Task 6: 集成验证与构建

**Files:**
- Verify: `core`, `player`, `feature/player-ui`, `app`

- [ ] **Step 1: 运行 core 测试**

Run:

```bash
./gradlew :core:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 运行 player 测试**

Run:

```bash
./gradlew :player:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 运行 player-ui 测试**

Run:

```bash
./gradlew :feature:player-ui:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 构建 Debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 查看最终 diff**

Run:

```bash
git diff --stat main...HEAD
```

Expected: 输出只包含本任务相关的 `core`、`player`、`feature/player-ui` 文件和测试。

- [ ] **Step 6: 检查工作区**

Run:

```bash
git status --short --branch
```

Expected: 当前分支为 `feat/player-detail-controls`，工作区干净。

---

### Task 7: 设备或模拟器运行态验收

**Files:**
- Runtime: Android device or emulator

- [ ] **Step 1: 确认设备可用**

Run:

```bash
adb devices
```

Expected: 至少有一台设备显示为 `device`。如果没有设备，记录“运行态验收未执行：无可用设备或模拟器”，并不要伪造验收结论。

- [ ] **Step 2: 安装 Debug APK**

Run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: 输出 `Success`。

- [ ] **Step 3: 手动验证封面页布局**

操作：

1. 启动应用。
2. 播放一首歌曲。
3. 进入播放详情页。
4. 观察封面页功能栏位置。

Expected: 功能栏位于进度条正上方，二者之间只有固定小间距；功能栏不悬在页面中部。

- [ ] **Step 4: 手动验证歌词页返回封面**

操作：

1. 在播放详情页点击封面。
2. 进入歌词页后点击歌词文本。
3. 再次从封面进入歌词页，点击歌词空白区域。

Expected: 两次点击都能回到封面页；拖动歌词出现 seek overlay 时，点击 overlay 周边不应立刻误返回封面。

- [ ] **Step 5: 手动验证播放模式三态**

操作：

1. 连续点击左下角播放模式按钮至少 4 次。
2. 观察图标变化。
3. 在随机状态点击下一首。
4. 从随机切到单曲后再观察图标。

Expected: 图标按 `列表循环 -> 随机播放 -> 单曲循环 -> 列表循环` 循环，随机状态可进入，退出随机后图标不再显示随机。

- [ ] **Step 6: 记录运行态结论**

如果已执行运行态验收，最终回复必须写明实际设备或模拟器名称。示例：

```text
运行态验收：已在 Pixel_7_API_35 模拟器验证播放详情页布局、歌词返回封面、播放模式三态。
```

如果 Step 1 无设备，最终回复中写明：

```text
运行态验收：未执行，原因是没有可用设备或模拟器。
```

---

### Task 8: 最终整理

**Files:**
- Verify: all changed files

- [ ] **Step 1: 运行 diff 检查**

Run:

```bash
git diff --check main...HEAD
```

Expected: 无输出。

- [ ] **Step 2: 输出提交列表**

Run:

```bash
git log --oneline main..HEAD
```

Expected: 至少包含以下提交主题：

```text
feat(core): add playback mode mapping
feat(player): cycle playback modes
feat(player-ui): expose playback mode cycling
feat(player-ui): show playback mode tri-state
fix(player-ui): align detail controls with seek bar
```

- [ ] **Step 3: 准备最终说明**

最终说明包含：

```text
已完成：
- 播放详情页功能栏贴近进度条。
- 播放模式按钮支持随机、单曲循环、列表循环三态。
- 歌词页点击返回封面测试保持通过。

验证：
- ./gradlew :core:testDebugUnitTest
- ./gradlew :player:testDebugUnitTest
- ./gradlew :feature:player-ui:testDebugUnitTest
- ./gradlew :app:assembleDebug
- 运行态验收：已执行时写实际设备名称；未执行时写清楚没有可用设备或模拟器。
```
