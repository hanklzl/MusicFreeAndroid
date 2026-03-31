# Feature Specification: 插件能力对齐（添加-更新-搜索）

**Feature Branch**: `[001-plugin-parity-search]`  
**Created**: 2026-03-30  
**Status**: Draft  
**Input**: User description: "帮我对比目前Compose版本FreeMusic和原版插件能力的差异. 从添加插件到使用插件搜索歌曲, 对比差异. 制订测试和对齐方案, 做到能力完全对齐. 能添加,更新插件, 并通过插件搜索歌曲"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 建立插件能力差异基线 (Priority: P1)

作为产品与测试负责人，我需要一份从“添加插件”到“插件搜索歌曲”的能力对比基线，明确 Compose 版与原版在每个能力点上的状态差异，确保后续对齐范围和验收口径一致。

**Why this priority**: 如果没有统一差异基线，后续“能力完全对齐”无法被客观验证，容易出现已开发但未对齐的遗漏。

**Independent Test**: 仅通过评审能力矩阵和对应验收项即可判断是否完成该故事，无需依赖代码变更落地。

**Acceptance Scenarios**:

1. **Given** 已确定原版与 Compose 版为对比对象, **When** 产出差异基线文档, **Then** 每个能力点都包含“原版状态、Compose 状态、差异级别、目标状态”。
2. **Given** 存在“部分支持”或“缺失”能力点, **When** 差异基线发布, **Then** 每个差异点都绑定明确的对齐验收标准与测试证据要求。

---

### User Story 2 - 添加与更新插件能力对齐 (Priority: P1)

作为终端用户，我希望在 Compose 版里与原版一样可以稳定添加插件、更新插件，并得到可理解的成功或失败反馈。

**Why this priority**: 插件生命周期能力是后续搜索能力的前置条件，不先对齐会直接阻断核心使用路径。

**Independent Test**: 仅执行“添加/更新插件”相关验收用例即可独立判断该故事是否完成。

**Acceptance Scenarios**:

1. **Given** 用户提供有效插件来源, **When** 执行添加插件, **Then** 插件进入已安装列表并可用于后续搜索。
2. **Given** 已安装插件存在可用更新来源, **When** 用户执行更新插件, **Then** 新版本替换旧版本且插件身份保持唯一。
3. **Given** 插件来源不可访问或插件内容无效, **When** 用户执行添加或更新, **Then** 系统保留原有可用插件并返回明确失败原因。

---

### User Story 3 - 通过插件搜索歌曲能力对齐 (Priority: P2)

作为终端用户，我希望在插件可用后，能够选择插件并稳定搜索歌曲，结果行为与原版一致且可复现。

**Why this priority**: “能搜索歌曲”是插件系统对用户可感知的核心价值，需要在添加/更新能力稳定后完成体验闭环。

**Independent Test**: 仅执行“插件搜索歌曲”相关用例即可独立验证该故事，不依赖其他非搜索能力。

**Acceptance Scenarios**:

1. **Given** 至少存在一个可搜索插件, **When** 用户输入关键词并发起搜索, **Then** 返回对应插件的搜索结果或明确空结果状态。
2. **Given** 搜索结果存在多页数据, **When** 用户加载下一页, **Then** 结果追加且页码与结束状态正确。
3. **Given** 插件刚完成更新, **When** 用户再次发起同关键词搜索, **Then** 使用更新后的插件能力并保持可用搜索结果。

---

### User Story 4 - 对齐测试与发布准入 (Priority: P3)

作为质量负责人，我希望有一套覆盖差异点的测试方案和准入门槛，确保“能力完全对齐”不是口头结论，而是可重复验证的结果。

**Why this priority**: 没有统一测试方案，后续迭代容易引入回归，导致“已经对齐”的能力再次偏离原版。

**Independent Test**: 仅执行对齐测试计划并核对通过门槛即可判断该故事是否完成。

**Acceptance Scenarios**:

1. **Given** 差异基线中的所有能力点均有测试映射, **When** 执行一次完整回归, **Then** 可输出每个能力点的通过/失败结论及证据。
2. **Given** 存在任一关键能力未通过, **When** 评估发布状态, **Then** 功能标记为未完成对齐并阻断发布。

---

### Edge Cases

- 插件来源地址可访问但返回内容不是有效插件定义时如何处理。
- 订阅列表中同时包含有效、无效、重复插件地址时如何汇总结果。
- 更新请求中插件缺少更新来源时如何提示并保持现状。
- 新安装插件与现有插件标识冲突时如何避免重复条目与错误替换。
- 当前无可搜索插件时，搜索页如何给出下一步操作指引。
- 搜索请求超时或中断时，如何保证已有结果不被错误覆盖。

## Requirements *(mandatory)*

### Current Capability Baseline (Observed on 2026-03-30)

| Capability | Original Version | Current Compose Version | Gap Status |
|---|---|---|---|
| Add plugin from URL | Supported | Supported | Aligned |
| Add plugin from local file | Supported | Engine-level support exists, but user-visible add flow is incomplete | Partially Aligned |
| Add/update by subscription list | Supported with configurable subscription sources | Supports fixed/default subscription import only | Partially Aligned |
| Update single plugin | Supported | No explicit user-facing update path | Not Aligned |
| Update all plugins | Supported | No explicit batch update path | Not Aligned |
| Filter searchable plugins by plugin capability/state | Supported | Basic plugin selection exists, capability/state filtering is incomplete | Partially Aligned |
| Search songs with selected plugin (query + pagination) | Supported | Supported | Aligned |
| Release-level parity evidence for add -> update -> search chain | Supported by established behavior | Evidence exists but still incomplete as a formal parity gate | Partially Aligned |

### Functional Requirements

- **FR-001**: 系统 MUST 产出并维护“插件能力对齐矩阵”，覆盖从添加插件到搜索歌曲的完整链路。
- **FR-002**: 对齐矩阵 MUST 至少记录每个能力点的原版状态、Compose 状态、差异等级（已对齐/部分对齐/未对齐）、目标完成条件、验证证据。
- **FR-003**: 系统 MUST 支持与原版一致的插件添加入口能力（本地文件、插件链接、订阅列表）。
- **FR-004**: 插件添加成功后，用户 MUST 能在插件列表中看到可识别的插件标识与版本信息，并可直接用于搜索能力选择。
- **FR-005**: 系统 MUST 支持单插件更新能力：当插件存在更新来源时，用户可触发更新并获得成功或失败结果。
- **FR-006**: 系统 MUST 支持批量更新能力：用户可一次性更新全部可更新插件，并看到逐插件结果汇总。
- **FR-007**: 当插件不具备更新来源、更新失败或版本不满足更新条件时，系统 MUST 返回明确原因，且不得破坏当前可用状态。
- **FR-008**: 系统 MUST 保证插件唯一性：重复安装或更新不得生成重复插件条目。
- **FR-009**: 搜索插件选择列表 MUST 仅包含“已安装且具备歌曲搜索能力”的插件。
- **FR-010**: 用户 MUST 能选择指定插件进行歌曲搜索，并获得该插件返回的结果、空结果或错误状态。
- **FR-011**: 搜索流程 MUST 支持分页加载，并正确维护当前查询词、页码与是否结束状态。
- **FR-012**: 插件更新完成后，后续搜索 MUST 默认使用最新插件能力，不要求用户重新安装插件。
- **FR-013**: 系统 MUST 提供一套对齐测试方案，覆盖差异矩阵中所有 P1/P2 能力点，并将每项能力映射到对应测试用例。
- **FR-014**: 对齐测试方案 MUST 同时定义自动化测试与人工验收路径，并给出失败时的阻断规则。
- **FR-015**: 仅当对齐矩阵中的 P1 能力点全部通过验证时，系统才可标记为“插件能力完全对齐”。

### Key Entities *(include if feature involves data)*

- **CapabilityGapItem**: 单个能力差异记录，包含能力名称、原版状态、Compose 状态、差异等级、目标状态、验收证据。
- **PluginDescriptor**: 插件业务标识，包含插件身份、版本、更新来源、是否可搜索、当前状态。
- **PluginOperationResult**: 插件操作结果，包含操作类型（添加/更新）、目标插件、结果状态、失败原因、时间戳。
- **SearchExecutionRecord**: 搜索执行记录，包含插件、关键词、页码、结果条数、结束状态、错误信息。
- **ParityTestCase**: 对齐测试用例，包含覆盖能力点、前置条件、操作步骤、期望结果、执行结论。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% 的“添加-更新-搜索”能力点在对齐矩阵中有明确状态与验收证据链接。
- **SC-002**: 对齐矩阵中所有 P1 能力点在连续 2 次完整回归中通过率达到 100%。
- **SC-003**: 在标准验证数据集下，插件添加与更新操作的可恢复失败率不高于 5%，且失败均提供可读原因。
- **SC-004**: 在至少 3 个代表性插件上，歌曲搜索主路径（选择插件 -> 搜索 -> 查看结果）首轮成功率达到 95% 及以上。
- **SC-005**: 对齐版本发布前，人工验收中“添加插件、更新插件、通过插件搜索歌曲”三条关键路径均无阻断级问题。
- **SC-006**: 对齐版本发布后首个回归周期内，与插件添加/更新/搜索相关的高优先级缺陷数为 0。

## Assumptions

- 原版对齐基线以 `/Users/zili/code/android/MusicFree` 在 2026-03-30 的可运行行为为准。
- 本次对齐范围聚焦“添加插件、更新插件、插件搜索歌曲”，不包含下载、歌词、评论等非本轮核心能力。
- 用于验收的插件来源在测试窗口内可访问且具备稳定返回。
- 用户已具备执行插件管理与搜索操作所需的基础网络与应用访问条件。
- 现有插件生态的业务规则（如插件身份定义与更新来源字段语义）保持不变。
