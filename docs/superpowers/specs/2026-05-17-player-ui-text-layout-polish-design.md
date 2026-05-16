# 播放器文字布局细节修复设计

> 文档状态：当前规范
> 适用范围：底部 mini 播放器当前歌曲文字布局、播放详情页标题区插件标签样式
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) / [AGENTS](../../../AGENTS.md)
> 参考来源：用户截图、RN `../MusicFree/src/components/musicBar/`、RN `../MusicFree/src/pages/musicDetail/components/navBar.tsx`、RN `../MusicFree/src/components/base/tag.tsx`

## 背景

用户截图显示两个视觉回归：

1. 首页底部 mini 播放器中歌曲名显示为“我们都是这...”，但播放 / 队列按钮左侧仍有可用空间。当前 Android `MiniPlayerContent` 把标题和歌手分别放在两个 `weight(1f, fill = false)` 文本中，标题最多只能使用信息区约一半宽度，因此在歌手较短时也提前省略。
2. 播放详情页标题区中 “元力QQ” 插件标签是小矩形，圆角和高度都没有对齐 RN。RN 播放页使用通用 `Tag`：高度 `rpx(32)`、左间距 `rpx(12)`、横向 padding `rpx(12)`、圆角 `rpx(24)`，并与歌手文字在同一 `headerDesc` 行内垂直居中。

## 目标

- mini 播放器在不挤压播放 / 队列按钮的前提下，让“标题 + 分隔符 + 歌手”共享同一行可用宽度；歌手较短时标题可以自然吃到右侧剩余空间。
- mini 播放器仍保持 RN 的单行信息结构，超出整行宽度时整体尾部省略，不改成双行、不滚动、不改变点击 / 横滑切歌语义。
- 播放详情页插件标签改成 RN 风格 pill：高度对齐歌手描述行，圆角明显，文字垂直居中。
- 不改变播放器状态、队列、歌词、音质、倍速、分享等功能逻辑。

## 非目标

- 不重做 mini 播放器整体结构、封面尺寸、播放按钮尺寸或队列面板。
- 不新增跑马灯、自动滚动、动态测量文本宽度或复杂自定义 Layout。
- 不调整播放详情页背景沉浸、状态栏 inset、封面页底部控制区和歌词页交互。
- 不新增运行时日志；本次是纯 Compose 布局细节，不涉及可失败业务链路。

## 方案

### mini 播放器文字行

在 `MiniPlayerContent` 中保留现有外层结构：`Row(height = rpx(132))`、左侧信息区 `weight(1f)`、右侧播放按钮和队列按钮固定展示。仅调整信息区内部文字：

- 封面和左右间距保持现状。
- 将标题、分隔符、歌手从多个互相竞争宽度的 `Text` 改为一个单行 `Text`。
- 使用 `buildAnnotatedString` 让标题沿用 `FontSizes.content` 和不透明 `musicBarText`，分隔符与歌手使用 0.6 alpha，并让歌手沿用 `FontSizes.description` 的视觉层级。
- 单个 `Text` 使用 `Modifier.weight(1f)`、`maxLines = 1`、`overflow = TextOverflow.Ellipsis`，这样省略发生在整条 “标题 - 歌手” 的末尾，而不是标题中段提前省略。

该方案与 RN `MusicInfo` 的 `Text numberOfLines={1}` 更接近：RN 也是让标题和歌手共同处在一个单行文本容器内，由容器整体决定省略。

### 播放页插件标签

在 `PlayerNavBar` 内提取一个小的 `PlayerPlatformTag` composable，专门承载播放页标题区的标签样式：

- 歌手与标签所在描述行固定为 `rpx(32)` 高，沿用 `Alignment.CenterVertically`，对齐 RN `headerDesc`。
- 高度固定为 `rpx(32)`。
- 左间距 `rpx(12)`，横向 padding `rpx(12)`。
- 圆角使用 `rpx(24)`。
- 背景使用 `Color.White.copy(alpha = 0.2f)`，文字使用白色，和 RN 播放页 `tagBg` / `tagText` 对齐。
- 保留 `FontSizes.tag`，单行省略，标签本身 `flexShrink = 0` 对应 Android 侧用固定高度、内容宽度和 `maxLines = 1` 控制。
- 歌手文字可用 `weight(1f, fill = false)` 限制长歌手名，保证平台标签不被挤掉。

`PlayerNavBar` 仍是 `PlayerScreen` 的特殊沉浸式 chrome 内容，`PlayerContentLayer` 的状态栏避让不变。

## 测试与验收

- 单测增加 mini 播放器长标题短歌手场景，断言完整标题文本节点可见且队列按钮仍唯一存在。
- 单测增加播放页插件标签样式的语义锚点或测试标签，验证标签文本节点存在，并通过 bounds 检查标签高度与 RN 目标 `rpx(32)` 接近。
- 跑 `:feature:player-ui:testDebugUnitTest` 覆盖播放器 UI 组件测试。
- 跑 `python3 scripts/dev-harness/grep-check.py`，避免触碰 UI harness 禁止写法。
- 收尾默认跑 `:app:assembleDebug`，不跑 release 构建。
