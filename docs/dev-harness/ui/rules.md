# UI / Compose Screen Rules

> 文档状态：当前规范（Dev Harness — UI）
> 适用范围：Screen 切换动画、普通 AppBar 页面、沉浸式状态栏、Compose UI 设计原则
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[UI Harness Screen Chrome 设计](../../superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md)
> 最后校验：2026-05-09

## 设计原则

- 组件层使用 Material3（`androidx.compose.material3.*`）；信息架构与布局对齐 RN 原版（`../MusicFree/src/pages/`）。组件实现可与 RN 不同，但用户路径、菜单结构与默认状态必须一致。
- 首页 UI fidelity 取证规范见 [docs/home-fidelity/](../../home-fidelity/)，非本 ui rules 范围；新增页面级 fidelity 规范前先讨论是否折成 `docs/dev-harness/ui/fidelity/` 子域。

## 强制入口

新增或修改 Compose Screen 前，必须先读取本文件。

本文件是 Screen 切换动画、普通 AppBar、状态栏沉浸式处理的唯一当前规则来源。`docs/superpowers/plans/*.md` 中出现的旧动画时长、旧 AppBar 写法、旧状态栏做法均视为历史执行快照，不可作为当前规范。

## Screen 切换动画 {#rule-nav-animation-rn-android}

implemented_by: INC-2026-0006

- 普通页面 MUST 使用全局默认 `slide_from_right` 动画。
- 普通页面前进动画 MUST 为新页面从右向左进入、旧页面向左退出。
- 普通页面返回动画 MUST 为上一页从左侧回入、当前页向右退出。
- 普通页面默认动画时长 MUST 为 `400ms`，按 RN Android 实际生效链路对齐 `slide_from_right` 的 `@android:integer/config_mediumAnimTime` 口径。
- `AppNavHost` MUST 引用集中 transition helper（`MusicFreeNavTransitions.kt`），MUST NOT 在 `NavHost` 参数里手写 `tween(250)` 或其他局部时长。
- Screen 内部 MUST NOT 用局部 `AnimatedContent`、`AnimatedVisibility` 或自定义 offset 动画伪装页面切换。
- 特殊页面若需要不同页面切换动画，MUST 在 route/destination 注册处显式覆盖，并在本文件“特殊 Chrome 页面”中登记原因。

## 普通 AppBar 页面 {#rule-no-raw-material3-topappbar}

implemented_by: INC-2026-0007

普通 AppBar 页面 MUST 使用 `com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold` 或 `MusicFreeTopAppBar`。

普通 AppBar 页面 MUST NOT 直接手写以下模式：

```kotlin
TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MusicFreeTheme.colors.appBar,
    ),
)
```

普通 AppBar 页面状态栏规则：

- Activity 级别保持 edge-to-edge。
- 系统状态栏保持透明。
- `MusicFreeTheme.colors.appBar` MUST 铺到状态栏后方。
- AppBar 内容 MUST 从状态栏下方开始。
- AppBar 内容高度 MUST 对齐 RN `rpx(88)`。
- 标题文字 MUST 使用 `FontSizes.appBar` 和 `MusicFreeTheme.colors.appBarText`，除非页面设计文档声明了特殊标题内容。

## 特殊 Chrome 页面

以下页面不使用普通 AppBar，但 MUST 自行负责状态栏背景和顶部 inset：

- `HomeRoute` / `HomeScreen`：首页使用自定义 `HomeNavBar`，状态栏区域保持首页背景，不依赖 `MainActivity` 顶部 safe inset。
- `SearchRoute` / `SearchScreen`：搜索页使用自定义搜索栏，`appBar` 色延伸到状态栏后方。
- `PlayerRoute` / `PlayerScreen`：播放器是全屏沉浸式页面，顶部内容可以绘制到系统栏区域。

新增特殊 Chrome 页面时，必须在本节登记 route、Screen、原因和状态栏策略。

## MainActivity 责任边界 {#rule-mainactivity-no-implicit-top-inset}

implemented_by: INC-2026-0008

`MainActivity` 负责 App 级 `Scaffold`、MiniPlayer、横向 safe inset、底部 safe inset。

`MainActivity` MUST NOT 维护“普通页面统一补顶部 safe inset，某些页面排除”的隐式白名单。顶部 chrome 是 Screen 或公共 UI harness 的责任。

## 新增 Screen 默认做法

新增普通页面时，默认结构为：

```kotlin
MusicFreeScreenScaffold(
    title = "页面标题",
    onBack = onBack,
    modifier = modifier,
) { innerPadding ->
    // Page content starts here.
}
```

新增自定义顶部页面时，必须先说明为什么不能使用普通 AppBar，并使用 `MusicFreeStatusBarChrome` 或等价实现显式处理顶部状态栏区域。

## 浮层 / Drawer / Popup 状态栏避让 {#rule-overlay-respects-statusbar}

implemented_by: INC-2026-0015

- `ModalDrawerSheet`、`ModalBottomSheet`、自定义 `Popup` 等浮层 composable MUST 通过 `Modifier.windowInsetsPadding(WindowInsets.statusBars)`（或等价的 `Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))`）显式避让顶部状态栏。
- MUST NOT 让浮层标题、按钮、列表第一项被状态栏遮挡。
- 适用范围：`feature/*/src/main/.../*.kt` 中所有顶层浮层 composable。
- 与 INC-2026-0008（MainActivity 不补 implicit top inset）配合：MainActivity 不为浮层补 inset，浮层 composable 自己负责。

## 待开域 backlog

- DB schema during dev：dev 阶段直接改 entity 类、不写 `Migration` 对象。这是 data 域 rule，等 `docs/dev-harness/data/rules.md` 引入时正式落入。来源：项目记忆 promotion。
