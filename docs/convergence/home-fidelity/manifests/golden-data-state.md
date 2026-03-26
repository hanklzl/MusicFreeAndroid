# Home Fidelity Golden Data State

Status: verification run completed on `2026-03-26`, but the checked-in fixtures are still bootstrap-only and do not satisfy the approved golden-state checklist. The full regression bundle passed. Baseline artifact capture succeeded only for the two top fragments on both platforms.

## Device

- Emulator id: `emulator-5554`
- Physical size: `1080x2400`
- Physical density: `420`
- Crop rectangle:
  - `crop.leftPx: 0`
  - `crop.topPx: 63`
  - `crop.rightPx: 1080`
  - `crop.bottomPx: 2337`

## Commits

- Android build commit: `4ca4381` (`home-fidelity-implementation`)
- RN reference commit: `f700035` (`home-fidelity-reference-anchors`)

## Target Golden Baseline

- Selected tab: `我的歌单`
- `miniPlayerVisibility: hidden`
- Android package: `com.zili.android.musicfreeandroid`
- RN package: `fun.upup.musicfree`

## Required Checklist

- [ ] `我的歌单 >= 2`
- [ ] `收藏歌单 >= 2`
- [ ] Fixed row order for `我的歌单`
- [ ] Fixed row order for `收藏歌单`
- [ ] Fixed title and subtitle for every row
- [ ] Fixed cover provenance for every row
- [ ] `miniPlayerVisibility: hidden` verified on both Android and RN
- [ ] RN and Android restore flows both reproduce the same semantic home state
- [ ] RN and Android capture flows both reach the required canonical anchors

## Verification Bundle

All requested regression suites were run fresh on `Medium_Phone_API_36.0(AVD) - 16` and passed:

- `:app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"`
- `:feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.HomeAnchorContractTest"`
- `:feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModelTest"`
- `:feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModelTest"`
- `:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDaoTest`
- `:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.StarredSheetRepositoryTest`
- `:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.db.AppDatabaseMigrationTest`
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.MainActivityStartupTest`
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeFidelityHomeStructureTest`
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.HomeEntryNavigationTest`

## Restore Behavior

### Android

- `scripts/convergence/home-fidelity/restore-android-home-state.sh emulator-5554` initially failed with `run-as: unknown package: com.zili.android.musicfreeandroid`.
- `./gradlew :app:installDebug` was required to reinstall the Android app on the emulator after the instrumentation bundle.
- After reinstall, the Android restore command exited `0`.

### RN

- `scripts/convergence/home-fidelity/restore-rn-home-state.sh emulator-5554` exited `0`.
- The current `fixtures/rn/mmkv/` directory contains only `README.md`; the restore script accepted that placeholder because it checks for any file, not a real MMKV payload.
- Result: RN restore is mechanically reproducible, but it does not restore canonical MMKV-backed starred-sheet state.

## Artifact Matrix

| Fragment | RN capture | Android capture | Notes |
| --- | --- | --- | --- |
| `home-top/nav-bar` | pass | pass | paired screenshots and dumps exist on both platforms |
| `home-top/operations` | pass | pass | paired screenshots and dumps exist on both platforms |
| `home-sheets/sheets-header` | fail | fail | final failure on both: missing resource id pattern `^home\\.sheets\\.item\\.` |
| `home-sheets/sheets-list` | fail | fail | final failure on both: missing resource id pattern `^home\\.sheets\\.item\\.` |
| `home-scroll/home-scroll` | fail | fail | final failure on both: missing resource id pattern `^home\\.sheets\\.item\\.` |
| `drawer-open/drawer` | fail | fail | final failure on both: missing resource ids `['home.drawer.root']` |

## Bootstrap Observation

### Android

- Verified after restore:
  - `screen.home.root`
  - `home.navBar.root`
  - `home.operations.root`
  - `home.sheets.root`
- Current selected tab: `我的歌单`
- Current `我的歌单` count: `0`
- Current `收藏歌单` count: `0`
- Visible empty-state text: `暂无歌单`
- Successful baseline artifacts:
  - `android/raw/home-top-nav-bar.png`
  - `android/cropped/home-top-nav-bar.png`
  - `android/dumps/home-top-nav-bar.xml`
  - `android/raw/home-top-operations.png`
  - `android/cropped/home-top-operations.png`
  - `android/dumps/home-top-operations.xml`
- Exported restore inputs:
  - `fixtures/android/musicfree.db`
  - `fixtures/android/musicfree.db-wal`
  - `fixtures/android/musicfree.db-shm`

### RN

- Restore inputs currently used:
  - placeholder MMKV file: `fixtures/rn/mmkv/README.md`
  - legacy AsyncStorage seed: `fixtures/rn/seed/RKStorage`
  - optional journal: `fixtures/rn/seed/RKStorage-journal`
- Verified after restore:
  - `screen.home.root`, `home.navBar.root`, `home.navBar.search`, `home.operations.root`, and `home.sheets.root` are visible in `uiautomator dump`
  - current `我的歌单` count: `1`
  - current `收藏歌单` count: `0`
  - current visible local row title: `我喜欢`
  - current visible local row subtitle: `0首`
  - debug warning overlay visible near the bottom: `Open debugger to view warnings.`
- Successful baseline artifacts:
  - `rn/raw/home-top-nav-bar.png`
  - `rn/cropped/home-top-nav-bar.png`
  - `rn/dumps/home-top-nav-bar.xml`
  - `rn/raw/home-top-operations.png`
  - `rn/cropped/home-top-operations.png`
  - `rn/dumps/home-top-operations.xml`

## Planned Row Inventory

### 我的歌单

| Order | Id | Title | Subtitle | Cover provenance | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | `TBD` | `TBD` | `TBD` | `TBD` | blocked: golden seed data not prepared |
| 2 | `TBD` | `TBD` | `TBD` | `TBD` | blocked: golden seed data not prepared |

### 收藏歌单

| Order | Id | Title | Subtitle | Cover provenance | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | `TBD` | `TBD` | `TBD` | `TBD` | blocked: golden seed data not prepared |
| 2 | `TBD` | `TBD` | `TBD` | `TBD` | blocked: golden seed data not prepared |

## Controlled Empty

| Fragment | Allowed empty in final golden capture? | Current bootstrap state | Notes |
| --- | --- | --- | --- |
| `home-top/nav-bar` | no | not empty | capture completed on both platforms |
| `home-top/operations` | no | not empty | capture completed on both platforms |
| `home-sheets/sheets-header` | no | partially visible | both platforms fail because no `home.sheets.item.*` anchor is restorable |
| `home-sheets/sheets-list` | no | empty on Android bootstrap | both platforms fail because no `home.sheets.item.*` anchor is restorable |
| `drawer-open/drawer` | no | unresolved on both | both platforms fail because `home.drawer.root` never appears after the scripted menu tap |

## Known Blockers

1. Android fixture data is still bootstrap-only; `我的歌单` and `收藏歌单` restore as `0 / 0`, and no `home.sheets.item.*` anchor is available for sheet-dependent captures.
2. RN MMKV restore input is still a placeholder `README.md`, not an exported MMKV payload; the current guard in `restore-rn-home-state.sh` is too weak to distinguish placeholder content from a real fixture.
3. RN restore reaches a non-golden semantic state (`我的歌单 = 1`, `收藏歌单 = 0`, visible row `我喜欢`, debug warning overlay present), so it cannot be used as the approved baseline.
4. Both drawer captures timed out waiting for `home.drawer.root`, even though top-home anchors were available.
5. The approved golden row inventory and cover provenance are still unset.

## Next Actions

1. Seed Android fixtures with at least 2 local playlists and 2 starred sheets.
2. Export a real RN MMKV payload and replace the placeholder `README.md` in `fixtures/rn/mmkv/`.
3. Tighten `restore-rn-home-state.sh` so placeholder files do not count as a valid MMKV fixture.
4. Recreate both apps' golden data so `home.sheets.item.*` anchors and drawer anchors are present for the remaining fragments.
5. Replace the `TBD` rows above with final approved values and rerun both capture pipelines.
