# Tasks: 插件能力对齐（添加-更新-搜索）

**Input**: Design documents from `/Users/zili/code/android/MusicFreeAndroid/specs/001-plugin-parity-search/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 本特性规格明确要求“测试与发布准入”，因此包含测试任务（单元、集成、人工对比证据）。
**Organization**: 任务按用户故事分组，确保每个故事可独立实现与独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件且不依赖未完成任务）
- **[Story]**: 用户故事标签（US1/US2/US3/US4）
- 每条任务均包含明确文件路径

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立插件能力对齐的文档与执行脚手架

- [X] T001 Create parity matrix skeleton in specs/001-plugin-parity-search/parity-matrix.md
- [X] T002 Create evidence log template in specs/001-plugin-parity-search/evidence-log.md
- [X] T003 [P] Create sub-agent ownership map in specs/001-plugin-parity-search/subagent-workmap.md
- [X] T004 [P] Create comparison runner script in scripts/convergence/plugin-parity/run-compose-vs-rn.sh

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有用户故事共用的基础能力（完成前不进入 US 实现）

**⚠️ CRITICAL**: 本阶段未完成前，不开始 US1-US4 实现

- [X] T005 Add plugin lifecycle operation models in plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginOperationResult.kt
- [X] T006 [P] Extend plugin metadata for install source tracking in plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/LoadedPlugin.kt
- [X] T007 [P] Add update entrypoints (single/batch) in plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt
- [X] T008 Normalize plugin operation error mapping in plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt
- [X] T009 Add unit tests for install/update recovery baseline in plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerUpdateFlowTest.kt
- [X] T010 Add instrumentation baseline for add-update-search chain in plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt

**Checkpoint**: 插件生命周期内核基础就绪，可进入各用户故事实现

---

## Phase 3: User Story 1 - 建立插件能力差异基线 (Priority: P1) 🎯 MVP

**Goal**: 形成 Compose vs 原版的可执行对比基线与证据规则
**Independent Test**: 仅检查 `parity-matrix.md` 与 `evidence-log.md`，确认每个能力点均有状态、目标与证据槽位

### Tests for User Story 1

- [X] T011 [P] [US1] Add matrix completeness check script in scripts/convergence/plugin-parity/validate-matrix.sh
- [X] T012 [US1] Add matrix validation usage examples in specs/001-plugin-parity-search/quickstart.md

### Implementation for User Story 1

- [X] T013 [US1] Populate capability baseline rows in specs/001-plugin-parity-search/parity-matrix.md
- [X] T014 [P] [US1] Add evidence mapping by capability in specs/001-plugin-parity-search/evidence-log.md
- [X] T015 [P] [US1] Document compose-vs-rn execution checkpoints in specs/001-plugin-parity-search/quickstart.md
- [X] T016 [US1] Document matrix status semantics in specs/001-plugin-parity-search/research.md

**Checkpoint**: US1 可独立验收（无需代码改动即可验证能力基线完整性）

---

## Phase 4: User Story 2 - 添加与更新插件能力对齐 (Priority: P1)

**Goal**: 对齐插件添加与更新能力（本地/URL/订阅 + 单个/批量更新）
**Independent Test**: 在设置页独立执行“添加/更新插件”链路并验证成功、失败、恢复行为

### Tests for User Story 2

- [X] T017 [P] [US2] Extend add/update viewmodel tests in feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt
- [X] T018 [P] [US2] Add plugin manager update tests in plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerUpdateFlowTest.kt
- [X] T019 [P] [US2] Add single+batch update integration test in plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt

### Implementation for User Story 2

- [X] T020 [US2] Implement single-plugin update flow in plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt
- [X] T021 [US2] Implement batch update flow with per-plugin summary in plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt
- [X] T022 [US2] Add local-file plugin install action in feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt
- [X] T023 [US2] Add install/update operation state handling in feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt
- [X] T024 [US2] Add local install + single update + batch update UI actions in feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt
- [X] T025 [US2] Add update failure messaging and retry UX in feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt
- [X] T026 [US2] Wire default subscription URL fallback to https://13413.kstore.vip/yuanli/yuanli.json in feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt
- [X] T027 [US2] Wire settings navigation callbacks for file-based install in feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/navigation/SettingsNavigation.kt
- [X] T028 [US2] Update lifecycle contract with implemented behaviors in specs/001-plugin-parity-search/contracts/plugin-lifecycle-contract.md

**Checkpoint**: US2 可独立验收（不依赖搜索页，单测与集成测试覆盖添加/更新路径）

---

## Phase 5: User Story 3 - 通过插件搜索歌曲能力对齐 (Priority: P2)

**Goal**: 对齐搜索插件集合规则与分页行为，并保证更新后搜索行为稳定
**Independent Test**: 在搜索页使用已安装插件完成“搜索 + 加载更多 + 更新后再搜索”独立验证

### Tests for User Story 3

- [X] T029 [P] [US3] Create search viewmodel test suite in feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModelTest.kt
- [X] T030 [P] [US3] Add post-update search regression integration test in plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt

### Implementation for User Story 3

- [X] T031 [US3] Filter searchable plugin list by capability/state in feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt
- [X] T032 [US3] Refine pagination and load-more error transitions in feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt
- [X] T033 [US3] Add explicit empty/error/no-plugin UI states in feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt
- [X] T034 [US3] Keep selected plugin consistency after plugin updates in feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt
- [X] T035 [US3] Add search state contract fields if needed in feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchUiState.kt
- [X] T036 [US3] Update search contract mapping with delivered behavior in specs/001-plugin-parity-search/contracts/plugin-search-contract.md

**Checkpoint**: US3 可独立验收（仅依赖插件已可安装，搜索路径可单独验证）

---

## Phase 6: User Story 4 - 对齐测试与发布准入 (Priority: P3)

**Goal**: 固化自动化 + 人工对比的发布门禁，形成“完全对齐”可复验结论
**Independent Test**: 执行门禁检查脚本与对比清单，得到 P1/P2 能力通过结论

### Tests for User Story 4

- [X] T037 [P] [US4] Add parity test case registry in specs/001-plugin-parity-search/parity-test-cases.md
- [X] T038 [P] [US4] Add release-gate checker script test cases in scripts/convergence/plugin-parity/check-release-gate.sh

### Implementation for User Story 4

- [X] T039 [US4] Implement release-gate checker script in scripts/convergence/plugin-parity/check-release-gate.sh
- [X] T040 [US4] Add compose automated verification command evidence section in specs/001-plugin-parity-search/quickstart.md
- [X] T041 [US4] Add RN comparison verification report template in docs/convergence/iteration-14/plugin-parity-verification.md
- [X] T042 [US4] Record first full dry-run evidence in docs/convergence/iteration-14/plugin-parity-dry-run.md
- [X] T043 [US4] Update final P1 readiness statuses in specs/001-plugin-parity-search/parity-matrix.md

**Checkpoint**: US4 可独立验收（门禁规则与证据输出可重复执行）

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 跨故事收口、文档同步、总体验证

- [X] T044 [P] Consolidate parity implementation notes in docs/convergence/iteration-14/analysis.md
- [X] T045 [P] Consolidate final verification summary in docs/convergence/iteration-14/verification.md
- [X] T046 Run full quickstart validation and align command outputs in specs/001-plugin-parity-search/quickstart.md
- [X] T047 Verify sub-agent delivery checklist completion in specs/001-plugin-parity-search/subagent-workmap.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 可立即开始
- **Phase 2 (Foundational)**: 依赖 Phase 1，且阻塞所有用户故事
- **Phase 3 (US1)**: 依赖 Phase 2，可作为 MVP 最小交付
- **Phase 4 (US2)**: 依赖 Phase 2；建议在 US1 基线稳定后执行
- **Phase 5 (US3)**: 依赖 Phase 2 与 US2（需要可用插件添加/更新能力）
- **Phase 6 (US4)**: 依赖 US1-US3 的能力与证据产出
- **Phase 7 (Polish)**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1 (P1)**: 仅依赖基础设施，可独立完成
- **US2 (P1)**: 依赖基础设施，不依赖 US3/US4
- **US3 (P2)**: 依赖 US2 提供稳定插件生命周期能力
- **US4 (P3)**: 依赖 US1/US2/US3 的产物与测试结果

### Within Each User Story

- 测试任务优先（先写并验证失败，再实现）
- 先核心模型/状态，再业务逻辑，再 UI 与文档契约
- 每个故事完成后必须执行其独立验收标准

## Parallel Opportunities

- Phase 1 中 T003/T004 可并行
- Phase 2 中 T006/T007/T008 可并行，T009/T010 在核心 API 初版后并行补测
- US2 中测试任务 T017/T018/T019 可并行；UI 与内核任务由不同 sub-agent 并行推进
- US3 中 T029/T030 可并行；T033 与 T034 可并行
- US4 中 T037/T038 可并行；报告文档与门禁脚本可并行完善

## Parallel Example: User Story 2

```bash
# Sub-agent A (plugin core)
Task: "T020 Implement single-plugin update flow in plugin/.../PluginManager.kt"
Task: "T021 Implement batch update flow with per-plugin summary in plugin/.../PluginManager.kt"

# Sub-agent B (settings UI)
Task: "T024 Add local install + single update + batch update UI actions in feature/settings/.../SettingsScreen.kt"
Task: "T025 Add update failure messaging and retry UX in feature/settings/.../SettingsScreen.kt"

# Sub-agent D (tests)
Task: "T017 Extend add/update viewmodel tests in feature/settings/.../SettingsViewModelTest.kt"
Task: "T019 Add single+batch update integration test in plugin/.../PluginRuntimeIntegrationTest.kt"
```

## Parallel Example: User Story 3

```bash
# Sub-agent C (search)
Task: "T031 Filter searchable plugin list by capability/state in feature/search/.../SearchViewModel.kt"
Task: "T033 Add explicit empty/error/no-plugin UI states in feature/search/.../SearchScreen.kt"

# Sub-agent D (tests)
Task: "T029 Create search viewmodel test suite in feature/search/.../SearchViewModelTest.kt"
Task: "T030 Add post-update search regression integration test in plugin/.../PluginRuntimeIntegrationTest.kt"
```

## Implementation Strategy

### MVP First (US1)

1. 完成 Phase 1 + Phase 2
2. 完成 Phase 3 (US1)
3. 执行基线完整性验证（矩阵 + 证据槽位）
4. 若通过，冻结 US1 基线作为后续对齐参照

### Incremental Delivery

1. US1（先建立基线）
2. US2（补齐添加/更新能力）
3. US3（对齐搜索行为）
4. US4（门禁与发布准入）
5. 每个阶段结束都执行独立验收并更新证据文档

### Sub-Agent Team Strategy

1. 主 Agent 先完成 Phase 1/2 依赖拆解
2. 按 `subagent-workmap.md` 将 US2/US3 任务分配给 A/B/C/D
3. 主 Agent 负责串行集成、冲突处理与最终门禁判定
4. 任一 P1 失败时停止推进后续发布任务

## Notes

- `[P]` 仅用于可并行任务（不同文件、无直接依赖）
- `[USx]` 仅出现在用户故事阶段任务
- 所有任务均提供明确文件路径，适合直接交给 LLM/sub-agent 执行
- 任务顺序已按依赖排好，执行时优先遵循 Phase 关卡
