# 搜索结果点击链路 RN 对齐设计

> 文档状态：当前规范
> 适用范围：仅适用于主搜索页单曲、专辑、歌手、歌单搜索结果的数据解析、展示与点击跳转。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/pages/searchPage/`、`../../../../MusicFree/src/components/mediaItem/albumItem.tsx`、`../../../../MusicFree/src/components/mediaItem/sheetItem.tsx`、`../../../../MusicFree/src/core/router/index.ts`
> Dev Harness：UI 规则见 [ui/rules](../../dev-harness/ui/rules.md)，插件规则见 [plugin/rules](../../dev-harness/plugin/rules.md)，测试规则见 [test/rules](../../dev-harness/test/rules.md)
> 最后校验：2026-05-10

## 背景

RN 搜索页按媒体类型渲染不同结果组件：

- `music`：点击后播放歌曲，按配置决定是否替换播放队列。
- `album`：点击 `AlbumItem`，导航到 `album-detail`，传完整 `albumItem`。
- `artist`：点击 `ArtistResultItem`，导航到 `artist-detail`，传完整 `artistItem` 与插件标识。
- `sheet`：点击 `SheetItem`，导航到 `plugin-sheet-detail`，传完整 `sheetInfo`。

Android 当前 `PluginApi.search()` 的结果统一解析为 `List<MusicItem>`，`SearchScreen` 也把所有 tab 的点击都接到 `resolveAndPlay()`。因此单曲可用，但专辑、歌手、歌单搜索结果会被当成歌曲播放，且丢失 RN 详情页需要的原始对象字段。

## 目标

1. `PluginApi.search(query, page, type)` 按 `type` 返回匹配的强类型搜索项：歌曲、专辑、歌手、歌单。
2. 搜索页 tab 切换时按当前媒体类型过滤可搜索插件，对齐 RN `PluginManager.getSortedSearchablePlugins(type)`。
3. 单曲点击保持现有播放能力和共享音源解析。
4. 专辑点击导航到 `AlbumDetailRoute`，传列表项轻字段与一次性 seed，详情页优先使用完整 `AlbumItemBase`。
5. 歌手点击导航到 `ArtistDetailRoute`，传列表项轻字段与一次性 seed，详情页优先使用完整 `ArtistItemBase`。
6. 歌单点击导航到 `PluginSheetDetailRoute`，复用现有 `PluginSheetSeedStore`，详情页使用完整 `MusicSheetItemBase`。
7. 保持 `SearchRoute` 特殊 chrome、自动聚焦、状态栏策略不变。

## 非目标

- 不重做搜索页视觉 fidelity、搜索历史、歌词搜索或 RN 配置项 UI。
- 不改变 JS 插件协议，不要求插件修改返回结构。
- 不把 `raw` 任意字段写入 Navigation route；完整对象只通过进程内一次性 seed 传递。
- 不新增数据库字段或 Room migration。
- 不做 Release 构建验收；普通功能收尾以 Debug 构建为闸门。

## 设计

### 搜索结果模型

在 `:plugin` 中把 `SearchResult.data` 从 `List<MusicItem>` 改为 `List<PluginSearchItem>`：

- `PluginSearchItem.Music(MusicItem)`
- `PluginSearchItem.Album(AlbumItemBase)`
- `PluginSearchItem.Artist(ArtistItemBase)`
- `PluginSearchItem.Sheet(MusicSheetItemBase)`

`JsBridge.parseSearchResult(...)` 增加 `type` 参数，并按 `music`、`album`、`artist`、`sheet` 分别调用现有转换函数。未知类型返回空列表，避免把非歌曲误解析为歌曲。

### 插件能力过滤

`PluginManager.getSearchablePlugins()` 增加可选 `type: String = "music"` 参数。实现仍基于 `getSortedEnabledPlugins()`，只保留 `info.supportsSearchType(type)` 的插件。`SearchViewModel` 继续持有一个插件 tab 列表，但该列表改为当前 `selectedMediaType` 对应的插件列表。

切换媒体类型后：

1. 重新计算可用插件列表。
2. 如果当前平台不支持该类型，自动选中第一个支持插件。
3. 当前 query 非空且该类型未搜索过时，按新类型搜索。

### UI 与点击

`SearchScreen` 的结果列表按 `PluginSearchItem` 渲染：

- 歌曲：保留现有 `MusicResultItem` 和长按菜单。
- 专辑：显示封面、标题、平台 tag、副文案 `artist + date`，点击发出 `AlbumItemBase`。
- 歌手：显示头像、名称、平台 tag、副文案优先 `description`，再 `worksNum`。
- 歌单：显示三列封面网格，标题最多两行，点击发出 `MusicSheetItemBase`。

列表 key 使用媒体类型、平台与 id 组合，避免不同媒体类型 id 冲突。

### 详情 seed

新增两个一次性 seed store：

- `AlbumDetailSeedStore`
- `ArtistDetailSeedStore`

`AppNavHost` 在搜索点击回调中写入 seed，并导航到对应 route。`AlbumDetailViewModel` / `ArtistDetailViewModel` 优先 `take(route.seedToken)`，取不到时用 route 轻字段构造 fallback seed。`PluginSheetDetailRoute` 继续使用现有 `PluginSheetSeedStore`。

`AlbumDetailRoute` 增加 `date`、`description`、`worksNum`、`seedToken`。`ArtistDetailRoute` 增加 `description`、`fans`、`worksNum`、`seedToken`。这些是普通 typed route 字段，不依赖运行时反射类名协议，不新增 R8 keep。

## 错误处理

- 当前类型无支持插件：进入 `NO_PLUGIN`，文案继续使用现有无插件空态。
- seed token 失效：详情页用 route fallback，不崩溃。
- 搜索项 id 为空：仍展示结果，但导航 route 使用空 id 可能导致详情失败；实现时用字段兜底生成 route，保持插件错误显式暴露。
- 插件搜索失败：保持当前 per-plugin error state。

## 测试计划

1. `JsBridgeTest` 覆盖 album / artist / sheet 搜索结果解析与 fallback platform。
2. `SearchViewModelTest` 覆盖按媒体类型过滤插件、非歌曲搜索结果保留强类型、分页追加同类型数据。
3. 新增 seed store 纯单测，验证 `put` 后只能 `take` 一次。
4. `RoutesTest` 或现有 route contract 覆盖新增 route 字段可序列化。
5. 构建验证：
   - `./gradlew :plugin:testDebugUnitTest --no-daemon`
   - `./gradlew :feature:search:testDebugUnitTest --no-daemon`
   - `./gradlew :feature:home:testDebugUnitTest --no-daemon`
   - `./gradlew :app:testDebugUnitTest --no-daemon`
   - `./gradlew :app:assembleDebug --no-daemon`
   - `python3 scripts/dev-harness/grep-check.py`

## 验收标准

1. 搜索页单曲点击仍能播放。
2. 搜索页专辑点击进入专辑详情，并用点击项作为 `getAlbumInfo()` seed。
3. 搜索页歌手点击进入歌手详情，并用点击项作为 `getArtistWorks()` seed。
4. 搜索页歌单点击进入插件歌单详情，并用点击项作为 `getMusicSheetInfo()` seed。
5. 切到专辑、歌手、歌单 tab 时只展示支持对应搜索类型的插件。
6. 相关单测与 Debug 构建通过；无法运行的运行态验收必须在最终说明中明确记录。
