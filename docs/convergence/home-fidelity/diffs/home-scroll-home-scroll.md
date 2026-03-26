# home-scroll / home-scroll

- State: `home-scroll`
- Fragment: `home-scroll`
- Conclusion: `not closed`

## Canonical Anchors

- `screen.home.root`
- `home.sheets.root`
- one or more `home.sheets.item.*`

## RN Evidence

- Source: `rn/raw/home-scroll-home-scroll.png`
- Crop: `rn/cropped/home-scroll-home-scroll.png`
- Dump: `rn/dumps/home-scroll-home-scroll.xml`

## Android Evidence

- Source: `android/raw/home-scroll-home-scroll.png`
- Crop: `android/cropped/home-scroll-home-scroll.png`
- Dump: `android/dumps/home-scroll-home-scroll.xml`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `screen.home.root`, `home.sheets.root`, and populated mine-row anchors are visible | same | both dumps | open |
| Visible text | populated mine-row text is visible | populated mine-row text is visible | both dumps and crops | open |
| Clickability | visible mine rows are clickable | visible mine rows are clickable | both dumps | open |
| Selected/open state | scroll target is visible after restore without extra retries | same | both dumps | open |
| Size | blocked | observed from Android dump bounds only | Android dump | open |
| Spacing | blocked | observed from Android crop only | Android crop | open |
| Radius | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Font | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Color | blocked | unresolved from current evidence | Android crop not sufficient for exact parity call | open |
| Icon or static-asset provenance | blocked | Android uses placeholder/default cover for seeded rows | fixture DB plus Android crop | open |

## Notes

- The fragment is now capturable on both platforms.
- The remaining difference is content/text fidelity rather than structural observability.
