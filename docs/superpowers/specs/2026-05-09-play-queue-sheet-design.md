---
status: 当前规范
branch: feat/play-queue-sheet
parent: main
created: 2026-05-09
---

# Play Queue Sheet 设计（迷你播放器 / 全屏播放器播放列表）

## 背景

当前 MiniPlayer 与 PlayerScreen 都缺少“播放列表”面板：

- `feature/player-ui/.../component/MiniPlayer.kt:38` 把 `onNavigateToQueue ?: {}` 接进 `MiniPlayerContent.onOpenQueue`，目前任何调用方（含 `MainActivity`）都未传入回调，点击图标无任何反应。
- `feature/player-ui/.../PlayerScreen.kt:652` 的播放列表图标 `IconButton(onClick = { /* TODO: 弹出队列 */ })` 是显式 TODO。

原版 RN（`../MusicFree`）在 `components/musicBar` 与 `pages/musicDetail` 都通过 `showPanel("PlayList")` 展示同一面板（`components/panels/types/playList/header.tsx` + `body.tsx`）。本次目标是**严格对齐 RN** 的播放列表面板能力，让两个入口都能弹出同一份面板，并保持与 RN 的交互、文案、字号、行为一致。

## 范围

### 必做

- 抽出共享面板组件 `PlayQueueSheet`，由 MiniPlayer 与 PlayerScreen 各自挂载（路线 A：共享 Composable + 本地 host）。
- 面板内容包含：
  - Header：`播放列表 (N首)` + 循环模式按钮 + 清空按钮。
  - List body：当前歌曲行高亮 + `title - artist` + 平台 tag + `×` 删除按钮 + 行点击切歌；初次打开滚动到当前歌。
- 串通现有 `PlayerController` 能力（`skipTo` / `removeFromQueue` / `cycleRepeatMode` / `reset`）。
- 暴露播放队列的反应式快照：`PlayerController.queueState: StateFlow<PlayQueueSnapshot>`。

### 不做

- 不实现长按拖拽排序（RN 也没有）。
- 不实现删除项的 Snackbar 撤销（RN 也没有）。
- 不为 `IconTextButton` 抽出全局原子组件（仅本面板使用，先内联）。
- 不调整 `PlayQueue` 内部为 Flow 类（保持纯逻辑可单测，由 `PlayerController` 负责 emit）。
- 不修改 miniplayer / PlayerScreen 既有的图标、布局、点击区。

## 非目标的清晰边界

| 维度 | 决定 | 原因 |
|---|---|---|
| 行为对齐 | 严格对齐 RN | 用户明确选择 |
| 面板形态 | M3 `ModalBottomSheet` | 与现有 `AddToPlaylist*` / `PlayerLyricSearchSheet` 一致 |
| 全局 host | 否（每个入口本地 host） | 两入口互斥，不会同时显示；最小新增面 |
| 切歌后 sheet 行为 | 保持打开 | 对齐 RN |
| 清空后 sheet 行为 | 保持打开，显示空态 | 对齐 RN（RN 不自动关）|

## 架构

### 模块边界

仅改动 `:feature:player-ui` 与 `:player`，不跨其它模块。

```
player/.../controller/PlayerController.kt          # 新增 queueState 与 emit；不改方法签名
player/.../queue/PlayQueueSnapshot.kt              # 新增。data class 值类型
feature/player-ui/.../PlayerViewModel.kt           # 新增 queueUiModel 与若干转发方法
feature/player-ui/.../component/queue/
    PlayQueueSheet.kt                              # 新增。ModalBottomSheet 包装
    PlayQueueSheetContent.kt                       # 新增。无副作用版（header + LazyColumn）
    PlayQueueRow.kt                                # 新增。单行 Composable
    PlayQueueUiModel.kt                            # 新增。data class
feature/player-ui/.../component/MiniPlayer.kt      # 内部持 showQueueSheet 状态，挂 PlayQueueSheet
feature/player-ui/.../PlayerScreen.kt              # 第 652 行 TODO 接到 showQueueSheet
```

`MainActivity.kt` 不变（`MiniPlayer` 不再需要 `onNavigateToQueue` 参数）。

### 数据流

```
PlayQueue 变更 (在 PlayerController 各 mutating 方法尾部)
    │
    ▼
PlayerController.emitQueueState()  ──▶  queueState: StateFlow<PlayQueueSnapshot>
                                                        │
                                                        ▼
PlayerViewModel.queueUiModel = combine(queueState, playerState.repeatMode)
    .stateIn(WhileSubscribed(5_000), PlayQueueUiModel.EMPTY)
                                                        │
                                                        ▼
PlayQueueSheet(viewModel) ──collectAsStateWithLifecycle──▶ PlayQueueSheetContent
```

### 类型定义

```kotlin
// :player
data class PlayQueueSnapshot(
    val items: List<MusicItem>,
    val currentIndex: Int,
) {
    companion object { val EMPTY = PlayQueueSnapshot(emptyList(), -1) }
}

// :feature:player-ui
data class PlayQueueUiModel(
    val items: List<MusicItem>,
    val currentIndex: Int,
    val repeatMode: RepeatMode,
) {
    val count: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    val currentItem: MusicItem? get() = items.getOrNull(currentIndex)
    companion object { val EMPTY = PlayQueueUiModel(emptyList(), -1, RepeatMode.OFF) }
}
```

### 在 PlayerController 中 emit 的位置

所有改动都集中在 `PlayerController.kt`，每个会改 `playQueue` 内容或 `currentIndex` 的方法尾部新增 `emitQueueState()` 调用：

- `playItem`、`playQueue(items, ...)`、`addToQueue`、`addNextInQueue`
- `removeFromQueue`、`moveInQueue`、`reset`
- `skipToNext`、`skipToPrevious`、`skipTo`、`toggleShuffle`
- `handleTrackEnded`（队列自然推进时）

`emitQueueState()` 实现：

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

## UI 规格（严格对齐 RN）

### Sheet 容器

- M3 `ModalBottomSheet`，`onDismissRequest = { showQueueSheet = false }`。
- `sheetState = rememberModalBottomSheetState()`（默认半屏，可上拖展开）。
- `containerColor = MusicFreeTheme.colors.backgroundColor`（原版用 `colors.musicBar` 区域之外的标准底色，沿用主题）。
- 内部底部 padding 走 `WindowInsets.navigationBars`，避免被三键栏盖住。

### Header（对应 RN `header.tsx`）

- `Row` 高 `rpx(80)`，`paddingHorizontal = rpx(24)`，外部 margin `top = rpx(18) / bottom = rpx(12)`。
- 左侧：weight=1
  - `Text("播放列表 ", fontSize = FontSizes.title, fontWeight = SemiBold, color = colors.text)` 后接 `Text("(${count}首)", color = colors.textSecondary, fontSize = FontSizes.title, fontWeight = SemiBold)`。两段之间没有额外 margin（RN 用 `<ThemeText>` 内嵌实现）。
- 中：循环模式按钮（内联 `IconTextButton`：垂直 Column，icon 上、文字下，整体 `clickable`）。
  - 三档与 RN `repeatModeConst` 一致：`OFF → 列表循环` / `ALL → 单曲循环` / `ONE → 随机播放`（实际枚举与 i18n 文案以 `core.theme` 与本仓库现有翻译为准；本次直接硬编码三段中文，与既有 PlayerControls 风格一致）。
  - 图标沿用现有 `R.drawable.ic_repeat_song / ic_repeat_song_1 / ic_shuffle`，与 `PlayerControls` 中的映射保持一致。
- 右：清空按钮（同样内联 `IconTextButton`，icon = `R.drawable.ic_trash`，label = "清空"）。
- `IconTextButton` 内部规格：`Column(horizontalAlignment = Center) { Icon(size = rpx(36)) ; Spacer(rpx(4)) ; Text(fontSize = FontSizes.description) }`，整体 `clickable` + `padding(horizontal = rpx(8), vertical = rpx(4))`。

### List body（对应 RN `body.tsx`）

- `LazyColumn(state = rememberLazyListState())` 包在 `Box(Modifier.weight(1f))` 中。
- 初次定位逻辑（仅一次，对齐 RN `useMemo([])`，后续切歌不自动滚动）：
  ```kotlin
  var didInitialScroll by remember { mutableStateOf(false) }
  LaunchedEffect(uiModel.items.size, uiModel.currentIndex) {
      if (!didInitialScroll && uiModel.items.isNotEmpty() && uiModel.currentIndex >= 0) {
          listState.scrollToItem(uiModel.currentIndex)
          didInitialScroll = true
      }
  }
  ```
- 每行 `PlayQueueRow` 高 `rpx(108)`，`paddingHorizontal = rpx(24)`：
  - 当 `isCurrent`：行首 `Icon(painter = R.drawable.ic_music_note, tint = colors.textHighlight)`，size `FontSizes.content`，右侧 margin `rpx(6)`。
  - 主文本：`Row(weight = 1f)` 内 `Text(title, fontSize = FontSizes.content, color = if (isCurrent) colors.textHighlight else colors.text, maxLines = 1, ellipsis)`，紧接 `Text(" - ${artist}", fontSize = FontSizes.description, 同色)`，artist 为空时省略。
  - 平台 tag：`PlatformTag(text = item.platform)`，直接复用 `core/.../ui/PlatformTag.kt`，无需新增依赖路径或下移。`item.platform.isNullOrBlank()` 时不渲染。
  - 行尾：`IconButton(onClick = onRemove, modifier.size(...))` 加载 `R.drawable.ic_xmark`，左 margin `rpx(14)`。
  - 整行 `Modifier.clickable { onPlay(index) }`。**点击行不关闭 sheet**。
- 空态（`items.isEmpty()`）：替换 LazyColumn，渲染 `Box(fillMaxWidth().padding(vertical = rpx(120)), contentAlignment = Center) { Text("暂无歌曲", color = colors.textSecondary) }`。

### 触发点改动

- **MiniPlayer.kt**：
  ```kotlin
  @Composable
  fun MiniPlayer(
      onNavigateToPlayer: () -> Unit,
      modifier: Modifier = Modifier,
      viewModel: PlayerViewModel = hiltViewModel(),
  ) {
      val state by viewModel.playerState.collectAsStateWithLifecycle()
      if (!state.hasMedia) return
      var showQueue by remember { mutableStateOf(false) }
      MiniPlayerContent(
          uiModel = state.toMiniPlayerUiModel(),
          onOpenPlayer = onNavigateToPlayer,
          onTogglePlayPause = viewModel::togglePlayPause,
          onOpenQueue = { showQueue = true },
          onSkipNext = {},
          onSkipPrev = {},
          modifier = modifier,
      )
      if (showQueue) {
          PlayQueueSheet(viewModel = viewModel, onDismiss = { showQueue = false })
      }
  }
  ```
  函数签名移除 `onNavigateToQueue`（`MainActivity` 此前根本没传，此参数纯属遗留，删除符合 CLAUDE.md “不保留未使用参数”原则）。

- **PlayerScreen.kt**：
  - 顶部新增 `var showQueueSheet by remember { mutableStateOf(false) }`。
  - `PlayerControls` 末尾的 `IconButton(onClick = { /* TODO: 弹出队列 */ })` 改为 `onClick = { showQueueSheet = true }`，并通过参数透出。
  - 在 `Box(modifier = Modifier.fillMaxSize())` 末尾追加 `if (showQueueSheet) PlayQueueSheet(viewModel = viewModel, onDismiss = { showQueueSheet = false })`。

### PlayQueueSheet 接线

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayQueueSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val uiModel by viewModel.queueUiModel.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
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

`PlayQueueSheetContent` 是无副作用版本（接 lambda 而非 viewModel），便于 Compose 测试。

### ViewModel 新增方法

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

fun playQueueIndex(index: Int) = playerController.skipTo(index)

fun removeFromQueue(index: Int) {
    playerController.removeFromQueue(index)
}

fun clearQueue() = playerController.reset()
```

`cycleRepeatMode()` 已存在，无需新增。

## 错误处理

- **越界 index**：`PlayQueue.skipTo` / `remove` 越界都返回 null / 不变，UI 不需要额外保护。`itemsIndexed` 保证 index 与列表同步。
- **空队列**：`currentIndex = -1`，`scrollToItem` 因为 `if (currentIndex >= 0)` 分支保护不会触发；UI 进入空态。
- **队列突然变空（清空动作触发）**：`hasMedia` 变 false 后 MiniPlayer 整体 return（既有逻辑 `MiniPlayer.kt:32`），sheet 仍渲染但宿主 MiniPlayer 退出 → ModalBottomSheet 也会被解构。这是符合预期的：用户清空后 sheet 自动随 MiniPlayer 一起消失。PlayerScreen 入口侧 sheet 不依赖 MiniPlayer，正常显示空态。
- **Sheet 中点击切歌触发底层异常**：`PlayerController.skipTo` 内部对未连接 controller 已有兜底（`withConnectedController` 异步连接）。错误事件继续走 `errorEvents` Flow，由 PlayerScreen 上既有 `LaunchedEffect` 收集 → Toast。MiniPlayer 入口未订阅 `errorEvents`（保持现状，错误更可能在跳转后由 PlayerScreen 显示）。

## 测试计划

### 单元测试

1. **`PlayerControllerQueueStateTest`**（新增，复用现有 `PlayerControllerNotificationControlsTest` 风格）
   - `playQueue(items)` 后 `queueState.value` items / currentIndex 正确。
   - `skipTo(index)` 后 currentIndex 更新。
   - `removeFromQueue(index)` 移除非当前 / 当前 / 最后一项 三种情况，items 与 currentIndex 都正确。
   - `reset()` 后 queueState == EMPTY。
   - `addToQueue` / `addNextInQueue` / `moveInQueue` 都触发 emit。

2. **`PlayerViewModelQueueTest`**（新增）
   - `queueUiModel` 来源于 `queueState` + `playerState.repeatMode` 的 combine，初值 EMPTY。
   - `cycleRepeatMode` 调用后 `queueUiModel.repeatMode` 更新。

### Compose 测试

3. **`PlayQueueSheetContentTest`**（新增，沿用 `MiniPlayerContentTest` 风格）
   - 多项渲染：标题包含 `(N首)`，count 与 items.size 一致。
   - 当前歌曲行渲染高亮 icon；非当前不渲染。
   - 点击非当前行 → `onPlayIndex(index)` 被以正确 index 调用。
   - 点击 × → `onRemove(index)` 被以正确 index 调用。
   - 点击循环模式按钮 → `onCycleRepeatMode` 被调用。
   - 点击清空 → `onClear` 被调用。
   - `items.isEmpty()` → 显示「暂无歌曲」，不渲染 LazyColumn。
   - 初始化后 LazyColumn 滚动至 `currentIndex`（用 `assertIsDisplayed` 校验当前歌曲行可见）。

### 手动验收（运行态闸门）

1. 进入有队列的播放状态，在 home 上点 miniplayer 列表 icon → sheet 弹出，初始定位到当前歌；count 与队列长度一致。
2. 点列表中其他歌 → 切到该歌；sheet 仍打开；高亮 icon 与高亮文本切换到新行。
3. ×：移除非当前歌 → 列表更新；移除当前歌 → 跳到下一首继续播放，高亮迁移到新当前。
4. 清空 → 显示「暂无歌曲」；播放停止；MiniPlayer 整体隐藏（既有 `hasMedia` 逻辑）。在 PlayerScreen 入口的清空：sheet 显示空态，PlayerScreen 上方控制保持显示但无 currentItem 显示。
5. 切换循环模式（OFF/ALL/ONE）：header 文案+图标更新；切歌后边界行为符合该模式（OFF 末尾不切；ALL 循环；ONE 单曲）。
6. 在 PlayerScreen 重复 1–5；两个入口先后打开，状态一致。
7. dismiss 路径：点遮罩、下滑、系统返回，sheet 都能正常关闭。

### 自动化构建闸门

- `./gradlew :player:testDebugUnitTest`
- `./gradlew :feature:player-ui:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

## 实施顺序

1. 在 `:player` 引入 `PlayQueueSnapshot` + `PlayerController.queueState` + 各 emit 点；补 `PlayerControllerQueueStateTest`。
2. 在 `:feature:player-ui` 引入 `PlayQueueUiModel`，扩展 `PlayerViewModel.queueUiModel` / `playQueueIndex` / `removeFromQueue` / `clearQueue`；补 `PlayerViewModelQueueTest`。
3. 写 `PlayQueueRow` / `PlayQueueSheetContent` / `PlayQueueSheet`；补 `PlayQueueSheetContentTest`。
4. 接入 MiniPlayer（删除遗留 `onNavigateToQueue` 参数）；MainActivity 调整为新签名（即不再传该回调）。
5. 接入 PlayerScreen（移除 TODO，挂 sheet）。
6. 跑 unit + Compose 测试 + 装机验收 7 条。
7. 收尾：调用 `superpowers:finishing-a-development-branch`。

## 已知风险与回退

- **`emitQueueState` 漏点**：每加一个会改 queue 的 controller 方法都必须 emit。回归测试覆盖现有所有方法；后续若新增方法需在 PR review 检查清单中点名。
- **滚动初定位 race**：`LaunchedEffect(Unit)` 在首次组合执行；如果 LazyColumn items 还没 emit 完成（即首次打开瞬间 queueUiModel 仍为 EMPTY），`scrollToItem` 会失败。
  - 缓解策略：使用 `var didInitialScroll by remember { mutableStateOf(false) }`，在 `LaunchedEffect(uiModel.items.size, uiModel.currentIndex)` 中判断 `if (!didInitialScroll && uiModel.items.isNotEmpty() && uiModel.currentIndex >= 0) { listState.scrollToItem(uiModel.currentIndex); didInitialScroll = true }`，确保只在 items 首次可用时执行一次定位。

## 验收清单

- [ ] 两个入口都能弹出同一面板，状态一致。
- [ ] Header 文案 / 图标 / 颜色与 RN 视觉对齐。
- [ ] 行渲染规则与 RN 完全一致（包括 artist 为空、platform 为空、title 截断）。
- [ ] 清空 / 循环模式 / 单项删除 / 单项点击切歌 / 当前歌定位 5 项行为对齐 RN。
- [ ] `:player` `:feature:player-ui` 单测全绿。
- [ ] `:app:assembleDebug` 构建通过。
- [ ] 手动验收 7 条全部通过。
