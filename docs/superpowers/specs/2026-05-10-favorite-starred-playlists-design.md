# 默认我喜欢与收藏歌单修复设计

> 文档状态：当前规范
> 适用范围：`:data` 默认 `我喜欢` 歌单兜底、远程插件歌单收藏数据层、`:feature:home` 首页歌单分区、插件歌单详情收藏入口。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/core/musicSheet/index.ts`、`../../../../MusicFree/src/pages/home/components/homeBody/sheets.tsx`、`../../../../MusicFree/src/components/base/playAllBar.tsx`
> Dev Harness：UI 规则见 [ui/rules.md](../../dev-harness/ui/rules.md)，测试规则见 [test/rules.md](../../dev-harness/test/rules.md)
> 最后校验：2026-05-10

## 背景

当前 Android 代码已经落地本地用户歌单、默认 `favorite` 概念和远程收藏歌单表，但最新实现仍有两个运行态缺口：

1. `SeedFavoriteCallback` 只在 Room `onCreate` 时插入 `favorite/我喜欢`。用户从已有库升级、历史测试库残留、或任何异常路径导致默认行缺失时，首页 `observeAllPlaylists()` 不会修复它，默认歌单会从“我的歌单”消失。
2. 远程收藏歌单只完成了 `StarredSheetRepository`、`StarredSheetEntity` 和少量测试；首页“收藏歌单”仍由 `HomeMockVisualFactory.STARRED_ROWS` mock 驱动，插件歌单详情没有 RN `PlayAllBar` 中的 heart 收藏入口，点首页收藏歌单也不能进入插件歌单详情。

RN 原版在 `MusicSheet.setup()` 中会修复默认歌单：没有 sheet 时创建 `_defaultSheet`，默认 sheet 不在第一个时移动到第一个。远程歌单收藏由 `starredMusicSheetsAtom` 保存完整歌单对象，首页“收藏歌单” tab 直接显示该列表，插件歌单详情通过 heart 在 `starMusicSheet` / `unstarMusicSheet` 间切换。

## 目标

1. 默认 `favorite/我喜欢` 行在新库、已有库、异常缺失库中都能自动恢复。
2. 首页“我的歌单”继续强制 `我喜欢` 置顶，不再因数据库缺行而显示为空或只显示用户歌单。
3. 插件歌单详情提供收藏 / 取消收藏 heart 入口，状态随数据库反向同步。
4. 首页“收藏歌单” tab 显示真实收藏歌单列表，数量来自数据库。
5. 点击首页收藏歌单进入 `PluginSheetDetailRoute`，并尽量保留 RN 的完整 sheet seed，减少插件详情二次加载缺上下文的风险。

## 非目标

- 不实现收藏歌单的批量管理、排序拖拽、搜索或导入导出。
- 不为本地 `我喜欢` 歌单新增额外收藏入口；单曲 heart 已由 `PlaylistRepository.toggleFavorite` 负责。
- 不改变插件搜索、推荐歌单、榜单的列表布局。
- 不新增 Room migration；本项目开发阶段仍按当前约定直接改 entity 并使用 destructive fallback。
- 不改播放器队列或下一首播放能力。

## 方案取舍

推荐方案：在数据层提供幂等兜底，再把远程收藏歌单贯通到首页和插件歌单详情。

备选方案 A 是只在 `SeedFavoriteCallback.onOpen` 里补默认行。这能修复大多数启动路径，但 Repository 单元或未来非 Hilt 构造路径仍可能绕过 callback，覆盖面不足。

备选方案 B 是只在 UI 层发现列表为空时伪造 `我喜欢` row。这会让首页看起来恢复，但详情页、单曲收藏和加入歌单底部弹窗仍可能写入不存在的 playlist，属于显示层掩盖数据错误。

最终采用“callback + Repository ensure”双兜底：Room 打开时修复库，Repository 观察和 favorite 写路径前再次 `INSERT OR IGNORE`。远程收藏采用已有 `starred_sheets` 表，补齐字段以保存插件 sheet seed，并用真实数据替换首页 mock。

## 数据设计

### 默认歌单

`SeedFavoriteCallback` 在 `onCreate` 和 `onOpen` 都执行：

```sql
INSERT OR IGNORE INTO playlists
    (id, name, coverUri, description, sortMode, createdAt, updatedAt)
VALUES ('favorite', '我喜欢', NULL, NULL, 'Manual', now, now)
```

`PlaylistDao` 增加 `insertPlaylistIgnore`。`PlaylistRepository` 增加私有 `ensureFavoritePlaylist()`，在这些入口调用：

- `observeAllPlaylists().onStart { ensureFavoritePlaylist() }`
- `observeFavorite().onStart { ensureFavoritePlaylist() }`
- `isFavorite(item).onStart { ensureFavoritePlaylist() }`
- `toggleFavorite(item)` 写入前

这样既能修复首页，也能修复直接收藏单曲时默认歌单缺失的问题。

### 远程收藏歌单

`StarredSheet` 保持当前核心身份字段，并扩展可选 seed 字段：

```kotlin
data class StarredSheet(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val raw: Map<String, Any?> = emptyMap(),
)
```

`StarredSheetEntity` 同步增加 `description`、`artwork`、`worksNum`、`rawJson`。`rawJson` 使用 `Converters` 中公开的 raw map JSON helpers 做转换。`StarredSheetRepository` 增加：

- `observeIsStarred(id, platform): Flow<Boolean>`
- `toggle(sheet: StarredSheet)`

`upsert` 沿用 RN 语义：同一 `(id, platform)` 再收藏时刷新内容和 `updatedAt`，保留原 `createdAt`。

## UI 与导航设计

### 首页

`HomeViewModel` 注入 `StarredSheetRepository`，暴露：

- `playlists: StateFlow<List<Playlist>>`，继续 `我喜欢` 置顶。
- `starredSheets: StateFlow<List<StarredSheet>>`，来自真实 repository。

`HomeScreen` 将真实收藏歌单映射为 `HomeSheetUiModel.fromStarredSheet`，`buildHomeVisualUiModel` 改为接收 `starredRows` 参数，不再使用 `STARRED_ROWS` mock。`HomePlaylistSectionUiModel.starredCount` 来自真实收藏数量。

点击“我的歌单”仍进入 `PlaylistDetailRoute`。点击“收藏歌单”进入 `PluginSheetDetailRoute`，并通过 `PluginSheetSeedStore.put(...)` 传入 `MusicSheetItemBase` seed。

### 插件歌单详情

`PluginSheetDetailViewModel` 注入 `StarredSheetRepository`，用路由 `(sheetId, pluginPlatform)` 观察收藏状态，并提供：

- `isSheetStarred: StateFlow<Boolean>`
- `toggleSheetStarred()`

`toggleSheetStarred()` 使用当前加载到的 `currentSheet`；若详情尚未加载完成，则使用路由 fallback seed。映射到 `StarredSheet` 时保留 `description`、`coverImg`、`artwork`、`worksNum` 和 `raw`。

`PluginSheetDetailScreen` 在 `MusicFreeScreenScaffold(actions = ...)` 中加入 heart `IconButton`。已收藏用 `ic_heart` 并染为 `MusicFreeTheme.colors.primary`，未收藏用 `ic_heart_outline`。普通 AppBar 仍走 `MusicFreeScreenScaffold`，符合 UI Harness。

## 错误处理

- 默认歌单补种使用 `INSERT OR IGNORE`，不会覆盖用户已有的 `我喜欢` 名称、封面、歌曲数量或更新时间。
- `toggleFavorite(item)` 若默认歌单缺失，先补种再插入 cross-ref，避免外键失败。
- 远程收藏歌单缺少 `raw` 时仍可通过 route 字段进入详情；`raw` 只是增强 seed 完整度。
- 插件歌单详情尚未加载完成时点击 heart，会收藏 fallback seed；详情加载成功后再次收藏会刷新记录。

## 测试策略

- `PlaylistRepositoryTest`：验证 `observeAllPlaylists()` 在缺失默认行的新库中自动发出 `favorite/我喜欢`，并验证 `toggleFavorite()` 能在默认行缺失时先恢复再收藏单曲。
- `StarredSheetRepositoryTest`：验证 `toggle` 可收藏 / 取消收藏；验证扩展字段与 raw 能 round-trip；验证 `observeIsStarred` 随 upsert/delete 发射。
- `HomeMockVisualFactoryTest`：改为真实 `starredRows` 输入，验证收藏 tab 不再使用 mock、数量来自传入数据。
- `HomeSheetUiModelTest`：验证 starred row 保留插件详情导航所需字段。
- `PluginSheetStarredMapperTest`：验证 `MusicSheetItemBase` 与 `StarredSheet` 双向映射保留 id/platform/title/artist/description/cover/artwork/worksNum/raw。
- 编译与回归：`./gradlew :data:testDebugUnitTest :feature:home:testDebugUnitTest`，最终再跑 `./gradlew :app:assembleDebug` 与 `python3 scripts/dev-harness/grep-check.py`。
