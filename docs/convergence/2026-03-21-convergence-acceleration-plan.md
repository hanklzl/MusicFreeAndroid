# MusicFree Convergence Acceleration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-prioritize convergence work around real user journeys, shared collection tooling, and global UI shell parity, while enabling safe parallel development on independent write sets.

**Architecture:** Treat convergence as three coordinated tracks: shared collection foundations, high-frequency flow hardening, and UI shell/visual parity. Parallelize only tasks with disjoint write sets; all compile, unit, connected, emulator-click, and merge acceptance returns to one controller/reviewer.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Hilt, Room, DataStore, Media3, QuickJS plugin runtime, Gradle, adb/emulator, JVM tests, connected Android tests

---

## Current Findings

- `searchMusicList` is already implemented on Android in commit `06e6d51`, so the docs are stale and the true remaining missing RN pages are now:
  - `musicListEditor`
  - `fileSelector`
  - `downloading`
  - `setCustomTheme`
- User-provided original screenshots show the biggest product gap is not raw page count. It is the mismatch in:
  - global shell: splash, drawer, top bar style, mini-player, list density
  - collection operations: search-in-list, batch edit, local import, download path, download queue
  - high-frequency end-to-end flows: import subscription, search, play, open detail pages, return to shell
- Android’s strongest verified flow today is still `default subscription -> search -> play`. Most plugin-backed detail flows exist in code but remain under-validated on real data.

## Hard Rules For Parallel Development

- Only parallelize tasks whose write sets are disjoint.
- If two tasks both touch `Routes.kt`, `AppNavHost.kt`, the same feature screen, the same repository, or the same convergence doc, they are not parallel-safe.
- Multiple agents may implement independent features in parallel, but final acceptance must be centralized:
  - one controller
  - one integration branch
  - one compile/test gate
  - one emulator-click review pass
- Parallel implementation is allowed only after each task packet has:
  - explicit file ownership
  - explicit test commands
  - explicit merge order

## Validation Gate

Every iteration must pass this shared gate before it is considered accepted:

- [ ] `./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"`
- [ ] `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest"`
- [ ] `./gradlew :plugin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginRuntimeIntegrationTest#defaultSubscription_installAndWyPlaybackChain_succeeds`
- [ ] `./gradlew :player:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.player.controller.PlayerControllerTest`
- [ ] `./gradlew :player:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.player.service.PlaybackServiceTest`
- [ ] Emulator real-click replay of:
  - Home
  - Drawer
  - Settings
  - Permissions
  - Default subscription import
  - Search result to player
  - Recommend sheets detail
  - Top list detail
  - Music detail
  - Album detail
  - Artist detail
  - History populated state
  - Playlist/search-in-list when local data exists

## Parallelization Matrix

### Safe To Parallelize

- `musicListEditor-lite` screen/state work vs plugin-flow validation docs/screenshots
- `fileSelector` SAF/storage foundation vs player/detail-flow emulator verification
- Home/drawer/player visual shell convergence vs collection repository foundation
- Docs/status backlog updates vs code changes, but only if one agent owns each doc file

### Not Safe To Parallelize

- Two tasks that both change:
  - [Routes.kt](/Users/zili/code/android/MusicFreeAndroid/core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt)
  - [AppNavHost.kt](/Users/zili/code/android/MusicFreeAndroid/app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt)
  - [HomeScreen.kt](/Users/zili/code/android/MusicFreeAndroid/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt)
  - the same `ViewModel`
  - the same repository/DAO
  - [docs/convergence/STATUS.md](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/STATUS.md)
  - the same iteration `analysis.md` or `verification.md`

## Next 3 Iterations

### Iteration 13 Tail: Baseline Repair + Flow Proof

**Why now:** The branch already contains `searchMusicList`; the docs and screenshot baseline are behind reality.

### Task 1: Repair Convergence Baseline After SearchMusicList

**Files:**
- Modify: [docs/convergence/STATUS.md](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/STATUS.md)
- Modify: [docs/convergence/iteration-13/analysis.md](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/iteration-13/analysis.md)
- Create/Modify: [docs/convergence/iteration-13/verification.md](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/iteration-13/verification.md)
- Use screenshots in: [docs/convergence/screenshots/iteration-13](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/screenshots/iteration-13)

- [ ] Update page coverage from `14/19` to `15/19`
- [ ] Remove `searchMusicList` from the missing-page backlog and rewrite its item as “partially converged”
- [ ] Add current Android screenshot references for home/drawer/history
- [ ] Add notes from the user-provided original screenshots:
  - orange top bar system
  - left drawer depth
  - mini-player shape
  - dense list item layout
- [ ] Commit docs baseline update

### Task 2: Real-Click Validation Sweep For Existing Plugin-Backed Detail Flows

**Files:**
- Modify: [docs/convergence/iteration-13/verification.md](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/iteration-13/verification.md)
- Modify: [docs/convergence/STATUS.md](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/STATUS.md)
- Use screenshots in: [docs/convergence/screenshots/iteration-13](/Users/zili/code/android/MusicFreeAndroid/docs/convergence/screenshots/iteration-13)

- [ ] Replay `default subscription -> search -> play`
- [ ] Replay `recommend sheets -> detail -> song -> music detail`
- [ ] Replay `top list -> detail -> song -> music detail`
- [ ] Replay `music detail -> album detail / artist detail`
- [ ] Capture one screenshot per terminal state
- [ ] Record failures as backlog items, not vague “待补”
- [ ] Commit verification artifacts

### Iteration 14: Shared Collection Foundations + MusicListEditor-lite

**Why now:** RN treats `searchMusicList` and `musicListEditor` as generic collection tools. Android needs reusable collection abstractions before it can converge multiple pages cheaply.

### Task 3: Introduce Generic Collection Source Model

**Files:**
- Modify: [core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt](/Users/zili/code/android/MusicFreeAndroid/core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt)
- Modify: [app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt](/Users/zili/code/android/MusicFreeAndroid/app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt)
- Modify: [feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt](/Users/zili/code/android/MusicFreeAndroid/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt)
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/collection/CollectionSource.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/collection/CollectionCapabilities.kt`
- Test: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/collection/*`

- [ ] Write failing tests for local-library and transient collection-source resolution
- [ ] Run the new tests and confirm RED
- [ ] Implement minimal `CollectionSource` / loader support
- [ ] Extend `searchMusicList` reachability without breaking playlist/history
- [ ] Run collection tests, `RoutesTest`, and compile
- [ ] Commit collection-source foundation

### Task 4: Build MusicListEditor-lite For Playlist Sources

**Files:**
- Modify: [core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt](/Users/zili/code/android/MusicFreeAndroid/core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt)
- Modify: [app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt](/Users/zili/code/android/MusicFreeAndroid/app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt)
- Modify: [feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt](/Users/zili/code/android/MusicFreeAndroid/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt)
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorScreen.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorViewModel.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorUiState.kt`
- Create/Modify tests under `feature/home/src/test/.../musiclisteditor`

- [ ] Write failing tests for multi-select, delete, save, add-next-play, add-to-playlist
- [ ] Run targeted tests and confirm RED
- [ ] Implement playlist-only editor lite
- [ ] Reuse existing playlist dialog and player queue APIs
- [ ] Do not add download support in this slice
- [ ] Run unit tests, compile, and emulator click verification
- [ ] Commit musicListEditor-lite

### Iteration 15: Storage Access + FileSelector-class Capabilities

**Why now:** `fileSelector` is not just a page. It unlocks local scan, backup/export, and download path selection.

### Task 5: Add SAF-Backed StorageAccessGateway

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/storage/StorageAccessGateway.kt`
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/storage/DocumentTreeItem.kt`
- Modify: [feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt](/Users/zili/code/android/MusicFreeAndroid/feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt)
- Modify: [feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt](/Users/zili/code/android/MusicFreeAndroid/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt)
- Test: focused unit tests and one connected test if feasible

- [ ] Write failing tests around persisted URI/path preference behavior
- [ ] Implement minimal SAF gateway
- [ ] Add one settings entry or local-scan entry to prove the gateway works
- [ ] Run unit tests, one connected test if natural, and compile
- [ ] Commit SAF/storage foundation

### Task 6: Add FileSelector-lite UI On Top Of StorageAccessGateway

**Files:**
- Modify: [core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt](/Users/zili/code/android/MusicFreeAndroid/core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt)
- Modify: [app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt](/Users/zili/code/android/MusicFreeAndroid/app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt)
- Create: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/*`
- Test: focused route/UI state tests

- [ ] Write failing tests for file/folder selection state
- [ ] Implement fileSelector-lite around SAF results
- [ ] Wire one caller first:
  - local scan OR backup/export OR download path selection
- [ ] Run tests, compile, emulator click validation
- [ ] Commit fileSelector-lite

## Candidate Parallel Task Packets

### Packet A: Collection Track
- Task 3
- Task 4
- Serial within packet, because both touch shared collection abstractions and nav

### Packet B: Verification Track
- Task 2
- Task 1 doc repair
- Can run in parallel with Packet A if docs ownership is split cleanly

### Packet C: UI Shell Track
- Home/drawer/player first-impression parity
- Must avoid touching `Routes.kt`, `AppNavHost.kt`, and verification docs during the same wave

### Packet D: Storage/Download Track
- Task 5
- Task 6
- Future downloader core and `downloading` page
- Serial within packet, but parallelizable against Packet C

## Final Acceptance Ownership

- One controller or one designated reviewer owns:
  - cherry-pick/merge order
  - compile and unit-test reruns
  - connected test reruns
  - emulator real-click replay
  - final doc updates
- Parallel agents may not mark work “accepted” on their own.

