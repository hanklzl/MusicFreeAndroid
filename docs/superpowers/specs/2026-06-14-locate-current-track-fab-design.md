# 歌单详情页「定位当前播放」悬浮按钮

> 文档状态：当前规范补充（设计决策记录）
> 适用范围：`:feature:home` 的 4 个歌曲列表详情页（本地歌单 / 插件歌单 / 榜单 / 专辑）+ `:core:ui` 新增共享 FAB 组件
> 关联规则：[ui/rules.md#rule-no-raw-material3-topappbar](../../dev-harness/ui/rules.md#rule-no-raw-material3-topappbar)、[#rule-user-action-logging](../../dev-harness/ui/rules.md#rule-user-action-logging)、[runtime/rules.md#rule-runtime-state-classification](../../dev-harness/runtime/rules.md#rule-runtime-state-classification)、[player/rules.md](../../dev-harness/player/rules.md)
> 决策日期：2026-06-14

## 背景 / 目标

对齐 RN 原版功能：在歌单/列表详情页，当存在当前播放曲目且该曲目在本列表中时，右下角显示一个悬浮按钮（FAB），点击后把列表滚动定位到当前正在播放的歌曲。

RN 参考实现（`../MusicFree`）：

- 共享组件 `src/components/musicList/index.tsx` 内置该按钮：圆形 FAB，`crosshair` 图标，背景 `colors.notification`，定位在 `bottom: rpx(80) / right: rpx(84)`。
- 滚动用 `flashListRef.scrollToIndex({ index, animated: false, viewPosition: 0 })`（顶端对齐当前曲目）。
- 当前曲目来自 `useCurrentMusic()`，用 `isSameMediaItem`（`platform + id`）匹配列表项。
- RN 中该按钮的可见性为「滚动触发 + 停止约 5 秒自动隐藏」，且实际只接到了本地歌单详情页（`src/pages/sheetDetail`），其它列表页未接 `highlightMusicItem`，属未完成铺开。

本设计与 RN 的两处有意分歧（已与需求方确认）：

1. **覆盖范围**：覆盖 4 个歌曲列表详情页 —— 本地歌单（`PlaylistDetailScreen`）、插件歌单（`PluginSheetDetailScreen`）、榜单（`TopListDetailScreen`）、专辑（`AlbumDetailScreen`）。补齐 RN 未铺开的部分，体验统一。播放历史、本地音乐列表本期不做。
2. **显示时机**：改为「当前播放曲目存在且在本列表中时常驻显示」，不做滚动触发与自动隐藏。曲目不在本列表则隐藏（FAB 始终是「点了有用」的状态）。

## 现状关键事实（已核验）

- 这 4 个页面各自持有自己的 `LazyColumn`，**没有共享的列表容器**（不存在 Android 版 `MusicList`）；共享的只到「行」级别（`core/ui/MusicItemRow`、`feature/home/component/PluginMusicRow`）。
- 当前没有任何详情页持有 `LazyListState`，`LazyColumn` 均未传 `state=`，无法程序化滚动。
- `PlaylistDetailScreen` 的 `LazyColumn` 结构为：`item(key="header")` → `itemsIndexed(state.musics)`，故音乐项的 lazy index = `1 + 列表内下标`，即 `headerOffset = 1`（已核验）。其余 3 页的 header 项数需在实现时逐一核验。
- 当前播放曲目来自 `PlayerController.playerState: StateFlow<PlayerState>` 的 `currentItem: MusicItem?`；曲目身份在全仓一致地用 `platform + id` 标识。`PlaylistDetailViewModel` / `PluginSheetDetailViewModel` 已注入 `PlayerController`。
- `MusicFreeScreenScaffold` 已暴露 `floatingActionButton: @Composable () -> Unit = {}` 槽位并透传给 Material3 `Scaffold`（`core/ui/MusicFreeScreenChrome.kt:120/149→162`），是新增 FAB 的合规入口。
- MiniPlayer 由 `MainActivity` 外层 `Scaffold` 的 `bottomBar` 渲染，`AppNavHost` 内容以 `innerPadding` 上移到 MiniPlayer 之上（`MainActivity.kt:243`）。因此每个页面的 `MusicFreeScreenScaffold` 内容区本就在 MiniPlayer 之上，bottom-end 的 FAB 会自然浮在 MiniPlayer 上方，无需额外避让。

## 决策：共享 FAB 组件 + 纯函数下标计算（方案 B）

不抽取统一的「列表容器」（4 页的 header、行组件、actions、分页差异大，统一容器属高风险且超出本期范围）；也不在每页各写一份可见性/滚动/埋点逻辑（重复 4 份、header offset 易错）。折中：把按钮/可见性/滚动/埋点/样式收敛到一个共享 composable，每页只负责持有 `LazyListState` 并把 FAB 放进 scaffold 的 `floatingActionButton` 槽位。

### 新增共享组件 `core/src/main/java/com/hank/musicfree/core/ui/LocateCurrentTrackFab.kt`

```kotlin
// 纯函数，可单测：当前曲目不在本列表时返回 -1。
fun currentTrackIndex(items: List<MusicItem>, current: MusicItem?): Int =
    if (current == null) -1
    else items.indexOfFirst { it.id == current.id && it.platform == current.platform }

@Composable
fun LocateCurrentTrackFab(
    listState: LazyListState,
    items: List<MusicItem>,
    currentItem: MusicItem?,
    headerOffset: Int,        // 音乐行之前的 lazy 项数（4 页预期均为 1，实现时逐页核验）
    screen: String,           // 稳定页面名，如 "playlist_detail"
) {
    val target = remember(items, currentItem) { currentTrackIndex(items, currentItem) }
    if (target < 0) return    // 无当前曲目，或当前曲目不在本列表 → 不显示 FAB
    val scope = rememberCoroutineScope()
    SmallFloatingActionButton(
        onClick = {
            logUiClick("$screen.fab.scroll_to_current", screen)
            scope.launch { listState.scrollToItem(headerOffset + target) }
        },
        containerColor = MusicFreeTheme.colors.primary,
        contentColor = MusicFreeTheme.colors.onPrimary,
    ) { Icon(Icons.Filled.MyLocation, contentDescription = "定位当前播放") }
}
```

### 数据流

每个 VM 暴露 `val currentPlayingItem: StateFlow<MusicItem?> = playerController.playerState.map { it.currentItem }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)`；页面用 `collectAsStateWithLifecycle()` 收集后传入 FAB。`TopListDetailViewModel` / `AlbumDetailViewModel` 若未注入 `PlayerController`，实现时补注入。

### 行为

- **可见性**：`currentItem != null && 该曲目在本列表中`（`platform + id` 匹配）。常驻，无滚动触发、无自动隐藏。
- **点击**：即时 `scrollToItem(headerOffset + index)`，把当前曲目顶端对齐（等价 RN 的 `viewPosition:0`）。用即时滚动而非 `animateScrollToItem`，避免长列表逐项动画的卡顿，并与既有 `PlayQueue` 初始滚动一致。
- **分页（插件歌单）**：当前曲目若在尚未加载的分页上，匹配不到（index = -1），FAB 暂不显示，待该页加载后出现。可接受。
- **「正在播放」的口径**：以 `currentItem != null` 为准（含暂停态），对齐 RN 的 `useCurrentMusic()`。

### 样式 / 位置

小号圆形 FAB（`SmallFloatingActionButton`），经 scaffold 槽位定位在 bottom-end（对齐 RN 右下角）。图标用 Material `Icons.Filled.MyLocation`（「定位」十字准星，最接近 RN 的 `crosshair`）。着色用 `MusicFreeTheme.colors.primary`（与队列行当前项高亮一致的强调色）。FAB 自然浮在 MiniPlayer 上方（见上文布局事实）。

### 每页改动（×4）

`PlaylistDetailScreen`、`PluginSheetDetailScreen`、`TopListDetailScreen`、`AlbumDetailScreen`，每页：

1. `val listState = rememberLazyListState()`，传给 `LazyColumn(state = listState)`。
2. 页面外层从 `viewModel.currentPlayingItem` 收集 `currentItem`，传入无状态内容 composable（如 `PlaylistDetailContent` 增加 `currentItem: MusicItem? = null` 参数，默认 `null` 以兼容既有预览/测试）。
3. `MusicFreeScreenScaffold(..., floatingActionButton = { LocateCurrentTrackFab(listState, <本页音乐列表>, currentItem, headerOffset = <核验值>, screen = "<稳定名>") })`。
4. 实现时逐页核验 `headerOffset`（预期 1）与本页「音乐列表」字段名。

稳定页面名（用于 `logUiClick` 与 `screen`）：`playlist_detail`、`plugin_sheet_detail`、`top_list_detail`、`album_detail`。

## 取舍

- **不抽取统一列表容器**：4 页结构差异大，统一属高风险重构，违背「不做无关重构」。共享只到 FAB 组件级别即可消除重复。
- **常驻 vs RN 滚动触发**：选常驻，可发现性更好、实现更简单，符合需求方「有歌在播就显示」的表述；代价是与 RN 的「滚动浮现 + 5s 自动隐藏」细节不一致。
- **即时滚动 vs 动画滚动**：选即时，长列表更稳，且对齐 RN `animated:false` 与既有 `PlayQueue`。
- **不做行高亮**：RN 的 `MusicList` 还会高亮当前播放行；本期只做按钮+定位，行高亮显式排除（YAGNI），后续可作为独立小改进接入各行组件。

## 状态归类与合规

- FAB 可见性、`LazyListState` 滚动协调属 **UI transient**（`#rule-runtime-state-classification`），用普通 `remember` / `rememberLazyListState`，**不入** RuntimeStore / SnapshotStore。
- 读当前曲目走 `StateFlow` + `collectAsStateWithLifecycle()`，**无** 主线程 `runBlocking`（player rules）。
- FAB 点击经 `core/ui/LoggedClickable.kt` 的 `logUiClick(targetId, screen)` 埋点；`targetId` 形如 `playlist_detail.fab.scroll_to_current`（`#rule-user-action-logging`）。
- 走 `MusicFreeScreenScaffold` 既有 `floatingActionButton` 槽位，不手写 `Box` 绝对定位浮层（`#rule-no-raw-material3-topappbar` 隐含）。非特殊 Chrome 页，无需登记；非 `FidelityAnchors` 必需项（如写位置/对比测试再加 anchor）。

## 测试与验收

- **单测（`:core`）**：`currentTrackIndex` —— 命中、`current == null`、不在列表、重复项取首个。
- **Compose 测试**：`LocateCurrentTrackFab` —— 当前曲目在列表时显示、`null`/不在列表时隐藏、点击触发滚动（断言 `listState.firstVisibleItemIndex` 变化）。
- **目标模块单测**：`:core:testDebugUnitTest`、`:feature:home:testDebugUnitTest`。
- **构建闸门**：`bash scripts/dev-harness/check.sh`、`./gradlew :app:assembleDebug --no-daemon`。
- **运行态验收**：装 Debug 包，播放一首歌，依次进 4 个详情页：当前曲目在列表时 FAB 出现、点击顶端对齐定位；当前曲目不在该列表时 FAB 隐藏；确认 FAB 不与 MiniPlayer 重叠。

## 参考

- RN：`../MusicFree/src/components/musicList/index.tsx`、`../MusicFree/src/core/trackPlayer/index.ts`（`useCurrentMusic`）、`../MusicFree/src/utils/mediaUtils.ts`（`isSameMediaItem`）。
- Android：`feature/home/.../playlist/PlaylistDetailScreen.kt`、`.../pluginsheet/PluginSheetDetailScreen.kt`、`.../toplist/TopListDetailScreen.kt`、`.../albumdetail/AlbumDetailScreen.kt`、`player/.../controller/PlayerController.kt`、`player/.../model/PlayerState.kt`、`core/.../ui/MusicFreeScreenChrome.kt`、`core/.../ui/LoggedClickable.kt`、`core/.../theme/Rpx.kt`、`app/.../MainActivity.kt`。
