# home-sheets / sheets-list

- State: `home-sheets`
- Fragment: `sheets-list`
- Conclusion: `not closed`

## Canonical Anchors

- `home.sheets.root`
- one or more `home.sheets.item.*`

## RN Evidence

- Capture command attempted: `scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-sheets sheets-list`
- Result: no paired artifact written for this fragment
- Final failure: missing resource id pattern `^home\\.sheets\\.item\\.`

## Android Evidence

- Capture command attempted: `scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-sheets sheets-list`
- Result: no paired artifact written for this fragment
- Final failure: missing resource id pattern `^home\\.sheets\\.item\\.`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `home.sheets.root` is restorable, but no list item anchor appears | same | capture command output during this verification run | blocked |
| Visible text | blocked: no canonical fragment dump was saved | blocked: no canonical fragment dump was saved | capture failed before artifact write | blocked |
| Clickability | blocked: no canonical fragment dump was saved | blocked: no canonical fragment dump was saved | capture failed before artifact write | blocked |
| Selected/open state | closed; canonical list fragment never materializes | closed; canonical list fragment never materializes | capture helper timed out waiting for list anchors | blocked |
| Size | blocked | blocked | no paired fragment dump | blocked |
| Spacing | blocked | blocked | no paired fragment dump | blocked |
| Radius | blocked | blocked | no paired fragment crop | blocked |
| Font | blocked | blocked | no paired fragment artifact | blocked |
| Color | blocked | blocked | no paired fragment artifact | blocked |
| Icon or static-asset provenance | blocked | blocked | no paired fragment artifact | blocked |

## Notes

- Android bootstrap data restores as `暂无歌单`.
- RN restore lands on a non-golden single-row state, but the canonical list capture still cannot be written because the expected `home.sheets.item.*` contract is not satisfied.
