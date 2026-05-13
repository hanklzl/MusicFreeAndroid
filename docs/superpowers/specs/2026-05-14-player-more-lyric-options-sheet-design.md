# 播放器三点歌词操作浮窗 RN 对齐设计

> 文档状态：当前规范
> 适用范围：`feature/player-ui` 全屏播放器封面页与歌词页三点按钮的歌曲信息 / 歌词操作浮窗
> 直接执行：是
> 参考来源：`../../../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx`、`../../../MusicFree/src/pages/musicDetail/components/content/lyric/lyricOperations.tsx`、`../../../MusicFree/src/components/panels/types/musicItemLyricOptions.tsx`
> 最后校验：2026-05-14

## 背景

当前 Android 封面页三点按钮只弹出 `DropdownMenu`，菜单里只有“加入歌单”。用户给定的 RN 验收截图要求点击该三点按钮后展示底部浮窗，内容为当前歌曲摘要、ID、作者、专辑，以及桌面歌词和本地歌词操作。

RN 源码里存在两个相邻入口：

- 封面页 `albumCover/operations.tsx` 的三点打开 `MusicItemOptions`。
- 歌词页 `lyric/lyricOperations.tsx` 的三点打开 `MusicItemLyricOptions`。

用户截图中的浮窗内容对应 `MusicItemLyricOptions`。本次以截图和 `MusicItemLyricOptions` 为验收目标：Android 封面页红框三点与歌词页三点均打开同一套 RN 风格歌曲信息 / 歌词操作底部浮窗。

## 目标

1. 封面页三点按钮点击后展示底部浮窗，不再使用仅含“加入歌单”的 `DropdownMenu`。
2. 浮窗顶部展示当前歌曲封面、标题、`作者 - 专辑` 摘要。
3. 浮窗列表按 RN `MusicItemLyricOptions` 顺序展示：
   - `ID: <platform>@<id>`
   - `作者: <artist>`
   - `专辑: <album>`，无专辑时隐藏
   - `开启桌面歌词`
   - `上传本地歌词`
   - `上传本地歌词翻译`
   - `删除本地歌词`
4. ID、作者、专辑点击复制到剪贴板，并给出 toast。
5. 上传本地歌词、上传本地歌词翻译、删除本地歌词复用现有 `PlayerViewModel.importLocalLyric` / `deleteLocalLyric` 链路。
6. 歌词页三点不再展示 `AlertDialog`，改用同一底部浮窗。

## 非目标

- 不实现 Android 悬浮窗 / 桌面歌词 native 能力。现有歌词设计明确把桌面歌词列为非目标；本次保留“开启桌面歌词”入口并用 toast 告知暂未接入，不伪造开启状态。
- 不补 RN `MusicItemOptions` 的下一首播放、加入歌单、下载、评论、关联歌词、清缓存等通用菜单项。它们属于另一个 RN 面板，本次按截图只实现 `MusicItemLyricOptions`。
- 不改变播放器队列、歌词解析、下载、评论面板或插件缓存行为。

## 设计

新增 `PlayerMoreOptionsSheet`，由 `ModalBottomSheet` 承载，内部拆出可测试的 `PlayerMoreOptionsSheetContent`。内容组件只接收 `MusicItem` 与回调，不直接依赖 `PlayerViewModel`，便于 Compose 单测覆盖文案、顺序、隐藏规则和点击回调。

`PlayerScreen` 维护 `showMoreOptionsSheet` 状态。封面页 `PlayerOperationsBar` 新增 `onMoreClick` 回调；歌词页 `PlayerLyricsOperations.onMore` 也设置同一状态。打开浮窗时若当前歌曲为空则不显示。上传本地歌词仍使用现有 `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` 和 `pendingImportKind`，删除仍调用 `viewModel.deleteLocalLyric()`。

浮层遵守 UI Harness：`ModalBottomSheet` 使用 `Modifier.windowInsetsPadding(WindowInsets.statusBars)`，避免 sheet 拉满时遮挡状态栏。视觉尺寸使用 `rpx` 对齐 RN：顶部圆角 `rpx(28)`、header 高 `rpx(200)`、封面 `rpx(140)`、列表项高 `rpx(96)`、横向 padding `rpx(24)`、图标槽宽 `rpx(48)`。

## 验收

- `PlayerOperationsBarTest` 覆盖封面页三点回调，不再断言 DropdownMenu 的“加入歌单”。
- 新增 `PlayerMoreOptionsSheetTest` 覆盖：
  - 当前歌曲摘要和列表项顺序；
  - 无专辑时隐藏专辑行；
  - 上传、上传翻译、删除、桌面歌词入口回调；
  - ID、作者、专辑行存在且文案格式对齐 RN。
- 运行 `:feature:player-ui:testDebugUnitTest`。
- 运行 `python3 scripts/dev-harness/grep-check.py`。
- 运行 `./gradlew :app:assembleDebug --no-daemon`。
