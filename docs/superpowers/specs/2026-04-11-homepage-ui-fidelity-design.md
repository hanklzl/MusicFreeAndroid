# 首页 UI Fidelity 设计文档

## 概述

本文档定义 MusicFreeAndroid 首页 UI 专项收敛方案，目标是在固定黄金样本 Android 设备上，将首页主体与 Drawer 的静态结构、关键资源和关键动画尽可能完整地对齐到原版 MusicFree React Native 实现。

本轮设计是在已有首页 fidelity 设计基础上的聚焦细化，重点关闭当前最明显的两类差异：

- Drawer 结构、分组和底部操作区与原版偏差较大
- 顶部四宫格、导航栏与歌单区仍混用 Android 默认图标和当前 Compose 骨架，缺少原版资源和交互节奏

## 已确认边界

- 范围：`NavBar + 四宫格 + 歌单区 + Drawer`
- Drawer 目标：完整镜像原版信息架构和视觉结构
- 底部操作区：`返回桌面 / 退出应用` 视觉和真实行为都纳入本轮
- Fidelity 优先级：以黄金样本设备 100% 还原为先，必要时允许首页专项的页面级适配
- 动效要求：不仅对齐静态 UI，也要对齐关键动画观感
- 基准来源：RN 源码 + RN 运行态截图/录屏 + `uiautomator dump`

不在本轮范围内：

- 横屏首页
- 深浅色双主题同时收口
- 首页以外页面的 100% 还原
- 为追求通用性而牺牲黄金样本还原度的额外抽象

## 设计目标

1. 将 Android 首页收敛为与 RN 一致的单一纵向滚动信息架构
2. 将首页关键视觉资源切回原版图标体系，消除默认 Material 图标带来的视感偏差
3. 将 Drawer 从当前简化版列表恢复为原版分组式侧滑面板
4. 将关键动画纳入正式验收对象，而不是把“静态像了”当作完成
5. 为首页专项建立完整的结构、交互、截图、录屏和 dump 证据链

## 原版参考

原版 MusicFree 代码位于 `/Users/zili/code/android/MusicFree`。本轮首页 UI fidelity 直接参考以下文件：

- `src/pages/home/index.tsx`
- `src/pages/home/components/navBar.tsx`
- `src/pages/home/components/homeBody/operations.tsx`
- `src/pages/home/components/homeBody/sheets.tsx`
- `src/pages/home/components/drawer/index.tsx`
- `src/pages/home/components/ActionButton.tsx`
- `src/utils/rpx.ts`
- `src/core/theme.ts`
- `src/assets/icons/*.svg`

## 当前 Android 偏差

当前 Android 首页已有搜索入口、四快捷卡片和基础 Drawer，但与 RN 仍存在结构性偏差：

- `HomeScreen` 仍以当前 Compose 组件拼装为主，视觉骨架没有严格镜像 RN 分片
- `HomeOperations` 使用 `Icons.Default.*`，四宫格图标并非原版资源
- `HomeNavBar` 使用 Material 默认菜单/搜索图标，而不是 RN 的 `bars-3` / `magnifying-glass`
- `HomeDrawerNavigation` 仅建模了 3 个平铺入口，无法表达 RN 的分组、动态尾部文案和底部操作区
- `HomeDrawerContent` 使用 `ModalDrawerSheet + NavigationDrawerItem` 的默认抽屉语义，结构和观感都偏离 RN
- 当前实现只对齐了“能进入口”，没有把 Drawer 开合、按压反馈、tab 切换动画当作正式验收对象

## 总体方案

采用“镜像 RN 结构 + 首页专项资源映射 + 关键动画对齐”的路线。

执行原则：

1. 先按 RN 结构重新定义首页片段边界
2. 首页专项所有关键图标都回到 RN 原始资源体系
3. Drawer 升级为显式的分组化 UI model，而不是继续堆平铺 destinations
4. 动画只做关键路径，不追求额外装饰，但必须摆脱 Android 默认味道
5. 验收以黄金样本的静态截图、动态录屏和 `uiautomator dump` 为准

## 组件边界

首页固定拆为以下片段：

1. `HomeScreen`
2. `HomeNavBar`
3. `HomeOperations`
4. `HomeSheetsHeader`
5. `HomeSheetsList`
6. `HomeDrawer`

### `HomeScreen`

职责：

- 持有唯一纵向滚动上下文
- 持有 drawer state
- 协调首页内容、Drawer、遮罩和系统返回键的交互
- 只负责页面壳和片段拼装，不承载视觉细节

约束：

- `NavBar -> Operations -> SheetsHeader -> SheetsList` 必须处于同一个纵向滚动上下文
- Drawer 打开/关闭不重置首页滚动位置
- 迷你播放器存在时，首页底部留白保持稳定

### `HomeNavBar`

职责：

- 左侧菜单入口
- 右侧搜索胶囊
- 按压反馈

约束：

- 图标改用 RN 资源 `bars-3` 和 `magnifying-glass`
- 尺寸、圆角、间距、点击区按 RN 基线对齐

### `HomeOperations`

职责：

- 四宫格排布
- 图标、标题、点击区
- 卡片按压反馈

约束：

- 图标改用 RN 资源 `fire`、`trophy`、`clock-outline`、`folder-music-outline`
- 不再接受 `Icons.Default.*` 作为首页最终资源

### `HomeSheetsHeader`

职责：

- `我的歌单 / 收藏歌单` tab
- 数量文案
- 新建、导入入口
- tab 切换视觉反馈

### `HomeSheetsList`

职责：

- 当前 tab 的歌单列表渲染
- 封面、标题、副文案、右侧动作
- tab 切换后的内容切换表现

约束：

- 列表切换必须稳定，避免突兀跳闪和高度抖动

### `HomeDrawer`

职责：

- 抽屉整体结构
- 分组卡片
- 条目渲染
- 底部操作区
- 抽屉开合动画中的内容稳定性

约束：

- 不继续沿用“简化 Material 抽屉列表”作为最终结构
- 必须按原版信息架构完整建模

## 资源策略

首页专项涉及的关键图标全部直接复用 RN 资源，优先保证最终视觉一致。

### 必须复用的首页图标

- 顶部导航：`bars-3`、`magnifying-glass`
- 四宫格：`fire`、`trophy`、`clock-outline`、`folder-music-outline`
- 歌单区操作：`plus`、`inbox-arrow-down`
- Drawer：`cog-8-tooth`、`javascript`、`t-shirt-outline`、`circle-stack`、`shield-keyhole-outline`、`alarm-outline`、`language`、`arrow-path`、`information-circle`、`home-outline`、`power-outline`

### 资源复用规则

- 原版已有资源时，不重绘，不找近似替代
- 允许做最小平台格式转换，例如 SVG 转 Android VectorDrawable
- 最终视觉结果必须等价，包括笔画粗细、留白和视觉重心
- 首页专项建立一层显式的图标映射，例如 `HomeIcons`，避免在各 composable 中散写资源引用

## Drawer 信息架构

Drawer 需要从当前的平铺入口模型升级为显式的分组化结构。

### 顶部 Header

- 应用名 `MusicFree`
- 左右 padding、垂直高度与 RN 对齐
- 不额外引入头像、副标题或 Android 特有区域

### 分组 1：设置

- 基础设置
- 插件管理
- 主题设置

### 分组 2：其他

- 定时关闭
- 备份与恢复
- 权限管理（Android 平台保留）

### 分组 3：软件

- 语言设置，右侧显示当前语言
- 检查更新，右侧显示当前版本
- 关于 MusicFree

### 底部操作区

- 返回桌面
- 退出应用

### Drawer 数据模型

Drawer UI model 至少需要支持以下条目类型：

- section header
- 普通条目
- 右侧动态副文案条目
- 底部系统操作条目

该模型必须能表达：

- 分组顺序
- 条目图标
- 标题
- 右侧尾部文案
- 锚点 tag
- 点击行为

### 未落地入口策略

- 已实现入口走真实导航或真实行为
- 未实现入口允许先落到现有设置页或专项占位落点
- 即便行为未完成，也不能删入口、改文案、改分组或改图标

## 状态与动画设计

首页 UI fidelity 不仅验收静态结构，也验收关键动画观感。

### 动画 1：Drawer 开合

要求：

- 抽屉面板位移曲线尽量贴近 RN
- 遮罩透明度变化受控
- 点击遮罩关闭
- 系统返回键在 Drawer 打开时优先关闭 Drawer
- 点击条目时，导航和关闭过程不出现明显闪烁

实现边界：

- 由 `HomeScreen` 统一持有和协调 Drawer 动画状态
- `HomeDrawer` 不单独管理整场开合时序

### 动画 2：顶部入口和四宫格按压反馈

要求：

- 菜单按钮、搜索胶囊、四宫格卡片都要有统一的 press 反馈
- 避免默认 Material ripple 造成的 Android 风格偏差
- 视觉反馈要轻量，但必须有明显压感

### 动画 3：歌单 tab 切换

要求：

- 选中态切换包括字重、颜色和高亮的联动变化
- 列表内容切换稳定，不跳闪、不明显抖动
- 切换时不重置整页主滚动上下文

### 动画 4：整页滚动与迷你播放器关系

要求：

- 首页内容和迷你播放器之间的留白稳定
- Drawer 开关和 tab 切换都不应导致底部内容突然被遮挡或跳位

## 状态建模

### `HomeScreenState`

负责：

- drawer open/closed
- 页面级交互状态
- 与系统返回键、遮罩点击的协调

### `HomeSheetsUiState`

负责：

- 当前选中 tab
- tab 计数
- 当前列表数据

### `HomeDrawerUiModel`

负责：

- Drawer 分组
- 条目顺序
- 右侧动态尾部文案
- 底部操作区条目

### `HomeInteractionStyle`

负责：

- 首页专项统一按压反馈和选中态规范
- 避免每个组件自行定义交互语言

## 系统级动作设计

`返回桌面 / 退出应用` 需要从 UI 组件中抽离成独立 action 接口。

目标：

- 生产环境执行真实系统行为
- 测试环境可替换为 fake/spy
- UI 测试能够断言动作被触发，而不是因系统行为无法验证

## 测试策略

### 1. 纯逻辑测试

- `HomeDrawerUiModel` 分组构造、顺序、尾部文案映射
- `HomeSheetsUiState` tab 切换和数据源切换
- 首页图标映射契约，防止关键资源退回默认 Material 图标

### 2. Compose 结构测试

- 首页根节点、导航栏、四宫格、歌单区、Drawer 根节点存在
- Drawer section header 和关键条目 anchor 存在
- 底部操作区 anchor 存在
- tab 切换后列表 anchor 对应正确数据集

### 3. 运行态交互测试

- 菜单按钮打开 Drawer
- 点击遮罩关闭 Drawer
- Drawer 中关键入口导航可达
- `返回桌面 / 退出应用` 通过可替换 handler 验证触发

### 4. 视觉与动态证据

静态截图至少覆盖：

- `NavBar`
- `Operations`
- `SheetsHeader + 首屏列表`
- `Drawer`

动态录屏至少覆盖：

- Drawer 打开
- Drawer 关闭
- `我的歌单 -> 收藏歌单` 切换
- 任一四宫格卡片按压

`uiautomator dump` 用于验证：

- 层级结构
- 可点击区域
- 文案
- Drawer 可见性
- 整页滚动上下文

## 验收闭环

首页专项必须按以下顺序完成验收：

1. 首页专项相关单测通过
2. Compose/UI 测试通过
3. 在黄金样本设备恢复固定首页数据态
4. 采集 Android 静态截图、动态录屏和 `uiautomator dump`
5. 与 RN 基线逐项比对并记录差异
6. 所有差异闭合后，才可判定首页专项完成

## 风险与约束

- 若继续保留默认 Material 图标，即使布局接近，整体视感仍会明显偏离 RN
- 若 Drawer 仍沿用平铺 destinations，后续补齐分组和动态尾部信息会产生结构性返工
- 若不单独定义 press/selection 动画规范，首页各交互件会出现反馈风格不一致
- 若系统级动作不抽象，测试无法稳定覆盖底部操作区真实行为
- 若只看截图不看录屏，关键动效差异会被遗漏

## 结论

本轮首页 UI fidelity 应采用“镜像 RN 结构、复用 RN 关键图标资源、重建分组化 Drawer、对齐关键动画”的实现路线。完成标准不是“看起来差不多”，而是首页主体和 Drawer 的结构、资源、交互节奏与证据链都达到可核验的一致状态。
