# 歌曲列表点击播放不进入播放器页 RN 对齐设计

> 文档状态：当前规范
> 适用范围：Android 各歌曲列表中“点击歌曲行”的播放行为，不包含 MiniPlayer 点击进入播放器页、播放页自身交互或更多菜单独立动作。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../MusicFree/src/components/mediaItem/musicItem.tsx`、`../../../MusicFree/src/pages/searchPage/components/resultPanel/results/musicResultItem.tsx`、`../../../MusicFree/src/components/musicSheetPage/components/sheetMusicList.tsx`、`../../../MusicFree/src/pages/searchMusicList/searchResult.tsx`、`../../../MusicFree/src/components/musicBar/index.tsx`
> 最后校验：2026-05-13

## 背景

用户期望对齐 RN 原版：点击歌曲列表中的歌曲时，只切换当前播放歌曲或按设置替换播放队列，不自动进入歌曲详情页或播放器页。当前 Android 多个列表在播放成功后调用 `onNavigateToPlayer()`，导致用户点击普通歌曲行后直接进入 `PlayerRoute`，与 RN 行为不一致。

RN 侧已确认的行为：

1. 通用 `MusicItem` 的默认 `onPress` 是 `TrackPlayer.play(musicItem)`。
2. 搜索音乐结果读取 `basic.clickMusicInSearch`，在 `playMusic` 时只播放歌曲，在 `playMusicAndReplace` 时使用搜索结果替换播放列表。
3. 歌单/专辑/榜单详情的 `MusicSheetPage` 读取 `basic.clickMusicInAlbum`，在 `playMusic` 时只播放歌曲，在 `playAlbum` 时使用当前列表替换播放列表。
4. 歌单内搜索结果使用通用 `MusicItem`，默认只播放歌曲。
5. RN `MusicBar` 中进入 `MUSIC_DETAIL` 的点击逻辑处于注释状态，底部播放条并不是本轮歌曲行点击行为的证据。

Android 当前相关入口：

- `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
- `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt`
- `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayer.kt`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

## 目标

1. 所有歌曲列表点击歌曲行后停留当前页面。
2. 保留已有播放语义：
   - 搜索结果继续按 `basic.clickMusicInSearch` 决定只播当前歌曲或替换为搜索结果队列。
   - 歌单/专辑/榜单/歌手作品继续按 `basic.clickMusicInAlbum` 决定只播当前歌曲或替换为当前列表队列。
   - 本地音乐、播放历史、用户歌单、歌单内搜索继续使用当前 Android 已有的播放队列语义，只移除自动进入播放器页。
3. MiniPlayer 继续作为进入播放器页的主入口；用户主动点底部播放条时仍进入 `PlayerRoute`。
4. 更多菜单、下载、收藏、加入歌单、移出歌单等独立动作不受普通歌曲行点击行为影响。

## 非目标

- 不删除 `MusicDetailRoute` 或歌曲信息详情页能力；本轮只修正歌曲行普通点击后的导航副作用。
- 不重做播放器页 UI、歌词页、MiniPlayer UI 或队列面板。
- 不调整 `PlayerController` 队列算法和音源解析策略。
- 不新增设置项。Android 已有 `clickMusicInSearch` / `clickMusicInAlbum` 设置继续生效。
- 不补齐 RN 注释掉的 `MusicBar -> MUSIC_DETAIL` 跳转。
- 不做 Release 构建验收；普通功能收尾以 Debug 构建为闸门。

## 设计方案

### 行为边界

“歌曲行点击”定义为列表主体中单击某一首歌，包括：

- 全局搜索音乐结果。
- 歌单内搜索结果。
- 本地音乐列表。
- 播放历史列表。
- 用户歌单详情列表。
- 插件歌单详情列表。
- 榜单详情列表。
- 专辑详情列表。
- 歌手作品列表。

这些入口点击后只触发 ViewModel 的播放方法，不调用 `onNavigateToPlayer()`。

不属于“歌曲行点击”的入口：

- MiniPlayer / 底部播放条：继续进入播放器页。
- 播放控制按钮：继续播放、暂停、切歌或打开队列。
- 歌曲更多菜单：继续打开 bottom sheet 或执行菜单动作。
- 明确的“详情”入口：如果实现仍保留单独详情按钮或菜单项，它可以进入 `MusicDetailRoute`；但普通行点击不能进入详情页。

### 导航调整

各 Screen 的 `onNavigateToPlayer` 参数需要按实际用途清理：

- 若 Screen 仅在歌曲行点击后调用 `onNavigateToPlayer()`，实现时应移除该调用，必要时进一步移除该参数及对应 navigation wrapper / `AppNavHost` 传参。
- 若 Screen 仍有独立播放器入口，则保留参数，但不得从歌曲行点击链路触发。
- `SearchViewModel.PlayEvent.NavigateToPlayer` 当前只服务搜索结果播放成功后自动导航。实现时优先改为非导航事件，或在 Screen 侧不再响应为 `onNavigateToPlayer()`。如果没有其他用途，应删除该事件，避免后续误用。

### 播放和队列语义

播放链路保持当前 Android 已接入的 RN 设置：

- `SearchViewModel.resolveAndPlay(item, queue)` 继续解析音源并按 `SearchResultClickAction` 调用 `playerController.playItem(...)` 或 `playerController.playQueue(...)`。
- `AlbumDetailViewModel`、`ArtistDetailViewModel`、`TopListDetailViewModel`、`PluginSheetDetailViewModel` 继续按 `AlbumMusicClickAction` 调用 `playerController.playItem(...)` 或 `playerController.playQueue(...)`。
- 本地音乐、播放历史、用户歌单、歌单内搜索维持现有播放方法，不因本轮对齐改变队列内容。

实现重点是删除“播放成功后立刻导航”的副作用，而不是重新解释播放队列规则。

### 歌曲详情入口

当前 Android 存在 `MusicDetailRoute`，且榜单详情中有独立“详情”按钮。RN 的普通歌曲行点击不会进入详情页，但 RN 仍存在独立的 `musicDetail` 页面。因此本轮保守处理：

- 普通行点击不进入 `MusicDetailRoute`。
- 已存在的独立详情按钮或菜单入口可以保留，除非实现时确认它只是为了弥补原先错误点击行为而添加。
- `AppNavHost` 中 `musicDetailScreen` 注册保留，避免影响专辑/歌手信息详情链路。

### 用户反馈

播放失败时继续使用现有错误反馈：

- 搜索结果播放失败继续 toast “播放失败，请重试”。
- 详情列表 `playAt` 返回 `false` 时停留当前页面，不导航。
- 不新增成功 toast。RN 点击歌曲后主要通过底部播放条/当前歌曲状态体现切换结果。

## 测试计划

单元测试优先覆盖行为副作用，避免只依赖人工点击：

1. `SearchViewModelTest`
   - `resolveAndPlay` 成功时仍按 `clickMusicInSearch` 播放或替换队列。
   - 成功播放不再发出 `NavigateToPlayer` 事件；若删除该事件，则测试新的失败事件/无导航事件语义。
2. `SearchMusicListScreen` 或其 ViewModel 测试
   - 点击过滤结果会调用播放方法，但 Screen 不调用 `onNavigateToPlayer()`。
3. `feature:home` 各详情 ViewModel 既有测试
   - 保留 `clickMusicInAlbum` 对 `playItem` / `playQueue` 的覆盖。
4. 必要的 Compose 测试
   - 对用户歌单、本地音乐、历史或榜单中至少一个代表性歌曲列表，验证点击歌曲行后不会触发导航 callback。

构建验证：

```bash
./gradlew :feature:search:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

运行态验证：

- 若有可用设备或模拟器：安装 Debug APK，分别验证搜索结果、插件歌单详情、榜单详情、本地音乐至少 3 个入口。点击歌曲后当前页面保持不变，MiniPlayer 当前歌曲更新；点击 MiniPlayer 仍进入播放器页。
- 若没有可用设备或插件数据：明确报告运行态验收未完成，只把单测和 Debug 构建作为静态闸门。

## 验收标准

1. 搜索结果点击歌曲后不自动进入播放器页。
2. 歌单内搜索、本地音乐、播放历史、用户歌单、插件歌单、榜单、专辑、歌手作品点击歌曲后不自动进入播放器页。
3. `basic.clickMusicInSearch` 与 `basic.clickMusicInAlbum` 仍控制播放/替换队列语义。
4. MiniPlayer 点击仍可进入播放器页。
5. 播放失败反馈不退化，不因移除导航而静默失败。
6. 相关单测与 `:app:assembleDebug` 通过。

## 实施约束

- 按 [AGENTS](../../../AGENTS.md) 的 Git Worktree 开发约束，本轮实施在 `.worktrees/rn-play-click-stay` 中完成。
- 修改 Compose Screen 前必须遵守 [UI rules](../../dev-harness/ui/rules.md)。
- 修改 `PlayerController` 或 `:player` 模块前必须遵守 [Player rules](../../dev-harness/player/rules.md)。本设计预期不需要修改 `:player`。
- 修改测试时必须遵守 [Test rules](../../dev-harness/test/rules.md)。
- 文档和代码注释中的跨仓库引用使用相对路径，不写入环境绝对路径。
