---
status: 当前规范（实现进行中）
applies_to: feature/player-ui PlayerOperationsBar、PlayerController、AppPreferences
created: 2026-05-10
owner: fix/player-page-actions
references:
  - ../../../feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt
  - ../../../feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt
  - ../../../player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt
  - ../../../downloader/src/main/java/com/hank/musicfree/downloader/Downloader.kt
  - ../../../../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx
  - ../../../../MusicFree/src/components/panels/types/musicQuality.tsx
  - ../../../../MusicFree/src/components/panels/types/playRate.tsx
---

# 播放页操作栏修复设计

修复全屏播放页 `PlayerOperationsBar` 操作栏 4 个失效/缺失能力，对齐 RN `pages/musicDetail/components/content/albumCover/operations.tsx`。

## 问题清单与定位

| # | 问题 | 现状 | 定位 |
| --- | --- | --- | --- |
| 1 | 点击收藏会崩溃 | `viewModel.toggleCurrentFavorite()` 无异常处理；`isCurrentFavorite` flow 未对 currentItem 去重，每 200ms 重订一次 Room flow | `PlayerViewModel.kt:110-121` |
| 2 | 无法切换品质 | 只有静态 `Text("标准")`，未读取 `AppPreferences.playQuality`，无 picker，无 controller 方法 | `PlayerScreen.kt:545-549` |
| 3 | 无法下载 | `IconButton(onClick = {})` 空回调；`Downloader.enqueue` 已具备但未注入 ViewModel | `PlayerScreen.kt:550-557` |
| 4 | 无法切换播放速率 | 只有静态 `Text("1.0x")`，无 picker，PlayerState/PlayerController 无 speed 字段或方法 | `PlayerScreen.kt:558-562` |

## 根因 / 范围确认

- **Issue 1 — 收藏崩溃**：纯代码审查未发现确定性 NPE/SQLite 异常路径（DAO suspend、cover sync 走 Dispatchers.IO、SeedFavoriteCallback 已注册）。但存在两个真实代码气味：
  1. `toggleCurrentFavorite` 完全无 try/catch，任何 DB 异常会随 `viewModelScope` 未捕获异常路径冒泡，被 Hilt VM 默认 handler 转给 Android 全局 uncaught handler → 崩溃。
  2. `isCurrentFavorite` flow 没有 `distinctUntilChangedBy { it?.platform to it?.id }` 去重；播放时每 200ms 重订一次 Room flow（同模块 `rawLyricLoadState` 已用此 pattern，line 64）。这能加剧 Room/Coroutine 调度压力。
  
  方案：补上去重（消除已知性能/稳定性气味）+ 包 try/catch 在 `toggleCurrentFavorite()`，错误转 `errorEvents` toast。这两个改动对正常路径无影响，但能把"看不见的崩溃"变成"可见的 toast"，下次复现时立刻有信号。
- **Issue 2/3/4**：纯 UI/Controller 接线缺失，所有底层基础（`MediaSourceResolver.resolve(item, quality)`、`Downloader.enqueue(items, quality)`、Media3 `Player.setPlaybackParameters`、`AppPreferences.playQuality`）都已具备。

## 总体方案

| 项 | 决策 | 备注 |
| --- | --- | --- |
| 操作栏改造 | 保留现有 `PlayerOperationsBar` 6 槽位布局；用 `ModalBottomSheet` 实现品质 / 速率 picker（与 `PlayQueueSheet` 同宿 `feature:player-ui`） | RN 用全局 panel，Android 落地为 ModalBottomSheet |
| 品质显示 | 当前选中品质从 `AppPreferences.playQuality` 读取，文本显示中文（"标准"/"高音质"等），与 RN `qualityText` 对齐 | 暂不引入图标 asset，沿用文字（保持现状一致），后续可独立任务接入图标 |
| 品质切换 | `PlayerController.changeQuality(quality)`：拉取新 URL 后调 `setMediaItem` + `prepare`；保留当前 position 和 isPlaying；失败 toast | 不要求重连 MediaController；遵循现有 `withConnectedController` pattern |
| 速率显示 | 当前速率从 `AppPreferences.playRate` 读取，格式 `${rate}x`（如 `1.0x`、`1.5x`） | 新增 preference（默认 1.0f） |
| 速率切换 | `PlayerController.setPlaybackSpeed(speed)`：调 `controller.setPlaybackParameters(PlaybackParameters(speed))` 并写 `_playerState.value.copy(playbackSpeed=...)` | Media3 setPlaybackParameters 不会暂停播放 |
| 下载 | 注入 `Downloader` 到 `PlayerViewModel`；点击时弹品质 picker（type=download），用户选品质后 `downloader.enqueue(listOf(currentItem), quality)`，toast "已加入下载队列" | RN 用同一个 `MusicQuality` panel；这里我们用一个公用的 `MusicQualitySheet` composable，`mode = play | download` 区分 |
| 下载完成态 | UI 显示 `ic_check_circle` 替换 `ic_arrow_down_tray`，对齐 RN `isDownloaded ? check-circle-outline : arrow-down-tray` | 通过 `Downloader.downloadedKeys` StateFlow 派生 |

## 关键模块改动概览

### `core` 模块
- 新增 `PlaybackSpeed` 常量列表（`0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f`），与 RN `rates [50,75,100,125,150,175,200]` 对齐。

### `data` 模块
- `AppPreferences`：补 `playRate: Flow<Float>` + `setPlayRate(rate: Float)`，默认 1.0f。

### `player` 模块
- `PlayerState`：补 `playbackSpeed: Float = 1.0f` 字段；EMPTY 默认 1.0f。
- `PlayerController`：
  - `changeQuality(quality: PlayQuality)`：在 controller 线程内：
    1. 当前 currentItem 为空 → no-op
    2. 暂存 `currentPosition` 和 `isPlaying`
    3. 异步调 `mediaSourceResolver.resolve(item, quality.wireName())`：成功 → `setMediaItem(newMediaItem)` → `prepare()` → `seekTo(position)` → `play()` if was playing；失败 → `_errorEvents.emit("当前歌曲不支持该音质")`
  - `setPlaybackSpeed(speed: Float)`：`controller.setPlaybackParameters(PlaybackParameters(speed))` → `_playerState.value = _playerState.value.copy(playbackSpeed=speed)`
  - `emitState()` 同时携带 `playbackSpeed`（基于 `mediaController?.playbackParameters?.speed ?: 1.0f`）

### `feature/player-ui` 模块
- `PlayerViewModel`：
  - 注入 `Downloader`（构造参数）
  - 注入已有 `appPreferences`（已存在）
  - `currentQuality: StateFlow<PlayQuality>` ← `appPreferences.playQuality.stateIn(...)`
  - `currentSpeed: StateFlow<Float>` ← `appPreferences.playRate.stateIn(...)`
  - `isCurrentDownloaded: StateFlow<Boolean>` ← combine(playerState, downloader.downloadedKeys) { state, keys -> state.currentItem?.let { keys.contains(MediaKey(it.platform, it.id)) } ?: false }
  - `setCurrentQuality(q: PlayQuality)`：`viewModelScope.launch { appPreferences.setPlayQuality(q); playerController.changeQuality(q) }`
  - `setPlaybackSpeed(s: Float)`：`viewModelScope.launch { appPreferences.setPlayRate(s); playerController.setPlaybackSpeed(s) }`
  - `downloadCurrent(quality: PlayQuality)`：`val item = playerState.value.currentItem ?: return; downloader.enqueue(listOf(item), quality)`
  - **修复 Issue 1**：在 `isCurrentFavorite` flow 加 `distinctUntilChangedBy { it?.let { item -> item.platform to item.id } }`；在 `toggleCurrentFavorite()` 包 try/catch，捕获到的异常通过 `_internalErrorEvents` `MutableSharedFlow<String>` 发射，UI 侧合并到现有 errorEvents（PlayerScreen 已经 collect errorEvents 转 toast）。
- 新增 `MusicQualitySheet` composable（`feature/player-ui/component/quality/MusicQualitySheet.kt`）：
  - 参数：`current: PlayQuality?, mode: Mode`（Play / Download）, `availableQualities: Map<PlayQuality, QualityInfo>?, onSelect: (PlayQuality) -> Unit, onDismiss`
  - 列出 4 个品质（low/standard/high/super），中文标签，附带 `(size)` 字符串（若 `qualities[q].size != null`）
  - 当前选中加 check 图标
- 新增 `PlayRateSheet` composable（`feature/player-ui/component/rate/PlayRateSheet.kt`）：
  - 参数：`current: Float, onSelect: (Float) -> Unit, onDismiss`
  - 列出 7 档（0.5x..2.0x），当前选中加 check 图标
- `PlayerScreen` / `PlayerOperationsBar`：
  - 接受新参数：`currentQuality, onQualityClick, isDownloaded, onDownloadClick, currentSpeed, onSpeedClick`
  - 品质槽位文字改为 `qualityLabel(currentQuality)` 并 `clickable`
  - 下载图标根据 `isDownloaded` 切换；点击触发下载 picker
  - 速率槽位文字改为 `"${formatRate(currentSpeed)}x"` 并 `clickable`
  - PlayerScreen 顶层维护 `var qualitySheetTrigger: QualitySheetTrigger? by remember`（mode = Play / Download）和 `var rateSheetVisible by remember`，根据触发器渲染对应 ModalBottomSheet

### Hilt
- `PlayerViewModel` 构造已经被 `@HiltViewModel`；只需补 `private val downloader: Downloader`，确保 `:feature:player-ui` 依赖 `:downloader` 模块（看 build.gradle.kts 是否已加；若未加需添加）。

## 测试策略

| 类别 | 用例 | 位置 |
| --- | --- | --- |
| Unit (PlayerViewModelTest) | `currentQuality` 反映 `appPreferences.playQuality` | `feature/player-ui/src/test/java/.../PlayerViewModelTest.kt` 追加 |
| Unit | `setCurrentQuality` 同时写 prefs 和调 controller.changeQuality | 同上 |
| Unit | `setPlaybackSpeed` 同时写 prefs 和调 controller.setPlaybackSpeed | 同上 |
| Unit | `downloadCurrent` 调用 `downloader.enqueue(listOf(item), quality)` | 同上 |
| Unit | `isCurrentFavorite` 在同一 currentItem 多次发射时只调 isFavorite 一次 | 同上 |
| Unit | `toggleCurrentFavorite` 捕获异常并发射到 errorEvents | 同上 |
| Unit (PlayerControllerTest) | `changeQuality` 在 currentItem 为空时 no-op | `player/src/test/java/.../PlayerControllerTest.kt` 新建（若缺） |
| Unit | `setPlaybackSpeed` 调用 controller.setPlaybackParameters 并写 PlayerState.playbackSpeed | 同上 |
| Unit (Robolectric Compose) | `MusicQualitySheet` 显示 4 个品质，选中项有 check | `feature/player-ui/src/test/java/.../component/quality/MusicQualitySheetTest.kt` |
| Unit (Robolectric Compose) | `PlayRateSheet` 显示 7 档，选中项有 check | `feature/player-ui/src/test/java/.../component/rate/PlayRateSheetTest.kt` |

不打算引入 instrumentation/connected test（避免 connect controller hang，遵循 player rules）。

## 验收

1. `./gradlew :app:assembleDebug` 通过
2. `./gradlew :feature:player-ui:testDebugUnitTest :player:testDebugUnitTest :data:testDebugUnitTest` 全过
3. 真机/模拟器手动验证：
   - 收藏：连续点击 5 次不崩溃；切歌时 isFav 状态正确
   - 品质：从 picker 选择"高音质"，播放继续；插件不支持时 toast 提示
   - 下载：选品质后 toast "已加入下载队列"；下次进入页面下载图标变 check
   - 速率：选 1.5x，播放速度立即变化；切歌后保持
4. 沿现有 R8 keep 规则；本次未引入新的反射/序列化类。

## 不在范围

- 品质/速率图标资源（沿用文字显示）
- 下载列表页（已有 `pages/downloading` 单独 plan，本次不接入）
- 下载完成后的"播放本地副本"路径
- 多歌曲批量下载

## 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| 品质切换时插件解析失败 | 解析失败保留原 mediaItem，不重置队列；toast 提示 |
| Media3 PlaybackParameters 在某些 codec 下不支持非 1.0 速率 | 捕获异常并 toast；恢复到 1.0 |
| Downloader 注入触发 Hilt 循环依赖 | `:downloader` 已 Singleton，独立模块；`PlayerViewModel` 已注入 `PlaylistRepository` 等多个 singleton，无循环依赖风险 |
| 收藏 flow 去重导致已存在测试断言失效 | 现有 `PlayerViewModelTest` 用 `MusicItem` 数据类比较，去重不会改变首次发射；新增"重复发射只调一次"测试覆盖 |
