# 播放器 / Media3 Incidents

> 文档状态：当前规范（Dev Harness — Player Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [player/rules.md](./rules.md)
> 最后校验：2026-06-13

## INC-2026-0026 — 远端坏字节缓存导致 3003 反复自动切歌

- id: INC-2026-0026
- area: player
- date: 2026-06-13
- status: active
- rule_ref: docs/dev-harness/player/rules.md#rule-remote-source-parse-failure-refreshes-cache-source
- guard:
    type: contract-test
    target: player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerStaleUrlRefreshTest.kt
- fix_ref: 2026-06-13 v1.2.14 反馈包播放失败排查

### 根因

反馈包中多次 `playback_failure_skip_next` / `playback_failure_next_unavailable` 的 Media3 errorCode 为 `3003`（container unsupported）。对应 sid 前后的 `media3_datasource_open` 显示远端 cache key 命中 SimpleCache，`bytesFromCache=131076`、`bytesFromUpstream=0`，随后 extractor sniff 失败；旧逻辑只在 `ERROR_CODE_IO_BAD_HTTP_STATUS` 时驱逐缓存并重新解析 URL，导致坏的远端字节缓存可被反复复用并立即触发自动切歌。

同一反馈包也出现 `content://` 本地源解析失败；这类失败不应走远端缓存刷新，需按本地文件不可解析继续诊断。

### 复发条件

修改播放失败恢复逻辑后，以下任一断言被破坏：

- 远端 `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` 没有调用 `StaleUrlRefresher.evictCacheEntry()` 和 `resolveFresh()`。
- 远端 `ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE` / `ERROR_CODE_PARSING_CONTAINER_MALFORMED` 未进入同一条刷新路径。
- `file://` / `content://` / `platform=="local"` 的本地源 3003 被误判为远端坏缓存并触发刷新。
- 同一首歌同一轮失败恢复允许无限刷新，绕过后续换源或自动切歌策略。
- 刷新后的远端 URL 仍然 3003 时，只记录 `playback_stale_url_retry_exhausted`，但没有再次驱逐刷新后写入的坏字节缓存；用户下一次手动重试又先命中同一个稳定 cache key 的坏缓存。

### 教训

Media3 3003 不一定是订阅源无法返回 URL；如果日志里先出现稳定 cache key 命中且只读到固定小段缓存，再出现 extractor sniff 失败，应优先怀疑远端字节缓存已被写入非音频或截断内容。HTTP bad status、invalid content type、container malformed / unsupported 都必须共用一次性的远端缓存刷新预算。

2026-06-13 v1.2.15 反馈包复盘补充：`世界真细小` 的 `元力QQ:187297:super` 首次失败先从 SimpleCache 读到 `131076` 字节，驱逐后 `resolveFresh()` 拿到新 `car-er.kuwo.cn/*.mgg` URL；刷新后的第二次播放从 upstream 仍只读到 `131076` 字节并 3003。刷新预算必须保持一次，但这次新写入的坏字节也要驱逐，否则下一次用户手动重试会先命中本地坏缓存。`Mamma Mia` 同一反馈包另有两条不同根因：`元力WY/1868436337` 的插件 API 返回 `code=201` 且无 URL 后回退到原始 `share.duanx.cn` URL，最终 SSL 握手失败；`元力QQ/302986918` 命中本地 `content://` 下载文件后 3003，本地源不属于远端缓存刷新范围。

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
