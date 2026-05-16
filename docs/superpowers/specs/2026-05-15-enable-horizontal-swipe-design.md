# 搜索结果与榜单横向手势设计

> 文档状态：当前规范
> 适用范围：主搜索页结果区、推荐歌单页、榜单页的 Tab 横向滑动切换手势。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/pages/searchPage/components/resultPanel/`、`../../../../MusicFree/src/pages/recommendSheets/components/body/index.tsx`、`../../../../MusicFree/src/pages/topList/components/topListBody.tsx`
> UI Harness 规则：[UI rules](../../dev-harness/ui/rules.md)
> 测试规则：[Test rules](../../dev-harness/test/rules.md)
> 最后校验：2026-05-15

## 背景

RN 原版在三处目标页面使用 `react-native-tab-view`：

1. 搜索结果一级媒体类型 Tab：`ResultPanel`。
2. 搜索结果二级插件 Tab：`ResultSubPanel`。
3. 推荐歌单和榜单插件 Tab：`recommendSheets` 与 `topList` body。

这些 `TabView` 没有显式禁用 swipe，因此默认支持内容区左右滑动切换。Android 当前实现只使用 `ScrollableTabRow` / `Tab` 和单一内容区：

- `SearchResultPanel` 点按切换媒体类型和插件。
- `PluginCapabilityTabs` 点按切换推荐歌单 / 榜单插件。
- 页面内容区没有横向 drag 识别，因此用户只能点击 Tab。

这不是导航动画问题，也不是特殊 chrome 问题。`SearchScreen` 仍保持已登记的特殊 Chrome 页面；推荐歌单和榜单仍保持 `MusicFreeScreenScaffold` 普通页面结构。

## 目标

1. 搜索结果区支持左右滑动切换媒体类型 Tab。
2. 搜索结果区支持左右滑动切换插件 Tab。
3. 推荐歌单页支持左右滑动切换插件 Tab。
4. 榜单页支持左右滑动切换插件 Tab。
5. 竖向列表 / 网格滚动、推荐歌单 tag 横向滚动、TabRow 自身横向滚动保持现有行为。
6. 不改变插件加载、分页、点击进入详情、播放、收藏和搜索焦点语义。

## 非目标

- 不重构搜索结果为完整多 scene pager。
- 不为每个插件或媒体类型新增独立缓存状态。
- 不改变 RN 对齐过的推荐歌单 / 榜单视觉结构。
- 不改 `SearchScreen` 状态栏、AppBar 或页面切换动画。
- 不新增业务日志；本次只是本地 UI 手势输入能力。
- 不做 Release 构建验收；普通功能收尾以 Debug 构建为闸门。

## 方案比较

### 方案 A：全面迁移到 `HorizontalPager`

优点是视觉模型接近 RN `TabView`，可以跟随拖动展示相邻页。缺点是当前 ViewModel 只有单一选中 Tab 的状态，Pager 会在滑动期间组合相邻页；搜索与推荐歌单的分页 footer 使用 `LaunchedEffect` 自动 load more，重复组合可能触发额外分页请求。若要彻底规避，需要把每个 scene 的状态分离，范围明显超过本次需求。

### 方案 B：共享水平 drag helper 切换现有 Tab（推荐）

在内容区增加 `Modifier.horizontalTabSwipe(...)`，使用 Compose Foundation 的水平 drag 识别：横向拖动超过阈值时选择前一 / 后一 Tab，竖向手势仍交给 LazyColumn / LazyGrid，TabRow 和 tag LazyRow 不挂载该 modifier，避免抢占自身横向滚动。

优点是改动小，复用现有 ViewModel 与页面结构，不引入重复组合和分页副作用；缺点是不会展示 RN TabView 那种跟手滑动中的相邻页预览。对“开启左右滑动手势”的当前目标，这是成本和风险最合适的方案。

### 方案 C：各页面本地复制 pointerInput 逻辑

优点是不用新增公共 API。缺点是搜索、推荐歌单、榜单会出现三份手势阈值和边界逻辑，后续容易漂移，也更难单测。该方案不采用。

## 设计

### 共享手势 helper

在 `:core` 的 `core.ui` 新增轻量 helper：

```kotlin
enum class HorizontalSwipeDirection { Previous, Next }

fun Modifier.horizontalSwipeNavigation(
    enabled: Boolean = true,
    onSwipe: (HorizontalSwipeDirection) -> Unit,
): Modifier

fun Modifier.horizontalTabSwipe(
    selectedIndex: Int,
    pageCount: Int,
    enabled: Boolean = true,
    onSelectIndex: (Int) -> Unit,
): Modifier
```

行为：

- `pageCount <= 1` 或 `selectedIndex` 越界时不安装手势。
- 横向拖动累计距离超过阈值才切换，避免轻微抖动误触。
- 向左拖动切到下一项，向右拖动切到上一项。
- 边界处保持当前 Tab。
- 使用 `detectHorizontalDragGestures`，让 Compose 先按方向 touch slop 判定；竖向滚动优先时不触发横向切换。

`horizontalSwipeNavigation(...)` 只负责把一次有效横向 drag 转成 `Previous` / `Next`。`horizontalTabSwipe(...)` 在其上封装常见的单层 Tab 切换。同时提取纯函数 `resolveHorizontalSwipeTarget(...)`，覆盖阈值、方向、边界和 disabled case 的 JVM 单测。

实现上使用 `Modifier.composed` + `rememberUpdatedState` 保存最新 callback，`pointerInput` 只以稳定配置作为 key，避免普通重组在拖动过程中取消手势协程。

### 搜索结果区

`SearchResultPanel` 保留现有两个 `ScrollableTabRow`：

- 媒体类型 Tab 继续调用 `onSelectMediaType(type)`。
- 插件 Tab 继续调用 `onSelectPlatform(platform)`。

内容容器改为带手势的区域，但搜索页只挂一个横向手势入口，避免同一次 drag 同时触发媒体类型和插件切换：

1. 若当前插件 Tab 在拖动方向仍可移动，优先按 `searchablePlugins` 顺序切换插件。
2. 若插件不可移动（无插件、单插件、选中项非法或已到边界），再按 `SearchMediaType.entries` 顺序切换媒体类型。
3. 手势只挂在结果内容区，不挂在两个 TabRow 上，避免和 TabRow 横向滚动冲突。

切换后继续走 `SearchViewModel.selectMediaType()` / `selectPlatform()`，保持已有搜索加载、pending search、当前插件状态和分页逻辑。

### 推荐歌单和榜单

`PluginCapabilityTabs` 仍只是显示和点按选择，不在组件内部处理手势，避免让 TabRow 自身的横向滚动变成页面切换。

`RecommendSheetsScreen` 与 `TopListScreen` 在 TabRow 下方内容区挂插件手势：

- 当前选中平台通过 `plugins.indexOfFirst { it.platform == selectedPlugin }` 转成 index。
- 左滑选择后一个插件，右滑选择前一个插件。
- 切换后调用现有 `viewModel.selectPlugin(platform)`。
- 推荐歌单 tag `LazyRow` 不挂手势，保持 tag 横向滚动优先。

## 错误处理与边界

- 插件列表为空或只有一个插件时不安装手势。
- 当前选中项不在列表内时不安装手势，等待 ViewModel 自行恢复选中项。
- 拖动距离不足阈值时不切换。
- 边界 Tab 继续保持当前项，不报错、不弹 toast。
- 手势切换仍走现有 ViewModel，插件不存在、加载失败、空态等错误处理保持不变。
- 搜索结果分页在 `SearchViewModel.loadMore()` 内维护同一 `(generation, query, mediaType, platform, nextPage)` 的 in-flight guard，并在响应应用前校验当前 query / generation，避免横向切换、Lazy footer 重新组合或新搜索期间对同一页发起重复分页请求，且旧 query 分页不能覆盖新 query 结果。

## 测试计划

单元测试：

1. `HorizontalTabSwipeTest`
   - 左滑超过阈值从中间项切到后一项。
   - 右滑超过阈值从中间项切到前一项。
   - 拖动不足阈值不切换。
   - 首项右滑和末项左滑保持边界。
   - `pageCount <= 1` 或 index 越界保持当前项。
2. `SearchViewModelTest`
   - 同一页 `loadMore()` 请求未完成时，重复触发只调用一次插件分页接口。
   - 旧 query 的分页仍在 flight 时，新 query 的同页分页不被拦截，旧响应返回后不覆盖新 query 结果。

构建和守门：

```bash
./gradlew :core:testDebugUnitTest --tests "com.hank.musicfree.core.ui.HorizontalTabSwipeTest" --no-daemon
./gradlew :feature:search:testDebugUnitTest --tests "com.hank.musicfree.feature.search.SearchViewModelTest" --no-daemon
./gradlew :feature:search:compileDebugKotlin --no-daemon
./gradlew :feature:home:compileDebugKotlin --no-daemon
python3 scripts/dev-harness/grep-check.py
bash scripts/dev-harness/check.sh
git diff --check
./gradlew :app:assembleDebug --no-daemon
```

运行态验收：

- 若当前有可用设备 / 模拟器：安装 Debug APK，搜索有结果后在结果内容区左右滑动，确认媒体类型和插件 Tab 可切换；进入推荐歌单和榜单后在内容区左右滑动，确认插件 Tab 可切换。
- 若设备或插件数据不可用，明确报告运行态验收缺口，不把静态构建误称为完整运行态通过。

## 验收标准

1. 搜索结果内容区左滑 / 右滑可以切换媒体类型 Tab。
2. 搜索结果内容区左滑 / 右滑可以切换插件 Tab。
3. 推荐歌单内容区左滑 / 右滑可以切换插件 Tab。
4. 榜单内容区左滑 / 右滑可以切换插件 Tab。
5. 竖向列表 / 网格滚动不被横向手势破坏。
6. UI Harness grep 守门、相关编译和 Debug 构建通过。
