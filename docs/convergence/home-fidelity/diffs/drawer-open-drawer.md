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
- Manual verification: the drawer content itself becomes visible after tapping `home.navBar.menu`; visible texts include `更多功能`, `基础设置`, `插件管理`, and `权限管理`, and entry anchors such as `home.drawer.permissions` appear in the dump. The missing piece is specifically the canonical root anchor.

## Android Evidence

- Source: `android/raw/drawer-open-drawer.png`
- Crop: `android/cropped/drawer-open-drawer.png`
- Dump: `android/dumps/drawer-open-drawer.xml`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `home.navBar.menu` opens the drawer visually, but `home.drawer.root` is still missing from the RN dump | `home.navBar.menu` opens `home.drawer.root`, then `home.drawer.settings`, `home.drawer.pluginManagement`, `home.drawer.permissions` | manual RN dump plus Android drawer dump | open |
| Visible text | `更多功能`, `基础设置`, `插件管理`, `权限管理` are visible manually, but no canonical root artifact is written | `更多功能`, `基础设置`, `插件管理`, `权限管理` | manual RN dump plus Android dump/crop | open |
| Clickability | menu anchor is clickable and drawer entries become visible manually, but the canonical root anchor never materializes | Android drawer entries are clickable | manual RN dump plus Android dump | open |
| Selected/open state | RN drawer opens visually but not to a capturable canonical-root state | Android drawer opens to the canonical panel state | manual RN dump plus Android dump | open |
| Size | blocked | observed from Android dump bounds only | Android dump | open |
| Spacing | blocked | observed from Android crop only | Android crop | open |
| Radius | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Font | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Color | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Icon or static-asset provenance | blocked | Android drawer icons are present in the captured drawer dump | Android dump and crop | open |

## Notes

- Android drawer capture is fixed after correcting the capture state transition order.
- RN restore is now reproducible; the remaining blocker is only `home.drawer.root` observability.
