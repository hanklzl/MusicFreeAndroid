# 歌单详情列表整体滚动 RN 对齐设计

> 文档状态：当前规范
> 适用范围：`:feature:home` 本地歌单详情、专辑详情、插件歌单详情、榜单详情的列表/header 滚动结构；本轮实际改动聚焦本地歌单详情和专辑详情，插件歌单详情与榜单详情以回归测试守门。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../MusicFree/src/pages/sheetDetail/`、`../../../MusicFree/src/components/musicSheetPage/`、`../../../MusicFree/src/components/musicList/index.tsx`
> 最后校验：2026-05-16

## 背景

RN 原版的歌单类详情页把顶部封面、标题、简介、播放全部条作为列表 Header 传给 `MusicList`：

- 本地歌单：`pages/sheetDetail/components/sheetMusicList.tsx` 使用 `<MusicList Header={<Header />} ... />`。
- 插件歌单、榜单、专辑：`components/musicSheetPage/components/sheetMusicList.tsx` 使用 `<MusicList Header={<Header ... />} ... />`。
- `MusicList` 内部用 `FlashList` 的 `ListHeaderComponent={Header}` 渲染，因此顶部简介区域和歌曲行共用一个滚动容器。

Android 当前状态：

- `PluginSheetDetailScreen` 与 `TopListDetailScreen` 已把 `MusicSheetPageHeader` 放进 `LazyColumn.item(key = "header")`，结构符合 RN。
- `PlaylistDetailScreen` 先渲染 `PlaylistDetailHeader`，再单独渲染歌曲 `LazyColumn`；滚动歌曲列表时 header 固定在 AppBar 下方，不符合 RN。
- `AlbumDetailScreen` 没有复用 `MusicSheetPageHeader`，专辑元信息没有进入歌曲列表 header；这与 RN `AlbumDetail -> MusicSheetPage` 不一致。

## 目标

1. 本地歌单详情页滚动时，封面、标题、简介、播放全部/搜索入口随歌曲列表一起滚出屏幕。
2. 本地歌单空态也属于同一个 `LazyColumn`，避免空歌单时 header 固定、空态单独占满剩余区域。
3. 专辑详情页补齐 RN `MusicSheetPage` 风格 header，并让 header 作为 `LazyColumn` 第一项跟随滚动。
4. 专辑详情页复用 `PlayAllBar` 时隐藏尚未接入的“添加到歌单”按钮，避免出现无响应操作。
5. 插件歌单详情和榜单详情保持现有 header-in-list 结构，并用测试防止回退。
6. 保持普通 AppBar 页面继续使用 `MusicFreeScreenScaffold`，不新增特殊 Chrome，不改导航动画。

## 非目标

- 不重做歌单详情视觉全量 fidelity。
- 不改变插件、播放器、数据库或导航 route 协议。
- 不引入新的持久化字段或 Room migration。
- 不改歌曲行点击语义；行点击仍按现有 ViewModel 行为播放对应位置。
- 不验证 Release 构建；普通功能收尾以 Debug 构建为闸门。

## 方案比较

### 方案 A：仅移动本地歌单 header 到 `LazyColumn`

改动最小，直接修复用户指出的固定 header 问题。缺点是专辑详情仍与 RN `MusicSheetPage` 不一致。

### 方案 B：本地歌单与专辑详情都采用 header-as-list-item

本地歌单修复固定 header；专辑详情补齐 RN 共用详情页的 header 口径。插件歌单和榜单已经符合结构，只补测试守门。这是本轮推荐方案，范围仍集中在 `:feature:home` UI。

### 方案 C：抽一个跨页面 `MusicSheetLazyList` 统一四类详情页

长期可减少重复，但会同时重排本地歌单、专辑、插件歌单、榜单的行渲染和分页逻辑，当前问题不需要这么大改动。

本轮采用方案 B。

## 设计

### 本地歌单详情

`PlaylistDetailScreen` 的内容区从外层 `Column + LazyColumn` 改为单个 `LazyColumn`：

1. `item(key = "header")` 渲染 `PlaylistDetailHeader`。
2. 歌曲为空时，`item(key = "empty")` 渲染 `EmptyState`。
3. 歌曲非空时，`itemsIndexed` 渲染 `MusicItemRow`。

这样 header、空态和歌曲行共享同一个滚动状态。`PlaylistDetailHeader` 本身暂不拆分，只保留当前本地歌单的播放全部与搜索入口。`onNavigateToMusicListEditorLite` 暂保持未使用状态，不在本轮扩展菜单行为。

### 专辑详情

`AlbumDetailScreen` 的 `LazyColumn` 增加首项 `MusicSheetPageHeader`：

- `cover` 使用首屏 `uiState.albumItem?.artwork ?: uiState.albumItem?.coverImg`。
- `title` 使用 `uiState.albumItem?.title ?: uiState.title`。
- `worksNum` 使用 `uiState.albumItem?.worksNum`。
- `musicListSize` 使用 `uiState.musicList.size`。
- `description` 使用 `uiState.albumItem?.description`。
- `actions` 使用 `PlayAllBar(onPlayAll = viewModel.playAll, onAddToPlaylist = {}, starred = isStarred, onToggleStarred = viewModel.toggleAlbumStarred, showAddToPlaylist = false)`。专辑页当前没有批量加入歌单链路，本轮不新增无响应按钮。

如果实现时发现 `AlbumDetailUiState` 没有完整 `albumItem` 字段，则先补 ViewModel/UI state 的只读字段，来源使用现有详情 seed 或插件返回的 album item，不改 route 协议。

### 插件歌单和榜单详情

两页已经通过 `LazyColumn.item(key = "header")` 渲染 `MusicSheetPageHeader`。本轮不改业务逻辑，仅新增测试确保 header marker 在 `LazyColumn` 内，避免后续回归成外层固定 header。

### 测试策略

新增纯源码结构测试，原因是 Compose runtime 测试当前没有现成的 Screen fixture 和 Hilt 替身；本需求的关键风险是结构回退，不是复杂状态变换。

测试放在 `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/detail/ScrollableHeaderSourceTest.kt`：

- 断言 `PlaylistDetailScreen.kt` 不再出现 `Column(modifier = Modifier.fillMaxSize().padding(padding))` 包住 `PlaylistDetailHeader`。
- 断言 `PlaylistDetailScreen.kt` 中 `PlaylistDetailHeader` 出现在 `LazyColumn` 的 `item(key = "header")` 之后。
- 断言 `AlbumDetailScreen.kt` 中存在 `MusicSheetPageHeader` 且位于 `LazyColumn` header item 内。
- 断言 `PluginSheetDetailScreen.kt` 和 `TopListDetailScreen.kt` 保持 `item(key = "header")` + `MusicSheetPageHeader`。

同时扩展 `AlbumDetailViewModelTest`，验证插件详情返回的 `albumItem` 会进入 `AlbumDetailUiState.albumItem`，供 header 渲染封面、简介与曲目数。

源码结构测试不替代编译验证；最终仍运行 `:feature:home:testDebugUnitTest`、`scripts/dev-harness/grep-check.py`、`git diff --check` 和 `:app:assembleDebug`。

## 验收标准

1. 本地歌单详情滚动歌曲列表时，header 不固定在 AppBar 下方。
2. 本地空歌单页面中，header 和空态属于同一滚动容器。
3. 专辑详情顶部信息进入歌曲列表 header，并跟随列表滚动。
4. 插件歌单详情、榜单详情的 header-in-list 结构仍存在。
5. Debug 构建通过；没有新增 UI Harness grep 违规。
