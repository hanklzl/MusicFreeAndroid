# home-scroll / home-scroll

- State: `home-scroll`
- Fragment: `home-scroll`
- Conclusion: `not closed`

## Canonical Anchors

- `screen.home.root`
- `home.sheets.root`
- one or more `home.sheets.item.*`

## RN Evidence

- Capture command attempted: `scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 home-scroll home-scroll`
- Result: no paired artifact written for this fragment
- Final failure: missing resource id pattern `^home\\.sheets\\.item\\.` after restore, retries, and swipe attempts

## Android Evidence

- Capture command attempted: `scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 home-scroll home-scroll`
- Result: no paired artifact written for this fragment
- Final failure: missing resource id pattern `^home\\.sheets\\.item\\.` after restore, retries, and swipe attempts

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `screen.home.root` can appear, but no scroll target item anchor is available | same | capture command output during this verification run | blocked |
| Visible text | blocked: no canonical fragment dump was saved | blocked: no canonical fragment dump was saved | capture failed before artifact write | blocked |
| Clickability | blocked: no canonical fragment dump was saved | blocked: no canonical fragment dump was saved | capture failed before artifact write | blocked |
| Selected/open state | scroll target never becomes visible | scroll target never becomes visible | helper timed out waiting for scroll fragment | blocked |
| Size | blocked | blocked | no paired fragment dump | blocked |
| Spacing | blocked | blocked | no paired fragment dump | blocked |
| Radius | blocked | blocked | no paired fragment crop | blocked |
| Font | blocked | blocked | no paired fragment artifact | blocked |
| Color | blocked | blocked | no paired fragment artifact | blocked |
| Icon or static-asset provenance | blocked | blocked | no paired fragment artifact | blocked |

## Notes

- This fragment is downstream of the same missing-row issue as the two `home-sheets` captures. Without populated sheet anchors, the scroll target is undefined on both platforms.
