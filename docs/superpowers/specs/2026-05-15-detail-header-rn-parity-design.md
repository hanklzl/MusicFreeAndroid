---
title: 榜单 / 插件歌单详情页 顶部 Header 与 PlayAllBar RN 对齐设计
status: 当前规范（设计）
applies_to: feature/home – topListDetail / pluginSheetDetail
authors: AI 自动化（Claude Opus 4.7）
date: 2026-05-15
related:
  - ../../../AGENTS.md
  - ../../dev-harness/ui/rules.md
  - ../../../../MusicFree/src/components/musicSheetPage/
  - ../../../core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicFreeScreenChrome.kt
  - ../../../feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt
  - ../../../feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt
---

# 1. 背景

`榜单详情` (`TopListDetailScreen`) 与 `插件歌单详情` (`PluginSheetDetailScreen`) 当前只渲染了曲目列表，缺少 RN 原版 `MusicSheetPage` 顶部的封面 + 标题 + 描述 + PlayAllBar 区域。

参考 RN 实现：

- `../MusicFree/src/components/musicSheetPage/index.tsx` 统一组合
- `../MusicFree/src/components/musicSheetPage/components/header.tsx` 顶部信息块
- `../MusicFree/src/components/base/playAllBar.tsx` 操作行
- `../MusicFree/src/pages/topListDetail/index.tsx` 与 `pluginSheetDetail/index.tsx` 复用 `MusicSheetPage`

参考截图（用户提供，2026-05-15）：

- 飙升榜：cover + 标题 + `共100首` + `刚刚更新` 描述 + `播放全部` + 添加到歌单 + 编辑
- KPOP 韩团超燃歌单：cover + 标题 + `共40首` + 多行描述 + `播放全部` + 收藏 + 添加到歌单 + 编辑

# 2. 目标

1. 在两类详情页（`TopListDetailScreen` / `PluginSheetDetailScreen`）的曲目列表上方渲染统一的 Sheet Header，可视信息与 RN 一致。
2. 抽出共享 Compose 组件 `MusicSheetPageHeader` + `PlayAllBar`，所有当前 / 未来基于 `MusicSheetItemBase` 的详情页复用。
3. PlayAllBar 中的操作按钮按 RN 顺序排列；可以可靠工作的按钮提供完整逻辑闭环；非本期范围的按钮显式登记为 known-gap，不放进 UI（不留死按钮）。
4. 同步对齐 `TopListDetailScreen` 行布局（插件 tag + 更多菜单），让两个详情页的列表行风格一致，沿用现有 `PluginSheetMusicRow` 的 UX。
5. 不重写 ViewModel 既有 loading / paging / 日志逻辑，仅新增最小的 batch add / play-all 接口。

# 3. 非目标 / Known Gap

| 项 | 状态 | 原因 |
| --- | --- | --- |
| AppBar 中的 `搜索此列表` 入口（RN navBar magnifying-glass） | 不实现 | `SearchMusicListRoute.transient` 链路尚未接入插件歌单的曲目供给，超出本期 |
| PlayAllBar 中的 `批量编辑` 按钮（RN pencil-square） | **不在 UI 中渲染** | 当前 `MusicListEditorLiteRoute` 仅支持 `playlist` / `local-library`；transient 编辑器超出本期 |
| AppBar overflow 菜单（RN navBar 的菜单条目） | 不实现 | 与上一条一致；详细动作需 transient 编辑器 |
| 自定义封面渐变 / 默认占位封面差异化 | 不实现 | `CoverImage` 已有占位 fallback；本期复用现状 |

# 4. 范围

- 模块：`:core:ui` 新增组件；`:feature:home` 修改 `topListDetail` / `pluginSheetDetail`；`:data` 无需新增 API（已具备 `addMusicsToPlaylist`）。
- 不修改：`:app`、Routes、Navigation、插件桥、播放器。

# 5. 设计

## 5.1 共享组件

### 5.1.1 `MusicSheetPageHeader`（新增 `core/ui/MusicSheetPageHeader.kt`）

签名：

```kotlin
@Composable
fun MusicSheetPageHeader(
    cover: String?,          // artwork ?: coverImg
    title: String?,
    worksNum: Int?,          // null 时 fallback 到 musicListSize
    musicListSize: Int,
    description: String?,    // null/blank 时不渲染描述区
    actions: @Composable () -> Unit, // 内嵌 PlayAllBar 等
    modifier: Modifier = Modifier,
)
```

布局对齐 RN `header.tsx`：

- 外层背景：`MusicFreeTheme.colors.card`
- 主区 padding：`rpx(24)`
- Cover：`rpx(210)` × `rpx(210)`，圆角 `rpx(24)`，使用 `CoverImage(uri, size, cornerRadius)`
- 详情 Column 高度：`rpx(140)`，左 padding `rpx(36)`，顶/底 `SpaceBetween`：上行标题（最多 3 行），下行 `共 N 首`
- 描述区（可选）：宽度撑满，`marginTop = rpx(28)`，默认 6 行 + Pressable 切换展开（与 RN 一致）
- `actions` 槽位渲染在主区下方（即 PlayAllBar 的位置），由调用方提供

行为：

- description 文本展开/收起：使用 `remember { mutableStateOf(true) }` 控制 `maxLines`；`true → 6`，`false → Int.MAX_VALUE`
- `worksNum ?: musicListSize` 决定 `共 X 首` 中的 X；都为 0 时显示 `共 - 首`，对齐 RN `"-"` fallback
- 不提供 onClick / 不暴露内部 state，保持纯展示组件

### 5.1.2 `PlayAllBar`（新增 `core/ui/PlayAllBar.kt`）

签名：

```kotlin
@Composable
fun PlayAllBar(
    onPlayAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    starred: Boolean? = null,           // null = 不显示收藏按钮（topList）
    onToggleStarred: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

布局对齐 RN `playAllBar.tsx`：

- 行高：`rpx(84)`，水平 padding `rpx(24)`，垂直居中
- 左侧 Pressable：`ic_play_circle` icon（`IconSizes.normal`）+ `Spacer(rpx(12))` + `Text("播放全部", bold)`，`weight(1f)`
- 右侧依次为：
  1. 收藏：当 `starred != null` 时渲染 `IconButton`，红心填充 / 描边切换；颜色：`#e31639`（红心）/`MusicFreeTheme.colors.text`（描边）
  2. 添加到歌单：`IconButton(ic_folder_plus)`
- 按钮间距：`marginLeft = rpx(36)`（对齐 RN `optionButton`）
- `pencil-square` 编辑按钮不渲染（known-gap）

行为：

- 所有 onClick 通过 lambda 上抛
- 不在组件内做任何业务判断（list 为空时仍可点击，由 ViewModel 决定 noop）

## 5.2 共享行组件复用

将 `feature/home/pluginsheet/PluginSheetDetailScreen.kt` 中的 `PluginSheetMusicRow` 提升为模块内共享：

- 改名：`PluginMusicRow`
- 物理位置：`feature/home/component/PluginMusicRow.kt`
- 公开签名：`index`, `item`, `isFavorite`, `onClick`, `onLongClick`, `onAction` —— 与现有一致
- 同步提取 `pluginSheetPlatformTagText` / `pluginSheetDescription` 为 `pluginPlatformTagText` / `pluginRowDescription`

`TopListDetailScreen` 与 `PluginSheetDetailScreen` 均改用 `PluginMusicRow`。

> 行内现状：`PluginSheetDetailScreen` 行已含插件 tag + 更多菜单；`TopListDetailScreen` 行仅有 `详情` TextButton，本次替换为 `PluginMusicRow`，去掉 `onOpenMusicDetail` 入参（菜单内通过 ViewModel + 操作触发，与 PluginSheet 行为一致）。

> 注：复用 `PluginSheetDetailViewModel` 的 favorite/toggle 接口语义；`TopListDetailViewModel` 需要对应能力扩展（见 5.4）。

## 5.3 Screen 集成

### 5.3.1 `TopListDetailScreen`

- `MusicFreeScreenScaffold(title = uiState.title, onBack = ...)` 顶栏不变
- 内容区 `LazyColumn`：
  - `item { MusicSheetPageHeader(... actions = { PlayAllBar(starred = null, ...) }) }`
  - `itemsIndexed(uiState.musicList) { i, item -> PluginMusicRow(...) }`
  - 末尾保留现有 paging footer
- `loading / error` 状态先于 LazyColumn 渲染时不展示 header（与 RN 行为一致：列表加载完成前没有 sheetInfo 不渲染 header）；空列表（已加载完且 0 条）仍展示 header

### 5.3.2 `PluginSheetDetailScreen`

- 同上结构，区别：
  - PlayAllBar 传入 `starred = isSheetStarred`，`onToggleStarred = viewModel::toggleSheetStarred`
  - AppBar `actions` 中**移除**心形按钮（已经迁到 PlayAllBar；保持 AppBar 简洁，对齐 RN navBar 只放搜索/菜单的设计）
- 现有的 `AddToPlaylistBottomSheet` + `CreatePlaylistDialog` 流程保留；新增的 batch-add 复用同一个 `_sheetState`

## 5.4 ViewModel 增量

### 5.4.1 `PluginSheetDetailViewModel`

- 新增 `fun playAll()`：等价于 `playAt(0)`，但日志事件 `plugin_sheet_play_all_*`
- 新增 `fun showBatchAddToPlaylistSheet()`：`_sheetState.value = AddToPlaylistSheetState.batch(_uiState.value.musicList)`
- 改造 `fun addPendingToPlaylist(targetPlaylistId: String)`：
  - 当 `pendingItems.size > 1` 走 `playlistRepository.addMusicsToPlaylist(...)`，否则保留 `addMusicToPlaylist(...)` 单条路径（向后兼容现有单条调用）
- 改造 `fun createPlaylistAndAddPending(name)`：同上，`addMusicsToPlaylist` 走批量
- 日志事件复用现有 `plugin_sheet_add_to_playlist` / `plugin_sheet_create_playlist` 前缀，`fields` 中加 `itemCount`、`added`、`skipped`

### 5.4.2 `TopListDetailViewModel`

- 注入新依赖：`PlaylistRepository` + 现有 `MusicSheetItemBase` 收藏不在范围（topList 不可收藏）；插件歌单的 favorite Flow 复用 `PlaylistRepository.isFavorite`
- 暴露 `val sheetState: StateFlow<AddToPlaylistSheetState>` + `val allPlaylists: StateFlow<List<Playlist>>` + `fun isFavoriteFlow(item)` + `fun toggleFavorite(item)` + `fun showAddToPlaylistSheet(item)` / `showBatchAddToPlaylistSheet()` / `hideAddToPlaylistSheet()` / `addPendingToPlaylist(targetId)` / `createPlaylistAndAddPending(name)` / `fun playAll()`
- 这些与 `PluginSheetDetailViewModel` 同语义；可考虑提取 mixin / 共用 helper，但本期为了控制 churn 直接复制，未来再合并
- 日志事件命名按 `top_list_detail_*` 前缀

## 5.5 状态机与边界

| 状态 | 渲染 |
| --- | --- |
| `loading = true` 且 `musicList.isEmpty()` | 中心 `CircularProgressIndicator`，不显示 header |
| `errorMessage != null` 且 `musicList.isEmpty()` | 居中错误 + 重试按钮，不显示 header |
| `musicList.isEmpty()` 且 `!loading` 且 `errorMessage == null` | 显示 header + 空列表占位（沿用 RN：仅显示 header） |
| 正常状态 | header + 行 + paging footer |

## 5.6 主题与尺寸

- 所有间距使用 `rpx(...)`，按 RN 值一比一对应
- 文本颜色：`MusicFreeTheme.colors.text` / `textSecondary`，与 `MusicFreeTheme.colors.danger`（错误状态）
- 字号：标题使用 `FontSizes.content`，描述使用 `FontSizes.description`
- 红心填充色按 RN `#e31639` 硬编码（与 RN 一致；后续如有 danger token 可替换）

# 6. 验证

## 6.1 静态

- `./gradlew :core:assembleDebug :feature:home:assembleDebug :app:assembleDebug` 通过
- `./gradlew :feature:home:testDebugUnitTest :core:testDebugUnitTest` 通过
- 若现有 `PluginSheetDetailViewModelTest` / `TopListDetailViewModelTest` 存在，相关用例同步更新

## 6.2 行为单测（新增/扩展）

- `MusicSheetPageHeaderTest`（如果项目已有 Compose UI 测试基建则用 Robolectric / `createComposeRule`；否则降级为快照对比 + 仅做 state-mapping 测试）
- `PluginSheetDetailViewModelTest`：补两条用例
  - `showBatchAddToPlaylistSheet → addPendingToPlaylist → 调用 addMusicsToPlaylist 且 sheet hide`
  - `playAll → 在非空列表上调用 playerController.playQueue/playItem`
- `TopListDetailViewModelTest`：同上结构

## 6.3 运行态

- 启动模拟器，构建 `:app:assembleDebug` 并 install
- 走链路：首页 → 榜单 → 任意榜单 → 应看到顶部 cover + title + 共 N 首 + （可选描述）+ PlayAllBar
- 走链路：首页 → 推荐歌单 → 任意歌单 → 应看到上述 header；心形按钮可点击且与列表里收藏状态同步
- 操作验证：
  - 点击 PlayAllBar `播放全部` → 从第一首开始播放
  - 点击 add-to-playlist icon → 弹出 batch sheet（含创建新歌单）→ 选择已有歌单 → toast 成功 + 数据库写入
  - PluginSheet 中点击 heart → 切换收藏状态，下次进入保持

# 7. 风险与回滚

- 行组件提升到 `feature/home/component/`：旧位置 `PluginSheetMusicRow` 引用全部替换，文件移动 + 改名；如果担心 binary compat（库内私有 API），无需考虑（feature 模块内部）
- ViewModel 新接口：保持向后兼容（旧的单条 `addPendingToPlaylist` 仍可工作）
- 回滚策略：单 commit squash，回滚直接 `git revert <sha>`

# 8. 任务拆分（写入 plan 文档）

详见 `docs/superpowers/plans/2026-05-15-detail-header-rn-parity-plan.md`。
