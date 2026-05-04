# 歌单封面与详情行展示对齐 RN 设计

> 文档状态：当前规范
> 适用范围：仅适用于本仓库 `:data` 歌单封面管线、`:core/ui` 共享音乐行组件、`:feature:home` `PlaylistDetailScreen` 接入。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../DOCS_STATUS.md)、[AGENTS](../../AGENTS.md)
> RN 参考：`../../../MusicFree/src/pages/sheetDetail/components/header.tsx`、`../../../MusicFree/src/components/mediaItem/musicItem.tsx`、`../../../MusicFree/src/components/mediaItem/titleAndTag.tsx`、`../../../MusicFree/src/components/base/tag.tsx`、`../../../MusicFree/src/constants/commonConst.ts`
> 上游 spec：[2026-05-04-playlist-feature-design](./2026-05-04-playlist-feature-design.md)
> 最后校验：2026-05-05

## 背景

[2026-05-04-playlist-feature-design](./2026-05-04-playlist-feature-design.md) 落地后的运行态验收发现两个与 RN 行为不对齐的问题：

1. **歌单封面经常为空。** `PlaylistRepository.syncPlaylistCoverIfNeeded` → `PlaylistCoverStore.copyFromArtwork` 实现里 `if (uri.scheme !in listOf("file", "content")) return null` 会把 http/https artwork 全部 reject 掉。插件源歌曲的 `artwork` 几乎都是 http(s) URL，所以"自动用首歌封面"这条路径在线上歌曲场景下静默失效。同时 `setCover` 把图片落到 `filesDir/playlist_covers/<id>.jpg` 后回写的是相对路径 `"playlist_covers/<id>.jpg"`，`CoverImage` 把字符串原样交给 Coil，无 scheme 的相对路径无法被 Coil 解析，**用户从相册选的封面同样不显示**。
2. **歌单详情行只展示 title / artist。** RN `mediaItem/musicItem.tsx` 的行结构是「title + platform tag」/「artist - album」，能直观看出歌曲来自哪个订阅源、所属哪张专辑。当前 `PlaylistDetailScreen.kt` 内的私有 `PlaylistRow` 只渲染 `title` 和 `artist`，丢掉了 platform tag 与 album 信息。

## 目标

1. 用户从插件源（artwork 为 http(s) URL）添加第一首歌后，歌单封面立刻可见；用户从相册选的封面也立刻可见，杀进程重开仍可见。
2. 歌单详情行内同时展示订阅源 platform tag 与 album 信息，与 RN `mediaItem/musicItem.tsx` 信息架构一致。
3. 抽出可在多个 surface 复用的 `MusicItemRow` 组件，本次只在 `PlaylistDetailScreen` 接入；其它 surface 后续按机械替换迁移。

## 非目标

- 不动其它 surface（搜索结果 / 插件歌单详情 / 全屏播放器搜索 / 本地音乐列表）的行展示。
- 不重做 `MusicItemMoreMenu`，行末 ⋮ 行为维持现状。
- 不引入下载管理 / artwork 离线持久化策略，依赖 Coil 自带磁盘缓存即可。
- 不改 `local` platform 字符串常量，仅在渲染层中文化为「本地」。
- 不写 Room `Migration`，无 schema 变化。
- 不引入新跨模块依赖（`:feature:home` 仍然只依赖 `:core` / `:data`）。

## 实施约束（worktree）

按 [AGENTS](../../AGENTS.md) 「Git Worktree 开发约束」节：本 spec 的实施在 `.worktrees/<branch-name>` 下进行（建议分支名 `feat/playlist-cover-and-row`），不在主工作区直接堆叠改动。

## `coverUri` 字段语义重定义

`Playlist.coverUri` 字段语义从「本地相对路径或 null」统一为「Coil 可直接消费的 model 字符串或 null」，允许形态：

- `null` —— 无封面。favorite 走 `R.drawable.ic_playlist_favorite_cover`；其它走 `CoverPlaceholder`。
- `http://...` / `https://...` —— 远程 URL，来自插件源 artwork 自动同步，由 Coil 直接远程加载。
- `file:///...` —— 本地绝对路径 URI，来自用户从相册拷贝到 `filesDir/playlist_covers/`。

**调用方约束：** 所有写入 `coverUri` 的路径必须落在以上三种形态之一，禁止写相对路径。

**老数据兼容：** `PlaylistMapper` 在 entity → model 时做一次容错：如果 `coverUri` 以 `playlist_covers/` 开头（旧相对路径形态），返回前包成 `file://${filesDir}/${rel}`。新写入永远是 file:// 绝对 URI，老数据读出来也能渲染。无 schema 变更，开发期 destructive fallback 即使重置 DB 也兼容。

## 数据层（`:data`）

### `PlaylistCoverStore`

```kotlin
suspend fun copyFromArtwork(playlistId: String, artworkUrl: String?): String? {
    if (artworkUrl.isNullOrBlank()) return null
    val uri = runCatching { Uri.parse(artworkUrl) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "http", "https" -> artworkUrl                        // URL 透传，不落盘
        "file", "content" -> saveFromUri(playlistId, uri)    // 本地拷贝，返回 file://
        else -> null
    }
}

suspend fun saveFromUri(playlistId: String, src: Uri): String? = withContext(Dispatchers.IO) {
    val dest = File(baseDir, "$playlistId.jpg")
    runCatching {
        context.contentResolver.openInputStream(src)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }
    if (dest.exists() && dest.length() > 0) Uri.fromFile(dest).toString() else null
}
```

**Diff 要点：**

- `copyFromArtwork`：新增 http/https 分支，原样回传 URL（与 RN 行为对齐）。
- `saveFromUri`：返回 `Uri.fromFile(dest).toString()`（即 `file:///data/user/.../files/playlist_covers/<id>.jpg`），不再返回相对路径。
- 其它方法（`delete` / `absoluteFile`）不变。

### `PlaylistRepository`

`syncPlaylistCoverIfNeeded` 逻辑保持不变 —— 拿到非 null 就回写 DB。`coverStore.copyFromArtwork` 现在对 http/https 直接返回 URL，自动同步链路自然通了。其它 API（`setCover` / `addMusicToPlaylist` / `addMusicsToPlaylist`）签名与契约不变。

### `PlaylistMapper`

```kotlin
class PlaylistMapper @Inject constructor(
    private val coverStore: PlaylistCoverStore,
) {
    fun toModel(entity: PlaylistEntity, worksNum: Int = 0): Playlist =
        Playlist(
            id = entity.id,
            name = entity.name,
            coverUri = resolveCoverUri(entity.coverUri),
            description = entity.description,
            sortMode = runCatching { SortMode.valueOf(entity.sortMode) }.getOrDefault(SortMode.Manual),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            worksNum = worksNum,
        )

    private fun resolveCoverUri(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("playlist_covers/")) {
            return Uri.fromFile(coverStore.absoluteFile(raw)).toString()
        }
        return raw
    }
}
```

> 当前 `PlaylistMapper` 是 `object` / 顶层扩展函数，引入 `PlaylistCoverStore` 注入需要把它改成 `@Inject` 类，调用站点（Repository）相应改为通过注入实例调用。如果在实现时发现这导致改动面过大，允许用等价的 `(rawCoverUri, filesDir) -> String?` 纯函数 + 注入 `@ApplicationContext` 给 Repository 的方案落地，只要保证：mapper 输出的 `coverUri` 永远是 Coil-ready 字符串。

## UI 层

### 新增 `:core/ui/MusicItemRow.kt`

纯展示组件，对齐 RN `mediaItem/musicItem.tsx` 信息架构。无 Repository 依赖，调用方提供数据与回调：

```kotlin
@Composable
fun MusicItemRow(
    item: MusicItem,
    isFavorite: Boolean,
    actions: Set<MusicItemAction>,
    onClick: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
    modifier: Modifier = Modifier,
)
```

**结构：**

```
Row(verticalAlignment = CenterVertically, padding 16dp / 8dp, clickable -> onClick)
  CoverImage(item.artwork, 40dp, corner 4dp)
  Spacer(12dp)
  Column(weight 1f)
    Row(verticalAlignment = CenterVertically)
      Text(item.title, bodyLarge, modifier = weight(1f), maxLines = 1, ellipsis)
      PlatformTag(displayPlatform(item.platform))
    Spacer(2dp)
    Text(descriptionText(item), bodySmall, color = textSecondary, maxLines = 1, ellipsis)
  MusicItemMoreMenu(
    actions = actions,
    isFavorite = isFavorite,
    onAction = onAction,
    triggerIcon = painterResource(R.drawable.ic_ellipsis_vertical),
  )
```

**派生函数：**

```kotlin
private fun descriptionText(item: MusicItem): String =
    item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else ""

private fun displayPlatform(platform: String): String =
    if (platform == "local") "本地" else platform
```

`displayPlatform` 与 `descriptionText` 放在 `MusicItemRow.kt` 内的私有顶层函数；不建议改 `MusicItem` 域模型字段（避免渲染语义污染域模型）。

### 新增 `:core/ui/PlatformTag.kt`

RN-vibe 描边小药丸：

```kotlin
@Composable
fun PlatformTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(start = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MusicFreeTheme.colors.card,
        border = BorderStroke(1.dp, MusicFreeTheme.colors.divider),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MusicFreeTheme.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
```

视觉对齐 RN `components/base/tag.tsx`：`colors.card` 背景 + `colors.divider` 描边 + 小字号。

### `PlaylistDetailScreen` 接入

删除文件内私有 `PlaylistRow` composable；`LazyColumn` 的 `items` block 直接渲染 `MusicItemRow`，actions 集合保持现状：

```kotlin
items(items = items, key = { "${it.platform}::${it.id}" }) { item ->
    val isFavorite by viewModel.isFavoriteFlow(item)
        .collectAsStateWithLifecycle(initialValue = false)
    MusicItemRow(
        item = item,
        isFavorite = isFavorite,
        actions = setOf(
            MusicItemAction.PlayNext,
            MusicItemAction.ToggleFavorite,
            MusicItemAction.AddToPlaylist,
            MusicItemAction.RemoveFromPlaylist,
        ),
        onClick = {
            val idx = items.indexOf(item)
            viewModel.playAll(startIndex = if (idx >= 0) idx else 0)
            onNavigateToPlayer()
        },
        onAction = { action ->
            when (action) {
                MusicItemAction.ToggleFavorite -> viewModel.toggleFavorite(item)
                MusicItemAction.RemoveFromPlaylist -> viewModel.removeFromPlaylist(item)
                MusicItemAction.PlayNext -> { /* TODO: PlayerController.playNext when API exists */ }
                MusicItemAction.AddToPlaylist -> viewModel.showAddToPlaylistSheet(item)
            }
        },
    )
}
```

ViewModel / 状态层 / 路由层均无改动。

## 错误处理

| 失败场景 | 反馈 |
|---|---|
| 远程 artwork URL 加载失败 / URL 已失效 | Coil error 占位（已有 `CoverPlaceholder`），DB 中 `coverUri` 不变，下次仍尝试加载 |
| `saveFromUri` 失败（流读不出来 / 写盘满 / 用户撤回授权） | 返回 null，DB 不更新 coverUri，UI 静默（`setCover` 调用方原本就吞了 null） |
| 老数据 `coverUri` 是已不存在的相对路径文件 | mapper 输出 `file://...` 但底层文件不存在 → Coil error 占位 |
| `displayPlatform("")`（空 platform，理论不应出现） | 渲染空 tag，视觉异常但不崩；行业务依赖 `(id, platform)` 作 PK，空 platform 已是数据完整性问题 |
| `copyFromArtwork` 收到不识别 scheme（如 `data:`、`asset:`） | 返回 null，与 v1 行为一致 |

## 测试策略

### 单元测试（`:data`）

- `PlaylistCoverStore.copyFromArtwork`：
  - `https://...` 原样返回。
  - `http://...` 原样返回。
  - `file:///...` 落盘并返回 `file://` 形态绝对路径。
  - `content://...` 落盘并返回 `file://` 形态绝对路径。
  - 空串 / null 返回 null。
  - 不识别 scheme（`asset://`）返回 null。
- `PlaylistMapper.toModel`：
  - 旧相对路径 `playlist_covers/abc.jpg` → `file:///.../playlist_covers/abc.jpg`。
  - `https://...` 透传。
  - `file:///...` 透传。
  - `null` / 空串返回 null。
- `PlaylistRepositoryGuardsTest`：补一条「首歌 artwork 是 https URL」→ 加歌后 `observePlaylist(id)` 的 `coverUri == artworkUrl`。

### Compose UI 测试（`:core` androidTest 与 `:feature:home`）

- `MusicItemRowTest`（`:core` androidTest）：
  - 渲染 title 与 platform tag 节点。
  - `album` 非空时 description 文本含 ` - <album>`；为空时仅 artist。
  - `platform == "local"` 时 tag 显示「本地」；其它 platform 原样显示。
  - actions 集合按 `MusicItemMoreMenu` 既有 contract 渲染对应项。
- `PlaylistDetailScreen` 测试：
  - 加歌后行内可见 platform tag 节点与 `artist - album` 文本。

### 手工运行态验收（按 [AGENTS](../../AGENTS.md) "运行态优先"）

- 创建空歌单 → 从插件搜索结果加一首 → 返回首页歌单卡片显示远程封面（不再为空）。
- 进入歌单详情 → 行内能看到 platform tag（如「网易云」、「QQ音乐」）和 `artist - album` 文本。
- 编辑歌单 → 从相册选图 → 保存后封面立刻显示；杀进程再开仍显示。
- 本地歌曲行 platform tag 显示「本地」二字。
- 老数据兼容：在该 fix 前已写入相对路径 coverUri 的歌单（开发机上未触发 destructive fallback），更新后仍能正常渲染封面。

## 验收闸门

按 [AGENTS](../../AGENTS.md) 验收闸门：

- 上述单元、DAO、Compose UI 测试全绿。
- `./gradlew :app:assembleDebug` 通过。
- 手工运行态清单全部通过。
- 全仓 `grep -rn '"playlist_covers/"' --include="*.kt"` 不应出现新写入路径，仅在 mapper 兼容分支与 `PlaylistCoverStore.BASE_DIR_NAME` 出现。
- 不引入新跨模块依赖。

## 后续 Phase 建议（非本 spec）

1. 把 `:core/ui/MusicItemRow` 接入 `SearchScreen.MusicResultItem`、`PluginSheetDetailScreen` 行、`SearchMusicListScreen`、`LocalMusicContent`，做一次性的全 surface 行展示统一（独立 spec / plan）。
2. artwork 离线持久化（remote artwork 主动下载到 cache 目录，断网仍可显示）—— 仅在用户确实出现"切走 wifi 后封面挂掉"反馈后再做。
