# 收藏专辑与首页收藏 Tab 取消收藏设计

> 文档状态：当前规范
> 适用范围：`:data` 收藏歌单 / 专辑统一存储、`:feature:home` 专辑详情收藏入口、首页“收藏歌单” tab 删除按钮、Starred 行点击导航分流。
> 直接执行：是（用户已授权 spec → plan → implement 直接执行）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：
> - `../../../../MusicFree/src/core/musicSheet/index.ts`（`starMusicSheet` / `unstarMusicSheet`）
> - `../../../../MusicFree/src/pages/home/components/homeBody/sheets.tsx`（首页收藏列表、trash 取消收藏弹窗）
> - `../../../../MusicFree/src/components/base/playAllBar.tsx`（`canStar` heart 按钮）
> - `../../../../MusicFree/src/pages/albumDetail/index.tsx`（RN 原版专辑详情，未启用 `canStar`）
> 上游 spec：[2026-05-10-favorite-starred-playlists-design](2026-05-10-favorite-starred-playlists-design.md)
> Dev Harness：UI 规则见 [ui/rules.md](../../dev-harness/ui/rules.md)，插件规则见 [plugin/rules.md](../../dev-harness/plugin/rules.md)，测试规则见 [test/rules.md](../../dev-harness/test/rules.md)
> 最后校验：2026-05-10

## 背景

上游 spec（2026-05-10-favorite-starred-playlists-design）已完成插件 **歌单** 收藏端到端：
`StarredSheetEntity / StarredSheetDao / StarredSheetRepository` + `PluginSheetDetailScreen` heart 按钮 +
首页“我的收藏” tab 真实数据。

本期补齐用户提出的剩余缺口：

1. **专辑无法收藏**：搜索“专辑”tab → 点开 `AlbumDetailScreen`，header 无 heart 按钮；`AlbumDetailViewModel` 也没有星标状态观察；首页“我的收藏” tab 完全看不到任何收藏的专辑。
2. **首页“我的收藏” tab 的 trash 图标是装饰性的**：`HomeSheetsList.kt:133-140` 渲染了 `ic_home_trash_outline`，但 `Icon` 没有 `onClick`/`Modifier.clickable`。点击没有任何反应，无法在首页直接取消收藏。
3. **Starred 行点击始终走 `PluginSheetDetailRoute`**：当后续专辑收藏落地，点击专辑 row 也会被错误地送进“歌单详情”，加载会失败。

RN 原版 `albumDetail` 不启用 `canStar`，专辑收藏不在原版行为范围。本期扩展时保持 **完全复用 RN 已有的 “heart in detail header + Starred tab + trash 弹窗”** 视觉/交互范式，不引入新的 UI 模式。

## 目标

1. 用户可在 `AlbumDetailScreen` 顶栏点击 heart 收藏 / 取消收藏当前专辑，状态与数据库双向同步。
2. 收藏的专辑出现在首页“我的收藏” tab，与收藏歌单同列同样式，使用插件平台 tag 区分来源。
3. 首页“我的收藏” tab 中点击 trash 图标弹出确认弹窗，确认后调用 `unstar` 从数据库删除该收藏（歌单或专辑同样适用）。
4. 点击“我的收藏” tab 的 row：歌单类型 → `PluginSheetDetailRoute`，专辑类型 → `AlbumDetailRoute`，分别带回原 seed。
5. 当前已有插件歌单收藏路径不被破坏；上游 spec 中所有测试和数据库行为继续通过。

## 非目标

- 不在搜索结果列表上长按或“一键收藏”；保持与 RN 一致，必须进入详情页才能收藏。
- 不实现批量取消、拖拽排序、收藏分组、收藏导出。
- 不改变首页“我的收藏” tab 的视觉布局（cover + 标题 + 副标题 + 平台 tag + trash 图标），仅补齐交互。
- 不改变 `:feature:search` 现有结果点击导航；专辑 row 已经能进 `AlbumDetailRoute`，本期只在详情页内补 heart。
- 不为本地（`platform == "local"`）歌单提供任何收藏入口。
- 不新增任何 Room migration 对象；按 `~/.claude/.../memory/db-schema-during-dev.md` 约定，开发期直接改 entity + bump version + destructive fallback。

## 方案取舍

**最终方案：单表 `kind` 字段统一存储 + 详情页 heart + 首页 trash 弹窗**。

`StarredSheetEntity` 增加 `kind: String`（`"sheet"` | `"album"`），保留主键 `(id, platform)`。歌单与专辑共用同一张表、同一组 DAO、同一个 repository，但首页 row 可以根据 `kind` 决定导航目标。版本号 `6 → 7`，沿用 `fallbackToDestructiveMigration`。

备选方案 A：新建 `StarredAlbumEntity / StarredAlbumDao / StarredAlbumRepository`，与 sheet 完全平行。优点是无 schema 变更，缺点是首页“我的收藏” tab 必须 `combine` 两条 Flow，HomeSheetUiModel 需要从两个源派生，首页 trash 也要分两条删除路径。同样的字段在两张表里维护两份 mapper、两份测试、两份 androidTest。本期 RN 原版本就只有一个 starred 列表，单表分流更贴近 RN 心智模型。

备选方案 B：只为专辑加 heart，但收藏存到现有 `starred_sheets` 表，并约定“platform 是 album 的就是专辑”。这是隐式协议，无法用类型系统保证；首页 row 也无法直接得知应当走 sheet 详情还是 album 详情。会埋下脏数据风险。

备选方案 C：在首页加“专辑”单独的 tab。RN 原版“我的收藏”tab 是单一列表（仅含远程歌单），用户明确要求“UI 对齐原版”。新增 tab 与原版偏离，被否决。

## 数据设计

### `StarredSheetEntity`

新增 `kind`，默认 `"sheet"`：

```kotlin
@Entity(tableName = "starred_sheets", primaryKeys = ["id", "platform"])
data class StarredSheetEntity(
    val id: String,
    val platform: String,
    val kind: String = StarredKind.SHEET, // "sheet" | "album"
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val rawJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
```

`StarredKind` 常量放在 `:core/model`：

```kotlin
object StarredKind {
    const val SHEET = "sheet"
    const val ALBUM = "album"
}
```

`AppDatabase` 版本由 6 升至 7，destructive fallback 已在 `DataModule` 中开启（按上游开发期约定）。如未开启，本期连同 `DataModule` 一并加上。

### `StarredSheet` 域模型

加 `kind: String`，默认 `StarredKind.SHEET`：

```kotlin
data class StarredSheet(
    val id: String,
    val platform: String,
    val kind: String = StarredKind.SHEET,
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

`StarredSheetMapper` 同步 round-trip `kind`。`StarredSheetRepository.observeIsStarred(id, platform)` 不变（主键不含 kind，避免“同 id 同 platform 既是 sheet 又是 album”的歧义；同 (id, platform) 视作同一收藏）。`toggle` 与 `upsert` 也不变，写入时由调用方填好 kind。

### Plugin → StarredSheet 转换

新增 `:feature:home/albumdetail` 包内 `AlbumStarredMapper.kt`：

```kotlin
fun AlbumItemBase.toStarredSheet(): StarredSheet = StarredSheet(
    id = id, platform = platform, kind = StarredKind.ALBUM,
    title = title.orEmpty(), artist = artist,
    coverUri = artwork, sourceUrl = raw["sourceUrl"] as? String,
    description = description, artwork = artwork, worksNum = worksNum,
    raw = raw,
)
```

已存在的 `PluginSheetStarredMapper.kt` 加上 `kind = StarredKind.SHEET`。

## UI 与导航设计

### `AlbumDetailScreen`

`MusicFreeScreenScaffold` 增加 `actions` 槽，渲染 heart `IconButton`（与 `PluginSheetDetailScreen` 完全一致的图标 / 着色 / contentDescription 文案 “收藏专辑” / “取消收藏专辑”）。`AlbumDetailViewModel` 注入 `StarredSheetRepository`，暴露：

- `isAlbumStarred: StateFlow<Boolean> = starredSheetRepository.observeIsStarred(albumId, pluginPlatform).stateIn(...)`
- `fun toggleAlbumStarred()`：用 `currentAlbum ?: initialAlbumSeed` 通过新 mapper 转 `StarredSheet`，调用 `starredSheetRepository.toggle(...)`

详情未加载时的 `currentAlbum` 兜底：复用 `initialAlbumSeed`（已经从 route + `AlbumDetailSeedStore` 还原）。

### 首页“我的收藏” tab

#### Row 显示与导航

`HomeSheetUiModel` 增加 `kind: String`（默认 `StarredKind.SHEET`）。`fromStarredSheet` 写入 `sheet.kind`。`HomeSheetsList` 中点击 row：

```kotlin
when (item.kind) {
    StarredKind.ALBUM -> onOpenStarredAlbum(item)
    else              -> onOpenStarredSheet(item) // 兼容已有 sheet 路径
}
```

`HomeScreen` 把两个回调传入：
- `onOpenStarredSheet(uiModel)`：和现状一致 → `PluginSheetSeedStore.put(...)` + `PluginSheetDetailRoute(...)`
- `onOpenStarredAlbum(uiModel)`：新增 → `AlbumDetailSeedStore.put(...)` + `AlbumDetailRoute(...)`

需要新增 `HomeSheetUiModel.toAlbumItemBase()` 扩展方法（位置与 `toMusicSheetItemBase()` 同文件）。

#### Trash 取消收藏

`HomeSheetsList` 中 trash 图标改造：

- 改 `Modifier.size(rpx(42))` → `Modifier.size(rpx(42)).clickable { onTrashClick(item) }`，并把 `onTrashClick` 透传到 `homeSheetsList(...)` 入参。
- `HomeScreen` 在 `homeSheetsList` 调用处提供 `onTrashClick = { showUnstarConfirm = it }`。
- 弹出确认对话框（`AlertDialog`）：标题“取消收藏”，正文 `"确定要取消收藏「${item.title}」吗？"`，确认 → `viewModel.unstar(item)`。

`HomeSheetsViewModel` 新增：

```kotlin
fun unstar(item: HomeSheetUiModel) {
    val platform = item.platform ?: return // Mine tab 不应触发
    viewModelScope.launch {
        starredSheetRepository.deleteByIdAndPlatform(id = item.id, platform = platform)
    }
}
```

Mine tab 的 trash 仍未与本期相关；保持现状（也未连任何动作），不在本期范围。

### 视觉规则

- heart 图标使用现有 `R.drawable.ic_heart` / `R.drawable.ic_heart_outline`，已收藏 tint = `MusicFreeTheme.colors.primary`，未收藏 tint = `MusicFreeTheme.colors.appBarText`。
- `AlbumDetailScreen` 顶栏继续走 `MusicFreeScreenScaffold`，不直接组装 `TopAppBar`，符合 [ui/rules.md](../../dev-harness/ui/rules.md) AppBar 强约束。
- `AlertDialog` 使用 Material3 默认实现，文字色 `MusicFreeTheme.colors.text` / `textSecondary`。

## 错误处理

- 详情未加载完成时点击 heart：使用 `currentAlbum ?: initialAlbumSeed` 写入；后续详情成功后再次点击会刷新 `updatedAt` 与 raw（与歌单路径一致）。
- 插件已卸载时点击首页收藏行：导航不阻塞；目标 `AlbumDetailScreen` / `PluginSheetDetailScreen` 维持现有“插件不存在：…”错误页。
- Starred row 缺失 `platform`：理论不可能（DB 约束），但 `unstar` 仍 early-return，避免 NPE。
- 弹窗确认前 navigate 离开：弹窗与 row 弱耦合，dispose 时自动隐藏，无残留。

## 日志

按 [AGENTS.md 日志规范](../../../AGENTS.md#日志记录规范) 与本仓库现有 `MfLogger` 用法补：

- `starred_added`：fields `kind`、`platform`、`id`、`title`、`source`（`detail_album` / `detail_sheet` / 其他）
- `starred_removed`：fields `kind`、`platform`、`id`、`source`（`detail_album` / `detail_sheet` / `home_starred_trash`）
- `starred_unstar_confirm_shown`：fields `kind`、`platform`、`id`

事件名用稳定 snake_case，不放 UI 文案。三处入口（两个详情 toggle、一个首页弹窗）都至少打一个 event。

## 测试策略

### 单元测试

- `StarredSheetMapperJvmTest`（新增）：`StarredSheet ↔ StarredSheetEntity` round-trip 包括 `kind`，覆盖 `"sheet"` / `"album"` 两条路径，验证默认值兜底。
- `StarredSheetRepositoryJvmTest`（已存在，扩展）：toggle 一个 `kind = "album"` 的 `StarredSheet` 后再 toggle 一个 `kind = "sheet"` 但同 `(id, platform)` 的，应被视为同一记录（覆盖文档明确语义）。
- `AlbumDetailViewModelTest`（新增）：用 fake `StarredSheetRepository` + fake `PluginManager`，验证：
    1. `isAlbumStarred` 初始 false；调用 `toggleAlbumStarred()` 后 repository 收到 `kind = ALBUM` 的 `StarredSheet`。
    2. 详情加载完成前点击 heart 使用 fallback seed。
    3. 重复点击 toggle 来回切换。
    遵守 [test/rules.md](../../dev-harness/test/rules.md) `MainDispatcherRule + runTest + advanceUntilIdle`。
- `HomeSheetsViewModelTest`（已存在，扩展）：starred 列表混合 sheet 与 album；调用 `unstar(albumRow)` 后 repository 收到 `deleteByIdAndPlatform` 调用并以正确 `(id, platform)` 触发。
- `HomeSheetUiModelTest`（已存在，扩展）：`fromStarredSheet` 保留 `kind`；`toAlbumItemBase()` 输出与原 `AlbumItemBase` 字段对齐。

### 仪器测试

- `StarredSheetDaoTest`（已存在，扩展）：插入 album row → `observeAll` 包含；按 `(id, platform)` 删除生效；`observeExists(id, platform)` 与 kind 无关。每测试用例使用 unique DataStore/DB 路径（按 [test/rules.md](../../dev-harness/test/rules.md) #4）。
- `StarredSheetRepositoryTest`（已存在，扩展）：覆盖 album upsert + toggle + delete 端到端。

### 验收

- `./gradlew :data:testDebugUnitTest :feature:home:testDebugUnitTest`
- `./gradlew :data:connectedDebugAndroidTest`（设备/模拟器）
- `./gradlew :app:assembleDebug`
- `python3 scripts/dev-harness/grep-check.py`
- 运行态验证：搜索“某专辑” → 进入 `AlbumDetailScreen` → 点 heart → 返回首页“我的收藏”看到该专辑 row 与 platform tag → 点 trash → 弹窗确认 → row 消失。再次同样流程对“某歌单”重复（回归）。

## 风险与回滚

- DB 升至 v7，destructive fallback 会清空开发设备数据库。已是开发期约定；用户已知悉。
- `kind` 默认值 `"sheet"` 让所有遗留 starred 行表现等价于上游 spec。
- 若 album 详情字段缺失（部分插件不返回 description / artwork），row 会显示空副标题但功能不影响；这是插件返回数据问题，不在本期修复范围。
- 若发现专辑收藏导致首页 row 大量增长影响性能，可在后续 spec 引入分页或独立 tab；本期不预先优化。
