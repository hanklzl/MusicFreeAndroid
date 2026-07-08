# 歌单当前播放行状态设计

> 文档状态：当前规范
> 适用范围：`:core/ui` `MusicItemRow` 当前播放视觉状态、`:feature:home` `PlaylistDetailScreen` 当前歌曲行状态接入。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/components/musicList/index.tsx`、`../../../../MusicFree/src/components/mediaItem/musicItem.tsx`
> 上游 spec：[2026-05-05-playlist-cover-and-row-display-design](./2026-05-05-playlist-cover-and-row-display-design.md)、[2026-06-14-locate-current-track-fab-design](./2026-06-14-locate-current-track-fab-design.md)
> 最后校验：2026-07-08

## 背景

[2026-06-14-locate-current-track-fab-design](./2026-06-14-locate-current-track-fab-design.md) 让歌单详情页在滚动时展示定位当前歌曲的 FAB，并按 `id + platform` 找到当前播放歌曲。运行后发现一个视觉缺口：用户点击 FAB 回到目标行后，该歌曲行和其它歌曲没有任何区别，定位动作完成后缺少“这里就是当前项”的视觉确认。

RN 原版 `MusicList` 已有 `highlightMusicItem` 语义，会把匹配项传给 `MusicItem(highlight = true)`；`MusicItem` 再把标题、序号与描述切到 `primary` 色。Android 已有共享 `MusicItemRow`，本次应在共享行组件上补当前播放状态，而不是在歌单页私有画一套行 UI。

## 目标

1. 歌单详情列表中，如果当前播放器歌曲存在于该歌单，对应歌曲行必须有清晰的当前项状态。
2. 当前项状态必须同时支持播放中与暂停中：暂停后仍保留当前项标记。
3. 视觉上采用已确认的方案 B：淡主色背景、左侧主色竖条、标题前波形。
4. 播放中与暂停中的波形图形保持同一形状；播放中允许轻微动效，暂停中为同一波形的静止状态。
5. 状态判断继续按歌曲 `id + platform`，避免播放器缓存标题或列表标题不一致时失配。

## 非目标

- 不新增列表行点击入口，不改变 `MusicItemMoreMenu` 行为。
- 不新增“播放中 / 暂停中”文字徽标，避免挤占长歌名空间。
- 不改变 FAB 出现、隐藏和滚动定位策略。
- 不重做播放器状态模型；复用现有 `PlayerState.currentItem` 与 `PlayerState.isPlaying`。
- 不把当前播放态持久化到数据库或 SnapshotStore；这是由播放器运行态派生的 UI 状态。

## 视觉设计

### 普通行

保持现有 `MusicItemRow` 视觉不变：

- 封面 40dp。
- 标题 + platform tag。
- 描述行展示 `artist - album`。
- 行末更多按钮保持原位。

### 当前项通用标记

播放中与暂停中都使用同一套当前项外观：

- 行背景：由 `MusicFreeTheme.colors.primary` 派生的低透明度背景，浅色主题约 10%，深色主题可略高到 12%-16%。
- 左侧竖条：`primary` 色，宽 3dp，位于行最左，垂直方向避开行上下边距。
- 标题：使用 `primary` 色并轻微加重字重。
- 标题前插入波形图形，尺寸固定约 16dp，不改变封面、标题、标签、更多按钮的位置关系。

该设计比 RN 仅变文字颜色更可识别，也避免只依赖颜色传达状态。

### 播放中

- 波形使用三根主色圆角竖条，保持同一基准尺寸。
- 可以用轻量 Compose 动效让竖条高度循环变化。
- 动效属于 UI 层表现，不能放入 ViewModel。
- 如果系统减少动态效果，或第一版暂不做动效，播放中也可以退化为静止波形。

### 暂停中

- 仍显示同一个波形图形。
- 波形为静止状态，不替换为 pause icon。
- 行背景、左侧竖条和标题高亮保留，表示它仍是当前歌曲。
- 不额外显示“已暂停”文字；暂停语义由 mini player / 播放器控制区承担，列表行只标识当前项。

## 组件 API

在 `:core/ui` 为 `MusicItemRow` 增加行播放状态参数，默认保持兼容：

```kotlin
enum class MusicItemRowPlaybackState {
    None,
    CurrentPlaying,
    CurrentPaused,
}

@Composable
fun MusicItemRow(
    item: MusicItem,
    isFavorite: Boolean,
    actions: Set<MusicItemAction>,
    onClick: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    playbackState: MusicItemRowPlaybackState = MusicItemRowPlaybackState.None,
)
```

`MusicItemRow` 内部根据 `playbackState` 决定：

- `None`：完全沿用现有样式。
- `CurrentPlaying`：当前项样式 + 可动波形。
- `CurrentPaused`：当前项样式 + 静止波形。

实现时建议把波形拆成私有 `CurrentPlaybackWave(isAnimating: Boolean)`，避免把列表行主体变得过长。波形是当前项视觉的一部分，不需要暴露给外部 surface 直接调用。

## 歌单详情接入

`PlaylistDetailViewModel` 现在只暴露 `currentPlayingItem`。实现时应改为或补充一个包含 `currentItem` 与 `isPlaying` 的 UI 状态，例如：

```kotlin
data class CurrentPlaybackItemState(
    val item: MusicItem?,
    val isPlaying: Boolean,
)
```

来源仍是 `playerController.playerState`：

- `item = playerState.currentItem`
- `isPlaying = playerState.isPlaying`

`PlaylistDetailContent` 在渲染每个 `MusicItemRow` 时按 `id + platform` 匹配当前项：

```kotlin
private fun MusicItem.hasSameMediaIdentity(other: MusicItem?): Boolean =
    other != null && id == other.id && platform == other.platform
```

匹配后：

- `isPlaying == true` -> `MusicItemRowPlaybackState.CurrentPlaying`
- `isPlaying == false` -> `MusicItemRowPlaybackState.CurrentPaused`
- 不匹配 -> `MusicItemRowPlaybackState.None`

FAB 的 `currentPlayingIndex` 仍可复用同一匹配逻辑。若实现时保留 `currentPlayingItem` 字段，也必须确保行状态和 FAB 使用同一来源，避免列表高亮和 FAB 目标分叉。

## 无障碍

当前项不能只靠颜色和动效表达。`MusicItemRow` 在当前项状态下应补充语义：

- `CurrentPlaying`：状态描述为“当前歌曲，播放中”。
- `CurrentPaused`：状态描述为“当前歌曲，已暂停”。
- 波形本身作为装饰图形，不单独抢占 TalkBack 焦点。

如果 Compose 测试对 `stateDescription` 读取不稳定，可退而求其次用稳定 test tag 验证 UI 状态，但运行态验收仍需确认 TalkBack 可读出当前项语义。

## 日志

本设计不新增用户可点击入口，不需要新增 `ui_click`。现有 FAB 点击日志 `playlist_detail.fab.scroll_to_current` 保持不变。

## 测试策略

### `:core` 组件测试

在 `MusicItemRowTest` 覆盖：

- `None` 状态不展示当前项波形，不影响现有标题、来源、描述和更多菜单。
- `CurrentPlaying` 展示当前项波形与当前项语义。
- `CurrentPaused` 展示同一个波形图形，并暴露暂停语义。
- 长标题 + platform tag + 波形不会挤掉更多按钮。

### `:feature:home` 歌单详情测试

在 `PlaylistDetailScreenTest` 覆盖：

- 当 `currentItem` 与列表项 `id + platform` 一致但标题不同，目标行仍进入当前项状态。
- `isPlaying = true` 时目标行传入 `CurrentPlaying`。
- `isPlaying = false` 时目标行传入 `CurrentPaused`。
- 当前歌曲不在歌单中时，列表不展示当前项状态，FAB 也不显示。
- 既有“点击 FAB 滚到当前歌曲”测试继续通过。

## 运行态验收

使用真实或模拟播放器状态验证：

1. 播放歌单内某首歌，进入同一歌单详情页，目标行展示淡主色背景、左侧竖条和波形。
2. 暂停播放后，目标行仍保留当前项状态，波形保持同一形状但静止。
3. 播放歌单外歌曲时，当前歌单不展示当前项状态。
4. 滚动列表触发 FAB，点击定位后，落点行能直接看出当前项。
5. 浅色 / 深色主题下文字、波形、竖条和背景对比度可读。

## 实施约束

按 [AGENTS](../../../AGENTS.md) 的 worktree 约束，后续实现建议在 `.worktrees/feat-playlist-current-row-state` 中进行。实现前仍需读取：

- [docs/dev-harness/ui/rules.md](../../dev-harness/ui/rules.md)
- [docs/dev-harness/player/rules.md](../../dev-harness/player/rules.md)（若实现中触及播放器状态或播放器模块）
- [docs/dev-harness/test/rules.md](../../dev-harness/test/rules.md)（若新增或修改测试）

本 spec 只定义视觉与接入边界，不要求修改 Room、DataStore、插件 API 或播放器底层行为。
