# MusicFree Android 旅程驱动迭代工作流设计文档

## 概述

本文档定义 MusicFreeAndroid 在后续长期迭代中的统一工作流，目标是在继续复刻 RN 版 MusicFree 的同时，同时提升：

- 迭代速度
- UI 还原度
- 功能完整性
- 运行时验收通过率
- 自动化验证覆盖率

本文档不描述某一个具体页面或功能的实现方案，而是定义“如何推进任意一个复刻迭代单元”的方法学、产物体系、验证体系与全局治理规则。

## 设计目标

### 核心目标

1. 将 backlog 的基本单位从“页面”改为“用户旅程”
2. 将“完成”拆分为 `Functional Done` 与 `Fidelity Done` 两个显式状态
3. 将集成测试与运行态证据提升为主验收依据，单元测试作为局部正确性补充
4. 在每条高优先级旅程开工前，先补齐最小可复用验证基础设施
5. 让 AI 能基于稳定动作面自动执行真实旅程，而不是依赖临时坐标点击
6. 降低“编译通过、单测通过、代码 review 通过，但运行时验收失败”的返工率

### 非目标

- 不在本设计中一次性建设完整测试平台或完整 CI 体系
- 不把所有页面都先拆成独立页面任务再统一收口
- 不以“页面数量完成率”作为项目主进度指标
- 不以主观截图相似度替代结构、行为和数据对齐验证

## 已确认的关键决策

本次工作流设计基于以下已确认决策：

- 作用范围：`单次功能迭代 SOP + 全局 backlog/优先级治理`
- backlog 切分方式：按 `用户旅程/能力链路` 切分，而非按页面切分
- 完成定义：每条旅程同时追踪 `Functional Done` 与 `Fidelity Done`
- 基础设施节奏：采用 `最小平台先行`，只补当前高优先级旅程必需的验证能力
- 验证节奏：采用 `分层阻塞`，轻量 gate 每次迭代必过，重型 gate 在旅程里程碑与合并前集中执行
- 测试取向：`集成测试优先于单元测试`

## 核心原则

### 1. RN 是唯一业务与视觉真相来源

任何 Android 侧工作开始前，都必须先分析 RN 代码、RN 运行态表现和现有 Android 差距。截图只能作为视觉锚点，不能取代代码侧分析。

### 2. 用户旅程是最小交付单元

一个迭代单元必须是可独立验收的用户旅程，例如：

- `插件安装 -> 搜索 -> 播放 -> 暂停`
- `首页歌单浏览 -> 详情 -> 播放`
- `搜索结果 -> 添加到歌单 -> 返回首页可见`

单个页面或单个技术模块只有在它本身就是旅程的一部分，或明确是旅程前置 `Enabler` 时，才进入近期迭代。

### 3. “完成”必须显式分为双状态

每条旅程都必须维护两个状态：

- `Functional Done`
- `Fidelity Done`

禁止使用单一 `Done` 状态掩盖“功能已通但保真未收敛”或“实现已写但验证未完成”的中间状态。

### 4. 先补最小验证能力，再推进该旅程实现

高优先级旅程开工前，必须补齐该旅程真正需要的最小验证能力，包括但不限于：

- 可恢复数据态
- 语义选择器
- 自动化动作脚本
- 截图与 `uiautomator dump` 采集
- 关键 Logcat 锚点
- 最小集成测试

### 5. 集成测试是主验收证据

单元测试用于防守局部逻辑，集成测试用于证明用户旅程真的成立。对 MusicFreeAndroid 这类“插件 + 播放 + UI + 数据持久化”强耦合应用，真正决定验收结论的，是端到端或近端到端的集成验证，而不是纯函数级测试数量。

### 6. 运行时证据优先于乐观判断

一条旅程即使已经：

- 编译通过
- 单元测试通过
- 代码 review 通过

也仍然可能因为运行态问题未达标。因此运行态证据必须进入必经 gate。

### 7. 文档与 backlog 必须同步收敛

每次迭代结束后，必须回写：

- 当前旅程状态
- 新发现的前置依赖
- 失效文档与失效 backlog
- 新增的验证能力与可复用资产

否则上下文会快速腐化，反过来拖慢后续迭代。

## 全局工作流总览

推荐采用固定的 `Journey Loop`，每次只推进一个用户旅程型迭代单元。

### 阶段总览

| 阶段 | 目标 | 必要输出 |
|------|------|----------|
| `Journey Intake` | 选定旅程并锁定范围、依赖、完成定义 | 旅程卡片、范围、非目标、完成标准 |
| `RN Baseline Analysis` | 建立 RN 与 Android 对齐蓝本 | RN 文件映射、流程图、数据结构对齐表、差异清单 |
| `Minimum Platform Prep` | 建立本旅程可复用最小验证能力 | seed/恢复脚本、语义锚点、动作脚本、证据采集方案 |
| `Functional Implementation` | 打通真实用户路径 | 代码实现、状态流与异常流处理 |
| `Functional Verification` | 证明主路径和关键异常路径可运行 | 集成测试、关键单元测试、日志与状态断言 |
| `Fidelity Convergence` | 收敛结构、交互与视觉差异 | RN/Android 截图与 dump、差异关闭记录 |
| `Closeout Review` | 做集中验收与文档回写 | 重型 gate 结果、review、文档更新、backlog 更新 |

### 阶段顺序的强约束

- 禁止跳过 `RN Baseline Analysis` 直接进入实现
- 禁止在没有最小验证能力的情况下推进高优先级旅程
- 禁止在 `Functional Verification` 缺失时宣称“功能完成”
- 禁止在差异未闭合时宣称 `Fidelity Done`

## 旅程卡片模型

后续应将全局 backlog 重构为 `Journey Portfolio`。每条旅程卡片至少包含以下字段：

| 字段 | 说明 |
|------|------|
| `Journey ID` | 稳定标识，例如 `J-PLUGIN-SEARCH-PLAY` |
| `Journey Name` | 面向人的旅程名称 |
| `User Value` | 该旅程对最终成品的用户价值 |
| `RN Entry` | RN 入口页、核心组件、关键源码路径 |
| `Android Entry` | 当前 Android 入口、现状与缺口 |
| `Dependencies` | 前置旅程、前置子系统、共享热点文件 |
| `Functional Status` | `Not Started / In Progress / Functional Done` |
| `Fidelity Status` | `Not Started / In Progress / Fidelity Done` |
| `Verification Readiness` | seed、脚本、语义锚点、集成测试是否齐备 |
| `Evidence Links` | 关联截图、dump、日志、差异报告 |
| `Open Gaps` | 未闭合问题与阻塞项 |
| `Next Action` | 当前最小推进动作 |

### 推荐泳道

全局 portfolio 建议固定维护 4 条泳道：

- `Now`
- `Next`
- `Later`
- `Infra Queue`

规则如下：

- `Now`：最多 1 到 2 条正在推进的核心旅程
- `Next`：已经完成 Intake、可随时进入开发的旅程
- `Later`：尚未拆清或前置未满足的旅程
- `Infra Queue`：由高优先级旅程显式拉出的最小平台能力

`Infra Queue` 不能独立膨胀成平台大工程，只能服务于近期高优先级旅程。

## 优先级模型

优先级不按“缺几个页面”排序，而按以下 5 个维度综合判断：

1. `User Journey Weight`
   是否属于最终成品中的高频核心路径。
2. `Dependency Leverage`
   完成后是否能解锁更多旅程。
3. `Confidence Gap`
   当前是否存在“看起来差不多，但没有真实运行证据”的风险。
4. `Fidelity Gap`
   与 RN 在结构、交互、视觉上的偏差是否显著。
5. `Platform Reuse`
   本次补的验证能力后续是否可以被多条旅程复用。

### 旅程分类

建议将旅程分为 3 类：

- `Core Journey`
  最接近最终用户价值的主路径，例如插件安装、搜索、播放。
- `Enabler`
  不直接面向最终用户，但能解锁多条旅程，例如 `fileSelector`、seed 恢复、播放器状态快照。
- `Polish / Secondary`
  不会解锁主路径的次级页面或局部保真补差。

优先级规则：

- `Core Journey` 与必要 `Enabler` 优先进入近期迭代
- `Polish / Secondary` 不能挤占核心旅程资源
- 页面问题若本质是前置能力缺口，必须回退为 `Enabler`

## 进入迭代前的 Intake Gate

任何旅程进入开发前，必须满足以下条件：

1. RN 参考源码路径已锁定
2. 主路径与关键异常路径已写清
3. 前置依赖已识别
4. `Functional Done` 与 `Fidelity Done` 标准已写清
5. 最小平台缺口已列出
6. 集成测试思路已存在

禁止出现以下节奏：

- 先写页面，后面再想怎么对齐 RN
- 先写功能，后面再想怎么自动化验证
- 先写代码，后面再决定要不要补集成测试

## 完成定义

### `Functional Done`

一条旅程满足以下条件，才可进入 `Functional Done`：

1. 用户主路径可真实运行
2. 至少 1 条关键异常路径可验证
3. 数据结构、字段与关键参数和 RN 对齐
4. 相关最小集成测试通过
5. 必要单元测试通过
6. 关键运行态日志与状态断言成立

### `Fidelity Done`

一条旅程满足以下条件，才可进入 `Fidelity Done`：

1. 关键 UI 片段与 RN 结构对齐
2. 文案、层级、点击语义与 RN 对齐
3. RN/Android 截图与 `uiautomator dump` 证据闭环
4. 动画、滚动、状态切换的关键差异有明确结论
5. 未闭合差异为 `0`，或剩余差异已被显式降级并记录理由

### 禁止的灰色状态

后续状态表达中禁止出现以下模糊措辞：

- “功能已完成，待补测试”
- “基本完成，后面再看 fidelity”
- “看起来没问题，先算 done”

必须显式写成：

- `Implementation Incomplete`
- `Verification Missing`
- `Functional Done`
- `Fidelity Done`

## 产物体系

建议围绕每条旅程固定产出 6 类文档与资产。

### 1. `Journey Spec`

定义本次旅程的：

- 范围
- 非目标
- RN 入口
- Android 当前入口
- 完成定义
- 风险与前置依赖

### 2. `RN Mapping`

记录：

- RN 相关源码文件
- Android 对应组件映射
- 业务流程图
- 数据结构与参数对齐表
- 当前差异清单

### 3. `Verification Pack`

记录并沉淀：

- seed 数据态
- 恢复脚本
- 截图脚本
- `uiautomator dump` 脚本
- Logcat 关键 tag
- 断言矩阵
- 集成测试清单

### 4. `Implementation Plan`

按依赖顺序拆任务，明确：

- 共享热点文件
- 并行边界
- 可独立验证的小步提交
- 重型验证触发点

### 5. `Evidence Pack`

收集：

- RN 截图
- RN dump
- Android 截图
- Android dump
- 关键日志
- 差异记录

### 6. `Iteration Review`

记录：

- 本轮发现的错误模式
- 假 backlog / 失效文档
- 返工原因
- 应沉淀的新平台能力

### 推荐目录结构

推荐后续新增统一目录：

```text
docs/convergence/
  portfolio.md
  journeys/
    <journey-id>/
      journey-spec.md
      rn-mapping.md
      verification-matrix.md
      manifests/
      evidence/
scripts/convergence/
  <journey-id>/
    restore-*.sh
    capture-*.sh
    verify-*.sh
```

说明：

- `docs/superpowers/specs` 保留设计文档与方法文档
- `docs/convergence` 用于沉淀旅程执行态资产
- `scripts/convergence` 用于沉淀可重复执行脚本

## 真实数据策略

在 MusicFreeAndroid 中，“真实数据”不能只理解为“直接连外网跑一次”。为了兼顾可重复性与真实度，建议明确拆成 3 层数据策略。

### 1. `Frozen Real`

使用真实插件、真实 QuickJS、真实数据库结构、真实播放器状态机，但输入数据和环境被冻结。

适用：

- 日常开发
- `Fidelity Done`
- 稳定回归

典型内容：

- 固定插件集合
- 固定订阅源快照
- 固定搜索词清单
- 固定歌单 seed
- 固定样例媒体

### 2. `Controlled Live`

仍然走真实插件和真实网络，但只使用经过筛选的稳定 live 样本集。

适用：

- `Functional Done` 阶段的烟测
- 合并前活性校验

典型断言：

- 搜索有结果
- 结果可打开
- 可解析播放源
- 播放器能进入 `playing`

不要求断言：

- 第一个结果必须是固定歌曲
- 排名必须完全稳定

### 3. `Offline Deterministic`

使用本地可控夹具验证播放器状态机、队列、暂停恢复和持久化逻辑。

适用：

- 快速回归
- CI
- 与网络无关的能力验证

### 数据策略规则

- `Functional Done` 允许结合 `Controlled Live`
- `Fidelity Done` 必须回到 `Frozen Real`
- 播放/暂停等状态机验证，应优先使用 `Offline Deterministic`
- 外网 live 结果不能承担保真验收基线

## AI 自动执行面设计

不应让 AI 直接自由操作手机界面，而应提供一套稳定的 `执行面`。目标不是让 AI “偶尔点成功”，而是让它能重复证明某条旅程成立。

### 1. `State Restore`

必须支持一键恢复应用到某个已知状态，例如：

- 清空并重建 DataStore / Room
- 安装指定插件集合
- 导入指定歌单
- 恢复当前播放队列
- 设置首页 tab、迷你播放器可见性

推荐实现方式：

- debug-only `adb shell am broadcast`
- debug deep link
- debug menu
- 读取本地 manifest / fixture 的恢复入口

### 2. `Semantic Selectors`

必须优先通过稳定语义节点，而不是坐标，驱动 AI 操作界面。

建议：

- 为关键 Compose 节点补稳定 `testTag`
- 同时保证 `uiautomator dump` 可观测到这些节点
- 关键节点命名稳定，例如：
  - `home.search_entry`
  - `search.result.0`
  - `player.play`
  - `player.pause`

### 3. `Action DSL`

应将常见旅程动作抽象成可组合脚本，而不是让 AI 每次重新思考操作细节。

示例：

```text
restore("plugin_search_play_seed")
install_plugin("kuwo")
search("周杰伦")
open_result(0)
play()
assert_player_state("playing")
pause()
assert_player_state("paused")
capture("player-paused")
dump_ui("player-paused")
```

### 4. `Evidence Collector`

每个关键动作后，应支持自动采集：

- screenshot
- `uiautomator dump`
- Logcat 关键 tag
- 当前页面语义树
- 当前播放器状态快照

### 5. `Assertion Layer`

断言必须分两类：

- `Structure Assertion`
  校验节点、层级、文案、数量、可见性
- `Behavior Assertion`
  校验搜索结果、页面跳转、播放状态、持久化结果

### 行为约束

- 坐标点击只能作为临时兜底，不得作为长期主方案
- 新高优先级旅程开工前，必须先补最小语义锚点
- 每条核心旅程都应逐步沉淀为可脚本化动作序列

## 集成测试体系

建议将测试体系明确拆成 4 层。

### 1. `Logic Unit Tests`

用于验证纯逻辑与易回归局部：

- mapper
- 参数转换
- 队列算法
- 状态机纯规则

特点：

- 成本低
- 速度快
- 不能单独代表旅程成立

### 2. `Module Integration Tests`

用于验证模块内真实依赖组合：

- `plugin -> search result parsing`
- `data -> playlist persistence`
- `player -> play/pause/queue transition`

### 3. `Journey Integration Tests`

这是主验收层。测试直接围绕用户旅程编写。

每条高优先级旅程至少应具备：

- 1 条主路径测试
- 1 条关键异常路径测试

示例：

- `安装插件 -> 搜索 -> 打开结果 -> 播放 -> 暂停`
- `首页歌单 -> 详情 -> 播放`
- `搜索 -> 添加到歌单 -> 返回首页可见`

### 4. `Fidelity Verification`

这不是传统代码测试，但它是保真验收必经层。内容包括：

- RN/Android 截图对比
- RN/Android `uiautomator dump` 对比
- 文案、层级、节点、点击语义断言
- 动画/滚动/状态切换采样

### 测试主张

- 集成测试是验收主线
- 单元测试用于压缩局部回归面
- 没有旅程级验证的“功能完成”不成立

## 分层 Gate 设计

### 轻量 Gate

每次提交到旅程分支都应通过轻量 gate。其目标是尽早发现明显错误。

轻量 gate 至少包括：

- 受影响模块编译通过
- 受影响单元测试通过
- 受影响旅程的最小集成测试通过
- 基本静态检查通过
- 关键语义锚点与日志锚点未被破坏

关键规则：

- 一条旅程一旦进入开发，就必须尽早有最小集成测试
- 最小集成测试属于轻量 gate，不是末端补作业

### 重型 Gate

重型 gate 在以下两个时机触发：

- 旅程准备进入 `Functional Done` 或 `Fidelity Done`
- 准备合并到主线

重型 gate 至少包括：

- 全量编译
- 相关模块单元测试
- 旅程级集成测试套件
- 模拟器真实运行验证
- RN/Android 截图对比
- RN/Android `uiautomator dump` 对比
- Logcat 关键链路核对
- 最终 review 与文档回写

## 推荐的验证矩阵

后续每条旅程都应维护 `verification-matrix.md`，明确：

- 哪些验证属于 `Functional Done` 必过
- 哪些验证属于 `Fidelity Done` 必过
- 哪些验证可在合并前集中执行
- 哪些依赖 `Controlled Live`
- 哪些必须基于 `Frozen Real`

示例：`插件安装 -> 搜索 -> 播放`

`Functional Done`

- 固定插件清单安装成功
- 固定查询词返回非空
- 搜索结果可打开
- 能解析播放源
- `play -> pause` 状态流成立

`Fidelity Done`

- 搜索页结构与 RN 对齐
- 结果项结构、文案、点击区与 RN 对齐
- 播放器关键控件与 RN 对齐
- 截图与 dump 差异闭合

## 推荐的首批旅程组合

为了让工作流尽快进入正反馈，建议优先用 2 到 3 条高杠杆旅程做试点。

### 旅程 1：插件安装 -> 搜索 -> 播放 -> 暂停

价值：

- 覆盖插件系统、搜索、播放器、运行态验证
- 是最接近成品能力的核心主路径

推荐前置 `Enabler`：

- 固定插件清单
- 搜索 query manifest
- 播放状态快照
- 最小动作 DSL

### 旅程 2：首页歌单浏览 -> 详情 -> 播放

价值：

- 连接首页 fidelity 与实际播放路径
- 兼顾视觉还原与真实业务闭环

推荐前置 `Enabler`：

- 首页黄金数据态恢复
- 首页语义锚点
- 详情页入口稳定化

### 旅程 3：搜索结果 -> 添加到歌单 -> 返回首页可见

价值：

- 覆盖搜索、数据持久化、首页展示回流
- 能暴露“功能通了但数据回写不完整”的问题

推荐前置 `Enabler`：

- 歌单 seed
- 添加成功后的状态断言
- 首页歌单刷新与可见性验证

### 高优先级 Enabler

建议优先排入 `Infra Queue` 的能力：

- `fileSelector`
- seed 数据恢复
- 语义锚点命名约定
- 通用截图与 dump 采集脚本
- 播放器状态快照与日志锚点

## 度量指标

建议后续至少跟踪以下指标：

1. `Journey Lead Time`
   从进入 `Now` 到 `Functional Done` / `Fidelity Done` 的耗时。
2. `Reopen Rate`
   已完成旅程因运行态或保真问题被重新打开的比例。
3. `Automation Coverage`
   核心旅程中已具备恢复脚本、动作脚本、证据采集的覆盖率。
4. `Integration Test Coverage by Journey`
   高优先级旅程中已具备主路径与异常路径集成测试的比例。
5. `Evidence Completeness`
   已达到 `Fidelity Done` 的旅程中，证据包完整率。

目标不是追求抽象测试覆盖率，而是追求“核心旅程被真实验证的比例”。

## 风险与对策

### 风险 1：平台建设失控，反客为主

对策：

- 只允许高优先级旅程拉动 `Infra Queue`
- 平台能力只做“当前旅程真正需要的最小集合”

### 风险 2：live 数据不稳定导致验证结果飘忽

对策：

- 将数据分层为 `Frozen Real / Controlled Live / Offline Deterministic`
- 保真验收必须回到冻结基线

### 风险 3：AI 自动执行脆弱，过度依赖坐标

对策：

- 优先建设语义锚点
- 用 DSL 驱动动作
- 坐标点击只做临时兜底

### 风险 4：页面视角 backlog 持续回流

对策：

- 强制所有近期工作映射到某条旅程或某个 `Enabler`
- 页面型任务如果不能映射到旅程，就不能进入 `Now`

### 风险 5：文档再次过时

对策：

- 每轮 closeout 必须更新 portfolio、证据包和过时文档
- 已失效文档必须显式删除或标注失效

## 推进建议

推荐按以下顺序落地：

1. 建立 `Journey Portfolio` 与旅程卡片模板
2. 选定首批 2 到 3 条核心旅程
3. 为首条旅程补齐最小验证能力
4. 跑通一条完整 `Journey Loop`
5. 复盘后固化模板、命名约定与脚本目录结构
6. 再扩展到第二、第三条旅程

## 结论

对 MusicFreeAndroid 这样的 RN 复刻项目，最高杠杆的工作流不是“按页面把功能补齐”，而是：

- 按用户旅程切分 backlog
- 用 `Functional Done / Fidelity Done` 双状态管理完成度
- 用最小平台先行的方式建设可复用验证能力
- 把集成测试、运行态证据和保真对比提升为主验收依据
- 让 AI 在稳定动作面上执行真实旅程，而不是临时坐标点按

这套工作流的目标不是把流程变重，而是把返工前移，把“伪完成”尽早暴露，把每次迭代真正变成可验证、可复用、可收敛的工程推进单元。
