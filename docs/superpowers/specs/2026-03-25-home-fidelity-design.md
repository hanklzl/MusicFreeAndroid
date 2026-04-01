# 首页 100% 还原设计文档

## 概述

本文档定义 MusicFreeAndroid 首页专项收敛方案，目标是在单一黄金样本 Android 设备上，将首页竖屏完整滚动内容、Drawer 以及首页可达入口，尽可能完整地还原到原版 MusicFree React Native 实现。

本次设计不直接进入实现细节，而是先固定验收边界、证据体系、组件边界、数据适配策略与验证闭环，避免“肉眼接近但结构不一致”的伪完成。

## 已确认边界

- 验收范围：竖屏首页完整滚动内容 + Drawer + 首页入口可达
- 基准来源：RN 源码 + RN 真机截图 + `uiautomator dump`
- 还原原则：视觉严格复刻，交互允许少量 Android 原生化
- 验收设备：一台固定 Android 黄金样本设备
- 调试支持：允许新增不影响线上行为的语义标记、截图脚本、dump 脚本和对比基础设施

不在本次首页专项范围内：
- 横屏首页
- 深色/浅色双主题同时收口
- 权限弹窗、加载态、错误态、空态的全状态矩阵
- 首页以外页面的 100% 还原

## 黄金样本环境

首页专项的截图、`uiautomator dump`、差异判断和最终闭合结论，全部以同一黄金样本环境为准。

### 当前锁定环境

| 项目 | 值 |
|------|----|
| 设备标识 | `emulator-5554` |
| AVD 名称 | `Medium_Phone_API_36.0` |
| 设备型号 | `sdk_gphone64_x86_64` |
| Android 版本 | `16` |
| 分辨率 | `1080 x 2400` |
| 物理 density | `420` |
| 强制 density | 无 (`display_density_forced = null`) |
| font scale | `1.0` |
| 语言/地区 | `en-US` |
| 当前顶层 Activity | `com.zili.android.musicfreeandroid/.MainActivity` |

### 环境锁定规则

- 后续首页专项的 RN 截图、Android 截图和 dump 采集，默认都基于该环境
- 若黄金样本环境发生变化，原有证据包默认失效，需要重新采集
- 实现计划不得把“换设备再比”当成关闭差异的方法

### 黄金样本数据态

首页专项的基线不仅锁设备，也锁数据态。未锁定数据态前，不允许关闭首页片段差异。

数据态要求：

- 使用固定 manifest 描述首页验收数据，不依赖临时人工状态
- 首页主验收态默认使用非空数据，而不是空态
- `我的歌单` 与 `收藏歌单` 都必须存在可见列表项，避免只对齐空态
- 迷你播放器是否可见，也必须写入 manifest，不允许每次凭当前播放状态临时决定
- 若某能力当前只能以空态验收，必须在 manifest 中显式记录该片段为“受控空态基线”

推荐以独立的首页专项 manifest 文档承载该数据态定义，并与首页取证资产放在同一组可维护文档中。

manifest 至少包含：

- RN 参考提交或版本标识
- Android 参考提交或版本标识
- 首页当前选中 tab
- `我的歌单` 列表项数量、顺序、标题、副文案、封面来源
- `收藏歌单` 列表项数量、顺序、标题、副文案、封面来源
- 迷你播放器可见性，以及展示中的歌曲标题/歌手
- 是否允许某片段以受控空态参与验收

### 黄金数据态落地方案

首页专项的实现计划必须首先建设一套“可恢复的首页黄金数据态”，而不是依赖人工现配。

最低落地要求：

1. 版本化 manifest
   - 维护一份固定、可版本化的首页黄金数据态说明文档
   - 作为 RN 与 Android 双侧共享的数据态说明

2. 双侧恢复流程
   - RN 侧：提供一条可重复执行的恢复流程，使 `fun.upup.musicfree` 首页进入 manifest 描述的数据态
   - Android 侧：提供一条可重复执行的恢复流程，使 `com.zili.android.musicfreeandroid` 首页进入同一语义数据态

3. 最小样例数据
   - 首页默认选中 `我的歌单`
   - `我的歌单` 至少 2 条
   - `收藏歌单` 至少 2 条
   - 4 条歌单都必须固定顺序、标题、副文案和封面来源
   - 迷你播放器可见性固定，若可见则固定一首样例歌曲标题/歌手

4. 恢复方式
   - 优先使用 seed 数据导入、固定测试夹具、已有本地数据库初始化或可重复脚本
   - 不依赖人工逐步点击配置
   - 不要求一定引入 mock 开关；但若不用 mock，也必须能一键恢复到 manifest 描述状态

在首页专项中，只有进入这套黄金数据态后采集的截图和 dump，才可作为正式验收证据。

## 原版参考

原版 MusicFree 代码位于 `/Users/zili/code/android/MusicFree`。本次首页设计的直接参考如下：

- `src/pages/home/index.tsx`
- `src/pages/home/components/navBar.tsx`
- `src/pages/home/components/homeBody/index.tsx`
- `src/pages/home/components/homeBody/operations.tsx`
- `src/pages/home/components/homeBody/sheets.tsx`
- `src/pages/home/components/drawer/index.tsx`
- `src/utils/rpx.ts`
- `src/core/theme.ts`

### UI 资源复用策略

如果首页还原涉及 UI 资源，允许直接从原 MusicFree 工程中拷贝，而不是重新手工绘制或重新导出。

适用对象包括：

- 图标资源
- 插图
- 占位图
- 可复用的静态图片资源
- 为 100% 还原所必需的其他首页视觉资源

复用原则：

- 优先保证与原版视觉结果一致
- 仅拷贝当前首页专项实际需要的资源，避免无关资源批量迁移
- 如 Android 平台需要不同格式或命名，可做最小必要转换，但不能改变最终视觉效果
- 若原版已有可直接复用的资源，不优先选择重绘、重写或近似替代

### 当前 Android 基线

当前 Android 首页虽然已有顶部搜索入口、四快捷卡片和 Drawer，但核心结构仍与 RN 不一致：

- RN 首页是单一纵向滚动页面
- RN 首页下半部分是 `我的歌单 / 收藏歌单` 区域
- 当前 Android 首页仍保留 `本地音乐 / 播放列表` 双页结构

因此，首页若要达到本次定义的 100% 还原，必须将 `Sheets` 区纳入首页专项，而不是只修顶部和 Drawer。

## 设计目标

### 核心目标

1. 将首页结构收敛为与 RN 一致的单一滚动信息架构
2. 为首页每个片段建立可重复执行的证据链，而不是依赖主观截图判断
3. 利用 Compose 语义标记增强 `uiautomator dump` 的可观测性
4. 让首页专项完成标准从“高分”改为“无未闭合差异”

### 非目标

- 不在本次设计中展开首页以外页面的实现方案
- 不为“更原生”而主动偏离 RN 视觉结果
- 不做与首页还原无关的通用重构

## 总体方案

采用“基线驱动收敛”为主、局部镜像 RN 结构为辅的路线。

执行思路：

1. 先把首页拆成可验收片段
2. 为每个片段建立 RN/Android 双侧证据包
3. 在 Android 侧按 RN 结构重组首页
4. 逐片段收敛视觉、文案、层级和点击区
5. 最后做整页联调和回归

不采用“直接改到看起来像”的路线，因为首页当前最大偏差在结构本身，先视觉微调只会带来重复返工。

## 首页片段拆分

首页专项固定拆为 5 个验收片段：

1. `NavBar`
2. `Operations`
3. `SheetsHeader`
4. `SheetsList`
5. `Drawer`

另有 1 个整页级验收对象：

- `HomeScrollPage`

### 片段职责

#### `NavBar`

- 左侧菜单入口
- 右侧搜索胶囊入口
- 顶部高度、左右间距、圆角、提示文案、图标尺寸与点击区

#### `Operations`

- 推荐歌单
- 榜单
- 播放历史
- 本地音乐

验收内容包括卡片尺寸、排列、图标、标题、点击反馈和目标路由。

#### `SheetsHeader`

- `我的歌单`
- `收藏歌单`
- 右侧新建按钮
- 右侧导入按钮

验收内容包括 tab 文案、计数、选中态、右侧操作排列与点击语义。

#### `SheetsList`

- 当前 tab 对应歌单列表
- 封面、标题、副文案、右侧动作
- 列表密度、滚动表现、条目可见数量

#### `Drawer`

- 顶部标题
- 分组标题
- 列表项层级
- 入口文案
- 分组间距、滚动、点击行为

#### `HomeScrollPage`

- 顶部到底部为同一个纵向滚动上下文
- `NavBar -> Operations -> SheetsHeader -> SheetsList`
- 底部迷你播放器与页面可视区域关系

## 首页入口最低验收

首页专项只要求“入口可达”，不要求把目标页面一并做到 100% 还原；但每个入口的最低验收必须明确，避免计划范围失控。

### 顶部入口

| 入口 | 最低验收 |
|------|----------|
| 菜单按钮 | 可稳定打开 Drawer，且 `home.drawer.root` 可见 |
| 搜索入口 | 必须进入 `SearchRoute`，且 `screen.search.root` 可见 |

### 四快捷入口

| 入口 | 最低验收 |
|------|----------|
| 推荐歌单 | 必须进入 `RecommendSheetsRoute`，且 `screen.recommendSheets.root` 可见 |
| 榜单 | 必须进入 `TopListRoute`，且 `screen.topList.root` 可见 |
| 播放历史 | 必须进入 `HistoryRoute`，且 `screen.history.root` 可见 |
| 本地音乐 | 必须进入独立的 `LocalRoute`，且 `screen.local.root` 可见；仅在首页内部滚动到某个区域不算达标 |

### Drawer 入口

| 入口 | 最低验收 |
|------|----------|
| 基础设置 | 必须进入 `SettingsRoute` 根页面，且 `screen.settings.root` 可见 |
| 插件管理 | 本 spec 允许进入 `SettingsRoute` 根页面，但页面内必须存在 `settings.pluginManagement.entry`；不要求在本轮完成深链到插件管理子页 |
| 权限管理 | 必须进入 `PermissionsRoute` 根页面，且 `screen.permissions.root` 可见 |

### 范围约束

- 本节定义的是首页专项对“入口可达”的最低要求
- 目标页面的完整视觉还原不在本 spec
- 若某入口当前完全没有等价落点，则允许在实现计划中加入最小必要目标页壳层，以满足首页入口可达
- 对于本地音乐入口，本 spec 明确允许新增最小 `LocalRoute` 壳页，并复用当前首页中的本地音乐内容能力

### `LocalRoute` 最小边界

若当前 Android 导航中不存在独立本地音乐页，则首页专项允许新增最小 `LocalRoute`。

该壳页的最低边界为：

- 暴露 `screen.local.root`
- 呈现现有本地音乐列表能力
- 支持从首页“本地音乐”入口独立进入
- 不要求在本轮完成该页面的完整 fidelity 收敛

## 验收与证据体系

### 证据包结构

每个首页片段必须拥有自己的证据包。证据包至少包含：

- 对应 RN 源码路径
- RN 黄金样本截图
- RN 同时刻 `uiautomator dump`
- Android 截图
- Android 同时刻 `uiautomator dump`
- 差异清单
- 语义契约

整页验收对象 `HomeScrollPage` 同样需要完整证据包。

### 差异关闭原则

首页专项不再使用 `✅/⚠️/❌` 评分作为完成标准，而改用“未闭合差异清单”。

只有当某片段满足以下条件，才算闭合：

- 结构一致
- 文案一致
- 层级顺序一致
- 可点击节点一致
- 视觉误差全部落在预设容差内

任何未被显式容忍的偏差，都视为未闭合差异。

### 容差原则

容差只允许出现在以下维度：

- 尺寸
- 间距
- 圆角
- 颜色
- 字号

### 默认容差与度量方法

若后续计划未为某个片段单独覆写，默认使用以下容差：

| 维度 | 默认容差 | 判定方法 |
|------|----------|----------|
| 结构/节点/顺序 | `0` 容差 | 以 RN/Android dump 对比，必须一致 |
| 文案 | `0` 容差 | 以截图和 dump 交叉确认，必须一致 |
| 点击目标 | `0` 容差 | 以入口清单和实际导航结果确认，必须一致 |
| 尺寸/间距/圆角 | 绝对误差 `<= 2dp`；满宽级区域允许 `<= 4dp` | 优先对照源码值与截图量测，必要时以黄金样本截图像素换算 |
| 字号 | 绝对误差 `<= 1sp` | 对照设计 token、源码值和截图视觉结果 |
| 颜色 | 优先同资源/同 token/同 hex；若需截图采样，单通道 RGB 差值 `<= 8`，alpha 差值 `<= 0.03` | 优先看资源与 token 是否一致，其次做截图采样 |
| 图标/静态图片资源 | 必须使用同资源或从原工程拷贝的等价资源 | 不接受手绘近似替代 |

度量优先级：

1. 源码与资源值一致性
2. 同时刻截图与 dump
3. 黄金样本环境下的人工复核

若不同度量方式冲突，以更接近源码和原始资源的一方为准。

容差不能用于掩盖：

- 错误的信息架构
- 缺失的节点
- 文案不一致
- 可点击区域错误
- 列表密度与滚动上下文错误

## 首页结构设计

### 目标结构

Android 首页将收敛为与 RN 对齐的单一纵向滚动结构：

`HomeScreenScaffold`
→ `HomeNavBar`
→ `HomeOperations`
→ `HomeSheetsSection`

不再以当前的 `TabRow + HorizontalPager` 作为首页主信息架构。

### 外层骨架

`HomeScreenScaffold` 负责：

- `Drawer` 容器
- 顶部安全区处理
- 页面背景
- 与现有底部迷你播放器协同布局
- 单一纵向滚动容器

### 内容块

#### `HomeNavBar`

仅负责：

- 打开 Drawer
- 打开搜索页

不承载其他状态和业务逻辑。

#### `HomeOperations`

仅负责四快捷卡片渲染与入口事件派发。

#### `HomeSheetsSection`

负责：

- tab 头部
- 右侧操作
- 当前 tab 列表

`SheetsHeader` 与 `SheetsList` 在组件上可拆，但应属于同一业务块，保持与 RN 一致的阅读顺序和滚动关系。

#### `HomeDrawerContent`

独立承载 Drawer 的标题、分组标题、列表项与页面跳转。

### 底部迷你播放器边界

当前底部迷你播放器并非首页局部实现，而是 `MainActivity` 中 `Scaffold.bottomBar` 承载的既有 `MiniPlayer`。

边界定义如下：

- 首页专项只负责处理首页内容与迷你播放器的共存关系
- 首页内容不得被迷你播放器遮挡、裁切或产生错误的可视区域计算
- 迷你播放器内部视觉、控件样式与播放器业务逻辑，不在本次首页专项内重做
- 迷你播放器的实际占位，以运行时现有 `Scaffold.bottomBar` 行为为准，而不是首页另行定义一个“预留高度”

## 数据适配设计

### 首页专用适配层

首页歌单区不应直接消费当前 Android 的原始 `PlaylistRepository` 输出，而应增加首页专用适配层，例如 `HomeSheetsUiModel`。

其职责是把现有 Android 数据能力组织成 RN 首页所需的两组语义：

- `我的歌单`
- `收藏歌单`

### 适配原则

- 首页 UI 只依赖首页语义模型，不直接拼接 repository 原始数据
- 如果当前 Android 数据层缺乏完整“收藏歌单”能力，则应在首页层显式建模缺口
- 不允许继续用 `播放列表` 语义冒充 `收藏歌单`

### 当前缺口处理

若实现阶段发现 Android 现有数据层暂时无法等价支撑 RN `收藏歌单`，应优先采用以下顺序：

1. 增加首页适配能力
2. 增加必要数据支撑
3. 明确受控空态

不允许把错误结构继续保留在首页主布局中。

## Compose 语义契约

本次首页专项允许引入语义标记，但语义标记的目标不是引入新的产品行为，而是提升可访问性与可观测性。

### 语义设计目标

1. 增强 `uiautomator dump` 可读性
2. 为截图与 dump 提供同一时刻的结构证据
3. 让首页片段具备稳定的自动化抓取锚点

### 语义覆盖范围

首页至少为以下节点建立语义契约：

- 页级容器
- 分区标题
- 菜单按钮
- 搜索入口
- 四快捷卡片
- `Sheets` tab
- 新建/导入按钮
- 歌单列表项
- Drawer 分组标题
- Drawer 列表项

### 语义规则

- 有文本的标准按钮优先复用内建语义
- 自定义点击容器补充明确 role 和点击语义
- 纯装饰图标与背景节点显式静默
- 需要整体读取的复合节点，在不损失信息的前提下进行合并
- 不为测试便利而改变用户可见层级

### 语义命名方案

首页专项不要求 RN 与 Android 的原始 View 树完全同构；dump 比对口径固定为“语义锚点集合 + 顺序 + 状态”，而不是整棵原始树逐节点强行相等。

首页专项的权威锚点集合是平台无关的 canonical anchor key，而不是某一侧框架原生节点名。

命名约定：

- 页面根节点：`screen.<route>.root`
- 首页片段根节点：`home.<fragment>.root`
- 首页动作节点：`home.<fragment>.<action>`
- Drawer 动作节点：`home.drawer.<action>`
- 目标页根节点：`screen.<route>.root`

### 双侧锚点策略

Android 侧：

- 通过 Compose 语义标记实现 canonical anchor key
- 以语义节点作为 dump 对比的直接来源

RN 侧：

- 优先复用现有 `accessibilityLabel`、可见文本和可稳定定位的节点
- 若现有 RN dump 无法稳定映射到 canonical anchor key，允许在 RN 工程补最小必要的 `testID` 或无用户感知的可访问性标记
- 若不修改 RN 工程，则必须提供一份权威映射清单，将 RN 源码节点、RN dump 线索与 canonical anchor key 建立稳定对应

权威映射应落在一份独立、可版本化的 RN 锚点映射文档中。

该文件至少记录：

- canonical anchor key
- RN 对应源码路径
- RN 定位方式：`testID`、`accessibilityLabel`、可见文本或结构描述
- 对应截图状态

结构一致性的判断口径因此固定为：

- Android dump 中的 canonical anchor 集合
- RN dump 或 RN anchor map 可恢复出的 canonical anchor 集合
- 两侧按同一 canonical anchor key、顺序和状态进行比较

首页首批必备锚点包括：

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
- `home.drawer.root`
- `home.drawer.settings`
- `home.drawer.pluginManagement`
- `home.drawer.permissions`

目标页最小锚点包括：

- `screen.search.root`
- `screen.recommendSheets.root`
- `screen.topList.root`
- `screen.history.root`
- `screen.settings.root`
- `settings.pluginManagement.entry`
- `screen.permissions.root`
- `screen.local.root`

### dump 比对口径

dump 比对时，至少检查以下字段：

- 锚点是否存在
- 锚点顺序是否符合 spec
- 锚点是否可点击
- 锚点文本或 `contentDescription`
- 选中态与可见态

不把以下内容作为首页专项的刚性比对对象：

- 整棵底层原始 View 树
- 平台自动插入的中间语义节点
- 与功能无关的系统辅助节点

### Compose 约束

结合首页专项涉及的 Compose 约束，设计阶段固定以下规则：

- 可点击入口保底满足 `48dp × 48dp` 触达区；若 RN 视觉更小，使用外扩点击区
- 列表项使用稳定 key，避免切换 tab 或变更列表时状态串位
- `Modifier` 顺序作为验收关注项，因为它直接影响背景、圆角、padding 和点击区
- 首页滚动容器保持单一纵向滚动上下文，避免再次退化为视觉相似但结构不同的分页布局

## 截图与 dump 取证规范

### 取证原则

每个首页状态必须同时产出：

- 一张截图
- 一份同一时刻的 `uiautomator dump`

截图负责视觉对比，dump 负责结构、文案、顺序和点击节点对比。

### 取证对象

至少覆盖以下对象：

- 首页首屏
- 首页滚动到 `SheetsList` 可见区域
- Drawer 打开态
- 首页关键入口点击前状态

### 截图范围与裁剪口径

截图对比默认以应用内容区域为准，不把系统状态栏和系统导航栏纳入首页视觉差异判断。

规则如下：

- 原始全屏截图可以保留归档
- 用于视觉对比的标准截图，应裁剪到应用内容区域
- 顶部裁剪边界以首页内容开始位置为准，不比较系统时间、电量、运营商等不稳定信息
- 底部应保留应用自身迷你播放器，但不比较系统导航栏区域
- 若某轮采集改用了沉浸式或系统栏显示策略，必须在 manifest 中记录

### RN 侧取证流程

RN 参考应用包名固定为：

`fun.upup.musicfree`

Android 原生应用包名固定为：

`com.zili.android.musicfreeandroid`

RN 侧证据采集要求：

1. 使用与 Android 侧相同的黄金样本设备
2. 先恢复到 `golden-data-state.md` 描述的数据态
3. 通过稳定导航进入目标首页状态
4. UI 静止后，连续执行一组采集：
   - 原始截图
   - `uiautomator dump`
   - 基于统一口径的内容区裁剪截图
5. 采集过程中不再插入新的点击或滚动，保证截图与 dump 属于同一稳定时刻

RN 与 Android 双侧都应遵守同样的落盘目录、命名规则和裁剪规则。

### 调试基础设施

允许纳入正式方案的基础设施包括：

- Android 截图脚本
- Android `uiautomator dump` 脚本
- RN 侧截图与 dump 采集说明
- 首页语义标记规范
- 差异清单模板

这些能力属于首页专项正式交付的一部分，不视为一次性辅助文件。

### 证据包落盘约定

首页专项应有一套独立的证据包目录，用于存放：

- RN 侧截图与 dump
- Android 侧截图与 dump
- 差异清单
- manifest 与锚点映射

具体目录名不在本设计中固定，但要求 RN、Android、diff、manifest 四类资产分组清晰、可长期维护。

推荐命名规则：

- 截图：`<state>-<fragment>.png`
- dump：`<state>-<fragment>.xml`
- 差异清单：`<state>-<fragment>.md`

其中：

- `state` 例如 `home-top`、`home-sheets`、`drawer-open`
- `fragment` 例如 `nav-bar`、`operations`、`sheets-header`、`sheets-list`、`home-scroll`

### 对比产物定义

每个首页片段的最终差异清单必须是结构化产物，至少包含以下字段：

- 片段名
- 目标状态名
- canonical anchor key 列表及顺序
- RN 证据来源
- Android 证据来源
- 文案
- 可点击性
- 选中态/可见态
- 尺寸
- 间距
- 圆角
- 字号
- 颜色 token 或 hex
- 图标/静态资源来源
- 当前差异结论：闭合 / 未闭合

各字段的证据优先级固定为：

1. 源码与资源值
2. canonical anchor map / dump
3. 裁剪后的标准截图
4. 黄金样本环境下的人工复核

推荐为每个 `<state>-<fragment>` 维护独立差异清单模板文件。

## 错误处理与退化策略

首页专项虽然以还原为核心，但仍需定义受控退化规则，避免在能力缺口出现时重新滑回错误结构。

### 数据缺口

- 若 `收藏歌单` 数据暂不可得，允许进入受控空态
- 受控空态必须保留 RN 对应的信息架构、tab 结构和操作入口
- 不允许以 `播放列表`、`本地音乐列表` 或其他不等价内容替代 `收藏歌单`

### 资源缺口

- 若目标 UI 资源可从原 MusicFree 工程直接复用，则优先复用
- 若短期内无法直接复用，允许在实现阶段记录为阻塞项
- 不允许为了绕过资源缺口而改用明显不同的近似视觉资源，并仍宣称 100% 还原

### dump 可观测性不足

- 若默认 Compose 语义不足以稳定反映结构，允许补充受控语义标记
- 语义补强只能增强可观测性，不能改变用户可见行为和视觉层级

### 导航与交互差异

- 允许少量 Android 原生化交互细节
- 但入口位置、操作顺序、目标页面和用户可感知结果必须与 RN 保持一致

允许的原生化细节包括：

- ripple 样式差异
- overscroll/回弹物理效果
- 点击反馈时长的轻微差异
- Drawer 手势灵敏度差异

不允许的差异包括：

- 入口位置变化
- 操作顺序变化
- 目标路由变化
- 页面可见内容层级变化
- 需要用户多一步才能到达原本一步可达的结果

## 测试与验证要求

首页专项的验证分为 4 层：

### 1. 适配层测试

- 覆盖 `HomeSheetsUiModel` 等首页适配逻辑
- 重点验证 `我的歌单 / 收藏歌单` 语义映射是否正确
- 重点验证在数据缺口下是否进入受控空态，而不是错误替代结构

### 2. 组件与语义测试

- 覆盖首页关键节点的语义契约
- 验证菜单、搜索、四快捷卡片、tab、歌单项、Drawer 项存在稳定可抓取语义
- 验证关键入口的点击区不低于最小触达要求

### 3. 取证链路验证

- 验证截图脚本可稳定生成当前页面截图
- 验证 dump 脚本可稳定生成同一时刻结构快照
- 验证截图与 dump 可以映射到同一首页片段

### 4. 人工验收

- RN 黄金样本截图与 Android 截图并排检查
- RN dump 与 Android dump 逐片段对比
- 更新未闭合差异清单并得出当前闭合结论

## 迭代顺序

首页专项按以下顺序收敛：

1. 基线采集
2. 首页结构重组
3. `NavBar`
4. `Operations`
5. `SheetsHeader`
6. `SheetsList`
7. `Drawer`
8. 整页联调
9. 证据回归

顺序说明：

- 先做基线采集，避免后续比较失真
- 先做结构重组，避免在错误架构上抠视觉细节
- `Sheets` 区晚于结构调整但早于最终整页联调，因为这是当前首页最大结构偏差源

## 验证闭环

每轮首页收敛后都必须更新以下内容：

- RN 黄金样本截图
- RN dump
- Android 截图
- Android dump
- 差异清单
- 当前闭合结论

首页专项只有在以下条件全部满足时才可标记完成：

- 5 个首页片段均无未闭合差异
- `HomeScrollPage` 无未闭合差异
- 首页与 Drawer 的关键入口可达
- 证据包已更新到最新实现状态

## 风险与约束

### 主要风险

1. 当前 Android 数据层对 `收藏歌单` 语义支持不足
2. Compose 默认语义对自定义布局的结构表达不一定足够
3. 若不锁定黄金样本设备，`rpx` 对齐会持续漂移
4. 若继续保留现有双页结构，后续任何视觉微调都无法真正闭合差异

### 应对策略

- 使用首页专用适配层隔离数据缺口
- 引入受控语义标记增强 dump 取证
- 所有首页验收以单一黄金样本设备为准
- 把结构重组作为首页专项前置步骤

## 结论

首页 100% 还原不是一次纯视觉打磨任务，而是一个带证据链的结构收敛任务。

本设计将首页专项固定为：

- 以 RN 源码、RN 截图和 `uiautomator dump` 共同作为硬基准
- 以单一滚动首页结构替换当前错误的信息架构
- 以首页适配层承接 `我的歌单 / 收藏歌单` 语义
- 以 Compose 语义契约提升自动化取证能力
- 以“无未闭合差异”作为完成标准

在该设计获批后，下一步应进入实现计划阶段，将上述结构、证据链和验证流程拆成可执行任务。
