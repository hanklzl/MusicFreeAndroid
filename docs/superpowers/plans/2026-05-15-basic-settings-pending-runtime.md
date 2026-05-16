# 基础设置剩余待接入项 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将基础设置页当前剩余 `待接入` / disabled 行全部接入真实偏好和运行态。

**Architecture:** 偏好统一落在 `AppPreferences`，UI 继续使用 `BasicSettingsContent` 的 stateless 参数模式；播放器/通知栏/日志只消费偏好，不反向依赖 settings。桌面歌词采用 `feature:player-ui` 内的 overlay controller，通知栏关闭走现有 `PlaybackNotificationCommandHandler`。

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, Hilt, Media3 MediaSession, Logan/MfLog, JUnit/Compose UI tests.

---

## File Map

- Modify `core/src/main/java/com/hank/musicfree/core/model/BasicSettingModels.kt`: add lyric association and desktop lyric alignment enums.
- Modify `data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt`: add flows/setters for remaining Basic settings and debug log switches.
- Modify `logging/src/main/java/com/hank/musicfree/logging/*`: add log switches and readable error log mirror.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`: expose new state and actions.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt`: remove pending rows, add dialogs and rows.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`: overlay permission path and view-error-log dialog.
- Modify `player/src/main/java/com/hank/musicfree/player/service/*` and `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`: notification close command.
- Modify `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/*`: lyric association input path and desktop lyric overlay.
- Modify `app/src/main/AndroidManifest.xml`: add overlay permission.
- Update targeted tests under touched modules.

### Task 1: Preferences And Settings UI

- [ ] Add `LyricAssociationType` and `DesktopLyricAlignment` enums in `BasicSettingModels.kt`.
- [ ] Add DataStore keys, flows, setters, and clamps/defaults in `AppPreferences.kt`.
- [ ] Extend `BasicSettingsUiState` and `SettingsViewModel` with remaining settings.
- [ ] Replace `PendingValueRow` usages in `BasicSettingsContent.kt` with real switch/value/action rows and choice dialogs.
- [ ] Add overlay permission callback and error-log dialog plumbing in `SettingsScreen.kt`.
- [ ] Update `SettingsViewModelTest` and `BasicSettingsContentTest` so all new settings are visible, clickable, and persisted.

Verification for this task:

```bash
./gradlew :data:testDebugUnitTest :feature:settings:testDebugUnitTest --no-daemon
```

### Task 2: Logging Runtime Switches And Readable Error Log

- [ ] Add a small logging switch state in `MfLog` so `error/detail/trace` can be enabled or disabled at runtime.
- [ ] Add `ReadableLogStore` under `:logging` and pass it through `LoggingConfig` or a singleton holder without creating a data dependency.
- [ ] Make `LoganMfLogger.error(...)` append readable error records.
- [ ] Make `FeedbackLogExporter.clearLogs()` clear readable logs too.
- [ ] Start collecting debug log switch preferences from `MusicFreeApplication` after Hilt injection and call `MfLog.configure(...)`.
- [ ] Add logging unit tests for filtering and readable log clear/read behavior.

Verification for this task:

```bash
./gradlew :logging:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```

### Task 3: Player Runtime For Association, Desktop Lyrics, Notification Close

- [ ] Extend `PlaybackRuntimeSettings` and `AppPlaybackRuntimeSettings` with `showExitOnNotification`.
- [ ] Add `ACTION_CLOSE` command and optional stop button in `PlaybackNotificationActions`.
- [ ] Extend `PlaybackNotificationCommandHandler` and `PlayerController` with notification-close handling.
- [ ] Add `DesktopLyricOverlayController` in `feature/player-ui` using `WindowManager.TYPE_APPLICATION_OVERLAY`.
- [ ] Expose desktop lyric config and association type from `PlayerViewModel`.
- [ ] Add input association dialog in `PlayerScreen`; parse `platform@id` and JSON media key payload.
- [ ] Wire Player more-options desktop lyric toggle to real preferences and overlay permission checks.
- [ ] Add/update `player` and `feature/player-ui` tests for close command, input parser, and overlay config mapping.

Verification for this task:

```bash
./gradlew :player:testDebugUnitTest :feature:player-ui:testDebugUnitTest --no-daemon
```

### Task 4: Integration, Docs, And Merge Gate

- [ ] Run grep/dev-harness checks.
- [ ] Run `git diff --check`.
- [ ] Run `./gradlew :app:assembleDebug --no-daemon`.
- [ ] Do code review against spec: no remaining `待接入` in Basic settings, no raw `android.util.Log.*`, no UI harness violations.
- [ ] Commit branch changes with Chinese conventional commit message.
- [ ] Squash merge `feat/basic-settings-pending` back to local `main`.
- [ ] Re-run final Debug build or targeted smoke on `main`.
- [ ] Remove worktree and local feature branch after merge verification.
