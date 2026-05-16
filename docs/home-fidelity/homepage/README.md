# Homepage Fidelity Evidence

> 文档状态：当前规范（首页专项）
> 适用范围：首页取证资产的目录、命名与采集约束。
> 直接执行：是（仅首页专项）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 备注：该文档描述当前可用的首页证据采集约定。
> 最后校验：2026-04-11

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
- Foreground package must be `com.hank.musicfree` before each capture.
- Default anchor checks:
- `home-top` must include `screen.home.root`.
- `home-sheets` must include `home.sheets.root`.
- `drawer-open` must include `home.drawer.root`.
- If a capture script run fails any validation, discard and re-capture the same state.
