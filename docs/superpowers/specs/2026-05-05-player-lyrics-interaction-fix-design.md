# 播放页歌词交互修正设计

> 文档状态：当前规范
> 适用范围：播放页歌词交互修正，覆盖 Android 原生播放器页内歌词加载态、自动跟随、点击切换、手动滑动跳转体验。
> 直接执行：是（作为后续实施计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 关联设计：[播放页歌词设计](./2026-05-04-player-lyrics-design.md)
> 参考来源：`../../../../MusicFree/src/pages/musicDetail/components/content/`、`../../../../MusicFree/src/core/lyricManager.ts`
> 最后校验：2026-05-05

## 概要

本设计修正当前播放页歌词功能的运行态交互问题，并在不重写整个歌词系统的前提下，让 Android 歌词页的核心体验进一步对齐 RN 原版。

已确认范围：

1. 歌词加载或自动搜索过程中不得短暂显示“暂无歌词 / 搜索歌词”。
2. 歌词切换下一句时，列表滚动和当前行高亮都要更柔和。
3. 点击歌词页空白区域应切回封面。
4. 歌曲已播放一段时间后进入详情页并切到歌词页，应自动定位到当前进度对应歌词。
5. 歌词跳转横线和播放按钮只在用户手动滑动歌词列表时展示；播放按钮应位于最右侧，而不是居中。

本次采用“交互状态机 + RN 对齐修正”路线。改动集中在 `:feature:player-ui`，只在必要时补充 `:core` 的歌词时序测试。

后续开发使用 git worktree：

- 分支：`feat/player-lyrics-interaction-fix`
- worktree：`.worktrees/feat-player-lyrics-interaction-fix`

## 当前事实

Android 当前歌词实现已经具备歌词加载、自动搜索、翻译、字号、偏移、搜索关联、本地导入等基础能力：

- 播放页入口：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- 播放页 ViewModel：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- 歌词加载器：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoader.kt`
- 歌词内容组件：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt`
- 歌词时序计算：`core/src/main/java/com/zili/android/musicfreeandroid/core/lyric/LyricTiming.kt`

主要偏差集中在 UI 状态和手势滚动：

- `PlayerLyricsContent` 根据 `NoLyric` 立即显示“暂无歌词\n搜索歌词”，加载链路中如果状态短暂落入空态会产生闪烁。
- 自动跟随使用 `animateScrollToItem(currentLineIndex)`，没有 RN 的 `viewPosition: 0.5` 体验，当前行容易不在视觉中心。
- 初次进入歌词页时，列表定位依赖当前 Compose effect 时机；如果布局尚未稳定或 current line 更新错过时机，可能停在第一行。
- `showDragSeekOverlay` 由 `listState.isScrollInProgress` 驱动，程序自动滚动、初次定位、惯性滚动都可能触发 overlay。
- 当前 overlay 布局为左右横线夹中间时间与播放按钮，播放按钮视觉上接近居中；RN 原版为左时间、中横线、右播放按钮。

RN 原版参考行为：

- `Content` 中点击封面/歌词在 `album` 和 `lyric` 之间切换，横屏除外。
- `lyric/index.tsx` 在当前歌词变化时通过 `scrollToIndex({ viewPosition: 0.5 })` 自动滚动。
- 用户触发 `onScrollBeginDrag` 后才显示拖动跳转状态。
- 手动滑动时展示目标时间、水平线、右侧播放按钮；松手后短暂保留，点击播放按钮跳转并播放。
- `lyricManager.refreshLyric()` 在当前歌曲变化或歌词刷新时设置 loading，只有插件歌词和自动搜索都失败后才进入 no lyric。

## 非目标

本次不做以下内容：

- Android 悬浮窗/桌面歌词。
- 横屏歌词视觉专项。
- 歌词缓存清理设置页入口。
- 插件 API 大改或歌词搜索面板重做。
- 歌词缓存文件落盘策略重做。
- 播放页整体重写。

## 状态模型

修正后的歌词页按两层状态理解：

### 歌词数据状态

数据状态继续由 `LyricLoadState` 表达：

- `NoTrack`：没有当前播放歌曲。
- `Loading`：正在读取缓存、调用当前插件、自动搜索或解析歌词。
- `Ready`：有可展示歌词文档。
- `NoLyric`：当前插件歌词失败，自动搜索完成且失败。
- `Error`：出现不可恢复错误。

要求：

- `Loading` 必须覆盖完整加载链路，不能在自动搜索尚未完成时进入 `NoLyric`。
- 如果同一首歌已经有 `Ready` 歌词，后续 refresh、缓存保存或偏好变化期间应保留旧歌词，不清空为 `NoLyric`。
- 只有最终确认无歌词时，UI 才展示“暂无歌词”和“搜索歌词”入口。

### 歌词交互状态

交互状态由 `PlayerLyricsContent` 内部维护，建议显式表达为以下语义：

- `AutoFollowing`：允许播放进度驱动列表自动跟随。
- `ProgrammaticPositioning`：初次进入、文档变化、字号变化、偏移变化时的程序滚动。
- `ManualScrolling`：用户手动拖动歌词列表。
- `SeekOverlayVisible`：用户滑动后短暂展示跳转横线和播放按钮。

要求：

- 程序滚动不得触发 seek overlay。
- 用户手动拖动才进入 `ManualScrolling` 并展示 overlay。
- overlay 展示期间暂停自动跟随。
- overlay 隐藏后恢复自动跟随。

## 加载态设计

`PlayerLyricLoader.observeLyrics()` 和 `PlayerViewModel.lyricsUiState` 需要避免歌词从 `Ready` 短暂退回空态。

设计行为：

- 新歌曲开始加载时可以显示 loading。
- 同一首歌已有歌词时，刷新期间继续展示旧歌词；如果需要提示加载，只使用非遮挡型轻量状态。
- `NoLyric` 只在当前插件歌词、关联歌词、本地歌词、缓存歌词、自动搜索均不可用后发出。
- 保存远程歌词导致 `observeCache()` 再次发射时，不应产生 `Ready -> Loading -> NoLyric -> Ready` 的状态序列。
- 错误态不吞掉搜索入口，但不能在普通加载中提前出现。

UI 文案拆分：

- 状态文本：“暂无歌词”
- 操作入口：“搜索歌词”

二者应是独立节点，方便点击、测试和可访问性识别。

## 滚动与高亮设计

歌词列表继续使用 Compose `LazyColumn`，但自动跟随需要对齐 RN 的中心锚点体验。

设计行为：

- 自动跟随目标为当前行接近视口垂直中心。
- 初次进入歌词页、歌词文档变化、字体大小变化、偏移变化后，等待布局稳定并定位当前行。
- 如果当前播放进度位于第一句之前，定位第一行。
- 播放中当前行变化时使用平滑滚动。
- 用户手动滚动或 overlay 展示期间，不抢用户滚动。
- 无时间戳纯文本歌词不做自动跟随，也不显示 seek overlay。
- 暂停播放时不强行持续跟随；恢复播放后继续跟随。

高亮设计：

- 当前行颜色从普通白色透明态平滑过渡到主题主色或更高不透明度。
- 非当前行保持较低透明度。
- 字重变化可以保留，但不应造成明显跳动；不引入夸张字号变化。
- 翻译文本随主歌词同一行展示，现有开关行为保持不变。

## 点击切封面设计

歌词页点击行为对齐 RN 竖屏体验：

- 点击封面切到歌词。
- 点击歌词页空白区域切回封面。
- 点击歌词文本行不切回封面，避免误触。
- 点击“搜索歌词”、更多操作、播放按钮等明确控件不切回封面。
- overlay 展示期间点击空白不立刻切回封面，避免手动滑动后的误触退出。

实现上应避免整屏 `clickable` 与子节点 `clickable` 的冲突。建议以 pointer input 或明确命中区域判断实现空白点击，保留测试辅助函数用于验证命中规则。

## 手动滑动跳转设计

seek overlay 对齐 RN 结构：

- 用户开始拖动歌词列表后，显示 overlay。
- overlay 根据列表视口中心线命中的歌词行更新目标行。
- overlay 布局从左到右为：
  - 目标时间。
  - 横线。
  - 播放按钮。
- 播放按钮固定在最右侧可点击区域，不居中。
- 松手后 overlay 保留约 2 秒再隐藏。
- 点击播放按钮 seek 到目标歌词时间并开始播放，然后隐藏 overlay。
- 目标时间应考虑 `userOffsetMs` 和 `metaOffsetMs` 的 seek 计算，实际 seek 仍走 `PlayerViewModel.seekToLyricLine()`。

overlay 只允许 timed lyrics：

- 如果歌词文档为纯文本或最后一行时间小于有效阈值，不显示 overlay。
- 当前行无法解析时不显示播放按钮。

## 测试策略

### 单元测试

`core` 如有必要补充 `LyricTiming` 测试：

- 当前播放进度映射当前行。
- `userOffsetMs` 和 `metaOffsetMs` 参与 seek 计算。

`feature/player-ui` 补充或调整测试：

- 加载中不出现 `NoLyric`。
- 同一首歌已有 `Ready` 后 refresh 不清空当前歌词。
- 初次进入歌词页时 currentLineIndex 不为 0 会请求定位到该行。
- 程序自动滚动不展示 seek overlay。
- 用户手动滚动展示 seek overlay。
- overlay 展示时点击空白不切封面。
- overlay 播放按钮位于右侧语义区域并触发 seek。
- 点击歌词空白切封面，点击歌词行不切封面。
- 真正无歌词时显示“暂无歌词”和“搜索歌词”两个独立节点。

### 本地验证

默认收尾验证：

```bash
./gradlew :feature:player-ui:testDebugUnitTest
./gradlew :app:assembleDebug
```

如果设备或模拟器可用，追加运行态验收：

1. 播放一首有 timed lyric 的歌曲。
2. 等待播放到中段。
3. 进入播放详情页并切到歌词页，确认自动定位当前歌词。
4. 继续播放，确认下一句切换滚动和高亮平滑。
5. 手动滑动歌词，确认横线和右侧播放按钮只在滑动后出现。
6. 点击播放按钮，确认跳转到对应歌词位置并继续播放。
7. 点击歌词空白，确认切回封面。

## 验收标准

本次完成必须满足：

- 不再出现加载中短暂闪烁“暂无歌词 / 搜索歌词”。
- 已播放歌曲进入歌词页会定位到当前进度对应歌词。
- 播放中歌词滚动与高亮不再生硬突变。
- 点击歌词空白可切回封面。
- seek overlay 只由用户手动滑动触发。
- overlay 播放按钮在最右侧，并可正确 seek + play。
- 相关单元/Compose 测试通过。
- Debug 构建通过。
