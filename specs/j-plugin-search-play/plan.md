# J-PLUGIN-SEARCH-PLAY Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive `J-PLUGIN-SEARCH-PLAY` from `Ready for Plan` to `Functional Done` by using the current default-subscription import path, adding only the minimum controlled-live baseline and debug-assisted observability needed for repeatable search and playback verification.

**Architecture:** Keep the real user path intact: `Home -> Drawer -> Settings -> 默认订阅导入 -> Home/Search -> Search -> Player -> Pause/Resume`. Add only the smallest supporting assets required to make that path repeatable: a checked-in controlled-live baseline doc, stable semantics/log anchors on settings/search/player, and a minimal debug-only reset/snapshot hook that instrumentation can use to start from a known plugin/player state. Verification remains journey-first: one happy path and one failure path instrumentation test, plus closeout evidence and portfolio updates.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Media3, QuickJS plugin runtime, Android instrumentation, Compose UI Test, debug-only Android source set, Markdown evidence docs

---

## File Structure

### New files in this repo

| File | Responsibility |
|------|---------------|
| `specs/j-plugin-search-play/fixtures/controlled-live.md` | Controlled-live subscription source, query set, acceptance rules, and known instability boundaries |
| `app/src/main/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayDebugContract.kt` | Shared action names, extras, and snapshot file names for the pilot debug hooks |
| `app/src/debug/AndroidManifest.xml` | Register debug-only receiver for pilot reset/snapshot actions |
| `app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlayDebugEntryPoint.kt` | Hilt entry point to access `PluginManager` and `PlayerController` from debug-only code |
| `app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlayDebugReceiver.kt` | Debug-only broadcast receiver for reset/snapshot actions |
| `app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlaySnapshotStore.kt` | Persist pilot plugin/player snapshots to app cache for instrumentation assertions |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayDebugHookSmokeTest.kt` | Smoke test for the pilot debug-only reset/snapshot path |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayJourneyTest.kt` | Happy-path and failure-path journey instrumentation tests |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayJourneyTestSupport.kt` | Shared helpers for sending debug actions, reading snapshots, and waiting on tags |
| `core/src/test/java/com/zili/android/musicfreeandroid/core/ui/PluginSearchPlayAnchorContractTest.kt` | Contract coverage for newly introduced pilot anchors |
| `specs/j-plugin-search-play/evidence/functional-smoke.md` | Functional evidence summary after the journey tests pass |
| `specs/j-plugin-search-play/review.md` | Closeout notes, remaining risks, and next enablers after Functional Done |

### Modified files in this repo

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt` | Add settings/search/player anchors needed by the pilot |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt` | Tag the default subscription CTA, install state summary, and plugin rows |
| `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt` | Add stable pilot log markers and expose deterministic install-state transitions |
| `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt` | Tag the query field, submit control, plugin chips, result rows, and empty states |
| `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt` | Add stable search/playback log markers for the pilot |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt` | Tag the player root and pause/resume transport controls |
| `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt` | Add stable playback-state log markers used by pilot verification |
| `specs/portfolio.md` | Move `J-PLUGIN-SEARCH-PLAY` from `Ready for Plan` to `Functional Done` after evidence exists |
| `specs/j-plugin-search-play/verification-matrix.md` | Update statuses from `Planned` to actual outcome at closeout |

### Existing files used as-is

| File | Role in this journey |
|------|----------------------|
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt` | Existing navigation-test style to mirror for journey assertions |
| `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt` | Existing unit-test surface for default-subscription behavior |
| `feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModelTest.kt` | Existing unit-test surface for search and playback resolution |
| `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelTest.kt` | Existing unit-test surface for pause/resume behavior |
| `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt` | Existing runtime evidence that the default subscription and WY path are live and playable |
| `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayJourneyTest.kt` | Planned exact journey test path targeted by this plan |

---

## Task 1: Pin the Controlled-Live Baseline

**Files:**
- Create: `specs/j-plugin-search-play/fixtures/controlled-live.md`
- Modify: `specs/j-plugin-search-play/journey-spec.md`
- Modify: `specs/j-plugin-search-play/verification-matrix.md`

- [ ] **Step 1: Create the fixture directory and verify it is new**

Run:

```bash
test -d specs/j-plugin-search-play/fixtures && echo "unexpected: fixtures already exists" && exit 1 || echo "fixtures missing as expected"
```

Expected: prints `fixtures missing as expected`.

- [ ] **Step 2: Write `controlled-live.md`**

Create the doc with these exact sections:

```markdown
# Controlled Live Baseline

## Subscription Source
- `https://13413.kstore.vip/yuanli/yuanli.json`
- Journey path uses `SettingsViewModel.installDefaultSubscription()`

## Query Set
- `in the end`
- `In The End Linkin Park`
- `linkin park`

## Acceptance Rules
- Search result must be non-empty
- Ranking is not asserted
- Success is defined as: can open player, can observe pause/resume

## Known Volatility
- Plugin list returned by subscription may change
- Search result order may change
- Verification must rely on non-empty and playable, not exact first-row identity
```

Record the source of each query in the fixture doc:

- `in the end` comes from `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt`
- `In The End Linkin Park` comes from `feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/MusicMatchTest.kt`
- `linkin park` is the narrow fallback query derived from the same title/artist pair and reserved for reruns when ranking shifts

- [ ] **Step 3: Align the journey docs with the new baseline**

Update:

- `journey-spec.md` to reference `fixtures/controlled-live.md` in `Current Android Status`
- `verification-matrix.md` `Data Strategy` rows so the query set and subscription source point to `specs/j-plugin-search-play/fixtures/controlled-live.md`

- [ ] **Step 4: Verify the baseline doc and references**

Run:

```bash
test -f specs/j-plugin-search-play/fixtures/controlled-live.md
rg -n "^## Subscription Source$|^## Query Set$|^## Acceptance Rules$|^## Known Volatility$" specs/j-plugin-search-play/fixtures/controlled-live.md
rg -n "controlled-live.md" specs/j-plugin-search-play/journey-spec.md specs/j-plugin-search-play/verification-matrix.md
```

Expected: all checks pass and all `rg` commands print matches.

- [ ] **Step 5: Commit**

```bash
git add specs/j-plugin-search-play/fixtures/controlled-live.md \
  specs/j-plugin-search-play/journey-spec.md \
  specs/j-plugin-search-play/verification-matrix.md
git commit -m "docs(journey): add controlled live baseline for plugin search play"
```

---

## Task 2: Add Pilot Anchors and Runtime Log Markers

**Relevant skills:** `@test-driven-development`, `@jetpack-compose`

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt`
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Create: `core/src/test/java/com/zili/android/musicfreeandroid/core/ui/PluginSearchPlayAnchorContractTest.kt`

- [ ] **Step 1: Write the failing anchor contract test**

Create `PluginSearchPlayAnchorContractTest.kt` with assertions for these anchor constants/helpers:

```kotlin
class PluginSearchPlayAnchorContractTest {
    @Test
    fun `pilot anchors are unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Settings.DefaultSubscriptionImport,
            FidelityAnchors.Settings.InstallStateSummary,
            FidelityAnchors.Search.QueryInput,
            FidelityAnchors.Search.SubmitButton,
            FidelityAnchors.Player.Root,
            FidelityAnchors.Player.PlayPause,
        )
        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.core.ui.PluginSearchPlayAnchorContractTest"
```

Expected: FAIL because the pilot anchor groups do not exist yet.

- [ ] **Step 3: Extend the anchor registry**

Add these new groups to `FidelityAnchors.kt`:

```kotlin
object Settings {
    const val PluginManagementEntry = "settings.pluginManagement.entry"
    const val DefaultSubscriptionImport = "settings.subscription.default"
    const val InstallStateSummary = "settings.install.state"
}

object Search {
    const val QueryInput = "search.input.query"
    const val SubmitButton = "search.action.submit"
    const val EmptyStateNoPlugins = "search.empty.noPlugins"
    const val EmptyStateNoSearchablePlugins = "search.empty.noSearchablePlugins"
    const val EmptyStateNoResults = "search.empty.noResults"
}

object Player {
    const val Root = "player.screen.root"
    const val PlayPause = "player.control.playPause"
    const val SkipNext = "player.control.skipNext"
    const val SkipPrevious = "player.control.skipPrevious"
}

object FidelityAnchorPatterns {
    // preserve existing helpers
    fun searchPluginChip(platform: String) = "search.pluginChip.$platform"
    fun searchResultRow(index: Int) = "search.result.row.$index"
    fun settingsPluginRow(platform: String) = "settings.plugin.row.$platform"
}
```

- [ ] **Step 4: Wire the anchors into the journey UI**

Apply the new tags in:

- `SettingsScreen.kt`
  - default subscription card/action
  - install-state summary text/progress
  - plugin rows
- `SearchScreen.kt`
  - query field
  - submit/search icon button
  - plugin chips
  - result rows
  - empty states
- `PlayerScreen.kt`
  - root
  - play/pause
  - next/previous

Use `.testTag(...)` and, where needed, `.semantics { testTagsAsResourceId = true }` on the roots so `uiautomator dump` can see them later.

- [ ] **Step 5: Add stable pilot log markers**

Add consistent log lines under a single tag, for example `JourneyPluginSearchPlay`, covering:

- default subscription import started / finished / failed
- search started / finished / failed
- playback resolve succeeded / failed
- player play / player pause

Do this in:

- `SettingsViewModel.kt`
- `SearchViewModel.kt`
- `PlayerController.kt`

Do not log raw payload bodies; log only state transitions and identifiers needed for verification.

- [ ] **Step 6: Re-run the anchor test and targeted unit tests**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.core.ui.PluginSearchPlayAnchorContractTest" \
  :feature:settings:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.settings.SettingsViewModelTest" \
  :feature:search:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.search.SearchViewModelTest" \
  :feature:player-ui:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModelTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt \
  core/src/test/java/com/zili/android/musicfreeandroid/core/ui/PluginSearchPlayAnchorContractTest.kt \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt \
  feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt \
  feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt \
  feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt \
  player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt
git commit -m "feat(journey): add plugin search play anchors and logs"
```

---

## Task 3: Add Minimal Debug-Only Reset and Snapshot Hooks

**Relevant skills:** `@test-driven-development`

**Files:**
- Create: `app/src/main/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayDebugContract.kt`
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlayDebugEntryPoint.kt`
- Create: `app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlaySnapshotStore.kt`
- Create: `app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlayDebugReceiver.kt`
- Create: `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayDebugHookSmokeTest.kt`
- Create: `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayJourneyTestSupport.kt`

- [ ] **Step 1: Write the failing debug-hook smoke test helper usage**

Create `PluginSearchPlayJourneyTestSupport.kt` first with TODO-driving helper signatures used by later tests:

```kotlin
object PluginSearchPlayJourneyTestSupport {
    fun sendResetBroadcast(context: Context) = TODO()
    fun sendSnapshotBroadcast(context: Context, snapshotName: String) = TODO()
    fun readSnapshot(context: Context, snapshotName: String): String = TODO()
}
```

Create `PluginSearchPlayDebugHookSmokeTest.kt` under `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/` that expects a snapshot file after sending the broadcast.

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.journeys.PluginSearchPlayDebugHookSmokeTest
```

Expected: FAIL because the debug receiver and support code do not exist yet.

- [ ] **Step 3: Implement the minimal debug contract**

Create `PluginSearchPlayDebugContract.kt` with:

- broadcast action for reset: `com.zili.android.musicfreeandroid.debug.RESET_PLUGIN_SEARCH_PLAY`
- broadcast action for snapshot: `com.zili.android.musicfreeandroid.debug.SNAPSHOT_PLUGIN_SEARCH_PLAY`
- extra key for snapshot name
- fixed snapshot file name prefix

- [ ] **Step 4: Implement the debug-only receiver path**

Implement:

- `PluginSearchPlayDebugEntryPoint.kt` to access `PluginManager` and `PlayerController`
- `PluginSearchPlaySnapshotStore.kt` to write a JSON snapshot in app cache
- `PluginSearchPlayDebugReceiver.kt` to:
  - clear persisted plugin state by deleting every file under `File(context.filesDir, "plugins")`, matching the strategy already used by `PluginRuntimeIntegrationTest.clearPluginStorage()`
  - call `loadAllPlugins()` immediately after deletion so in-memory state matches disk
  - pause playback if currently playing
  - clear the player queue by removing items until `playerController.playQueue.isEmpty`
  - clear play history with `playerController.clearHistory()`
  - write a snapshot containing plugin count, plugin platforms, and player `isPlaying/currentItem`
- `app/src/debug/AndroidManifest.xml` to register the receiver only in debug builds

This receiver is intentionally thin. Do not build a generic command framework.

- [ ] **Step 5: Implement the instrumentation support helper**

Update `PluginSearchPlayJourneyTestSupport.kt` so tests can:

- send reset and snapshot broadcasts
- wait briefly for the receiver to complete
- read the resulting JSON snapshot

- [ ] **Step 6: Re-run the smoke test**

Run the same instrumentation command from Step 2.

Expected: PASS on a connected device/emulator.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayDebugContract.kt \
  app/src/debug/AndroidManifest.xml \
  app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlayDebugEntryPoint.kt \
  app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlaySnapshotStore.kt \
  app/src/debug/java/com/zili/android/musicfreeandroid/debug/journeys/PluginSearchPlayDebugReceiver.kt \
  app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayDebugHookSmokeTest.kt \
  app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayJourneyTestSupport.kt
git commit -m "feat(journey): add plugin search play debug hooks"
```

---

## Task 4: Implement the Journey Integration Tests

**Relevant skills:** `@test-driven-development`, `@systematic-debugging`

**Files:**
- Create: `app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayJourneyTest.kt`

- [ ] **Step 1: Write the failing happy-path and failure-path tests**

Create `PluginSearchPlayJourneyTest.kt` with two tests:

```kotlin
@Test
fun defaultSubscription_search_openPlayer_pauseResume_reachesFunctionalDonePath()

@Test
fun noPlugins_searchShowsEmptyState()
```

Happy-path outline:

1. launch `MainActivity`
2. use `PluginSearchPlayJourneyTestSupport.sendResetBroadcast()`
3. navigate via home drawer to settings
4. trigger default subscription import
5. assert install state success text
6. navigate back to home, then to search
7. enter first controlled-live query
8. assert non-empty result row
9. tap first result row
10. assert player root
11. tap play/pause and assert state change or snapshot/log confirmation

Failure-path outline:

1. reset plugin state
2. go to search directly from home
3. assert `NoPlugins` or `NoSearchablePlugins` empty-state anchor/text

- [ ] **Step 2: Run the instrumentation class to verify it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.journeys.PluginSearchPlayJourneyTest
```

Expected: FAIL before the UI tags, logs, and support helpers are fully aligned.

- [ ] **Step 3: Make the tests pass using the new anchors and hooks**

Use the existing style from:

- `HomeEntryNavigationTest.kt`
- `HomeFidelityHomeStructureTest.kt`

Keep the helpers local to this journey test. Do not create a generic journey framework.

- [ ] **Step 4: Re-run the journey tests**

Run the same instrumentation command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/zili/android/musicfreeandroid/journeys/PluginSearchPlayJourneyTest.kt
git commit -m "test(journey): add plugin search play instrumentation coverage"
```

---

## Task 5: Functional Closeout and Evidence

**Files:**
- Create: `specs/j-plugin-search-play/evidence/functional-smoke.md`
- Create: `specs/j-plugin-search-play/review.md`
- Modify: `specs/j-plugin-search-play/verification-matrix.md`
- Modify: `specs/portfolio.md`

- [ ] **Step 1: Run the full Functional Done verification set**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.core.ui.PluginSearchPlayAnchorContractTest" \
  :feature:settings:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.settings.SettingsViewModelTest" \
  :feature:search:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.search.SearchViewModelTest" \
  :feature:player-ui:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModelTest" \
  :plugin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeIntegrationTest \
  :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.journeys.PluginSearchPlayJourneyTest
```

Expected: PASS.

- [ ] **Step 2: Write the evidence summary**

Create `specs/j-plugin-search-play/evidence/functional-smoke.md` with:

- executed commands
- pass/fail outcome
- controlled-live query used
- which plugin platform actually satisfied the search/play path
- any non-blocking instability observed

- [ ] **Step 3: Update the verification matrix statuses**

Update `verification-matrix.md`:

- mark all `Functional Done` rows as `Passed`
- keep `Fidelity Done` rows `Deferred`
- mark minimum integration tests as `Passed`

- [ ] **Step 4: Update the portfolio and write the review**

Update `specs/portfolio.md` so:

- `J-PLUGIN-SEARCH-PLAY` becomes `Functional Done`
- `Current Gap` points to deferred fidelity work
- `Next Action` points to the future fidelity plan

Create `specs/j-plugin-search-play/review.md` with:

- what worked
- what had to be added as minimal enablers
- what remains before `Fidelity Done`
- whether any new reusable enabler should stay in `Infra / Enablers`

- [ ] **Step 5: Commit**

```bash
git add specs/j-plugin-search-play/evidence/functional-smoke.md \
  specs/j-plugin-search-play/review.md \
  specs/j-plugin-search-play/verification-matrix.md \
  specs/portfolio.md
git commit -m "docs(journey): close out plugin search play functional done"
```

---

## Exit Criteria

This journey plan is complete when:

1. `specs/j-plugin-search-play/fixtures/controlled-live.md` exists and defines the baseline
2. settings/search/player expose the minimum anchors and logs required by the pilot
3. a minimal debug-only reset/snapshot path exists for this journey only
4. `PluginSearchPlayJourneyTest.kt` passes for happy path and failure path
5. `specs/j-plugin-search-play/evidence/functional-smoke.md` exists
6. `specs/portfolio.md` marks `J-PLUGIN-SEARCH-PLAY` as `Functional Done`

## Follow-On Work

Do not extend this plan once the exit criteria are met. The next plan should target:

1. `J-PLUGIN-SEARCH-PLAY` fidelity convergence
2. richer controlled-live fixtures only if the current baseline proves too unstable
