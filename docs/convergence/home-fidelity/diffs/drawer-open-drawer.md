# drawer-open / drawer

- State: `drawer-open`
- Fragment: `drawer`
- Conclusion: `not closed`

## Canonical Anchors

- `home.navBar.menu`
- `home.drawer.root`

## RN Evidence

- Capture command attempted: `scripts/convergence/home-fidelity/capture-rn-home.sh emulator-5554 drawer-open drawer`
- Result: no paired artifact written for this fragment
- Final failure: missing resource ids `['home.drawer.root']`

## Android Evidence

- Capture command attempted: `scripts/convergence/home-fidelity/capture-android-home.sh emulator-5554 drawer-open drawer`
- Result: no paired artifact written for this fragment
- Final failure: missing resource ids `['home.drawer.root']`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `home.navBar.menu` exists, but `home.drawer.root` never appears after the scripted tap | same | successful top dumps plus drawer capture command output | blocked |
| Visible text | blocked: no canonical drawer dump was saved | blocked: no canonical drawer dump was saved | capture failed before artifact write | blocked |
| Clickability | menu anchor exists but drawer root never materializes | menu anchor exists and is clickable in top dump, but drawer root never materializes | top dumps plus drawer capture failure | blocked |
| Selected/open state | drawer never opens to canonical state | drawer never opens to canonical state | helper timed out waiting for `home.drawer.root` | blocked |
| Size | blocked | blocked | no paired drawer dump | blocked |
| Spacing | blocked | blocked | no paired drawer dump | blocked |
| Radius | blocked | blocked | no paired drawer crop | blocked |
| Font | blocked | blocked | no paired drawer artifact | blocked |
| Color | blocked | blocked | no paired drawer artifact | blocked |
| Icon or static-asset provenance | blocked | blocked | no paired drawer artifact | blocked |

## Notes

- The missing drawer anchor is independent of the missing sheet-row anchors. Both platforms reached top-home before the drawer helper timed out.
