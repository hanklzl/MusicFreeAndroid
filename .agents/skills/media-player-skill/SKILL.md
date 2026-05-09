---
name: media-player
description: >
  Use this skill for any change in :player or feature/player-ui (Media3,
  ExoPlayer, MediaSessionService, PlayerController connection, lyric
  rendering / follow / seek overlay, mini player, immersive chrome).
  Trigger phrases: "Media3", "ExoPlayer", "MediaSession", "PlayerController",
  "connect", "主线程 runBlocking", "lyric", "状态栏沉浸", "mini player",
  "PlaybackService".
---

# Media Player Skill

Cross-tool guidance for player engine and player UI.

## 必读 gate

- [`docs/dev-harness/player/rules.md`](references/rules.md)
- [`docs/dev-harness/player/incidents.md`](references/incidents.md)
- 涉及 instrumentation 测试时还需 [`docs/dev-harness/test/rules.md`](../test-stability-skill/references/rules.md)

## Workflow checklist

1. 读 rules.md / incidents.md，确认改动落点：PlayerController 连接 / 沉浸式 chrome / 歌词跟随 / PlaybackService。
2. instrumentation 测试 MUST NOT 在主线程内 `runBlocking { controller.connect() }`；用 bounded `withTimeout(5_000L)`（INC-2026-0002 / rule-no-runblocking-mainthread-in-instrumentation）。
3. `PlayerScreen` 内容层 MUST 用 `WindowInsets.statusBars` 显式避让（INC-2026-0011 / rule-immersive-content-respects-statusbar）。
4. 改歌词跟随 / seek overlay 必须跑：`./gradlew :feature:player-ui:testDebugUnitTest --no-daemon`，含 `MiniPlayerContentTest`、`LyricFollow*Test`、`LyricSeekOverlay*Test`。
5. 改 PlaybackService 生命周期对照 `docs/superpowers/specs/2026-05-04-playback-notification-design.md`。

## References

- [controller-connect.md](references/controller-connect.md)
- [lyrics-follow-and-seek.md](references/lyrics-follow-and-seek.md)
- [immersive-statusbar-vs-content.md](references/immersive-statusbar-vs-content.md)
- [playback-service-lifecycle.md](references/playback-service-lifecycle.md)
