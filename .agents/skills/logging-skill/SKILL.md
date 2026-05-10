---
name: logging
description: Use for any MusicFreeAndroid change that touches ViewModels, Repository/DAO-facing writes, plugin operations, QuickJS/JsBridge, network requests, playback, lyrics, download, file IO, feedback export, import/export flows, or any catch block that swallows, degrades, or converts errors to UI state. Ensures key logic uses the repository logging module with structured MfLog events.
---

# Logging Skill

Use this skill before editing key business logic that can fail after the app ships.

## Required Reading

- `AGENTS.md` logging section.
- `docs/superpowers/specs/2026-05-05-logging-system-design.md`
- `docs/superpowers/specs/2026-05-10-logging-diagnostics-coverage-design.md`

If the change also touches plugins, player, UI, or tests, read that domain's dev-harness `rules.md` too.

## Workflow

1. Identify the user action or background operation boundary.
2. Log start with stable fields: `screen`, `operation`, `flowId` when available, and input summary.
3. Log exactly one terminal result for each operation: `success`, `failure`, `cancelled`, `stale`, or `skipped`.
4. Use `MfLog.error` when an exception is swallowed, converted to toast/UI error, or causes fallback.
5. Use `MfLog.detail` for normal start/success/cancelled/skipped/stale diagnostics.
6. Use `durationMs` for network, plugin, file IO, database writes, playback source resolution, download, scan, import, and export operations.
7. Keep field values primitive: String, Number, Boolean, list, or shallow map. Convert domain objects to ID/name/platform/count summaries.
8. Do not log high-frequency progress, recomposition, lyric follow frame updates, or every network callback.
9. Do not add direct `android.util.Log.*` or Logan calls in business code.

## Field Names

Prefer existing names: `screen`, `operation`, `flowId`, `generation`, `platform`, `pluginVersion`, `mediaType`, `itemId`, `itemName`, `playlistId`, `sheetId`, `query`, `url`, `host`, `pathType`, `count`, `page`, `quality`, `durationMs`, `result`, `reason`.

## Verification

- Run the touched module's `testDebugUnitTest`.
- Run `:logging:testDebugUnitTest` when changing categories/helpers.
- Run `bash scripts/dev-harness/check.sh` before completion when the task touches guarded domains.
