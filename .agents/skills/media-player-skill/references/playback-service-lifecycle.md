# PlaybackService Lifecycle

参照 `docs/superpowers/specs/2026-05-04-playback-notification-design.md`。

要点：

- `PlaybackService` 继承 `MediaSessionService`，在 `onTaskRemoved`、`onDestroy` 中按 RN 行为处理通知 / 播放停止。
- `MediaSession` 实例由 `PlaybackService` 拥有；`PlayerController` 通过 `MediaController` 包装层访问。

本 rule 只要求改动 PR 对照该 spec；具体策略在该 spec 内已写明。
