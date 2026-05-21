# 搜索结果、推荐歌单与榜单 Scene Pager 设计

> 文档状态：当前规范
> 适用范围：主搜索页结果区一级媒体 Tab、搜索结果二级插件 Tab、推荐歌单页插件 Tab、榜单页插件 Tab 的 ViewPager 式横向滑动与 scene 状态隔离。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/pages/searchPage/components/resultPanel/`、`../../../../MusicFree/src/pages/recommendSheets/components/body/index.tsx`、`../../../../MusicFree/src/pages/topList/components/topListBody.tsx`
> UI Harness 规则：[UI rules](../../dev-harness/ui/rules.md)
> Runtime State 规则：[Runtime rules](../../dev-harness/runtime/rules.md)
> 测试规则：[Test rules](../../dev-harness/test/rules.md)
> 取代：[2026-05-15 搜索结果与榜单横向手势设计](./2026-05-15-enable-horizontal-swipe-design.md)
> 最后校验：2026-05-20

## 背景

RN 原版在目标区域使用 `react-native-tab-view`：

1. 搜索结果一级媒体类型 Tab：`ResultPanel`。
2. 搜索结果二级插件 Tab：`ResultSubPanel`。
3. 推荐歌单插件 Tab：`recommendSheets/components/body`。
4. 榜单插件 Tab：`topList/components/topListBody`。

这些 TabView 的 scene 以 route 为单位存在。用户左右滑动时内容跟手移动并在 settle 后切换 index；每个 scene 可独立保存加载结果、分页、错误和滚动上下文。

Android 当前实现已经有 `horizontalTabSwipe` / `horizontalSwipeNavigation` 轻量手势：拖动结束后直接调用现有 ViewModel 的单一选中状态进行 Tab 切换。这解决了“能左右滑”的入口问题，但不是完整 ViewPager 体验：

- 滑动过程中没有相邻页跟手预览。
- 推荐歌单和榜单仍只有一份当前插件 UI state。
- 搜索结果仍围绕当前 media type / 当前 plugin state 切换。
- 切回旧 Tab 时，状态保留依赖现有结果缓存是否覆盖到该维度，而不是 scene 边界天然保证。

本次设计按用户确认的方案升级为完整 scene Pager：Pager 本身承载 scene，且每个 Tab 的状态独立。

## 目标

1. 搜索结果一级媒体 Tab 使用 `HorizontalPager`，滑动过程跟手，settle 后更新选中媒体类型。
2. 搜索结果二级插件 Tab 使用 `HorizontalPager`，每个媒体类型下的每个插件有独立搜索 scene state。
3. 推荐歌单页插件 Tab 使用 `HorizontalPager`，每个插件有独立 tags、selectedTag、sheets、page、loading、loadingMore、error、isEnd。
4. 榜单页插件 Tab 使用 `HorizontalPager`，每个插件有独立 groups、loading、error、loaded / in-flight 信息。
5. 点击 Tab 与横向滑动必须双向同步，保持 RN `TabView` 的基础交互语义。
6. 竖向列表 / 网格滚动、推荐歌单 tag 横向滚动、TabRow 自身横向滚动保持现有行为。
7. 旧请求返回只能更新自己的 scene，不得覆盖当前选中 scene 或其他 scene。

## 非目标

- 不改变搜索、推荐歌单和榜单的视觉信息架构。
- 不改变插件能力过滤、详情跳转 seed、播放、收藏、导入歌单等业务语义。
- 不引入跨进程或落盘的长期 RuntimeStore 快照；本次 scene state 是页面级运行态。
- 不重写插件管理、搜索协议或数据层。
- 不新增业务日志事件；已有插件加载 / 搜索 / 推荐歌单 / 榜单错误日志继续保留。
- 不做 Release 构建验收；普通功能收尾以 Debug 构建为默认闸门。

## 方案比较

### 方案 A：现有单状态外套 `HorizontalPager`

优点是改动小，能快速得到跟手动画。缺点是 Pager 只改变输入手势，scene 仍共享一份当前 UI state，切到相邻页时会立即覆盖内容。它无法满足“每个 Tab 状态独立”的要求。

### 方案 B：完整 scene Pager，每个 Tab 独立状态（采用）

每个 Tab 对应一个 scene key。ViewModel 保存 `selectedKey` 与 `scenes: Map<Key, SceneState>`，Pager 只负责显示和切换 scene，加载逻辑以 scene key 为边界幂等执行。

优点是最接近 RN `TabView` 语义，状态保留清晰；缺点是会改到 ViewModel 状态模型和测试，范围大于轻量手势。

### 方案 C：局部动画模拟 Pager

保留现有 `horizontalTabSwipe`，在切换时加 `AnimatedContent` / `Crossfade`。优点是低成本，缺点是仍然不是 ViewPager：没有跟手滑动，也不能自然承载相邻 scene。该方案不采用。

## 架构设计

### 公共 Pager Tab 组件

在 Compose 层新增一个小型公共组件，承载 TabRow 与 Pager 的同步逻辑。命名在实现阶段按现有包结构确定，推荐形态：

```kotlin
@Composable
fun <K> MusicFreeScenePagerTabs(
    pages: List<ScenePagerPage<K>>,
    selectedKey: K?,
    onSelectedKeyChange: (K) -> Unit,
    modifier: Modifier = Modifier,
    tabLabel: @Composable (ScenePagerPage<K>, selected: Boolean) -> Unit,
    pageContent: @Composable (ScenePagerPage<K>) -> Unit,
)

data class ScenePagerPage<K>(
    val key: K,
    val label: String,
)
```

组件职责：

- `ScrollableTabRow` 显示 pages，Tab 点击时 `animateScrollToPage(index)`。
- `HorizontalPager` 显示 scene，滑动 settle 后通过 `snapshotFlow { pagerState.settledPage }` 或等价方式调用 `onSelectedKeyChange(page.key)`。
- 外部 selectedKey 改变时，Pager 与 TabRow 同步到对应 index。
- pages 为空时不渲染 Pager，由调用方保留现有空态。
- selectedKey 不存在时回退到第一个 page，但 ViewModel 仍是选中状态的单一事实源。

组件不负责业务加载，不保存 scene 数据，不吞掉页面里的竖向滚动手势。

### 推荐歌单 scene state

推荐歌单 ViewModel 从单一 `RecommendSheetsUiState` 改为 pager 状态：

```kotlin
data class RecommendSheetsPagerUiState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginCapabilityUiModel> = emptyList(),
    val scenes: Map<String, RecommendSheetsSceneState> = emptyMap(),
)

data class RecommendSheetsSceneState(
    val tags: List<RecommendSheetTagUiModel> = emptyList(),
    val selectedTagId: String = DEFAULT_TAG_ID,
    val sheets: List<MusicSheetItemBase> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val errorMessage: String? = null,
    val emptyMessage: String? = null,
    val loaded: Boolean = false,
    val firstPageInFlight: Boolean = false,
    val loadingMorePage: Int? = null,
)
```

行为：

- 插件列表变化时保留仍存在平台的 scene，移除不存在平台的 scene。
- 首次进入或 Pager 预加载 scene 时调用 `ensureSceneLoaded(platform)`。
- scene 已 loaded 或 firstPageInFlight 时不重复首屏加载。
- tag 切换只清空并重载当前插件 scene，不影响其他插件 scene。
- `loadMore(platform)` 只更新指定 scene，且同一 scene 的同一页只能有一个 in-flight。

### 榜单 scene state

榜单 ViewModel 从单一 `TopListUiState` 改为 pager 状态：

```kotlin
data class TopListPagerUiState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginCapabilityUiModel> = emptyList(),
    val scenes: Map<String, TopListSceneState> = emptyMap(),
)

sealed interface TopListSceneState {
    data object Idle : TopListSceneState
    data object Loading : TopListSceneState
    data class Error(val message: String) : TopListSceneState
    data class Success(val groups: List<MusicSheetGroupItem>) : TopListSceneState
}
```

实现可额外保存 per-scene `loaded` / `inFlight` 元数据，避免把纯 UI sealed state 塞得太重。

行为：

- 首次进入 scene 时加载该插件榜单。
- 已加载成功的 scene 切回时直接展示旧结果。
- refresh 只刷新当前 scene。
- 插件列表变化时，仍存在插件的 scene 保留。
- 旧请求返回后只写回发起请求对应的平台 scene；如果该插件已从列表消失，则丢弃结果。

### 搜索一级媒体 scene

搜索结果一级 media type 改为 Pager scene。每个 media type scene 内部拥有自己的插件 Pager。

推荐状态边界：

```kotlin
data class SearchResultsPagerUiState(
    val selectedMediaType: SearchMediaType = SearchMediaType.Music,
    val mediaScenes: Map<SearchMediaType, SearchMediaSceneState> = emptyMap(),
)

data class SearchMediaSceneState(
    val selectedPlatform: String? = null,
    val plugins: List<PluginCapabilityUiModel> = emptyList(),
    val pluginScenes: Map<String, SearchPluginSceneState> = emptyMap(),
)
```

每个 `SearchPluginSceneState` 独立保存该媒体类型 + 插件维度的搜索结果：

- results
- page / nextPage
- loading / loadingMore
- error
- isEnd
- loadedQuery
- requestGeneration 或 requestKey
- firstPageInFlight
- loadingMorePage

如果当前搜索 query 改变，所有 media/plugin scene 必须进入新 query 的未加载状态，旧 query 请求返回时不得覆盖新 query scene。

### 搜索二级插件 scene

每个 media scene 下的插件 Pager 使用同一公共 Pager Tab 组件。插件 Tab 的状态独立于其他 media type：

- 在“单曲”媒体类型选择 A 插件后，切到“歌单”媒体类型再回来，单曲仍选中 A。
- 在某媒体类型下插件 A 加载到第 3 页，切到插件 B 后再回来，插件 A 保留列表和页码。
- 某插件失败只影响该插件 scene，不影响同媒体类型其他插件。

### Pager 预加载与请求守门

`HorizontalPager` 可能组合当前页和相邻页。所有加载入口必须按 scene key 幂等：

- `ensureSceneLoaded(key)` 只在未 loaded 且未 in-flight 时发起首屏加载。
- `loadMore(key)` 只在该 scene 未 loading、未 loadingMore、未 isEnd 时发起。
- 搜索分页额外带 `(queryGeneration, mediaType, platform, page)` request key。
- 推荐歌单分页额外带 `(platform, selectedTagId, page)` request key。
- 榜单首屏额外带 `(platform, generation)` request key。
- 响应应用前检查 scene key、query/tag/generation 仍匹配；不匹配则丢弃。

## UI 结构

### 推荐歌单页

保持 `MusicFreeScreenScaffold(title = "推荐歌单")`。页面主体：

1. 插件 Pager TabRow。
2. 当前 scene 的 tag `LazyRow`。
3. 当前 scene 的 grid / loading / error / empty / load more。

tag `LazyRow` 在 Pager scene 内部，横向滚动手势优先由 tag row 处理；内容 grid 区域可触发 Pager 横滑。

### 榜单页

保持 `MusicFreeScreenScaffold(title = "榜单")`。页面主体：

1. 插件 Pager TabRow。
2. 当前 scene 的 grouped list / loading / error / empty。

榜单 scene 的 `LazyColumn` 继续负责竖向滚动。

### 搜索结果页

保持 `SearchScreen` 已登记的特殊 Chrome，不改状态栏策略。结果区结构：

1. 一级 media Pager TabRow。
2. 一级 `HorizontalPager`。
3. 每个 media page 内部渲染该 media type 的插件 Pager TabRow。
4. 每个 plugin page 渲染该 media + plugin 的结果列表 / loading / error / empty / load more。

一级 Pager 和二级 Pager 都是横向手势。实际实现需要验证嵌套横向 Pager 的手势分发；若 Compose 嵌套 Pager 发生抢手，优先保证用户正在操作的内容层级：在二级插件区域内横滑切插件，在一级 media TabRow 或二级插件列表边界以外才切 media。若需要降级，必须在实现计划中明确降级策略并说明与本 spec 的差异。

## Runtime State 分类

本次新增或调整的是页面级高价值运行态：

- 搜索 scene、推荐歌单 scene、榜单 scene 在 Activity 重建时属于应该尽量保留的 ViewModel local state。
- 本次不要求进程冷启动恢复 scene 内容，也不落盘保存插件结果。
- 不持久化 QuickJS、插件实例、Coroutine job、Media3、Android Context 或 Repository / DAO。
- 如果后续把这些 scene 纳入 RuntimeStore / SnapshotStore，必须另起设计并遵守 Runtime State 规则。

## 错误处理与边界

- 插件列表为空时保留现有空态，不渲染 Pager。
- selectedKey 不存在时选择第一个可用插件，并初始化对应 scene。
- scene 加载失败只影响该 scene；其他 scene 保持原状态。
- 插件被禁用或卸载后，对应 scene 从 map 移除；正在返回的旧请求丢弃。
- 搜索 query 更新后，旧 query 的响应全部按 generation 丢弃。
- Pager 页面数量为 1 时仍可显示单页，横向滑动不会切换。
- 边界滑动由 Pager 自身处理，不弹 toast。

## 测试计划

单元测试：

1. 推荐歌单 ViewModel
   - 切换插件后旧插件 scene 保留 tags、selectedTag、sheets、page。
   - Pager 预加载同一插件时 `ensureSceneLoaded()` 不重复请求。
   - 当前插件 tag 切换不清空其他插件 scene。
   - 旧请求返回只写回自己的 platform scene。
2. 榜单 ViewModel
   - 每个插件独立保存 Success / Error / Loading。
   - 切回已加载插件不重复调用 `getTopLists()`。
   - 插件列表变化时保留仍存在 scene，移除失效 scene。
3. 搜索 ViewModel
   - 每个 media type 独立保存 selectedPlatform。
   - 每个 media + plugin 独立保存 results、page 和 error。
   - 同一 scene 同一页 in-flight 时不重复分页。
   - 新 query 开始后旧 query 响应不覆盖新 query scene。
4. Pager 同步逻辑
   - 点击 Tab 会请求滚动到对应 page。
   - Pager settled page 改变会回调 selected key。
   - selectedKey 外部变化时能同步到 page index。

构建和守门：

```bash
./gradlew :feature:search:testDebugUnitTest --no-daemon
./gradlew :feature:home:testDebugUnitTest --no-daemon
./gradlew :feature:search:compileDebugKotlin --no-daemon
./gradlew :feature:home:compileDebugKotlin --no-daemon
python3 scripts/dev-harness/grep-check.py
bash scripts/dev-harness/check.sh
git diff --check
./gradlew :app:assembleDebug --no-daemon
```

运行态验收：

- 若有可用设备 / 模拟器：安装 Debug APK。
- 搜索有结果后横滑一级媒体 Tab，确认内容跟手移动，settle 后 Tab 选中态同步。
- 在某个媒体类型下横滑二级插件 Tab，确认插件结果 scene 独立保留。
- 推荐歌单页横滑插件 Tab，确认 tags、列表和分页状态在切回后保留。
- 榜单页横滑插件 Tab，确认各插件榜单加载结果在切回后保留。
- 竖向滚动、tag 横向滚动、TabRow 横向滚动不被破坏。

如果设备或插件数据不可用，必须在完成报告中明确运行态验收缺口，不把静态构建称为完整通过。

## 验收标准

1. 四类目标 Tab 都具备 ViewPager 式跟手横向滑动和 settle 动画。
2. 每个 Tab scene 的加载结果、分页、错误和选中子状态互相独立。
3. 切回旧 Tab 时不重新加载已成功 scene，除非用户显式刷新或 query/tag 改变。
4. Pager 预加载不会造成重复首屏请求或重复分页请求。
5. 搜索旧 query 响应不会覆盖新 query。
6. UI Harness、相关单测、编译和 Debug 构建通过。
