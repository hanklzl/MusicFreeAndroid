# 推荐歌单与榜单 RN 对齐及详情点击修复设计

> 文档状态：当前规范
> 适用范围：仅适用于 `:feature:home` 推荐歌单、榜单、插件歌单详情、榜单详情链路，以及相关导航 route 的轻字段扩展。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../MusicFree/src/pages/recommendSheets/`、`../../../MusicFree/src/pages/topList/`、`../../../MusicFree/src/pages/pluginSheetDetail/`、`../../../MusicFree/src/pages/topListDetail/`、`../../../MusicFree/src/components/mediaItem/sheetItem.tsx`、`../../../MusicFree/src/components/mediaItem/topListItem.tsx`
> 最后校验：2026-05-09

## 背景

当前 Android 推荐歌单与榜单页面已经有基础链路，但与 RN 原版存在明显差距，并且从列表点击进入详情时存在运行风险。

已确认的代码差异：

1. RN 推荐歌单与榜单页面使用 `TabView + TabBar`，插件来源分别是 `PluginManager.getSortedPluginsWithAbility("getRecommendSheetsByTag")` 与 `PluginManager.getSortedPluginsWithAbility("getTopLists")`。Android 当前展示所有插件，并使用分段按钮显示 `platform`，没有按能力过滤。
2. RN 推荐歌单列表是 `FlashList` 三列封面网格，卡片使用 `SheetItem -> ImageBtn`，展示方形封面和两行标题。Android 当前是单列行，展示 96rpx 封面、标题和 `artist`。
3. RN 榜单列表是 `SectionList`，行项使用 `TopListItem -> ListItem`，副文案来自 `topListItem.description`。Android 当前行副文案使用 `artist`，常见榜单数据会显示为空。
4. RN 进入插件歌单详情与榜单详情时把完整 `sheetInfo` / `topList` 对象放入 route 参数，详情加载调用插件方法时传原始对象。Android 当前推荐歌单详情只把部分轻字段写入 route，榜单详情只传 `topListId`，详情页再调用 `getTopLists()` 反查对象。对依赖 `raw` 字段、扩展字段或不稳定列表结果的插件，这会导致详情加载失败或信息丢失。

## 目标

1. 首页入口进入推荐歌单和榜单后，只展示支持对应插件能力的已启用插件，并按用户插件排序。
2. 推荐歌单入口页主要 UI 对齐 RN：插件 tab、tag 横向区、三列封面网格、卡片展示封面和两行标题。
3. 榜单入口页主要 UI 对齐 RN：插件 tab、分组列表、榜单行副文案优先使用 `description`。
4. 修复推荐歌单和榜单点击进入详情的 seed 对象丢失问题，详情页调用插件方法时优先使用列表点击时的完整 `MusicSheetItemBase`。
5. 保持普通 AppBar 页面继续通过 `MusicFreeScreenScaffold`，符合 [screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)。

## 非目标

- 不重做插件歌单详情页和榜单详情页的完整 RN `MusicSheetPage` header、播放全部条、收藏交互或 mini player fidelity。
- 不改 QuickJS 插件协议，不改变 `PluginApi` 方法签名。
- 不把插件 `raw` 任意字段序列化进 Navigation route。
- 不新增数据库字段或 Room migration。
- 不改首页四宫格入口本身的布局。
- 不做 Release 构建验收；普通功能收尾以 Debug 构建为闸门。

## 实施约束（worktree）

按 [AGENTS](../../../AGENTS.md) 「Git Worktree 开发约束」节：本 spec 的实施在 `.worktrees/recommend-toplist-rn-align` 下进行，不在主工作区直接堆叠改动。文档、代码和提交信息中的路径引用使用相对路径，不写入 `/Users/...` 绝对路径。

## 设计方案

### 插件能力过滤

推荐歌单和榜单 ViewModel 的插件来源改为 `pluginManager.getSortedEnabledPlugins()`，再按 `PluginInfo.supportedMethods` 过滤：

- 推荐歌单：`getRecommendSheetsByTag`
- 榜单：`getTopLists`

初始选择第一个支持插件。切换插件时重置页面状态并加载对应插件数据。插件列表为空时区分两种空态：

- 没有可用插件：`暂无可用插件，请先在设置中安装或启用插件`
- 有插件但没有支持能力：`当前没有支持推荐歌单的插件` / `当前没有支持榜单的插件`

这对齐 RN `getSortedPluginsWithAbility(...)` 行为，也避免用户选择不支持能力的插件后进入无意义错误态。

### 共享插件 Tab

在 `:feature:home` 内新增轻量 composable，例如 `PluginCapabilityTabs`，供推荐歌单和榜单复用。它只负责展示和选择，不持有业务状态。

设计约束：

- 使用 Material3 `ScrollableTabRow`，接近 RN `TabBar scrollEnabled`。
- tab 文案优先用当前可用的 `PluginInfo.platform`。当前 Android `PluginInfo` 没有 RN `name` 字段，本轮不扩展插件元数据模型。
- 选中态使用 `MusicFreeTheme.colors.primary`，未选中态使用 `MusicFreeTheme.colors.text`。
- 不抽到 `:core`，避免形成过早公共 API。

### 推荐歌单 UI

`RecommendSheetsScreen` 保持 `MusicFreeScreenScaffold(title = "推荐歌单")`。内容结构：

1. `PluginCapabilityTabs`
2. tag 横向区
3. `LazyVerticalGrid(columns = GridCells.Fixed(3))`

网格卡片对齐 RN `ImageBtn` 信息架构：

- 封面优先 `artwork`，再用 `coverImg`。
- 方形封面，圆角约 `rpx(12)`。
- 标题最多两行，省略尾部。
- 不显示 `artist` 副文案。
- 卡片点击调用 `onOpenSheetDetail(pluginPlatform, item)`，传入完整列表 item。

tag 区保持 Android 已有 `RecommendTag` 数据结构，但视觉更接近 RN `TypeTag`：

- 第一项是默认/当前分类入口。
- pinned tag 横向排列。
- 本轮不实现 RN 的 `SheetTags` 浮层；全部分类浮层属于后续完整 fidelity 范围。

### 榜单 UI

`TopListScreen` 保持 `MusicFreeScreenScaffold(title = "榜单")`。内容结构：

1. `PluginCapabilityTabs`
2. 分组 `LazyColumn`

分组标题：

- 使用 `FontSizes.title` 或本地现有标题层级，字重加粗。
- 间距参考 RN `BoardPanel`：顶部约 `rpx(28)`，底部约 `rpx(24)`，左侧 `rpx(24)`。

榜单行：

- 封面优先 `coverImg`，再用 `artwork`，尺寸接近 RN `ListItemImage` 的 `rpx(80)`。
- 标题使用 `item.title ?: "未命名榜单"`。
- 副文案优先 `item.description`，为空再兜底 `item.artist`，仍为空则不显示副文案行。
- 点击调用 `onOpenTopListDetail(pluginPlatform, item)`，传入完整 topList item，而不是只传 id。

### 详情 seed store

新增 `PluginSheetSeedStore`，默认放在 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/navigation/`，结构参考现有 `MusicDetailSeedStore`：

```kotlin
object PluginSheetSeedStore {
    fun put(item: MusicSheetItemBase): String
    fun take(token: String?): MusicSheetItemBase?
    internal fun clear()
}
```

如果实现时认为推荐歌单与榜单分开更清晰，也允许命名为 `MusicSheetSeedStore` 并同时服务 `PluginSheetDetailRoute` 与 `TopListDetailRoute`。关键约束是：

- seed 仅用于同进程短期导航传递。
- `take()` 一次性消费，避免无限增长。
- route 必须保留轻字段兜底，seed 丢失时仍可加载。

### Route 扩展

`PluginSheetDetailRoute` 增加：

- `description: String? = null`
- `worksNum: Int? = null`
- `seedToken: String? = null`

`TopListDetailRoute` 从只包含 `pluginPlatform` / `topListId` 扩展为：

- `pluginPlatform: String`
- `topListId: String`
- `title: String? = null`
- `artist: String? = null`
- `description: String? = null`
- `coverImg: String? = null`
- `artwork: String? = null`
- `worksNum: Int? = null`
- `seedToken: String? = null`

不在 route 中保存 `raw`。详情页 seed 构造逻辑：

1. 优先 `PluginSheetSeedStore.take(route.seedToken)`。
2. 如果取不到，使用 route 轻字段构造 `MusicSheetItemBase`。
3. 构造 fallback 的 `raw` 只包含可安全重建的轻字段：`id`、`platform`、`title`、`artist`、`description`、`coverImg`、`artwork`、`worksNum`。

### 详情 ViewModel 加载

`PluginSheetDetailViewModel`：

- `loadInitial()` 优先使用 seed store 恢复完整 sheet。
- seed 不存在时使用 route fallback。
- `getMusicSheetInfo(seed, page = 1)` 的调用逻辑保持现状。
- 插件返回 `detail.sheetItem` 后继续用返回对象更新 `currentSheet`，但保留 fallback title。

`TopListDetailViewModel`：

- 删除首屏必须 `findTopListById(route.topListId)` 的依赖。
- `loadInitial()` 直接使用 seed store 或 route fallback 作为 `seedTopList` 调用 `getTopListDetail(seedTopList, page = 1)`。
- `loadMore()` 使用 `currentTopList`，保持当前分页逻辑。
- 可保留 `findTopListById` 作为极端兜底，但不能作为正常路径前置条件。

这样对齐 RN `useTopListDetail(topList, pluginHash)` 与 `usePluginSheetMusicList(sheetInfo)`：详情请求使用点击时拿到的媒体对象，而不是只用 id 反查。

## 错误处理

- 插件不存在：保留当前 `插件不存在：<platform>` 错误。
- 插件方法返回 `null`：保留当前 `加载推荐歌单失败` / `加载榜单失败` / `加载歌单失败`。
- seed token 失效：不直接报错，使用 route fallback。
- route fallback 的 `id` 或 `platform` 为空理论上被 route 构造约束避免；如果实现中遇到空值，显示 `加载失败` 并允许返回，不崩溃。
- 推荐歌单 tag 切换、刷新、加载更多保持现有 pending/error/end 状态语义。

## 测试计划

单元测试：

1. `RecommendSheetsViewModelTest`
   - 只选择支持 `getRecommendSheetsByTag` 的已启用插件。
   - 没有支持能力插件时进入明确空态。
   - 选择 tag 后把完整 tag payload 传给插件。
2. `TopListViewModelTest`
   - 只选择支持 `getTopLists` 的已启用插件。
   - 点击 callback 接收完整 `MusicSheetItemBase` 所需的数据结构。
3. `PluginSheetSeedStoreTest`
   - `put` 后 `take` 返回同一对象。
   - 第二次 `take` 返回 null。
   - 空 token 返回 null。
4. route 测试
   - `TopListDetailRoute` 和 `PluginSheetDetailRoute` 新字段可序列化。
5. 展示派生函数测试
   - 榜单行副文案优先 `description`，为空才用 `artist`。

如现有 ViewModel 依赖 Hilt/Android 环境不便直接单测，允许把过滤、fallback seed 构造、榜单副文案等逻辑提取为包内纯函数后测试纯函数；不为了测试引入复杂框架。

构建验证：

```bash
./gradlew :feature:home:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

运行态验证：

- 若有可用设备或模拟器：安装 Debug APK，验证 `首页 -> 推荐歌单 -> 歌单详情` 和 `首页 -> 榜单 -> 榜单详情` 可以进入详情并完成首屏加载。
- 若没有可用设备或插件数据：明确报告未完成运行态验收，并保留编译/单测结果作为静态闸门。

## 验收标准

1. 推荐歌单页面不再展示不支持 `getRecommendSheetsByTag` 的插件。
2. 榜单页面不再展示不支持 `getTopLists` 的插件。
3. 推荐歌单列表为三列封面网格，卡片显示封面与两行标题。
4. 榜单分组列表显示分组标题，行副文案优先显示 `description`。
5. 从推荐歌单点击歌单能进入插件歌单详情，详情请求使用完整 seed 对象。
6. 从榜单点击榜单能进入榜单详情，不再依赖详情页重新按 id 反查榜单对象。
7. route seed token 丢失时仍有轻字段 fallback，不因进程重建直接崩溃。
8. 上述单元测试与 Debug 构建验证通过，或清楚记录无法运行的原因。
