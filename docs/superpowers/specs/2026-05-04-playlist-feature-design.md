# 用户歌单功能（创建 / 编辑 / 删除 / 加歌 / 收藏）设计

> 文档状态：当前规范
> 适用范围：仅适用于本仓库 `:data` 歌单数据层、`:feature:home` 歌单 UI、`:feature:search` / `:feature:player-ui` / 插件歌单详情的 ⭐ surface 接入。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/core/musicSheet/`、`../../../../MusicFree/src/pages/sheetDetail/`、`../../../../MusicFree/src/components/panels/types/addToMusicSheet.tsx`
> UI Harness 规则：[screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)
> 最后校验：2026-05-04

## 背景

当前 Android 仓库已有歌单脚手架（`PlaylistEntity` / `PlaylistMusicCrossRef` / `PlaylistDao` / `PlaylistRepository` / `PlaylistDetailScreen` / `PlaylistDialogs` / `PlaylistDetailRoute`），但功能未串联：

- 首页"我的歌单" tab 仍显示 `HomeMockVisualFactory.MINE_ROWS` 提供的 mock 数据。
- `HomeScreen` 中 `onCreateClick` 为空回调，无创建入口；`onOpenMineSheet` 被 `allowHomeMockSheetNavigation = false` 关闭。
- `SearchScreen` 的 `MusicResultItem` 下拉菜单中"添加到歌单"项是 `Toast("功能即将上线")` 占位。
- 没有默认"我喜欢"歌单的概念，无 ⭐ 收藏入口。
- 现有 `EditPlaylistDialog` 仅 rename，缺封面选择与描述编辑；`AddToPlaylistDialog` 是简单 AlertDialog，无"新建歌单"快捷入口。
- 排序模式、首歌封面自动同步等 RN 行为缺失。

RN 的等价实现参见 `../../../../MusicFree/src/core/musicSheet/` 和 `../../../../MusicFree/src/pages/sheetDetail/`。

## 目标

1. 用户可创建 / 重命名 / 编辑信息 / 删除自定义歌单；favorite 默认歌单不可删除、不可改名。
2. 用户可从搜索结果、插件歌单详情、本地歌单详情、全屏播放器把歌曲加入歌单。
3. 用户可对单曲一键收藏到默认 `我喜欢` 歌单（⭐），收藏状态在 UI 上反向同步。
4. 用户可对自定义歌单切换排序模式（手动 / 标题 / 艺术家 / 专辑 / 最新加入 / 最早加入）。
5. 用户可为歌单更换封面图（来自系统相册 / 媒体选择器）。
6. 首页"我的歌单" tab 完全替换为真实数据，`我喜欢` 强制置顶且使用心形封面。

## 非目标

- 不实现 RN `MusicListEditor` 的批量多选编辑（多选 / 全选 / 批量移除 / 移到其他歌单）。
- 不实现歌单备份 / 恢复（JSON 导入导出）。
- 不实现"收藏歌单" tab 的真数据接入；该 tab 仍由 `HomeMockVisualFactory.STARRED_ROWS` 驱动。
- 不实现封面图片裁剪。
- 不在迷你播放器加 ⭐ 按钮（对齐 RN）。
- 不实现长按多选 / 滑动手势（属于批量编辑 surface）。
- 不写 Room `Migration` 对象（开发阶段直接改 entity，参见下文 DB 章节）。

## 模块边界

```
:core      — 加 SortMode enum；扩展 Playlist 模型字段
:data      — 直接改 entity；扩展 PlaylistRepository；新增 PlaylistCoverStore；DataModule 改用 destructive fallback + Room callback 种子
:feature:home          — 替换 home mock；重做 PlaylistDetailScreen；升级 PlaylistDialogs；新增 AddToPlaylistBottomSheet；插件歌单详情行加 "..." 菜单
:feature:search        — 替换 Toast 占位；行 "..." 菜单加 "收藏" / "加入歌单"
:feature:player-ui     — 标题加 heart 按钮；overflow 菜单加 "加入歌单"
```

依赖方向遵循 `:app → :feature:* → :data, :player, :plugin → :core`，不引入新跨模块依赖。Hilt 现有 `DataModule` 已 `@Singleton @Inject` 暴露 `PlaylistRepository`，新增方法即可；`PlaylistCoverStore` 同样在 `DataModule` 提供。

## 域模型（`:core`）

```kotlin
data class Playlist(
    val id: String,                            // UUID 或 "favorite"
    val name: String,
    val coverUri: String? = null,              // 相对路径 "playlist_covers/<id>.jpg"
    val description: String? = null,
    val sortMode: SortMode = SortMode.Manual,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val worksNum: Int = 0,                     // 由 cross-ref count 计算
) {
    val isDefault: Boolean get() = id == DEFAULT_FAVORITE_ID

    companion object {
        const val DEFAULT_FAVORITE_ID = "favorite"
        const val DEFAULT_FAVORITE_NAME = "我喜欢"
    }
}

enum class SortMode { Manual, Title, Artist, Album, Newest, Oldest }
```

新增字段全部带默认值，现有所有 `Playlist(id, name, coverUri)` 三参数构造调用站点（含 `PlaylistViewModel.createPlaylist`、各处 ViewModel 单元测试）无需改动 — 位置参数顺序保留 id/name/coverUri 在前。

`MusicItem` 域模型增加 `addedAt: Long = 0L` 字段，仅在 from-cross-ref 的查询路径上有意义；其他路径默认 0。同样默认值兼容现有构造调用。

## 数据层（`:data`）

### 实体改动（直接改 entity，不写 Migration）

`PlaylistEntity` 新增列：

- `description: String?`
- `sortMode: String`（NOT NULL，默认 `"Manual"`，存 `SortMode.name`）

`PlaylistMusicCrossRef` 新增列：

- `addedAt: Long`（NOT NULL，默认 0L；新加的行写入当前时间戳）

`PlaylistMusicCrossRef` 的复合 PK `(playlistId, musicId, musicPlatform)` 与对 `PlaylistEntity` / `MusicItemEntity` 的 `ON DELETE CASCADE` 已经在当前 schema 上配置完毕（见 `data/.../entity/PlaylistMusicCrossRef.kt`），本 spec 不再调整。

> 开发阶段约束：见项目记忆 `db-schema-during-dev`。直接改 entity，不维护 `Migration` 对象。

### Room 装配

- `@Database(version = 3)`（自 2 升至 3）。
- `DataModule.provideAppDatabase`：移除 `addMigrations(Migrations.MIGRATION_1_2)`；改为 `.fallbackToDestructiveMigration()`；并 `addCallback(SeedFavoriteCallback)`。
- `Migrations.kt` 文件直接删除（开发阶段不再维护历史 migration）。
- `SeedFavoriteCallback.onCreate`：执行 `INSERT OR IGNORE INTO playlists(id, name, description, coverUri, sortMode, createdAt, updatedAt) VALUES('favorite', '我喜欢', NULL, NULL, 'Manual', <now>, <now>)`。
- `PlaylistRepository.observeFavorite()` 第一次发现行不存在时再 idempotent insert 一次（兜底）。

### `PlaylistRepository` API

> 下方签名展示**意图**而非强约束。实现时若与现有签名习惯（如 `createPlaylist(playlist: Playlist)`，由调用方生成 UUID）保持一致更顺手，可保留现状；只要保证下面"业务规则"小节描述的不变量与可观测行为成立即可。


```kotlin
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val musicDao: MusicDao,
    private val coverStore: PlaylistCoverStore,
) {
    // —— 既有：observeAllPlaylists / getPlaylistById / countMusicInPlaylist
    // —— 既有但实现替换：observeMusicInPlaylist / addMusicToPlaylist / removeMusicFromPlaylist / deletePlaylist

    // —— Favorite 专用
    fun observeFavorite(): Flow<Playlist>
    fun isFavorite(item: MusicItem): Flow<Boolean>
    suspend fun toggleFavorite(item: MusicItem)

    // —— CRUD
    suspend fun createPlaylist(name: String, description: String? = null): String
    suspend fun updatePlaylistInfo(id: String, name: String? = null, description: String? = null)
    suspend fun setSortMode(id: String, mode: SortMode)
    suspend fun setCover(id: String, sourceUri: Uri?)
    suspend fun deletePlaylist(id: String)

    // —— 加歌
    suspend fun addMusicToPlaylist(id: String, item: MusicItem): Boolean
    suspend fun addMusicsToPlaylist(id: String, items: List<MusicItem>): Int
}
```

### 业务规则

`addMusicToPlaylist(id, item)`：

1. `musicDao.upsert(item.toEntity())` — 保证 `music_items` 行存在（搜索结果 / 插件源歌曲第一次入库都走这条路径）。
2. `playlistDao.insertCrossRefIgnore(PlaylistMusicCrossRef(id, musicId = item.id, musicPlatform = item.platform, sortOrder = nextOrder, addedAt = now))` — `OnConflictStrategy.IGNORE` 实现 `(playlistId, musicId, musicPlatform)` 维度的 dedup（沿用现有复合 PK）。
3. 返回是否真新增（rowId != -1L）。
4. 若加歌前 `playlist.coverUri == null` 且新增成功，且 `item.artwork` 非空，异步 `coverStore.copyFromArtwork(id, item.artwork)` 把 artwork 同步到歌单封面（对齐 RN）。

`addMusicsToPlaylist(id, items)`：在 `withTransaction` 内循环上面，返回真新增条数。

`toggleFavorite(item)`：

```kotlin
val present = playlistDao.isInPlaylist(DEFAULT_FAVORITE_ID, item.id, item.platform)
if (present) removeMusicFromPlaylist(DEFAULT_FAVORITE_ID, item)
else         addMusicToPlaylist(DEFAULT_FAVORITE_ID, item)
```

`isFavorite(item)`：依赖新加 DAO 方法 `observeIsInPlaylist(playlistId, musicId, musicPlatform): Flow<Boolean>`，实现为 `SELECT EXISTS(SELECT 1 FROM playlist_music WHERE playlistId = ? AND musicId = ? AND musicPlatform = ?)`。

`deletePlaylist(id)`：当 `id == DEFAULT_FAVORITE_ID` 时抛 `IllegalStateException("Cannot delete the default favorite playlist")`；否则事务内 `coverStore.delete(id)` + `playlistDao.deletePlaylist(id)`（cross-ref 行由现有 FK `ON DELETE CASCADE` 处理）。

`updatePlaylistInfo(id, name, description)`：当 `id == DEFAULT_FAVORITE_ID` 且 `name != null` 时抛 `IllegalArgumentException("Cannot rename the default favorite playlist")`；其他字段允许变更（`description`、`coverUri` 单独经 `setCover` 入库）；写入时 `updatedAt = now`。

`setSortMode(id, mode)`：写入 `sortMode`。当 `mode == SortMode.Manual` 时，先按当前观察到的排序结果（即上一个非 Manual 模式下的视觉顺序）回写 `sortOrder` 列，避免切到 Manual 后乱序。

### Sort 应用

```kotlin
fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItem>> =
    playlistDao.observePlaylist(playlistId)
        .flatMapLatest { playlistEntity ->
            val mode = playlistEntity?.sortMode?.toSortMode() ?: SortMode.Manual
            playlistDao.observeMusicInPlaylist(playlistId)        // DAO 永远 ORDER BY sortOrder ASC
                .map { entities -> entities.map { it.toModel() }.applySort(mode) }
        }

private fun List<MusicItem>.applySort(mode: SortMode): List<MusicItem> {
    val collator = Collator.getInstance(Locale.CHINESE)
    return when (mode) {
        SortMode.Manual  -> this
        SortMode.Title   -> sortedWith(compareBy(collator) { it.title })
        SortMode.Artist  -> sortedWith(compareBy(collator) { it.artist.orEmpty() })
        SortMode.Album   -> sortedWith(compareBy(collator) { it.album.orEmpty() })
        SortMode.Newest  -> sortedByDescending { it.addedAt }
        SortMode.Oldest  -> sortedBy { it.addedAt }
    }
}
```

千级以下歌单内存排序成本可忽略；如未来出现万级歌单再考虑 SQL 动态 ORDER BY。

### Cover 图片管线

`PlaylistCoverStore` 单例（`:data`，`DataModule` 提供）：

```kotlin
class PlaylistCoverStore(private val context: Context) {
    private val baseDir get() = File(context.filesDir, "playlist_covers").apply { mkdirs() }

    suspend fun saveFromUri(playlistId: String, src: Uri): String?    // 拷贝到 baseDir / "<id>.jpg"，返回相对路径或 null
    suspend fun copyFromArtwork(playlistId: String, artworkUrl: String?): String?
    suspend fun delete(playlistId: String)
    fun absoluteFile(relativePath: String): File
}
```

封面入口：`EditPlaylistDialog` 使用 `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())`；点击"更换封面"按钮触发 `launch(PickVisualMediaRequest(ImageOnly))`；回调拿到 `Uri` 后调 `viewModel.setCover(playlistId, uri)`。**不裁剪**（对齐 RN）。

默认 favorite 封面：`coverUri == null && isDefault` 时，`CoverImage` composable 渲染 `R.drawable.ic_playlist_favorite_cover` —— 一个新增的 vector drawable，配色对齐 RN 的红色心形。

## UI 改动

> 设计原则（项目级）：组件用 Material3，layout 与信息架构对齐 RN，**图标 / 图片资源直接从 `../MusicFree/` 取**。详见项目记忆 `ui-md3-components-rn-layout`。具体决策点引用 RN 文件路径作为锚点。

### 图标资源

本 spec 涉及的所有图标都从原版 RN 取用，不使用 Material Icons 替代（除非 RN 本身使用的就是同一 Material glyph）。资源转成 vector drawable 后放 `app/src/main/res/drawable/`，文件名以 `ic_` 前缀命名。

需要导入 / 新增的图标（实施阶段在计划里精确登记，命名暂定如下）：

| 图标用途 | 暂定资源名 | RN 来源（参考） |
|---|---|---|
| ⭐ 心形 实心 / 空心（播放器、菜单文案） | `ic_heart_filled.xml` / `ic_heart_outline.xml` | RN `react-native-vector-icons` 中 Feather `heart` |
| "我喜欢" 默认歌单封面（红心方块） | `ic_playlist_favorite_cover.xml` | RN 同名占位，参考 `../MusicFree/src/components/musicSheet/...` |
| "新建歌单" 入口 | `ic_folder_plus.xml` | Feather `folder-plus` |
| "下一首播放" | `ic_queue_music.xml` | RN 详情页菜单同名项 |
| "从歌单移除" | `ic_minus_circle.xml` | Feather `minus-circle` |
| "排序" 菜单触发 | `ic_sort.xml` | RN sheet detail nav menu |
| "播放全部" 大按钮 | `ic_play_circle.xml` | Feather `play-circle` |
| "搜索" 详情页内搜索 | `ic_search.xml`（沿用现有） | RN sheet detail header |

实施时按 `grep -r '<icon-name>' ../MusicFree/src` 反查实际使用名与 SVG 来源；若复用了项目已存在的 `ic_*` 资源（如 `ic_folder_plus` 已在搜索结果菜单使用），不要重复创建。

### 首页 — `HomeScreen` / `HomeViewModel` / `HomeSheetsSection`

- `HomeViewModel` 暴露 `playlists: StateFlow<List<Playlist>>`（域模型），由 `repo.observeAllPlaylists()` 驱动；每条带 `worksNum`（DAO join cross-ref count 后注入）。
- 排序：`Playlist.isDefault` 强制置顶；其余按 `createdAt` 降序（对齐 RN `src/pages/home/components/homeBody/sheets.tsx` 默认顺序）。
- `HomeScreen` 在 Compose 层把 `List<Playlist>` 映射为 `List<HomeSheetUiModel>`（沿用现有 UI 模型）。
- 删除 `HomeMockVisualFactory.MINE_ROWS` 引用与所有调用站点。"我的歌单" tab 的 `rows` 改由上面映射结果驱动。
- `HomeSheetUiModel` 新增字段 `isFavorite: Boolean`；卡片渲染时 `isFavorite && coverUri == null` → 心形 vector drawable。
- `onCreateClick`：弹 `CreatePlaylistDialog`，确认后 `viewModel.createPlaylist(name)`（不立即跳转）。
- `onOpenMineSheet(playlist)`：`navController.navigate(PlaylistDetailRoute(playlist.id))`；删除 `allowHomeMockSheetNavigation` 哨兵字段。
- "收藏歌单" tab 维持 `STARRED_ROWS` mock，不在本 spec 内。

### `PlaylistDetailScreen`（`:feature:home/playlist/`）

参考 `../../../../MusicFree/src/pages/sheetDetail/components/header.tsx` 的信息架构：

```
TopAppBar(back, name, overflow ⋮)
  overflow: 编辑信息 / 排序 / 删除歌单     // favorite 隐藏 "删除歌单"
Header
  [大封面 160dp 矩形圆角]    name (titleLarge)
                             description (bodyMedium, 单行省略)
                             "X 首歌曲" (bodySmall)
                             [▶ 播放全部]   [🔍 搜索]
LazyColumn rows
  [缩略封面 40dp] [title (bodyLarge) / artist (bodySmall)]   [⋮ IconButton]
  row ⋮ DropdownMenu: 下一首播放 / 收藏(or 取消收藏) / 加入歌单 / 从歌单移除
EmptyState (musics.isEmpty())
  "歌单还没有歌曲" + 文字按钮 "去搜索添加" → SearchMusicListRoute(SOURCE_TYPE_PLAYLIST, id)
```

排序弹窗：MD3 `AlertDialog` 单选 6 项 `SortMode`（label 中文化）；选中调 `viewModel.setSortMode(id, mode)`。

行的 inline 删除图标按钮**移除**（原功能改放 row ⋮）。"播放全部" 按钮把当前已排序列表写入 `PlayQueue` 并播第一首（沿用现有 `PlayerController` API）。

`PlaylistDetailViewModel` 状态：`combine(repo.observePlaylist(id), repo.observeMusicInPlaylist(id))` → `PlaylistDetailUiState(playlist, musics, sortMode, isLoading)`。

### Dialogs（`:feature:home/playlist/PlaylistDialogs.kt`）

| Dialog | 形态 | 主要变化 |
|---|---|---|
| `CreatePlaylistDialog` | `AlertDialog`（保留） | 不变，单 `OutlinedTextField` 输入名称 |
| `EditPlaylistDialog`（替代旧 `RenamePlaylistDialog`） | `AlertDialog` | 顶部封面预览 96dp 方块 + "更换封面" 按钮（启动 `PickVisualMedia`）；下方 name `OutlinedTextField`（favorite 时 `readOnly = true` + 灰显 + helper 文字 "默认歌单不可改名"）；description `OutlinedTextField` 多行（最多 4 行）；保存时先 `coverStore.saveFromUri` 再 `repo.updatePlaylistInfo` + `repo.setCover` |
| `DeletePlaylistDialog` | `AlertDialog`（保留） | 不变；favorite 不会到这个 dialog |
| `AddToPlaylistBottomSheet`（替代旧 `AddToPlaylistDialog`） | `ModalBottomSheet` | 顶部 sticky "新建歌单" 行（folder-plus icon）→ 弹 `CreatePlaylistDialog` 后回调 `onCreated(newId)` 即自动 `addMusicToPlaylist(newId, item)`；下方 `LazyColumn` 列出全部歌单，favorite 首位、其他按 `createdAt` 降序；每行展示封面 / 名称 / `worksNum`；选中即 `viewModel.add(playlistId)` + toast + `dismiss` |

`AddToPlaylistBottomSheet` 因为可全局调用，提供 `rememberAddToPlaylistController()` 可挂在任意 Composable 触发 `controller.show(item: MusicItem)` 或 `controller.show(items: List<MusicItem>)`。

### ⭐ Surface roll-out

抽 `MusicItemMoreMenu(item, actions: Set<MusicItemAction>, onAction: (MusicItemAction) -> Unit)` 复合 composable，4 个 surface 共用。`actions` 控制本 surface 显示哪些项：

```kotlin
enum class MusicItemAction { PlayNext, ToggleFavorite, AddToPlaylist, RemoveFromPlaylist }
```

**模块放置：** 模块依赖图为 `:app → :feature:* → :data → :core`，feature 之间不可互相依赖。因此跨 feature 共用的 UI primitives 放 `:core/ui/`：

- `:core/ui/MusicItemMoreMenu.kt` — 纯 Composable，输入 `item / actions / isFavorite: Boolean / onAction`，无 Repository 依赖。
- `:core/ui/AddToPlaylistBottomSheetContent.kt` — 纯 Composable，输入 `playlists: List<Playlist> / onSelect(playlistId) / onCreateNew(name) / onDismiss`，无 Repository 依赖。

每个 surface 的 ViewModel 各自注入 `PlaylistRepository`，暴露：

- `playlists: StateFlow<List<Playlist>>`（来自 `repo.observeAllPlaylists()`）
- `isFavorite(item): StateFlow<Boolean>` 或 `Flow<Boolean>`（按 surface 需要）
- `addToPlaylistSheet: StateFlow<AddToPlaylistSheetState>`（visibility + 当前 item）
- `onAction(item, action)` 业务函数

这样 `:core/ui/` 的 composable 是纯展示层，业务规则集中在每个 feature 的 ViewModel；不需要新建 `:feature:common` 模块。

| Surface | 文件 | 改动 |
|---|---|---|
| `PlayerScreen` | `:feature:player-ui` | 标题旁加 heart `IconButton`；icon 由 `playerViewModel.isCurrentFavorite: StateFlow<Boolean>`（`repo.isFavorite(currentItem).flatMapLatest`）驱动；点击 `repo.toggleFavorite(currentItem)`。Overflow 菜单加 "加入歌单" 项 → 调起 `AddToPlaylistBottomSheet` |
| `SearchScreen.MusicResultItem` | `:feature:search` | 替换原 Toast 占位为 `MusicItemMoreMenu(item, actions = {PlayNext, ToggleFavorite, AddToPlaylist})` |
| `PluginSheetDetailScreen` 行 | `:feature:home/pluginsheet/` | 行末新增 "..." `IconButton` + `MusicItemMoreMenu(item, actions = {PlayNext, ToggleFavorite, AddToPlaylist})` |
| `PlaylistDetailScreen` 行 | `:feature:home/playlist/` | 行末 `MusicItemMoreMenu(item, actions = {PlayNext, ToggleFavorite, AddToPlaylist, RemoveFromPlaylist})`；同时移除原 inline 删除图标 |
| `MiniPlayer` | `:feature:player-ui` | **不动**（对齐 RN） |

⭐ 状态文案：`isFavorite` 时显示 "取消收藏"；否则显示 "收藏"。`PlayerScreen` 的 heart 按钮直接是图标态（实心 / 空心），不显示文字。

## UI Harness 与状态栏

本 spec 涉及的所有新 / 改 Screen 都属于普通 AppBar 页面：

- `PlaylistDetailScreen`：使用 `MusicFreeScreenScaffold` + 自定义 overflow，AppBar 内容高度对齐 `rpx(88)`。
- 其他 surface 均在已注册 Screen 内修改，沿用所属 Screen 的 chrome。
- 不新增"特殊 Chrome 页面"，无需在 [screen-chrome-rules](../../ui-harness/screen-chrome-rules.md) 登记。

`AddToPlaylistBottomSheet` 使用 MD3 `ModalBottomSheet`，由系统接管底部 inset，不需要额外 chrome 规则。

## 默认 favorite 守卫 与 错误处理

| 失败场景 | 反馈 |
|---|---|
| `deletePlaylist("favorite")` | Repository 抛 `IllegalStateException`；UI 在 favorite 详情页隐藏删除入口 + ViewModel try-catch 兜底 toast"默认歌单不可删除" |
| favorite 改名 | Repository 抛 `IllegalArgumentException`；UI 中 `EditPlaylistDialog` 对 favorite 的 name 字段 `readOnly = true` |
| 加歌已存在 | Repository 返回 `false`，UI 静默 + toast"已在歌单中" |
| `coverStore.saveFromUri` 失败 | 返回 `null`；UI toast"封面保存失败"，entity 不写 coverUri，保留旧封面 |
| `PickVisualMedia` 用户取消 | callback `Uri == null`，静默 |
| `musicDao.upsert` 失败（极少见） | 异常上抛；ViewModel 捕获 + toast"添加失败" |
| `setSortMode` | 幂等，无失败路径 |
| 删歌单时封面文件已不存在 | `coverStore.delete` 静默忽略 |
| Plugin 源 MusicItem 字段不全（artist/album 空） | 入库 NULL；排序时 `orEmpty()` 兜底 |

Toast 复用现有项目主流提示组件（沿现状：`SnackbarHost` 或 `Toast.makeText`，按调用 surface 现状选）。

## 测试策略

### 单元测试（Repository）

- `deletePlaylist("favorite")` 抛 `IllegalStateException`。
- `updatePlaylistInfo("favorite", name = "X")` 抛 `IllegalArgumentException`；`updatePlaylistInfo("favorite", name = null, description = "X")` 通过。
- `addMusicToPlaylist` 第一次返回 `true`，第二次返回 `false`（dedup）。
- `addMusicToPlaylist` 在歌单 `coverUri == null` 时把 `item.artwork` 同步成 `coverUri`。
- `setSortMode(id, Manual)` 后 cross-ref 的 `sortOrder` 反映切换前观察到的顺序。
- `toggleFavorite` 互逆：调两次回到原状态。
- `applySort` 6 种模式各自命中预期顺序，包括中文 collator 对 `["香蕉", "苹果", "Apple"]` 的排序。

### DAO 测试（Room in-memory）

- `insertCrossRefIgnore`：重复 `(playlistId, musicId)` PK 时 `rowId == -1L`。
- `observeIsInPlaylist` 在 add/remove 时正确翻转 Flow 输出。
- `observeMusicInPlaylist` 默认按 `sortOrder ASC`。
- `SeedFavoriteCallback.onCreate` 在新建 DB 后插入 favorite 行。

### 集成测试（destructive fallback 路径）

- 完整链路：`createPlaylist` → `addMusicToPlaylist ×3` → `setSortMode(Newest)` → `observeMusicInPlaylist` 顺序断言 → `removeMusicFromPlaylist` → `deletePlaylist`。
- favorite 不存在时 `observeFavorite()` 触发 idempotent insert（兜底）。

### Compose UI 测试

按现有 `:feature:home` 测试范式：

- `HomeScreen`："我的歌单" tab 第一格永远是 favorite，封面是心形资源。
- `PlaylistDetailScreen`：favorite 时无"删除"overflow 项；切换 SortMode 后行顺序变化。
- `AddToPlaylistBottomSheet`：点"新建歌单"→ 创建对话框 → 提交后回调中带新歌单 id 并完成添加。
- `MusicItemMoreMenu` 按 `actions` 集合渲染对应项。

### 手工运行态验收（按 [AGENTS](../../../AGENTS.md) "运行态优先"）

- 创建 / 重命名 / 删除歌单。
- 从搜索结果 `MusicResultItem` 的 ⋮ 加入到刚创建歌单。
- 从插件歌单详情行 ⋮ 加入。
- 全屏播放器 heart 点击切换收藏；下拉再上拉播放器 heart 状态保持。
- 切换 SortMode：UI 立刻重排；切回 Manual 不乱序。
- `PickVisualMedia` 选图后，杀 app 重开，封面仍在；删除歌单后 `filesDir/playlist_covers/<id>.jpg` 文件已清。
- 重装 app（destructive fallback 触发）后，"我喜欢"行仍存在。

## Out-of-scope（显式排除）

- RN `MusicListEditor` 批量多选（`MUSIC_LIST_EDITOR` 路由）。
- 备份 / 恢复（JSON 导入导出）。
- "收藏歌单" tab 真数据。
- 封面图片裁剪。
- mini player ⭐。
- 长按多选 / 滑动手势。

## 后续 Phase 建议（非本 spec）

1. Phase 2：批量编辑 + 多选移除 / 移到歌单（独立 spec）。
2. Phase 3：本地歌单备份 / 恢复 + 与 RN JSON 格式互通（独立 spec）。
3. Phase 4：starred remote sheets 的 "收藏歌单" tab 真数据接入（独立 spec）。

## 验收闸门

按 [AGENTS](../../../AGENTS.md) 验收闸门：

- 单测、DAO 测、集成测、Compose UI 测全绿。
- `./gradlew :app:assembleDebug` 通过。
- 手工运行态验收清单（上）全部通过。
- 旧 `Migrations.MIGRATION_1_2` 的引用全部清除（`grep -r "MIGRATION_1_2"` 应为空）。
- 旧 `HomeMockVisualFactory.MINE_ROWS` 的引用全部清除。
- favorite 行的存在性在新装与重装两种路径下都得到验证。
