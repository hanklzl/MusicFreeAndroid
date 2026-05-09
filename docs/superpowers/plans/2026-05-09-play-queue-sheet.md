# Play Queue Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the shared "播放列表" bottom sheet for both `MiniPlayer` and `PlayerScreen`, aligned to the original RN `playList` panel.

**Architecture:** Reactive queue snapshot exposed by `PlayerController`; `PlayerViewModel` derives a UI model from it; both entry points host their own `ModalBottomSheet` containing the same shared `PlayQueueSheetContent` composable. Tests are TDD-first: failing test → minimal impl → green → commit.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose (BOM 2026.04.01), Material3, Hilt, Coroutines/Flow, Robolectric (sdk 29 / 34), JUnit 4, Mockito-Kotlin, Media3 (existing).

**Spec:** `docs/superpowers/specs/2026-05-09-play-queue-sheet-design.md`

**Branch / worktree:** `feat/play-queue-sheet` at `.worktrees/feat-play-queue-sheet`. All paths below are relative to that worktree root.

**Module touchpoints:**

| Path | Action |
|---|---|
| `player/src/main/java/com/zili/android/musicfreeandroid/player/queue/PlayQueueSnapshot.kt` | create |
| `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt` | modify (add `queueState`, emit in mutating methods) |
| `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt` | create |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueUiModel.kt` | create |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueRow.kt` | create |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheetContent.kt` | create |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheet.kt` | create |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt` | modify (`queueUiModel`, transfer methods) |
| `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelQueueTest.kt` | create |
| `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheetContentTest.kt` | create |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayer.kt` | modify (drop `onNavigateToQueue`, host sheet) |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt` | modify (host sheet, replace TODO) |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt` | modify (add Player.Queue.* anchors) |

---

## Task 1: Introduce `PlayQueueSnapshot` value type

**Files:**
- Create: `player/src/main/java/com/zili/android/musicfreeandroid/player/queue/PlayQueueSnapshot.kt`

This is a pure data class. No behavior, no test of its own — it will be exercised by Task 2's tests.

- [ ] **Step 1: Create the data class**

```kotlin
package com.zili.android.musicfreeandroid.player.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem

data class PlayQueueSnapshot(
    val items: List<MusicItem>,
    val currentIndex: Int,
) {
    companion object {
        val EMPTY = PlayQueueSnapshot(items = emptyList(), currentIndex = -1)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run from worktree root: `./gradlew :player:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/queue/PlayQueueSnapshot.kt
git commit -m "feat(player): add PlayQueueSnapshot value type"
```

---

## Task 2: Add `queueState` to `PlayerController` (initial empty state)

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Create: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt`

We add the StateFlow first, default `EMPTY`, no emit hookups yet. Subsequent tasks will wire emits one mutating method at a time.

- [ ] **Step 1: Write the failing test**

Create `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerQueueStateTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `queueState defaults to EMPTY`() {
        val controller = PlayerController(context)
        try {
            assertEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
        } finally {
            controller.release()
        }
    }

    internal fun item(id: String) = MusicItem(
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

- [ ] **Step 2: Run the test, expect compile failure**

Run: `./gradlew :player:testDebugUnitTest --tests com.zili.android.musicfreeandroid.player.controller.PlayerControllerQueueStateTest`
Expected: FAILED — `unresolved reference: queueState`.

- [ ] **Step 3: Add `queueState` to `PlayerController`**

In `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`, near the existing `_playerState`/`_playHistory` declarations (around line 57-60 in the current file), add:

```kotlin
import com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot
```

```kotlin
private val _queueState = MutableStateFlow(PlayQueueSnapshot.EMPTY)
val queueState: StateFlow<PlayQueueSnapshot> = _queueState.asStateFlow()

private fun emitQueueState() {
    _queueState.value = PlayQueueSnapshot(
        items = playQueue.items,
        currentIndex = playQueue.currentIndex,
    )
}
```

- [ ] **Step 4: Run test, expect PASS**

Run: `./gradlew :player:testDebugUnitTest --tests com.zili.android.musicfreeandroid.player.controller.PlayerControllerQueueStateTest`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt
git commit -m "feat(player): expose queueState StateFlow on PlayerController"
```

---

## Task 3: Emit `queueState` from queue-mutating methods

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Modify: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt`

Methods to instrument with `emitQueueState()` at their tail (after `playQueue` mutation, before any return path that doesn't already pass through emit):

| Method | Why |
|---|---|
| `playItem` | adds + skipTo |
| `playQueue(items, startIndex)` | replaces queue |
| `addToQueue` | appends |
| `addNextInQueue` | inserts after current |
| `removeFromQueue` | removes |
| `moveInQueue` | reorders |
| `skipToNext` | currentIndex moves |
| `skipToPrevious` | currentIndex moves (only when actually skipping, not when seeking to 0) |
| `skipTo(index)` | currentIndex moves |
| `toggleShuffle` | reorders |
| `reset` | clears |
| `handleTrackEnded` (RepeatMode.OFF branch when `playQueue.next` returns non-null) | currentIndex moves |

`skipToNext` already mutates via `playQueue.next` → emit unconditionally if a next item was found.

`setRepeatMode` and `cycleRepeatMode` do **not** mutate the queue → no emit needed (they affect `_playerState.repeatMode` only).

- [ ] **Step 1: Write the failing tests**

Append the following tests to `PlayerControllerQueueStateTest.kt`:

```kotlin
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import org.junit.Assert.assertNotEquals

@Test
fun `playQueue emits snapshot with items and startIndex`() {
    val controller = PlayerController(context)
    try {
        val items = listOf(item("1"), item("2"), item("3"))
        controller.playQueue(items, startIndex = 1)
        val snapshot = controller.queueState.value
        assertEquals(items, snapshot.items)
        assertEquals(1, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `playItem adds new item and emits snapshot pointing at it`() {
    val controller = PlayerController(context)
    try {
        controller.playItem(item("1"))
        val snapshot = controller.queueState.value
        assertEquals(listOf(item("1")), snapshot.items)
        assertEquals(0, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `playItem skipTo when item already exists and emits snapshot`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
        controller.playItem(item("3"))
        val snapshot = controller.queueState.value
        assertEquals(3, snapshot.items.size)
        assertEquals(2, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `addToQueue emits snapshot with appended item`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1")), startIndex = 0)
        controller.addToQueue(item("2"))
        val snapshot = controller.queueState.value
        assertEquals(listOf(item("1"), item("2")), snapshot.items)
        assertEquals(0, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `addNextInQueue emits snapshot with item inserted after current`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("3")), startIndex = 0)
        controller.addNextInQueue(item("2"))
        val snapshot = controller.queueState.value
        assertEquals(listOf(item("1"), item("2"), item("3")), snapshot.items)
        assertEquals(0, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `removeFromQueue non-current emits snapshot with smaller list`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
        controller.removeFromQueue(2)
        val snapshot = controller.queueState.value
        assertEquals(listOf(item("1"), item("2")), snapshot.items)
        assertEquals(0, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `removeFromQueue current emits snapshot with adjusted currentIndex`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 1)
        controller.removeFromQueue(1)
        val snapshot = controller.queueState.value
        assertEquals(listOf(item("1"), item("3")), snapshot.items)
        assertEquals(1, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `moveInQueue emits snapshot with new order`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
        controller.moveInQueue(0, 2)
        val snapshot = controller.queueState.value
        assertEquals(listOf(item("2"), item("3"), item("1")), snapshot.items)
        assertEquals(2, snapshot.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `skipTo emits snapshot with target index`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("2"), item("3")), startIndex = 0)
        controller.skipTo(2)
        assertEquals(2, controller.queueState.value.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `skipToNext emits snapshot with advanced currentIndex`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("2")), startIndex = 0)
        controller.skipToNext()
        assertEquals(1, controller.queueState.value.currentIndex)
    } finally {
        controller.release()
    }
}

@Test
fun `toggleShuffle emits snapshot whose items differ in order`() {
    val controller = PlayerController(context)
    try {
        val original = (1..6).map { item(it.toString()) }
        controller.playQueue(original, startIndex = 0)
        controller.toggleShuffle()
        val snapshot = controller.queueState.value
        // current still at index 0 by PlayQueue.shuffle contract
        assertEquals(0, snapshot.currentIndex)
        assertEquals(original.size, snapshot.items.size)
        // ordering may collide for tiny inputs; compare bag of ids stays equal
        assertEquals(original.map { it.id }.toSet(), snapshot.items.map { it.id }.toSet())
    } finally {
        controller.release()
    }
}

@Test
fun `reset emits empty snapshot`() {
    val controller = PlayerController(context)
    try {
        controller.playQueue(listOf(item("1"), item("2")), startIndex = 0)
        assertNotEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
        controller.reset()
        assertEquals(PlayQueueSnapshot.EMPTY, controller.queueState.value)
    } finally {
        controller.release()
    }
}
```

Note: `skipToPrevious` is intentionally not tested here because it depends on `MediaController.currentPosition` (>3000ms = seek to 0, no queue mutation) — Robolectric's `MediaController.buildAsync` resolution is fragile in unit tests. Coverage for that path is via the existing `androidTest/.../PlayerControllerTest.kt` instrumented suite which we leave untouched.

- [ ] **Step 2: Run the new tests, expect failures**

Run: `./gradlew :player:testDebugUnitTest --tests com.zili.android.musicfreeandroid.player.controller.PlayerControllerQueueStateTest`
Expected: PASS only `queueState defaults to EMPTY`; the 12 new tests FAIL because no method emits yet.

- [ ] **Step 3: Wire `emitQueueState()` calls**

In `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`, add `emitQueueState()` at the end of each of the following methods. Apply tightly — keep return paths unchanged.

`playItem` — at the end of the function body, after `setMediaItemAndPlay(item)`:

```kotlin
fun playItem(item: MusicItem) {
    val index = playQueue.items.indexOfFirst {
        it.id == item.id && it.platform == item.platform
    }
    if (index >= 0) {
        playQueue.skipTo(index)
    } else {
        playQueue.add(item)
        playQueue.skipTo(playQueue.size - 1)
    }
    setMediaItemAndPlay(item)
    emitQueueState()
}
```

`playQueue` — at the end:

```kotlin
fun playQueue(items: List<MusicItem>, startIndex: Int = 0) {
    playQueue.setQueue(items, startIndex)
    if (shuffleEnabled) playQueue.shuffle()
    playQueue.currentItem?.let { setMediaItemAndPlay(it) }
    emitQueueState()
}
```

`skipToNext` — only when a next item was found:

```kotlin
fun skipToNext() {
    val next = playQueue.next(repeatMode) ?: return
    setMediaItemAndPlay(next)
    emitQueueState()
}
```

`skipToPrevious` — only on the actual skip path (not when seek-to-0):

```kotlin
fun skipToPrevious() {
    withConnectedController { controller ->
        val position = controller.currentPosition
        if (position > 3_000L) {
            controller.seekTo(0L)
            return@withConnectedController
        }
        val prev = playQueue.previous(repeatMode) ?: return@withConnectedController
        val mediaItem = prev.toMediaItem(defaultArtworkUri)
        recordHistory(prev)
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
        emitQueueState()
    }
}
```

`skipTo(index)` — at the end after success:

```kotlin
fun skipTo(index: Int) {
    val item = playQueue.skipTo(index) ?: return
    setMediaItemAndPlay(item)
    emitQueueState()
}
```

`toggleShuffle` — inside the existing `runOnControllerThread { ... }` block, after `emitState()`:

```kotlin
fun toggleShuffle() {
    shuffleEnabled = !shuffleEnabled
    if (shuffleEnabled) {
        playQueue.shuffle()
    } else {
        playQueue.unshuffle()
    }
    runOnControllerThread {
        emitState()
        emitQueueState()
    }
}
```

`addToQueue`:

```kotlin
fun addToQueue(item: MusicItem) {
    playQueue.add(item)
    emitQueueState()
}
```

`addNextInQueue`:

```kotlin
fun addNextInQueue(item: MusicItem) {
    playQueue.addNext(item)
    emitQueueState()
}
```

`reset` — inside the existing `runOnControllerThread { ... }`, after `_playerState.value = PlayerState.EMPTY`:

```kotlin
fun reset() {
    runOnControllerThread {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        mediaController?.stop()
        mediaController?.clearMediaItems()
        playQueue.clear()
        repeatMode = RepeatMode.OFF
        shuffleEnabled = false
        _playerState.value = PlayerState.EMPTY
        emitQueueState()
    }
}
```

`removeFromQueue` — at the end after the existing `runOnControllerThread { emitState() }`:

```kotlin
fun removeFromQueue(index: Int): MusicItem? {
    val wasCurrentItem = playQueue.currentItem
    val newCurrent = playQueue.remove(index)
    if (newCurrent != null && newCurrent != wasCurrentItem) {
        setMediaItemAndPlay(newCurrent)
    } else if (newCurrent == null) {
        runOnControllerThread {
            mediaController?.stop()
            mediaController?.clearMediaItems()
        }
    }
    runOnControllerThread {
        emitState()
        emitQueueState()
    }
    return newCurrent
}
```

`moveInQueue`:

```kotlin
fun moveInQueue(fromIndex: Int, toIndex: Int) {
    playQueue.move(fromIndex, toIndex)
    emitQueueState()
}
```

`handleTrackEnded` — only the OFF branch when `next` is non-null already triggers via `setMediaItemAndPlay` which doesn't emit; add an explicit emit after the call:

```kotlin
private fun handleTrackEnded() {
    when (repeatMode) {
        RepeatMode.ONE -> {
            mediaController?.seekTo(0L)
            mediaController?.play()
        }
        RepeatMode.ALL -> skipToNext()
        RepeatMode.OFF -> {
            val next = playQueue.next(repeatMode)
            if (next != null) {
                setMediaItemAndPlay(next)
                emitQueueState()
            }
        }
    }
}
```

- [ ] **Step 4: Run the full test class, expect PASS**

Run: `./gradlew :player:testDebugUnitTest --tests com.zili.android.musicfreeandroid.player.controller.PlayerControllerQueueStateTest`
Expected: PASS (13 tests).

Also run regression to make sure existing controller tests still pass:

Run: `./gradlew :player:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt
git commit -m "feat(player): emit queueState from queue-mutating PlayerController methods"
```

---

## Task 4: Add `PlayQueueUiModel`

**Files:**
- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueUiModel.kt`

- [ ] **Step 1: Create the directory and data class**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode

data class PlayQueueUiModel(
    val items: List<MusicItem>,
    val currentIndex: Int,
    val repeatMode: RepeatMode,
) {
    val count: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val EMPTY = PlayQueueUiModel(
            items = emptyList(),
            currentIndex = -1,
            repeatMode = RepeatMode.OFF,
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :feature:player-ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueUiModel.kt
git commit -m "feat(player-ui): add PlayQueueUiModel"
```

---

## Task 5: Extend `PlayerViewModel` with queue-related API

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelQueueTest.kt`

- [ ] **Step 1: Write the failing test**

Create `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelQueueTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueUiModel
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricLoadState
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricLoader
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelQueueTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playerController: PlayerController = mock()
    private val playlistRepository: PlaylistRepository = mock()
    private val playerLyricLoader: PlayerLyricLoader = mock()
    private val appPreferences: AppPreferences = mock()
    private val playerStateFlow = MutableStateFlow(PlayerState.EMPTY)
    private val queueStateFlow = MutableStateFlow(PlayQueueSnapshot.EMPTY)
    private val lyricShowTranslationFlow = MutableStateFlow(false)
    private val lyricDetailFontSizeFlow = MutableStateFlow(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerState).thenReturn(playerStateFlow)
        whenever(playerController.queueState).thenReturn(queueStateFlow)
        whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
        whenever(playerLyricLoader.observeLyrics(anyOrNull()))
            .thenReturn(flowOf(LyricLoadState.NoTrack))
        whenever(appPreferences.lyricShowTranslation).thenReturn(lyricShowTranslationFlow)
        whenever(appPreferences.lyricDetailFontSize).thenReturn(lyricDetailFontSizeFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlayerViewModel(
        playerController,
        playlistRepository,
        playerLyricLoader,
        appPreferences,
    )

    private fun item(id: String) = MusicItem(
        id = id, platform = "test", title = "Song $id",
        artist = "Artist", album = null, duration = 1_000L,
        url = null, artwork = null, qualities = null,
    )

    @Test
    fun `queueUiModel initial value is EMPTY`() = runTest(testDispatcher) {
        val vm = viewModel()
        // Trigger collection to start the stateIn pipeline.
        val collector = launchCollect(vm.queueUiModel)
        advanceUntilIdle()
        assertEquals(PlayQueueUiModel.EMPTY, vm.queueUiModel.value)
        collector.cancel()
    }

    @Test
    fun `queueUiModel reflects queueState items and currentIndex`() = runTest(testDispatcher) {
        val vm = viewModel()
        val collector = launchCollect(vm.queueUiModel)
        val items = listOf(item("1"), item("2"))
        queueStateFlow.value = PlayQueueSnapshot(items = items, currentIndex = 1)
        advanceUntilIdle()
        val ui = vm.queueUiModel.value
        assertEquals(items, ui.items)
        assertEquals(1, ui.currentIndex)
        assertEquals(2, ui.count)
        collector.cancel()
    }

    @Test
    fun `queueUiModel includes repeatMode from playerState`() = runTest(testDispatcher) {
        val vm = viewModel()
        val collector = launchCollect(vm.queueUiModel)
        playerStateFlow.value = PlayerState.EMPTY.copy(repeatMode = RepeatMode.ONE)
        advanceUntilIdle()
        assertEquals(RepeatMode.ONE, vm.queueUiModel.value.repeatMode)
        collector.cancel()
    }

    @Test
    fun `playQueueIndex delegates to PlayerController_skipTo`() {
        viewModel().playQueueIndex(2)
        verify(playerController).skipTo(eq(2))
    }

    @Test
    fun `removeFromQueue delegates to PlayerController_removeFromQueue`() {
        viewModel().removeFromQueue(1)
        verify(playerController).removeFromQueue(eq(1))
    }

    @Test
    fun `clearQueue delegates to PlayerController_reset`() {
        viewModel().clearQueue()
        verify(playerController).reset()
    }

    private fun <T> kotlinx.coroutines.test.TestScope.launchCollect(
        flow: kotlinx.coroutines.flow.StateFlow<T>,
    ) = kotlinx.coroutines.launch { flow.collect { } }
}
```

- [ ] **Step 2: Run the test, expect failures**

Run: `./gradlew :feature:player-ui:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModelQueueTest`
Expected: FAILED — `unresolved reference: queueState` and friends.

- [ ] **Step 3: Wire `queueUiModel` and forwarders into `PlayerViewModel`**

In `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`:

Add imports near the top:

```kotlin
import com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueUiModel
```

Add the following section right after the existing `val playerState: StateFlow<PlayerState> = ...` line (around line 47):

```kotlin
val queueUiModel: StateFlow<PlayQueueUiModel> = combine(
    playerController.queueState,
    playerState,
) { snapshot, player ->
    PlayQueueUiModel(
        items = snapshot.items,
        currentIndex = snapshot.currentIndex,
        repeatMode = player.repeatMode,
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayQueueUiModel.EMPTY)
```

In the existing `// ---- playback controls ----` section, alongside `togglePlayPause`/`skipToNext`/etc., add:

```kotlin
fun playQueueIndex(index: Int) = playerController.skipTo(index)

fun removeFromQueue(index: Int) {
    playerController.removeFromQueue(index)
}

fun clearQueue() = playerController.reset()
```

- [ ] **Step 4: Run the test, expect PASS**

Run: `./gradlew :feature:player-ui:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModelQueueTest`
Expected: PASS (6 tests).

Regression: `./gradlew :feature:player-ui:testDebugUnitTest` — BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt \
        feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelQueueTest.kt
git commit -m "feat(player-ui): expose queueUiModel and queue forwarders on PlayerViewModel"
```

---

## Task 6: Add FidelityAnchors keys for the queue sheet

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`

We need anchors to drive Compose tests in Task 7/8.

- [ ] **Step 1: Extend `FidelityAnchors.Player`**

Replace the existing `object Player { ... }` block (lines 54-58) with:

```kotlin
    object Player {
        const val MiniRoot = "player.mini.root"
        const val MiniPlayPause = "player.mini.playPause"
        const val MiniQueue = "player.mini.queue"

        object Queue {
            const val SheetRoot = "player.queue.root"
            const val RepeatModeButton = "player.queue.repeatMode"
            const val ClearButton = "player.queue.clear"
            const val EmptyState = "player.queue.empty"
            const val Row = "player.queue.row"
            const val CurrentMarker = "player.queue.currentMarker"
            const val RemoveButton = "player.queue.removeButton"
        }
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt
git commit -m "feat(core): add FidelityAnchors for play queue sheet"
```

---

## Task 7: Implement `PlayQueueRow`

**Files:**
- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueRow.kt`

This is the row composable. It will be tested as part of `PlayQueueSheetContent` in Task 8 (rendering individual rows in isolation is overkill).

- [ ] **Step 1: Create the row composable**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.PlatformTag

@Composable
internal fun PlayQueueRow(
    item: MusicItem,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MusicFreeTheme.colors.textHighlight
    val titleColor = if (isCurrent) highlightColor else MusicFreeTheme.colors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(108))
            .clickable(onClick = onPlay)
            .padding(horizontal = rpx(24))
            .testTag(FidelityAnchors.Player.Queue.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = highlightColor,
                modifier = Modifier
                    .size(rpx(28))
                    .testTag(FidelityAnchors.Player.Queue.CurrentMarker),
            )
            Spacer(Modifier.width(rpx(6)))
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title,
                color = titleColor,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = " - ${item.artist}",
                    color = titleColor,
                    fontSize = FontSizes.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (item.platform.isNotBlank()) {
            Spacer(Modifier.width(rpx(8)))
            PlatformTag(text = item.platform)
        }
        Spacer(Modifier.width(rpx(14)))
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag(FidelityAnchors.Player.Queue.RemoveButton),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除",
                tint = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(rpx(36)),
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :feature:player-ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueRow.kt
git commit -m "feat(player-ui): add PlayQueueRow composable"
```

---

## Task 8: Implement `PlayQueueSheetContent` + Compose tests

**Files:**
- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheetContent.kt`
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheetContentTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayQueueSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun item(id: String, title: String = "Song $id", artist: String = "Artist") =
        MusicItem(
            id = id, platform = "test", title = title, artist = artist,
            album = null, duration = 1_000L, url = null, artwork = null, qualities = null,
        )

    private fun ui(
        items: List<MusicItem> = listOf(item("1"), item("2"), item("3")),
        currentIndex: Int = 1,
        repeatMode: RepeatMode = RepeatMode.OFF,
    ) = PlayQueueUiModel(items = items, currentIndex = currentIndex, repeatMode = repeatMode)

    @Test
    fun `header shows count`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(),
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onNodeWithText("播放列表 ").assertIsDisplayed()
        composeRule.onNodeWithText("(3首)").assertIsDisplayed()
    }

    @Test
    fun `current row renders the current marker`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(currentIndex = 0),
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.Queue.CurrentMarker)
            .assertCountEquals(1)
    }

    @Test
    fun `clicking a row calls onPlayIndex with row index`() {
        var clickedIndex = -1
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(currentIndex = 0),
                    onPlayIndex = { clickedIndex = it },
                    onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.Queue.Row)[1].performClick()
        composeRule.runOnIdle { assertEquals(1, clickedIndex) }
    }

    @Test
    fun `clicking remove on a row calls onRemove with that index`() {
        var removedIndex = -1
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(currentIndex = 0),
                    onPlayIndex = {}, onRemove = { removedIndex = it },
                    onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.Queue.RemoveButton)[2].performClick()
        composeRule.runOnIdle { assertEquals(2, removedIndex) }
    }

    @Test
    fun `clicking clear button calls onClear`() {
        var clearCount = 0
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(),
                    onPlayIndex = {}, onRemove = {},
                    onClear = { clearCount++ }, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.Player.Queue.ClearButton).performClick()
        composeRule.runOnIdle { assertEquals(1, clearCount) }
    }

    @Test
    fun `clicking repeat mode button calls onCycleRepeatMode`() {
        var cycleCount = 0
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(),
                    onPlayIndex = {}, onRemove = {}, onClear = {},
                    onCycleRepeatMode = { cycleCount++ },
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.Player.Queue.RepeatModeButton).performClick()
        composeRule.runOnIdle { assertEquals(1, cycleCount) }
    }

    @Test
    fun `repeat mode label updates with mode`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(repeatMode = RepeatMode.ONE),
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithText("单曲循环").onFirst().assertIsDisplayed()
    }

    @Test
    fun `empty state renders placeholder`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = PlayQueueUiModel.EMPTY,
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.Player.Queue.EmptyState).assertIsDisplayed()
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.Queue.Row).assertCountEquals(0)
    }
}
```

- [ ] **Step 2: Run the test, expect failures**

Run: `./gradlew :feature:player-ui:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueSheetContentTest`
Expected: FAILED — `unresolved reference: PlayQueueSheetContent`.

- [ ] **Step 3: Implement `PlayQueueSheetContent`**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

@Composable
fun PlayQueueSheetContent(
    uiModel: PlayQueueUiModel,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag(FidelityAnchors.Player.Queue.SheetRoot),
    ) {
        QueueHeader(
            count = uiModel.count,
            repeatMode = uiModel.repeatMode,
            onCycleRepeatMode = onCycleRepeatMode,
            onClear = onClear,
        )
        if (uiModel.isEmpty) {
            QueueEmptyState()
        } else {
            QueueList(
                uiModel = uiModel,
                onPlayIndex = onPlayIndex,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun QueueHeader(
    count: Int,
    repeatMode: RepeatMode,
    onCycleRepeatMode: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(80))
            .padding(horizontal = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "播放列表 ",
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "(${count}首)",
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.title,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconTextButton(
            iconPainter = painterResource(repeatModeIcon(repeatMode)),
            label = repeatModeLabel(repeatMode),
            onClick = onCycleRepeatMode,
            modifier = Modifier.testTag(FidelityAnchors.Player.Queue.RepeatModeButton),
        )
        Spacer(Modifier.size(rpx(16)))
        IconTextButton(
            icon = Icons.Outlined.DeleteOutline,
            label = "清空",
            onClick = onClear,
            modifier = Modifier.testTag(FidelityAnchors.Player.Queue.ClearButton),
        )
    }
}

@Composable
private fun QueueList(
    uiModel: PlayQueueUiModel,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(uiModel.items.size, uiModel.currentIndex) {
        if (!didInitialScroll &&
            uiModel.items.isNotEmpty() &&
            uiModel.currentIndex >= 0
        ) {
            listState.scrollToItem(uiModel.currentIndex)
            didInitialScroll = true
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(uiModel.items, key = { _, item -> "${item.platform}:${item.id}" }) { index, item ->
            PlayQueueRow(
                item = item,
                isCurrent = index == uiModel.currentIndex,
                onPlay = { onPlayIndex(index) },
                onRemove = { onRemove(index) },
            )
        }
    }
}

@Composable
private fun QueueEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rpx(120))
            .testTag(FidelityAnchors.Player.Queue.EmptyState),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "暂无歌曲",
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.content,
        )
    }
}

@Composable
private fun IconTextButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = rpx(8), vertical = rpx(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(rpx(36)),
            )
            iconPainter != null -> Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(rpx(36)),
            )
        }
        Spacer(Modifier.size(rpx(4)))
        Text(
            text = label,
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.description,
        )
    }
}

private fun repeatModeIcon(mode: RepeatMode): Int = when (mode) {
    RepeatMode.OFF, RepeatMode.ALL -> R.drawable.ic_repeat_song
    RepeatMode.ONE -> R.drawable.ic_repeat_song_1
}

private fun repeatModeLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF -> "顺序播放"
    RepeatMode.ALL -> "列表循环"
    RepeatMode.ONE -> "单曲循环"
}
```

- [ ] **Step 4: Run the tests, expect PASS**

Run: `./gradlew :feature:player-ui:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueSheetContentTest`
Expected: PASS (8 tests).

Regression: `./gradlew :feature:player-ui:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheetContent.kt \
        feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheetContentTest.kt
git commit -m "feat(player-ui): add PlayQueueSheetContent with compose tests"
```

---

## Task 9: Implement `PlayQueueSheet` wrapper

**Files:**
- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheet.kt`

This is the thin Hilt-aware bottom-sheet wrapper around `PlayQueueSheetContent`. No new tests — its glue logic is exercised end-to-end by manual verification.

- [ ] **Step 1: Implement the wrapper**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayQueueSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val uiModel by viewModel.queueUiModel.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PlayQueueSheetContent(
            uiModel = uiModel,
            onPlayIndex = viewModel::playQueueIndex,
            onRemove = viewModel::removeFromQueue,
            onClear = viewModel::clearQueue,
            onCycleRepeatMode = viewModel::cycleRepeatMode,
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :feature:player-ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/queue/PlayQueueSheet.kt
git commit -m "feat(player-ui): add PlayQueueSheet ModalBottomSheet wrapper"
```

---

## Task 10: Wire `PlayQueueSheet` into MiniPlayer (drop legacy parameter)

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayer.kt`
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayerContentTest.kt` (only if it breaks)
- Indirect: confirm `MainActivity.kt` doesn't pass `onNavigateToQueue` (it currently doesn't — see lines 110-114).

- [ ] **Step 1: Update `MiniPlayer.kt`**

Replace the entire body of `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayer.kt` with:

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModel
import com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueSheet
import com.zili.android.musicfreeandroid.player.model.PlayerState

internal fun PlayerState.toMiniPlayerUiModel(): MiniPlayerUiModel = MiniPlayerUiModel(
    coverUri = currentItem?.artwork,
    title = currentItem?.title ?: "",
    artist = currentItem?.artist ?: "",
    isPlaying = isPlaying,
    progress = if (duration > 0L) position.toFloat() / duration else 0f,
    hasPrev = true,
    hasNext = true,
    prevTitle = null,
    nextTitle = null,
)

@Composable
fun MiniPlayer(
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()

    if (!state.hasMedia) return

    var showQueueSheet by remember { mutableStateOf(false) }

    MiniPlayerContent(
        uiModel = state.toMiniPlayerUiModel(),
        onOpenPlayer = onNavigateToPlayer,
        onTogglePlayPause = viewModel::togglePlayPause,
        onOpenQueue = { showQueueSheet = true },
        onSkipNext = {},
        onSkipPrev = {},
        modifier = modifier,
    )

    if (showQueueSheet) {
        PlayQueueSheet(
            viewModel = viewModel,
            onDismiss = { showQueueSheet = false },
        )
    }
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew :feature:player-ui:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

If the build fails complaining about `MiniPlayer(onNavigateToQueue = ...)` callers in `:app`, search and fix:

```bash
grep -rn "onNavigateToQueue" app/src/main/
```

There should be no callers (confirmed: `app/.../MainActivity.kt:110` only passes `onNavigateToPlayer`). If any are found, simply delete them.

- [ ] **Step 3: Run unit tests for `:feature:player-ui` and `:app`**

Run: `./gradlew :feature:player-ui:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayer.kt
git commit -m "feat(player-ui): wire PlayQueueSheet into MiniPlayer"
```

---

## Task 11: Wire `PlayQueueSheet` into PlayerScreen

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`

- [ ] **Step 1: Add the import**

In the `import` block of `PlayerScreen.kt`, add:

```kotlin
import com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueSheet
```

- [ ] **Step 2: Add `showQueueSheet` state**

Inside `PlayerScreen` near the existing state declarations (around lines 88-92, alongside `var contentPage`, `showLyricSearchSheet`, etc.), add:

```kotlin
var showQueueSheet by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Pass `onOpenQueue` into `PlayerControls`**

Update the `PlayerControls(...)` call (around lines 225-234) to pass a new callback. First, change the `PlayerControls` function signature (at the bottom of the file, around line 587):

```kotlin
@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenQueue: () -> Unit,
)
```

Replace the existing TODO `IconButton(onClick = { /* TODO: 弹出队列 */ })` (around line 652) with:

```kotlin
IconButton(onClick = onOpenQueue) {
    Icon(
        painter = painterResource(R.drawable.ic_playlist),
        contentDescription = "播放列表",
        tint = Color.White,
        modifier = Modifier.size(rpx(56)),
    )
}
```

And wire the call site (around line 225):

```kotlin
PlayerControls(
    isPlaying = state.isPlaying,
    repeatMode = state.repeatMode,
    shuffleEnabled = state.shuffleEnabled,
    onTogglePlayPause = { viewModel.togglePlayPause() },
    onSkipPrevious = { viewModel.skipToPrevious() },
    onSkipNext = { viewModel.skipToNext() },
    onCycleRepeatMode = { viewModel.cycleRepeatMode() },
    onToggleShuffle = { viewModel.toggleShuffle() },
    onOpenQueue = { showQueueSheet = true },
)
```

- [ ] **Step 4: Render the sheet**

At the end of the outer `Box(modifier = Modifier.fillMaxSize())` block (after the existing `if (showLyricMoreDialog) { ... }` block, around line 309), add:

```kotlin
if (showQueueSheet) {
    PlayQueueSheet(
        viewModel = viewModel,
        onDismiss = { showQueueSheet = false },
    )
}
```

- [ ] **Step 5: Run module tests + build**

Run: `./gradlew :feature:player-ui:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt
git commit -m "feat(player-ui): wire PlayQueueSheet into PlayerScreen"
```

---

## Task 12: Manual verification & finishing

**Files:** none.

- [ ] **Step 1: Install Debug APK on a device/emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, app installed.

- [ ] **Step 2: Run the seven-step manual verification (from spec §测试计划 → 手动验收)**

1. With a populated queue, tap the queue icon on the mini player → sheet opens, list scrolled to the current song; header reads `(N首)`.
2. Tap a non-current row → playback switches to that song; sheet stays open; current marker / highlight color migrates.
3. Tap the × on a non-current row → row disappears, count decrements. Tap × on the current row → playback advances to the next item, current marker migrates.
4. Tap "清空" → list shows "暂无歌曲", playback stops, mini player disappears (because `hasMedia = false`).
5. Tap the repeat-mode button three times → label cycles 顺序播放 → 列表循环 → 单曲循环 → 顺序播放. Verify each mode behaves correctly when songs end.
6. Open `PlayerScreen` (tap mini player), tap its bottom-right playlist icon → same sheet appears with same state.
7. Dismiss paths (tap scrim, swipe down, system back) all close the sheet cleanly.

Record any issue and fix before continuing.

- [ ] **Step 3: Run the full test suite as a final gate**

Run: `./gradlew :player:testDebugUnitTest :feature:player-ui:testDebugUnitTest :core:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run `:app:assembleDebug` once more**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Final commit (only if any fix-ups landed)**

If steps 2-4 caused additional code changes:

```bash
git status
git add -p
git commit -m "fix(player-ui): adjustments after manual verification"
```

If everything was already green and no extra commits are needed, skip.

- [ ] **Step 6: Hand off to `superpowers:finishing-a-development-branch`**

The implementation phase is done. Trigger the finishing skill to decide on merge / PR / cleanup.

---

## Self-review checklist (do not skip)

- Spec §架构 / 数据流 → covered by Tasks 2, 3, 5.
- Spec §UI 规格 (header, list, row, empty state) → covered by Tasks 7, 8.
- Spec §触发点改动 → covered by Tasks 10, 11.
- Spec §错误处理 → covered by Task 3 (越界 / 空队列) and the `if (uiModel.isEmpty)` branch in Task 8.
- Spec §测试计划 (PlayerControllerQueueStateTest, PlayerViewModelQueueTest, PlayQueueSheetContentTest, 7 manual checks) → covered by Tasks 3, 5, 8, 12.
- Spec §实施顺序 (1→2→3→4→5→6→7) → matches Tasks 1-12 (ordered, with finer granularity).
- Spec §已知风险 → addressed: emit hookups exhaustively listed in Task 3; scrollToItem race guarded by `didInitialScroll` in Task 8.

No placeholders. All exact paths and code blocks present. Method names consistent across tasks (`queueUiModel`, `playQueueIndex`, `removeFromQueue`, `clearQueue`, `cycleRepeatMode`).
