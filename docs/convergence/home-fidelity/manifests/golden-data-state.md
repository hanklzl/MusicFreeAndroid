# Home Fidelity Golden Data State

Status: verification runs completed on `2026-03-26` and `2026-03-27`. The full regression bundle passed. Android baseline capture now succeeds for all requested fragments. RN restore is now seed-driven and reproducible for the home-top, home-sheets, and home-scroll fragments; the only remaining automated blocker is `drawer-open`, where the drawer content appears but `home.drawer.root` still does not surface as a canonical resource id.

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

- `scripts/convergence/home-fidelity/restore-rn-home-state.sh emulator-5554` now restores from:
  - checked-in seed file `fixtures/rn/home-fidelity-seed.json`
  - exported MMKV snapshot under `fixtures/rn/mmkv/`
- The RN app imports the seed before `MusicSheet.setup()` and exports the generated MMKV snapshot into an internal app-readable directory, from which the current repo fixture payload was exported.
- Result: RN restore now reaches a reproducible `2 / 2` home state after startup settles.

## Artifact Matrix

| Fragment | RN capture | Android capture | Notes |
| --- | --- | --- | --- |
| `home-top/nav-bar` | pass | pass | paired screenshots and dumps exist on both platforms |
| `home-top/operations` | pass | pass | paired screenshots and dumps exist on both platforms |
| `home-sheets/sheets-header` | pass | pass | both platforms now write canonical header artifacts |
| `home-sheets/sheets-list` | pass | pass | both platforms now expose `home.sheets.item.mine.*` anchors |
| `home-scroll/home-scroll` | pass | pass | both platforms now capture the populated home stack |
| `drawer-open/drawer` | fail | pass | Android drawer root and drawer entries are now captured successfully |

## Bootstrap Observation

### Android

- Verified after restore:
  - `screen.home.root`
  - `home.navBar.root`
  - `home.operations.root`
  - `home.sheets.root`
- Current selected tab: `我的歌单`
- Current `我的歌单` count: `2`
- Current `收藏歌单` count: `2`
- Current visible mine rows:
  - `夜间驾驶` / `2 首歌曲`
  - `晨间通勤` / `1 首歌曲`
- Successful baseline artifacts:
  - `android/raw/home-top-nav-bar.png`
  - `android/cropped/home-top-nav-bar.png`
  - `android/dumps/home-top-nav-bar.xml`
  - `android/raw/home-top-operations.png`
  - `android/cropped/home-top-operations.png`
  - `android/dumps/home-top-operations.xml`
  - `android/raw/home-sheets-sheets-header.png`
  - `android/cropped/home-sheets-sheets-header.png`
  - `android/dumps/home-sheets-sheets-header.xml`
  - `android/raw/home-sheets-sheets-list.png`
  - `android/cropped/home-sheets-sheets-list.png`
  - `android/dumps/home-sheets-sheets-list.xml`
  - `android/raw/home-scroll-home-scroll.png`
  - `android/cropped/home-scroll-home-scroll.png`
  - `android/dumps/home-scroll-home-scroll.xml`
  - `android/raw/drawer-open-drawer.png`
  - `android/cropped/drawer-open-drawer.png`
  - `android/dumps/drawer-open-drawer.xml`
- Exported restore inputs:
  - `fixtures/android/musicfree.db`
  - `fixtures/android/musicfree.db-wal`
  - `fixtures/android/musicfree.db-shm`

### RN

- Restore inputs currently used:
  - seed file: `fixtures/rn/home-fidelity-seed.json`
  - MMKV payload under `fixtures/rn/mmkv/`
  - legacy AsyncStorage seed: `fixtures/rn/seed/RKStorage`
  - optional journal: `fixtures/rn/seed/RKStorage-journal`
- Verified after restore:
  - `screen.home.root`, `home.navBar.root`, `home.navBar.search`, `home.operations.root`, and `home.sheets.root` are visible in `uiautomator dump`
  - current `我的歌单` count: `2`
  - current `收藏歌单` count: `2`
  - current visible local rows:
    - `我喜欢` / `0首`
    - `夜间驾驶` / `2首`
  - debug warning overlay visible near the bottom: `Open debugger to view warnings.`
- Successful baseline artifacts:
  - `rn/raw/home-top-nav-bar.png`
  - `rn/cropped/home-top-nav-bar.png`
  - `rn/dumps/home-top-nav-bar.xml`
  - `rn/raw/home-top-operations.png`
  - `rn/cropped/home-top-operations.png`
  - `rn/dumps/home-top-operations.xml`
  - `rn/raw/home-sheets-sheets-header.png`
  - `rn/cropped/home-sheets-sheets-header.png`
  - `rn/dumps/home-sheets-sheets-header.xml`
  - `rn/raw/home-sheets-sheets-list.png`
  - `rn/cropped/home-sheets-sheets-list.png`
  - `rn/dumps/home-sheets-sheets-list.xml`
  - `rn/raw/home-scroll-home-scroll.png`
  - `rn/cropped/home-scroll-home-scroll.png`
  - `rn/dumps/home-scroll-home-scroll.xml`

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
| `home-sheets/sheets-header` | no | Android closed, RN blocked | Android header/list anchors now restore correctly |
| `home-sheets/sheets-list` | no | Android closed, RN blocked | Android list row anchors now restore correctly |
| `drawer-open/drawer` | no | Android closed, RN blocked | Android drawer root and drawer entries now restore correctly |

## Known Blockers

1. RN `drawer-open` still fails because the drawer content becomes visible but `home.drawer.root` does not surface as a canonical resource id.
2. RN still shows the debug warning overlay `Open debugger to view warnings.` near the bottom.
3. The approved cross-platform golden row inventory and cover provenance are still unset.

## Next Actions

1. Solve the remaining RN drawer-root observability gap so `drawer-open` can be captured automatically.
2. Replace the `TBD` rows above with final approved cross-platform values.
3. Refresh the diff bundle against the aligned `我喜欢 + 夜间驾驶` / `收藏歌单 A + 收藏歌单 B` baseline.
