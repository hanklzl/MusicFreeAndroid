# 播放详情更多面板 RN 动作对齐设计

> 文档状态：当前规范
> 适用范围：播放详情页三点更多面板的歌曲操作补齐。
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：`../../../MusicFree/src/components/panels/types/musicItemOptions.tsx`、`../../../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx`
> 最后校验：2026-05-16

## 背景

RN 播放详情页封面操作区的三点按钮打开 `MusicItemOptions`，其中包含歌曲身份、作者、专辑、下一首播放、加入歌单、下载/已下载、歌词关联、定时关闭和清除插件缓存等动作。Android 当前播放详情页三点面板复用了歌词选项面板，只包含 ID、作者、专辑、桌面歌词和本地歌词导入/删除，缺少用户明确指出的四个核心动作：下一首播放、加入歌单、下载和清除插件缓存。

本次目标是补齐播放详情页当前歌曲更多面板的 RN 核心操作语义；独立的 `feature/home/musicdetail` 信息页暂不新增三点入口，避免把截图中的播放详情页问题扩大成另一条页面链路。

## 目标

1. 播放详情页三点面板显示并可执行“下一首播放”。
2. 面板显示并可执行“加入歌单”，复用现有 `AddToPlaylistBottomSheetContent` 和 `PlayerViewModel` 的待加入状态。
3. 面板显示“下载”或“已下载”。未下载时选择音质后入队；已下载时不重复下载。
4. 面板显示并可执行“清除插件缓存(播放异常时使用)”，只清当前歌曲 `platform/id` 的媒体源缓存。
5. 保留现有桌面歌词、本地歌词导入/删除能力，不破坏歌词页更多菜单已有行为。
6. 为新增动作补结构化日志和单元测试。

## 方案选择

推荐方案：在现有 `PlayerMoreOptionsSheet` 中补齐 RN `MusicItemOptions` 的核心动作，并在 `PlayerViewModel` 接入业务能力。这样能复用当前播放页状态、下载音质 sheet、加入歌单 sheet 和错误 toast，改动集中。

备选方案 A：完全拆成 `MusicItemOptionsSheet` 与 `MusicItemLyricOptionsSheet` 两个面板。对 RN 结构更纯，但需要重新拆分播放页封面/歌词入口，改动面更大。

备选方案 B：只给独立 `MusicDetailScreen` 加菜单。它不能解决截图中的播放详情页三点浮窗缺口，也会留下播放器更多面板与 RN 不一致的问题。

## 行为设计

- “下一首播放”：调用 `PlayerController.addNextInQueue(currentItem)`。队列层 `PlayQueue.addNext` 对齐 RN `TrackPlayer.addAll(... beforeIndex)`：如果目标歌曲已在队列中，先移除旧位置，再插入当前项之后，避免同一歌曲重复残留。
- “加入歌单”：面板点击后关闭更多面板并调用 `PlayerViewModel.showAddToPlaylistSheet()`；后续沿用现有歌单选择、新建歌单、`PlaylistRepository.addMusicToPlaylist` 去重逻辑。
- “下载/已下载”：UI 读取 `isCurrentDownloaded`。已下载显示“已下载”且禁用点击；未下载打开现有下载音质 sheet。`PlayerViewModel.downloadCurrent()` 再防御性检查 `downloader.downloadedKeys` 和 `downloader.tasks`，避免快速重复点击或已有任务时重复入队。
- “清除插件缓存”：新增 `MediaCacheRepository.deleteItem(platform, id)`，删除当前歌曲所有音质的媒体源缓存行并同步清内存 LRU；`PlayerViewModel.clearCurrentPluginCache()` 调用它并发出 toast/日志。

## 测试与验收

- `PlayerMoreOptionsSheetTest` 覆盖新增行文案、顺序和回调。
- `PlayerViewModelTest` 覆盖下一首播放、下载去重、清除插件缓存。
- `PlayQueueTest` 覆盖 `addNext` 已存在歌曲时移动到下一首而不是重复。
- `MediaCacheRepositoryDeleteTest` 覆盖删除当前歌曲所有缓存。
- 收尾运行 `:feature:player-ui:testDebugUnitTest`、`:player:testDebugUnitTest`、`:data:testDebugUnitTest`、`bash scripts/dev-harness/check.sh`、`:app:assembleDebug`。
