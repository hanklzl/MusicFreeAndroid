# 单元测试耗时审计 设计

> 文档状态：当前规范
> 适用范围：JVM 单测（`testDebugUnitTest`）一次性耗时审计与必要性评估
> 直接执行：是（作为实现计划输入）
> 创建日期：2026-05-19

## 背景

仓库当前 `*/src/test/` 下共有约 **209 个 JVM 单测文件**（不含 androidTest 与 `.worktrees/`），分布在 `:app`、`:core`、`:data`、`:downloader`、`:plugin`、`:player`、`:logging`、`:feature:*` 等模块。`./gradlew testDebugUnitTest` 会在每个模块的 `build/test-results/testDebugUnitTest/TEST-*.xml` 输出标准 JUnit XML，每个 `<testcase>` 都带 `time` 属性，是天然的耗时数据源。

现有 `docs/dev-harness/test/rules.md` 覆盖了 ViewModel `runTest` 范式、`@Ignore` 政策、DataStore 隔离等约束，但**没有针对"测试耗时"这一维度**的规则或巡检；也没有数据说明当前慢测分布在哪里、哪些值得保留、哪些可以删。

本设计的目标是用最小成本完成一轮一次性审计，给出明确的「保留并优化 / 收敛 / 删除」三类建议，不引入新的 gate、不动测试代码。

## 目标

1. 列出当前 JVM 单测中 `testcase.time > 2.0s` 的全部慢测，按耗时降序排列；
2. 对每条慢测给出三件套：**成因初诊**（基于代码 + 关键词扫描）、**必要性分类**（守门型 / 回归型 / 高价重复型 / 低价值型，附 evidence）、**处置建议**（保留 + 优化 / 合并收敛 / 删除）；
3. 给一份模块级耗时聚合，作为补充信号识别"单 case 不慢但模块整体重"的模块；
4. 产物落地为 `docs/test-perf-audit/REPORT.md` + 两份原始 JSON，便于后续 review 与归档。

## 非目标

- **不接入 `scripts/dev-harness/check.sh` 与 `rules.md` 的硬性 gate**：本轮只交付报告，是否升级为巡检由后续单独 spec 决定。
- **不覆盖 `androidTest` / `-Pintegration`**：instrumentation 与网络通道测试运行成本与采集策略完全不同，超出本轮范围。
- **不引入 Gradle plugin 或第三方 timing 工具**：和"一次性报告"严重不匹配，违反 YAGNI。
- **本次不修改任何测试代码 / build 配置**：报告里"删除候选 / 优化候选"清单作为输入，后续按 plan 落地。
- 不做多轮平均 / 中位数采样：单次运行噪声接受、显式声明（见"风险与限制"）。

## 阈值与数据采集

### 慢测阈值

- 单个 `testcase` 的 `time > 2.0s`（含等于）入候选；
- 对模块级总耗时**不设阈值**，直接给完整聚合表，但报告里挑 Top 5 模块单独点名；
- 报告对耗时落在 `2.0–2.5s` 的 case 单独标注「噪声敏感」，处置建议默认偏保守（不轻易判低价值）。

### 跑测命令

```bash
./gradlew testDebugUnitTest --rerun-tasks --continue
```

- `--rerun-tasks`：绕开 Gradle build cache，强制实际执行，避免 XML 上是历史时间；
- `--continue`：单模块失败时让其他模块继续跑出 XML，最大化数据覆盖；
- 单次运行；不并行重跑取中位数。

### 解析脚本

新增 `scripts/test-perf/collect-slow-cases.py`：

- 输入：`*/build/test-results/testDebugUnitTest/TEST-*.xml`（glob 仓库根，排除 `.worktrees/` 与 `.gradle/`）；
- 输出 1：`docs/test-perf-audit/slow-cases.json`
  - 字段：`module`、`class`、`method`、`time_seconds`、`status`（`passed` / `failed` / `error` / `skipped`）、`failure_message`（如有）
  - 仅保留 `time_seconds > 2.0` 或 `status != passed`（失败的快测也列出，方便人复核）
- 输出 2：`docs/test-perf-audit/module-totals.json`
  - 字段：`module`、`testcase_count`、`module_total_seconds`、`slow_count`、`slow_total_seconds`
  - 全部模块，按 `module_total_seconds` 降序
- 不写 README / 不做可视化、保持脚本单文件、无第三方依赖。

`module` 字段从 XML 文件路径反推：取 `/build/test-results/` 之前的子路径，前置 `:` 并把 `/` 替换为 `:`。例如 `app/build/test-results/...` → `:app`，`feature/search/build/test-results/...` → `:feature:search`。

## 成因初诊维度

对每条候选慢测，AI 读取该测试源码 + 直接被测代码 + 测试类共享 fixture，根据下表给 **1–2 个最可能成因**，标注证据行号：

| 模式 | 关键词 / 痕迹 | 典型规避方案 |
|---|---|---|
| 真实阻塞睡眠 | `Thread.sleep`、`kotlinx.coroutines.delay`（非 `runTest`） | 改 `runTest` virtual time |
| `runBlocking` 自旋谓词 | `runBlocking { ... .first { predicate } }` | `runTest + advanceUntilIdle + Flow.first()`（已被 `rule-runtest-mandatory` 禁止） |
| Robolectric 启动开销 | `@RunWith(RobolectricTestRunner::class)`、`@Config(sdk=…)` | 拆出纯 JVM 单测；或合并同 class 多 case |
| 真实 Room / DataStore | `Room.databaseBuilder`、`PreferenceDataStoreFactory.create` | 用内存 Room / fake DataStore |
| QuickJS 引擎启动 | `QuickJsEngine`、`JsBridge` 实跑 | 用静态 fixture / contract test 共享 engine |
| MockWebServer 真握手 | `MockWebServer().start()`、`enqueue` 串行 | 复用 server / 缩短 socket timeout |
| 大型 fixture IO | 资源文件读、本地 JSON 大量解析 | inline 最小化 fixture |
| 重复 parameterized | `@RunWith(Parameterized::class)` 多组输入 | 收敛覆盖范围 |

成因列表只用于 AI 判读 checklist，**不需要做自动识别**。

## 必要性分类

每条慢测必须落到下表四档之一，且**强制提供 evidence**：

| 标签 | 判据 | evidence 形式 | 默认建议 |
|---|---|---|---|
| 守门型 | 测试文件位于 `**/harness/contracts/**` 路径下；或测试文件被 `docs/dev-harness/<area>/incidents.md` 或 `docs/dev-harness/incidents/INC-*.md` 中的 `target:` 字段引用；或测试覆盖的行为是 `docs/dev-harness/*/rules.md` 中某条 MUST / MUST NOT 规则的直接守门 | 引用 `harness/contracts/<file>` 路径、`INC-YYYY-NNNN`、或 `rules.md#anchor` | 保留 + 优化耗时 |
| 回归型 | 测试名 / 注释 / git blame 能定位到具体 bugfix（commit / PR / incident） | 引用 commit hash 或 PR / issue 号 | 保留 + 优化耗时 |
| 高价重复型 | 同断言已被另一个**更快**测试覆盖；或 parameterized 输入无实质差异 | 指向被覆盖的等价测试文件路径 | 合并 / 参数收敛 / 部分删除 |
| 低价值型 | 仅断言框架返回值 / 纯 mock 互证 / 测试的代码分支已不存在 | 简短说明"删了不亏"的论据 | 删除 |

### 判定顺序（逐级降级）

每条慢测必须按下列顺序逐级检查，**前一档命中即定档，不再追问其耗时是否合理**：

1. 是否位于 `**/harness/contracts/**`，或被 `docs/dev-harness/<area>/incidents.md` / `incidents/INC-*.md` 的 `target:` 引用，或守门 `rules.md` 某条 MUST → 命中 → 守门型；
2. 否则查测试名 / 注释 / `git log -p <file>` → 命中 bugfix 痕迹 → 回归型；
3. 否则在同模块 / 同 class 内查等价更快测试 → 命中 → 高价重复型；
4. 都未命中 → 低价值型。

### Evidence 强制规则

- 任何分类结论**必须**带 evidence；缺 evidence 视为分类未完成，不准列入 REPORT；
- "低价值型"的 evidence 不能仅是"我觉得没用"，必须列出：被测代码当前是否还存在 / 断言的属性是否还有意义 / 删除后是否有其他测试兜底。

## 交付物结构

spec 本身落 `docs/superpowers/specs/2026-05-19-unit-test-perf-audit-design.md`；审计产物单独走 `docs/test-perf-audit/`：

```
docs/test-perf-audit/
├── REPORT.md            # 审计主报告（人读）
├── slow-cases.json      # 原始解析结果（机器读 / 复核用）
└── module-totals.json   # 模块级聚合
```

新增脚本：

```
scripts/test-perf/
└── collect-slow-cases.py
```

### `REPORT.md` 章节

1. **概览**：JVM 单测总数、总耗时、`> 2s` 候选数、候选总耗时占比；
2. **模块耗时分布表**：`module / case 数 / 模块总耗时 / > 2s 数 / 模块占比`，按总耗时降序；Top 5 模块点名；
3. **慢测详表**（核心）：按 testcase 耗时降序，每行 `module · class.method · time · 成因初诊 · 必要性分类 · 处置建议 · evidence`；
4. **快速优化清单**：可机械改造的子集（如 sleep → virtual time、Robolectric → 纯 JVM、`runBlocking` 自旋 → `runTest + advanceUntilIdle`），按"预计可省耗时"降序；
5. **删除 / 合并候选清单**：必要性判定为「低价值型 / 高价重复型」的 case；低价值型给出"删除"建议，高价重复型给出"合并 / 参数收敛 / 部分删除"建议，每条带 evidence；
6. **数据局限声明**：单次运行噪声、Gradle worker / class loading 启动不算在 testcase time 内、`--rerun-tasks` 仍可能受 JVM JIT 影响、阈值附近的 case 易抖动。

## 风险与限制

- **单次运行噪声**：> 2s 阈值附近（`2.0–2.5s`）的 case 可能因 GC / JIT 抖动忽进忽出；报告里这类 case 标注「噪声敏感」，处置建议偏保守，不轻判低价值。
- **testcase time 不含 fork 启动**：Gradle worker 启动、class loading、Robolectric 首例 ResourceLoader 等成本不计入 `<testcase time="...">`；因此**模块级总耗时是补充信号，不被阈值过滤**，给完整聚合表。
- **必要性判定为 AI + 启发式**：会有误判；报告里"删除 / 合并候选清单"**不会被自动执行**，必须人复核后通过后续 plan 落地。
- **本次完全不动测试代码 / build 配置**：spec 落地不影响 CI / release / 已有 harness 检查；无回滚成本。

## 实施步骤总览（交给 writing-plans 落地）

1. 新增 `scripts/test-perf/collect-slow-cases.py`（解析 + 输出两份 JSON，单文件 Python，无第三方依赖）；
2. 跑 `./gradlew testDebugUnitTest --rerun-tasks --continue`，必要时挂掉的模块重跑；
3. 执行解析脚本生成 `docs/test-perf-audit/slow-cases.json` + `module-totals.json`；
4. AI 按"必要性判定流程"逐个分类（工作量主体）；
5. 写 `docs/test-perf-audit/REPORT.md`；
6. commit 三份产物（脚本 + REPORT + 两份 JSON）。
