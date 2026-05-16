# 冷启动播放状态恢复 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 mini player 冷启动后记住上次播放位置；点击播放按钮能从该位置继续播放。

**Architecture:** Restore 阶段（启动 IO 流）从 `AppPreferences` 读队列索引 + position + duration，传入 `PlayerController.restoreQueue` 写入内存与 `_playerState`，但不准备 MediaController（懒准备）。Activate 阶段（用户点 play）由 `PlayerController.play()` 检测 pending 并触发 `setMediaItemAndPlay`，STATE_READY 监听器消费一次 `pendingRestorePosition` 完成 seek。稳态阶段 coordinator 每 5s + 关键事件把 position/duration 写回 `AppPreferences`；`PlaybackService.onTaskRemoved/onDestroy` 做 best-effort flush。

**Tech Stack:** Kotlin coroutines + Flow、Media3、Hilt、DataStore（Preferences）、Robolectric + mockito-kotlin、JUnit4。

**Spec:** [`docs/superpowers/specs/2026-05-17-playback-state-cold-restore-design.md`](../specs/2026-05-17-playback-state-cold-restore-design.md)

---

## 文件结构

| 路径 | 状态 | 责任 |
|---|---|---|
| `data/.../datastore/AppPreferences.kt` | 修改 | 新增 `currentMusicPositionMs` / `currentMusicDurationMs` Flow + setter + KEY 常量 |
| `data/.../datastore/AppPreferencesTest.kt` | 修改 | 验证两个新 key 的默认值 + 往返 |
| `player/.../controller/PlayerController.kt` | 修改 | `restoreQueue` 签名扩展、`pendingRestorePosition` 状态机、`play()` 懒激活、STATE_READY 消费、`seekTo` 前置语义、所有切歌入口清零 |
| `player/.../controller/PlayerControllerQueueStateTest.kt` | 修改 | 覆盖 restoreQueue+savedPosition / 清零入口 |
| `player/.../controller/PlayerControllerLazyRestoreTest.kt` | 新建 | 单独文件覆盖懒激活 / STATE_READY 消费 / seek 前置 |
| `app/.../bootstrap/PlaybackStartupCoordinator.kt` | 修改 | 读 position+duration 传入 restoreQueue；新增 savePositionLoop |
| `app/.../bootstrap/PlaybackStartupCoordinatorTest.kt` | 新建 | 覆盖 restore + save loop 行为 |
| `player/.../service/PlaybackService.kt` | 修改 | 注入 AppPreferences；onTaskRemoved/onDestroy 做 flushLastPosition |
| `player/.../service/PlaybackServiceFlushTest.kt` | 新建 | 单元覆盖 flushLastPosition 的输入/输出 |
| `feature/player-ui/.../component/MiniPlayerContentTest.kt` 或同目录测试 | 修改 | 新增"restored state 渲染上次进度"用例（如已有 helper 则复用） |

---

## Task 1：AppPreferences 新增 `currentMusicPositionMs`

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferencesTest.kt`

- [ ] **Step 1: 写失败用例**

在 `AppPreferencesTest.kt` 现有 `@Test` 块尾部追加：

```kotlin
@Test
fun `default currentMusicPositionMs is 0`() = testScope.runTest {
    assertEquals(0L, prefs.currentMusicPositionMs.first())
}

@Test
fun `set and get currentMusicPositionMs`() = testScope.runTest {
    prefs.setCurrentMusicPositionMs(123_456L)
    assertEquals(123_456L, prefs.currentMusicPositionMs.first())
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :data:testDebugUnitTest --tests "*AppPreferencesTest.default currentMusicPositionMs is 0" --no-daemon`
Expected: 编译失败 — `prefs.currentMusicPositionMs` / `setCurrentMusicPositionMs` 未定义。

- [ ] **Step 3: 实现**

在 `AppPreferences.kt` 中 `currentMusicIndex` 块之后插入：

```kotlin
val currentMusicPositionMs: Flow<Long> = dataStore.data.map { prefs ->
    prefs[KEY_CURRENT_MUSIC_POSITION_MS] ?: 0L
}

suspend fun setCurrentMusicPositionMs(positionMs: Long) {
    dataStore.edit { it[KEY_CURRENT_MUSIC_POSITION_MS] = positionMs.coerceAtLeast(0L) }
}
```

在 `private companion object { ... }` 内 `KEY_CURRENT_MUSIC_INDEX` 这一行下方加：

```kotlin
val KEY_CURRENT_MUSIC_POSITION_MS = longPreferencesKey("current_music_position_ms")
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :data:testDebugUnitTest --tests "*AppPreferencesTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt \
        data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferencesTest.kt
git -c commit.gpgsign=false commit -m "feat(data): 新增 currentMusicPositionMs 偏好"
```

---

## Task 2：AppPreferences 新增 `currentMusicDurationMs`

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferencesTest.kt`

- [ ] **Step 1: 写失败用例**

追加到 `AppPreferencesTest.kt`：

```kotlin
@Test
fun `default currentMusicDurationMs is 0`() = testScope.runTest {
    assertEquals(0L, prefs.currentMusicDurationMs.first())
}

@Test
fun `set and get currentMusicDurationMs`() = testScope.runTest {
    prefs.setCurrentMusicDurationMs(987_654L)
    assertEquals(987_654L, prefs.currentMusicDurationMs.first())
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :data:testDebugUnitTest --tests "*AppPreferencesTest.default currentMusicDurationMs is 0" --no-daemon`
Expected: 编译失败。

- [ ] **Step 3: 实现**

在 `AppPreferences.kt` 中 `currentMusicPositionMs` 块之后插入：

```kotlin
val currentMusicDurationMs: Flow<Long> = dataStore.data.map { prefs ->
    prefs[KEY_CURRENT_MUSIC_DURATION_MS] ?: 0L
}

suspend fun setCurrentMusicDurationMs(durationMs: Long) {
    dataStore.edit { it[KEY_CURRENT_MUSIC_DURATION_MS] = durationMs.coerceAtLeast(0L) }
}
```

在 companion object 内追加：

```kotlin
val KEY_CURRENT_MUSIC_DURATION_MS = longPreferencesKey("current_music_duration_ms")
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :data:testDebugUnitTest --tests "*AppPreferencesTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt \
        data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferencesTest.kt
git -c commit.gpgsign=false commit -m "feat(data): 新增 currentMusicDurationMs 偏好"
```

---

## Task 3：PlayerController.restoreQueue 接受 savedPositionMs/savedDurationMs（懒分支）

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt`

- [ ] **Step 1: 写失败用例**

追加到 `PlayerControllerQueueStateTest.kt`：

```kotlin
@Test
fun `restoreQueue with savedPosition and savedDuration emits PlayerState with both values`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        val items = listOf(item("1"), item("2"), item("3"))
        controller.restoreQueue(
            items = items,
            startIndex = 1,
            savedPositionMs = 42_000L,
            savedDurationMs = 180_000L,
            playWhenRestored = false,
        )
        val playerState = controller.playerState.value
        assertEquals(item("2"), playerState.currentItem)
        assertEquals(42_000L, playerState.position)
        assertEquals(180_000L, playerState.duration)
        assertEquals(false, playerState.isPlaying)
    } finally {
        controller.release()
    }
}

@Test
fun `restoreQueue with savedPosition zero leaves player position at 0`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 0L,
            savedDurationMs = 0L,
            playWhenRestored = false,
        )
        assertEquals(0L, controller.playerState.value.position)
        assertEquals(0L, controller.playerState.value.duration)
    } finally {
        controller.release()
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerQueueStateTest.restoreQueue with savedPosition*" --no-daemon`
Expected: 编译失败 — `restoreQueue` 没有 `savedPositionMs / savedDurationMs` 参数。

- [ ] **Step 3: 实现**

在 `PlayerController.kt` 中：

1. 在 `currentPlayQuality` 字段下方新增字段：

```kotlin
@Volatile
private var pendingRestorePosition: Long? = null
```

2. 把 `fun restoreQueue(...)`（约 221 行）替换为：

```kotlin
fun restoreQueue(
    items: List<MusicItem>,
    startIndex: Int = 0,
    savedPositionMs: Long = 0L,
    savedDurationMs: Long = 0L,
    playWhenRestored: Boolean = false,
) {
    playQueue.setQueue(items, startIndex)
    pendingRestorePosition = savedPositionMs.takeIf { it > 0L }
    if (playWhenRestored) {
        playQueue.currentItem?.let { setMediaItemAndPlay(it) }
    } else {
        runOnControllerThread {
            _playerState.value = PlayerState(
                currentItem = playQueue.currentItem,
                isPlaying = false,
                playbackState = mediaController?.playbackState.toPlaybackState(),
                duration = savedDurationMs.coerceAtLeast(0L),
                position = savedPositionMs.coerceAtLeast(0L),
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                playbackSpeed = playbackSpeed,
            )
            emitQueueState()
        }
    }
    emitQueueState()
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerQueueStateTest*" --no-daemon`
Expected: 全绿（含新增 2 条与旧的 `restoreQueue emits snapshot without starting playback when autoplay is false`）。

- [ ] **Step 5: 提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerQueueStateTest.kt
git -c commit.gpgsign=false commit -m "feat(player): restoreQueue 接受持久化的位置与时长"
```

---

## Task 4：PlayerController.play() 懒激活分支

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt` (new)

- [ ] **Step 1: 新建测试文件，写失败用例**

新建 `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.listening.ListenTracker
import com.zili.android.musicfreeandroid.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerLazyRestoreTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `play after lazy restoreQueue keeps pendingRestorePosition set`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1"), item("2")),
                startIndex = 0,
                savedPositionMs = 30_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            // controller.play() should be a no-op against the (unconnected) MediaController
            // but must NOT clear pendingRestorePosition.
            controller.play()
            assertEquals(30_000L, controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `user-initiated skipToNext clears pendingRestorePosition`() {
        val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
        try {
            controller.restoreQueue(
                items = listOf(item("1"), item("2")),
                startIndex = 0,
                savedPositionMs = 30_000L,
                savedDurationMs = 60_000L,
                playWhenRestored = false,
            )
            controller.skipToNext()
            assertNull(controller.pendingRestorePositionForTest)
        } finally {
            controller.release()
        }
    }

    private fun item(id: String) = MusicItem(
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

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest*" --no-daemon`
Expected: 编译失败 — `pendingRestorePositionForTest` 未定义。

- [ ] **Step 3: 实现**

在 `PlayerController.kt` 中：

1. 在 `pendingRestorePosition` 字段附近暴露测试可见 getter：

```kotlin
@VisibleForTesting
internal val pendingRestorePositionForTest: Long?
    get() = pendingRestorePosition
```

2. 修改 `fun play()`（约 175 行）：

```kotlin
fun play() {
    withConnectedController { controller ->
        val pending = pendingRestorePosition
        if (pending != null && controller.currentMediaItem == null) {
            playQueue.currentItem?.let { setMediaItemAndPlay(it) }
        } else {
            controller.play()
        }
    }
}
```

3. 在 `skipToNext`、`skipToPrevious`、`skipTo`、`playItem`、`playQueue`、`reset` 入口加一行清零（位置见 §Task 7 集中处理，这里**仅在 `skipToNext` 顶部加**一行作为最小可通过实现）：

```kotlin
fun skipToNext() {
    pendingRestorePosition = null
    // … existing body
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest*" --tests "*PlayerControllerQueueStateTest*" --no-daemon`
Expected: 全绿。`PlayerControllerLazyRestoreTest.play after lazy restoreQueue keeps pendingRestorePosition set` 通过的关键是：在单元测试里 `withConnectedController` 因 `mediaController` 为 null 直接 no-op，不会进入 if 分支，但也不会清掉 `pendingRestorePosition`。

- [ ] **Step 5: 提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt
git -c commit.gpgsign=false commit -m "feat(player): play() 检测 pendingRestorePosition 触发懒激活"
```

---

## Task 5：STATE_READY 消费 pendingRestorePosition

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt`

- [ ] **Step 1: 写失败用例**

在 `PlayerControllerLazyRestoreTest.kt` 中追加：

```kotlin
@Test
fun `consumePendingRestoreForTest seeks to pending and clears it`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 45_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        var lastSeek: Long? = null
        controller.consumePendingRestoreForTest(durationMs = 60_000L) { target ->
            lastSeek = target
        }
        assertEquals(45_000L, lastSeek)
        assertNull(controller.pendingRestorePositionForTest)
    } finally {
        controller.release()
    }
}

@Test
fun `consumePendingRestoreForTest coerces pending into [0, duration]`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 999_999L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        var lastSeek: Long? = null
        controller.consumePendingRestoreForTest(durationMs = 50_000L) { target ->
            lastSeek = target
        }
        assertEquals(50_000L, lastSeek)
    } finally {
        controller.release()
    }
}

@Test
fun `consumePendingRestoreForTest second call after consumption is no-op`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 10_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        var seekCount = 0
        controller.consumePendingRestoreForTest(durationMs = 60_000L) { seekCount++ }
        controller.consumePendingRestoreForTest(durationMs = 60_000L) { seekCount++ }
        assertEquals(1, seekCount)
    } finally {
        controller.release()
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest.consumePendingRestoreForTest*" --no-daemon`
Expected: 编译失败 — `consumePendingRestoreForTest` 未定义。

- [ ] **Step 3: 实现**

在 `PlayerController.kt` 中新增一个内部 helper，并把它嵌入到 `playerListener.onPlaybackStateChanged` 中。

在 `playerListener` 定义附近增加：

```kotlin
private fun consumePendingRestoreIfReady(controllerDurationMs: Long, seek: (Long) -> Unit) {
    val pending = pendingRestorePosition ?: return
    val upper = if (controllerDurationMs > 0L) controllerDurationMs else pending
    val target = pending.coerceIn(0L, upper)
    pendingRestorePosition = null
    seek(target)
}

@VisibleForTesting
internal fun consumePendingRestoreForTest(durationMs: Long, seek: (Long) -> Unit) {
    consumePendingRestoreIfReady(durationMs, seek)
}
```

修改 `playerListener.onPlaybackStateChanged`：

```kotlin
override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_ENDED) {
        listenTracker.onTrackEnded(playQueue.currentItem)
        handleTrackEnded()
    }
    if (playbackState == Player.STATE_READY) {
        val controllerRef = mediaController
        if (controllerRef != null) {
            consumePendingRestoreIfReady(controllerRef.duration) { target ->
                controllerRef.seekTo(target)
            }
        }
    }
    emitState()
    updatePositionTracking()
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt
git -c commit.gpgsign=false commit -m "feat(player): STATE_READY 消费 pendingRestorePosition 完成 seek"
```

---

## Task 6：seekTo 前置语义（未激活时仅更新 pending 与 UI）

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt`

- [ ] **Step 1: 写失败用例**

在 `PlayerControllerLazyRestoreTest.kt` 追加：

```kotlin
@Test
fun `seekTo before activation updates pendingRestorePosition and playerState position`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 10_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        controller.seekTo(25_000L)
        assertEquals(25_000L, controller.pendingRestorePositionForTest)
        assertEquals(25_000L, controller.playerState.value.position)
    } finally {
        controller.release()
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest.seekTo before activation*" --no-daemon`
Expected: 失败 — pending 仍为 10_000L 或 playerState.position 仍为 10_000L。

- [ ] **Step 3: 实现**

修改 `fun seekTo(positionMs: Long)`（约 187 行）：

```kotlin
fun seekTo(positionMs: Long) {
    val sanitized = positionMs.coerceAtLeast(0L)
    val controller = mediaController
    if (controller == null || controller.currentMediaItem == null) {
        // Not yet activated — record into pending and mirror into UI state.
        pendingRestorePosition = sanitized
        _playerState.value = _playerState.value.copy(position = sanitized)
        return
    }
    withConnectedController { c ->
        c.seekTo(sanitized)
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt
git -c commit.gpgsign=false commit -m "feat(player): seekTo 在懒激活前仅更新 pending 与 UI 位置"
```

---

## Task 7：所有切歌入口清零 pendingRestorePosition

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt`

- [ ] **Step 1: 写失败用例**

在 `PlayerControllerLazyRestoreTest.kt` 追加：

```kotlin
@Test
fun `skipToPrevious clears pendingRestorePosition`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1"), item("2")),
            startIndex = 1,
            savedPositionMs = 10_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        controller.skipToPrevious()
        assertNull(controller.pendingRestorePositionForTest)
    } finally {
        controller.release()
    }
}

@Test
fun `skipTo clears pendingRestorePosition`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1"), item("2"), item("3")),
            startIndex = 0,
            savedPositionMs = 10_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        controller.skipTo(2)
        assertNull(controller.pendingRestorePositionForTest)
    } finally {
        controller.release()
    }
}

@Test
fun `playItem clears pendingRestorePosition`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 10_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        controller.playItem(item("99"))
        assertNull(controller.pendingRestorePositionForTest)
    } finally {
        controller.release()
    }
}

@Test
fun `playQueue clears pendingRestorePosition`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 10_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        controller.playQueue(listOf(item("9"), item("8")), startIndex = 1)
        assertNull(controller.pendingRestorePositionForTest)
    } finally {
        controller.release()
    }
}

@Test
fun `reset clears pendingRestorePosition`() {
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        controller.restoreQueue(
            items = listOf(item("1")),
            startIndex = 0,
            savedPositionMs = 10_000L,
            savedDurationMs = 60_000L,
            playWhenRestored = false,
        )
        controller.reset()
        assertNull(controller.pendingRestorePositionForTest)
    } finally {
        controller.release()
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest.skipToPrevious clears*" --tests "*PlayerControllerLazyRestoreTest.skipTo clears*" --tests "*PlayerControllerLazyRestoreTest.playItem clears*" --tests "*PlayerControllerLazyRestoreTest.playQueue clears*" --tests "*PlayerControllerLazyRestoreTest.reset clears*" --no-daemon`
Expected: 部分失败（除了 `skipToNext` 已在 Task 4 加过清零）。

- [ ] **Step 3: 实现**

在 `PlayerController.kt` 顶部以下方法首行各加一行 `pendingRestorePosition = null`（如果该方法尚未有）：

- `fun playItem(item: MusicItem)`
- `fun playQueue(items: List<MusicItem>, startIndex: Int = 0)`
- `fun skipToNext()`（Task 4 已加，确认存在即可）
- `fun skipToPrevious()`
- `fun skipTo(index: Int)`
- `fun reset()`

例如 `fun playItem` 改为：

```kotlin
fun playItem(item: MusicItem) {
    pendingRestorePosition = null
    val previousIndex = playQueue.currentIndex
    // … existing body unchanged
}
```

`removeFromQueue(index: Int)` 同样在方法首行加 `pendingRestorePosition = null`：移除任何 item 后语义上需要重新激活，统一清零即可。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlayerControllerLazyRestoreTest*" --tests "*PlayerControllerQueueStateTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerLazyRestoreTest.kt
git -c commit.gpgsign=false commit -m "feat(player): 用户切歌入口统一清零 pendingRestorePosition"
```

---

## Task 8：PlaybackStartupCoordinator 读取持久化位置传入 restoreQueue

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinator.kt`
- Test: `app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinatorTest.kt` (new)

- [ ] **Step 1: 新建测试**

新建文件 `app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinatorTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.bootstrap

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlayQueueRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.onBlocking
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStartupCoordinatorTest {

    @Test
    fun `start passes saved position and duration to restoreQueue`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val testItem = item("1")
        val queueRepo = mock<PlayQueueRepository> {
            onBlocking { getQueue() } doReturn listOf(testItem)
        }
        val prefs = mock<AppPreferences> {
            on { currentMusicIndex } doReturn flowOf(0)
            on { currentMusicPositionMs } doReturn flowOf(42_000L)
            on { currentMusicDurationMs } doReturn flowOf(180_000L)
        }
        val runtime = mock<PlaybackRuntimeSettings> {
            onBlocking { autoPlayWhenAppStart() } doReturn false
        }
        val playerStateFlow: MutableStateFlow<com.zili.android.musicfreeandroid.player.model.PlayerState> =
            MutableStateFlow(com.zili.android.musicfreeandroid.player.model.PlayerState.EMPTY)
        val queueStateFlow = MutableStateFlow(
            com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot.EMPTY,
        )
        val controller = mock<PlayerController> {
            on { playerState } doReturn (playerStateFlow as StateFlow<_>)
            on { queueState } doReturn (queueStateFlow as StateFlow<_>)
        }

        PlaybackStartupCoordinator(
            playerController = controller,
            playQueueRepository = queueRepo,
            appPreferences = prefs,
            playbackRuntimeSettings = runtime,
            applicationScope = scope,
        ).start()

        advanceUntilIdle()

        verify(controller).restoreQueue(
            items = eq(listOf(testItem)),
            startIndex = eq(0),
            savedPositionMs = eq(42_000L),
            savedDurationMs = eq(180_000L),
            playWhenRestored = eq(false),
        )
        scope.cancel()
    }

    private fun item(id: String) = MusicItem(
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

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlaybackStartupCoordinatorTest.start passes saved position*" --no-daemon`
Expected: 编译失败 — `currentMusicPositionMs` / `currentMusicDurationMs` 在 prefs mock 没有；coordinator 也没传新参数。

- [ ] **Step 3: 实现**

修改 `PlaybackStartupCoordinator.kt` 中 `start()` 函数的 restore 分支：

```kotlin
val queue = playQueueRepository.getQueue()
if (queue.isNotEmpty()) {
    val savedIndex = appPreferences.currentMusicIndex.first()
    val savedPositionMs = appPreferences.currentMusicPositionMs.first()
    val savedDurationMs = appPreferences.currentMusicDurationMs.first()
    val startIndex = savedIndex.coerceIn(0, queue.lastIndex)
    val autoPlay = playbackRuntimeSettings.autoPlayWhenAppStart()
    withContext(Dispatchers.Main.immediate) {
        playerController.restoreQueue(
            items = queue,
            startIndex = startIndex,
            savedPositionMs = savedPositionMs,
            savedDurationMs = savedDurationMs,
            playWhenRestored = autoPlay,
        )
    }
    MfLog.detail(
        category = LogCategory.PLAYER,
        event = "playback_startup_restore_completed",
        fields = mapOf(
            "queueSize" to queue.size,
            "startIndex" to startIndex,
            "savedPositionMs" to savedPositionMs,
            "savedDurationMs" to savedDurationMs,
            "autoPlay" to autoPlay,
            "durationMs" to elapsedMs(startedAt),
        ),
    )
} else { /* 现有 else 不变 */ }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlaybackStartupCoordinatorTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinator.kt \
        app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinatorTest.kt
git -c commit.gpgsign=false commit -m "feat(app): coordinator 把持久化位置与时长传给 restoreQueue"
```

---

## Task 9：PlaybackStartupCoordinator 周期 + 关键事件保存位置

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinator.kt`
- Test: `app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinatorTest.kt`

- [ ] **Step 1: 写失败用例**

在 `PlaybackStartupCoordinatorTest.kt` 追加（在文件顶部增加 `import org.mockito.kotlin.times`）：

```kotlin
@Test
fun `saves position when isPlaying transitions to false`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = CoroutineScope(dispatcher + Job())
    val testItem = item("1")
    val queueRepo = mock<PlayQueueRepository> {
        onBlocking { getQueue() } doReturn listOf(testItem)
    }
    val prefs = mock<AppPreferences> {
        on { currentMusicIndex } doReturn flowOf(0)
        on { currentMusicPositionMs } doReturn flowOf(0L)
        on { currentMusicDurationMs } doReturn flowOf(0L)
    }
    val runtime = mock<PlaybackRuntimeSettings> {
        onBlocking { autoPlayWhenAppStart() } doReturn false
    }
    val playerStateFlow = MutableStateFlow(
        com.zili.android.musicfreeandroid.player.model.PlayerState.EMPTY.copy(
            currentItem = testItem,
            isPlaying = true,
            position = 5_000L,
            duration = 60_000L,
        ),
    )
    val queueStateFlow = MutableStateFlow(
        com.zili.android.musicfreeandroid.player.queue.PlayQueueSnapshot.EMPTY,
    )
    val controller = mock<PlayerController> {
        on { playerState } doReturn (playerStateFlow as StateFlow<_>)
        on { queueState } doReturn (queueStateFlow as StateFlow<_>)
    }

    PlaybackStartupCoordinator(
        playerController = controller,
        playQueueRepository = queueRepo,
        appPreferences = prefs,
        playbackRuntimeSettings = runtime,
        applicationScope = scope,
    ).start()
    advanceUntilIdle()

    // Simulate pause: isPlaying true → false at 12_345ms
    playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false, position = 12_345L)
    advanceUntilIdle()

    verify(prefs).setCurrentMusicPositionMs(12_345L)
    verify(prefs).setCurrentMusicDurationMs(60_000L)
    scope.cancel()
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlaybackStartupCoordinatorTest.saves position*" --no-daemon`
Expected: 失败 — `setCurrentMusicPositionMs` 从未被调用。

- [ ] **Step 3: 实现**

在 `PlaybackStartupCoordinator.kt` 的现有 `applicationScope.launch(Dispatchers.IO)` 块尾（即 `playerController.queueState.drop(1).collect ...` 之后）追加一段并行 collect：

```kotlin
applicationScope.launch(Dispatchers.IO) {
    var lastIsPlaying = false
    var lastItemKey: String? = null
    var lastPersistedPosition = -1L
    var lastPersistedDuration = -1L
    var lastTickAt = System.currentTimeMillis()
    playerController.playerState.collect { state ->
        val itemKey = state.currentItem?.let { "${it.platform}:${it.id}" }
        val now = System.currentTimeMillis()

        val flush = when {
            // 切歌：把上一首终态写入（如果有）
            itemKey != lastItemKey -> true
            // 边沿：从播放变为暂停 / 停止
            lastIsPlaying && !state.isPlaying -> true
            // 周期：播放中 5s 写一次，且变化超过 1s
            state.isPlaying &&
                (now - lastTickAt) >= 5_000L &&
                kotlin.math.abs(state.position - lastPersistedPosition) >= 1_000L -> true
            // duration 首次更新或变更
            state.duration > 0L && state.duration != lastPersistedDuration -> true
            else -> false
        }

        if (flush) {
            try {
                if (state.position != lastPersistedPosition) {
                    appPreferences.setCurrentMusicPositionMs(state.position.coerceAtLeast(0L))
                    lastPersistedPosition = state.position
                }
                if (state.duration > 0L && state.duration != lastPersistedDuration) {
                    appPreferences.setCurrentMusicDurationMs(state.duration)
                    lastPersistedDuration = state.duration
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                MfLog.error(
                    category = LogCategory.PLAYER,
                    event = "playback_position_persist_failed",
                    throwable = error,
                    fields = mapOf(
                        "position" to state.position,
                        "duration" to state.duration,
                    ),
                )
            }
            lastTickAt = now
        }

        lastIsPlaying = state.isPlaying
        lastItemKey = itemKey
    }
}
```

> **注意**：上面这段独立 launch 与原本的 IO launch 并行；不要嵌进原 `try/catch` 块。结构调整方式：在 `start()` 内现有 `applicationScope.launch(Dispatchers.IO) { /* restore + queue collect */ }` 之后追加 `applicationScope.launch(Dispatchers.IO) { /* save loop */ }`。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlaybackStartupCoordinatorTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinator.kt \
        app/src/test/java/com/zili/android/musicfreeandroid/bootstrap/PlaybackStartupCoordinatorTest.kt
git -c commit.gpgsign=false commit -m "feat(app): 周期与关键事件保存播放位置 / 时长"
```

---

## Task 10：PlaybackService 注入 AppPreferences 并在生命周期刷盘

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/service/PlaybackService.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/service/PlaybackServiceFlushTest.kt` (new)

- [ ] **Step 1: 新建测试**

新建 `player/src/test/java/com/zili/android/musicfreeandroid/player/service/PlaybackServiceFlushTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.player.service

import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.never

class PlaybackServiceFlushTest {

    @Test
    fun `flushLastPositionTo persists position and duration when non-zero`() = runTest {
        val prefs = mock<AppPreferences>()
        runBlocking {
            PlaybackService.flushLastPositionTo(
                prefs = prefs,
                positionMs = 12_345L,
                durationMs = 60_000L,
            )
        }
        verify(prefs).setCurrentMusicPositionMs(12_345L)
        verify(prefs).setCurrentMusicDurationMs(60_000L)
    }

    @Test
    fun `flushLastPositionTo is no-op when both values are zero`() = runTest {
        val prefs = mock<AppPreferences>()
        runBlocking {
            PlaybackService.flushLastPositionTo(
                prefs = prefs,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
        verify(prefs, never()).setCurrentMusicPositionMs(0L)
        verify(prefs, never()).setCurrentMusicDurationMs(0L)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlaybackServiceFlushTest*" --no-daemon`
Expected: 编译失败 — `flushLastPositionTo` 未定义。

- [ ] **Step 3: 实现**

修改 `PlaybackService.kt`：

1. 顶部 import 增加：

```kotlin
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
```

2. 在类内 `@Inject lateinit var playbackRuntimeSettings: PlaybackRuntimeSettings` 下方加：

```kotlin
@Inject lateinit var appPreferences: AppPreferences
```

3. `onTaskRemoved` 改为（在调用 `stopSelf()` 之前先 flush）：

```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    flushLastPosition()
    val player = mediaSession?.player
    if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
        stopSelf()
    }
}
```

4. `onDestroy` 在 `mediaSession?.run { player.release(); release() }` **之前**插入 `flushLastPosition()`：

```kotlin
override fun onDestroy() {
    MfLog.detail(/* … 现有日志不变 */)
    flushLastPosition()
    mediaSession?.run {
        player.release()
        release()
    }
    mediaSession = null
    serviceScope.cancel()
    super.onDestroy()
}
```

5. 在类底部新增私有方法 + companion helper：

```kotlin
private fun flushLastPosition() {
    val player = mediaSession?.player ?: return
    val positionMs = player.currentPosition.coerceAtLeast(0L)
    val durationMs = player.duration.let { if (it > 0L) it else 0L }
    runBlocking {
        withTimeoutOrNull(200L) {
            flushLastPositionTo(appPreferences, positionMs, durationMs)
        }
    }
}

companion object {
    internal suspend fun flushLastPositionTo(
        prefs: AppPreferences,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (positionMs <= 0L && durationMs <= 0L) return
        if (positionMs > 0L) prefs.setCurrentMusicPositionMs(positionMs)
        if (durationMs > 0L) prefs.setCurrentMusicDurationMs(durationMs)
    }
}
```

> 已有 companion object 内常量（`PLAYBACK_NOTIFICATION_CHANNEL_ID`、`PLAYBACK_NOTIFICATION_ID` 等）合并到同一个 companion 里即可，不要重复声明。

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :player:testDebugUnitTest --tests "*PlaybackServiceFlushTest*" --no-daemon`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/service/PlaybackService.kt \
        player/src/test/java/com/zili/android/musicfreeandroid/player/service/PlaybackServiceFlushTest.kt
git -c commit.gpgsign=false commit -m "feat(player): 服务生命周期 best-effort 刷盘最后位置"
```

---

## Task 11：Mini player 恢复态渲染回归测试

**Files:**
- Modify: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayerContentTest.kt`

- [ ] **Step 1: 读现有测试**

Run: `grep -n "progress\|isPlaying\|MiniPlayerUiModel" feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayerContentTest.kt | head -20`

确认存在 `MiniPlayerUiModel(...)` 实例化形式。

- [ ] **Step 2: 写失败用例**

在 `MiniPlayerContentTest.kt` 中追加：

```kotlin
@Test
fun renders_restored_state_with_progress_and_play_icon() {
    val model = MiniPlayerUiModel(
        coverUri = null,
        title = "Restored Song",
        artist = "Artist",
        isPlaying = false,
        progress = 0.5f,
        hasPrev = false,
        hasNext = false,
        prevTitle = null,
        nextTitle = null,
    )
    composeTestRule.setContent {
        MiniPlayerContent(
            uiModel = model,
            onClick = {},
            onPlayPauseClick = {},
            onSwipeToNext = {},
            onSwipeToPrev = {},
        )
    }
    composeTestRule.onNodeWithText("Restored Song").assertExists()
    composeTestRule.onNodeWithContentDescription("播放").assertExists()
}
```

> 如果现有测试文件没有 `composeTestRule`，参考同目录已有用例的 setup 复用即可。`onPlayPauseClick` / `onSwipeToNext` 等签名以 `MiniPlayerContent` 实际签名为准——若签名不一致，调整实参即可，**不要修改 production 代码**。

- [ ] **Step 3: 跑测试确认通过 / 失败**

Run: `./gradlew :feature:player-ui:testDebugUnitTest --tests "*MiniPlayerContentTest.renders_restored_state*" --no-daemon`

若失败，按错误信息修正 `assertExists` 选择器（例如 content description 实际是 `play` / "暂停" / icon 资源 description 等）。**不要**改 production 代码：本任务只是回归保护，渲染逻辑已经无需修改。

- [ ] **Step 4: 提交**

```bash
git add feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayerContentTest.kt
git -c commit.gpgsign=false commit -m "test(player-ui): mini player 恢复态渲染回归"
```

---

## Task 12：全量验证 + lint + 手工验收

**Files:** 无（验证步骤）

- [ ] **Step 1: 跑各模块单测**

Run（按模块依赖顺序）:

```bash
./gradlew :data:testDebugUnitTest --no-daemon
./gradlew :player:testDebugUnitTest --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

Expected: 全部 BUILD SUCCESSFUL。

- [ ] **Step 2: Debug 构建**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Lint**

Run: `./gradlew :app:lintDebug --no-daemon`
Expected: 无新增 error；warnings 与基线相同。

- [ ] **Step 4: 手工验收（设备/模拟器）**

安装 debug APK 后按以下顺序操作并核对：

1. 播放任一首歌，停留在某个非起点位置 → 暂停 → 杀掉进程 → 重新启动 app。
   - 期望：mini player 显示该曲，进度条停在上次位置，按钮是「播放」状态。
   - 点击播放：1–2s 内开始从上次位置播放。
2. 选择本地音乐播放至中段 → 杀进程 → 重启。
   - 期望：同上。
3. 把 quality 调到能让 duration 改变的档位，或拖动到 `duration - 1s` 附近 → 杀进程 → 重启 → 点 play。
   - 期望：从位置开始，几秒内自动切下一首。
4. 打开飞行模式 → 上次记录的是远程插件源 → 重启 → 点 play。
   - 期望：弹错误提示；再次点 play 仍按相同语义尝试。
5. 在开发者选项里强停 app → 等 6s 以上重启。
   - 期望：进度被保留（5s 内的 tick + 任何 isPlaying=false 边沿即刷盘）。

- [ ] **Step 5: 验收成功后提交收尾**

无代码变更则不创建空 commit；若手工验收过程中发现需要小修，先回到对应 Task 走 TDD 路径修复。

---

## 自检

**1. Spec 覆盖**

- §1 背景 / §3 架构两阶段：Task 3–7（PlayerController）+ Task 8–9（Coordinator）+ Task 10（Service）共同实现。
- §2 决策 — 持久化层：Task 1–2。
- §4.1 AppPreferences 接口：Task 1–2。
- §4.2 PlayerController：Task 3（restoreQueue 签名）、Task 4（play 懒激活）、Task 5（STATE_READY 消费）、Task 6（seekTo 前置）、Task 7（清零入口）。
- §4.3 Coordinator：Task 8（restore）+ Task 9（saveLoop）。
- §4.4 PlaybackService：Task 10。
- §4.5 MiniPlayer：Task 11（仅回归测试，无 production 修改 —— 符合 spec 说"不需要修改"）。
- §5 错误处理：①解析失败：Task 4/7 保留 pending；②stale-url：依赖现有路径，Task 5 STATE_READY 处理；③autoplay 失败：同链路；④主动切歌：Task 7；⑤持久层异常：Task 9 try/catch；⑥服务释放：Task 10 顺序保证；⑦强杀：5s tick 自带；⑧越界 coerce：Task 5；⑨升级首启动：默认 0L 自然满足。
- §6 测试：Task 1–11 各自的 TDD 步骤覆盖。

**2. 占位扫描** — 无 TBD/TODO/伪步骤。

**3. 类型一致** — `pendingRestorePosition: Long?` 全程使用；`savedPositionMs / savedDurationMs` 参数名在 Task 3、Task 8 一致；`pendingRestorePositionForTest` getter + `consumePendingRestoreForTest` helper 名称一致。

---

**Plan complete.** 用户已指定 subagent-driven-development，下一步直接调 `superpowers:subagent-driven-development` 执行。
