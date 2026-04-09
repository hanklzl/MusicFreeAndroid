# 首页 Root 功能与 UI 对齐设计

## 概述

本文档定义 `J-HOME-BROWSE-DETAIL-PLAY` 在当前 brainstorming 阶段的首页 root 专项设计，目标是在不扩散到首页外页面 fidelity 的前提下，完成首页 root 的真实结构对齐、核心可见内容对齐，以及可重复执行的验证闭环。

本轮范围已经确认：

- 优先策略：功能和 UI 一起做，但优先做不会返工的功能闭环
- 专项范围：仅收口首页 root 本身，不把“首页浏览 -> 详情 -> 播放”整条旅程并入本轮
- 首页内二级动作范围：只要求首页可见入口和 tab 切换可用；`新建/导入/删除/更多项` 允许先占位

## 目标

1. 重建与 RN 一致的首页 root 结构，而不是继续沿用当前 Android 的简化骨架
2. 让首页主体内容在固定黄金数据态下可重复截图、可重复 dump、可重复断言
3. 收敛首页最重要的 3 个区域：结构骨架、歌单区、Drawer

## 非目标

- 不在本轮完成搜索、推荐歌单、榜单、历史、本地页的 fidelity
- 不在本轮完成首页歌单区的导入、删除、更多项闭环
- 不在本轮完成 Drawer 全量功能闭环
- 不把首页外页面的视觉差异混入首页 root 验收

## 当前实现与 RN 的关键差异

### 1. 首页结构假设错误

当前 Android 的 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt` 将 `NavBar`、`Operations`、`Sheets` 放进同一个 `LazyColumn`。  
RN 的 `/Users/zili/code/android/MusicFree/src/pages/home/index.tsx` 与 `/Users/zili/code/android/MusicFree/src/pages/home/components/homeBody/index.tsx` 则是：

- 顶部 `NavBar` 固定
- 中部 `Operations + Sheets` 独立滚动
- 底部 `MusicBar` 独立于首页滚动内容

因此，继续以“整页单一滚动”作为首页专项基线会直接导致返工。

### 2. 歌单区尚未锁定黄金数据态

当前 Android 已有 `HomeSheetsViewModel` 和基础 anchor，但缺少与 RN 共享的数据 manifest。没有稳定 seed 时，歌单区 UI 差异、空态噪音、数量变化都会干扰对齐判断。

### 3. Drawer 仍是简化占位实现

当前 Android 的 `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/component/HomeDrawerContent.kt` 仅是单组 Material3 Drawer。  
RN 的 `/Users/zili/code/android/MusicFree/src/pages/home/components/drawer/index.tsx` 是多分组、长列表、包含标题和分区结构的侧栏。这个差异必须按结构问题处理，而不是按样式微调处理。

## 最优先的 3 个事项

### 1. 重建首页 root 的真实结构基线

本项负责收口：

- 固定 `NavBar`
- 独立滚动的 `Operations + Sheets`
- 底部迷你播放器与可视区域的关系
- 首页黄金数据态接入能力

这是当前最优先事项，因为它决定后续 UI 对齐是否会返工。

### 2. 收口歌单区

本项负责收口：

- `我的歌单 / 收藏歌单` tab 语义与选中态
- 计数展示
- 列表密度、封面、标题、副文案
- 非空基线数据
- tab 切换和歌单入口点击

本项是首页主体内容的核心，也是截图差异最集中的区域。

### 3. 重建 Drawer 的层级与版式

本项负责收口：

- 顶部标题
- 分组标题
- 条目顺序与间距
- 主要入口存在且可点击
- 可滚动长列表结构

本项不要求在本轮把所有二级设置项都做真，但必须把首页 root 能看到的结构做对。

## 目标结构

### 顶部固定层

`NavBar` 与 RN 一致，始终固定在首页最上方，不跟随主体内容滚动。其职责仅包括：

- 菜单按钮
- 搜索入口
- 高度、左右间距、圆角、图标、文案、点击区

### 中部滚动层

首页主体只有一个纵向滚动上下文，仅包含：

1. `Operations`
2. `SheetsHeader`
3. `SheetsList`

`Drawer`、`NavBar`、底部迷你播放器都不应被塞进该滚动容器。

### 底部关系层

本轮不把迷你播放器本体纳入首页 fidelity 的主对象，但必须保证：

- 首页主体内容不会被错误遮挡
- 可视区域与底部播放器占位关系稳定
- 截图时不会因为播放器关系错误导致“看起来像，但可视高度不对”

## Compose 组件边界

### `HomeRouteScreen`

职责：

- 收集 ViewModel 状态
- 维护 `drawerState`
- 注入导航回调
- 组合首页各个纯 UI 组件

不负责视觉细节和列表结构。

### `HomeLayout`

职责：

- 表达首页 root 的三层骨架
- 固定顶部 `NavBar`
- 承载中部滚动层
- 处理与底部播放器的布局关系

这是本轮最关键的结构基线组件。

### `HomeScrollableBody`

职责：

- 提供首页主体的唯一纵向滚动上下文
- 只渲染 `Operations + Sheets`

不直接依赖 repository 或导航对象。

### `HomeSheetsSection`

职责：

- 渲染 tab、计数、列表项、空态
- 处理 tab 切换
- 处理歌单入口点击

延续现有 `HomeSheetsViewModel` 作为歌单区状态源，但不把导入、删除、更多项拉进本轮必做范围。

### `HomeDrawerSheet`

职责：

- 按 RN Drawer 的结构渲染标题、分组、主要入口
- 通过 callback 暴露主要导航动作

不再停留在“单组导航项”的简化层级。

## 状态边界

- `drawer open/close`：保留为 route 层本地 Compose 状态
- `selected tab / sheet rows / counts`：由 `HomeSheetsViewModel` 提供
- `Operations` 与 `Drawer` 的配置：使用静态 UI model 或轻量 mapper
- 现有 `HomeViewModel` 中的本地扫描和播放逻辑：不再作为首页 root 主状态源

这样可以避免首页 root 在 fidelity 阶段继续膨胀成杂糅业务容器。

## 黄金数据态

首页专项使用独立的首页黄金数据态 manifest。RN 和 Android 必须共享同一套语义描述，至少包含：

- `selectedTab`
- `mineSheets`
- `starredSheets`
- `miniPlayerVisibility`
- `drawerExpectedSections`

其中：

- `mineSheets` 至少包含 `id/title/musicCount/cover`
- `starredSheets` 至少包含 `id/platform/title/artist/cover`
- 两个 tab 默认都必须有非空基线
- 如果某片段只能用空态验收，必须在 manifest 显式标记为受控空态

RN 侧现有 `/Users/zili/code/android/MusicFree/src/entry/bootstrap/homeFidelitySeedPlan.ts` 可作为共享语义基线，Android 侧应建立等价 seed 恢复能力，而不是依赖人工临时状态。

## 验收边界

### 必做

- `NavBar`
- `Operations`
- `SheetsHeader`
- `SheetsList`
- `Drawer`
- 首页 root 的层级和滚动关系
- 首页 root 的非空黄金数据态

### 允许后置

- 歌单区导入、删除、更多项闭环
- Drawer 次级设置项闭环
- 首页外页面的视觉 fidelity

### 主要入口要求

本轮继续保留并复用现有首页主要入口测试基线：

- 搜索入口
- 推荐歌单
- 榜单
- 播放历史
- 本地音乐
- Drawer 主入口

它们必须继续稳定可达，但本轮不把目标页 fidelity 并入首页 root 完成定义。

## 测试与验证闭环

### 结构验收

- 顶部 `NavBar` 固定
- 中部 `Operations + Sheets` 独立滚动
- Drawer 独立展开
- 底部播放器关系正确

### 数据验收

- `mine/starred` 两个 tab 均能在固定 seed 下展示稳定非空列表
- 标题、计数、副文案、平台标识稳定

### 入口验收

保留现有首页入口仪器测试，并在结构重建后继续通过。

### 视觉验收

在固定设备、固定语言、固定字体缩放、固定 seed 下，产出：

- RN 截图
- Android 截图
- `uiautomator dump` 或等价布局证据
- Compose/语义 anchor 证据
- 片段级差异清单

### 测试类型

- 单测：sheet seed 到 UI model 的映射、tab 状态、Drawer section mapper
- Compose/UI 测试：首页结构 anchor、tab 切换、列表项存在、Drawer 主入口存在
- 仪器测试：扩展现有首页结构测试和入口测试
- 运行态证据：截图、dump、日志和差异记录归档

## 失败判定

出现以下任一情况，本轮首页专项不得判定完成：

- 仍以错误的单一滚动结构实现首页 root
- seed 恢复失败后改用随机真实数据继续验收
- 视觉接近，但滚动关系或层级不一致
- 主要入口可见但会破坏首页结构
- 差异未挂账，直接以“先这样”关闭

## 后续计划输入

本设计将直接作为后续实现计划的输入，计划阶段需要进一步拆解：

- 首页 root 骨架重建任务
- 歌单区 seed/映射/列表任务
- Drawer 结构重建任务
- 结构/视觉/入口验证任务

该实现计划不得扩展到首页外页面 fidelity，也不得把歌单区二级动作提前拉入必做项。
