# Status Bar Immersive

系统栏图标明暗：

- 状态栏 / 导航栏图标明暗由 `MusicFreeTheme` 依据 App 主题 `isDark` 统一驱动（`isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars`），随运行时切主题即时生效。
- MUST NOT 依赖 `enableEdgeToEdge()` 默认 `auto`（跟随设备夜间模式，与 App 主题解耦时深色主题图标看不清）；MUST NOT 在 Screen / `MainActivity` 各自手写 `isAppearanceLight*`。
- 背景与主题无关的全屏页（如恒黑的 `PlayerScreen`）若需例外，在「特殊 chrome 页面」登记并自处理。

普通 AppBar 页面的状态栏：

- Activity 已 `enableEdgeToEdge()`，状态栏透明。
- `MusicFreeTopAppBar` 内部用 `MusicFreeStatusBarChrome` 把 `colors.appBar` 铺到状态栏后方。
- AppBar 内容高度 `rpx(88)`；状态栏高度由 `WindowInsets.statusBars` 提供。

特殊 chrome 自处理：

- `HomeScreen`：自定义 `HomeNavBar`，状态栏区域保持首页背景。
- `SearchScreen`：搜索栏自处理 `appBar` 色延伸。
- `PlayerScreen`：背景层可绘到状态栏后；内容层 MUST 用 `WindowInsets.statusBars` 显式避让（INC-2026-0011）。
- `LocalScreen`：无 AppBar，必须显式加顶部 status bar spacer。
