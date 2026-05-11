# UI Harness 与 Screen Chrome 规范设计

> 文档状态：当前规范（UI Harness 设计）
> 适用范围：适用于 Screen 切换动画、普通 AppBar 页面、沉浸式状态栏和后续 AI Coding 规则入口设计。
> 直接执行：是（作为实现计划输入；具体代码改动需先生成 implementation plan）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 最后校验：2026-05-11

## 背景

当前仓库的 Screen 切换动画和 AppBar/状态栏处理已经出现分叉：

- `AppNavHost` 中有全局 slide 动画，但当前时长为 `250ms`。
- 原版 RN 在 `../MusicFree/src/entry/index.tsx` 中使用 `animation: "slide_from_right"`；Android 实际时长按 `react-native-screens` 的系统 medium animation 资源生效，而不是 JS `animationDuration` 字面值。
- `MainActivity` 当前对多数页面统一补顶部 safe inset，同时排除搜索页和播放器页。
- 多个普通 Screen 直接手写 Material3 `TopAppBar` 和 `TopAppBarDefaults.topAppBarColors(...)`。
- `SearchScreen` 自行用 `WindowInsets.statusBars` 处理沉浸式顶部区域。
- `PlayerScreen` 和 `HomeScreen` 使用自定义顶部区域，但缺少统一文档化的例外规则。

这些分叉会让后续 AI Coding 从现有代码中学习到多套做法。仅写说明文档不能稳定约束后续实现；规则必须同时落在文档入口和公共 Compose API 上。

## 目标

1. 建立跨工具可读的 UI Harness 规则入口，优先覆盖 Codex 和 Claude 等读取仓库文档的 AI 工具。
2. 将 Screen 切换动画规范化为“全局默认 + 显式例外”。
3. 将普通 AppBar 页面统一到一个 Compose 公共入口。
4. 将普通 AppBar 的状态栏沉浸式处理对齐 RN：状态栏透明，`appBar` 色块延伸到状态栏后方。
5. 迁移存量不一致的普通 AppBar 页面，减少相反示例。
6. 保持本轮范围聚焦，不做完整导航 DSL 重构，不强制新增静态检查或合同测试。

## 非目标

- 不在本轮新增 `CLAUDE.md`、Cursor、Windsurf、Gemini 等工具专用入口。
- 不把所有 Navigation Compose destination 注册重写成自定义 DSL。
- 不改播放器、插件、数据层或业务状态管理。
- 不要求所有自定义顶部区域都使用普通 AppBar。
- 不在本轮强制加入静态检查、lint rule 或合同测试；这些可作为后续强化项。

## 推荐方案

采用“公共 Harness 入口 + 显式例外”的方案。

规则正文放在通用仓库文档中，`AGENTS.md` 只保留短强制规则和相对链接。代码侧新增小而明确的公共 Compose API，让普通页面默认走统一路径；特殊页面必须在文档和 route/destination 层面显式声明例外。

不采用仅文档方案，因为存量代码仍会提供相互冲突的示例。不采用强 DSL 方案，因为它会扩大改动面，并可能与现有 type-safe Navigation Compose 扩展函数发生不必要摩擦。

## 规则入口设计

### AGENTS 入口

`AGENTS.md` 新增“UI Harness Rules”段落，内容保持短而强：

- 新增或修改 Screen 前必须读取 `docs/ui-harness/screen-chrome-rules.md`。
- Screen 切换动画、普通 AppBar、状态栏沉浸式处理必须走公共 harness 入口。
- 特殊 chrome 页面必须显式声明例外，不能依赖 `MainActivity` 隐式补偿。
- `docs/superpowers/plans/*.md` 中旧动画或 AppBar 说法不作为当前规则来源。

`AGENTS.md` 不复制完整正文，避免与规范文件产生双源漂移。

### DOCS_STATUS 入口

`docs/DOCS_STATUS.md` 登记新的 rules 文档为 `当前规范`。本设计 spec 也登记为当前 UI Harness 设计输入。

### 规则正文

新增 `docs/ui-harness/screen-chrome-rules.md`：

- 写明文档状态、适用范围、当前入口和最后校验日期。
- 用 MUST / MUST NOT / SHOULD 表达强规则。
- 明确普通页面和特殊 chrome 页面的边界。
- 明确新增 Screen 的默认做法和例外申请方式。
- 使用相对路径引用代码和 RN 参考。

## Screen 切换动画设计

导航动画采用分层规则：

- 普通页面默认对齐 RN Android 实际行为：`slide_from_right`，时长 `400ms`。
- 前进时，新页面从右向左进入，旧页面向左退出。
- 返回时，上一页从左侧回入，当前页向右退出。
- `HomeRoute` 作为 start destination 不额外做首次进入动画。
- `PlayerRoute`、`SearchRoute` 和后续明确拥有自定义沉浸式 chrome 的页面可以显式覆盖动画。
- 禁止在 Screen 内部用局部动画伪装页面切换。

实现上将动画时长和 transition builder 抽到集中入口。`AppNavHost` 保留 destination 装配职责，只引用统一函数。当前 `250ms` 全局动画不再作为规范。

## AppBar 与状态栏设计

普通 AppBar 页面采用 RN 对齐口径：

- Activity 级别启用 edge-to-edge。
- 系统状态栏透明。
- 普通 AppBar 的容器色使用 `MusicFreeTheme.colors.appBar`。
- `appBar` 色块铺到状态栏后方。
- AppBar 内容从状态栏下方开始，内容高度对齐 RN `rpx(88)`。
- 完整顶部 chrome 高度为状态栏高度 + `rpx(88)`。
- 文案颜色使用 `MusicFreeTheme.colors.appBarText`。
- 标题字号使用 `FontSizes.appBar`。

`MainActivity` 不再为普通页面统一补顶部 safe inset。普通页面的顶部 inset 由公共 harness 容器负责。

## 公共 Compose API 设计

公共 UI chrome API 放在 `:core`，供各 feature 模块复用。

建议入口包括：

- `MusicFreeScreenScaffold`
  - 负责页面背景、顶部 chrome 插槽、内容 padding、横向/底部 safe inset 配合。
  - 面向普通 AppBar 页面提供默认结构。
- `MusicFreeTopAppBar`
  - 负责状态栏背景延伸、AppBar 内容高度、标题、返回按钮、actions。
  - 内部处理 `WindowInsets.statusBars`，调用方不再手写状态栏 spacer。
- `MusicFreeStatusBarChrome` 或等价私有实现
  - 负责透明状态栏后的背景色占位。
  - 可作为自定义顶部页面的复用构件，但普通页面优先使用 `MusicFreeTopAppBar`。

命名可在 implementation plan 中按现有包结构细化，但 API 必须保持“小而明确”，不要引入复杂 DSL。

## 特殊 Chrome 例外

以下页面不强制使用普通 AppBar，但必须自行负责状态栏背景和顶部 inset：

- `HomeRoute` / `HomeScreen`
  - 首页顶部为自定义 HomeNavBar，状态栏策略需要和首页 fidelity 规范一致。
- `SearchRoute` / `SearchScreen`
  - 搜索页顶部为自定义搜索栏，`appBar` 色延伸到状态栏后方。
- `PlayerRoute` / `PlayerScreen`
  - 播放器为全屏沉浸式页面，顶部内容和背景图/黑色遮罩共同处理。

后续新增特殊 chrome 页面时，必须在 rules 文档中登记原因和处理策略。

## 存量迁移范围

普通 AppBar 页面需要迁移到公共入口，业务逻辑尽量不动：

- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/PermissionsScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/fileselector/FileSelectorLiteScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/pluginlist/PluginListScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/pluginsort/PluginSortScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/pluginsub/PluginSubscriptionScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/recommendsheets/RecommendSheetsScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteScreen.kt`

迁移时只替换外层 chrome 和 scaffold 结构，避免混入无关 UI 重构。

## MainActivity 设计

`MainActivity` 继续负责：

- `enableEdgeToEdge()`。
- App 级 `Scaffold`。
- MiniPlayer bottomBar。
- 横向和底部 safe inset。

`MainActivity` 不再维护“普通页面补顶部 safe inset、搜索/播放器排除”的隐式白名单。若仍需要 route chrome 分类，应集中为清晰命名的 route policy，并由 rules 文档解释。

## 验收设计

实现阶段至少执行：

- `./gradlew :app:build`
- 受影响模块的相关测试，若已有测试覆盖则运行对应 `testDebugUnitTest`。

运行态验收应覆盖：

- 普通页面从首页进入时使用 `400ms slide_from_right`。
- 返回时方向正确。
- 普通 AppBar 页面状态栏区域显示 `appBar` 色，不出现额外顶部空白或双重 inset。
- Search、Player、Home 特殊页面没有被普通 AppBar 容器错误包裹。
- MiniPlayer 底部占位不因顶部规则变化产生回归。

如果后续仍出现 AI 反复绕开公共入口，再升级到静态检查或合同测试。

## 风险与缓解

- 风险：迁移多个 Screen 可能引入 padding 叠加。
  - 缓解：公共 scaffold 只负责 chrome 和 content padding，迁移时逐页检查原有 `padding(innerPadding)`。
- 风险：Material3 `TopAppBar` 默认 inset 与自定义状态栏占位叠加。
  - 缓解：公共 `MusicFreeTopAppBar` 明确控制窗口 inset，不让调用方直接依赖默认行为。
- 风险：特殊页面边界不清导致后续新增页面随意例外。
  - 缓解：rules 文档要求特殊 chrome 例外必须登记原因和状态栏策略。
- 风险：历史 plan 中存在 `250ms` 或其他动画说法。
  - 缓解：`DOCS_STATUS` 和 rules 文档明确当前规范优先于历史执行快照。

## 后续计划输入

下一步 implementation plan 应拆分为：

1. 新增 rules 文档并更新 `AGENTS.md` / `DOCS_STATUS.md`。
2. 新增导航动画 harness。
3. 新增普通 AppBar / Screen scaffold harness。
4. 调整 `MainActivity` 顶部 inset 责任边界。
5. 迁移普通 AppBar Screen。
6. 运行编译、测试和必要运行态验收。
