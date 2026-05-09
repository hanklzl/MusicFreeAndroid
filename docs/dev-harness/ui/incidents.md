# UI / Compose Screen Incidents

> 文档状态：当前规范（Dev Harness — UI Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [ui/rules.md](./rules.md)
> 最后校验：2026-05-10

## INC-2026-0015 — Drawer / 浮层未让 status bar

- id: INC-2026-0015
- area: ui
- date: 2026-05-10
- status: active
- rule_ref: docs/dev-harness/ui/rules.md#rule-overlay-respects-statusbar
- guard:
    type: manual
- fix_ref: 3a5c474, 48cf1fb

### 根因

抽屉 / Modal / Popup 浮层（`ModalDrawerSheet`、`ModalBottomSheet`、自定义 `Popup`）启用 edge-to-edge 后内容默认延伸到状态栏后方但不通过 `WindowInsets.statusBars` 避让；标题、操作按钮、列表第一项被状态栏遮挡。

### 复发条件

新增 `ModalDrawerSheet` / `ModalBottomSheet` / `Popup` composable 但未通过 `WindowInsets.statusBars` / `windowInsetsPadding(WindowInsets.statusBars)` 避让顶部。

### 教训

浮层顶部 chrome 与普通 Screen 同等责任：`Modifier.windowInsetsPadding(WindowInsets.statusBars)` 显式避让。MainActivity 的 inset 责任不延伸到浮层（与 INC-2026-0008 的边界配合）。

### 备注

guard 当前 manual：未来 contract-test 可静态扫 `ModalDrawerSheet` / `ModalBottomSheet` / `PopupProperties` 出现的 composable 是否在同一 scope 含 `WindowInsets.statusBars` 引用，作为 heuristic guard。

## INC-2026-0008 — MainActivity 隐式补顶部 inset 白名单

- id: INC-2026-0008
- area: ui
- date: 2026-05-03
- status: active
- rule_ref: docs/dev-harness/ui/rules.md#rule-mainactivity-no-implicit-top-inset
- guard:
    type: grep + manual
- signature: |
    grep -nE 'WindowInsetsSides\.Top' app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt
- fix_ref: docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md#mainactivity-设计

### 根因

旧 `MainActivity` 为多数页面统一补顶部 safe inset，再在白名单里排除 search / player 等沉浸式页面。这种隐式约定让新加页面默认拿到错的顶部 padding，且会与公共 harness 的状态栏占位叠加。

### 复发条件

`MainActivity.kt` 的 Scaffold 含 `WindowInsetsSides.Top`（直接或通过 union），或顶部 inset 在 Activity 层补偿。

### 教训

顶部 chrome 是 Screen / 公共 harness（`MusicFreeScreenScaffold` / `MusicFreeStatusBarChrome`）的责任；MainActivity 只承担 App 级 Scaffold + 横向/底部 safe inset。

### 备注

manual 部分用于审查 `MainActivity` 替代写法（例如自定义 modifier 注入顶部 padding）；grep 仅捕捉最直接形态。

## INC-2026-0007 — 散落的 TopAppBarDefaults.topAppBarColors 手写

- id: INC-2026-0007
- area: ui
- date: 2026-05-03
- status: active
- rule_ref: docs/dev-harness/ui/rules.md#rule-no-raw-material3-topappbar
- guard:
    type: grep
- signature: |
    grep -rEn 'TopAppBarDefaults\.topAppBarColors\(' \
      --include='*.kt' \
      --exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle . \
      | grep -v 'core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicFreeScreenChrome.kt'
- fix_ref: docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md#公共-compose-api-设计

### 根因

多个 Screen 直接手写 `TopAppBar(colors = TopAppBarDefaults.topAppBarColors(...))`；与公共 `MusicFreeTopAppBar` 并存导致 AI 工具从混合示例学习，复制错误模式。

### 复发条件

`*.kt` 中除 `core/ui/MusicFreeScreenChrome.kt` 外出现 `TopAppBarDefaults.topAppBarColors(`。

### 教训

普通 AppBar 走 `MusicFreeScreenScaffold` 或 `MusicFreeTopAppBar`；自定义 chrome 走 `MusicFreeStatusBarChrome` 等价实现。

## INC-2026-0006 — 顶部导航动画 250ms 偏离 RN 100ms

- id: INC-2026-0006
- area: ui
- date: 2026-05-03
- status: active
- rule_ref: docs/dev-harness/ui/rules.md#rule-nav-animation-100ms
- guard:
    type: contract-test
    target: app/src/test/java/com/zili/android/musicfreeandroid/harness/contracts/UiNavAnimationDurationContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md#screen-切换动画设计

### 根因

旧 `AppNavHost` 使用 `tween(250)` 全局动画，与 RN 原版 `animationDuration: 100` 不一致。原 plan 文件中残留 250ms 写法，新人/AI 复制旧示例。

### 复发条件

`MusicFreeScreenTransitionDurationMillis` 常量值偏离 100；`NavHost` 中手写 `tween(<其他值>)`。

### 教训

集中入口 `MusicFreeNavTransitions.kt` 是唯一 transition builder；常量值锁定 100，由 contract test 守门。
