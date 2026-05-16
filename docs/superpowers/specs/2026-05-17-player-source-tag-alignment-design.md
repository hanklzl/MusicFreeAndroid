# 播放器插件源标签垂直对齐修复设计

> 文档状态：当前规范（播放器顶部元信息专项）
> 适用范围：`feature/player-ui` 播放详情页顶部标题 / 歌手 / 插件源展示。
> 直接执行：是
> 参考来源：`../../../../../../MusicFree/src/pages/musicDetail/components/navBar.tsx`、`../../../../../../MusicFree/src/components/base/tag.tsx`
> 日期：2026-05-17

## 背景

播放详情页顶部副标题行已展示插件源，但真实设备截图中仍有两个问题：

- 插件源 `元力QQ` 在胶囊内没有垂直居中，视觉上没有和歌手名 `陈奕迅` 对齐。
- 歌手名底部疑似被裁切，中文笔画显示不完整。

Android 当前实现已经使用 RN 尺寸：副标题行 `rpx(32)`，插件源 tag 高度 `rpx(32)`，tag 左间距 / 横向 padding / 圆角也基本对齐 RN。剩余问题来自 Compose `Text` 默认字体 padding 与未显式约束 lineHeight：在固定 16dp 左右的副标题行内，中文文本实际排版盒可能高于容器，从而出现底部裁切；tag 内文本同样会受默认字体 padding 影响，导致视觉中心偏移。

## RN 对齐事实

RN 原版 `musicDetail/components/navBar.tsx`：

- 顶部容器高度：`rpx(150)`。
- 标题：`fontSizeConst.title`，`includeFontPadding: false`，标题下方 `marginBottom: rpx(12)`。
- 副标题行：`height: rpx(32)`，`flexDirection: "row"`，`alignItems: "center"`，`paddingHorizontal: rpx(40)`。
- 歌手名：`fontSizeConst.subTitle`，`includeFontPadding: false`。
- 插件源：使用通用 `Tag`，高度 `rpx(32)`，左间距 `rpx(12)`，横向 padding `rpx(12)`，圆角 `rpx(24)`，内容居中。

因此 Android 不应通过增大副标题行高度来掩盖裁切；应保留 RN 尺寸并修正文字排版盒。

## 设计目标

1. `陈奕迅` 这类中文歌手名在播放器顶部副标题行内完整显示，底部不被裁切。
2. `元力QQ` 文本在胶囊内垂直居中，并与歌手名视觉中线对齐。
3. 保持 RN 原版尺寸语义：副标题行和 tag 均为 `rpx(32)`，间距 / padding / 圆角不放宽。
4. 保持 `PlayerScreen` 全屏沉浸式背景与内容层 status bar inset 边界不变。
5. 不改变插件源数据口径，不做平台名规范化；`MusicItem.platform` 原值是什么就展示什么。

## 非目标

- 不调整播放器页面整体布局、封面位置、底部操作栏和进度条。
- 不新增插件源映射表，也不把 `元力 QQ` 改写为 `元力QQ`。
- 不修改播放队列、插件解析、歌词、下载或分享行为。
- 不把播放器页改造成普通 `MusicFreeScreenScaffold` 页面。

## 技术方案

### 1. 抽取播放器顶部文字样式

在 `PlayerScreen.kt` 内为播放器顶部标题、歌手名、插件源 tag 增加局部 `TextStyle` helper：

- 设置对应 `fontSize`、颜色、粗细。
- 将 `lineHeight` 显式设置为对应 `fontSize`。
- 使用 Compose Android 文本平台能力关闭额外 font padding，对齐 RN `includeFontPadding: false`。

该 helper 只服务播放器顶部 nav bar，避免影响全局字体和其他页面。

### 2. 保留 RN 尺寸，修正副标题行约束

`PlayerNavBar` 保持：

- 外层高度 `rpx(150)`。
- 副标题行高度 `rpx(32)`。
- 副标题行水平内边距 `rpx(40)`。
- tag 高度 `rpx(32)`、左间距 `rpx(12)`、横向 padding `rpx(12)`、圆角 `rpx(24)`。

在这些固定尺寸内，依赖关闭 font padding + 显式 lineHeight 解决文字裁切和垂直偏移。

### 3. 补充可回归测试

扩展 `PlayerNavBarTest`：

- 保留现有“长歌手名时插件源仍显示”的测试。
- 新增中文歌手名 + 插件源的布局测试，断言副标题行、歌手文本、tag 高度均为 RN 目标高度，并且可显示。
- 为副标题行增加内部 test tag，避免测试只能找到 tag 但无法锁定行高。

Compose JVM 测试无法完全替代真机截图，但能锁住本次修复的尺寸约束，真机 / 模拟器打开播放器页作为最终视觉验收。

## 验收标准

1. `PlayerNavBarTest` 通过。
2. `:feature:player-ui:testDebugUnitTest` 至少覆盖本次新增 / 修改的测试并通过。
3. `python3 scripts/dev-harness/grep-check.py` 通过。
4. `./gradlew :app:assembleDebug --no-daemon` 通过。
5. 模拟器安装 / 打开 Debug 包后，播放器页顶部副标题行肉眼检查：歌手名不截断，插件源文字在胶囊内居中且与歌手名对齐。

## 风险与回滚

- 若 Compose 当前版本关闭 font padding 的 API 有弃用提示但仍可用，本次保持局部使用，避免扩大影响。
- 若某些系统字体仍在固定 `rpx(32)` 内出现极端裁切，可再单独评估是否引入播放器顶部专用行高策略；本次不先放宽 RN 尺寸。
- 回滚范围限于 `PlayerScreen.kt`、`PlayerNavBarTest.kt` 和本设计 / 计划文档。
