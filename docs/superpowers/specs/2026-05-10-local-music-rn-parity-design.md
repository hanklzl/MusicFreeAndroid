# Local Music RN Parity Design

> 文档状态：当前规范
> 适用范围：本地音乐页面 AppBar 主题色、RN 本地音乐能力对齐、搜索/编辑/扫描/持久化链路。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../MusicFree/src/pages/localMusic/`、`../MusicFree/src/core/localMusicSheet.ts`、`../MusicFree/src/components/base/appBar.tsx`
> 最后校验：2026-05-10

## 背景

用户反馈 Android 本地音乐页面的 AppBar 没有使用主题色，并要求对比原版 RN 本地音乐模块，找出并补齐当前 Android 缺失功能。

当前 Android 事实：

- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt` 使用自定义顶部区域，只铺 `pageBackground` 到状态栏，未使用 `MusicFreeScreenScaffold`/`MusicFreeTopAppBar`。
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt` 顶部只有下载列表图标，没有 RN 的搜索、扫描、批量编辑入口。
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt` 扫描结果只进入 transient `HomeUiState`，没有写入本地音乐持久层。
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt` 已定义 `CollectionSource.LocalLibrary`，但当前返回 `emptyList()`。
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt` 只支持本地歌单 `playlistId`，不能编辑本地音乐库。

RN 参考事实：

- `../MusicFree/src/pages/localMusic/mainPage/index.tsx` 使用 `AppBar withStatusBar`，其背景来自 `colors.appBar`，内容色来自 `colors.appBarText`。
- RN AppBar 直接动作包含本地列表搜索；菜单包含扫描本地音乐、批量编辑、下载列表。
- `../MusicFree/src/pages/localMusic/mainPage/localMusicList.tsx` 通过 `LocalMusicSheet.useMusicList()` 显示持久本地曲库，并传入 `localMusicSheetId`，支持列表行更多操作。
- `../MusicFree/src/core/localMusicSheet.ts` 在启动 setup 时读取持久本地曲库、过滤已失效本地路径；扫描时递归导入音频文件、读取元数据、去重并持久化；删除时可仅从曲库移除，也可删除原始本地文件。

## 设计目标

1. 本地音乐页面改为普通 AppBar 页面，状态栏与 AppBar 背景使用 `MusicFreeTheme.colors.appBar`，标题和图标使用 `appBarText`。
2. 本地音乐页面补齐 RN 的主要入口：搜索本地列表、扫描本地音乐、批量编辑、下载列表。
3. 扫描结果写入 `MusicRepository` 的 `platform = "local"` 曲库，页面和搜索都观察同一持久数据源。
4. 本地音乐搜索路由 `SearchMusicListRoute.localLibrary()` 返回真实本地曲库。
5. 批量编辑支持本地曲库：选择、移除、下一首播放、添加到歌单、下载选中。
6. 保留 Android 现有 MediaStore/SAF 扫描能力，不引入跨模块反向依赖。

## 非目标

- 不实现 RN 多目录同时选择；Android 本轮使用单次 SAF 目录选择或现有全库 MediaStore 扫描。
- 不删除用户设备上的原始音频文件；本轮仅支持从本地曲库移除。物理删除需要更高风险权限与确认流，单独设计。
- 不重写全局音乐选项面板中的歌词、评论、缓存等非本地音乐核心能力。

## 方案对比

推荐方案：在 `feature:home` 新增专用 `LocalMusicViewModel`，本地页面、搜索、编辑都接入 `MusicRepository.observeByPlatform("local")`。

- 优点：边界清晰，避免继续把 `HomeViewModel` 扩成首页/本地页混合 VM；搜索和编辑共享持久曲库；测试面明确。
- 代价：新增一个 ViewModel，并扩展 `MusicListEditorLiteRoute` 支持非歌单来源。

备选方案：继续复用 `HomeViewModel`，在其中新增持久曲库状态和本地编辑动作。

- 优点：改动文件更少。
- 缺点：`HomeViewModel` 继续膨胀，首页依赖本地音乐细节；后续维护成本更高。

备选方案：只修 AppBar 和菜单入口，不做持久化/搜索/编辑数据链路。

- 优点：最快。
- 缺点：只能解决视觉问题，RN 主要能力仍缺失，不满足本次目标。

## 架构

新增 `LocalMusicViewModel` 作为本地音乐页面状态源：

- 观察 `MusicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)` 作为展示列表。
- 扫描动作调用 `LocalMusicScanner.scan(treeUriOrNull)`，将结果替换写入 `MusicRepository` 的本地平台曲库。
- 页面点击播放时调用 `PlayerController.playQueue(items, index)`。
- 下载相关继续通过 `Downloader` 与 `AppPreferences.defaultDownloadQuality`。

`LocalScreen` 改为 `MusicFreeScreenScaffold`：

- 标题为 `本地音乐`。
- action slot 中提供搜索图标和 overflow 菜单。
- 搜索导航到 `SearchMusicListRoute.localLibrary()`。
- 扫描菜单优先发起 SAF `OpenDocumentTree`，成功后持久授权、保存目录并扫描；若用户取消，可保留页面现状。
- 批量编辑导航到 `MusicListEditorLiteRoute.localLibrary()`。
- 下载列表导航到 `DownloadingRoute`。

`SearchMusicListSourceLoader`：

- 为 `CollectionSource.LocalLibrary` 返回 `musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)`。

`MusicListEditorLiteRoute` / `MusicListEditorLiteViewModel`：

- 路由新增 `sourceType` 与可空 `sourceId`，保留 `MusicListEditorLiteRoute(playlistId)` 构造兼容现有调用。
- playlist 来源沿用 `PlaylistRepository.observeMusicInPlaylist()` 与 `removeMusicFromPlaylist()`。
- local-library 来源使用 `MusicRepository.observeByPlatform("local")` 与 `MusicRepository.delete(item)`。
- UI 文案在 local-library 来源显示 `本地音乐`。

## 数据流

```text
LocalScreen scan action
  -> SAF directory permission and AppPreferences.storageDirectoryUri
  -> LocalMusicViewModel.scanLocalMusic(uri)
  -> LocalMusicScanner.scan(uri)
  -> MusicRepository.replaceByPlatform("local", items)
  -> Room music_items(platform="local")
  -> LocalScreen / SearchMusicList / MusicListEditor observe same Flow
```

对于未配置目录的场景，扫描仍可使用 `LocalMusicScanner.scan(null)` 走 MediaStore 查询，以保留现有 Android 行为。

## 错误处理

- 音频权限未授予：页面显示错误并提供重试，重试重新申请权限。
- SAF 目录选择取消：不改变当前曲库，不显示失败状态。
- 扫描或持久化失败：`LocalMusicUiState.Error(message)` 显示失败原因和重试入口。
- 扫描成功但没有音频：本地曲库替换为空；页面显示空态。

## 测试策略

单元测试：

- `LocalMusicViewModelTest`：扫描成功后替换写入本地平台曲库；页面列表来自持久 Flow；播放、下载、移除本地曲库分别委托正确依赖。
- `SearchMusicListSourceLoaderTest`：local-library 来源返回 `MusicRepository.observeByPlatform("local")`。
- `MusicListEditorLiteViewModelTest`：local-library 来源加载本地曲库，保存删除时调用 `MusicRepository.delete()`，playlist 来源行为不回退。
- `RoutesTest`：`MusicListEditorLiteRoute.localLibrary()` 可序列化，旧 `MusicListEditorLiteRoute(playlistId)` 仍可用。

UI/集成验证：

- `:feature:home:testDebugUnitTest`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- 可用设备时再执行本地音乐入口运行态验收；无设备时明确说明未执行仪器运行态。

## 执行备注

本设计在 `.worktrees/feat-local-music-rn-parity` 中实施。所有文档引用使用相对路径；`docs/superpowers/plans/*.md` 仅作为历史快照，不作为当前执行来源。
