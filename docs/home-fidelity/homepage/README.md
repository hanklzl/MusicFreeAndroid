# Homepage Fidelity Evidence
## Directory Layout
- `android/`
- `rn/`
- `diff/`
- `manifest/`

## Naming Rules
- screenshot: `<state>-<fragment>.png`
- dump: `<state>-<fragment>.xml`
- recording: `<state>-<fragment>.mp4`
- diff note: `<state>-<fragment>.md`

## Required States
- `home-top`
- `home-sheets`
- `drawer-open`

## Capture Order
1. Restore manifest state
2. Open target state
3. Capture screenshot
4. Capture `uiautomator dump`
5. Capture recording if required

## Validation Rules
- Foreground package must be `com.zili.android.musicfreeandroid` before each capture.
- Default anchor checks:
- `home-top` must include `screen.home.root`.
- `home-sheets` must include `home.sheets.root`.
- `drawer-open` must include `home.drawer.root`.
- If a capture script run fails any validation, discard and re-capture the same state.
