# MusicFree Android 旅程工作流设计文档

## 概述

本文档定义 MusicFreeAndroid 后续复刻 RN 版 MusicFree 的主工作流。目标不是单纯提高页面完成数，而是让每次迭代都围绕真实用户旅程推进，并能同时提升：

- 迭代速度
- 功能完整性
- UI 还原度
- 运行时验收通过率
- 集成测试覆盖率
- 自动化执行稳定性

本设计在当前仓库真实状态上成立，不依赖已移除的 `scripts/convergence` 或 `docs/convergence` 体系。

## 设计目标

### 核心目标

1. 以 `superpowers` 作为主流程，而不是让目录结构主导流程
2. 将 backlog 的最小推进单元从“页面”改为“用户旅程”
3. 将“完成”拆分为 `Functional Done` 与 `Fidelity Done`
4. 在编码前强制补齐最小旅程资产，避免“先写后补”
5. 将集成测试与运行态证据提升为主验收依据
6. 让 AI 的自动执行围绕单条旅程资产工作，而不是依赖全局散脚本

### 非目标

- 不一次性建设完整的通用自动化平台
- 不恢复旧的 `convergence` 目录结构
- 不以页面数量作为主进度指标
- 不用主观截图相似度替代结构、行为和数据对齐验证

## 已确认的关键决策

- 主流程：`superpowers`
- 工作流形态：`brainstorming -> design/spec -> implementation plan -> execution -> review -> closeout`
- 单条旅程资产容器：`specs/<journey-id>/`
- `spec-kit` 不是主流程，只保留 `specs/<journey-id>/` 作为旅程资产目录
- 编码前强制具备的文件：
  - `journey-spec.md`
  - `rn-mapping.md`
  - `verification-matrix.md`
  - `plan.md`
- 测试主张：`集成测试优先于单元测试`
- 验证节奏：`分层阻塞`

## 核心原则

### 1. RN 是唯一业务与视觉真相来源

Android 侧的任何旅程工作开始前，都必须先分析 RN 源码、RN 运行态和当前 Android 差距。截图只是视觉锚点，不能取代源码和流程分析。

### 2. 用户旅程是最小交付单元

每轮迭代都必须围绕一条可独立验收的用户旅程，例如：

- `插件安装 -> 搜索 -> 播放 -> 暂停`
- `首页歌单浏览 -> 详情 -> 播放`
- `搜索结果 -> 添加到歌单 -> 返回首页可见`

页面或技术模块只有在它本身是旅程的一部分，或明确属于 `Enabler` 时，才进入近期工作。

### 3. “完成”必须显式分为双状态

每条旅程都必须同时维护：

- `Functional Done`
- `Fidelity Done`

禁止使用单一 `Done` 状态掩盖“功能已通但保真未收敛”或“实现已写但验证未完成”的中间状态。

### 4. 先补最小验证能力，再推进实现

高优先级旅程开工前，必须先识别并补齐当前旅程真正需要的最小能力，例如：

- 真实数据基线说明
- 语义锚点
- 关键日志锚点
- 最小集成测试
- 必要的 restore / capture / verify 手段

### 5. 集成测试是主验收证据

单元测试负责局部正确性；集成测试负责证明真实用户旅程成立。对 MusicFreeAndroid 这类插件、播放、UI、持久化强耦合应用，真正决定验收结论的是旅程级验证。

### 6. 运行时证据优先于乐观判断

功能可以通过编译、单测、spec review、code review，但仍可能在运行时失败。因此运行态证据必须进入标准 gate。

### 7. 文档必须跟随旅程收敛

每轮结束后都必须回写：

- 当前旅程状态
- 新发现的前置依赖
- 失效文档或失效 backlog
- 新增且已证明可复用的验证能力

### 8. 默认自治执行，不要求用户反复确认

该工作流的默认执行方式应为：

- 用户确认方向、范围或计划后，agent 继续自主推进
- agent 依靠内部 gate、自检和复核机制确认阶段完成
- 不把每个中间步骤都抛回给用户做人工确认

只有在以下情况才应再次要求用户确认或决策：

- 目标范围发生变化
- 出现无法自行消解的需求歧义
- 多条技术路线成本或风险差异显著
- 需要执行高风险、破坏性或不可逆操作
- 发现用户已有决策与当前证据直接冲突

换句话说，用户主要确认：

- 方向
- 关键约束
- 最终产物

而不是替 agent 反复确认每一个中间步骤。

## 信息架构

新的工作流分成两层：

- `docs/superpowers/`
- `specs/<journey-id>/`

### `docs/superpowers/` 的职责

这里只放全局方法文档，而不是单条旅程的执行资产。

建议职责：

- `docs/superpowers/specs/`
  放方法级设计文档，例如当前 workflow 设计
- `docs/superpowers/plans/`
  放跨旅程或方法级 rollout plan

规则：

- 不在 `docs/superpowers/` 中堆单旅程的运行态证据
- 不把单旅程的验证明细散落进方法文档

### `specs/<journey-id>/` 的职责

这里是单条旅程的资产包，而不是主流程入口。主流程仍由 `superpowers` 驱动。

推荐示例：

```text
specs/
  portfolio.md
  j-plugin-search-play/
    journey-spec.md
    rn-mapping.md
    verification-matrix.md
    plan.md
    fixtures/
    tools/
    evidence/
    review.md
```

### `Journey ID` 与目录命名

推荐规则：

- `Journey ID`：大写业务标识，例如 `J-PLUGIN-SEARCH-PLAY`
- 目录名：小写 kebab，例如 `j-plugin-search-play`

同一条旅程在所有文档、测试、review 中都应使用同一个 `Journey ID`。

### `Journey` 与 `Enabler` 的边界

判定规则如下：

- 若一项工作可以被描述为“用户从入口出发，完成一条可独立验收的行为链”，它应成为新的 `Journey ID`
- 若一项工作本身不形成完整用户行为闭环，而是用于解锁、稳定或验证多条旅程，它应成为 `Enabler`

例子：

- `插件安装 -> 搜索 -> 播放 -> 暂停` 是 `Journey`
- `语义锚点补齐`、`真实数据基线说明`、`播放器状态可观测性` 是 `Enabler`

## 编码前的强制资产

一条旅程只有在以下 4 个文件都存在且内容完整时，才允许进入编码：

1. `journey-spec.md`
2. `rn-mapping.md`
3. `verification-matrix.md`
4. `plan.md`

### `journey-spec.md`

必须包含：

- 范围
- 非目标
- 旅程入口
- 当前 Android 状态
- `Functional Done`
- `Fidelity Done`

### `rn-mapping.md`

必须包含：

- RN 源码路径
- Android 对应入口与文件
- UI 结构映射
- 业务流程映射
- 数据结构或关键参数对齐点
- 当前差异和未知点

### `verification-matrix.md`

必须包含：

- `Functional Done` 必过项
- `Fidelity Done` 必过项
- 最小集成测试集合
- 所需真实数据或受控数据
- 日志、截图、dump、断言需求
- 轻量 gate 与重型 gate 的划分

### `plan.md`

必须包含：

- 文件级拆分
- 任务顺序
- TDD / 集成测试顺序
- 共享热点文件
- 验证命令
- 提交节奏

## 单旅程标准工作流

新的标准节奏固定为：

`brainstorming -> journey spec -> rn mapping -> verification matrix -> implementation plan -> execution -> evidence -> review -> backlog update`

### 自治执行约束

在单旅程工作流中，默认采用“少打扰、强自检”的执行方式：

- `brainstorming` 结束并锁定本轮范围后，后续优先由 agent 自主推进
- `journey-spec`、`rn-mapping`、`verification-matrix`、`plan` 的完整性由内部检查项保证
- `execution` 阶段通过测试、日志、运行态证据和 review 自我确认，不依赖用户逐步放行
- `closeout` 阶段由 agent 先给出自检结论，再向用户汇报最终结果和残留风险

### 阶段 1：Brainstorming

输入：

- 当前 `Journey ID`
- RN 入口
- Android 现状
- 本轮目标状态

输出：

- 本轮范围
- 非目标
- 风险
- 需要补的 `Enabler`
- 本轮完成定义

落地文件：`journey-spec.md`

### 阶段 2：RN Mapping

这一步不是可选项。其目标是固定 RN 真相源并避免后续凭记忆实现。

落地文件：`rn-mapping.md`

### 阶段 3：Verification Matrix

这一步决定“这条旅程如何证明真的做完了”。

落地文件：`verification-matrix.md`

### 阶段 4：Implementation Plan

只有在前三项完成后，才进入 plan。

落地文件：`plan.md`

### 阶段 5：Execution

执行时只围绕当前旅程推进，规则如下：

- 先补最小验证能力，再补功能
- 先让最小集成测试成立，再扩展
- 运行态验证优先于乐观判断

### 阶段 6：Evidence

实现和验收过程中，把旅程证据回写到 `evidence/`，例如：

- 截图
- `uiautomator dump`
- 日志摘要
- 差异记录
- 测试结果摘要

### 阶段 7：Review / Closeout

最后写 `review.md`，记录：

- 这轮真正完成了什么
- 哪些差异还没闭合
- 暴露了哪些新的 `Enabler`
- 哪些文档或 backlog 已经过时

然后更新全局 portfolio。

## 全局 Backlog 治理

新的结构下，全局 backlog 入口建议固定为：

`specs/portfolio.md`

它只做索引和排期，不复制单旅程细节。

### 推荐泳道

- `Now`
- `Next`
- `Later`
- `Infra / Enablers`

规则：

- `Now`：最多 1 到 2 条旅程
- `Next`：已拆清、可进入 brainstorming
- `Later`：尚未拆清或依赖不成熟
- `Infra / Enablers`：明确阻塞真实旅程的能力缺口

### 每条旅程的最少字段

- `Journey ID`
- `Journey Name`
- `User Value`
- `Dependencies`
- `Functional Status`
- `Fidelity Status`
- `Current Gap`
- `Next Action`
- `Asset Path`

### 更新责任与时机

`specs/portfolio.md` 不是静态文档，必须在以下时机更新：

1. 一条旅程从 `Later` 进入 `Next`
2. 一条旅程开始进入 `brainstorming`
3. 一条旅程进入 `In Progress`
4. 一条旅程达到 `Functional Done`
5. 一条旅程达到 `Fidelity Done`
6. 某个 `Enabler` 被创建、拆分、关闭或确认不再阻塞

默认由当前执行该旅程的 implementer 在 closeout 时更新；如果本轮只做设计而未进入执行，则由产出该设计文档的人更新。

### 状态模型

`Functional Status`

- `Not Started`
- `In Discovery`
- `Ready for Plan`
- `In Progress`
- `Functional Done`

`Fidelity Status`

- `Not Started`
- `In Analysis`
- `In Progress`
- `Fidelity Done`

这两个状态必须并行维护，不能互相覆盖。

## Enabler 治理规则

以下能力不应作为“顺手补一下”的零散工作，而应进入 `Infra / Enablers`：

- 真实数据恢复
- AI 自动执行入口
- 日志锚点
- 语义锚点
- 集成测试夹具

规则：

1. 只有当某个 `Enabler` 明确阻塞 `Now / Next` 旅程时，才允许进入近期工作
2. 每个 `Enabler` 必须映射到至少一条真实旅程
3. 不允许脱离旅程，独立膨胀成大而全平台工程

## 验证体系

### 测试代码的归属

代码级测试仍按 Android 工程正常组织：

- 单元测试：各模块 `src/test`
- 模块集成测试：各模块 `src/androidTest`
- 旅程级 instrumentation / Compose 测试：建议集中在 `app/src/androidTest/.../journeys/`

### 旅程级资产的归属

每条旅程目录建议在实现阶段逐步补齐：

- `fixtures/`
- `tools/`
- `evidence/`
- `review.md`

规则：

- 先写在单旅程目录内
- 只有在被两条及以上旅程复用后，才允许上提为全局共享脚本或模板

### 四层验证模型

1. `Logic Unit Tests`
   验证 mapper、参数转换、队列算法、纯状态机逻辑
2. `Module Integration Tests`
   验证模块内真实依赖组合
3. `Journey Integration Tests`
   直接围绕用户旅程编写，是主验收层
4. `Fidelity Verification`
   截图、`uiautomator dump`、结构与交互差异闭环

### 最低要求

每条高优先级旅程至少应具备：

- 1 条主路径测试
- 1 条关键异常路径测试
- 1 份 `verification-matrix.md`
- 1 组最小 `evidence/`

## 分层 Gate

### `Pre-Code Gate`

编码前必须存在并完成：

- `journey-spec.md`
- `rn-mapping.md`
- `verification-matrix.md`
- `plan.md`

并且 agent 必须自行检查：

- 四个文件彼此不冲突
- `plan.md` 没有越出 `journey-spec.md` 定义范围
- `verification-matrix.md` 足以支持后续自检

### `Dev Gate`

日常开发中每次都尽量快过：

- 受影响模块编译
- 受影响单元测试
- 至少 1 条最小旅程集成测试
- 关键日志 / 语义锚点未破坏

该 gate 默认由 agent 自行执行与判断，不要求用户逐步确认。

### `Functional Gate`

进入 `Functional Done` 前必须具备：

- 主路径集成测试
- 关键异常路径验证
- 可重复的 restore 思路
- 可核对的运行态日志
- 最小结果摘要写入 `evidence/`

该 gate 的结论必须基于证据而不是主观判断；agent 应先自我确认通过，再向用户报告。

### `Fidelity Gate`

进入 `Fidelity Done` 前额外要求：

- RN/Android 截图
- `uiautomator dump`
- 差异记录
- 结构、文案、点击语义闭环

若未达标，agent 应继续收敛或明确记录剩余差异，而不是把“是否算完成”回抛给用户做临场判断。

## 真实数据与 AI 自动执行策略

### 数据分层

推荐继续使用三层策略：

- `Frozen Real`
  用于稳定回归与 `Fidelity Done`
- `Controlled Live`
  用于 `Functional Done` 阶段的受控 live 验证
- `Offline Deterministic`
  用于播放器状态机、队列、暂停恢复等本地可控能力验证

规则：

- `Functional Done` 可以结合 `Controlled Live`
- `Fidelity Done` 必须回到 `Frozen Real`
- 播放 / 暂停等状态机验证优先使用 `Offline Deterministic`

### AI 的执行入口

AI 不应从全局脚本目录“猜测怎么跑”，而应以单条旅程为入口。

每次执行都应优先读取：

- `specs/<journey-id>/verification-matrix.md`
- `specs/<journey-id>/fixtures/`
- `specs/<journey-id>/tools/`
- `specs/<journey-id>/evidence/`

这意味着 AI 面对的是“单条旅程的完整资产包”，而不是一堆全局散脚本。

## 第一批迁移方案

当前仓库状态下，第一批迁移应保持克制，只做能立刻支撑后续迭代的最小集合。

### 1. 建立新的全局入口

新增：

- `specs/portfolio.md`

### 2. 选 1 条旅程做试点

推荐第一条仍然是：

- `J-PLUGIN-SEARCH-PLAY`

理由：

- 价值高
- 覆盖插件、搜索、播放、暂停
- 当前代码里已有较多基础
- 最容易暴露真实数据、AI 自动执行与集成测试问题

### 3. 为试点旅程建立最小资产包

新增：

```text
specs/j-plugin-search-play/
  journey-spec.md
  rn-mapping.md
  verification-matrix.md
  plan.md
```

先不要急于把 `tools/ fixtures/ evidence/ review` 全铺开；等旅程真正进入实现和验证阶段再补。

### 4. 第一批 `Enabler`

建议近期只保留以下四个：

- `E-SEMANTIC-ANCHORS`
- `E-REAL-DATA-BASELINE`
- `E-PLAYER-STATE-OBSERVABILITY`
- `E-JOURNEY-INTEGRATION-HARNESS`

### 5. 旧文档处理原则

- 仍提供业务或技术背景的旧文档可以保留
- 已经失效到会误导执行的旧文档应删除或显式标注失效
- 不再把新的 workflow 内容继续塞回旧的 milestone plan

### 6. 完整旅程资产示例

一条已进入稳定执行状态的旅程，最终资产可形如：

```text
specs/j-plugin-search-play/
  journey-spec.md
  rn-mapping.md
  verification-matrix.md
  plan.md
  fixtures/
    controlled-live-queries.md
  tools/
    restore.md
    verify.md
  evidence/
    functional-smoke.md
    fidelity-gap.md
  review.md
```

这不是要求一开始就把所有文件建齐，而是说明该目录在旅程成熟后应收敛成什么样子。

## 结论

删除旧 `convergence` 之后，正确的工作流不是恢复另一套平行体系，而是：

- 用 `superpowers` 作为主流程
- 用 `specs/portfolio.md` 作为全局旅程入口
- 用 `specs/<journey-id>/` 作为单旅程资产包
- 在编码前强制补齐 `journey-spec / rn-mapping / verification-matrix / plan`
- 把集成测试、运行态证据和保真对比提升为主验收依据
- 让 AI 围绕单旅程资产执行，而不是依赖全局散脚本

这套结构更贴近当前仓库状态，也更适合长期维护。
