# 播放详情页动态控制状态修复设计

> 文档状态：当前规范
> 适用范围：播放详情页封面操作栏音质/倍速图标、播放列表 sheet 播放模式按钮。
> 直接执行：是（作为实施计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：`../../../../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx`、`../../../../MusicFree/src/constants/assetsConst.ts`、`../../../../MusicFree/src/constants/repeatModeConst.ts`
> 最后校验：2026-05-11

## 概要

修复播放详情页三个状态展示问题：

1. 封面操作栏切换播放音质后，音质图标必须跟随 `PlayQuality` 从低/标准/高/超高切换，不再固定显示标准音质资源。
2. 封面操作栏切换倍速后，倍速图标必须跟随 `PlaybackSpeeds` 从 0.5x 到 2.0x 切换，不再固定显示 1.0x 资源。
3. 播放列表 sheet 顶部播放模式按钮必须与底部主控制按钮一致，支持随机播放、单曲循环、列表循环三态，并修正列表循环/单曲循环资源映射。

## 当前事实与根因

- `PlayerScreen` 已从 `PlayerViewModel` 收集 `currentQuality` 和 `currentSpeed`，但 `PlayerOperationsBar` 内部仍硬编码 `R.drawable.ic_quality_standard` 和 `R.drawable.ic_rate_100`。
- Android 已存在 RN 原版标准音质与 1.0x PNG，且文件 hash 与 `../../../../MusicFree/src/assets/imgs/standard-quality.png`、`100x.png` 一致；缺少其余音质和倍速资源。
- `PlaybackMode` 已在 `:core` 表达 `Shuffle / Single / Queue`，`PlayerController.cyclePlaybackMode()` 已按 RN 顺序 `Shuffle -> Single -> Queue -> Shuffle` 工作。
- `PlayQueueSheetContent` 仍只接收 `RepeatMode` 并调用 `PlayerViewModel.cycleRepeatMode()`，因此播放列表里不能切到随机播放。
- 播放列表 sheet 的 `repeatModeIcon()` 当前把 `RepeatMode.ALL` 映射到 `ic_repeat_song`，把 `RepeatMode.ONE` 映射到 `ic_repeat_song_1`。RN 常量相反：列表循环使用 `repeat-song-1`，单曲循环使用 `repeat-song`。

## 设计决策

采用局部状态映射修复，不重写播放器控制器。

### 动态音质和倍速图标

- 将 RN 原版 PNG 补齐到 `core/src/main/res/drawable/`：
  - `ic_quality_low.png`
  - `ic_quality_high.png`
  - `ic_quality_super.png`
  - `ic_rate_050.png`
  - `ic_rate_075.png`
  - `ic_rate_125.png`
  - `ic_rate_150.png`
  - `ic_rate_175.png`
  - `ic_rate_200.png`
- 保留现有 `ic_quality_standard.png` 和 `ic_rate_100.png`。
- 在 `PlayerScreen.kt` 或同包小 helper 中新增资源映射函数：
  - `playerQualityImage(PlayQuality): Int`
  - `playerRateImage(Float): Int`
- `PlayerOperationsBar` 使用传入的 `currentQuality` 和 `currentSpeed` 选择图片。未知倍速回退到 1.0x，避免异常状态崩溃。

### 播放列表 sheet 三态模式

- `PlayQueueUiModel` 改为携带 `PlaybackMode`，由 `PlayerViewModel.queueUiModel` 根据 `PlayerState.shuffleEnabled` 和 `PlayerState.repeatMode` 派生。
- `PlayQueueSheet` 将 header 按钮回调改为 `viewModel::cyclePlaybackMode`。
- `PlayQueueSheetContent` 使用与底部主控制一致的 `PlaybackMode` label/icon：
  - `Shuffle` -> `ic_shuffle` / `随机播放`
  - `Single` -> `ic_repeat_song` / `单曲循环`
  - `Queue` -> `ic_repeat_song_1` / `列表循环`
- 保留现有 `FidelityAnchors.Player.Queue.RepeatModeButton` tag，避免破坏已有 UI 锚点。

## 非目标

- 不新增播放模式模型；继续复用 `PlaybackMode`。
- 不改 `PlayerController.cyclePlaybackMode()` 的已实现状态迁移。
- 不改音质切换、倍速切换、下载或评论业务逻辑。
- 不改播放详情页布局、状态栏 inset、歌词跟随或 seek overlay。

## 测试策略

- `PlayerOperationsBarTest` 增加音质和倍速资源映射测试，先观察当前硬编码行为失败，再实现动态资源选择。
- `PlayQueueSheetContentTest` 增加随机播放 label 展示和三态按钮点击回调测试。
- `PlayerViewModelQueueTest` 增加 `queueUiModel` 从 `shuffleEnabled/repeatMode` 派生 `PlaybackMode` 的测试。
- 复跑：
  - `./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerOperationsBarTest' --tests '*PlayQueueSheetContentTest' --tests '*PlayerViewModelQueueTest' --tests '*PlayerControlsTest' --no-daemon`
  - `./gradlew :feature:player-ui:testDebugUnitTest --no-daemon`
  - `./gradlew :app:assembleDebug --no-daemon`

## 验收标准

- 切换播放音质后，封面操作栏音质图片资源随 `PlayQuality` 变化。
- 切换播放倍速后，封面操作栏倍速图片资源随当前倍速变化。
- 播放列表 sheet 顶部模式按钮可显示随机播放，并点击后调用三态 `cyclePlaybackMode()`。
- 列表循环与单曲循环在播放列表 sheet 中使用 RN 对应图标。
