# Status Bar Immersive

普通 AppBar 页面的状态栏：

- Activity 已 `enableEdgeToEdge()`，状态栏透明。
- `MusicFreeTopAppBar` 内部用 `MusicFreeStatusBarChrome` 把 `colors.appBar` 铺到状态栏后方。
- AppBar 内容高度 `rpx(88)`；状态栏高度由 `WindowInsets.statusBars` 提供。

特殊 chrome 自处理：

- `HomeScreen`：自定义 `HomeNavBar`，状态栏区域保持首页背景。
- `SearchScreen`：搜索栏自处理 `appBar` 色延伸。
- `PlayerScreen`：背景层可绘到状态栏后；内容层 MUST 用 `WindowInsets.statusBars` 显式避让（INC-2026-0011）。
- `LocalScreen`：无 AppBar，必须显式加顶部 status bar spacer。
