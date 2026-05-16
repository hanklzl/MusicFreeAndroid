# 播放页操作按钮 RN 尺寸对齐设计

> 文档状态：当前规范
> 适用范围：播放详情页封面页与歌词页中位于进度条上方的操作按钮行。
> 直接执行：是（作为后续实施计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：`../../../../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx`、`../../../../MusicFree/src/pages/musicDetail/components/content/lyric/lyricOperations.tsx`、当前 Android `:feature:player-ui` 实现。
> 最后校验：2026-05-10

## 概要

修复播放详情页操作按钮行尺寸不一致的问题。问题同时出现在封面页和歌词页：Android 当前实现混用了 Material3 默认 `IconButton`、文本占位和不同尺寸的图标内容，导致同一行里的可视按钮大小不统一。目标是严格对齐 RN 原版的操作行结构：整行固定 `rpx(80)` 高度，子项横向 `space-around`，图标统一使用 RN `iconSizeConst.normal == rpx(42)`，音质和倍速图片统一使用 `rpx(52)`。

本设计只处理 UI 尺寸和结构一致性，不扩展播放业务能力。

## 当前事实

Android 相关代码：

- 播放页入口和封面操作栏：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`
- 歌词操作栏：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsOperations.kt`
- RN 尺寸 token 已在 Android 对齐：`core/src/main/java/com/hank/musicfree/core/theme/Dimensions.kt`
- 已存在 RN 图片资源：`core/src/main/res/drawable/ic_quality_standard.png`、`core/src/main/res/drawable/ic_rate_100.png`

RN 原版事实：

- 封面页操作栏 `Operations`：
  - wrapper：`height: rpx(80)`、`width: "100%"`、`flexDirection: "row"`、`alignItems: "center"`、`justifyContent: "space-around"`、`marginBottom: rpx(24)`。
  - 收藏、下载、评论、更多使用 `Icon`，尺寸为 `iconSizeConst.normal`，即 `rpx(42)`。
  - 音质和倍速使用图片，尺寸为 `rpx(52)`。
- 歌词页操作栏 `LyricOperations`：
  - container：`height: rpx(80)`、`width: "100%"`、`flexDirection: "row"`、`alignItems: "center"`、`justifyContent: "space-around"`、`marginBottom: rpx(24)`。
  - 竖屏下包含收藏按钮，其余为字号、偏移、搜索、翻译、更多，图标统一 `rpx(42)`。

当前 Android 偏差：

- `PlayerOperationsBar` 中音质和倍速用 `Text("标准")`、`Text("1.0x")`，而 RN 使用 `rpx(52)` 图片。
- `PlayerLyricsOperations` 中字号、偏移、翻译使用文字 `"A"`、`"↔"`、`"译"`，搜索和更多使用未显式设定尺寸的 `Icon`。
- 两个操作栏都直接使用默认 `IconButton`，没有统一每个操作项的视觉内容盒和触控盒，容易被 Material3 默认最小尺寸和文本度量影响。

## 非目标

- 不实现音质切换、倍速切换、评论面板或歌词更多业务。
- 不调整底部播放控制区、进度条、播放模式循环、歌词跟随或 seek overlay。
- 不改 `:player`、`:data`、`:plugin`、导航或 `MainActivity`。
- 不做横屏专项；横屏只要求不因本次改动破坏现有布局。

## 设计决策

采用“局部 RN 操作项封装”的方案。

### 统一操作项

在 `:feature:player-ui` 局部新增私有操作项 composable，用固定尺寸包裹可点击区域和可视内容：

- 操作栏高度保持 `rpx(80)`。
- 每个操作项使用同一触控盒，建议 `rpx(64)`，内部内容居中。
- SVG 图标可视尺寸固定为 `IconSizes.normal`，即 `rpx(42)`。
- 图片按钮可视尺寸固定为 `rpx(52)`。
- disabled 或不可用状态只改变 alpha / enabled，不改变尺寸。

该封装只服务播放详情页操作栏，不上升到 `:core`，避免把局部 RN 复刻细节扩散为公共 API。

### 封面页操作栏

`PlayerOperationsBar` 保持 6 个操作项：

1. 收藏：心形图标，`rpx(42)`。
2. 音质：使用 `ic_quality_standard.png`，`rpx(52)`。
3. 下载：下载图标，`rpx(42)`。
4. 倍速：使用 `ic_rate_100.png`，`rpx(52)`。
5. 歌词入口：当前 Android 保留歌词入口以支持封面切歌词，可继续使用聊天气泡图标，`rpx(42)`。
6. 更多：省略号图标，`rpx(42)`。

说明：RN 第 5 项是评论入口，Android 当前未实现评论链路且此入口承担歌词可发现性。本次只修尺寸，保留现有行为。

### 歌词页操作栏

`PlayerLyricsOperations` 对齐 RN 竖屏结构，保留当前 Android 的 5 项能力并统一图标尺寸：

1. 字号：使用 RN `font-size` 同义图标，`rpx(42)`。
2. 偏移：使用 RN `arrows-left-right` 同义图标，`rpx(42)`。
3. 搜索：使用 `ic_magnifying_glass`，`rpx(42)`。
4. 翻译：使用 RN `translation` 同义图标，`rpx(42)`，按当前状态设置 primary / white / disabled alpha。
5. 更多：使用 `ic_ellipsis_vertical`，`rpx(42)`。

当前 Android 没有在歌词页显示收藏按钮；本次不新增第 6 项，避免顺手改变行为。后续若要完整复刻 RN 歌词页竖屏 6 项，应另开专项处理歌词页收藏状态和行为。

### 图标资源

如果 Android 缺少 RN 同义图标，则在 `core/src/main/res/drawable/` 增加局部需要的 vector drawable：

- `ic_font_size.xml`
- `ic_arrows_left_right.xml`
- `ic_translation.xml`

这些资源来自 RN 原版同名 SVG 的视觉语义，颜色通过 Compose `Icon(tint = ...)` 控制。

## 测试策略

新增或扩展 `:feature:player-ui` Compose 单元测试，避免像素脆弱断言，优先验证布局结构和可视尺寸：

- 封面操作栏高度为 RN `rpx(80)`，包含 6 个固定尺寸操作项。
- 封面操作栏图标项可视尺寸一致，音质和倍速图片可视尺寸一致且大于普通图标。
- 歌词操作栏高度为 RN `rpx(80)`，包含 5 个固定尺寸操作项。
- 歌词操作栏所有图标可视尺寸一致。
- 点击语义保持可用：收藏、歌词入口、歌词字号、偏移、搜索、翻译、更多仍有可访问描述或测试 tag。

默认验证命令：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*Player*Operations*Test' --no-daemon
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

如设备或模拟器可用，追加运行态验收：

1. 播放一首歌并进入播放详情页。
2. 在封面页确认进度条上方 6 个操作项大小一致，音质和倍速图片与 RN 尺寸关系一致。
3. 点击封面或歌词入口进入歌词页。
4. 在歌词页确认操作行按钮大小一致，无文字按钮造成的视觉跳动。
5. 验证歌词页操作行与进度条之间仍保持原有固定间距。

## 验收标准

- 封面页与歌词页操作行不再出现同一行按钮视觉尺寸不一致的问题。
- 操作栏结构、行高、图标尺寸和图片尺寸对齐 RN 原版。
- 现有点击行为不回退。
- `:feature:player-ui:testDebugUnitTest` 和 `:app:assembleDebug` 通过。
