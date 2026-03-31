# Phase 0 Research: 插件能力对齐（添加-更新-搜索）

## Decision 1: 以“能力链路矩阵”作为对齐主工件

- Decision: 采用统一能力矩阵管理对齐范围，矩阵覆盖 `添加插件 -> 更新插件 -> 插件搜索歌曲` 全链路，并为每个能力点绑定验收证据。
- Rationale: 仅靠代码完成无法证明“完全对齐”；能力矩阵可将原版行为、Compose 现状、目标状态和测试证据对齐到同一视图。
- Alternatives considered:
  - 仅使用需求列表：缺少对比维度，无法表达“与原版差距”。
  - 仅使用测试用例：无法在用例之外表达范围边界与优先级。

## Decision 2: 插件添加能力对齐范围固定为三类入口

- Decision: 添加能力必须覆盖本地文件、插件 URL、订阅列表三类入口，作为“添加能力完全对齐”的最低门槛。
- Rationale: 原版用户实际依赖这三类入口；若缺失任一入口，会导致迁移用户无法完成核心操作。
- Alternatives considered:
  - 只保留 URL 安装：无法覆盖本地插件与订阅驱动场景。
  - 先做订阅后补本地：会让“完全对齐”结论不成立。

## Decision 3: 更新能力采用“单插件 + 批量”双路径

- Decision: 更新能力同时包含单插件更新与批量更新，并要求失败可恢复、失败原因可见。
- Rationale: 原版既支持单点更新也支持集中维护；仅做单点会增加用户维护成本，且与原版体验不一致。
- Alternatives considered:
  - 仅批量更新：无法精确处理单插件异常。
  - 仅单插件更新：不满足批量维护诉求。

## Decision 4: 订阅验证使用用户指定地址作为默认基线

- Decision: 订阅链路验证默认使用 `https://13413.kstore.vip/yuanli/yuanli.json`。
- Rationale: 用户已明确提供可用订阅地址，能快速建立跨团队一致的复现与验收输入。
- Alternatives considered:
  - 使用随机公开订阅：稳定性与可复现性不足。
  - 仅使用本地模拟数据：不能证明真实订阅链路可用。

## Decision 5: 搜索能力对齐以“可搜索插件集合 + 分页一致性”为核心

- Decision: 搜索对齐核心为两点：可选插件集合规则一致、搜索分页行为一致。
- Rationale: 搜索差异主要发生在插件可选条件和分页状态处理，优先收敛这两处可避免表面可用但行为偏移。
- Alternatives considered:
  - 只校验首屏结果：不能覆盖分页与状态收敛问题。
  - 只校验结果数量：不能覆盖插件选择与错误恢复策略。

## Decision 6: 测试策略采用“三层验证 + 发布门禁”

- Decision: 使用单元测试、集成测试、人工端到端验收三层验证，并将 P1 能力点 100% 通过作为发布门禁。
- Rationale: 插件能力依赖运行时脚本与真实网络输入，仅单一测试层难以覆盖真实风险。
- Alternatives considered:
  - 只做自动化：无法覆盖交互路径与真实可用性观察。
  - 只做人工验收：回归成本高且不可重复。

## Matrix Status Semantics

- `Aligned`:
  - Compose 与 Original RN 在同一能力点的可观察行为一致
  - 且已有自动化 + 人工证据
- `Partially Aligned`:
  - Compose 覆盖了能力的一部分，或边界行为仍存在偏差
  - 或证据尚不完整
- `Not Aligned`:
  - Compose 缺少核心行为，或行为与 Original RN 明显不一致
  - 必须阻断 P1 发布门禁

状态更新规则：

1. 先补 `evidence-log.md` 证据
2. 运行 `scripts/convergence/plugin-parity/validate-matrix.sh`
3. 再更新 `parity-matrix.md` 的 `Gap Status`

## Open Questions Status

本特性计划阶段无未决 `NEEDS CLARIFICATION` 项，进入 Phase 1 设计。
