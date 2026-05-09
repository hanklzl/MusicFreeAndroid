# 播放器 / Media3 Rules

> 文档状态：当前规范（Dev Harness — Player）
> 适用范围：PlayerController 连接与状态、MediaSessionService 生命周期、播放器沉浸式 chrome、歌词跟随
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[播放器状态栏避让设计](../../superpowers/specs/2026-05-04-player-statusbar-inset-design.md)、[播放页歌词交互修正设计](../../superpowers/specs/2026-05-05-player-lyrics-interaction-fix-design.md)
> 最后校验：2026-05-09

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
