# 首页主界面 Mock 对齐设计文档

> 文档状态：当前参考
> 适用范围：首页主界面与底部 mini player 的第一阶段 UI mock 对齐。
> 直接执行：否
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 备注：用于收敛首页第一阶段 UI 目标与实现边界；不替代后续 implementation plan。
> 最后校验：2026-04-11

## 概述

本文档定义首页第一阶段 UI 对齐方案，目标是在不处理真实功能闭环的前提下，先将首页当前可见主界面与底部 mini player 收敛到接近原版 MusicFree 的结构、层级和观感。

本轮只处理图 2 所示的首页可见区域，不处理 Drawer，不追求文案逐字复刻，也不要求逐像素还原。允许在 Android 上做小幅自适应，但不能破坏原版页面的结构关系和视觉重心。

本轮是对 [2026-04-11-homepage-ui-fidelity-design.md](./2026-04-11-homepage-ui-fidelity-design.md) 的收窄执行前置。如果两份文档在首页第一阶段范围上有冲突，以本文档为准；Drawer 与真实行为恢复仍以后者为背景参考。

## 已确认边界

- 范围只包含：首页主界面可见区域 + 底部 mini player
- Drawer 不在本轮范围内，保持现状
- 首页使用固定 mock 数据，不要求接真实歌单、真实搜索或真实播放器状态
- mock 只要求结构和视觉层级对齐，不要求逐字复刻截图文案
- 允许小幅 Android 自适应，只保留与 RN 一致的结构和观感
- 首次进入首页时，应直接显示目标黄金态，而不是空态

不在本轮范围内：

- Drawer 重做或 Drawer 动效微调
- 首页空态、加载态、无播放器态等运行态收敛
- 歌单创建、导入、删除、详情跳转的真实功能
- mini player 与真实播放队列、真实播放控制的联通
- 横屏与其他页面的 fidelity 收敛

## 原版与当前实现参考

原版 RN 参考：

- `../../../MusicFree/src/pages/home/index.tsx`
- `../../../MusicFree/src/pages/home/components/navBar.tsx`
- `../../../MusicFree/src/pages/home/components/homeBody/operations.tsx`
- `../../../MusicFree/src/pages/home/components/homeBody/sheets.tsx`
- `../../../MusicFree/src/components/musicBar/index.tsx`

当前 Android 参考：

- `../../../feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreen.kt`
- `../../../feature/home/src/main/java/com/hank/musicfree/feature/home/HomeScreenContent.kt`
- `../../../feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeNavBar.kt`
- `../../../feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeOperations.kt`
- `../../../feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsHeader.kt`
- `../../../feature/home/src/main/java/com/hank/musicfree/feature/home/sheets/HomeSheetsList.kt`
- `../../../feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/MiniPlayer.kt`

## 当前问题归纳

相对于目标首页，当前 Android 首页存在以下结构性问题：

- 顶部导航栏横向密度不足，搜索框长度、留白和整体重心偏离原版
- 四个快捷入口虽然已具备骨架，但卡片尺度、间距和视觉重量仍偏散
- 歌单区被错误简化为“标题 + 空态”，缺少 tab、数量、头部动作区和列表内容态
- 首页默认落在空态，导致页面信息密度与目标图严重不符
- 底部 mini player 仍保留当前 Android 风格的线性进度条和“下一曲”动作，不符合目标图中的轻量播放器条

这些问题的根因不是单个控件画得不够像，而是首页把“展示态”和“真实功能态”耦合在了一起，导致页面始终停留在不稳定的半成品状态。

## 设计目标

1. 让首页首次进入时稳定展示单一黄金态，而不是依赖真实数据决定布局
2. 将首页主界面收敛为与原版一致的三段主结构：导航带、快捷入口、歌单区
3. 将底部 mini player 收敛为与原版一致的轻量信息条结构
4. 将视觉 mock 与真实功能态解耦，为后续逐步接入真实数据保留稳定接口
5. 为后续 plan 和测试提供明确的 UI model、交互边界与验收锚点

## 总体方案

采用“保留容器壳 + 新增纯展示 UI model 层”的方案。

具体原则：

1. 保留现有 `HomeScreen` 路由与 Drawer 壳，不在本轮重做
2. 将首页可见主界面改造成纯展示内容，只消费固定 mock UI model
3. 将 mini player 拆成“容器 + 纯展示 content”两层，第一阶段优先由 mock model 驱动
4. 所有第一阶段交互只做受控行为，不把未完成页面或真实功能链路带入首页验收

## 组件边界

### `HomeScreen`

职责：

- 保留当前页面入口、导航回调与 Drawer 状态协调
- 负责向首页主界面注入固定 mock UI model
- 不在本轮承担真实歌单与真实播放器状态的组合逻辑

约束：

- `HomeScreen` 继续作为容器层存在
- Drawer 继续沿用当前实现，避免本轮范围扩散

### `HomeScreenContent`

职责：

- 渲染首页可见主界面
- 只消费纯展示数据，不直接依赖真实 `HomeSheetsViewModel`
- 保持唯一纵向滚动上下文

建议输入模型：

- `HomeVisualUiModel`
  - `navBar`
  - `operations`
  - `playlistSection`

### `MiniPlayer`

职责拆分：

- 保留现有 `MiniPlayer` 作为真实播放器容器的长期入口
- 新增纯展示 `MiniPlayerContent(uiModel)` 承载第一阶段目标样式

建议输入模型：

- `MiniPlayerUiModel`
  - `coverUri`
  - `title`
  - `subtitle`
  - `isPlaying`
  - `showQueueButton`

这样做的目的，是先固定“长什么样”，后续再把真实播放器状态映射进同一套 UI，而不是再次重做播放器条外观。

## 布局与视觉结构

### 顶部导航带

- 左侧保留菜单按钮，但视觉权重低于搜索框
- 搜索框占据主要横向空间，长度与目标图接近
- 顶部留白收紧，避免当前页面内容整体下沉
- 搜索框内部维持“图标 + 占位文案”结构，不引入真实输入态

### 四个快捷入口

- 四张卡片宽度一致、圆角一致、间距一致
- 卡片允许轻微自适应，但不得出现明显发散的横向排列
- 图标与文字的上下层级必须清晰，整体更接近主操作区而非临时占位块

### 歌单区

- 使用完整头部结构，而不是单标题
- 左侧固定两个 tab：`我的歌单`、`收藏歌单`
- 数量使用次级文字，不抢主标题权重
- 选中态通过粗字重和底部短横线表达
- 右侧固定两个头部操作图标，和 tab 位于同一视觉带
- 列表默认展示 4 条 mock 歌单，优先保证封面、标题、副文案和右侧动作的层级稳定

### 底部 mini player

- 目标以 RN `MusicBar` 的轻量信息条结构为准
- 保留封面、标题、副标题、播放按钮、播放队列按钮
- 不保留当前 Android 版顶部线性进度条
- 不保留当前 Android 版“下一曲”动作
- 背景、内边距和按钮尺寸应接近目标图中的轻灰播放器条观感

## Mock 数据策略

首页使用固定展示态，而不是从真实 repository 或真实播放器推导：

- 默认选中 `我的歌单`
- `我的歌单` 下固定 4 条列表项
- `收藏歌单` 下准备单独的固定列表，用于 tab 切换
- mini player 使用固定 mock 标题、副标题、封面和播放态

mock 数据要求：

- 文案不必逐字复刻截图
- 不能出现测试味过强的占位文案，例如 `item1`、`todo`、`mock title`
- 数量、文案长度和封面形态应足以支撑真实页面观感

## 交互边界

第一阶段允许存在点击反馈，但不要求真实闭环。

可保留的真实行为：

- 菜单按钮打开当前 Drawer
- 搜索框保留现有导航入口
- 四个快捷入口保留现有导航入口

受控 mock 行为：

- tab 切换只切换 mock 列表
- 歌单头部两个动作只做点击反馈或空实现
- 歌单列表项不进入真实详情页，避免把未完成页面带入验收
- 列表右侧删除动作只展示，不执行真实删除
- mini player 播放按钮只切本地 mock 播放态
- mini player 队列按钮只做点击反馈或受控占位
- 点击 mini player 主体不进入真实播放器页

## 锚点与测试约束

现有首页结构锚点应继续保留，包括：

- `screen.home.root`
- `home.navBar.root`
- `home.navBar.menu`
- `home.navBar.search`
- `home.operations.root`
- `home.operations.recommendSheets`
- `home.operations.topList`
- `home.operations.history`
- `home.operations.localMusic`
- `home.sheets.root`
- `home.sheets.tab.mine`
- `home.sheets.tab.starred`
- `home.sheets.action.create`
- `home.sheets.action.import`

为 mini player 增加明确锚点：

- `player.mini.root`
- `player.mini.playPause`
- `player.mini.queue`

第一阶段最小验收：

1. 首次进入首页时，直接出现目标黄金态，而不是空态
2. 首页主体仍保有唯一纵向滚动上下文
3. tab 切换与播放按钮点击不会引发布局跳坏
4. mini player 结构与目标图一致，不再出现线性进度条与“下一曲”按钮

## 非目标与后续衔接

本文档不解决真实功能接线，只为后续实现计划建立稳定 UI 收敛面。

下一阶段接入真实能力时，应遵循以下顺序：

1. 保持 `HomeVisualUiModel` 与 `MiniPlayerUiModel` 不变
2. 用真实歌单数据替换首页 mock 映射
3. 用真实播放器状态替换 mini player mock 映射
4. 再逐步放开创建、导入、删除、详情和播放器跳转

在未完成这些映射前，不应为了“先跑通功能”而破坏第一阶段已经锁定的黄金态结构。
