# Implementation Plan: 插件能力对齐（添加-更新-搜索）

**Branch**: `[001-plugin-parity-search]` | **Date**: 2026-03-30 | **Spec**: [/Users/zili/code/android/MusicFreeAndroid/specs/001-plugin-parity-search/spec.md](/Users/zili/code/android/MusicFreeAndroid/specs/001-plugin-parity-search/spec.md)  
**Input**: Feature specification from `/Users/zili/code/android/MusicFreeAndroid/specs/001-plugin-parity-search/spec.md`

## Summary

本计划聚焦 Compose 版 FreeMusic 与原版在“添加插件 -> 更新插件 -> 通过插件搜索歌曲”链路上的能力完全对齐。执行路径分为三段：先固化差异基线与验收口径，再补齐插件添加/更新缺口，最后以统一测试门槛完成搜索链路回归收口。  
本次将使用指定订阅地址 `https://13413.kstore.vip/yuanli/yuanli.json` 作为默认验证输入之一，并要求所有 P1 能力点具备自动化与人工双重验证证据。

## Compose vs Original Comparison Execution

为确保“对齐”可落地复验，本计划采用双端并行验证：

1. Compose 版（当前仓库）执行一次完整链路：
   - 编译安装：`./gradlew installDebug`
   - 启动：`adb shell am start -S -n com.zili.android.musicfreeandroid/.MainActivity`
2. 原版 RN（`/Users/zili/code/android/MusicFree`）执行同链路：
   - 依赖安装（首次）：`npm install`
   - Metro：`npm start`
   - 编译安装：`npm run android`
   - 启动（可选直启）：`adb shell am start -S -n fun.upup.musicfree/.MainActivity`
3. 两端统一按同一用例顺序执行：
   - 添加插件（URL / 本地 / 订阅）
   - 更新插件（单个 / 全部）
   - 选择插件搜索歌曲（含分页）
4. 每一步必须产出并记录对比结论：
   - 行为一致 / 行为偏差 / Compose 缺失
   - 证据类型：截图、日志、测试结果

## Technical Context

**Language/Version**: Kotlin 2.2.10（Android 主实现）  
**Primary Dependencies**: Jetpack Compose + Material3, Hilt, Coroutines/Flow, QuickJS runtime, OkHttp, Room/DataStore  
**Storage**: 应用私有文件目录（插件脚本文件）、DataStore（偏好与配置）、现有 Room 数据  
**Testing**: JUnit/Mockito 单测、Android Instrumentation 测试、Compose UI 行为验证  
**Target Platform**: Android 10+（minSdk 29），重点验证真机/模拟器一致行为  
**Project Type**: 多模块 Android 移动应用（`:feature:*` + `:plugin` + `:data` + `:core`）  
**Performance Goals**: 插件添加/更新操作可在用户可接受时间内完成；插件搜索可稳定返回结果并支持分页  
**Constraints**: 不破坏现有插件可用性；更新失败需可恢复；与原版能力定义保持一致；订阅验证默认使用 `https://13413.kstore.vip/yuanli/yuanli.json`  
**Scale/Scope**: 覆盖插件管理与搜索主链路（设置页、插件管理能力、搜索页、对应测试与文档工件）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Gate 1 - Spec Completeness: PASS。规格已覆盖差异基线、功能需求、成功标准、边界条件。  
- Gate 2 - Clarification Status: PASS。无 `NEEDS CLARIFICATION` 未决项。  
- Gate 3 - Testability: PASS。需求可映射到自动化和人工验收。  
- Gate 4 - Constitution Enforceability: CONDITIONAL PASS。`.specify/memory/constitution.md` 当前为占位模板，未提供可执行硬性条款；本计划按仓库现有工程约束（`AGENTS.md`/`CLAUDE.md`）执行并记录复杂度为 0。  

**Post-Phase-1 Re-check**: PASS。设计产物（research/data-model/contracts/quickstart）与上述 gate 一致，无新增违例。

## Project Structure

### Documentation (this feature)

```text
specs/001-plugin-parity-search/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── plugin-lifecycle-contract.md
│   └── plugin-search-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
app/
core/
data/
plugin/
feature/
├── settings/
├── search/
├── home/
└── player-ui/
docs/
└── superpowers/
```

**Structure Decision**: 采用现有多模块 Android 架构，不新增新模块；本次改动集中在 `feature/settings`、`feature/search`、`plugin` 以及对应测试目录，并由 `specs/001-plugin-parity-search/` 承载全量设计工件。

## Codex Sub-Agent Execution Strategy

本特性执行将采用 Codex sub-agent 方式，按“并行开发 + 主线集成”组织：

1. 主 Agent（你当前会话）职责：
   - 维护任务分解与依赖顺序
   - 分发子任务并控制合并顺序
   - 统一验收与最终回归

2. Sub-agent 拆分与所有权（必须保持写入范围互斥）：
   - Sub-agent A（插件生命周期内核）：
     - 负责 `plugin/` 下添加、单插件更新、批量更新、订阅更新能力
   - Sub-agent B（设置页插件管理 UI）：
     - 负责 `feature/settings/` 下入口、状态、交互与错误反馈
   - Sub-agent C（搜索对齐）：
     - 负责 `feature/search/` 下可搜索插件集合规则、搜索与分页行为
   - Sub-agent D（测试与证据）：
     - 负责相关 `test/` 与 `docs/` 下对齐用例、验证记录模板

3. 执行顺序与依赖：
   - 阶段 1（可并行）：A/B/C 各自实现核心能力
   - 阶段 2（串行收敛）：主 Agent 集成并处理跨模块冲突
   - 阶段 3（可并行）：D 补齐自动化与人工验收证据
   - 阶段 4（串行门禁）：主 Agent 执行完整回归并判定 P1 通过

4. 子任务交付约定：
   - 每个 sub-agent 必须提交：
     - 变更文件清单
     - 已执行测试命令与结果
     - 未解决风险与阻塞项
   - 不允许 sub-agent 回退或覆盖其他 sub-agent 的改动

5. 完成标准（针对 sub-agent 执行模式）：
   - `添加插件 -> 更新插件 -> 搜索歌曲` 三条链路在 Compose 端完整通过
   - 与原版对比结果有证据并标记为 `一致/偏差/缺失`
   - P1 能力点在最终汇总回归中 100% 通过

## Complexity Tracking

无需要豁免的复杂度违例。
