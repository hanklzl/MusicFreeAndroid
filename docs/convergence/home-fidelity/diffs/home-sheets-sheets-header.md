# home-sheets / sheets-header

- State: `home-sheets`
- Fragment: `sheets-header`
- Conclusion: `not closed`

## Canonical Anchors

- `home.sheets.root`
- `home.sheets.tab.mine`
- `home.sheets.tab.starred`
- `home.sheets.action.create`
- `home.sheets.action.import`
- first `home.sheets.item.*`

## RN Evidence

- Source: `rn/raw/home-sheets-sheets-header.png`
- Crop: `rn/cropped/home-sheets-sheets-header.png`
- Dump: `rn/dumps/home-sheets-sheets-header.xml`

## Android Evidence

- Source: `android/raw/home-sheets-sheets-header.png`
- Crop: `android/cropped/home-sheets-sheets-header.png`
- Dump: `android/dumps/home-sheets-sheets-header.xml`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `home.sheets.root`, tabs, actions, and first `home.sheets.item.mine.*` anchor are present | same | both dumps | open |
| Visible text | `我的歌单`, `收藏歌单`, `我喜欢`, `夜间驾驶` | `我的歌单`, `收藏歌单`, `我喜欢`, `夜间驾驶` | both dumps and crops | open |
| Clickability | tabs/actions are clickable; first mine row is clickable | same | both dumps | open |
| Selected/open state | `我的歌单` selected with counts `(2)` / `(2)` | `我的歌单` selected with counts `(2)` / `(2)` | both dumps | open |
| Size | blocked | observed from Android dump bounds only | `android` dump | open |
| Spacing | blocked | observed from Android crop only | `android` crop | open |
| Radius | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Font | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Color | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Icon or static-asset provenance | blocked | Android uses placeholder/default cover for seeded rows | fixture DB plus Android crop | open |

## Notes

- The fragment is now capturable on both platforms.
- Remaining gap is visual/text parity, especially subtitle formatting (`0首` vs `0 首歌曲`, `2首` vs `2 首歌曲`) and the RN debug warning overlay outside the fragment body.
