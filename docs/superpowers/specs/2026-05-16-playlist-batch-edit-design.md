# 歌单批量修改 RN 对齐设计

> 文档状态：当前规范
> 适用范围：本仓库 `:feature:home` 歌单详情批量编辑入口、`MusicListEditorLite` 歌单来源行为与相关测试。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/pages/sheetDetail/components/navBar.tsx`、`../../../../MusicFree/src/pages/musicListEditor/`、`../../../../MusicFree/src/core/musicSheet/index.ts`
> 最后校验：2026-05-16

## 背景

Android 已有 `MusicListEditorLiteRoute`、`MusicListEditorLiteScreen` 和 `MusicListEditorLiteViewModel`，支持歌单来源与本地曲库来源的多选编辑。但歌单详情页 `PlaylistDetailScreen` 的 AppBar 菜单当前只包含“编辑信息 / 排序 / 删除歌单”，没有 RN `sheetDetail.batchEditMusic` 对应入口，导致用户从歌单详情无法进入已有批量修改能力。

RN 原版在本地歌单详情的 AppBar 菜单中提供“批量编辑音乐”，普通歌单和默认“我喜欢”都可进入；只有“删除歌单”对默认歌单隐藏。批量编辑不是在详情页内切换多选模式，而是进入独立 `MusicListEditor` 页面。

## 目标

1. 歌单详情菜单新增“批量编辑”，普通歌单和默认“我喜欢”都显示。
2. 点击“批量编辑”导航到 `MusicListEditorLiteRoute(playlistId)`，复用现有独立编辑页。
3. 保持现有批量编辑行为：多选、全选/清空、移除所选、下一首播放、添加到歌单、下载选中、删除后保存才写回。
4. 批量添加到其他歌单是复制，不从当前歌单移除。
5. 批量移除只删除当前歌单 membership，不删除 `music_items` 曲库记录或本地文件。
6. 保存后停留在编辑页，不自动返回详情页。
7. `addSelectedToPlaylist()` 使用已有事务化 `addMusicsToPlaylist()`，避免批量操作逐首写库和产生多条重复日志。

## 非目标

1. 不在歌单详情页内实现 inline 多选模式。
2. 不实现拖拽排序；当前 Android Lite 编辑页只覆盖批量修改的高价值操作。
3. 不扩展插件歌单、榜单、专辑等 transient 列表的批量编辑写库能力。
4. 不新增数据库字段或 Room migration。
5. 不改播放器队列、下载器或 AddToPlaylist 面板的既有语义。

## 方案选择

采用“复用独立编辑页 + 补入口”的方案。

备选一是在 `PlaylistDetailScreen` 内直接进入多选模式。它能少一次页面跳转，但会让普通详情页承担编辑状态、底部批量操作栏和保存流程，偏离 RN 独立编辑页，也更容易破坏现有详情页播放/菜单行为。

备选二是只实现批量删除入口。它改动更小，但 Android 现有 `MusicListEditorLite` 已经具备加入歌单、下一首播放和下载能力，裁掉这些能力会低于 RN 行为。

推荐方案改动面最小、风险最低，并且对齐 RN 的页面结构：详情页只提供入口，批量操作集中在编辑页。

## 用户路径

1. 用户进入任意本地歌单详情，包括默认“我喜欢”。
2. 点击右上角更多菜单。
3. 菜单中显示“批量编辑”。
4. 点击后进入 `MusicListEditorLiteScreen`，标题为当前歌单名。
5. 用户选择歌曲，执行底部批量操作。
6. 如果执行“移除所选”，列表先移除并显示未保存状态；点击“保存”后才调用 Repository 写回。

## Android 设计

`PlaylistDetailScreen` 已经接收 `onNavigateToMusicListEditorLite: (String) -> Unit`，`AppNavHost` 也已把它接到 `MusicListEditorLiteRoute(playlistId)`。本次只在更多菜单中新增一个 `DropdownMenuItem`：

```kotlin
DropdownMenuItem(
    text = { Text("批量编辑") },
    onClick = {
        menuExpanded = false
        onNavigateToMusicListEditorLite(playlist.id)
    },
)
```

该入口不受 `playlist.isDefault` 限制，和 RN 保持一致。默认歌单仍然只隐藏“删除歌单”。

`MusicListEditorLiteViewModel` 当前歌单来源行为保留：

- `observeMusicInPlaylist(playlistId)` 提供编辑列表。
- `removeSelectedFromPlaylist()` 只 staged 删除，不立即写库。
- `saveChanges()` 对 removed items 调用 `playlistRepository.removeMusicFromPlaylist(playlistId, item)`。
- `addSelectedToPlaylist()` / `createPlaylistAndAddSelected()` 通过 `playlistRepository.addMusicsToPlaylist()` 复制选中歌曲到目标歌单。
- `downloadSelected()` 使用默认下载音质批量入队。

## 日志与错误处理

入口点击本身不增加日志；批量编辑页已有结构化日志覆盖 staged 删除、保存、加入歌单、创建歌单、下载和跳过空选择等操作。新增入口不引入新的 catch 或失败降级路径。

## 测试

1. 新增/更新 `PlaylistDetailScreen` Compose 单测，验证菜单中出现“批量编辑”，点击后回调当前 `playlistId`。
2. 保留并运行 `MusicListEditorLiteViewModelTest`，确保批量编辑既有行为不回退。
3. 更新 `MusicListEditorLiteViewModelTest`，验证批量添加已有歌单走 `addMusicsToPlaylist(targetPlaylistId, selectedItems)`。
4. 运行 `:feature:home:testDebugUnitTest` 覆盖新增 UI 测试和现有 ViewModel 测试。
5. 收尾运行 `bash scripts/dev-harness/check.sh`、`python3 scripts/dev-harness/grep-check.py` 和 `./gradlew :app:assembleDebug --no-daemon`。

## 验收

- 歌单详情默认“我喜欢”和普通歌单菜单均有“批量编辑”。
- 点击入口后导航到批量编辑页，标题显示当前歌单名。
- 批量删除必须先 staged，再通过保存写回当前歌单。
- 批量添加到其他歌单不改变当前歌单。
- 批量添加已有歌单走 `addMusicsToPlaylist()`，保持事务、去重和新增数量语义。
- Debug 构建通过，不要求 Release 构建。
