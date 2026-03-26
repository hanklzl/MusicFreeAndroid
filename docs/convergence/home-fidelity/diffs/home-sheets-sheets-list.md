# home-sheets / sheets-list

- State: `home-sheets`
- Fragment: `sheets-list`
- Conclusion: `not closed`

## Canonical Anchors

- `home.sheets.root`
- one or more `home.sheets.item.*`

## RN Evidence

- Source: `rn/raw/home-sheets-sheets-list.png`
- Crop: `rn/cropped/home-sheets-sheets-list.png`
- Dump: `rn/dumps/home-sheets-sheets-list.xml`

## Android Evidence

- Source: `android/raw/home-sheets-sheets-list.png`
- Crop: `android/cropped/home-sheets-sheets-list.png`
- Dump: `android/dumps/home-sheets-sheets-list.xml`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `home.sheets.item.mine.favorite` then `home.sheets.item.mine.playlist-night` | `home.sheets.item.mine.favorite` then `home.sheets.item.mine.playlist-night` | both dumps | open |
| Visible text | `我喜欢`, `0首`, `夜间驾驶`, `2首` | `我喜欢`, `0 首歌曲`, `夜间驾驶`, `2 首歌曲` | both dumps and crops | open |
| Clickability | both mine rows are clickable | both mine rows are clickable | both dumps | open |
| Selected/open state | list fragment materializes in `我的歌单` tab | list fragment materializes in `我的歌单` tab | both dumps | open |
| Size | blocked | observed from Android dump bounds only | Android dump | open |
| Spacing | blocked | observed from Android crop only | Android crop | open |
| Radius | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Font | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Color | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Icon or static-asset provenance | blocked | Android uses placeholder/default cover for seeded rows | fixture DB plus Android crop | open |

## Notes

- Both platforms now restore a stable two-row mine list.
- The remaining difference is subtitle formatting rather than structural observability.
