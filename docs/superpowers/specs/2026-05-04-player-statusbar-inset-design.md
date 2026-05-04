# Player Status Bar Inset Design

> 文档状态：当前规范（播放器状态栏避让专项）
> 适用范围：仅适用于 `PlayerRoute` / `PlayerScreen` 的沉浸式状态栏内容避让修复。
> 直接执行：是（作为 implementation plan 输入；具体代码改动需先生成 implementation plan）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)、[screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)
> 最后校验：2026-05-04

## 背景

`PlayerRoute` / `PlayerScreen` 是已登记的特殊 Chrome 页面。按照 [screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)，播放器页可以把背景绘制到系统栏区域，但必须自行负责状态栏背景和顶部 inset。

当前 Android 实现中，`PlayerScreen` 使用三层结构：

1. 黑色背景层。
2. 封面模糊背景层。
3. 播放器内容层。

前两层使用 `fillMaxSize()` 是正确的沉浸式视觉；问题出在第三层内容 `Column` 也从屏幕顶端开始，导致返回按钮、标题和分享按钮可能进入状态栏区域。

RN 原版 `../MusicFree/src/pages/musicDetail/index.tsx` 采用 `SafeAreaView` 包裹内容，同时 `StatusBar` 背景透明。也就是说，背景可以延伸到状态栏后方，但 `NavBar` 内容从安全区域内开始。

## 目标

1. 保留播放器页沉浸式视觉：黑色背景和模糊封面背景继续绘制到状态栏后方。
2. 让播放器交互内容避开状态栏：返回按钮、标题、平台标签和分享按钮从状态栏下方开始。
3. 保持 `MainActivity` 的责任边界不变，不恢复全局顶部 safe inset 白名单。
4. 不把播放器页改成普通 `MusicFreeScreenScaffold` 页面。
5. 保持改动局部，避免重构播放器布局、播放状态或业务逻辑。

## 非目标

- 不调整播放器页面整体视觉层级、控制区布局或封面尺寸。
- 不新增歌词、队列、横屏或播放控制功能。
- 不修改普通 AppBar harness。
- 不修改 `HomeRoute`、`SearchRoute` 或其他特殊 Chrome 页面。
- 不处理已有 Kotlin / Hilt / Compose 测试 API deprecation warning。

## 决策

采用“背景全屏，内容层避让状态栏”的方案。

`PlayerScreen` 保持根 `Box.fillMaxSize()` 和背景层 `fillMaxSize()` 不变。仅在内容层 `Column` 上增加顶部状态栏 inset，让播放器导航栏整体下移到状态栏下方。

状态栏 inset 应作用在内容层容器，而不是藏进 `PlayerNavBar`。原因是状态栏责任属于 `PlayerScreen` 这个特殊 Chrome screen；如果把 inset 放进 `PlayerNavBar`，未来调整顶部区域时更容易遗漏或叠加 padding。

## Compose 实现口径

实现阶段应优先使用 Compose 的窗口 inset API，例如：

- `WindowInsets.statusBars`
- `windowInsetsPadding(WindowInsets.statusBars)` 或等价的 top-only inset 处理

Modifier 顺序应表达清楚的布局意图：内容层先占满可用空间，再应用状态栏顶部避让。背景层不能应用该 inset。

实现不应通过硬编码 `rpx()`、`dp` 或读取状态栏高度常量来模拟系统 inset。

## 验收

代码验收：

- `PlayerScreen` 背景层仍然全屏绘制。
- `PlayerScreen` 内容层显式处理顶部状态栏 inset。
- `MainActivity` 不新增播放器或普通页面顶部 inset 特判。
- `PlayerNavBar` 不承担隐藏的系统栏 inset 责任。

运行态验收：

- 进入播放器页后，返回按钮、标题、平台标签和分享按钮不与状态栏图标重叠。
- 状态栏区域仍显示播放器黑底或模糊封面背景，不出现普通页面 AppBar 色块。
- 底部播放控制区不因顶部 inset 修复产生明显挤压或错位。
- 非播放器页面的 MiniPlayer 显示逻辑不变，播放器页仍隐藏 MiniPlayer。

测试验收：

- 至少运行 `./gradlew :feature:player-ui:testDebugUnitTest`。
- 若实现改动影响 app shell 或导航容器，再运行 `./gradlew :app:build`。

## 风险与缓解

- 风险：状态栏 inset 与外层 `MainActivity` safe drawing 重叠。
  - 缓解：`MainActivity` 当前只提供横向和底部 safe inset；播放器顶部 inset 只在 `PlayerScreen` 内容层处理。
- 风险：给根容器加 inset 会让背景不再沉浸。
  - 缓解：只给内容层加 inset，背景层保持全屏。
- 风险：给 `PlayerNavBar` 内部加 padding 会隐藏责任边界。
  - 缓解：在 `PlayerScreen` 内容容器中显式处理 inset。
