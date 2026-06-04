# 播放器 / Media3 Incidents

> 文档状态：当前规范（Dev Harness — Player Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [player/rules.md](./rules.md)
> 最后校验：2026-06-05

## INC-2026-0025 — 上一首被进度回零语义吞掉

- id: INC-2026-0025
- area: player
- date: 2026-06-05
- status: active
- rule_ref: docs/dev-harness/player/rules.md#rule-previous-command-uses-queue-transition
- guard:
    type: contract-test
    target: player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerQueueStateTest.kt, feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/MiniPlayerContentTest.kt
- fix_ref: 本次上一首队列迁移修复

### 根因

`PlayerController.skipToPrevious()` 在当前播放进度超过 3 秒时直接 `seekTo(0)` 并早退，导致播放详情页等入口只能把当前歌曲进度重置，无法继续切到上一首。mini player 横向滑动虽然在 `MiniPlayerContent` 内识别了左右手势，但 wrapper 传入的是空 lambda，手势同样无法触发切歌。

### 复发条件

修改上一首 / 下一首入口后，以下任一断言被破坏：

- 当前进度超过 3 秒时，`skipToPrevious()` 没有把 `queueState.currentIndex` 切到前一项。
- `skipToPrevious()` 重新调用 `MediaController.seekTo(0L)` 替代队列迁移。
- mini player 横向右滑 / 左滑没有分别调用 `PlayerViewModel.skipToPrevious()` / `skipToNext()`。

### 教训

上一首是队列导航命令，不是 seek 命令；所有 UI 入口必须复用 controller 的队列迁移语义，并用 Compose 手势测试覆盖 wrapper wiring。

## INC-2026-0024 — 冷启动恢复后通知栏与 mini player 状态分叉

- id: INC-2026-0024
- area: player
- date: 2026-05-26
- status: active
- rule_ref: docs/dev-harness/player/rules.md#rule-restored-play-command-source-isolation
- guard:
    type: contract-test
    target: player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerNotificationControlsTest.kt, player/src/test/java/com/hank/musicfree/player/service/PlaybackServiceCommandRoutingTest.kt
- fix_ref: GitHub Issue #8

### 根因

冷启动恢复队列后，app 内 `MediaController` 可能仍持有 stale / idle 的 `currentMediaItem` 缓存，而 `PlaybackService.session.player` 为空。旧 `PlayerController.play()` 只判断 `currentMediaItem != null` 就直接 `controller.play()`；随后 `PlaybackService.onPlayerCommandRequest` 又把来自 app 自己包名的 empty-session `COMMAND_PLAY_PAUSE` 当作通知栏兜底，重复触发 `PlaybackNotificationCommandHandler.play()`，造成系统媒体控件和 mini player 状态短暂或持续不同步。

### 复发条件

修改 `PlayerController.play()`、`PlaybackService.onPlayerCommandRequest` 或通知命令 handler 后，以下任一断言被破坏：

- restored queue + stale / idle controller 时，app 内 `play()` 没有先 `setMediaItem` 再 `play()`。
- `controller.packageName == packageName` 的 app 内 empty-session `PLAY_PAUSE` 被转发到 notification fallback。
- 外部 / 系统控制器 empty-session `PLAY_PAUSE` 无法恢复队列当前项。

### 教训

controller 缓存与 session player 不是同一个事实源；冷启动恢复链路必须用队列当前项和 controller 状态共同判定是否需要重新装载。empty-session fallback 只属于外部媒体控制入口，app 自己的 controller 命令必须跳过该 fallback，并保留足够诊断字段供反馈包还原。

## INC-2026-0012 — 歌词自动跟随重复触发 / seek overlay 错位

- id: INC-2026-0012
- area: player
- date: 2026-05-05
- status: active
- rule_ref: docs/dev-harness/player/rules.md#rule-lyric-follow-debounce
- guard:
    type: contract-test
    target: feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/harness/contracts/LyricFollowDebounceContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-05-player-lyrics-interaction-fix-design.md

### 根因

歌词自动跟随与手动滑动状态没有显式互斥，自动跟随会在手动滑动期间被反复触发；seek overlay 进入条件分散，导致 overlay 与歌词卡片错位。

### 复发条件

`feature/player-ui` 修改歌词跟随逻辑后，`MiniPlayerContentTest`、`LyricFollow*Test`、`LyricSeekOverlay*Test` 任一断言被破坏。

### 教训

互斥状态由统一 helper 决定；任何相关 PR MUST 跑全量歌词单测。

## INC-2026-0011 — 全屏播放器内容贴到状态栏后方

- id: INC-2026-0011
- area: player
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/player/rules.md#rule-immersive-content-respects-statusbar
- guard:
    type: manual
- fix_ref: docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md

### 根因

`PlayerScreen` 启用 edge-to-edge 后内容层未通过 `WindowInsets.statusBars` 显式避让，标题与控件被状态栏遮挡。

### 复发条件

`PlayerScreen` 内容层去掉 `WindowInsets.statusBars` padding 或 helper。

### 教训

背景层可绘到状态栏后方；内容层（标题、按钮、歌词卡片）必须避让。

### 备注

manual：视觉断言难自动化。harness-curator-skill 巡检时提醒人工复核截图与 RN 对齐。
