# Home Fidelity Diff Bundle

Store all home-fidelity comparison artifacts under `docs/convergence/home-fidelity/`.

## Directory Layout

- `android/raw/`: raw Android screenshots
- `android/cropped/`: cropped Android screenshots
- `android/dumps/`: Android `uiautomator dump` XML files
- `rn/raw/`: raw RN screenshots
- `rn/cropped/`: cropped RN screenshots
- `rn/dumps/`: RN `uiautomator dump` XML files
- `fixtures/`: restore inputs for both apps
- `manifests/`: anchor maps and golden-state manifests
- `diffs/`: per-fragment comparison markdown files

## Naming

- Screenshot: `<state>-<fragment>.png`
- Dump: `<state>-<fragment>.xml`
- Diff file: `<state>-<fragment>.md`

Examples:

- `android/raw/home-top-nav-bar.png`
- `rn/dumps/drawer-open-drawer.xml`
- `diffs/home-sheets-sheets-list.md`

## Required Fields In Every Diff File

- Fragment name
- Target state name
- Canonical anchor list and order
- RN evidence source
- Android evidence source
- Visible text
- Clickability
- Selected/open/visible state
- Size evidence
- Spacing evidence
- Radius evidence
- Font evidence
- Color token or hex evidence
- Icon or static-asset provenance
- Current conclusion: `closed` or `not closed`

## Evidence Priority

1. Source code and resource values
2. Canonical anchor map and `uiautomator dump`
3. Cropped screenshots
4. Manual review on the locked golden device

## Suggested Diff Template

```md
# <state> / <fragment>

- State: `<state>`
- Fragment: `<fragment>`
- Conclusion: `closed | not closed`

## Canonical Anchors

- `<anchor-1>`
- `<anchor-2>`

## RN Evidence

- Source: `rn/raw/<state>-<fragment>.png`
- Dump: `rn/dumps/<state>-<fragment>.xml`

## Android Evidence

- Source: `android/raw/<state>-<fragment>.png`
- Dump: `android/dumps/<state>-<fragment>.xml`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Text |  |  |  |  |
| Clickability |  |  |  |  |
| Selected/open state |  |  |  |  |
| Size |  |  |  |  |
| Spacing |  |  |  |  |
| Radius |  |  |  |  |
| Font |  |  |  |  |
| Color |  |  |  |  |
| Asset provenance |  |  |  |  |
```
