# home-top / operations

- State: `home-top`
- Fragment: `operations`
- Conclusion: `not closed`

## Canonical Anchors

- `screen.home.root`
- `home.operations.root`
- `home.operations.recommendSheets`
- `home.operations.topList`
- `home.operations.history`
- `home.operations.localMusic`

## RN Evidence

- Source: `rn/raw/home-top-operations.png`
- Crop: `rn/cropped/home-top-operations.png`
- Dump: `rn/dumps/home-top-operations.xml`

## Android Evidence

- Source: `android/raw/home-top-operations.png`
- Crop: `android/cropped/home-top-operations.png`
- Dump: `android/dumps/home-top-operations.xml`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `home.operations.root -> recommendSheets -> topList -> history -> localMusic` | same | paired dumps | match |
| Visible text | `推荐歌单`, `榜单`, `播放历史`, `本地音乐` | same | paired dumps | match |
| Clickability | all four operation anchors report `clickable=false` in `uiautomator dump` | all four operation anchors report `clickable=true` | paired dumps | drift |
| Selected/open state | closed; no pressed/open state captured | closed; no pressed/open state captured | paired dumps and crops | match |
| Size | `[35,236][261,466]`, `[296,236][523,466]`, `[557,236][784,466]`, `[819,236][1045,466]` | `[35,236][262,466]`, `[297,236][523,466]`, `[558,236][784,466]`, `[819,236][1045,466]` | paired dumps | near match |
| Spacing | operation tiles are evenly spaced across the row; left/top alignment matches Android within `0-1px` in dump bounds | same | paired dumps | match |
| Radius | not measured in this run | rounded operation cards visible in Android crop | crops are the only available radius evidence for this fragment | blocked |
| Font | label text visible, but family/weight not derivable from dump | same | dumps show text only; no source-token review was done in this task | blocked |
| Color | not measured in this run | light neutral cards with dark icons/text visible in Android crop | crops are the only available color evidence for this fragment | blocked |
| Icon or static-asset provenance | operation cells are RN `ViewGroup` containers with nested SVG/image content | operation cells are Compose views under `home.operations.*` anchors | paired dumps | drift |

## Notes

- The geometry is close enough to support a top-row baseline discussion, but the clickability mismatch keeps the fragment open.
- The RN dump still includes lower-screen contamination from non-golden data (`我喜欢`, `0首`) and the debug warning overlay.
