# 播放器 / Media3 Rules

> 文档状态：当前规范（Dev Harness — Player）
> 适用范围：PlayerController 连接与状态、MediaSessionService 生命周期、播放器沉浸式 chrome、歌词跟随
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[播放器状态栏避让设计](../../superpowers/specs/2026-05-04-player-statusbar-inset-design.md)、[播放页歌词交互修正设计](../../superpowers/specs/2026-05-05-player-lyrics-interaction-fix-design.md)、[歌词纯秒小数时间戳修复设计](../../superpowers/specs/2026-05-10-lyric-second-only-timestamp-fix-design.md)
> 最后校验：2026-06-13

## 强制入口

新增或修改 `:player` 模块、`feature/player-ui` 模块、`MediaSessionService` 或 `PlayerController` 调用方前，必须先读取本文件。

## PlayerController 连接 {#rule-no-runblocking-on-mainthread}

implemented_by: INC-2026-0002

- `PlayerController.connect()` 调用 MUST 在 instrumentation test 线程执行（不在主线程 executor 内）。
- 若必须等待 connect 完成，MUST 用 `withTimeout(<= 5_000L) { ... }` 加 bounded 等待。
- 测试 helper 执行主线程动作时 MUST 用 bounded `latch.await(timeout, TimeUnit.SECONDS)`，捕获异常通过 `AtomicReference` 回传后 fail。
- MUST NOT 在 `context.mainExecutor.execute { ... }` 内嵌套 `kotlinx.coroutines.runBlocking { controller.connect() }`：会造成 MediaController async 连接因主线程阻塞永久挂起。

## 全屏沉浸式 chrome {#rule-immersive-content-respects-statusbar}

implemented_by: INC-2026-0011

- `PlayerScreen` 背景层 MAY 绘制到状态栏后方，但内容层 MUST 通过 `WindowInsets.statusBars` 显式避让。
- MUST NOT 让标题、控件、歌词卡片等可交互元素被状态栏遮挡。
- 该 rule 当前 guard 为 manual，由 `harness-curator-skill` 在巡检时显式列出，提醒人工复核截图与 RN 对齐。

## 歌词跟随防抖 {#rule-lyric-follow-debounce}

implemented_by: INC-2026-0012

- 自动跟随 MUST 与手动滑动状态互斥，避免在手动滑动期间被自动跟随重置。
- seek overlay 进入条件 MUST 由统一 helper 决定，不分散到多个 composable。
- 任何修改歌词跟随逻辑的 PR MUST 跑 `feature/player-ui` 相关单测：`MiniPlayerContentTest`、`LyricFollow*Test`、`LyricSeekOverlay*Test`。

## MediaSessionService 生命周期

- `PlaybackService` MUST 在 `onTaskRemoved`、`onDestroy` 中按 RN 行为停止当前播放或保留媒体通知（依现有实现，详见 `2026-05-04-playback-notification-design.md`）。
- 不在本 rule 强制具体策略；只要求改动 PR 对照该 spec。

## 远端源解析失败必须刷新缓存源 {#rule-remote-source-parse-failure-refreshes-cache-source}

implemented_by: INC-2026-0026

- `PlayerController` 遇到远端播放源的 `ERROR_CODE_IO_BAD_HTTP_STATUS`、`ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE`、`ERROR_CODE_PARSING_CONTAINER_MALFORMED` 或 `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` 后，MUST 先走一次 cache entry eviction + fresh media source resolve，再重新 `setMediaItem / prepare / play`。
- 同一首歌同一轮队列位置内该刷新预算 MUST 只有一次；刷新失败或刷新后仍失败时，才继续走换源 / 自动暂停 / 自动切下一首策略。
- 本地播放源（`platform == "local"` 或 `file://` / `content://`）MUST NOT 走远端缓存刷新；这类 3003 更可能是本地文件本身不可解析。
- 任何改 `refreshStaleUrlAfterFailure`、Media3 error code 分流或 `StaleUrlRefresher` 的 PR MUST 跑 `:player:testDebugUnitTest --tests *PlayerControllerStaleUrlRefreshTest* --tests *PlayerControllerPlaybackFailurePolicyTest*`。

## 通知播放命令不得递归回调 {#rule-notification-play-no-recursion}

implemented_by: INC-2026-0022

- `PlaybackService.onPlayerCommandRequest` MUST NOT 在 `session.player.mediaItemCount == 0` 路径里同步触发任何最终会经 `MediaController` IPC 回到 `onPlayerCommandRequest` 自身的 `controller.play()` 调用；否则会形成同步无限递归直到 `StackOverflowError`。
- `PlayerController.playFromNotification`（即 `PlaybackNotificationCommandHandler.play()` 的回调）MUST 总是经 `activateCurrentQueueItem` / `setMediaItemAndPlay` 等先 `setMediaItem` 再 `play` 的路径加载队列项，禁止根据 `mediaController.currentMediaItem` 走 `play() → controller.play()` 的捷径——controller 缓存与 session player 可能短暂不一致。
- 任何改 `onPlayerCommandRequest` 或 `playFromNotification` 的 PR MUST 跑 `:player:testDebugUnitTest --tests *PlayerControllerNotificationControlsTest*`。

## 恢复队列播放命令来源隔离 {#rule-restored-play-command-source-isolation}

implemented_by: INC-2026-0024

- `PlayerController.play()` 在 restored queue / 冷启动恢复后，若 `MediaController.currentMediaItem == null`、`MediaController.playbackState == STATE_IDLE`，或 `currentMediaItem.mediaId` 与队列当前项不一致，MUST 先走 `activateCurrentQueueItem` / `setMediaItemAndPlay` 重新装载队列当前项，再触发 `play()`；不得只因 controller 缓存非空就直接 `controller.play()`。
- `PlaybackService.onPlayerCommandRequest` 处理 empty-session `COMMAND_PLAY_PAUSE` 时 MUST 区分命令来源：`controller.packageName == packageName` 的 app 内 controller 命令不得转发到 `PlaybackNotificationCommandHandler.play()`；仅外部 / 系统控制器可走通知栏 empty-session 兜底。
- 该链路日志 MUST 至少包含 `controllerPackage`、`isAppController`、`sessionMediaItemCount`、`playerPlaybackState`、`queueIndex`、`queueSize`、`currentItemId`，用于反馈包判断系统媒体控件与 app 内 player state 是否分叉。
- 任何改 `PlayerController.play()`、`PlaybackService.onPlayerCommandRequest` 或 `PlaybackNotificationCommandHandler` 诊断字段的 PR MUST 跑 `:player:testDebugUnitTest --tests *PlayerControllerNotificationControlsTest* --tests *PlaybackServiceCommandRoutingTest*`。

## 上一首命令必须走队列迁移 {#rule-previous-command-uses-queue-transition}

implemented_by: INC-2026-0025

- `PlayerController.skipToPrevious()` MUST 通过 `PlayQueue.peekPreviousIndex(repeatMode)` 解析目标，并调用 `startQueuePlaybackTransition(operation = "skip_previous")`，与 `skipToNext()` 保持同一条队列切歌语义。
- MUST NOT 用 `currentPosition > threshold -> seekTo(0)` 替代上一首；播放详情页、mini player、通知栏等入口的上一首命令都必须能切到队列前一项。
- mini player 横向手势 MUST 连接到 `PlayerViewModel.skipToPrevious()` / `skipToNext()`，不得留下空 lambda。
- 任何改 `PlayerController.skipToPrevious()`、mini player 手势或播放控制上一首入口的 PR MUST 跑 `:player:testDebugUnitTest --tests *PlayerControllerQueueStateTest*` 与 `:feature:player-ui:testDebugUnitTest --tests *MiniPlayerContentTest*`。

## 歌词解析时间戳格式 {#rule-lyric-parser-supports-second-only-timestamp}

implemented_by: INC-2026-0017

- `LyricParser` MUST 接受 RN `timeReg=/\[[\d:.]+\]/g` 兼容的全部三档 LRC 时间戳：`[hh:mm:ss(.ff)?]`、`[mm:ss(.ff)?]`、`[s(.ff)?]`。
- `LyricParser.parseTimestampMs` 与 `timestampTokenRegex`、`LyricDocument.lyricTimestampRegex` 必须使用一致的格式集合，禁止任一处单独收紧到要求 colon。
- 任何放宽/收紧时间戳 regex 或 timestamp 解析的 PR MUST 跑 `:core:testDebugUnitTest --tests *LyricParserTest --tests *LyricTimingTest` 与 `:feature:player-ui:testDebugUnitTest --tests *LyricTimestampFormatContractTest`。

参见 [cache-and-logs.md](cache-and-logs.md) 了解三层缓存策略 + 排查 Recipe。
