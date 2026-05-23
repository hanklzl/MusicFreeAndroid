---
name: logging
description: "Use for MusicFreeAndroid changes to (1) key business logic that can fail after release: ViewModel async/user operations, Repository/DAO-facing writes, plugin operations, QuickJS/JsBridge, network requests, playback, lyrics, download, file IO, feedback export, import/export flows, or catch blocks that swallow/degrade/convert errors to UI state; (2) Compose UI changes that add or modify clickable elements (Modifier.clickable, IconButton, Card onClick, list rows, ModalBottomSheet open/dismiss) — every meaningful user-visible action must emit a structured ui_click / dialog_open / dialog_dismiss event."
---

# Logging Skill

Use this skill before editing key business logic that can fail after the app ships, OR before adding/modifying any clickable element in a Compose Screen.

## Required Reading

- `AGENTS.md` logging section.
- `docs/superpowers/specs/2026-05-05-logging-system-design.md` (持久化与 Logan 集成)
- `docs/superpowers/specs/2026-05-10-logging-diagnostics-coverage-design.md` (业务事件覆盖原则)
- `docs/superpowers/specs/2026-05-23-user-action-timeline-design.md` (UI / 导航 / 生命周期事件 + Action Timeline)
- `docs/dev-harness/ui/rules.md` — 用户行为埋点 rule section

## Two Layers

业务日志（async 操作、错误回退、网络/插件/播放/下载）走 `MfLog.detail / .error`，规则在下方"Business workflow"。

用户行为日志（点击、screen 进入、tab 切换、前后台、Activity / Store / 插件引擎 / MediaSession 生命周期）走专用工具与统一发射器，规则在下方"User action workflow"。

## Business workflow

1. Identify the user action or background operation boundary.
2. Log start with stable fields: `screen`, `operation`, `flowId` when available, and input summary.
3. Log exactly one terminal result for each operation: `success`, `failure`, `cancelled`, `stale`, or `skipped`.
4. Use `MfLog.error` when an exception is swallowed, converted to toast/UI error, or causes fallback.
5. Use `MfLog.detail` for normal start/success/cancelled/skipped/stale diagnostics.
6. Use `durationMs` for network, plugin, file IO, database writes, playback source resolution, download, scan, import, and export operations.
7. Keep field values primitive: String, Number, Boolean, list, or shallow map. Convert domain objects to ID/name/platform/count summaries.
8. Do not log high-frequency progress, recomposition, lyric follow frame updates, or every network callback.
9. Do not add direct `android.util.Log.*` or Logan calls in business code.

## User action workflow

Adding or modifying a clickable element (`Modifier.clickable`, `IconButton`, `Card onClick`, `ListItem onClick`, bottom sheet trigger, etc.)?

1. Prefer the public utilities in `:core:ui/LoggedClickable.kt`:
   - `Modifier.loggedClick(targetId, screen, fields = ..., onClick = ...)` — `clickable` 的带日志包装
   - `LoggedIconButton(targetId, screen, onClick, ...content)` — Material3 IconButton 的 drop-in 替换
   - `logUiClick(targetId, screen, extra = ...)` — 非 Modifier / 非 IconButton 链路手动发
2. `targetId` 用 `<screen>.<region>.<element>` snake_case，例 `player.controls.play` / `search.result.music_row` / `settings.row.theme`。每个 screen 保持稳定命名表。
3. `screen` 用稳定路由名（不带 path/后缀），例 `home` / `player` / `search` / `plugin_list`。
4. `ModalBottomSheet` / `Dialog` 必须在 show 时手动发 `dialog_open`，在 dismiss 时发 `dialog_dismiss(outcome=confirm|cancel|system)`：
   ```kotlin
   MfLog.detail(LogCategory.UI, UiLogEvents.DIALOG_OPEN, mapOf(
       UiLogEvents.Fields.DIALOG_ID to "MyDialog",
       UiLogEvents.Fields.SCREEN to "player",
       UiLogEvents.Fields.TRIGGER to UiLogEvents.Trigger.UI_CLICK,
   ))
   ```
5. **不要**重复埋这些已由全局发射器覆盖的事件——它们由 `AppNavHost` / `MusicFreeApplication` / `MainActivity` 统一发出：
   - `screen_enter` / `screen_exit` ← `AppNavHost` 的 NavController listener
   - `tab_switch` ← Tab bar 点击处（如未来添加 bottom nav）
   - `app_foreground` / `app_background` ← `ProcessLifecycleOwner`
   - `activity_created` / `activity_destroyed` ← `MainActivity`
   - `process_start_after_kill` ← 冷启动 `MusicFreeApplication.onCreate` 通过 SharedPreferences 检测
   - `media_session_started` / `media_session_destroyed` ← `PlaybackService`
   - `plugin_engine_init` ← `PluginManager.loadAllPlugins` 首次执行
6. 不需要埋点的：调试/dev 面板按钮、`onShare = {}` 等空 lambda、纯视觉的展开/折叠子节点（且有等价 ui_click 父节点）。
7. 业务事件（如 `player_play`、`search_start`）保留并与 `ui_click` 并存——viewer 侧 `playAction` / `searchAction` 等 ActionRule 优先吸收业务事件，`uiClick` 兜底，所以不会重复成卡。

## Field Names

Business logs：`screen`, `operation`, `flowId`, `generation`, `platform`, `pluginVersion`, `mediaType`, `itemId`, `itemName`, `playlistId`, `sheetId`, `query`, `url`, `host`, `pathType`, `count`, `page`, `quality`, `durationMs`, `result`, `reason`.

User action logs（全部在 `UiLogEvents.Fields` 里有常量）：`targetId`, `targetLabel`, `screen`, `route`, `params`, `source`, `from`, `to`, `dialogId`, `trigger`, `outcome`, `durationMs`, `lastScreen`, `resumeScreen`, `isPlaying`, `backgroundedDurationMs`, `activity`, `hasSavedState`, `isConfigChange`, `isColdStart`, `isFinishing`, `isChangingConfigurations`, `lifetimeMs`, `reason`, `storeId`, `scope`, `restoredFromSnapshot`, `snapshotKeys`, `pluginCount`, `jsEngineVersion`, `restoredQueueSize`, `lastSongId`, `queueSize`, `lastBackgroundedDurationMs`, `suspectedReason`, `previousSessionId`.

## Verification

- Run the touched feature module's `compileDebugKotlin`（每加几个埋点本地编译一次，错的早发现）。
- Run the touched module's `testDebugUnitTest`.
- Run `:logging:testDebugUnitTest` when changing categories/helpers.
- Run `bash scripts/dev-harness/check.sh` before completion when the task touches guarded domains.
- 复杂改动：装 debug APK 跑一次目标用户路径，反馈包导入 logan-viewer 验证 ActionCard 渲染正确。
