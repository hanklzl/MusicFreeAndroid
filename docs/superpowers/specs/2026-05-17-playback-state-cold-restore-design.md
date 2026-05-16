# 冷启动播放状态恢复设计

> 文档状态：当前规范（Player 域 spec）
> 适用范围：`:app:bootstrap` 启动协调器、`:player:controller` `PlayerController`、`:player:service` `PlaybackService`、`:data:datastore` `AppPreferences`、`:feature:player-ui` mini player UI 流
> 直接执行：是
> 最后校验：2026-05-17
> 关联 dev-harness：[player/rules.md](../../dev-harness/player/rules.md)

## 1. 背景与问题

### 1.1 反馈症状

用户反馈：冷启动后底部 mini player **没有记住上次退出时的播放进度**，并且**点击播放按钮无响应**。

期望行为：

- mini player 在冷启动后能记住上次播放位置（封面 / 标题 / 进度条停在上次位置）。
- 点击播放按钮能从上次位置继续播放，不需要先切歌或者执行其它操作。

### 1.2 根因分析（独立两条）

**问题 A — 进度未持久化**

- `app/.../bootstrap/PlaybackStartupCoordinator.kt` 仅在 `queueState.drop(1).collect` 中持久化队列（`PlayQueueRepository.saveQueue`）和当前索引（`AppPreferences.setCurrentMusicIndex`）。
- 整个仓库没有任何路径把 `currentPosition` / `duration` 写入 DataStore。
- 启动恢复时只读 `currentMusicIndex`，因此即便队列恢复正确，也只能从 `0ms` 开始。

**问题 B — 冷启动后 `play()` 是 no-op**

- `PlayerController.restoreQueue(items, startIndex, playWhenRestored=false)` 走的是"懒分支"：只调用 `playQueue.setQueue(...)` + `emitState()` + `emitQueueState()`。
- 该分支**没有调用 `setMediaItem(...) + prepare()`**，底层 `MediaController` 没有任何 `MediaItem`。
- 用户点击 mini player 播放按钮 → `PlayerViewModel.togglePlayPause()` → `playerController.play()` → `mediaController.play()`，但在没有 MediaItem 时 `play()` 是 no-op，UI 既不切换为播放态，也不出声。

### 1.3 RN 参照行为

`MusicFree/src/core/trackPlayer/index.ts::setupTrackPlayer`：

- 启动时同时读取 `PersistStatus["music.playList" | "music.musicItem" | "music.progress" | "music.quality" | "music.rate" | "music.repeatMode"]`。
- 调用 `addAll(musicQueue, ..., shuffle)` 重建队列，对 currentMusic 调用 `setCurrentMusic(track)`。
- 若 `autoPlayWhenAppStart=false`，对 track 打 `isInit=true` 标记；同时**仍然**通过 `getMediaSource → setTrackSource(track, false)` 把源 prepare 好，然后 `seekTo(progress)`。
- 用户点 play 时直接 `play()`，无需再次 prepare → 即点即播。

Android 侧选择**懒准备**（仅记录 `pendingRestorePosition`），见 §3.1 决策。

## 2. 决策摘要

| 维度 | 选择 | 理由 |
|---|---|---|
| 媒体源准备策略 | 懒准备：首次点击 play 才解析 URL 并 prepare | 启动零网络；RN 行为允许提前 prepare，但 Android 上让 `getMediaSource` 在启动阶段同步执行需要插件 + 网络协同，复杂度高。懒准备只要把"未活化的 PlayerState"语义补全即可。 |
| 进度保存频率 | 每 5s 周期 + 关键事件 | 平衡 DataStore 写压力与"强杀丢进度"。最差丢 5s。 |
| 末尾进度策略 | 原位置还原（RN parity） | 不特殊处理，点 play 后可能 1–2s 内自然 transition 到下一首。 |
| Mini player UI | 进度条停在上次位置 | 同时持久化 `duration`，restore 后 `progress = position/duration` 正常计算。 |
| 解析失败处理 | 保留队列与位置，仅弹错 | 用户重试 play 仍按相同语义恢复；不自动跳下一首避免丢恢复语义。 |
| 持久化层 | `AppPreferences` 加两个 `Long` 标量 key | 沿用 DataStore，原子标量 put，写性能友好。 |

## 3. 架构

### 3.1 冷启动 / 首次激活 两阶段

```
冷启动 (Restore phase)                    首次 play tap (Activate phase)
─────────────────────────                  ─────────────────────────────
PlaybackStartupCoordinator                 MiniPlayer ▶
  ├─ read queue + idx + pos + dur            └─ PlayerViewModel.togglePlayPause()
  └─ playerController.restoreQueue(           └─ playerController.play()
        items, startIndex,                       └─ if pendingRestorePosition != null
        savedPositionMs, savedDurationMs,            && currentMediaItem == null:
        playWhenRestored=autoPlay)                 setMediaItemAndPlay(currentItem)
       │                                           // STATE_READY listener
       ├─ playQueue.setQueue(items, idx)           //   seekTo(pendingRestorePosition)
       ├─ pendingRestorePosition = pos             //   pendingRestorePosition = null
       └─ _playerState.value = PlayerState(
             currentItem, isPlaying=false,
             position=pos, duration=dur)
     ↑
     懒：未碰 MediaController
```

`autoPlay = true` 分支（用户在设置里开了"应用启动时自动播放"）等价于：`setMediaItemAndPlay(currentItem)` 后由 STATE_READY listener 消费 `pendingRestorePosition` 完成 seek。两条路径共用同一段 STATE_READY 消费逻辑。

### 3.2 持久化稳态保存

```
playerController.playerState (StateFlow<PlayerState>)
  └─ PlaybackStartupCoordinator 内 saveLoop
     ├─ tick 5s：若 isPlaying && Δposition ≥ 1s → write position
     ├─ isPlaying 边沿 true→false → flush position
     ├─ currentItem key 变化 → flush 旧曲终态 + 写新曲 duration
     └─ duration 首次 > 0 且与持久化值不同 → write duration

PlaybackService
  ├─ onTaskRemoved → flushLastPosition()  (best-effort, 200ms timeout)
  └─ onDestroy    → flushLastPosition()
```

`flushLastPosition()` 从 `playerController.playerState.value` 取 snapshot，不依赖 `mediaController`（service 销毁时已释放）。

## 4. 组件改动

### 4.1 `data/.../datastore/AppPreferences.kt`

新增：

```kotlin
val currentMusicPositionMs: Flow<Long>
suspend fun setCurrentMusicPositionMs(value: Long)
val currentMusicDurationMs: Flow<Long>
suspend fun setCurrentMusicDurationMs(value: Long)
```

- 默认值均为 `0L`。
- DataStore key 命名沿用现有 snake_case 习惯，例如 `current_music_position_ms` / `current_music_duration_ms`。

### 4.2 `player/.../controller/PlayerController.kt`

1. **`restoreQueue` 签名扩展**：

   ```kotlin
   fun restoreQueue(
       items: List<MusicItem>,
       startIndex: Int = 0,
       savedPositionMs: Long = 0L,
       savedDurationMs: Long = 0L,
       playWhenRestored: Boolean = false,
   )
   ```

2. **新增字段** `@Volatile private var pendingRestorePosition: Long? = null`（仅在 controller 线程访问）。

3. **`restoreQueue` 实现**：
   - `playQueue.setQueue(items, startIndex)`
   - `pendingRestorePosition = savedPositionMs.takeIf { it > 0L }`
   - 懒分支：`_playerState.value = PlayerState(currentItem, isPlaying=false, position=savedPositionMs, duration=savedDurationMs, repeatMode, shuffleEnabled, playbackSpeed)`；`emitQueueState()`。
   - autoPlay 分支：调用 `setMediaItemAndPlay(currentItem)`。`pendingRestorePosition` 留给 STATE_READY 监听消费。

4. **`play()` 修改**：

   ```kotlin
   fun play() {
       withConnectedController { controller ->
           if (pendingRestorePosition != null && controller.currentMediaItem == null) {
               playQueue.currentItem?.let { setMediaItemAndPlay(it) }
           } else {
               controller.play()
           }
       }
   }
   ```

5. **`PlayerListener.onPlaybackStateChanged(state)`**：当 `state == Player.STATE_READY && pendingRestorePosition != null`：
   - `val target = pendingRestorePosition!!.coerceIn(0L, controller.duration.takeIf{it>0L} ?: pendingRestorePosition!!)`
   - `controller.seekTo(target); pendingRestorePosition = null`
   - 仅消费一次，下次 STATE_READY 不再 seek。

6. **`seekTo(positionMs)` 修改**：若 `mediaController?.currentMediaItem == null && pendingRestorePosition != null`：更新 `pendingRestorePosition = positionMs`，并 `_playerState.value = _playerState.value.copy(position = positionMs)`。否则走原 `controller.seekTo`。

7. **清零入口**：在 `setMediaItemAndPlay`、`playItem`、`playQueue(items, ...)`、`skipToNext`、`skipToPrevious`、`skipTo(index)`、`reset`、`removeFromQueue`（命中当前 item 时）入口处把 `pendingRestorePosition` 清掉，除非该路径自身就是 restore 激活路径（即 §4.2.4 的 `play()` 调到 `setMediaItemAndPlay` 那条）。
   - 实现技巧：在 `setMediaItemAndPlay` 入口判定"是否是 lazy activation"——若 `pendingRestorePosition != null` 且 currentItem 与即将播放的 item 同 key，则保留；否则清零。

### 4.3 `app/.../bootstrap/PlaybackStartupCoordinator.kt`

1. **`start()` restore 部分**：

   ```kotlin
   val savedPosition = appPreferences.currentMusicPositionMs.first()
   val savedDuration = appPreferences.currentMusicDurationMs.first()
   playerController.restoreQueue(
       items = queue, startIndex = startIndex,
       savedPositionMs = savedPosition,
       savedDurationMs = savedDuration,
       playWhenRestored = autoPlay,
   )
   ```

2. **新增 save loop**：扩展现有的 `applicationScope.launch(Dispatchers.IO) { ... }`。在原本 `playerController.queueState.drop(1).collect` 之外，**并行**起一段 `playerController.playerState.collect`，内部维护：
   - `var lastPersistedPosition: Long = savedPosition`
   - `var lastPersistedDuration: Long = savedDuration`
   - `var lastItemKey: String? = currentItem?.key`
   - `var lastTickAt: Long = System.currentTimeMillis()`
   - 节流规则：
     - position 写：`isPlaying && (now - lastTickAt) >= 5_000L && |state.position - lastPersistedPosition| >= 1_000L`
     - 边沿写：`wasPlaying && !isPlaying`（pause / stop / song end 等）
     - item key 变化：先把 `lastPersistedPosition` 写出（实际是上一首的终态），再切到新 item，清零 lastPersistedPosition for new item（下一次 tick 重新校准）
     - duration 写：`state.duration > 0L && state.duration != lastPersistedDuration`
   - 写失败 catch：`MfLog.error("playback_position_persist_failed", throwable, ...)`，不中断 collector。

   实现上为简洁起见，可以拆出一个 internal `PlaybackStateRecorder` 类承载该 collector 逻辑（注入 `appPreferences`），coordinator 仅 launch 它。是否拆视代码体量决定，**不强制**。

### 4.4 `player/.../service/PlaybackService.kt`

新增 `flushLastPosition()`：

```kotlin
private fun flushLastPosition() {
    val state = playerController.playerState.value
    if (state.duration <= 0L && state.position <= 0L) return
    runBlocking {
        withTimeoutOrNull(200L) {
            appPreferences.setCurrentMusicPositionMs(state.position)
            appPreferences.setCurrentMusicDurationMs(state.duration)
        }
    }
}
```

- 在 `onTaskRemoved` / `onDestroy` 已有清理逻辑之前调用一次。
- 注意 service 与 controller 的 DI：`AppPreferences` 已经在 `:data` 暴露给 `:app`，service 需要新增 inject。如果产生跨模块依赖问题，回退到由 `PlayerController` 在 `release()` 中向已注入的回调（一个 lambda `flushCallback: (Long, Long) -> Unit`）通报终态，让 coordinator 落盘。**首选 service 直接注入 AppPreferences**；若 Hilt 装配复杂，使用回调方案。

### 4.5 `feature/player-ui/.../component/MiniPlayer.kt`

`toMiniPlayerUiModel` **不需要修改**：`progress = if (duration > 0L) position.toFloat() / duration else 0f`。restore 时若 `savedDurationMs > 0L`，进度条立即停在 `savedPositionMs / savedDurationMs` 位置；若历史 duration 没保存（升级第一次启动），则进度条为 0、按钮仍可点。

按钮 icon 由 `isPlaying = false` 决定 → 显示「播放」icon，符合预期。

## 5. 错误处理与边界

| 场景 | 处理 |
|---|---|
| 插件 / 网络解析失败（首次 play） | `setMediaItemAndPlay → resolvePlayableItem` 走现有 rollback；`_errorEvents.tryEmit("播放失败: …")`；`pendingRestorePosition` **不清零**，用户重试 play 仍按相同语义恢复。 |
| stale-url 自动重试 | 沿用现有 `staleUrlRetryState` / `playFailureSourceRetryState`。若重试成功，STATE_READY 在同一 currentItem 上到达，pendingRestorePosition 仍能被消费。 |
| autoplay=true 且解析失败 | 走现有失败链路（自动 pause）；pendingRestorePosition 留待用户主动 play 时由 STATE_READY 消费。 |
| 用户在 prepare 中途切歌 / 换队列 | 入口清掉 pendingRestorePosition，新歌从 0 开始（"主动放弃恢复"语义）。 |
| 持久化层异常 | catch 记 `playback_position_persist_failed`；保留 in-memory 值，下次 tick 再试。 |
| onTaskRemoved 时 mediaController 已释放 | flushLastPosition 读 `playerState.value` 缓存值，不依赖 mediaController。 |
| 进程被强杀 | 最差丢 5s 进度，符合预期。 |
| 持久 position 大于实际 duration（quality 切换导致 duration 缩短等） | STATE_READY 消费时 `coerceIn(0, controller.duration)`。 |
| 升级首次启动 | 旧用户 DataStore 中无新 key → 走默认 0L → mini player 进度条为 0，play 后正常播放新 item。无回归。 |

## 6. 测试方案

### 6.1 单测

`player/src/test/.../controller/PlayerControllerQueueStateTest.kt`（扩展）

- `restoreQueue with savedPosition emits PlayerState position=saved & duration=saved, no setMediaItem`
- `play after lazy restoreQueue triggers setMediaItemAndPlay`
- `STATE_READY after lazy activate seeks to pendingRestorePosition and clears it`
- `subsequent STATE_READY events do not re-seek`
- `seekTo before activation updates _playerState.position and pendingRestorePosition`
- `skipToNext/Previous/playItem/playQueue/skipTo/reset clear pendingRestorePosition`
- `restoreQueue with playWhenRestored=true also seeks to savedPosition after STATE_READY`
- `STATE_READY coerces pendingRestorePosition into [0, duration]`

`app/src/test/.../bootstrap/PlaybackStartupCoordinatorTest.kt`（新建或扩展）

- `start reads position+duration and passes to restoreQueue`
- `playerState position checkpoint writes every 5s during playback`
- `isPlaying transition true→false flushes position immediately`
- `currentItem change flushes old item position and writes new item duration`
- `DataStore write failure logs error and does not crash collector`

`data/src/test/.../datastore/AppPreferencesTest.kt`

- `currentMusicPositionMs / currentMusicDurationMs default to 0 and round-trip`

`feature/player-ui/src/test/.../component/MiniPlayerContentTest.kt`（扩展）

- `restored state (position>0, duration>0, isPlaying=false) renders correct progress and play icon`

### 6.2 Instrumentation

`player/src/androidTest/.../PlayerControllerTest.kt`（扩展，不在主线程 runBlocking connect，遵守 `rule-no-runblocking-mainthread-in-instrumentation`）

- `restoreQueue (lazy) + play() activates source and seeks to saved position` —— 使用真实 MediaSessionService + fake source factory，断言 `controller.currentMediaItem != null` 且 `controller.currentPosition` 接近 savedPositionMs。

### 6.3 手工验收

- 冷启动后：mini player 显示上次歌曲与进度条停在上次位置、按钮显示「播放」、点击 → 1–2s 内开始从上次位置播。
- 离线本地音乐：同上，且首次 play 无明显等待。
- 边界：上次进度 = duration-1s → restore 后点 play，播 1s 自动跳下一首。
- 进程被强杀（开发者选项强停）→ 重启最多丢 5s。
- 飞行模式 + 上次是插件远程源 → 点 play 弹错误，再点 play 仍走相同恢复语义。

### 6.4 回归命令

```
./gradlew :data:testDebugUnitTest --no-daemon
./gradlew :player:testDebugUnitTest --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

## 7. Out of Scope

- 不引入"应用启动自动播放"行为变更（沿用 `autoPlayWhenAppStart` 默认 false）。
- 不在本 spec 内调整 `PlayQueueRepository` 数据格式或迁移策略。
- 不实现"播放历史"或"上次音质"额外持久化（RN 里有 `music.quality`，已存在于现有 quality 子系统中，独立路径）。
- 不修改 mini player 的视觉/动画规范。

## 8. 风险与回滚

- **风险点**：`pendingRestorePosition` 状态机分支错漏，导致用户主动切歌后被旧位置 seek 污染。**缓解**：所有切歌入口集中清零 + 单测覆盖。
- **风险点**：`PlaybackService.flushLastPosition` 阻塞主线程超过预期。**缓解**：`withTimeoutOrNull(200L)` 硬上界 + 失败仅记日志。
- **回滚策略**：feature 改动集中在 `PlayerController` + `PlaybackStartupCoordinator` + `AppPreferences`，单 commit 内可 revert。DataStore 新 key 默认 0L 对未升级用户无影响。
