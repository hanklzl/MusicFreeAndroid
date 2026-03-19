# 里程碑 3：播放引擎（Media3 + 后台服务）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 基于 Media3（ExoPlayer）实现完整的播放引擎，支持后台播放、通知栏控制、音频焦点处理，并通过 `StateFlow<PlayerState>` 向 UI 层暴露响应式播放状态。

**架构：** `PlaybackService`（MediaSessionService）持有 ExoPlayer + MediaSession。`PlayerController`（单例）通过 `MediaController` 连接服务，管理内存中的 `PlayQueue`，并暴露 `StateFlow<PlayerState>`。播放模块仅依赖 `:core` — 队列持久化由上层模块在后续里程碑处理。采用单曲播放模式：ExoPlayer 上只设置当前播放曲目，队列导航由 `PlayQueue` 管理。

**技术栈：** Media3 1.9.2（ExoPlayer、Session、DataSource-OkHttp）、Kotlin Coroutines + Flow、Hilt DI、JUnit + Turbine 测试。

---

## 文件结构

### `:player` 模块 — 播放引擎

| 文件 | 职责 |
|------|------|
| `player/model/PlaybackState.kt` | 播放状态枚举（IDLE、BUFFERING、READY、ENDED、ERROR） |
| `player/model/PlayerState.kt` | 不可变的播放器状态数据类（当前曲目、播放中、进度、模式） |
| `player/queue/PlayQueue.kt` | 内存队列管理：增删移动、随机/取消随机、带重复模式的上下曲导航 |
| `player/ext/MusicItemMediaExt.kt` | `MusicItem.toMediaItem()` 扩展函数，连接核心模型与 Media3 MediaItem |
| `player/service/PlaybackService.kt` | MediaSessionService，包装 ExoPlayer + MediaSession |
| `player/controller/PlayerController.kt` | MediaController 包装器、PlayQueue 协调器、StateFlow<PlayerState> 发射器 |
| `player/di/PlayerModule.kt` | Hilt 模块，提供 PlayerController 单例 |
| `player/AndroidManifest.xml` | Service 声明、前台服务权限 |

### `:core` 模块 — 已有，无需修改

已提供：`MusicItem`、`RepeatMode`、`PlayQuality`、`QualityInfo`、`MediaSourceResult`

### 测试文件

| 文件 | 职责 |
|------|------|
| `player/src/test/.../player/queue/PlayQueueTest.kt` | 单元测试：增删移动、随机/取消随机、上下曲、重复模式、边界情况 |
| `player/src/test/.../player/model/PlayerStateTest.kt` | 单元测试：默认状态、copy 行为 |
| `player/src/test/.../player/ext/MusicItemMediaExtTest.kt` | 单元测试：MusicItem → MediaItem 转换 |
| `player/src/androidTest/.../player/service/PlaybackServiceTest.kt` | 集成测试：Service 生命周期、MediaSession 创建 |
| `player/src/androidTest/.../player/controller/PlayerControllerTest.kt` | 集成测试：连接、播放/暂停/seek/跳曲、状态观察 |

---

## 任务 1：添加 Media3 依赖

**文件：**
- 修改：`gradle/libs.versions.toml`
- 修改：`player/build.gradle.kts`

- [ ] **步骤 1：在版本目录中添加 Media3 版本和库条目**

在 `gradle/libs.versions.toml` 的 `[versions]` 下添加：

```toml
media3 = "1.9.2"
```

在 `[libraries]` 下添加：

```toml
# Media3
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
androidx-media3-datasource-okhttp = { group = "androidx.media3", name = "media3-datasource-okhttp", version.ref = "media3" }
```

- [ ] **步骤 2：更新 player/build.gradle.kts，添加 Media3 和测试依赖**

替换 `player/build.gradle.kts` 的 `dependencies` 块：

```kotlin
dependencies {
    implementation(project(":core"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // 单元测试
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // 集成测试
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :player:assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：提交**

```bash
git add gradle/libs.versions.toml player/build.gradle.kts
git commit -m "chore(player): add Media3, coroutines, and test dependencies"
```

---

## 任务 2：定义 PlaybackState 和 PlayerState

**文件：**
- 创建：`player/src/main/java/com/zili/android/musicfreeandroid/player/model/PlaybackState.kt`
- 创建：`player/src/main/java/com/zili/android/musicfreeandroid/player/model/PlayerState.kt`
- 测试：`player/src/test/java/com/zili/android/musicfreeandroid/player/model/PlayerStateTest.kt`

- [ ] **步骤 1：编写 PlayerState 单元测试**

```kotlin
package com.zili.android.musicfreeandroid.player.model

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import org.junit.Assert.*
import org.junit.Test

class PlayerStateTest {

    @Test
    fun `EMPTY state has sensible defaults`() {
        val state = PlayerState.EMPTY
        assertNull(state.currentItem)
        assertFalse(state.isPlaying)
        assertEquals(PlaybackState.IDLE, state.playbackState)
        assertEquals(0L, state.duration)
        assertEquals(0L, state.position)
        assertEquals(RepeatMode.OFF, state.repeatMode)
        assertFalse(state.shuffleEnabled)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val item = MusicItem(
            id = "1", platform = "local", title = "Song",
            artist = "Artist", album = null, duration = 180_000L,
            url = "file:///music/song.mp3", artwork = null, qualities = null,
        )
        val state = PlayerState.EMPTY.copy(currentItem = item, isPlaying = true)
        assertEquals(item, state.currentItem)
        assertTrue(state.isPlaying)
        assertEquals(PlaybackState.IDLE, state.playbackState)
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }

    @Test
    fun `hasMedia returns true when currentItem is set`() {
        assertFalse(PlayerState.EMPTY.hasMedia)
        val withItem = PlayerState.EMPTY.copy(
            currentItem = MusicItem(
                id = "1", platform = "local", title = "Song",
                artist = "Artist", album = null, duration = 0L,
                url = null, artwork = null, qualities = null,
            )
        )
        assertTrue(withItem.hasMedia)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayerStateTest"`
预期：FAIL — 类未找到

- [ ] **步骤 3：实现 PlaybackState 枚举**

```kotlin
package com.zili.android.musicfreeandroid.player.model

/**
 * 映射 Media3 Player.STATE_* 常量。
 */
enum class PlaybackState {
    /** 播放器空闲 — 未设置媒体。 */
    IDLE,
    /** 媒体正在缓冲。 */
    BUFFERING,
    /** 媒体准备就绪，可以播放。 */
    READY,
    /** 当前媒体项播放完毕。 */
    ENDED,
    /** 播放出错。 */
    ERROR,
}
```

- [ ] **步骤 4：实现 PlayerState 数据类**

```kotlin
package com.zili.android.musicfreeandroid.player.model

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode

data class PlayerState(
    val currentItem: MusicItem? = null,
    val isPlaying: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val duration: Long = 0L,
    val position: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
) {
    val hasMedia: Boolean get() = currentItem != null

    companion object {
        val EMPTY = PlayerState()
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayerStateTest"`
预期：PASS（3 个测试）

- [ ] **步骤 6：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/model/ \
       player/src/test/java/com/zili/android/musicfreeandroid/player/model/
git commit -m "feat(player): add PlaybackState enum and PlayerState data class"
```

---

## 任务 3：实现 PlayQueue

**文件：**
- 创建：`player/src/main/java/com/zili/android/musicfreeandroid/player/queue/PlayQueue.kt`
- 测试：`player/src/test/java/com/zili/android/musicfreeandroid/player/queue/PlayQueueTest.kt`

这是逻辑最复杂的单元。PlayQueue 管理 `MusicItem` 的内存队列，跟踪当前索引，支持随机播放（含原始顺序恢复）和基于重复模式的导航。

### 步骤组 A：核心队列操作

- [ ] **步骤 1：编写基础队列操作测试**

```kotlin
package com.zili.android.musicfreeandroid.player.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayQueueTest {

    private lateinit var queue: PlayQueue

    private fun item(id: String) = MusicItem(
        id = id, platform = "test", title = "Song $id",
        artist = "Artist", album = null, duration = 180_000L,
        url = "https://example.com/$id.mp3", artwork = null, qualities = null,
    )

    private val song1 = item("1")
    private val song2 = item("2")
    private val song3 = item("3")
    private val song4 = item("4")

    @Before
    fun setUp() {
        queue = PlayQueue()
    }

    // --- isEmpty / size ---

    @Test
    fun `new queue is empty`() {
        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)
        assertNull(queue.currentItem)
        assertEquals(-1, queue.currentIndex)
    }

    // --- setQueue ---

    @Test
    fun `setQueue replaces all items and sets currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 1)
        assertEquals(3, queue.size)
        assertEquals(1, queue.currentIndex)
        assertEquals(song2, queue.currentItem)
    }

    @Test
    fun `setQueue with empty list clears queue`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        queue.setQueue(emptyList(), startIndex = 0)
        assertTrue(queue.isEmpty)
        assertEquals(-1, queue.currentIndex)
    }

    @Test
    fun `setQueue clamps startIndex to valid range`() {
        queue.setQueue(listOf(song1, song2), startIndex = 5)
        assertEquals(1, queue.currentIndex) // 限制到最后一个
    }

    // --- add / addNext ---

    @Test
    fun `add appends item to end of queue`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        queue.add(song2)
        assertEquals(2, queue.size)
        assertEquals(listOf(song1, song2), queue.items)
    }

    @Test
    fun `add to empty queue sets currentIndex to 0`() {
        queue.add(song1)
        assertEquals(0, queue.currentIndex)
        assertEquals(song1, queue.currentItem)
    }

    @Test
    fun `addNext inserts after currentIndex`() {
        queue.setQueue(listOf(song1, song3), startIndex = 0)
        queue.addNext(song2)
        assertEquals(listOf(song1, song2, song3), queue.items)
        assertEquals(0, queue.currentIndex) // 不变
    }

    // --- remove ---

    @Test
    fun `remove item before currentIndex decrements currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 2)
        queue.remove(0) // 移除 song1
        assertEquals(listOf(song2, song3), queue.items)
        assertEquals(1, queue.currentIndex) // 原来是 2，现在是 1
    }

    @Test
    fun `remove item after currentIndex keeps currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        queue.remove(2) // 移除 song3
        assertEquals(listOf(song1, song2), queue.items)
        assertEquals(0, queue.currentIndex) // 不变
    }

    @Test
    fun `remove current item moves to next available`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 1)
        val newCurrent = queue.remove(1) // 移除 song2（当前曲目）
        assertEquals(listOf(song1, song3), queue.items)
        assertEquals(1, queue.currentIndex) // 保持在 1
        assertEquals(song3, newCurrent)
    }

    @Test
    fun `remove last item when it is current wraps index`() {
        queue.setQueue(listOf(song1, song2), startIndex = 1)
        val newCurrent = queue.remove(1) // 移除 song2（当前，末尾）
        assertEquals(listOf(song1), queue.items)
        assertEquals(0, queue.currentIndex)
        assertEquals(song1, newCurrent)
    }

    @Test
    fun `remove only item empties queue`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        val newCurrent = queue.remove(0)
        assertTrue(queue.isEmpty)
        assertNull(newCurrent)
    }

    // --- move ---

    @Test
    fun `move item updates order and adjusts currentIndex`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0) // 当前 = song1
        queue.move(fromIndex = 0, toIndex = 2) // 将 song1 移到末尾
        assertEquals(listOf(song2, song3, song1), queue.items)
        assertEquals(2, queue.currentIndex) // 跟随当前曲目
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayQueueTest"`
预期：FAIL — PlayQueue 类未找到

- [ ] **步骤 3：实现 PlayQueue 核心操作**

```kotlin
package com.zili.android.musicfreeandroid.player.queue

import com.zili.android.musicfreeandroid.core.model.MusicItem

class PlayQueue {

    private val _items = mutableListOf<MusicItem>()
    val items: List<MusicItem> get() = _items.toList()

    var currentIndex: Int = -1
        private set

    val currentItem: MusicItem?
        get() = _items.getOrNull(currentIndex)

    val size: Int get() = _items.size
    val isEmpty: Boolean get() = _items.isEmpty()

    fun setQueue(items: List<MusicItem>, startIndex: Int = 0) {
        _items.clear()
        _items.addAll(items)
        currentIndex = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.lastIndex)
    }

    fun add(item: MusicItem) {
        _items.add(item)
        if (_items.size == 1) currentIndex = 0
    }

    fun addNext(item: MusicItem) {
        val insertAt = if (isEmpty) 0 else currentIndex + 1
        _items.add(insertAt, item)
        if (_items.size == 1) currentIndex = 0
    }

    /**
     * 移除指定 [index] 处的曲目。返回新的当前曲目（如果队列变空则返回 null）。
     * 自动调整 [currentIndex] 以跟随当前曲目或前进到下一曲。
     */
    fun remove(index: Int): MusicItem? {
        if (index !in _items.indices) return currentItem
        _items.removeAt(index)
        if (_items.isEmpty()) {
            currentIndex = -1
            return null
        }
        when {
            index < currentIndex -> currentIndex--
            index == currentIndex -> {
                // 保持在相同索引（现在指向下一曲），但限制范围
                currentIndex = currentIndex.coerceAtMost(_items.lastIndex)
            }
            // index > currentIndex -> 不变
        }
        return currentItem
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in _items.indices || toIndex !in _items.indices) return
        val item = _items.removeAt(fromIndex)
        _items.add(toIndex, item)
        // 调整 currentIndex 以跟随当前曲目
        currentIndex = when {
            currentIndex == fromIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
    }

    fun clear() {
        _items.clear()
        currentIndex = -1
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayQueueTest"`
预期：PASS（所有测试通过）

- [ ] **步骤 5：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/queue/ \
       player/src/test/java/com/zili/android/musicfreeandroid/player/queue/
git commit -m "feat(player): add PlayQueue with core queue operations"
```

### 步骤组 B：带重复模式的上下曲导航

- [ ] **步骤 6：编写上下曲导航测试**

在 `PlayQueueTest.kt` 中追加：

```kotlin
    // --- next / previous 与 RepeatMode ---

    @Test
    fun `next advances to next item in OFF mode`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        assertEquals(song2, queue.next(RepeatMode.OFF))
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `next at end returns null in OFF mode`() {
        queue.setQueue(listOf(song1, song2), startIndex = 1)
        assertNull(queue.next(RepeatMode.OFF))
        assertEquals(1, queue.currentIndex) // 不变
    }

    @Test
    fun `next at end wraps to first in ALL mode`() {
        queue.setQueue(listOf(song1, song2), startIndex = 1)
        assertEquals(song1, queue.next(RepeatMode.ALL))
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `next in ONE mode returns same item`() {
        queue.setQueue(listOf(song1, song2), startIndex = 0)
        assertEquals(song1, queue.next(RepeatMode.ONE))
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `previous goes to previous item in OFF mode`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 2)
        assertEquals(song2, queue.previous(RepeatMode.OFF))
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `previous at start returns null in OFF mode`() {
        queue.setQueue(listOf(song1, song2), startIndex = 0)
        assertNull(queue.previous(RepeatMode.OFF))
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `previous at start wraps to last in ALL mode`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        assertEquals(song3, queue.previous(RepeatMode.ALL))
        assertEquals(2, queue.currentIndex)
    }

    @Test
    fun `next on empty queue returns null`() {
        assertNull(queue.next(RepeatMode.ALL))
    }

    @Test
    fun `previous on empty queue returns null`() {
        assertNull(queue.previous(RepeatMode.ALL))
    }

    @Test
    fun `next with single item in ALL mode returns same item`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        assertEquals(song1, queue.next(RepeatMode.ALL))
        assertEquals(0, queue.currentIndex)
    }

    // --- skipTo ---

    @Test
    fun `skipTo sets currentIndex and returns item`() {
        queue.setQueue(listOf(song1, song2, song3), startIndex = 0)
        assertEquals(song3, queue.skipTo(2))
        assertEquals(2, queue.currentIndex)
    }

    @Test
    fun `skipTo with invalid index returns null`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        assertNull(queue.skipTo(5))
        assertEquals(0, queue.currentIndex) // 不变
    }
```

- [ ] **步骤 7：运行测试验证新测试失败**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayQueueTest"`
预期：FAIL — `next`、`previous`、`skipTo` 方法未找到

- [ ] **步骤 8：实现 next、previous、skipTo**

在 `PlayQueue.kt` 中添加：

```kotlin
    /**
     * 根据 [repeatMode] 前进到下一曲。
     * 返回新的当前曲目，如果到达末尾（OFF 模式下）则返回 null。
     */
    fun next(repeatMode: RepeatMode): MusicItem? {
        if (isEmpty) return null
        val nextIndex = when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL -> (currentIndex + 1) % _items.size
            RepeatMode.OFF -> {
                val idx = currentIndex + 1
                if (idx > _items.lastIndex) return null else idx
            }
        }
        currentIndex = nextIndex
        return currentItem
    }

    /**
     * 根据 [repeatMode] 后退到上一曲。
     * 返回新的当前曲目，如果在开头（OFF 模式下）则返回 null。
     */
    fun previous(repeatMode: RepeatMode): MusicItem? {
        if (isEmpty) return null
        val prevIndex = when (repeatMode) {
            RepeatMode.ONE -> currentIndex
            RepeatMode.ALL -> (currentIndex - 1 + _items.size) % _items.size
            RepeatMode.OFF -> {
                val idx = currentIndex - 1
                if (idx < 0) return null else idx
            }
        }
        currentIndex = prevIndex
        return currentItem
    }

    /**
     * 跳转到指定 [index]。返回该位置的曲目，如果索引无效则返回 null。
     */
    fun skipTo(index: Int): MusicItem? {
        if (index !in _items.indices) return null
        currentIndex = index
        return currentItem
    }
```

- [ ] **步骤 9：运行测试验证通过**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayQueueTest"`
预期：PASS（所有测试通过）

- [ ] **步骤 10：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/queue/ \
       player/src/test/java/com/zili/android/musicfreeandroid/player/queue/
git commit -m "feat(player): add PlayQueue navigation (next/prev/skipTo) with repeat mode"
```

### 步骤组 C：随机播放与原始顺序恢复

- [ ] **步骤 11：编写随机播放测试**

在 `PlayQueueTest.kt` 中追加：

```kotlin
    // --- shuffle / unshuffle ---

    @Test
    fun `shuffle randomizes order but keeps currentItem the same`() {
        val items = (1..20).map { item(it.toString()) }
        queue.setQueue(items, startIndex = 5)
        val currentBefore = queue.currentItem

        queue.shuffle()

        assertEquals(currentBefore, queue.currentItem)
        assertEquals(20, queue.size)
        // 随机后当前曲目移到索引 0
        assertEquals(0, queue.currentIndex)
        assertEquals(currentBefore, queue.items[0])
    }

    @Test
    fun `unshuffle restores original order and finds current item`() {
        val items = listOf(song1, song2, song3, song4)
        queue.setQueue(items, startIndex = 1) // 当前 = song2

        queue.shuffle()
        val currentAfterShuffle = queue.currentItem
        assertEquals(song2, currentAfterShuffle) // 仍然是 song2

        queue.unshuffle()
        assertEquals(items, queue.items) // 恢复原始顺序
        assertEquals(song2, queue.currentItem)
        assertEquals(1, queue.currentIndex) // song2 在原始索引 1
    }

    @Test
    fun `shuffle on empty queue does nothing`() {
        queue.shuffle()
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `shuffle single item queue keeps it`() {
        queue.setQueue(listOf(song1), startIndex = 0)
        queue.shuffle()
        assertEquals(listOf(song1), queue.items)
        assertEquals(0, queue.currentIndex)
    }

    @Test
    fun `items added after shuffle are included in unshuffle`() {
        queue.setQueue(listOf(song1, song2), startIndex = 0)
        queue.shuffle()
        queue.add(song3) // 随机后添加的曲目
        queue.unshuffle()
        // 原始顺序恢复，song3 追加到末尾
        assertEquals(song1, queue.items[0])
        assertEquals(song2, queue.items[1])
        assertEquals(song3, queue.items[2])
    }

    @Test
    fun `items removed during shuffle stay removed after unshuffle`() {
        queue.setQueue(listOf(song1, song2, song3, song4), startIndex = 0)
        queue.shuffle() // 当前 = song1（索引 0）
        // 找到 song3 在随机后的位置并移除
        val song3Index = queue.items.indexOfFirst { it.id == "3" }
        queue.remove(song3Index)
        assertEquals(3, queue.size) // song3 已移除

        queue.unshuffle()
        // 恢复原始顺序，但 song3 不应出现
        assertEquals(3, queue.size)
        assertEquals(song1, queue.items[0])
        assertEquals(song2, queue.items[1])
        assertEquals(song4, queue.items[2])
    }
```

- [ ] **步骤 12：运行测试验证新测试失败**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayQueueTest"`
预期：FAIL — `shuffle`、`unshuffle` 方法未找到

- [ ] **步骤 13：实现 shuffle/unshuffle**

在 `PlayQueue.kt` 中添加：

```kotlin
    private var originalOrder: List<MusicItem>? = null
    val isShuffled: Boolean get() = originalOrder != null

    /**
     * 随机打乱队列，将当前曲目移到索引 0。
     * 保存原始顺序以便通过 [unshuffle] 恢复。
     */
    fun shuffle() {
        if (_items.size <= 1) return
        val current = currentItem ?: return
        originalOrder = _items.toList()

        _items.remove(current)
        _items.shuffle()
        _items.add(0, current)
        currentIndex = 0
    }

    /**
     * 恢复随机前的原始顺序。随机后添加的曲目追加到末尾。
     * 保持当前曲目不变。
     */
    fun unshuffle() {
        val saved = originalOrder ?: return
        val current = currentItem
        // 随机后新增的曲目：在 _items 中但不在 originalOrder 中
        val originalIds = saved.map { "${it.platform}:${it.id}" }.toSet()
        val newItems = _items.filter { "${it.platform}:${it.id}" !in originalIds }
        // 记录当前队列中仍存在的曲目 ID（在 clear 之前！）
        val survivingIds = _items.map { "${it.platform}:${it.id}" }.toSet()

        _items.clear()
        // 只恢复在随机期间未被移除的曲目
        for (item in saved) {
            if ("${item.platform}:${item.id}" in survivingIds) {
                _items.add(item)
            }
        }
        _items.addAll(newItems)

        currentIndex = if (current != null) {
            _items.indexOfFirst { it.id == current.id && it.platform == current.platform }
                .coerceAtLeast(0)
        } else {
            -1
        }
        originalOrder = null
    }
```

- [ ] **步骤 14：运行测试验证通过**

运行：`./gradlew :player:testDebugUnitTest --tests "*.PlayQueueTest"`
预期：PASS（所有测试通过）

- [ ] **步骤 15：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/queue/ \
       player/src/test/java/com/zili/android/musicfreeandroid/player/queue/
git commit -m "feat(player): add shuffle/unshuffle with original order restoration"
```

---

## 任务 4：MusicItem → MediaItem 扩展

**文件：**
- 创建：`player/src/main/java/com/zili/android/musicfreeandroid/player/ext/MusicItemMediaExt.kt`
- 测试：`player/src/test/java/com/zili/android/musicfreeandroid/player/ext/MusicItemMediaExtTest.kt`

- [ ] **步骤 1：编写 MusicItem → MediaItem 转换测试**

```kotlin
package com.zili.android.musicfreeandroid.player.ext

import com.zili.android.musicfreeandroid.core.model.MusicItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicItemMediaExtTest {

    @Test
    fun `toMediaItem sets mediaId as platform colon id`() {
        val item = MusicItem(
            id = "abc", platform = "netease", title = "Song",
            artist = "Artist", album = "Album", duration = 200_000L,
            url = "https://example.com/song.mp3", artwork = "https://example.com/art.jpg",
            qualities = null,
        )
        val mediaItem = item.toMediaItem()
        assertEquals("netease:abc", mediaItem.mediaId)
        assertEquals("https://example.com/song.mp3", mediaItem.localConfiguration?.uri?.toString())
        assertEquals("Song", mediaItem.mediaMetadata.title?.toString())
        assertEquals("Artist", mediaItem.mediaMetadata.artist?.toString())
        assertEquals("Album", mediaItem.mediaMetadata.albumTitle?.toString())
        assertEquals("https://example.com/art.jpg", mediaItem.mediaMetadata.artworkUri?.toString())
    }

    @Test
    fun `toMediaItem handles null url gracefully`() {
        val item = MusicItem(
            id = "1", platform = "local", title = "Song",
            artist = "Artist", album = null, duration = 0L,
            url = null, artwork = null, qualities = null,
        )
        val mediaItem = item.toMediaItem()
        assertEquals("local:1", mediaItem.mediaId)
        assertNull(mediaItem.localConfiguration)
        assertNull(mediaItem.mediaMetadata.albumTitle)
        assertNull(mediaItem.mediaMetadata.artworkUri)
    }
}
```

注意：此测试使用 Robolectric 运行，因为 `MediaItem` 是 Android 类。需要在版本目录和 `player/build.gradle.kts` 中添加 Robolectric 依赖。

在 `gradle/libs.versions.toml` 的 `[versions]` 下添加：
```toml
robolectric = "4.14.1"
```

在 `[libraries]` 下添加：
```toml
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
```

在 `player/build.gradle.kts` 中添加：
```kotlin
testImplementation(libs.robolectric)
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :player:testDebugUnitTest --tests "*.MusicItemMediaExtTest"`
预期：FAIL

- [ ] **步骤 3：实现扩展函数**

```kotlin
package com.zili.android.musicfreeandroid.player.ext

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zili.android.musicfreeandroid.core.model.MusicItem

fun MusicItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId("$platform:$id")

    url?.let { builder.setUri(it) }

    builder.setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                album?.let { setAlbumTitle(it) }
                artwork?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
    )

    return builder.build()
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :player:testDebugUnitTest --tests "*.MusicItemMediaExtTest"`
预期：PASS

- [ ] **步骤 5：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/ext/ \
       player/src/test/java/com/zili/android/musicfreeandroid/player/ext/ \
       player/build.gradle.kts
git commit -m "feat(player): add MusicItem to MediaItem conversion extension"
```

---

## 任务 5：实现 PlaybackService

**文件：**
- 创建：`player/src/main/java/com/zili/android/musicfreeandroid/player/service/PlaybackService.kt`
- 创建：`player/src/main/AndroidManifest.xml`

- [ ] **步骤 1：创建 PlaybackService**

```kotlin
package com.zili.android.musicfreeandroid.player.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
```

- [ ] **步骤 2：创建 player 模块的 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <service
            android:name="com.zili.android.musicfreeandroid.player.service.PlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
    </application>

</manifest>
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :player:assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/service/ \
       player/src/main/AndroidManifest.xml
git commit -m "feat(player): add PlaybackService (MediaSessionService + ExoPlayer)"
```

---

## 任务 6：实现 PlayerController

**文件：**
- 创建：`player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`

这是核心协调类，连接 MediaController + PlayQueue 并暴露响应式状态。

- [ ] **步骤 1：实现 PlayerController**

```kotlin
package com.zili.android.musicfreeandroid.player.controller

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.player.ext.toMediaItem
import com.zili.android.musicfreeandroid.player.model.PlaybackState
import com.zili.android.musicfreeandroid.player.model.PlayerState
import com.zili.android.musicfreeandroid.player.queue.PlayQueue
import com.zili.android.musicfreeandroid.player.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaController: MediaController? = null
    private var positionUpdateJob: kotlinx.coroutines.Job? = null

    val playQueue = PlayQueue()

    private val _playerState = MutableStateFlow(PlayerState.EMPTY)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffleEnabled: Boolean = false

    /**
     * 连接到 [PlaybackService]。在任何播放操作之前必须调用一次。
     * 可以安全地多次调用 — 如果已连接则后续调用为空操作。
     */
    suspend fun connect() {
        if (mediaController != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val controller = suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
        mediaController = controller
        controller.addListener(playerListener)
        emitState()
    }

    // --- 播放控制 ---

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        emitState()
    }

    /**
     * 播放指定的 [item]。如果不在队列中则自动添加。
     */
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
    }

    /**
     * 替换队列并从 [startIndex] 开始播放。
     */
    fun playQueue(items: List<MusicItem>, startIndex: Int = 0) {
        playQueue.setQueue(items, startIndex)
        if (shuffleEnabled) playQueue.shuffle()
        playQueue.currentItem?.let { setMediaItemAndPlay(it) }
    }

    fun skipToNext() {
        val next = playQueue.next(repeatMode) ?: return
        setMediaItemAndPlay(next)
    }

    fun skipToPrevious() {
        // 如果播放超过 3 秒，重新开始而不是上一曲
        val position = mediaController?.currentPosition ?: 0L
        if (position > 3_000L) {
            mediaController?.seekTo(0L)
            return
        }
        val prev = playQueue.previous(repeatMode) ?: return
        setMediaItemAndPlay(prev)
    }

    fun skipTo(index: Int) {
        val item = playQueue.skipTo(index) ?: return
        setMediaItemAndPlay(item)
    }

    // --- 模式控制 ---

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        emitState()
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        emitState()
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled) {
            playQueue.shuffle()
        } else {
            playQueue.unshuffle()
        }
        emitState()
    }

    // --- 队列操作 ---

    fun addToQueue(item: MusicItem) {
        playQueue.add(item)
    }

    fun addNextInQueue(item: MusicItem) {
        playQueue.addNext(item)
    }

    fun removeFromQueue(index: Int): MusicItem? {
        val wasCurrentItem = playQueue.currentItem
        val newCurrent = playQueue.remove(index)
        // 如果移除的是当前播放曲目，播放新的当前曲目
        if (newCurrent != null && newCurrent != wasCurrentItem) {
            setMediaItemAndPlay(newCurrent)
        } else if (newCurrent == null) {
            mediaController?.stop()
            mediaController?.clearMediaItems()
        }
        emitState()
        return newCurrent
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        playQueue.move(fromIndex, toIndex)
    }

    // --- 生命周期 ---

    fun release() {
        positionUpdateJob?.cancel()
        mediaController?.release()
        mediaController = null
    }

    // --- 内部方法 ---

    private fun setMediaItemAndPlay(item: MusicItem) {
        val controller = mediaController ?: return
        val mediaItem = item.toMediaItem()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                handleTrackEnded()
            }
            emitState()
            updatePositionTracking()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            emitState()
            updatePositionTracking()
        }

        override fun onMediaItemTransition(
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            emitState()
        }
    }

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
                }
                // 否则：保持在结束状态
            }
        }
    }

    private fun updatePositionTracking() {
        val isPlaying = mediaController?.isPlaying == true
        if (isPlaying) {
            startPositionUpdates()
        } else {
            positionUpdateJob?.cancel()
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                emitState()
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun emitState() {
        val controller = mediaController
        _playerState.value = PlayerState(
            currentItem = playQueue.currentItem,
            isPlaying = controller?.isPlaying == true,
            playbackState = controller?.playbackState.toPlaybackState(),
            duration = controller?.duration?.coerceAtLeast(0L) ?: 0L,
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
        )
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 200L
    }
}

private fun Int?.toPlaybackState(): PlaybackState = when (this) {
    Player.STATE_IDLE -> PlaybackState.IDLE
    Player.STATE_BUFFERING -> PlaybackState.BUFFERING
    Player.STATE_READY -> PlaybackState.READY
    Player.STATE_ENDED -> PlaybackState.ENDED
    else -> PlaybackState.IDLE
}
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew :player:assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/controller/
git commit -m "feat(player): add PlayerController with MediaController, PlayQueue, and StateFlow"
```

---

## 任务 7：实现 PlayerModule（Hilt DI）

**文件：**
- 创建：`player/src/main/java/com/zili/android/musicfreeandroid/player/di/PlayerModule.kt`

- [ ] **步骤 1：实现 PlayerModule**

`PlayerController` 已经是 `@Singleton` + `@Inject constructor`，Hilt 可直接提供。模块用于未来扩展绑定：

```kotlin
package com.zili.android.musicfreeandroid.player.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    // PlayerController 通过 @Inject constructor 提供。
    // 后续可在此添加播放器相关的额外绑定。
}
```

- [ ] **步骤 2：验证完整项目编译**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：提交**

```bash
git add player/src/main/java/com/zili/android/musicfreeandroid/player/di/
git commit -m "feat(player): add PlayerModule Hilt DI module"
```

---

## 任务 8：集成测试 — PlaybackService

**文件：**
- 创建：`player/src/androidTest/java/com/zili/android/musicfreeandroid/player/service/PlaybackServiceTest.kt`
- 创建：`player/src/androidTest/res/raw/test_audio.mp3`（约 1 秒的静音 MP3）

- [ ] **步骤 1：生成短静音测试音频文件**

运行（从项目根目录）：
```bash
# 使用 ffmpeg 生成 1 秒的静音 MP3
mkdir -p player/src/androidTest/res/raw
ffmpeg -f lavfi -i anullsrc=r=44100:cl=mono -t 1 -q:a 9 -y player/src/androidTest/res/raw/test_audio.mp3
```

如果没有 ffmpeg，可以手动创建最小 WAV 或下载公共领域的静音音频文件。只需要一个有效的可播放音频文件即可。

- [ ] **步骤 2：编写 PlaybackService 集成测试**

```kotlin
package com.zili.android.musicfreeandroid.player.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var controller: MediaController? = null

    @Before
    fun setUp() = runTest {
        controller = connectController()
    }

    @After
    fun tearDown() {
        controller?.release()
    }

    @Test
    fun serviceReturnsValidMediaSession() {
        assertNotNull(controller)
        assertTrue(controller!!.isConnected)
    }

    @Test
    fun playerStartsInIdleState() {
        assertEquals(Player.STATE_IDLE, controller!!.playbackState)
        assertFalse(controller!!.isPlaying)
    }

    @Test
    fun setMediaItemAndPrepareTransitionsToReady() {
        val latch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) latch.countDown()
            }
        })

        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        controller!!.setMediaItem(MediaItem.fromUri(uri))
        controller!!.prepare()

        assertTrue("等待 READY 状态超时", latch.await(5, TimeUnit.SECONDS))
        assertEquals(Player.STATE_READY, controller!!.playbackState)
    }

    @Test
    fun playAndPauseWork() {
        val readyLatch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        controller!!.setMediaItem(MediaItem.fromUri(uri))
        controller!!.prepare()
        controller!!.play()

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        assertTrue(controller!!.playWhenReady)

        controller!!.pause()
        assertFalse(controller!!.playWhenReady)
    }

    @Test
    fun mediaSessionMetadataReflectsCurrentTrack() {
        val readyLatch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        val mediaItem = MediaItem.Builder()
            .setMediaId("test:1")
            .setUri("android.resource://${context.packageName}/${
                com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
            }")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Test Song")
                    .setArtist("Test Artist")
                    .build()
            )
            .build()

        controller!!.setMediaItem(mediaItem)
        controller!!.prepare()

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))

        // 验证 MediaSession 暴露的元数据
        val metadata = controller!!.mediaMetadata
        assertEquals("Test Song", metadata.title?.toString())
        assertEquals("Test Artist", metadata.artist?.toString())
    }

    @Test
    fun audioFocusLossPausesPlayback() {
        val readyLatch = CountDownLatch(1)
        controller!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        val uri = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }"
        controller!!.setMediaItem(MediaItem.fromUri(uri))
        controller!!.prepare()
        controller!!.play()
        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        assertTrue(controller!!.playWhenReady)

        // 模拟音频焦点丢失（如来电中断）
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val focusResult = audioManager.requestAudioFocus(
            android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .build()
        )
        // ExoPlayer 设置了 handleAudioFocus=true，应自动暂停
        // 注意：在某些测试环境中音频焦点可能不完全生效
        // 如果此测试不稳定，可用 @Ignore 标记并在真机上手动验证
        Thread.sleep(500)
        // 验证 ExoPlayer 响应了焦点丢失（playWhenReady 应为 false）
        assertFalse(
            "音频焦点丢失后 playWhenReady 应为 false",
            controller!!.playWhenReady
        )
    }

    private suspend fun connectController(): MediaController =
        suspendCancellableCoroutine { cont ->
            val token = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java),
            )
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                MoreExecutors.directExecutor(),
            )
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
}
```

**注意：** 测试资源引用（`R.raw.test_audio`）使用测试包的 R 类。如果资源引用无法解析，调整 URI 构造方式直接使用应用包名。重要的是提供有效的可播放音频 URI。

- [ ] **步骤 3：运行集成测试（需要模拟器/真机）**

运行：`./gradlew :player:connectedDebugAndroidTest --tests "*.PlaybackServiceTest"`
预期：PASS（6 个测试）

- [ ] **步骤 4：提交**

```bash
git add player/src/androidTest/
git commit -m "test(player): add PlaybackService integration tests"
```

---

## 任务 9：集成测试 — PlayerController

**文件：**
- 创建：`player/src/androidTest/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerTest.kt`

- [ ] **步骤 1：编写 PlayerController 集成测试**

```kotlin
package com.zili.android.musicfreeandroid.player.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.turbineScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.player.model.PlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: PlayerController

    private fun testItem(id: String) = MusicItem(
        id = id, platform = "test", title = "Song $id",
        artist = "Artist", album = null, duration = 1_000L,
        url = "android.resource://${context.packageName}/${
            com.zili.android.musicfreeandroid.player.test.R.raw.test_audio
        }",
        artwork = null, qualities = null,
    )

    @Before
    fun setUp() = runTest {
        controller = PlayerController(context)
        controller.connect()
    }

    @After
    fun tearDown() {
        controller.release()
    }

    @Test
    fun initialStateIsEmpty() {
        val state = controller.playerState.value
        assertNull(state.currentItem)
        assertFalse(state.isPlaying)
        assertEquals(PlaybackState.IDLE, state.playbackState)
    }

    @Test
    fun playQueueSetsCurrentItemAndPlays() = runTest {
        turbineScope {
            val states = controller.playerState.testIn(this)
            states.skipItems(1) // 跳过初始 EMPTY 状态

            controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 0)

            // 等待包含 currentItem 的状态
            val playingState = states.awaitItem()
            assertNotNull(playingState.currentItem)
            assertEquals("1", playingState.currentItem?.id)

            states.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pauseAndResumeWork() = runTest {
        controller.playQueue(listOf(testItem("1")), startIndex = 0)
        // 等待播放开始
        kotlinx.coroutines.delay(500)

        controller.pause()
        kotlinx.coroutines.delay(100)
        assertFalse(controller.playerState.value.isPlaying)

        controller.play()
        kotlinx.coroutines.delay(100)
        assertTrue(controller.playerState.value.isPlaying)
    }

    @Test
    fun skipToNextAdvancesQueue() = runTest {
        controller.playQueue(
            listOf(testItem("1"), testItem("2"), testItem("3")),
            startIndex = 0,
        )
        kotlinx.coroutines.delay(500)

        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("2", controller.playerState.value.currentItem?.id)

        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("3", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun skipToPreviousGoesBack() = runTest {
        controller.playQueue(
            listOf(testItem("1"), testItem("2"), testItem("3")),
            startIndex = 2,
        )
        kotlinx.coroutines.delay(500)

        controller.skipToPrevious()
        kotlinx.coroutines.delay(200)
        assertEquals("2", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun repeatModeAffectsNavigation() = runTest {
        controller.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 1)
        kotlinx.coroutines.delay(500)

        // OFF 模式下，末尾跳下一曲不生效
        controller.setRepeatMode(RepeatMode.OFF)
        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("2", controller.playerState.value.currentItem?.id)

        // ALL 模式下，末尾跳下一曲回到第一首
        controller.setRepeatMode(RepeatMode.ALL)
        controller.skipToNext()
        kotlinx.coroutines.delay(200)
        assertEquals("1", controller.playerState.value.currentItem?.id)
    }

    @Test
    fun shuffleToggleShufflesAndRestoresQueue() = runTest {
        val items = (1..10).map { testItem(it.toString()) }
        controller.playQueue(items, startIndex = 0)
        kotlinx.coroutines.delay(200)

        controller.toggleShuffle()
        assertTrue(controller.playerState.value.shuffleEnabled)
        assertEquals("1", controller.playerState.value.currentItem?.id) // 当前曲目保持不变

        controller.toggleShuffle()
        assertFalse(controller.playerState.value.shuffleEnabled)
        // 恢复原始顺序
        assertEquals(items.map { it.id }, controller.playQueue.items.map { it.id })
    }
}
```

- [ ] **步骤 2：运行集成测试（需要模拟器/真机）**

运行：`./gradlew :player:connectedDebugAndroidTest --tests "*.PlayerControllerTest"`
预期：PASS（6 个测试）

- [ ] **步骤 3：提交**

```bash
git add player/src/androidTest/java/com/zili/android/musicfreeandroid/player/controller/
git commit -m "test(player): add PlayerController integration tests"
```

---

## 任务 10：将 Player 模块接入 App

**文件：**
- 修改：`app/build.gradle.kts`（如果未包含 `:player` 依赖则添加）
- 验证完整构建

- [ ] **步骤 1：确保 app 模块依赖 :player**

检查 `app/build.gradle.kts` 中是否有 `implementation(project(":player"))`。如果缺少，在 dependencies 块中添加。

- [ ] **步骤 2：运行完整项目构建**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：运行所有 player 单元测试**

运行：`./gradlew :player:testDebugUnitTest`
预期：全部 PASS

- [ ] **步骤 4：提交（如有修改）**

```bash
git add app/build.gradle.kts
git commit -m "chore(app): wire player module dependency"
```

---

## 验证命令汇总

所有任务完成后，运行完整验证：

```bash
# 单元测试
./gradlew :player:testDebugUnitTest

# 完整项目构建
./gradlew assembleDebug

# 集成测试（需要模拟器/真机）
./gradlew :player:connectedDebugAndroidTest
```

预期结果：
- 所有单元测试通过（PlayQueueTest、PlayerStateTest、MusicItemMediaExtTest）
- 完整项目编译成功
- 集成测试通过（PlaybackServiceTest 含 MediaSession 元数据和音频焦点测试、PlayerControllerTest）
