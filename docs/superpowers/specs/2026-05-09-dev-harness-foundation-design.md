---
状态：当前规范（Dev Harness 基础设施专项）
适用范围：跨 AI 工具 / 跨开发者的开发守门入口、错误库、核心域 AI skills 与测试约束
直接执行：是（作为 writing-plans 的输入）
入口：[AGENTS.md](../../../AGENTS.md)
最后校验：2026-05-11
---

# Dev Harness 基础设施设计

## 0. 背景与目标

### 0.1 现状

仓库已有局部"harness"概念，但分散：

- `docs/ui-harness/screen-chrome-rules.md` 是当前规范（screen 切换动画 / 普通 AppBar / 沉浸式状态栏），有自己的 doc gate。
- `core/src/main/java/.../core/ui/MusicFreeScreenChrome.kt` 提供 `MusicFreeScreenScaffold` / `MusicFreeTopAppBar` / `MusicFreeStatusBarChrome`，44 处页面已采用，仓库内已无 `TopAppBarDefaults.topAppBarColors(...)` 散落手写。
- `app/.../navigation/MusicFreeNavTransitions.kt` + `MusicFreeNavTransitionsTest.kt` 抽离了导航动画并有 JVM 单测。
- `docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md` 治理了 `runTest` 死锁、`PlayerControllerTest` setUp 主线程 `runBlocking` 死锁、`mergeExtDexDebugAndroidTest` D8 OOM、AndroidJUnit4 多实例复用 DataStore 文件、feature 模块缺 androidTest runner 基线等问题，并引入 `Assume.assumeTrue` + `-Pintegration` 网络通道门控。
- `.agents/skills/`、`.claude/skills/`、`.codex/skills/` 三个目录已存在 `jetpack-compose-expert-skill` 等公共 skills，证明三家 AI 工具的 skill 入口都已落地。
- 用户 Claude Code auto-memory（`~/.claude/projects/.../memory/MEMORY.md`）当前混存了项目级 rule（`db schema during dev`、`UI: MD3 components, RN layout`）与个人会话偏好。

但缺乏：

- 单一开发守门入口：每域规则、错误库与 AI skills 的关系没有被显式表达。
- 错误库（已发生踩坑）未结构化沉淀，跨 AI 工具与跨开发者难以一致引用。
- 守门强度不够：现有规则除部分 contract test（如 `MusicFreeNavTransitionsTest`）外，多依赖人工 review；CI 没有专门的 dev-harness gate。
- skill 体系未与文档/规则做强绑定，AI 调起 skill 不一定同步加载现行 rules。
- 无定期巡检机制：drift（rules 引用消失、guard 文件被删、错误复发）只能靠下次踩坑暴露。

### 0.2 目标

1. 引入 **Dev Harness 总入口**（`docs/dev-harness/`），收敛 ui / plugin / player / test 四域的 rules + incidents + AI skills 关联，单一文档源、单一 AGENTS 入口。
2. 建立**结构化错误库**：每条 incident 有稳定 ID、根因、复发签名、关联 rule、guard 类型 / 目标，可被 AI、CI、人三方机器化访问。
3. 提供 **5 个 AI skills**（ui-harness / plugin-system / media-player / test-stability / harness-curator），单一来源 + `.claude/.codex` 软链。
4. 强制守门：**文档 + 针对性 contract test + grep 脚本 + CI 必跳作业**，gate 5 分钟内跑完，进 PR / push to main 默认触发。
5. 提供**定期巡检 skill**（harness-curator）：扫 drift / 错误复发 / 用户级 memory 中可 promote 项，输出 REPORT，不直接落盘。
6. 钉死 **项目记忆四层边界**：错误库 / 强约束 / AI skills / 个人 auto-memory，避免项目级踩坑沉淀到本机用户目录而其他 AI 工具与协作者看不到。

### 0.3 非目标

- 不在本期引入 detekt / lint 自定义规则，不引入运行时 invariant assertion，不引入 Compose UI 结构 snapshot test。
- 不引入 `data` / `nav` / `download` 等更多域的 rules.md（v1 仅 ui / plugin / player / test 四域）。
- 不接入 `.cursor/rules/`、`.windsurfrules` 等其他 AI 工具入口（v1 仅 Claude / Codex / 通用 agents 三家）。
- 不修改 release 签名 / APK 产物 workflow；不在 dev-harness gate 中跑 `connectedAndroidTest` 或 lint。
- 不为 `harness-curator-skill` 引入自动周期触发（定时 issue 提醒等），v1 完全靠手工/对话触发。
- 不强制把 `docs/home-fidelity/` 折叠到 dev-harness ui 子域。该目录是首页 UI fidelity 取证规范（窄域、独立工程纪律），保留原位、由 ui rules.md 反向交叉引用。
- 不主动改写历史 `docs/superpowers/plans/*.md` 或 `specs/*.md`（仅在 DOCS_STATUS 已标历史记录的不再当前规范源）。

## 1. 项目记忆四层边界

| 层 | 位置 | 进 git? | 跨 AI 工具? | 跨开发者? | 用途 |
|---|---|---|---|---|---|
| 错误库（incidents） | `docs/dev-harness/incidents/index.md` + `docs/dev-harness/<area>/incidents.md` | 是 | 是 | 是 | 已发生事故、根因、复发签名、guard |
| 强约束（rules） | `docs/dev-harness/<area>/rules.md` | 是 | 是 | 是 | MUST / MUST NOT 条款，反向引用 incident |
| AI skills（流程指引） | `.agents/skills/<area>-skill/` + `.claude/.codex/skills/<area>-skill` 软链 | 是 | 是 | 是（人也能读） | 工作流、清单、模板、references |
| 个人 auto-memory（Claude Code only） | `~/.claude/projects/.../memory/MEMORY.md`（用户本机） | 否 | 否 | 否 | 与 Claude 的私人协作偏好、临时上下文 |

`docs/superpowers/specs/` 与 `docs/superpowers/plans/` 是历史决策快照，不算记忆载体，不在本设计变动范围。

**用户 auto-memory 的项目级条目处置**：

- `db-schema-during-dev`（dev 阶段直接改 entity，不写 Migration）：未来 `data` 域引入时正式落入 `docs/dev-harness/data/rules.md`。本期先在 `docs/dev-harness/ui/rules.md` 末尾"待开域 backlog"段记一行，避免遗失。
- `UI: MD3 components, RN layout`（组件用 MD3，但布局信息架构对齐 RN）：合并进 `docs/dev-harness/ui/rules.md` 的"设计原则"一节。

迁出后，本机 auto-memory 仅留个人会话偏好。

**AGENTS.md 的发现机制**：

```markdown
## 项目记忆与守门约束

- 强约束：`docs/dev-harness/<area>/rules.md`
- 历史踩坑：`docs/dev-harness/incidents/index.md`（按 ID 反查到 area + rule + guard）
- AI 工作流：见 `.agents/skills/<area>-skill/`，软链到 `.claude/skills/`、`.codex/skills/`
- 历史决策快照：`docs/superpowers/specs/` 与 `plans/`（仅参考，不是当前规则源）
- 个人会话偏好（Claude Code only）：`~/.claude/projects/.../memory/MEMORY.md`，不进仓库
```

## 2. 总入口与目录形态

### 2.1 目录布局

```text
docs/
  DOCS_STATUS.md                # 现有，登记新文档
  home-fidelity/                # 保留，窄域（首页 UI fidelity 取证规范）
  ui-harness/
    screen-chrome-rules.md      # 内容迁到 docs/dev-harness/ui/rules.md，原位留 redirect stub
  dev-harness/                  # 新增，开发守门总入口
    INDEX.md                    # 总览：四域链接 + incidents 索引 + skills 索引
    ui/
      rules.md                  # 来源于 ui-harness/screen-chrome-rules.md，扩充新规则
      incidents.md              # 该域历史踩坑（结构化 H2 条目）
    plugin/
      rules.md
      incidents.md
    player/
      rules.md
      incidents.md
    test/
      rules.md
      incidents.md
    incidents/
      template.md               # 单条 incident 模板
      index.md                  # 全仓 incident 汇总（含 ID、area、rule_id 反查）
  superpowers/                  # 现有，specs/plans 不动

.agents/skills/                 # 单一来源
  ui-harness-skill/
  plugin-system-skill/
  media-player-skill/
  test-stability-skill/
  harness-curator-skill/        # 巡检/更新 incidents + rules
  jetpack-compose-expert-skill/ # 现有，保留
  speckit-*/                    # 现有，保留

.claude/skills/<area>-skill     # 软链到 .agents/skills/<area>-skill
.codex/skills/<area>-skill      # 软链到 .agents/skills/<area>-skill

scripts/dev-harness/
  grep-check.py                 # 跑各 incidents.md 中 guard.type=grep 的 signature
  symlinks-check.sh             # 验证 .claude/.codex/.agents 三处 skills 与 references 软链
  check.sh                      # 本地一键跑：symlinks + grep + JVM contract tests

.github/workflows/
  dev-harness-gate.yml          # 新增 CI 必跳作业
  android-debug-apk.yml         # 现有，不动
  android-release-apk.yml       # 现有，不动
```

### 2.2 AGENTS.md 入口

`AGENTS.md` 顶部"文档入口（先读）"之后加一节：

```markdown
## Dev Harness 强制入口

任何涉及下述域的改动，动手前必须读取对应 rules.md：

- UI / Compose Screen：`docs/dev-harness/ui/rules.md`
- 插件系统：`docs/dev-harness/plugin/rules.md`
- 播放器 / Media3：`docs/dev-harness/player/rules.md`
- 测试代码 / 测试基建：`docs/dev-harness/test/rules.md`

每条 rule 都关联一条或多条 incident（`docs/dev-harness/incidents/index.md`）和 / 或一条 contract test。
违反 rules.md 中标记 MUST / MUST NOT 的条款将在 CI `dev-harness-gate` 作业被拦下。

历史决策快照在 `docs/superpowers/specs/` 与 `plans/`（仅参考），不是当前规则源。
```

`AGENTS.md` 现有"## 文档入口（先读）"列表追加 `docs/dev-harness/INDEX.md` 一行。`docs/DOCS_STATUS.md` 把 `docs/dev-harness/INDEX.md` 与四域 `rules.md` 都登记为"当前规范"。

### 2.3 `docs/ui-harness/screen-chrome-rules.md` 迁移

- 内容整体复制到 `docs/dev-harness/ui/rules.md`，并在该文件中新增针对 ui 域的新规则（标题结构、动画时长、状态栏、`MainActivity` 责任边界等都按现有约束写明，新增"设计原则"段融合 user memory 中 RN layout 条目）。
- `docs/ui-harness/screen-chrome-rules.md` 原位保留 1 行 redirect stub：`> 已迁移至 [docs/dev-harness/ui/rules.md](../dev-harness/ui/rules.md)，旧链接保留以兼容历史引用。`
- `docs/DOCS_STATUS.md` 中该条目标"已迁移，引用 dev-harness/ui/rules.md"。
- AGENTS.md 中现有指向 `docs/ui-harness/screen-chrome-rules.md` 的引用全部改为 `docs/dev-harness/ui/rules.md`。

### 2.4 `docs/home-fidelity/` 处置

不并入 dev-harness。在 `docs/dev-harness/ui/rules.md` 的"设计原则"段加一行：

> 首页 UI fidelity 取证规范见 `docs/home-fidelity/`，非本 ui rules 范围；新增页面级 fidelity 规范前先讨论是否折成 `docs/dev-harness/ui/fidelity/` 子域。

`docs/home-fidelity/homepage/README.md` 在 DOCS_STATUS 仍标"当前规范（首页专项）"。

## 3. 错误库（incidents）格式与索引

### 3.1 文件分布

- 每域 `docs/dev-harness/<area>/incidents.md`，按 H2 排列条目，最新在上。
- 顶层汇总 `docs/dev-harness/incidents/index.md`（手维护表，全仓 ID 唯一性保证 + 跨域反查）。
- 模板 `docs/dev-harness/incidents/template.md`，新写一条直接复制。
- 永久不删；过期或被取代标 `status: superseded by INC-XXXX` 或 `status: stale`。

### 3.2 单条 incident 必填字段

```markdown
## INC-2026-0001 — runBlocking + Flow.first { predicate } 死锁

- id: INC-2026-0001
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-runtest-mandatory
- guard:
    type: contract-test
    target: app/src/test/java/.../harness/contracts/TestRunTestIdiomContractTest.kt
- signature: |
    grep -rEn 'runBlocking[^{]*\{[^}]*\.first\s*\{' \
      --include='*ViewModelTest.kt' \
      --exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle .
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#3-pr-1--group-c-runtest-迁移

### 根因
ViewModel 单测组合 `runBlocking + UnconfinedTestDispatcher + viewModelScope.launch + Flow.first { predicate }`：
predicate 在 hot dispatcher 上自旋 + viewModelScope 协程未被 advance，整个用例 hang。
仅在 Robolectric/ByteBuddy 预热的 JVM 复现，因此初次运行可能"看起来通过"。

### 复发条件
ViewModel 单测里同时出现 `runBlocking` 与 `Flow.first { ... }` 自旋谓词。

### 教训
单测全部走 `runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`，
`Flow.first { predicate }` 在测试代码里替换为显式 `advanceUntilIdle()` + `Flow.first()`。
```

字段语义：

- **id**：`INC-YYYY-NNNN`，年 + 4 位序号；递增不回收，跨 area 全局唯一。
- **area**：`ui` / `plugin` / `player` / `test`，与目录对应；未来扩 `data` / `nav` 时再加。
- **status**：`active` / `superseded` / `stale`；只有 `active` 进守门。
- **rule_ref**：相对路径 + anchor，指向 rules.md 中 MUST 条款。
- **guard.type**：`contract-test` / `grep` / `manual`；可复合（如 `grep + manual`）。
- **guard.target**：contract-test 类型为测试文件相对路径；grep 类型省略（signature 即守门命令）。
- **signature**：复发的 grep 命令或 test 入口，可直接跑、可被 CI 解析。
- **fix_ref**：当时如何修的——指向 spec / plan / commit hash。

`rules.md` 中的每条 MUST 反向写 `implemented_by: INC-XXXX, INC-YYYY`。

### 3.3 顶层索引（`docs/dev-harness/incidents/index.md`）

| ID | area | 标题 | rule | guard |
|---|---|---|---|---|
| INC-2026-0001 | test | runBlocking + Flow.first 死锁 | test/rules.md#rule-runtest-mandatory | contract-test |
| INC-2026-0002 | test | PlayerController.connect 主线程 runBlocking 死锁 | test/rules.md#rule-no-runblocking-on-mainthread | contract-test |
| INC-2026-0003 | test | mergeExtDexDebugAndroidTest D8 OOM | test/rules.md#rule-gradle-jvmargs-baseline | grep |
| INC-2026-0004 | test | DataStore multiple active 同文件 | test/rules.md#rule-datastore-per-instance-isolation | contract-test |
| INC-2026-0005 | test | feature module 缺 androidTest runner | test/rules.md#rule-feature-androidtest-baseline | contract-test |
| INC-2026-0006 | ui | 顶部导航动画误按 RN JS 100ms 建守门 | ui/rules.md#rule-nav-animation-rn-android | contract-test |
| INC-2026-0007 | ui | 散落的 TopAppBarDefaults.topAppBarColors 手写 | ui/rules.md#rule-no-raw-material3-topappbar | grep |
| INC-2026-0008 | ui | MainActivity 隐式补顶部 inset 白名单 | ui/rules.md#rule-mainactivity-no-implicit-top-inset | grep + manual |
| INC-2026-0009 | plugin | QuickJS 跨线程访问 runtime 崩溃 | plugin/rules.md#rule-quickjs-single-thread | manual |
| INC-2026-0010 | plugin | 集成测试默认依赖 kstore.vip 真网络 | plugin/rules.md#rule-network-test-gated | contract-test |
| INC-2026-0011 | player | 全屏播放器内容贴到状态栏后方 | player/rules.md#rule-immersive-content-respects-statusbar | manual |
| INC-2026-0012 | player | 歌词自动跟随重复触发 / seek overlay 错位 | player/rules.md#rule-lyric-follow-debounce | contract-test |

### 3.4 工作流：新增 incident

1. 复制 `docs/dev-harness/incidents/template.md` 到对应 area `incidents.md` 顶部，分配下一个未用 `INC-YYYY-NNNN`。
2. 在 `docs/dev-harness/<area>/rules.md` 加或引用对应 MUST 条款，反向写 `implemented_by: INC-XXXX`。
3. 实现 guard：
   - contract-test：在 `<module>/src/test/.../harness/contracts/` 加 `*ContractTest.kt` 文件，KDoc 头一行 `Guards INC-XXXX`。
   - grep：把 `signature` 写齐能直接跑，包含 `--exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle`。
   - manual：必须解释"为何不能机械化"与"何时升级 contract-test"的触发条件。
4. 更新 `docs/dev-harness/incidents/index.md`。
5. 本地跑 `bash scripts/dev-harness/check.sh` 全绿后再合 PR。

## 4. AI skills 体系（5 个）

### 4.1 命名与位置

```text
.agents/skills/                # 单一来源
  ui-harness-skill/
  plugin-system-skill/
  media-player-skill/
  test-stability-skill/
  harness-curator-skill/

.claude/skills/<name>          # 软链到 .agents/skills/<name>
.codex/skills/<name>           # 软链到 .agents/skills/<name>
```

每个 skill 目录：

```text
<skill-name>/
  SKILL.md                     # frontmatter + 必读 gate + workflow checklist
  references/
    rules.md                   # 软链 → docs/dev-harness/<area>/rules.md
    incidents.md               # 软链 → docs/dev-harness/<area>/incidents.md
    workflows.md               # 该域 AI 工作流详写（清单/模板/反例）
    [其他子主题，按需]
```

软链规则：所有 skill 实体只在 `.agents/skills/` 下落盘；`.claude/.codex` 下都是符号链接；`references/rules.md` 与 `references/incidents.md` 也用相对软链指向 `docs/dev-harness/<area>/`，避免双源漂移。`scripts/dev-harness/symlinks-check.sh` 在 CI 验证全部链接有效。

### 4.2 五个 skill 的 frontmatter 与触发轮廓

| skill | 触发词（写入 description） | 工作流主线 | references 子主题 |
|---|---|---|---|
| **ui-harness-skill** | "新建 Compose Screen / 改顶部栏 / 状态栏 / 切换动画 / Scaffold / TopAppBar / rpx / FidelityAnchors / 沉浸式" | 读 ui rules.md + incidents.md → 用 `MusicFreeScreenScaffold` 默认结构 → 登记特殊 chrome → 跑 `:app:testDebugUnitTest` `*MusicFreeNavTransitionsTest` 与 `*ContractTest` | `screen-scaffold-walkthrough.md`、`navigation-animation.md`、`status-bar-immersive.md`、`fidelity-anchors.md`（与 home-fidelity 交叉引用） |
| **plugin-system-skill** | "插件 / QuickJS / JsBridge / PluginApi / require shim / installFromUrl / updatePlugin / -Pintegration / MockWebServer / PluginManager" | 读 plugin rules.md + incidents.md → 选择网络 / 本地路径 → 守 single-thread runtime + DataStore 隔离 → 跑 `:plugin:connectedAndroidTest` 默认通道 / `-Pintegration` 真网通道 | `plugin-api-surface.md`（14 能力）、`quickjs-threading.md`、`mock-webserver-recipe.md`、`network-test-gate.md` |
| **media-player-skill** | "Media3 / ExoPlayer / MediaSession / PlayerController / connect / 主线程 runBlocking / lyric / 状态栏沉浸 / mini player / PlaybackService" | 读 player rules.md + incidents.md → 不在主线程 runBlocking + bounded await → 跑 `:player:testDebugUnitTest` + `:player:connectedDebugAndroidTest` | `controller-connect.md`、`lyrics-follow-and-seek.md`、`immersive-statusbar-vs-content.md`、`playback-service-lifecycle.md` |
| **test-stability-skill** | "单测 hang / runBlocking / runTest / advanceUntilIdle / Robolectric / DataStore multiple active / D8 OOM / @Ignore / instrumentation runner / connectedAndroidTest" | 读 test rules.md + incidents.md → 用 `runTest(mainDispatcherRule.dispatcher)` + bounded await + 唯一 prefs 文件 + feature runner baseline → 跑 contract-test 抓违规 | `runtest-idiom.md`、`bounded-await.md`、`datastore-isolation.md`、`androidtest-runner-baseline.md`、`ignore-policy.md` |
| **harness-curator-skill** | "巡检 harness / 更新错误库 / 盘点 incidents / 校核 rules / 校核 guard / 同步 user memory 项目级条目 / 生成 harness 报告" | 见 §4.3 工作流 | `curate-workflow.md`、`drift-detection.md`、`memory-promotion.md`、`report-template.md` |

每个 SKILL.md 头部固定包含一段"必读 gate"：

```markdown
## 必读 gate

调起本 skill 前，必须 Read：

- `docs/dev-harness/<area>/rules.md`
- `docs/dev-harness/<area>/incidents.md`
- `docs/dev-harness/incidents/index.md`（仅 harness-curator-skill）
```

### 4.3 `harness-curator-skill` 工作流

`SKILL.md` body checklist 由 AI 直接执行：

1. **盘点输入**
   - `git log --since="<N> 天前" --pretty=format:'%h %ad %s' --date=short`
   - `docs/dev-harness/incidents/index.md` 全部 `status=active` 条目
   - 每域 `rules.md` 与 `incidents.md`
   - `~/.claude/projects/.../memory/MEMORY.md`（识别项目级、可跨工具 promote 的条目；纯个人会话偏好留存原位）

2. **drift 检测**
   - 每条 incident 的 `rule_ref` anchor 在 rules.md 中是否存在；不存在 → 报"orphan incident"。
   - 每条 incident 的 `guard.target` 文件是否存在；不存在 → 报"missing guard"。
   - 每条 incident 的 `signature` 命令在 HEAD 仓库执行，期望"无匹配"——若有匹配 → 报"recurrence detected"，列出文件:行。
   - 每条 rule 是否至少被 1 个 active incident 反向引用（`implemented_by`）；否则报"rule without evidence"。
   - DOCS_STATUS.md 是否登记本期所有 dev-harness 文档；缺则报。
   - skills 软链是否完整（跑 `scripts/dev-harness/symlinks-check.sh`）。

3. **新增候选挖掘**
   - 从最近 commit message + PR description 抽取关键词 *fix / hang / leak / crash / OOM / deadlock / regression* 等；为每条候选生成草稿 incident（带 `id: TBD-1`、`status: draft`、root_cause 占位、guard 建议）。
   - 用户 auto-memory 项目级条目（rule 而非 incident 性质）→ 生成"提议合入 docs/dev-harness/<area>/rules.md"草稿。

4. **产出**
   - `.worktrees/harness-curate-YYYY-MM-DD/` 内生成 `REPORT.md`：分节 drift / recurrence / new candidates / memory promotion / 建议 diff（路径形态与本仓 `.worktrees/<branch-with-dashes>` 约定一致）。
   - 不直接写 `incidents.md` / `rules.md` / `index.md`；仅在 REPORT 里贴 patch 草稿（unified diff 或 fenced markdown 块），由用户确认后再合并。
   - 调用 worktree 的 git 操作（创建 worktree、切分支）严格遵循 AGENTS.md 的 worktree 约束。

5. **不在该 skill 范围**
   - 不直接修生产代码或 contract test 实现。
   - 不主动联网（除非 user 明确指示，比如要核对 RN 上游版本）。
   - 不删除任何 incident（仅 propose status 改 superseded / stale）。

### 4.4 触发节奏

设计上仍以手工 / 对话触发为主：用户说"巡检 Harness"或"看下错误库"即调用。两个被动节奏不在 v1 实现：

- 月度 issue 提醒（GitHub Action）→ 后续运营再加。
- release 前必跑 → 后续运营再加。

### 4.5 对 Claude Code auto-memory 的处置

- 调起 `harness-curator-skill` 时主动读 `~/.claude/projects/.../memory/MEMORY.md`，识别项目级 rule 候选并 propose 合入对应 `docs/dev-harness/<area>/rules.md`。
- 个人会话偏好（"用户喜欢简短回复"、"喜欢中文回应"等）留存原位，不 promote。
- 项目级 rule promote 落库后，建议在 user auto-memory 中删除已 promote 的条目，避免双源；本 skill 仅 propose，不直接删除 user memory。

## 5. 测试守门：contract test、grep 脚本、CI gate

### 5.1 contract test 命名与位置

默认放 JVM 单测，原因：在 `:app:testDebugUnitTest` 等已有 task 内运行，无需 emulator，CI 极快。只有真的需要 Compose UI / Media3 controller 真实状态的 case 才用 `androidTest`，归 nightly / instrumented 通道，不进 dev-harness gate 必跳作业。

```text
<module>/src/test/java/com/zili/android/musicfreeandroid/<module>/harness/contracts/
  <Area><Concern>ContractTest.kt
```

命名硬约定：

- 文件 / 类名：`<Area><Concern>ContractTest`，例：`UiNoRawTopAppBarContractTest`、`TestRunTestIdiomContractTest`、`PlayerControllerSetupContractTest`。
- 包名后缀：`harness.contracts`，给 Gradle `--tests '*harness.contracts.*'` 一刀过滤。
- 每个 `@Test` 方法 KDoc 头一行写 `Guards INC-YYYY-NNNN. See docs/dev-harness/<area>/rules.md#<anchor>.`，失败信息也必须包含同样的 incident ID + 修复指引行。

测试实现风格：

- 主体是文件遍历 + 文本 / 正则扫描 + allowlist。`File.walkTopDown()` 即可，不引入额外依赖。
- 每个 contract 测试自带 hard-coded `allowedFiles: Set<String>`；新增豁免必须改测试 + 在 incidents.md 该条目下记一笔"豁免：路径 + 原因"。

### 5.2 v1 种子（12 条 incident → 守门类型）

| ID | guard | 落地动作 |
|---|---|---|
| INC-2026-0001 | contract-test | **新增** `app/src/test/.../harness/contracts/TestRunTestIdiomContractTest.kt`：扫 `*ViewModelTest.kt`，禁止 `runBlocking { ... .first { ... } ... }` 模式，allowlist 显式列出 |
| INC-2026-0002 | contract-test | **新增** `app/src/test/.../harness/contracts/PlayerControllerSetupContractTest.kt`：JVM 静态扫 `player/src/androidTest/**/*Test.kt`，禁止 `mainExecutor.execute { runBlocking { ... } }` 与无界 `latch.await()` 模式 |
| INC-2026-0003 | grep | embedded grep 验证 `gradle.properties` 中 `org.gradle.jvmargs` 含 `-Xmx` 且数值 ≥ 4096m |
| INC-2026-0004 | contract-test | **新增** `plugin/src/test/.../harness/contracts/PluginDataStoreIsolationContractTest.kt`：扫 `plugin/src/androidTest/**/*Test.kt` 中 `PreferenceDataStoreFactory.create(`，要求同源代码上下文 5 行内出现 `UUID.randomUUID()` 或自定义 `testPreferencesFile(` |
| INC-2026-0005 | contract-test | **新增** `app/src/test/.../harness/contracts/FeatureAndroidTestRunnerBaselineContractTest.kt`：扫 `feature/*/build.gradle.kts`，要么不声明 `testInstrumentationRunner`，要么必须同时声明 `androidTestImplementation(libs.androidx.test.runner)` |
| INC-2026-0006 | contract-test | **重定位** 现有 `app/src/test/.../navigation/MusicFreeNavTransitionsTest.kt` → 新增 thin 包装 `UiNavAnimationDurationContractTest.kt` 在 `harness/contracts/` 复用同断言；保留原文件不动以减少 diff |
| INC-2026-0007 | grep | embedded grep `TopAppBarDefaults.topAppBarColors\(` 在 `--include='*.kt'` 仓库范围内除 `core/ui/MusicFreeScreenChrome.kt` 外应零匹配 |
| INC-2026-0008 | grep + manual | embedded grep 检查 `app/src/main/java/.../MainActivity.kt` 不含 `WindowInsetsSides.Top` 单独出现；manual 部分写在 incident 里 |
| INC-2026-0009 | manual | v1 manual + TODO；后续若再有跨线程崩溃事件，升级为 contract-test / runtime invariant；在 incident 字段写明"未自动化原因 = 静态扫成本高，运行时检测会侵入生产代码" |
| INC-2026-0010 | contract-test | **新增** `plugin/src/test/.../harness/contracts/PluginNetworkTestGateContractTest.kt`：扫 `plugin/src/androidTest/**/*Test.kt` 中出现真域名（`kstore.vip` 等显式列表）的文件，必须命名 `*NetworkIntegrationTest.kt` 且类内出现 `Assume.assumeTrue` 引用 `pluginNetworkTests` |
| INC-2026-0011 | manual | 视觉性，标记 manual + spec 反向链接；不进 gate |
| INC-2026-0012 | contract-test | **新增 thin 包装** 复用既有 `feature/player-ui/src/test/.../*LyricTest*.kt` 与 `*MiniPlayerContentTest.kt` 关键断言（防误删 / 退化），登记进 `harness/contracts/` |

落地后净新增 contract test 文件 ≈ 6（thin wrappers 不算重写）。所有新文件第一行 KDoc 写明 `Guards INC-XXXX`，并在 `incidents.md` 反向写 `guard.target: <相对路径>`。

### 5.3 grep-check.py

`scripts/dev-harness/grep-check.py`：

- 输入：四个 `docs/dev-harness/<area>/incidents.md`。
- 解析每个 H2 块的 `id` / `status` / `guard.type` / `signature: |` 字段。
- 仅处理 `status=active` 且 `guard.type=grep`（或包含 grep 的复合 guard）的条目。
- 逐条用 `subprocess.run(cmd, shell=True, cwd=repo_root)` 跑 signature；
  - signature 命令的语义约定为："匹配代表违规"——退出码 0 + stdout 非空 → fail。
  - signature 必须自带过滤路径，约定每条 grep 都包含 `--exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle`。
- 任意一条失败：脚本退出码 1，打印 incident ID + 命令 + 头 20 行匹配。
- ≤ 60 行 Python，无第三方依赖（仅 `pathlib` + `re` + `subprocess`）。

### 5.4 symlinks-check.sh

`scripts/dev-harness/symlinks-check.sh`：

- 检查 `.claude/skills/<name>` 与 `.codex/skills/<name>` 是否是符号链接、目标是否落在 `.agents/skills/<name>`。
- 检查 `.agents/skills/<name>/references/rules.md` 与 `incidents.md` 的软链是否指向 `docs/dev-harness/<area>/`。
- 任意不一致 → 退出码 1，stderr 输出"link X 指向 Y, 期望 Z"。
- 支持 `--allow-empty` 模式（PR 1 阶段使用）：未建链不报错，仅校验已建链的目标正确。

### 5.5 check.sh（本地一键）

`scripts/dev-harness/check.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

bash scripts/dev-harness/symlinks-check.sh
python3 scripts/dev-harness/grep-check.py
./gradlew \
  :app:testDebugUnitTest :core:testDebugUnitTest :data:testDebugUnitTest \
  :downloader:testDebugUnitTest :plugin:testDebugUnitTest :player:testDebugUnitTest \
  :feature:home:testDebugUnitTest :feature:player-ui:testDebugUnitTest \
  :feature:search:testDebugUnitTest :feature:settings:testDebugUnitTest \
  --tests '*harness.contracts.*' --no-daemon
```

### 5.6 CI 必跳作业

新建 `.github/workflows/dev-harness-gate.yml`（独立 workflow）：

```yaml
name: Dev Harness Gate
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main, 'feat/**', 'fix/**' ]

jobs:
  gate:
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v3
      - name: Symlinks check
        run: bash scripts/dev-harness/symlinks-check.sh
      - name: Grep guards
        run: python3 scripts/dev-harness/grep-check.py
      - name: Contract tests (JVM)
        run: |
          ./gradlew \
            :app:testDebugUnitTest :core:testDebugUnitTest :data:testDebugUnitTest \
            :downloader:testDebugUnitTest :plugin:testDebugUnitTest :player:testDebugUnitTest \
            :feature:home:testDebugUnitTest :feature:player-ui:testDebugUnitTest \
            :feature:search:testDebugUnitTest :feature:settings:testDebugUnitTest \
            --tests '*harness.contracts.*' --no-daemon
```

GitHub branch protection（如已配置）追加 `Dev Harness Gate / gate` 为 required check。如未配置 branch protection，本 spec 不强制开启（属仓库治理操作而非代码 PR），仅写入 §10 后续运营建议。

### 5.7 显式不在 gate 内的项

- `./gradlew lint`、`detekt` 自定义规则。
- 完整单元测试套件（gate 仅跑 `*harness.contracts.*` 子集；全量套件由现有或未来作业触发）。
- `connectedAndroidTest`（emulator 慢 + flaky；保留本地与 nightly 通道）。
- release 签名 / APK 产物（与 `android-release-apk.yml` 解耦）。

## 6. worktree 编排与 3 PR 拆分

### 6.1 worktree

按 AGENTS.md 的"`git worktree` 默认 + `.worktrees/<branch-name>`"约束：

```text
.worktrees/feat-dev-harness-foundation   # PR 1, branch: feat/dev-harness-foundation
.worktrees/feat-dev-harness-skills       # PR 2, branch: feat/dev-harness-skills
.worktrees/feat-dev-harness-contracts    # PR 3, branch: feat/dev-harness-contracts
```

（路径将 `/` 折成 `-` 与本仓现有 `.worktrees/feat-logging-system` 等一致。）

实施动作（实现计划首步检查，本 spec 不直接执行）：

```bash
grep -n 'worktrees' .gitignore   # 已确认本期已忽略
git worktree add .worktrees/feat-dev-harness-foundation -b feat/dev-harness-foundation main
git worktree add .worktrees/feat-dev-harness-skills     -b feat/dev-harness-skills     main
git worktree add .worktrees/feat-dev-harness-contracts  -b feat/dev-harness-contracts  main
```

### 6.2 三个 PR

| PR | 范围 | 主要触及文件 | 净新增 LOC 估计 |
|---|---|---|---|
| **PR 1 — foundation** | 目录骨架 + AGENTS / DOCS_STATUS / 入口文档 + 12 条 incident 全量种子（contract-test 类型条目暂记 `guard.target = TBD`） + grep-check.py + symlinks-check.sh + check.sh + `.github/workflows/dev-harness-gate.yml`（启用 symlinks `--allow-empty` + grep；contract-test 步骤暂留为 no-op 提示） + 把 `docs/ui-harness/screen-chrome-rules.md` 内容迁到 `docs/dev-harness/ui/rules.md` + 原位 redirect stub | `AGENTS.md`、`docs/DOCS_STATUS.md`、`docs/dev-harness/**`、`docs/ui-harness/screen-chrome-rules.md`、`scripts/dev-harness/**`、`.github/workflows/dev-harness-gate.yml` | ~600 |
| **PR 2 — skills** | 5 个 SKILL.md + `references/` + 软链；CI 切到 symlinks 严格模式；user auto-memory 中识别为项目级的 2 条 rule 合入对应 `rules.md` | `.agents/skills/{ui-harness,plugin-system,media-player,test-stability,harness-curator}-skill/**`、`.claude/skills/<name>` 软链、`.codex/skills/<name>` 软链、`docs/dev-harness/ui/rules.md` 微调 | ~800 |
| **PR 3 — contracts** | 6 个新 contract test + 3 个 thin wrapper + incidents `guard.target` 字段全部填齐 + CI 启用 contract-test 步骤 | `<module>/src/test/.../harness/contracts/**`、`docs/dev-harness/<area>/incidents.md`（仅字段更新） | ~700 |

执行顺序：PR 1 先合（spine），PR 2 与 PR 3 之间 PR 2 优先（skills 的 `references/rules.md` 软链需要 PR 1 已落地）；PR 3 可与 PR 2 并行开发，但合并入主线时必须基于 PR 1 + PR 2 已合的状态重跑 CI。

## 7. 验收闸门

### 7.1 PR 1 验收

```bash
bash scripts/dev-harness/symlinks-check.sh --allow-empty
python3 scripts/dev-harness/grep-check.py
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest --no-daemon
```

人工核对：

- AGENTS.md 顶部"Dev Harness 强制入口"一节链接全部相对路径、可点开。
- `docs/ui-harness/screen-chrome-rules.md` 留 redirect stub 一行可读。
- `docs/dev-harness/incidents/index.md` 12 条 ID 全部唯一。
- `.gitignore` 已忽略 `.worktrees/`。

### 7.2 PR 2 验收

```bash
bash scripts/dev-harness/symlinks-check.sh
python3 scripts/dev-harness/grep-check.py
./gradlew :app:assembleDebug --no-daemon
```

人工核对：

- 5 个 `SKILL.md` frontmatter 都含 `name` + `description`，description 含 §4.2 表格中触发词。
- 每个 skill 的 `references/rules.md` 与 `references/incidents.md` 软链 `realpath` 落在 `docs/dev-harness/<area>/`。
- user auto-memory 中两条项目级条目已 promote：`db-schema-during-dev` → `docs/dev-harness/ui/rules.md` 末尾"待开域 backlog"段；`UI: MD3 components, RN layout` → `docs/dev-harness/ui/rules.md` 设计原则段。
- 调用 `harness-curator-skill`（手工）输出 REPORT.md 显示 0 drift / 0 recurrence。

### 7.3 PR 3 验收

```bash
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

人工核对：

- `docs/dev-harness/incidents/index.md` 中 `guard.type=contract-test` 条目的 `guard.target` 全部为已存在文件路径。
- 每个 contract test 文件 KDoc 头一行写 `Guards INC-XXXX`。
- CI `dev-harness-gate.yml` 整体绿。

### 7.4 跨 PR 终态指标

| 指标 | 目标 |
|---|---|
| `docs/dev-harness/INDEX.md` 列出 4 域 + skills + incidents 索引 | 是 |
| AGENTS.md "Dev Harness 强制入口" 段已加 | 是 |
| DOCS_STATUS.md 登记 dev-harness 全部文档 | 是 |
| 5 个 skill `SKILL.md` 落盘 + 软链到 `.claude/.codex` | 是 |
| 12 条 v1 incident 都有 `status: active` + 完整字段（含 manual 类型） | 是 |
| 6 个新 contract test + 3 thin wrapper 完成 | 是 |
| `dev-harness-gate.yml` 在 PR / push to main 自动跑且全绿 | 是 |
| user auto-memory 中项目级条目数 | 0（仅留个人会话偏好） |
| 跑 `harness-curator-skill` 一次得到 0 drift / 0 recurrence REPORT | 是 |

## 8. 风险与缓解

| 风险 | 触发 | 缓解 |
|---|---|---|
| 软链在 Windows 表现差 | 团队后续引入 Windows 开发者 | 项目目标平台为 Android dev（macOS / Linux 主流）；后续若需 Windows 支持，加 `scripts/dev-harness/install.sh` 在该平台改用拷贝模式；本 spec v1 不做。 |
| `grep-check.py` 在没有 Python 3 的本地机器失败 | 旧机器 / 偶发环境 | 脚本第一行 `#!/usr/bin/env python3`；macOS 与 Ubuntu CI runner 自带；本地缺失时 `check.sh` 给清晰报错而非静默跳过。 |
| 历史 plans 中规则与新 rules.md 矛盾 | 旧 plan 残留旧动画时长 / 旧顶部 inset 写法 | rules.md 头部声明 `docs/superpowers/plans/*.md` 为历史快照、非当前规范；DOCS_STATUS.md 中所有 plans 已标"历史记录"。无需逐 plan 修订。 |
| `harness-curator-skill` 直接改 repo 导致状态污染 | AI 在 curator 工作流误判 | §4.3 已写"产 REPORT，不直接落盘"；REPORT 含 unified diff，由人确认后再合并。PR 3 加一条 contract test：扫 `harness-curator-skill/SKILL.md` 必须含"不直接修改 incidents.md / rules.md"字样。 |
| INC-2026-0009 QuickJS 单线程 manual guard 漂移 | 未来 QuickJS 跨线程 bug 复发 | incident 字段写明"未自动化原因 + 升级触发条件 = 再现一次跨线程崩溃即升级 contract-test"；harness-curator 在巡检时显式列 manual incidents，提醒人工复核。 |
| CI 必跳作业拉慢 PR | gate 覆盖测试集大 | gate 仅跑 `*harness.contracts.*` 子集（≤ 10 个测试，每个 < 5 秒）+ grep + symlinks，整 job < 5 分钟。 |
| 添加 `dev-harness-gate.yml` 后历史 PR 不通过 | 已有 PR 在 fork 分支落后 | gate 仅在 push to main / PR to main + feat/* + fix/* 触发；历史 PR rebase 后即生效；不强制 backport。 |
| `docs/home-fidelity/` 与 `docs/dev-harness/ui/` 关系不清 | 后续开发误以为重复 | rules.md 设计原则段加交叉引用："首页 fidelity 取证规范见 `docs/home-fidelity/`，非本 ui rules 范围"。 |
| user auto-memory promote 后双源 | 同一条 rule 同时存在两边 | promote 时 `harness-curator-skill` propose 在 user memory 中删除已迁移条目；用户确认后由 Claude Code 自身管理（本 spec 不直接动 user 本机文件）。 |

## 9. 不在本 spec 范围（防伪 backlog）

- detekt / lint 自定义规则；运行时 invariant assertion；Compose UI 结构 snapshot test。
- `data` / `nav` / `download` 等域的 rules.md。
- `.cursor/rules/`、`.windsurfrules` 等其他 AI 工具入口接入。
- `harness-curator-skill` 月度自动 issue 提醒 / release 前必跑等运营自动化。
- branch protection required check 配置（运营操作，PR 合入后由仓库管理员手动开）。
- 把 `.worktrees/<path>` 路径转换为 `<branch-with-slash>` 等命名重构（沿用现有 `.worktrees/<branch-with-dashes>` 约定）。
- 主动改写 `docs/superpowers/specs/`、`plans/` 历史文档（仅在 DOCS_STATUS 标历史记录，文档本身不动）。
- 改 release 签名 / APK 产物 workflow。

## 10. 计划交接

本 spec 完成 + 用户 review 后，下一步调用 `writing-plans` skill 输出实现计划。implementation plan 至少拆为：

1. `.gitignore` 与 `.worktrees/` 检查（首步，独立 commit）。
2. PR 1 — foundation（骨架 + 入口 + 12 incident 种子 + 脚本 + CI）。
3. PR 2 — skills（5 skills + 软链 + memory promotion）。
4. PR 3 — contracts（6 contract test + 3 thin wrapper + guard.target 填齐）。
5. 运营尾声：仓库管理员开 branch protection required check（仅文字指引，本仓库 AI 不执行）。

每步内部再拆为：worktree 创建 → 文件改动 → 本地验证 → 提交 → 推送 → 开 PR。

合并 PR 1 / PR 2 / PR 3 之前，需同步更新 `docs/DOCS_STATUS.md`：

- 新增 `docs/dev-harness/INDEX.md`、`docs/dev-harness/<area>/rules.md`（4 条）登记为"当前规范（Dev Harness）"。
- 本 spec 在 PR 1 合并后（已作为实现计划输入）保持"当前规范（Dev Harness 基础设施专项）"。三 PR 全合后将本 spec 状态降为"当前参考"，并在"最后校验"字段记录验收日期。
