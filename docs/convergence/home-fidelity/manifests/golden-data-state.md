# Home Fidelity Golden Data State

Status: bootstrap scaffold only. This file documents the target golden-state contract and the currently exported bootstrap fixtures. The checked-in fixtures do not yet satisfy the approved golden-state checklist.

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

- Android build commit: `a35ea2e` (`home-fidelity-implementation`)
- RN reference commit: `1bb58fc` (`home-fidelity-reference-anchors`)

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

## Bootstrap Observation

### Android

- Home root anchors visible in `uiautomator dump`:
  - `screen.home.root`
  - `home.navBar.root`
  - `home.operations.root`
  - `home.sheets.root`
- Current selected tab: `我的歌单`
- Current `我的歌单` count: `0`
- Current `收藏歌单` count: `0`
- Visible empty-state text: `暂无歌单`
- Exported fixture files:
  - `fixtures/android/musicfree.db`
  - `fixtures/android/musicfree.db-wal`
  - `fixtures/android/musicfree.db-shm`

### RN

- Exportable storage sources currently available:
  - external MMKV directory path confirmed at `/storage/emulated/0/Android/data/fun.upup.musicfree/files/mmkv`
  - legacy AsyncStorage seed under `fixtures/rn/seed/RKStorage`
  - optional journal under `fixtures/rn/seed/RKStorage-journal`
- Current export caveat:
  - ADB can list the external MMKV directory but cannot read file contents in the current device session, so `fixtures/rn/mmkv/` is present as a placeholder directory and still needs a successful export path.
- Current runtime caveat:
  - the installed debug app has required Metro during this session
  - the original RN home root anchor `screen.home.root` has been added in source, but the runtime on device must be rebuilt/reloaded before this anchor can be relied on in capture
- Current row inventory is unresolved until the RN app is restored into an approved seeded state

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
| `home-top/nav-bar` | no | not empty | anchor capture is possible on Android bootstrap |
| `home-top/operations` | no | not empty | anchor capture is possible on Android bootstrap |
| `home-sheets/sheets-header` | no | partially visible | header exists but list contract is not satisfied |
| `home-sheets/sheets-list` | no | empty on Android bootstrap | final golden baseline must have at least 2 rows per tab |
| `drawer-open/drawer` | no | unresolved on RN | depends on RN runtime reaching home with anchors |

## Known Blockers

1. Android fixture data is still bootstrap-only; `playlists` and `starred_sheets` are empty.
2. RN external MMKV file export is still blocked by device permissions in the current session; only the legacy `RKStorage` seed has been exported successfully.
3. RN capture contract is still not fully verified on device. Source now includes `screen.home.root`, but the updated runtime still needs to be installed or reloaded before formal capture.
4. The approved golden row inventory and cover provenance are still unset.

## Next Actions

1. Seed Android fixtures with at least 2 local playlists and 2 starred sheets.
2. Seed or regenerate RN MMKV fixtures so the same semantic rows exist on the RN home screen.
3. Reinstall or reload the RN app, then verify `screen.home.root` plus the home anchors in `uiautomator dump`.
4. Replace the `TBD` rows above with final approved values and rerun both capture pipelines.
