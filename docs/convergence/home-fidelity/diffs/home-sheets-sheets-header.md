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

- Capture command attempted: `scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-sheets sheets-header`
- Result: no paired artifact written for this fragment
- Final failure: missing resource id pattern `^home\\.sheets\\.item\\.`

## Android Evidence

- Capture command attempted: `scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-sheets sheets-header`
- Result: no paired artifact written for this fragment
- Final failure: missing resource id pattern `^home\\.sheets\\.item\\.`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | header anchors partially reachable, but first `home.sheets.item.*` never appears | same | capture command output during this verification run | blocked |
| Visible text | blocked: no canonical fragment dump was saved | blocked: no canonical fragment dump was saved | capture failed before artifact write | blocked |
| Clickability | blocked: no canonical fragment dump was saved | blocked: no canonical fragment dump was saved | capture failed before artifact write | blocked |
| Selected/open state | `我的歌单` restore state exists, but canonical header fragment is not capturable | same | restore observations plus capture failure | blocked |
| Size | blocked | blocked | no paired fragment dump | blocked |
| Spacing | blocked | blocked | no paired fragment dump | blocked |
| Radius | blocked | blocked | no paired fragment crop | blocked |
| Font | blocked | blocked | no paired fragment artifact | blocked |
| Color | blocked | blocked | no paired fragment artifact | blocked |
| Icon or static-asset provenance | blocked | blocked | no paired fragment artifact | blocked |

## Notes

- This fragment is blocked by data state, not by a test regression. Both platforms fail only after restore, with the same missing-row anchor pattern.
