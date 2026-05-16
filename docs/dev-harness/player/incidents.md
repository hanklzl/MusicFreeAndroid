# 播放器 / Media3 Incidents

> 文档状态：当前规范（Dev Harness — Player Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [player/rules.md](./rules.md)
> 最后校验：2026-05-09

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
