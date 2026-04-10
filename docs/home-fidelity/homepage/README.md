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
