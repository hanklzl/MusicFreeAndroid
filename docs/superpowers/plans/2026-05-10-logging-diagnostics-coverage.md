# Logging Diagnostics Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing Logan-backed `:logging` system so exported feedback logs can diagnose core ViewModel, plugin, network, file IO, download, data-write, lyric, and playback failures.

**Architecture:** Keep `:logging` as the only logging facade and use `MfLog` object calls to avoid ViewModel/repository constructor churn. Add shared categories and small field helpers first, then instrument disjoint module boundaries. Do not add `:core -> :logging`; keep `core` pure and log storage-directory outcomes in feature/data callers.

**Tech Stack:** Kotlin, Android Gradle modules, Logan-backed `MfLog` / `MfLogger`, Hilt modules that already exist, kotlinx.coroutines, Room/DataStore, OkHttp, Media3, JUnit4/Robolectric/androidTest where existing tests already require it.

**Spec:** [docs/superpowers/specs/2026-05-10-logging-diagnostics-coverage-design.md](../specs/2026-05-10-logging-diagnostics-coverage-design.md)

**Branch / Worktree:** `feat/logging-diagnostics` in `.worktrees/logging-diagnostics`

---

## File Map

### Create

- `.agents/skills/logging-skill/SKILL.md` — repo-level logging workflow skill.
- `.codex/skills/logging-skill/SKILL.md` — Codex-discoverable copy of the logging skill.
- `logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogFields.kt` — stable field-name constants and small helpers.
- `logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogFieldsTest.kt` — helper/category tests.

### Modify

- `docs/superpowers/plans/2026-05-10-logging-diagnostics-coverage.md` — this plan only until implementation begins.
- `logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogCategory.kt`
- `logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogEventFormatterTest.kt`
- `data/build.gradle.kts`
- `downloader/build.gradle.kts`
- `feature/player-ui/build.gradle.kts`
- `data/src/main/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStore.kt`
- `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
- `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/*.kt` where listed below
- `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngine.kt`
- `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/MediaStoreMusicWriter.kt`
- `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/OkHttpDownloader.kt`
- `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/NetworkMonitor.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsEngine.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/RequireShim.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/meta/PluginMetaStore.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/**/*.kt` ViewModels listed in Task 4
- `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/**/*.kt` ViewModels listed in Task 4
- `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoader.kt`
- `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- `player/src/main/java/com/zili/android/musicfreeandroid/player/service/PlaybackService.kt`
- Existing focused tests in touched modules when the implementation adds assertions.

---

## Global Implementation Rules

- Use only `MfLog` / `MfLogger` in business code. Do not add direct `android.util.Log.*` or Logan calls.
- Prefer `MfLog` object calls over constructor-injected loggers to avoid fixture churn.
- Every long or external operation should emit start plus one terminal event: `success`, `failure`, `cancelled`, `stale`, or `skipped`.
- Normal `CancellationException` paths are `detail` events with `result=cancelled`; swallowed exceptions, toast conversions, UI error states, and fallback returns are `error` events.
- Do not log per-progress download updates, per-frame lyric follow, or every Compose recomposition.
- Keep event names lowercase snake_case. Keep field values basic types only.
- Preserve QuickJS thread ownership. Logging around QuickJS calls must not move `Context`, `JsBridge`, or engine close/evaluate calls to another dispatcher.
- Keep `:core` free of a `:logging` dependency. Log document-tree permission outcomes from feature/data callers instead of `core/storage` unless a later design explicitly changes module direction.

---

## Task 1: Logging Foundations And Skill

**Files:**
- Create: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogFields.kt`
- Create: `logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogFieldsTest.kt`
- Create: `.agents/skills/logging-skill/SKILL.md`
- Create: `.codex/skills/logging-skill/SKILL.md`
- Modify: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogCategory.kt`
- Modify: `logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogEventFormatterTest.kt`

- [ ] **Step 1: Add failing logging foundation tests**

Create `logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogFieldsTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFieldsTest {
    @Test
    fun `new diagnostic categories have stable wire names`() {
        assertEquals("data", LogCategory.DATA.wireName)
        assertEquals("file_io", LogCategory.FILE_IO.wireName)
        assertEquals("download", LogCategory.DOWNLOAD.wireName)
        assertEquals("settings", LogCategory.SETTINGS.wireName)
        assertEquals("home", LogCategory.HOME.wireName)
        assertEquals("lyrics", LogCategory.LYRICS.wireName)
    }

    @Test
    fun `result helpers use stable values`() {
        assertEquals("success", LogFields.Result.SUCCESS)
        assertEquals("failure", LogFields.Result.FAILURE)
        assertEquals("cancelled", LogFields.Result.CANCELLED)
        assertEquals("stale", LogFields.Result.STALE)
        assertEquals("skipped", LogFields.Result.SKIPPED)
    }

    @Test
    fun `host extracts only network host`() {
        assertEquals("example.com", LogFields.host("https://example.com/path?q=1"))
        assertEquals("example.com", LogFields.host("http://example.com:8080/path"))
        assertEquals("", LogFields.host("not a url"))
        assertEquals("", LogFields.host(null))
    }

    @Test
    fun `trimmed preview preserves short input and marks truncation`() {
        assertEquals("short", LogFields.preview("short", maxLength = 8))
        val long = LogFields.preview("abcdefghijklmnop", maxLength = 8)
        assertTrue(long.startsWith("abcdefgh"))
        assertTrue(long.endsWith("..."))
        assertFalse(long.contains("ijklmnop"))
    }
}
```

Append one assertion to `logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogEventFormatterTest.kt` in `formats supported field types`:

```kotlin
assertEquals("success", LogFields.Result.SUCCESS)
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `./gradlew :logging:testDebugUnitTest --no-daemon`

Expected: compile/test failure because `LogCategory.DATA` and `LogFields` do not exist.

- [ ] **Step 3: Add diagnostic categories**

Replace `logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogCategory.kt` with:

```kotlin
package com.zili.android.musicfreeandroid.logging

enum class LogCategory(val wireName: String) {
    APP("app"),
    PLUGIN("plugin"),
    SEARCH("search"),
    PLAYER("player"),
    PLAYLIST_IMPORT("playlist_import"),
    FEEDBACK("feedback"),
    DATA("data"),
    FILE_IO("file_io"),
    DOWNLOAD("download"),
    SETTINGS("settings"),
    HOME("home"),
    LYRICS("lyrics"),
}
```

- [ ] **Step 4: Add field helper object**

Create `logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogFields.kt`:

```kotlin
package com.zili.android.musicfreeandroid.logging

import java.net.URI

object LogFields {
    object Result {
        const val SUCCESS = "success"
        const val FAILURE = "failure"
        const val CANCELLED = "cancelled"
        const val STALE = "stale"
        const val SKIPPED = "skipped"
    }

    object Reason {
        const val CANCELLED = "cancelled"
        const val STALE_GENERATION = "stale_generation"
        const val EMPTY_INPUT = "empty_input"
        const val NOT_FOUND = "not_found"
        const val DUPLICATE = "duplicate"
        const val NETWORK_UNAVAILABLE = "network_unavailable"
        const val CELLULAR_BLOCKED = "cellular_blocked"
        const val UNSUPPORTED = "unsupported"
        const val INVALID_URL = "invalid_url"
        const val UNKNOWN = "unknown"
    }

    fun operation(name: String): Pair<String, String> = "operation" to name
    fun screen(name: String): Pair<String, String> = "screen" to name
    fun result(value: String): Pair<String, String> = "result" to value
    fun reason(value: String): Pair<String, String> = "reason" to value
    fun platform(value: String?): Pair<String, String> = "platform" to value.orEmpty()
    fun item(id: String?, name: String? = null): Map<String, Any?> = mapOf(
        "itemId" to id.orEmpty(),
        "itemName" to name.orEmpty(),
    )

    fun host(url: String?): String = runCatching {
        val parsed = URI(url ?: return "")
        parsed.host.orEmpty()
    }.getOrDefault("")

    fun preview(value: String?, maxLength: Int = 256): String {
        if (value.isNullOrEmpty()) return ""
        if (value.length <= maxLength) return value
        return value.take(maxLength) + "..."
    }
}
```

- [ ] **Step 5: Add logging skill**

Create `.agents/skills/logging-skill/SKILL.md` and copy the same file to `.codex/skills/logging-skill/SKILL.md`:

```markdown
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
```

- [ ] **Step 6: Validate skill folders**

Run:

```bash
python3 "${CODEX_HOME:-$HOME/.codex}/skills/.system/skill-creator/scripts/quick_validate.py" .agents/skills/logging-skill
python3 "${CODEX_HOME:-$HOME/.codex}/skills/.system/skill-creator/scripts/quick_validate.py" .codex/skills/logging-skill
```

Expected: both validations pass. If the script path is unavailable, run `sed -n '1,80p'` on both SKILL files and manually verify frontmatter contains only `name` and `description`.

- [ ] **Step 7: Run logging tests**

Run: `./gradlew :logging:testDebugUnitTest --no-daemon`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogCategory.kt \
  logging/src/main/java/com/zili/android/musicfreeandroid/logging/LogFields.kt \
  logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogFieldsTest.kt \
  logging/src/test/java/com/zili/android/musicfreeandroid/logging/LogEventFormatterTest.kt \
  .agents/skills/logging-skill/SKILL.md \
  .codex/skills/logging-skill/SKILL.md
git commit -m "feat(logging): add diagnostic categories and workflow skill"
```

---

## Task 2: Plugin And Network Diagnostics

**Files:**
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsEngine.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/RequireShim.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/meta/PluginMetaStore.kt`
- Test: existing `plugin/src/test/**`

- [ ] **Step 1: Read plugin rules**

Run: `sed -n '1,220p' docs/dev-harness/plugin/rules.md`

Confirm before editing:

- Do not move QuickJS `Context` / `JsBridge` access to another dispatcher.
- Do not add true-network tests outside `*NetworkIntegrationTest.kt`.
- Do not change DataStore isolation in instrumentation tests.

- [ ] **Step 2: Update AxiosShim network events**

In `AxiosShim.kt`, keep existing behavior and add standard fields to all request, response, and failure events:

```kotlin
val host = LogFields.host(fullUrl)
MfLog.detail(
    LogCategory.PLUGIN,
    "axios_request",
    mapOf(
        "method" to method,
        "url" to fullUrl,
        "host" to host,
        "headerCount" to headers.size,
        "bodyLength" to (body?.length ?: 0),
        "result" to LogFields.Result.SUCCESS,
    )
)
```

For success responses, use `statusCode`, `durationMs`, `responseLength`, `result=success`. For non-2xx responses, keep returning the same response object but log `result=failure` and `reason=http_status`. For malformed URL or request-build failures, log `axios_request_failed` with `result=failure`, `reason=invalid_url` or `reason=request_build_failed`, `url`, `host`, and throwable.

- [ ] **Step 3: Standardize LoadedPlugin API call fields**

In `LoadedPlugin.executeApiCall(...)`, preserve central start/success/failure coverage and add:

```kotlin
val baseFields = mapOf(
    "platform" to info.platform,
    "pluginVersion" to info.version,
    "method" to method,
    "operation" to "plugin_api_call",
)
```

Success events must include `result=success`, `durationMs`, and `count` where the returned value is a list-like plugin result. Failure events must include `result=failure`. Missing unsupported methods must emit `plugin_api_call_skipped` with `result=skipped` and `reason=unsupported` before returning the existing fallback.

- [ ] **Step 4: Add PluginManager operation summaries**

For `installFromUrl`, `installFromFile`, `updatePlugin`, `updateAllPlugins`, subscription update paths, enable/disable, alternative plugin, order changes, userVariables writes, and uninstall paths, add high-level start and terminal events. Use `flowId = UUID.randomUUID().toString()` at public operation entrypoints and pass it through local helper calls where practical.

Required fields:

```kotlin
mapOf(
    "flowId" to flowId,
    "operation" to "install_from_url",
    "platform" to platformOrEmpty,
    "url" to url,
    "host" to LogFields.host(url),
    "targetCount" to targetCount,
    "successCount" to result.successCount,
    "failureCount" to result.failureCount,
    "result" to LogFields.Result.SUCCESS,
)
```

When `PluginOperationResult.failureCount > 0`, emit `MfLog.error` only when the operation wholly fails; emit `MfLog.detail` with `result=skipped` or `result=failure` for partial operation summaries to avoid double-reporting every per-plugin failure.

- [ ] **Step 5: Add PluginMetaStore write diagnostics**

In `PluginMetaStore`, add detail/error events around writes for disabled plugins, plugin order, alternative plugin, subscriptions, and user variables. Keep field values primitive: `platform`, `count`, `operation`, `result`, `reason`.

- [ ] **Step 6: Add RequireShim stable missing-module diagnostics**

Change existing fields from `module` to include `moduleName`; keep `module` if existing tests or logs rely on it. Add `assetPath` to register failures. When unsupported `require(name)` happens, emit `require_module_missing` with `moduleName`, `result=skipped`, `reason=unsupported`.

- [ ] **Step 7: Add JsEngine evaluate duration**

Wrap normal evaluate/evaluateOrNull entrypoints with duration logging without changing dispatcher behavior:

```kotlin
val startedAt = System.nanoTime()
try {
    val result = context.evaluate(script)
    MfLog.detail(LogCategory.PLUGIN, "js_evaluate_success", mapOf(
        "operation" to "evaluate",
        "durationMs" to (System.nanoTime() - startedAt) / 1_000_000,
        "result" to LogFields.Result.SUCCESS,
    ))
    return result
} catch (e: Exception) {
    MfLog.error(LogCategory.PLUGIN, "js_evaluate_failed", e, mapOf(
        "operation" to "evaluate",
        "durationMs" to (System.nanoTime() - startedAt) / 1_000_000,
        "result" to LogFields.Result.FAILURE,
    ))
    throw e
}
```

Adapt names to actual function signatures; do not swallow exceptions that currently propagate.

- [ ] **Step 8: Run plugin tests**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --no-daemon
./gradlew :logging:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add plugin/src/main/java/com/zili/android/musicfreeandroid/plugin logging/src/main/java/com/zili/android/musicfreeandroid/logging
git commit -m "feat(plugin): enrich plugin and network diagnostic logs"
```

---

## Task 3: Data, File IO, And Downloader Diagnostics

**Files:**
- Modify: `data/build.gradle.kts`
- Modify: `downloader/build.gradle.kts`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStore.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/MusicRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlayQueueRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/LyricRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/MediaCacheRepository.kt`
- Modify: `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngine.kt`
- Modify: `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/MediaStoreMusicWriter.kt`
- Modify: `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/OkHttpDownloader.kt`
- Modify: `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/NetworkMonitor.kt`

- [ ] **Step 1: Add module dependencies**

Add `implementation(project(":logging"))` to `dependencies` in `data/build.gradle.kts` and `downloader/build.gradle.kts`.

Do not add `:logging` to `core/build.gradle.kts`.

- [ ] **Step 2: Add PlaylistCoverStore file IO logs**

Instrument `saveFromUri`, `copyFromArtwork`, and `delete` with `LogCategory.FILE_IO`. Preserve current return values.

Required events:

- `playlist_cover_save_start`
- `playlist_cover_save_success`
- `playlist_cover_save_failed`
- `playlist_cover_copy_skipped`
- `playlist_cover_delete`

Use fields: `playlistId`, `pathType`, `sourceScheme`, `sizeBytes`, `result`, `reason`.

- [ ] **Step 3: Add repository write diagnostics**

Add bounded detail/error events around writes only:

- `PlaylistRepository`: favorite toggle, create, update info, set sort mode, manual sort, cover set/delete, playlist delete, add one, add many, remove.
- `MusicRepository`: insert/insertAll/replace/delete/deleteByPlatform.
- `StarredSheetRepository`: upsert/toggle/delete.
- `PlayQueueRepository`: save/clear.
- `LyricRepository`: cache save/delete, associate/clear association, local lyric import/delete, offset.
- `MediaCacheRepository`: parse failure, cache put, prune.

Use category `DATA` for database writes and `LYRICS` for lyric-specific records. If a function already returns silently for missing/duplicate data, log `result=skipped` and a stable `reason`.

- [ ] **Step 4: Add AppPreferences settings write diagnostics**

For setters in `AppPreferences`, log only writes that change runtime behavior: storage directory, search history length, click-play policies, default page/awake, quality defaults/order, max download, cellular play/download, lyric auto-search. Use category `SETTINGS`, event `settings_write`, and fields `key`, `value`, `result`.

- [ ] **Step 5: Add DownloadEngine diagnostics without progress spam**

Add events for:

- `download_engine_start`
- `download_enqueue`
- `download_schedule_skipped`
- `download_prepare_start`
- `download_source_resolve_success`
- `download_source_resolve_failed`
- `download_task_success`
- `download_task_failed`
- `download_task_cancelled`
- `download_retry`
- `download_clear_failed`
- `download_cancel_all`
- `download_cache_cleanup`

Use fields `itemId`, `itemName`, `platform`, `quality`, `url`, `host`, `bytes`, `durationMs`, `result`, `reason`. Do not log every progress callback.

- [ ] **Step 6: Add OkHttpDownloader and MediaStore writer logs**

In `OkHttpDownloader.download`, emit `download_http_start`, `download_http_success`, and `download_http_failed` with `url`, `host`, `statusCode`, `bytes`, `durationMs`, `result`.

In `MediaStoreMusicWriter.commit`, emit `download_mediastore_write_start`, `download_mediastore_write_success`, `download_mediastore_write_failed`, and cleanup result if resolver delete is attempted.

- [ ] **Step 7: Add NetworkMonitor state-change logs only**

Keep the state flow behavior unchanged. Log only when the effective `NetworkState` changes, using event `download_network_state_changed` and fields `state`, `result=success`.

- [ ] **Step 8: Run data/downloader tests**

Run:

```bash
./gradlew :data:testDebugUnitTest :downloader:testDebugUnitTest :logging:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

If Android instrumented tests are available, run:

```bash
./gradlew :data:connectedDebugAndroidTest --no-daemon
```

If no device is available, record that this runtime check was skipped.

- [ ] **Step 9: Commit**

```bash
git add data downloader
git commit -m "feat(logging): cover data file io and download diagnostics"
```

---

## Task 4: Feature ViewModel Diagnostics

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/downloading/DownloadingViewModel.kt`
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/FileSelectorLiteViewModel.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/pluginlist/PluginListViewModel.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/pluginsub/PluginSubscriptionViewModel.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/pluginsort/PluginSortViewModel.kt`

- [ ] **Step 1: Read test rules**

Run: `sed -n '1,220p' docs/dev-harness/test/rules.md`

Confirm before editing:

- Do not add `runBlocking + Flow.first { predicate }` ViewModel tests.
- Do not inject logger constructor parameters unless unavoidable.
- If constructor changes happen, update all fixtures and run module `testDebugUnitTest`.

- [ ] **Step 2: Add plugin-driven async load logs**

In `RecommendSheetsViewModel`, `TopListViewModel`, `PluginSheetDetailViewModel`, `TopListDetailViewModel`, `AlbumDetailViewModel`, `ArtistDetailViewModel`, and `MusicDetailViewModel`, add start/success/failure/stale events around existing async plugin loads.

Use fields:

```kotlin
mapOf(
    "screen" to "plugin_sheet_detail",
    "operation" to "load_initial",
    "flowId" to flowId,
    "generation" to generation,
    "platform" to platform,
    "sheetId" to sheetId,
    "page" to page,
    "count" to items.size,
    "durationMs" to durationMs,
    "result" to LogFields.Result.SUCCESS,
)
```

Where a ViewModel already uses generation guards, log `result=stale` before returning from stale branches.

- [ ] **Step 3: Add Home/local scan and user action logs**

In `HomeViewModel` and `LocalMusicViewModel`, log local scan start/success/failure/cancelled, storage directory persistence, play item, create playlist, add to playlist, and download intent. Use category `HOME` for screen actions and `FILE_IO` for directory/file access outcomes.

- [ ] **Step 4: Add playlist and editor action logs**

In `PlaylistViewModel`, `PlaylistDetailViewModel`, and `MusicListEditorLiteViewModel`, log create/rename/delete/sort/update cover/add/remove/batch edit/batch download actions. Keep repository-level data logs as the source of write details; ViewModel logs should capture user intent and UI result.

- [ ] **Step 5: Enrich SearchViewModel existing logs**

Preserve existing `SEARCH` events. Add missing `flowId`, `result`, `count`, `generation` or request identity where available, `durationMs`, and `reason` for stale/cancelled/skipped paths. Do not rename existing event names unless necessary.

- [ ] **Step 6: Add settings and plugin-list UI operation logs**

In `SettingsViewModel`, `FileSelectorLiteViewModel`, `PluginListViewModel`, `PluginSubscriptionViewModel`, and `PluginSortViewModel`, log settings writes, plugin install/update/uninstall/import/userVariables UI operations, subscription edits, order save, file-selector directory persist, feedback package create/share/clear. Preserve existing feedback errors.

PluginListViewModel must log operation start before `performOperation` and one terminal result in the `try/catch` body. If `targetImportInProgress` or loading state blocks an action, emit `result=skipped` with `reason=operation_in_progress`.

- [ ] **Step 7: Add downloading page intent logs**

In `DownloadingViewModel`, wrap `cancel`, `retry`, `retryAllFailed`, `clearFailed`, and `cancelAllInflight` with `HOME` or `DOWNLOAD` category detail logs. These are user intents; engine-level logs will record actual execution.

- [ ] **Step 8: Run feature tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest :feature:search:testDebugUnitTest :feature:settings:testDebugUnitTest :logging:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add feature/home feature/search feature/settings
git commit -m "feat(logging): add feature viewmodel diagnostics"
```

---

## Task 5: Player, Player UI, And Lyrics Diagnostics

**Files:**
- Modify: `feature/player-ui/build.gradle.kts`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoader.kt`
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/service/PlaybackService.kt`

- [ ] **Step 1: Read player rules**

Run: `sed -n '1,220p' docs/dev-harness/player/rules.md`

Confirm before editing:

- Do not touch high-frequency lyric follow Compose effects.
- Do not alter lyric follow debounce helpers.
- Do not change `PlayerController.connect()` instrumentation threading.

- [ ] **Step 2: Add player-ui logging dependency**

In `feature/player-ui/build.gradle.kts`, add:

```kotlin
implementation(project(":logging"))
```

Keep dependency order near existing `project(":player")` / `project(":data")` entries.

- [ ] **Step 3: Add PlayerViewModel user action logs**

In `PlayerViewModel`, add detail/error events for favorite toggle, quality/speed, download, show/hide/add-to-playlist, create playlist, play/pause, skip, seek, mode changes, queue index/remove/clear, lyric search/associate/import/offset/delete.

Use `LogCategory.PLAYER` for playback controls and `LogCategory.LYRICS` for lyric operations. Use `screen=player`, `itemId`, `itemName`, `platform`, `quality`, `speed`, `positionMs`, `result`, `reason`.

- [ ] **Step 4: Add PlayerLyricLoader boundary logs**

In `PlayerLyricLoader`, log:

- `lyric_load_start`
- `lyric_cache_hit`
- `lyric_plugin_fetch_start`
- `lyric_plugin_fetch_success`
- `lyric_plugin_fetch_failed`
- `lyric_auto_search_start`
- `lyric_auto_search_success`
- `lyric_auto_search_failed`
- `lyric_no_lyric`
- `lyric_load_failed`

Preserve `CancellationException` rethrow behavior. Do not log inside Compose lyric follow code.

- [ ] **Step 5: Add PlayerController quality and playback terminal fields**

Preserve existing player logs and add standard fields where missing: `operation`, `result`, `durationMs`, `quality`, `platform`, `itemId`, `reason`. Specifically instrument `changeQuality`, media-source resolve fallbacks, playback blocked by network, queue mutations, and Media3 runtime failures.

- [ ] **Step 6: Add PlaybackService lifecycle/command logs**

Add `playback_task_removed` to `onTaskRemoved`. Enrich service command events with `operation`, `result`, and `reason` for skipped/unsupported custom commands. Do not change notification or lifecycle behavior.

- [ ] **Step 7: Run player tests**

Run:

```bash
./gradlew :player:testDebugUnitTest :feature:player-ui:testDebugUnitTest :logging:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

If lyric tests are touched or fail, run focused tests:

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*MiniPlayerContentTest' --tests '*LyricFollow*Test' --tests '*LyricSeekOverlay*Test' --no-daemon
```

- [ ] **Step 8: Commit**

```bash
git add feature/player-ui player
git commit -m "feat(logging): add player and lyric diagnostics"
```

---

## Task 6: Integration, Guardrails, And Final Verification

**Files:**
- Modify if needed: `AGENTS.md` only if the new logging skill path or rule needs to be surfaced.
- Modify if needed: `docs/DOCS_STATUS.md` only if plan/spec status needs correction.
- No broad refactors.

- [ ] **Step 1: Search for forbidden direct logs**

Run:

```bash
rg -n "android\\.util\\.Log|Log\\.[a-z]\\(" app core data downloader feature plugin player -g '*.kt'
```

Expected: only allowed existing logging internals or no business-code direct `Log.*`. If new direct `Log.*` appears in business code, replace it with `MfLog`.

- [ ] **Step 2: Search for missing obvious error logs**

Run:

```bash
rg -n "catch \\([^)]*\\) \\{|runCatching \\{" data downloader feature plugin player -g '*.kt'
```

Review changed files only. Every swallowed error or UI degradation introduced/touched in this branch must have a nearby `MfLog.error` or clear normal-cancellation reason.

- [ ] **Step 3: Run module tests**

Run:

```bash
./gradlew :logging:testDebugUnitTest \
  :plugin:testDebugUnitTest \
  :data:testDebugUnitTest \
  :downloader:testDebugUnitTest \
  :feature:home:testDebugUnitTest \
  :feature:search:testDebugUnitTest \
  :feature:settings:testDebugUnitTest \
  :player:testDebugUnitTest \
  :feature:player-ui:testDebugUnitTest \
  --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run dev harness**

Run:

```bash
bash scripts/dev-harness/check.sh
```

Expected: all guard checks pass.

- [ ] **Step 5: Build app debug**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Runtime smoke if device is available**

Run:

```bash
adb devices
```

If a device/emulator is listed, install and smoke core flows:

```bash
./gradlew :app:installDebug --no-daemon
```

Open the app, perform one plugin search or plugin install, one local scan or download, then generate a feedback log package from settings. Decode the package with the existing `tools/logan` docs and confirm JSON lines include new categories such as `plugin`, `download`, `file_io`, `home`, or `lyrics`.

If no device is listed, record runtime smoke as skipped.

- [ ] **Step 7: Final diff review**

Run:

```bash
git status --short
git log --oneline --decorate -n 8
git diff --stat main...HEAD
```

Confirm only intended logging/spec/plan/skill/module files changed.

- [ ] **Step 8: Commit any integration fixes**

If Step 1-7 required fixes:

```bash
git add <fixed-files>
git commit -m "chore(logging): finalize diagnostics integration"
```

If no fixes were needed, do not create an empty commit.
