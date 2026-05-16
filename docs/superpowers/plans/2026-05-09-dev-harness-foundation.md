# Dev Harness Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the cross-tool / cross-developer Dev Harness: `docs/dev-harness/` as the unified entry, structured incidents ledger, 5 AI skills (single source + symlinks), JVM contract tests, grep guards, and a CI `dev-harness-gate` job — without breaking existing build / test signals.

**Architecture:** Three sequential PRs each in its own worktree. PR 1 ships the spine (docs + scripts + CI workflow with grep-only guards). PR 2 ships the 5 skills + symlinks + user-memory promotion. PR 3 ships the contract tests and finalizes the CI gate. Spec at `docs/superpowers/specs/2026-05-09-dev-harness-foundation-design.md`.

**Tech Stack:** Markdown + YAML frontmatter; Bash + Python 3 (stdlib only); Kotlin JUnit 4 contract tests living under `<module>/src/test/.../harness/contracts/`; GitHub Actions; Git worktrees per AGENTS.md convention `.worktrees/<branch-with-dashes>/`.

---

## File Structure

| Path | Created By | Notes |
|---|---|---|
| `docs/dev-harness/INDEX.md` | PR 1 | Top-level overview |
| `docs/dev-harness/ui/rules.md` | PR 1 | Migrated from `docs/ui-harness/screen-chrome-rules.md` + new sections |
| `docs/dev-harness/plugin/rules.md` | PR 1 | New |
| `docs/dev-harness/player/rules.md` | PR 1 | New |
| `docs/dev-harness/test/rules.md` | PR 1 | New |
| `docs/dev-harness/ui/incidents.md` | PR 1 | Seed: INC-2026-0006/0007/0008 |
| `docs/dev-harness/plugin/incidents.md` | PR 1 | Seed: INC-2026-0009/0010 |
| `docs/dev-harness/player/incidents.md` | PR 1 | Seed: INC-2026-0011/0012 |
| `docs/dev-harness/test/incidents.md` | PR 1 | Seed: INC-2026-0001..0005 |
| `docs/dev-harness/incidents/index.md` | PR 1 | Top-level incident index |
| `docs/dev-harness/incidents/template.md` | PR 1 | Template for new incidents |
| `docs/ui-harness/screen-chrome-rules.md` | PR 1 (modify) | Replace with redirect stub |
| `AGENTS.md` | PR 1 (modify) | Add `Dev Harness 强制入口` + `项目记忆与守门约束` + update doc-entry list + repoint UI Harness Rules link |
| `docs/DOCS_STATUS.md` | PR 1 (modify) | Register dev-harness docs; demote screen-chrome-rules to migrated |
| `scripts/dev-harness/grep-check.py` | PR 1 | Runs incident grep guards |
| `scripts/dev-harness/symlinks-check.sh` | PR 1 | Validates skill / reference symlinks |
| `scripts/dev-harness/check.sh` | PR 1 | Local one-shot driver |
| `.github/workflows/dev-harness-gate.yml` | PR 1 (modify in PR 2 + 3) | CI gate job |
| `.agents/skills/ui-harness-skill/SKILL.md` | PR 2 | Frontmatter + workflow checklist |
| `.agents/skills/ui-harness-skill/references/*.md` | PR 2 | Workflow detail; rules.md / incidents.md as symlinks |
| `.agents/skills/plugin-system-skill/**` | PR 2 | Same shape |
| `.agents/skills/media-player-skill/**` | PR 2 | Same shape |
| `.agents/skills/test-stability-skill/**` | PR 2 | Same shape |
| `.agents/skills/harness-curator-skill/**` | PR 2 | Plus curate-workflow / drift-detection refs |
| `.claude/skills/<name>` | PR 2 | Symlink → `.agents/skills/<name>` |
| `.codex/skills/<name>` | PR 2 | Symlink → `.agents/skills/<name>` |
| `app/src/test/java/com/hank/musicfree/harness/contracts/TestRunTestIdiomContractTest.kt` | PR 3 | Guards INC-2026-0001 |
| `app/src/test/.../harness/contracts/PlayerControllerSetupContractTest.kt` | PR 3 | Guards INC-2026-0002 |
| `app/src/test/.../harness/contracts/FeatureAndroidTestRunnerBaselineContractTest.kt` | PR 3 | Guards INC-2026-0005 |
| `app/src/test/.../harness/contracts/UiNavAnimationDurationContractTest.kt` | PR 3 | Thin wrapper for INC-2026-0006 |
| `plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginDataStoreIsolationContractTest.kt` | PR 3 | Guards INC-2026-0004 |
| `plugin/src/test/.../harness/contracts/PluginNetworkTestGateContractTest.kt` | PR 3 | Guards INC-2026-0010 |
| `feature/player-ui/src/test/.../harness/contracts/LyricFollowDebounceContractTest.kt` | PR 3 | Thin wrapper for INC-2026-0012 |

---

## Phase 0 — Pre-flight (run from main checkout)

### Task 0.1: Verify `.worktrees/` is gitignored

**Files:**
- Read: `.gitignore`

- [ ] **Step 1: Inspect**

Run:
```bash
grep -n '^\.worktrees/' .gitignore
```

Expected output:
```
12:.worktrees/
```

If the line is missing, add it as the **only** new entry in a one-line commit on `main` before continuing. Do not group with other PRs.

- [ ] **Step 2: Confirm clean main**

Run:
```bash
git status
git log --oneline -1
```

Expected: working tree clean and HEAD on `main`. If not, stop and consult the user.

---

## Phase 1 — PR 1: Foundation

Branch: `feat/dev-harness-foundation`. Worktree path: `.worktrees/feat-dev-harness-foundation`.

All Phase 1 tasks run from inside that worktree. Use `cd` only at the start of each shell session; otherwise prefix git commands with `git -C .worktrees/feat-dev-harness-foundation` if you stay in the main checkout.

### Task 1.1: Create the worktree

**Files:**
- Create (worktree directory): `.worktrees/feat-dev-harness-foundation/`

- [ ] **Step 1: Create the worktree from main**

Run from main checkout:
```bash
git worktree add .worktrees/feat-dev-harness-foundation -b feat/dev-harness-foundation main
cd .worktrees/feat-dev-harness-foundation
```

Expected: branch created, HEAD checked out, prompt now in worktree.

- [ ] **Step 2: Sanity check baseline**

Run inside the worktree:
```bash
git status
git log --oneline -1
```

Expected: clean working tree, HEAD = main HEAD.

### Task 1.2: Scaffold `docs/dev-harness/` directory tree

**Files:**
- Create: `docs/dev-harness/INDEX.md`
- Create (empty placeholder): `docs/dev-harness/ui/`, `docs/dev-harness/plugin/`, `docs/dev-harness/player/`, `docs/dev-harness/test/`, `docs/dev-harness/incidents/`

- [ ] **Step 1: Create directory tree**

Run:
```bash
mkdir -p docs/dev-harness/{ui,plugin,player,test,incidents}
```

- [ ] **Step 2: Write `docs/dev-harness/INDEX.md`**

```markdown
# Dev Harness — INDEX

> 文档状态：当前规范（Dev Harness 总入口）
> 适用范围：UI / 插件 / 播放器 / 测试 四域的开发守门、错误库与 AI skills 关联
> 直接执行：是
> 当前入口：[AGENTS](../../AGENTS.md) ｜ [DOCS_STATUS](../DOCS_STATUS.md)
> 设计来源：[Dev Harness 基础设施设计](../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)
> 最后校验：2026-05-09

## 域规则（rules）

| 域 | rules.md | incidents.md |
|---|---|---|
| UI / Compose Screen | [ui/rules.md](./ui/rules.md) | [ui/incidents.md](./ui/incidents.md) |
| 插件系统 | [plugin/rules.md](./plugin/rules.md) | [plugin/incidents.md](./plugin/incidents.md) |
| 播放器 / Media3 | [player/rules.md](./player/rules.md) | [player/incidents.md](./player/incidents.md) |
| 测试 / 测试基建 | [test/rules.md](./test/rules.md) | [test/incidents.md](./test/incidents.md) |

## 错误库

- 全仓索引：[incidents/index.md](./incidents/index.md)
- 新增条目模板：[incidents/template.md](./incidents/template.md)
- ID 规则：`INC-YYYY-NNNN`，年 + 4 位序号；递增不回收，跨域全局唯一。

## AI skills

- 单一来源：`.agents/skills/<area>-skill/`，软链至 `.claude/skills/`、`.codex/skills/`。
- 5 个 skill：`ui-harness-skill`、`plugin-system-skill`、`media-player-skill`、`test-stability-skill`、`harness-curator-skill`。

## CI 守门

- 工作流：`.github/workflows/dev-harness-gate.yml`
- 本地一键：`bash scripts/dev-harness/check.sh`

## 项目记忆边界

- 错误库 / 强约束 / AI skills 都进 git，跨 AI 工具与跨开发者生效。
- Claude Code 个人 auto-memory（`~/.claude/projects/.../memory/MEMORY.md`）仅承载个人会话偏好，不放项目级 rule。
- 历史决策快照在 `docs/superpowers/specs/` 与 `plans/`，仅参考。
```

- [ ] **Step 3: Stage and commit**

```bash
git add docs/dev-harness/INDEX.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): scaffold dev-harness INDEX and area directories

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: 1 file changed.

### Task 1.3: Migrate `docs/ui-harness/screen-chrome-rules.md` → `docs/dev-harness/ui/rules.md`

**Files:**
- Create: `docs/dev-harness/ui/rules.md`
- Modify: `docs/ui-harness/screen-chrome-rules.md` (replace body with one-line redirect stub)

- [ ] **Step 1: Read the existing file**

```bash
cat docs/ui-harness/screen-chrome-rules.md
```

Expected: 80 lines, headers `Screen 切换动画`, `普通 AppBar 页面`, `特殊 Chrome 页面`, `MainActivity 责任边界`, `新增 Screen 默认做法`.

- [ ] **Step 2: Write `docs/dev-harness/ui/rules.md`**

Copy the entire body of `docs/ui-harness/screen-chrome-rules.md` into the new file with header rewrites. Final content:

```markdown
# UI / Compose Screen Rules

> 文档状态：当前规范（Dev Harness — UI）
> 适用范围：Screen 切换动画、普通 AppBar 页面、沉浸式状态栏、Compose UI 设计原则
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[UI Harness Screen Chrome 设计](../../superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md)
> 最后校验：2026-05-09

## 设计原则

- 组件层使用 Material3（`androidx.compose.material3.*`）；信息架构与布局对齐 RN 原版（`../MusicFree/src/pages/`）。组件实现可与 RN 不同，但用户路径、菜单结构与默认状态必须一致。
- 首页 UI fidelity 取证规范见 [docs/home-fidelity/](../../home-fidelity/)，非本 ui rules 范围；新增页面级 fidelity 规范前先讨论是否折成 `docs/dev-harness/ui/fidelity/` 子域。

## 强制入口

新增或修改 Compose Screen 前，必须先读取本文件。

本文件是 Screen 切换动画、普通 AppBar、状态栏沉浸式处理的唯一当前规则来源。`docs/superpowers/plans/*.md` 中出现的旧动画时长、旧 AppBar 写法、旧状态栏做法均视为历史执行快照，不可作为当前规范。

## Screen 切换动画 {#rule-nav-animation-100ms}

implemented_by: INC-2026-0006

- 普通页面 MUST 使用全局默认 `slide_from_right` 动画。
- 普通页面前进动画 MUST 为新页面从右向左进入、旧页面向左退出。
- 普通页面返回动画 MUST 为上一页从左侧回入、当前页向右退出。
- 普通页面默认动画时长 MUST 为 `100ms`，对齐 RN 原版 `src/entry/index.tsx` 中的 `animationDuration: 100`；RN 仓库位置按 [AGENTS](../../../AGENTS.md) 的同级目录 `../MusicFree` 约定解析。
- `AppNavHost` MUST 引用集中 transition helper（`MusicFreeNavTransitions.kt`），MUST NOT 在 `NavHost` 参数里手写 `tween(250)` 或其他局部时长。
- Screen 内部 MUST NOT 用局部 `AnimatedContent`、`AnimatedVisibility` 或自定义 offset 动画伪装页面切换。
- 特殊页面若需要不同页面切换动画，MUST 在 route/destination 注册处显式覆盖，并在本文件“特殊 Chrome 页面”中登记原因。

## 普通 AppBar 页面 {#rule-no-raw-material3-topappbar}

implemented_by: INC-2026-0007

普通 AppBar 页面 MUST 使用 `com.hank.musicfree.core.ui.MusicFreeScreenScaffold` 或 `MusicFreeTopAppBar`。

普通 AppBar 页面 MUST NOT 直接手写以下模式：

```kotlin
TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MusicFreeTheme.colors.appBar,
    ),
)
```

普通 AppBar 页面状态栏规则：

- Activity 级别保持 edge-to-edge。
- 系统状态栏保持透明。
- `MusicFreeTheme.colors.appBar` MUST 铺到状态栏后方。
- AppBar 内容 MUST 从状态栏下方开始。
- AppBar 内容高度 MUST 对齐 RN `rpx(88)`。
- 标题文字 MUST 使用 `FontSizes.appBar` 和 `MusicFreeTheme.colors.appBarText`，除非页面设计文档声明了特殊标题内容。

## 特殊 Chrome 页面

以下页面不使用普通 AppBar，但 MUST 自行负责状态栏背景和顶部 inset：

- `HomeRoute` / `HomeScreen`：首页使用自定义 `HomeNavBar`，状态栏区域保持首页背景，不依赖 `MainActivity` 顶部 safe inset。
- `SearchRoute` / `SearchScreen`：搜索页使用自定义搜索栏，`appBar` 色延伸到状态栏后方。
- `PlayerRoute` / `PlayerScreen`：播放器是全屏沉浸式页面，顶部内容可以绘制到系统栏区域。
- `LocalRoute` / `LocalScreen`：当前 Android 实现没有普通 AppBar，必须显式添加顶部 status bar spacer，避免内容进入状态栏。

新增特殊 Chrome 页面时，必须在本节登记 route、Screen、原因和状态栏策略。

## MainActivity 责任边界 {#rule-mainactivity-no-implicit-top-inset}

implemented_by: INC-2026-0008

`MainActivity` 负责 App 级 `Scaffold`、MiniPlayer、横向 safe inset、底部 safe inset。

`MainActivity` MUST NOT 维护“普通页面统一补顶部 safe inset，某些页面排除”的隐式白名单。顶部 chrome 是 Screen 或公共 UI harness 的责任。

## 新增 Screen 默认做法

新增普通页面时，默认结构为：

```kotlin
MusicFreeScreenScaffold(
    title = "页面标题",
    onBack = onBack,
    modifier = modifier,
) { innerPadding ->
    // Page content starts here.
}
```

新增自定义顶部页面时，必须先说明为什么不能使用普通 AppBar，并使用 `MusicFreeStatusBarChrome` 或等价实现显式处理顶部状态栏区域。

## 待开域 backlog

- DB schema during dev：dev 阶段直接改 entity 类、不写 `Migration` 对象。这是 data 域 rule，等 `docs/dev-harness/data/rules.md` 引入时正式落入。来源：项目记忆 promotion。
```

- [ ] **Step 3: Replace `docs/ui-harness/screen-chrome-rules.md` with a redirect stub**

Overwrite the entire file with:

```markdown
# UI Harness Screen Chrome Rules（已迁移）

> 文档状态：已迁移
> 当前规范：[docs/dev-harness/ui/rules.md](../dev-harness/ui/rules.md)
> 旧 anchor 兼容：本 stub 保留以使历史 PR 与 plan 链接不失效。

本文件内容已并入 [docs/dev-harness/ui/rules.md](../dev-harness/ui/rules.md)。请直接读取新位置；本文件不再作为规则来源。
```

- [ ] **Step 4: Verify file contents**

Run:
```bash
wc -l docs/dev-harness/ui/rules.md docs/ui-harness/screen-chrome-rules.md
```

Expected: `dev-harness/ui/rules.md` ≥ 100 lines; `ui-harness/screen-chrome-rules.md` ≤ 10 lines.

- [ ] **Step 5: Commit**

```bash
git add docs/dev-harness/ui/rules.md docs/ui-harness/screen-chrome-rules.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): migrate ui screen-chrome-rules into ui/rules.md

Move the body to docs/dev-harness/ui/rules.md, anchor MUST clauses to
INC-2026-0006/0007/0008, fold the user-memory project-level UI design
principle into a new "设计原则" section, and leave the original file
as a one-paragraph redirect stub for backlink compatibility.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.4: Write `docs/dev-harness/plugin/rules.md`

**Files:**
- Create: `docs/dev-harness/plugin/rules.md`

- [ ] **Step 1: Write the file**

```markdown
# 插件系统 Rules

> 文档状态：当前规范（Dev Harness — Plugin）
> 适用范围：QuickJS runtime 线程模型、PluginManager 安装/更新编排、集成测试网络通道与 DataStore 隔离
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[QuickJS 线程修复设计](../../superpowers/specs/2026-04-19-quickjs-threading-fix-design.md)、[Android 测试稳定性设计](../../superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md)
> 最后校验：2026-05-09

## 强制入口

新增或修改 `:plugin` 模块代码 / 插件集成测试前，必须先读取本文件。

## QuickJS 线程模型 {#rule-quickjs-single-thread}

implemented_by: INC-2026-0009

- QuickJS `Context` / `JsBridge` MUST 仅在专用单线程 `CoroutineDispatcher` 上访问。
- 跨线程调用必须通过 `withContext(quickJsDispatcher)` 切回 owning thread。
- MUST NOT 在多线程 / `Dispatchers.IO` / `Dispatchers.Default` 上直接调用 `Context.evaluate(...)` 或 `JsBridge.invoke(...)`。
- 复发条件 + 升级触发：`harness-curator-skill` 巡检若发现新的 QuickJS 跨线程崩溃 commit，应将本条 rule 的 guard 升级为 contract-test。

## 集成测试网络通道 {#rule-network-test-gated}

implemented_by: INC-2026-0010

- `:plugin/src/androidTest/` 中依赖真实远端的测试 MUST 集中在 `*NetworkIntegrationTest.kt`。
- 此类测试类 `@Before` 必须执行 `Assume.assumeTrue("...", arg == "true")`，其中 `arg` 取自 instrumentation runner argument `pluginNetworkTests`。
- `:plugin/build.gradle.kts` 必须保留 `testInstrumentationRunnerArguments["pluginNetworkTests"]` 与 `-Pintegration` 的映射。
- 默认通道 (`./gradlew :plugin:connectedAndroidTest`) MUST 跳过真网测试；`-Pintegration` 才执行。
- 真域名（如 `kstore.vip`）出现在测试源中时，文件名必须以 `NetworkIntegrationTest.kt` 结尾且类内必须出现 `Assume.assumeTrue` 引用 `pluginNetworkTests`。

## DataStore 隔离 {#rule-datastore-per-instance-isolation}

implemented_by: INC-2026-0004

参见 [test/rules.md#rule-datastore-per-instance-isolation](../test/rules.md#rule-datastore-per-instance-isolation)。该规则同时影响 plugin 与 test 域，规范以 test/rules.md 为主，本文件保留交叉引用。

## PluginManager 编排

- `installFromUrl` 与 `updatePlugin` 编排路径 MUST 通过 `MockWebServer` 单测验证（`PluginManagerHttpLifecycleTest`），断言 request path 与磁盘内容、`plugins` StateFlow 单例。
- 真插件 JS 解析能力由 `:plugin/src/test/` 单测层守护，instrumentation 仅做编排验证。
```

- [ ] **Step 2: Commit**

```bash
git add docs/dev-harness/plugin/rules.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): add plugin rules for QuickJS threading and network gate

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.5: Write `docs/dev-harness/player/rules.md`

**Files:**
- Create: `docs/dev-harness/player/rules.md`

- [ ] **Step 1: Write the file**

```markdown
# 播放器 / Media3 Rules

> 文档状态：当前规范（Dev Harness — Player）
> 适用范围：PlayerController 连接与状态、MediaSessionService 生命周期、播放器沉浸式 chrome、歌词跟随
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[播放器状态栏避让设计](../../superpowers/specs/2026-05-04-player-statusbar-inset-design.md)、[播放页歌词交互修正设计](../../superpowers/specs/2026-05-05-player-lyrics-interaction-fix-design.md)
> 最后校验：2026-05-09

## 强制入口

新增或修改 `:player` 模块、`feature/player-ui` 模块、`MediaSessionService` 或 `PlayerController` 调用方前，必须先读取本文件。

## PlayerController 连接 {#rule-no-runblocking-on-mainthread}

implemented_by: INC-2026-0002

- `PlayerController.connect()` 调用 MUST 在 instrumentation test 线程执行（不在主线程 executor 内）。
- 若必须等待 connect 完成，MUST 用 `withTimeout(<= 5_000L) { ... }` 加 bounded 等待。
- 测试 helper 执行主线程动作时 MUST 用 bounded `latch.await(timeout, TimeUnit.SECONDS)`，捕获异常通过 `AtomicReference` 回传后 fail。
- MUST NOT 在 `context.mainExecutor.execute { ... }` 内嵌套 `kotlinx.coroutines.runBlocking { controller.connect() }`：会造成 MediaController async 连接因主线程阻塞永久挂起。

## 全屏沉浸式 chrome {#rule-immersive-content-respects-statusbar}

implemented_by: INC-2026-0011

- `PlayerScreen` 背景层 MAY 绘制到状态栏后方，但内容层 MUST 通过 `WindowInsets.statusBars` 显式避让。
- MUST NOT 让标题、控件、歌词卡片等可交互元素被状态栏遮挡。
- 该 rule 当前 guard 为 manual，由 `harness-curator-skill` 在巡检时显式列出，提醒人工复核截图与 RN 对齐。

## 歌词跟随防抖 {#rule-lyric-follow-debounce}

implemented_by: INC-2026-0012

- 自动跟随 MUST 与手动滑动状态互斥，避免在手动滑动期间被自动跟随重置。
- seek overlay 进入条件 MUST 由统一 helper 决定，不分散到多个 composable。
- 任何修改歌词跟随逻辑的 PR MUST 跑 `feature/player-ui` 相关单测：`MiniPlayerContentTest`、`LyricFollow*Test`、`LyricSeekOverlay*Test`。

## MediaSessionService 生命周期

- `PlaybackService` MUST 在 `onTaskRemoved`、`onDestroy` 中按 RN 行为停止当前播放或保留媒体通知（依现有实现，详见 `2026-05-04-playback-notification-design.md`）。
- 不在本 rule 强制具体策略；只要求改动 PR 对照该 spec。
```

- [ ] **Step 2: Commit**

```bash
git add docs/dev-harness/player/rules.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): add player rules for connect, immersive chrome, lyric debounce

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.6: Write `docs/dev-harness/test/rules.md`

**Files:**
- Create: `docs/dev-harness/test/rules.md`

- [ ] **Step 1: Write the file**

```markdown
# 测试 / 测试基建 Rules

> 文档状态：当前规范（Dev Harness — Test）
> 适用范围：JVM 单测异步范式、instrumentation 主线程模型、DataStore 隔离、feature 模块 androidTest runner 基线、Gradle JVM 内存基线、`@Ignore` 政策
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[Dev Harness 基础设施设计](../../superpowers/specs/2026-05-09-dev-harness-foundation-design.md)、[Android 测试稳定性设计](../../superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md)
> 最后校验：2026-05-09

## 强制入口

新增或修改任何 `*Test.kt` / `*build.gradle.kts` 测试 wiring / `gradle.properties` JVM 参数前，必须先读取本文件。

## ViewModel 单测 runTest 范式 {#rule-runtest-mandatory}

implemented_by: INC-2026-0001

- ViewModel 单测 MUST 用 `runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`。
- MUST NOT 在 `*ViewModelTest.kt` 中使用 `runBlocking { ... .first { predicate } ... }` 自旋谓词模式。
- 若需要等待 viewModelScope.launch 副作用，MUST 在 `runTest` block 内显式 `advanceUntilIdle()`，再读 `Flow.first()`（无谓词）。
- `MainDispatcherRule` 当前在 `:feature:settings`、`:feature:search` 各保留一份；不强制本期 dedup（详见非目标）。

## instrumentation test 主线程禁阻塞 {#rule-no-runblocking-mainthread-in-instrumentation}

implemented_by: INC-2026-0002

- `*Test.kt` 在 `androidTest/` 中 MUST NOT 使用 `context.mainExecutor.execute { runBlocking { ... } }` 或同义模式。
- `CountDownLatch.await()` MUST 提供有界 timeout，例如 `latch.await(5, TimeUnit.SECONDS)`。
- 异常 MUST 通过 `AtomicReference<Throwable?>` 等机制从主线程回传到测试线程，再 `throw` 触发 fail。

## Gradle JVM 内存基线 {#rule-gradle-jvmargs-baseline}

implemented_by: INC-2026-0003

- `gradle.properties` MUST 含 `org.gradle.jvmargs` 且 `-Xmx` 数值 ≥ 4096m。
- 全量 androidTest dex 合并需要至少 4 GiB 堆，否则 `:plugin:mergeExtDexDebugAndroidTest` 会 D8 OOM。
- 提升 heap 是仓库级基线，不要让开发者手动传 `-Dorg.gradle.jvmargs`。

## DataStore 隔离 {#rule-datastore-per-instance-isolation}

implemented_by: INC-2026-0004

- instrumentation test 中手工构造 `PreferenceDataStoreFactory.create(...)` 时，`produceFile` MUST 为每个 test 实例生成唯一文件名（如 `File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb")`）。
- MUST NOT 在 instrumentation test 复用固定 `*.preferences_pb` 路径；AndroidJUnit4 每个 `@Test` 创建新 test class 实例，DataStore 对同一文件路径的 active scope 有单例约束，固定文件名会触发 `There are multiple DataStores active for the same file`。
- `@After` MUST 调用 `pluginManager.uninstallAllPlugins()` 关闭 QuickJS 后再 `dataStoreScope.cancel()` 关闭 DataStore scope。

## feature 模块 androidTest runner 基线 {#rule-feature-androidtest-baseline}

implemented_by: INC-2026-0005

- 任何在 `feature/*/build.gradle.kts` 声明 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` 的模块 MUST 同时声明 `androidTestImplementation(libs.androidx.test.runner)`。
- 推荐同时引入 `androidTestImplementation(libs.androidx.junit)` 与 `androidTestImplementation(libs.androidx.espresso.core)` 作为基础组合，避免后续新增 androidTest 时再补依赖。
- 全量 `connectedAndroidTest` MUST 通过——空 androidTest 源的 feature 模块也必须能实例化 runner。

## `@Ignore` 政策

- `@Ignore` 在 `:plugin/src/androidTest/` 与 `:feature:settings/src/test/` 等位置当前应为 0。
- 新增 `@Ignore` MUST 同步登记 incident 并写明触发条件、升级方案；否则在下次 `harness-curator-skill` 巡检中报告。

## 网络通道门控

参见 [plugin/rules.md#rule-network-test-gated](../plugin/rules.md#rule-network-test-gated)。
```

- [ ] **Step 2: Commit**

```bash
git add docs/dev-harness/test/rules.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): add test rules for runTest, datastore isolation, runner baseline

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.7: Write `docs/dev-harness/incidents/template.md` and `index.md`

**Files:**
- Create: `docs/dev-harness/incidents/template.md`
- Create: `docs/dev-harness/incidents/index.md`

- [ ] **Step 1: Write `template.md`**

```markdown
# Incident Template

> 复制本文件为新条目时，先去掉本说明段。`id` 取下一个未占用的 `INC-YYYY-NNNN`，并在本期 `docs/dev-harness/incidents/index.md` 同步登记。

## INC-YYYY-NNNN — 一句话标题

- id: INC-YYYY-NNNN
- area: ui | plugin | player | test
- date: YYYY-MM-DD
- status: active | superseded by INC-XXXX | stale
- rule_ref: docs/dev-harness/<area>/rules.md#<anchor>
- guard:
    type: contract-test | grep | manual | grep + manual
    target: <relative path to test file>           # 仅 contract-test 类型必填
- signature: |
    <一行可直接跑的 grep / 测试入口命令；contract-test 类型可省略 signature 但必须保留 type/target>
- fix_ref: <相对路径或 commit hash>

### 根因

简短描述触发条件、症状、为什么会发生。

### 复发条件

何种代码或配置会再触发此问题。grep / contract-test 的设计依据。

### 教训

未来如何避免；已经写入 rule 的关键句。

### 备注（可选）

- 关联其他 incident、未自动化 guard 的原因、升级触发条件。
```

- [ ] **Step 2: Write `index.md`**

```markdown
# Dev Harness — Incidents Index

> 文档状态：当前规范（Dev Harness — Incidents 索引）
> 适用范围：跨域 incident ID 唯一性、状态汇总、guard 类型反查
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 最后校验：2026-05-09

## 编号规则

- 格式：`INC-YYYY-NNNN`，年 + 4 位序号。
- 递增不回收；废弃改 `status`，不复用 ID。
- 跨域全局唯一。

## status 取值

- `active`：当前生效。
- `superseded by INC-XXXX`：被另一条更准确的 incident 取代。
- `stale`：根因已不存在 / 不可复现，但保留作为历史记录。

## v1 索引

| ID | area | 标题 | rule | guard |
|---|---|---|---|---|
| INC-2026-0001 | test | runBlocking + Flow.first 死锁 | [test/rules.md#rule-runtest-mandatory](../test/rules.md#rule-runtest-mandatory) | contract-test |
| INC-2026-0002 | test | PlayerController.connect 主线程 runBlocking 死锁 | [test/rules.md#rule-no-runblocking-mainthread-in-instrumentation](../test/rules.md#rule-no-runblocking-mainthread-in-instrumentation) | contract-test |
| INC-2026-0003 | test | mergeExtDexDebugAndroidTest D8 OOM | [test/rules.md#rule-gradle-jvmargs-baseline](../test/rules.md#rule-gradle-jvmargs-baseline) | grep |
| INC-2026-0004 | test | DataStore multiple active 同文件 | [test/rules.md#rule-datastore-per-instance-isolation](../test/rules.md#rule-datastore-per-instance-isolation) | contract-test |
| INC-2026-0005 | test | feature module 缺 androidTest runner | [test/rules.md#rule-feature-androidtest-baseline](../test/rules.md#rule-feature-androidtest-baseline) | contract-test |
| INC-2026-0006 | ui | 顶部导航动画 250ms 偏离 RN 100ms | [ui/rules.md#rule-nav-animation-100ms](../ui/rules.md#rule-nav-animation-100ms) | contract-test |
| INC-2026-0007 | ui | 散落的 TopAppBarDefaults.topAppBarColors 手写 | [ui/rules.md#rule-no-raw-material3-topappbar](../ui/rules.md#rule-no-raw-material3-topappbar) | grep |
| INC-2026-0008 | ui | MainActivity 隐式补顶部 inset 白名单 | [ui/rules.md#rule-mainactivity-no-implicit-top-inset](../ui/rules.md#rule-mainactivity-no-implicit-top-inset) | grep + manual |
| INC-2026-0009 | plugin | QuickJS 跨线程访问 runtime 崩溃 | [plugin/rules.md#rule-quickjs-single-thread](../plugin/rules.md#rule-quickjs-single-thread) | manual |
| INC-2026-0010 | plugin | 集成测试默认依赖 kstore.vip 真网络 | [plugin/rules.md#rule-network-test-gated](../plugin/rules.md#rule-network-test-gated) | contract-test |
| INC-2026-0011 | player | 全屏播放器内容贴到状态栏后方 | [player/rules.md#rule-immersive-content-respects-statusbar](../player/rules.md#rule-immersive-content-respects-statusbar) | manual |
| INC-2026-0012 | player | 歌词自动跟随重复触发 / seek overlay 错位 | [player/rules.md#rule-lyric-follow-debounce](../player/rules.md#rule-lyric-follow-debounce) | contract-test |
```

- [ ] **Step 3: Commit**

```bash
git add docs/dev-harness/incidents/template.md docs/dev-harness/incidents/index.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): add incident template and v1 index of 12 seed entries

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.8: Seed `docs/dev-harness/test/incidents.md`

**Files:**
- Create: `docs/dev-harness/test/incidents.md`

- [ ] **Step 1: Write file with 5 incidents**

```markdown
# 测试 / 测试基建 Incidents

> 文档状态：当前规范（Dev Harness — Test Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [test/rules.md](./rules.md)
> 最后校验：2026-05-09

## INC-2026-0005 — feature 模块缺 androidTest runner 基线

- id: INC-2026-0005
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-feature-androidtest-baseline
- guard:
    type: contract-test
    target: app/src/test/java/com/hank/musicfree/harness/contracts/FeatureAndroidTestRunnerBaselineContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#2-pr-1--group-d-feature-androidtest-runner-基线

### 根因

`feature/{home,player-ui,search,settings}/build.gradle.kts` 声明 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`，但模块依赖未包含 `androidTestImplementation(libs.androidx.test.runner)`。AGP 仍创建 connected task；test APK 实例化 runner 时 `ClassNotFoundException`，instrumentation 进程 crash，全量 `connectedAndroidTest` 卡死在第一个 feature 模块。

### 复发条件

任何 feature `build.gradle.kts` 声明 runner 但未声明 runner 依赖。

### 教训

声明 runner 必带 runner 依赖；推荐同时带 `androidx.junit` 与 `espresso-core` 作为基础组合。

## INC-2026-0004 — DataStore multiple active 同文件

- id: INC-2026-0004
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-datastore-per-instance-isolation
- guard:
    type: contract-test
    target: plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginDataStoreIsolationContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#5-3-1-instrumentation-datastore-隔离

### 根因

`:plugin/src/androidTest/.../*Test.kt` 中手工 `PreferenceDataStoreFactory.create(...)` 复用固定 `*.preferences_pb` 文件名。AndroidJUnit4 每个 `@Test` 方法创建新 test class 实例，DataStore 对同一文件路径的 active scope 有单例约束；旧 scope 未关闭时新实例访问同一路径抛 `There are multiple DataStores active for the same file`，表现为 `installFromFile` / `installFromUrl` 返回 `null`。

### 复发条件

instrumentation test 中静态文件名 + 多 `@Test` 方法 + 未关闭 dataStoreScope。

### 教训

`produceFile = { File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb") }`；`@After` 调 `uninstallAllPlugins()` + `dataStoreScope.cancel()`。

## INC-2026-0003 — mergeExtDexDebugAndroidTest D8 OOM

- id: INC-2026-0003
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-gradle-jvmargs-baseline
- guard:
    type: grep
- signature: |
    awk -F= '/^org\.gradle\.jvmargs/ { line=$0 }
             END { if (line == "") exit 1
                   if (match(line, /-Xmx([0-9]+)m/, m) == 0) exit 1
                   if (m[1] < 4096) { print "gradle.properties -Xmx="m[1]"m < 4096m"; exit 1 }
                 }' gradle.properties
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#4-6-1-d8-oom-根因与修复

### 根因

`gradle.properties` 旧基线 `-Xmx2048m` 不足以支撑全量 androidTest dex 合并；`:plugin:mergeExtDexDebugAndroidTest` 在 D8 阶段 `OutOfMemoryError: Java heap space`，全量 `connectedAndroidTest` 失败。

### 复发条件

`gradle.properties` `-Xmx` 被回退到 < 4096m。

### 教训

仓库级基线必须写入 `gradle.properties`，不要靠开发者手动 `-Dorg.gradle.jvmargs`。

## INC-2026-0002 — PlayerController.connect 主线程 runBlocking 死锁

- id: INC-2026-0002
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-no-runblocking-mainthread-in-instrumentation
- guard:
    type: contract-test
    target: app/src/test/java/com/hank/musicfree/harness/contracts/PlayerControllerSetupContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#4-6-2-playercontrollertest-setup-死锁根因与修复

### 根因

`PlayerControllerTest.@Before` 在 `context.mainExecutor.execute { runBlocking { controller.connect() } }` 中阻塞主线程，`PlayerController.connect()` 内部又通过 `Dispatchers.Main.immediate` 等待 `MediaController.buildAsync()`；连接回调无法回到主线程，整个 instrumentation 永久挂起在 `Tests 0/N completed`。`runOnAppThread` helper 的无界 `latch.await()` 把失败放大为永久 hang。

### 复发条件

instrumentation test 中嵌套 `mainExecutor.execute { runBlocking { ... } }` 或使用无界 `latch.await()`。

### 教训

`controller.connect()` 在测试线程执行 + `withTimeout(5_000L)` 兜底；helper 改 bounded await + AtomicReference 异常回传。

## INC-2026-0001 — runBlocking + Flow.first { predicate } 死锁

- id: INC-2026-0001
- area: test
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/test/rules.md#rule-runtest-mandatory
- guard:
    type: contract-test
    target: app/src/test/java/com/hank/musicfree/harness/contracts/TestRunTestIdiomContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#3-pr-1--group-c-runtest-迁移

### 根因

`*ViewModelTest.kt` 同时使用 `runBlocking + UnconfinedTestDispatcher + viewModelScope.launch + Flow.first { predicate }`：predicate 在 hot dispatcher 上自旋；viewModelScope 协程未被 advance，Flow 无新发射；用例 hang。仅在 Robolectric/ByteBuddy 已被预热的 JVM 复现，因此初次运行可能"看起来通过"。

### 复发条件

ViewModel 单测里同时出现 `runBlocking` 与 `Flow.first { ... }` 自旋谓词。

### 教训

全部走 `runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`；`Flow.first { predicate }` 替换为 `advanceUntilIdle()` + `Flow.first()`。
```

- [ ] **Step 2: Commit**

```bash
git add docs/dev-harness/test/incidents.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): seed test incidents (INC-2026-0001..0005)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.9: Seed `docs/dev-harness/ui/incidents.md`

**Files:**
- Create: `docs/dev-harness/ui/incidents.md`

- [ ] **Step 1: Write file**

```markdown
# UI / Compose Screen Incidents

> 文档状态：当前规范（Dev Harness — UI Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [ui/rules.md](./rules.md)
> 最后校验：2026-05-09

## INC-2026-0008 — MainActivity 隐式补顶部 inset 白名单

- id: INC-2026-0008
- area: ui
- date: 2026-05-03
- status: active
- rule_ref: docs/dev-harness/ui/rules.md#rule-mainactivity-no-implicit-top-inset
- guard:
    type: grep + manual
- signature: |
    grep -nE 'WindowInsetsSides\.Top' app/src/main/java/com/hank/musicfree/MainActivity.kt
- fix_ref: docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md#mainactivity-设计

### 根因

旧 `MainActivity` 为多数页面统一补顶部 safe inset，再在白名单里排除 search / player 等沉浸式页面。这种隐式约定让新加页面默认拿到错的顶部 padding，且会与公共 harness 的状态栏占位叠加。

### 复发条件

`MainActivity.kt` 的 Scaffold 含 `WindowInsetsSides.Top`（直接或通过 union），或顶部 inset 在 Activity 层补偿。

### 教训

顶部 chrome 是 Screen / 公共 harness（`MusicFreeScreenScaffold` / `MusicFreeStatusBarChrome`）的责任；MainActivity 只承担 App 级 Scaffold + 横向/底部 safe inset。

### 备注

manual 部分用于审查 `MainActivity` 替代写法（例如自定义 modifier 注入顶部 padding）；grep 仅捕捉最直接形态。

## INC-2026-0007 — 散落的 TopAppBarDefaults.topAppBarColors 手写

- id: INC-2026-0007
- area: ui
- date: 2026-05-03
- status: active
- rule_ref: docs/dev-harness/ui/rules.md#rule-no-raw-material3-topappbar
- guard:
    type: grep
- signature: |
    grep -rEn 'TopAppBarDefaults\.topAppBarColors\(' \
      --include='*.kt' \
      --exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle . \
      | grep -v 'core/src/main/java/com/hank/musicfree/core/ui/MusicFreeScreenChrome.kt'
- fix_ref: docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md#公共-compose-api-设计

### 根因

多个 Screen 直接手写 `TopAppBar(colors = TopAppBarDefaults.topAppBarColors(...))`；与公共 `MusicFreeTopAppBar` 并存导致 AI 工具从混合示例学习，复制错误模式。

### 复发条件

`*.kt` 中除 `core/ui/MusicFreeScreenChrome.kt` 外出现 `TopAppBarDefaults.topAppBarColors(`。

### 教训

普通 AppBar 走 `MusicFreeScreenScaffold` 或 `MusicFreeTopAppBar`；自定义 chrome 走 `MusicFreeStatusBarChrome` 等价实现。

## INC-2026-0006 — 顶部导航动画 250ms 偏离 RN 100ms

- id: INC-2026-0006
- area: ui
- date: 2026-05-03
- status: active
- rule_ref: docs/dev-harness/ui/rules.md#rule-nav-animation-100ms
- guard:
    type: contract-test
    target: app/src/test/java/com/hank/musicfree/harness/contracts/UiNavAnimationDurationContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md#screen-切换动画设计

### 根因

旧 `AppNavHost` 使用 `tween(250)` 全局动画，与 RN 原版 `animationDuration: 100` 不一致。原 plan 文件中残留 250ms 写法，新人/AI 复制旧示例。

### 复发条件

`MusicFreeScreenTransitionDurationMillis` 常量值偏离 100；`NavHost` 中手写 `tween(<其他值>)`。

### 教训

集中入口 `MusicFreeNavTransitions.kt` 是唯一 transition builder；常量值锁定 100，由 contract test 守门。
```

- [ ] **Step 2: Commit**

```bash
git add docs/dev-harness/ui/incidents.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): seed ui incidents (INC-2026-0006..0008)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.10: Seed `docs/dev-harness/plugin/incidents.md`

**Files:**
- Create: `docs/dev-harness/plugin/incidents.md`

- [ ] **Step 1: Write file**

```markdown
# 插件系统 Incidents

> 文档状态：当前规范（Dev Harness — Plugin Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [plugin/rules.md](./rules.md)
> 最后校验：2026-05-09

## INC-2026-0010 — 集成测试默认依赖 kstore.vip 真网络

- id: INC-2026-0010
- area: plugin
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/plugin/rules.md#rule-network-test-gated
- guard:
    type: contract-test
    target: plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginNetworkTestGateContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md#5-3--pintegration-门控机制

### 根因

`:plugin/src/androidTest/.../PluginRuntimeIntegrationTest.kt` 旧版 4 个用例直连 `kstore.vip`，CI 默认通道因网络抖动 flaky；类级 `@Ignore` 同时阻塞 3 个本地用例。

### 复发条件

新 `:plugin/src/androidTest/` 中出现真域名（`kstore.vip` 等显式列表），但文件名不以 `NetworkIntegrationTest.kt` 结尾，或类内未 `Assume.assumeTrue` 引用 `pluginNetworkTests`。

### 教训

按 `PluginRuntimeLocalIntegrationTest`（本地）/ `PluginRuntimeNetworkIntegrationTest`（真网，`Assume` 门控）/ `PluginManagerHttpLifecycleTest`（MockWebServer）三类拆分；`-Pintegration` 启用真网通道。

## INC-2026-0009 — QuickJS 跨线程访问 runtime 崩溃

- id: INC-2026-0009
- area: plugin
- date: 2026-04-19
- status: active
- rule_ref: docs/dev-harness/plugin/rules.md#rule-quickjs-single-thread
- guard:
    type: manual
- fix_ref: docs/superpowers/specs/2026-04-19-quickjs-threading-fix-design.md

### 根因

QuickJS `Context` / `JsBridge` 实例不是线程安全的；多线程调用 `Context.evaluate(...)` 触发 native crash 或不确定结果。

### 复发条件

`:plugin/src/main/` 在非 owning thread / 非 `quickJsDispatcher` 上访问 `Context` / `JsBridge`。静态扫成本高（需要跨函数追踪 dispatcher 切换），暂列 manual。

### 教训

`withContext(quickJsDispatcher)` 切回单线程；JsBridge 内部用 `MutableSharedFlow` 路由跨线程请求。

### 备注

升级触发条件 = 再现一次 QuickJS 跨线程崩溃事件即升级为 contract-test 或 runtime invariant；harness-curator-skill 在巡检时显式列 manual incidents 提醒人工复核。
```

- [ ] **Step 2: Commit**

```bash
git add docs/dev-harness/plugin/incidents.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): seed plugin incidents (INC-2026-0009, INC-2026-0010)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.11: Seed `docs/dev-harness/player/incidents.md`

**Files:**
- Create: `docs/dev-harness/player/incidents.md`

- [ ] **Step 1: Write file**

```markdown
# 播放器 / Media3 Incidents

> 文档状态：当前规范（Dev Harness — Player Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [player/rules.md](./rules.md)
> 最后校验：2026-05-09

## INC-2026-0012 — 歌词自动跟随重复触发 / seek overlay 错位

- id: INC-2026-0012
- area: player
- date: 2026-05-05
- status: active
- rule_ref: docs/dev-harness/player/rules.md#rule-lyric-follow-debounce
- guard:
    type: contract-test
    target: feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/harness/contracts/LyricFollowDebounceContractTest.kt
- fix_ref: docs/superpowers/specs/2026-05-05-player-lyrics-interaction-fix-design.md

### 根因

歌词自动跟随与手动滑动状态没有显式互斥，自动跟随会在手动滑动期间被反复触发；seek overlay 进入条件分散，导致 overlay 与歌词卡片错位。

### 复发条件

`feature/player-ui` 修改歌词跟随逻辑后，`MiniPlayerContentTest`、`LyricFollow*Test`、`LyricSeekOverlay*Test` 任一断言被破坏。

### 教训

互斥状态由统一 helper 决定；任何相关 PR MUST 跑全量歌词单测。

## INC-2026-0011 — 全屏播放器内容贴到状态栏后方

- id: INC-2026-0011
- area: player
- date: 2026-05-04
- status: active
- rule_ref: docs/dev-harness/player/rules.md#rule-immersive-content-respects-statusbar
- guard:
    type: manual
- fix_ref: docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md

### 根因

`PlayerScreen` 启用 edge-to-edge 后内容层未通过 `WindowInsets.statusBars` 显式避让，标题与控件被状态栏遮挡。

### 复发条件

`PlayerScreen` 内容层去掉 `WindowInsets.statusBars` padding 或 helper。

### 教训

背景层可绘到状态栏后方；内容层（标题、按钮、歌词卡片）必须避让。

### 备注

manual：视觉断言难自动化。harness-curator-skill 巡检时提醒人工复核截图与 RN 对齐。
```

- [ ] **Step 2: Commit**

```bash
git add docs/dev-harness/player/incidents.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): seed player incidents (INC-2026-0011, INC-2026-0012)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.12: Update `AGENTS.md`

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Insert "Dev Harness 强制入口" + "项目记忆与守门约束" sections**

Replace the existing block (lines ~11-23 of AGENTS.md, the `## 文档入口（先读）` section) with the augmented version below. Use Edit tool with `old_string`:

```
## 文档入口（先读）

在动手实现前，按以下顺序读取文档：

1. `docs/DOCS_STATUS.md`（文档状态索引：当前规范 / 当前参考 / 历史记录）
2. `AGENTS.md`（当前仓库工作约束）
3. 与任务相关的“当前规范”文档

强制规则：

- `docs/superpowers/plans/*.md` 默认视为历史执行快照，不可直接当作当前执行指令。
- 文档之间的引用必须使用相对路径，禁止使用 `/Users/...` 绝对路径。
- 跨仓库引用也必须使用相对路径（例如 `../MusicFree/...`）。
```

`new_string`:

```
## 文档入口（先读）

在动手实现前，按以下顺序读取文档：

1. `docs/DOCS_STATUS.md`（文档状态索引：当前规范 / 当前参考 / 历史记录）
2. `AGENTS.md`（当前仓库工作约束）
3. `docs/dev-harness/INDEX.md`（开发守门总入口；按域跳到对应 rules.md / incidents.md）
4. 与任务相关的“当前规范”文档

强制规则：

- `docs/superpowers/plans/*.md` 默认视为历史执行快照，不可直接当作当前执行指令。
- 文档之间的引用必须使用相对路径，禁止使用 `/Users/...` 绝对路径。
- 跨仓库引用也必须使用相对路径（例如 `../MusicFree/...`）。

## Dev Harness 强制入口

任何涉及下述域的改动，动手前必须读取对应 rules.md：

- UI / Compose Screen：`docs/dev-harness/ui/rules.md`
- 插件系统：`docs/dev-harness/plugin/rules.md`
- 播放器 / Media3：`docs/dev-harness/player/rules.md`
- 测试代码 / 测试基建：`docs/dev-harness/test/rules.md`

每条 rule 都关联一条或多条 incident（`docs/dev-harness/incidents/index.md`）和 / 或一条 contract test。
违反 rules.md 中标记 MUST / MUST NOT 的条款将在 CI `dev-harness-gate` 作业被拦下。

历史决策快照在 `docs/superpowers/specs/` 与 `plans/`（仅参考），不是当前规则源。

## 项目记忆与守门约束

- 强约束：`docs/dev-harness/<area>/rules.md`
- 历史踩坑：`docs/dev-harness/incidents/index.md`（按 ID 反查到 area + rule + guard）
- AI 工作流：见 `.agents/skills/<area>-skill/`，软链到 `.claude/skills/`、`.codex/skills/`
- 历史决策快照：`docs/superpowers/specs/` 与 `plans/`（仅参考，不是当前规则源）
- 个人会话偏好（Claude Code only）：`~/.claude/projects/.../memory/MEMORY.md`，不进仓库
```

- [ ] **Step 2: Repoint UI Harness Rules link**

Edit AGENTS.md, replacing the old subsection:

`old_string`:

```
### UI Harness Rules

新增或修改 Compose Screen 前，必须读取并遵守 [screen-chrome-rules](docs/ui-harness/screen-chrome-rules.md)。

- Screen 切换动画、普通 AppBar、沉浸式状态栏处理必须走公共 harness 入口。
- 普通 AppBar 页面不得直接手写分散的 `TopAppBar` + `TopAppBarDefaults.topAppBarColors(...)`。
- 特殊 Chrome 页面必须在规则文档中登记，并自行负责状态栏背景和顶部 inset。
- `docs/superpowers/plans/*.md` 中旧动画或 AppBar 写法不作为当前 UI Harness 规范来源。
```

`new_string`:

```
### UI Harness Rules

新增或修改 Compose Screen 前，必须读取并遵守 [docs/dev-harness/ui/rules.md](docs/dev-harness/ui/rules.md)。

- Screen 切换动画、普通 AppBar、沉浸式状态栏处理必须走公共 harness 入口。
- 普通 AppBar 页面不得直接手写分散的 `TopAppBar` + `TopAppBarDefaults.topAppBarColors(...)`。
- 特殊 Chrome 页面必须在规则文档中登记，并自行负责状态栏背景和顶部 inset。
- `docs/superpowers/plans/*.md` 中旧动画或 AppBar 写法不作为当前 UI Harness 规范来源。
- 旧入口 `docs/ui-harness/screen-chrome-rules.md` 已迁移；保留只读 redirect stub 以兼容历史链接。
```

- [ ] **Step 3: Verify diff**

```bash
git diff AGENTS.md
```

Expected: 2 insertions (new sections) + 1 link rewrite, no other content drift.

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md
git commit -m "$(cat <<'EOF'
docs(agents): add Dev Harness 强制入口 and project-memory guardrails

Add the "Dev Harness 强制入口" and "项目记忆与守门约束" sections
referencing docs/dev-harness/, register the dev-harness INDEX in the
"文档入口（先读）" list, and repoint the existing UI Harness Rules link
to the migrated rules.md path.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.13: Update `docs/DOCS_STATUS.md`

**Files:**
- Modify: `docs/DOCS_STATUS.md`

- [ ] **Step 1: Demote `screen-chrome-rules.md` row**

Edit, `old_string`:

```
| [docs/ui-harness/screen-chrome-rules.md](./ui-harness/screen-chrome-rules.md) | 当前规范（UI Harness Rules） | 是 | Screen 切换动画、普通 AppBar 与沉浸式状态栏强制规则 |
```

`new_string`:

```
| [docs/ui-harness/screen-chrome-rules.md](./ui-harness/screen-chrome-rules.md) | 已迁移 | 否 | 已并入 [docs/dev-harness/ui/rules.md](./dev-harness/ui/rules.md)；本路径仅保留 redirect stub |
```

- [ ] **Step 2: Add new rows for dev-harness**

Find the row for `docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md` and insert above it (or insert at the top of the spec list — placement should keep the table grouped sensibly):

```
| [docs/dev-harness/INDEX.md](./dev-harness/INDEX.md) | 当前规范（Dev Harness 总入口） | 是 | UI / 插件 / 播放器 / 测试 四域规则、错误库、AI skills 关联入口 |
| [docs/dev-harness/ui/rules.md](./dev-harness/ui/rules.md) | 当前规范（Dev Harness — UI） | 是 | Screen 切换动画、普通 AppBar、沉浸式状态栏、UI 设计原则 |
| [docs/dev-harness/plugin/rules.md](./dev-harness/plugin/rules.md) | 当前规范（Dev Harness — Plugin） | 是 | QuickJS 线程模型、网络通道门控、PluginManager 编排 |
| [docs/dev-harness/player/rules.md](./dev-harness/player/rules.md) | 当前规范（Dev Harness — Player） | 是 | PlayerController 连接、沉浸式 chrome、歌词跟随防抖 |
| [docs/dev-harness/test/rules.md](./dev-harness/test/rules.md) | 当前规范（Dev Harness — Test） | 是 | runTest 范式、instrumentation 主线程模型、DataStore 隔离、runner 基线、JVM 内存基线 |
| [docs/dev-harness/incidents/index.md](./dev-harness/incidents/index.md) | 当前规范（Dev Harness — Incidents 索引） | 是 | INC-YYYY-NNNN 全仓索引；guard 类型反查 |
| [docs/superpowers/specs/2026-05-09-dev-harness-foundation-design.md](./superpowers/specs/2026-05-09-dev-harness-foundation-design.md) | 当前规范（Dev Harness 基础设施专项） | 是（作为实现计划输入） | 总入口 + 错误库 + 5 skills + 测试守门 + 3 PR 编排设计 |
```

- [ ] **Step 3: Update 最后校验日期 line**

Edit, `old_string`:

```
> 最后校验日期：2026-05-05
```

`new_string`:

```
> 最后校验日期：2026-05-09
```

Also update the same date appearing in the frontmatter block at the top.

- [ ] **Step 4: Commit**

```bash
git add docs/DOCS_STATUS.md
git commit -m "$(cat <<'EOF'
docs(status): register dev-harness docs and demote screen-chrome-rules

Add rows for INDEX.md, four area rules.md files, the incidents index,
and the foundation design spec; mark the original
docs/ui-harness/screen-chrome-rules.md as 已迁移 with a pointer to the
new rules.md location; bump 最后校验 to 2026-05-09.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.14: Add `scripts/dev-harness/grep-check.py`

**Files:**
- Create: `scripts/dev-harness/grep-check.py`

- [ ] **Step 1: Write the script**

```python
#!/usr/bin/env python3
"""Run dev-harness grep guards.

Walks docs/dev-harness/<area>/incidents.md, extracts H2 blocks with
status=active and guard.type containing 'grep', runs each block's
signature shell command, and fails (exit code 1) if any command
returns success with non-empty stdout.

The contract: a grep signature succeeding with output means a
recurrence has been detected and the build must fail.
"""
from __future__ import annotations

import pathlib
import re
import subprocess
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
AREAS = ("ui", "plugin", "player", "test")


def parse_incidents(path: pathlib.Path) -> list[dict[str, str]]:
    text = path.read_text(encoding="utf-8")
    blocks = re.split(r"\n(?=## INC-)", text)
    incidents = []
    for block in blocks:
        if not block.startswith("## INC-"):
            continue
        id_match = re.search(r"^## (INC-\d{4}-\d{4})", block, re.MULTILINE)
        status_match = re.search(r"^- status:\s*(\S+)", block, re.MULTILINE)
        guard_type_match = re.search(r"^- guard:\s*\n\s+type:\s*([^\n]+)", block, re.MULTILINE)
        signature_match = re.search(r"^- signature:\s*\|\s*\n((?:[ \t]+.*\n?)+)", block, re.MULTILINE)
        if not (id_match and status_match and guard_type_match):
            continue
        incidents.append(
            {
                "id": id_match.group(1),
                "status": status_match.group(1),
                "guard_type": guard_type_match.group(1).strip(),
                "signature": _dedent_block(signature_match.group(1)) if signature_match else "",
            }
        )
    return incidents


def _dedent_block(block: str) -> str:
    lines = [line.rstrip() for line in block.splitlines() if line.strip()]
    if not lines:
        return ""
    indent = min(len(line) - len(line.lstrip(" ")) for line in lines)
    return "\n".join(line[indent:] for line in lines).strip()


def main() -> int:
    failures: list[tuple[str, str, str]] = []
    for area in AREAS:
        path = REPO_ROOT / "docs" / "dev-harness" / area / "incidents.md"
        if not path.exists():
            continue
        for inc in parse_incidents(path):
            if inc["status"] != "active":
                continue
            if "grep" not in inc["guard_type"]:
                continue
            cmd = inc["signature"]
            if not cmd:
                continue
            result = subprocess.run(
                cmd,
                shell=True,
                capture_output=True,
                text=True,
                cwd=REPO_ROOT,
                check=False,
            )
            if result.returncode == 0 and result.stdout.strip():
                failures.append((inc["id"], cmd, result.stdout))
    if failures:
        for inc_id, cmd, output in failures:
            print(f"DEV-HARNESS GREP GUARD FAILED: {inc_id}")
            print(f"  command: {cmd}")
            print("  matches:")
            for line in output.splitlines()[:20]:
                print(f"    {line}")
            print()
        return 1
    print("All dev-harness grep guards passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Make executable and verify against current repo**

Run:
```bash
chmod +x scripts/dev-harness/grep-check.py
python3 scripts/dev-harness/grep-check.py
```

Expected output: `All dev-harness grep guards passed.` (exit 0).

If any guard fails, the corresponding INC needs investigation — see incidents.md for that area. For PR 1 baseline, all three grep-type guards (INC-0003 / 0007 / 0008) must already pass on `main`.

### Task 1.15: Add `scripts/dev-harness/symlinks-check.sh`

**Files:**
- Create: `scripts/dev-harness/symlinks-check.sh`

- [ ] **Step 1: Write the script**

```bash
#!/usr/bin/env bash
# Validate dev-harness skill symlinks.
#
# Verifies:
#   .claude/skills/<name>     -> .agents/skills/<name>
#   .codex/skills/<name>      -> .agents/skills/<name>
#   .agents/skills/<name>/references/rules.md     -> docs/dev-harness/<area>/rules.md
#   .agents/skills/<name>/references/incidents.md -> docs/dev-harness/<area>/incidents.md
#
# In --allow-empty mode (used during PR 1 before skills exist), missing
# .agents/skills/<area>-skill directories are tolerated; existing
# symlinks are still checked.

set -euo pipefail

ALLOW_EMPTY=0
if [[ "${1:-}" == "--allow-empty" ]]; then
  ALLOW_EMPTY=1
fi

cd "$(git rev-parse --show-toplevel)"

SKILLS=(
  ui-harness-skill:ui
  plugin-system-skill:plugin
  media-player-skill:player
  test-stability-skill:test
  harness-curator-skill:_curator
)

errors=0

check_link() {
  local link="$1" expected="$2"
  if [[ ! -L "$link" ]]; then
    echo "ERROR: $link is not a symlink (expected -> $expected)" >&2
    return 1
  fi
  local target
  target="$(readlink "$link")"
  local resolved
  resolved="$(cd "$(dirname "$link")" && cd "$(dirname "$target")" && pwd)/$(basename "$target")"
  local expected_resolved
  expected_resolved="$(cd "$(dirname "$expected")" && pwd)/$(basename "$expected")"
  if [[ "$resolved" != "$expected_resolved" ]]; then
    echo "ERROR: $link -> $resolved, expected -> $expected_resolved" >&2
    return 1
  fi
}

for entry in "${SKILLS[@]}"; do
  name="${entry%%:*}"
  area="${entry##*:}"
  agents_dir=".agents/skills/$name"

  if [[ ! -d "$agents_dir" ]]; then
    if [[ "$ALLOW_EMPTY" -eq 1 ]]; then
      continue
    fi
    echo "ERROR: $agents_dir is missing" >&2
    errors=$((errors + 1))
    continue
  fi

  for tool_root in .claude/skills .codex/skills; do
    link="$tool_root/$name"
    expected="$agents_dir"
    check_link "$link" "$expected" || errors=$((errors + 1))
  done

  if [[ "$area" != "_curator" ]]; then
    for ref in rules incidents; do
      link="$agents_dir/references/$ref.md"
      expected="docs/dev-harness/$area/$ref.md"
      if [[ -e "$link" || -L "$link" ]]; then
        check_link "$link" "$expected" || errors=$((errors + 1))
      else
        if [[ "$ALLOW_EMPTY" -eq 0 ]]; then
          echo "ERROR: $link is missing" >&2
          errors=$((errors + 1))
        fi
      fi
    done
  fi
done

if [[ "$errors" -gt 0 ]]; then
  echo "Dev-harness symlink check failed with $errors error(s)." >&2
  exit 1
fi

echo "All dev-harness symlinks valid."
```

- [ ] **Step 2: Make executable and run**

```bash
chmod +x scripts/dev-harness/symlinks-check.sh
bash scripts/dev-harness/symlinks-check.sh --allow-empty
```

Expected output: `All dev-harness symlinks valid.` (exit 0). The strict mode (no `--allow-empty`) will pass after PR 2.

### Task 1.16: Add `scripts/dev-harness/check.sh`

**Files:**
- Create: `scripts/dev-harness/check.sh`

- [ ] **Step 1: Write the driver**

```bash
#!/usr/bin/env bash
# Local one-shot driver: symlinks check + grep guards + JVM contract tests.
#
# In PR 1 the contract-test step is a no-op until contract tests land.
# Pass --skip-contract-tests to bypass the gradle invocation while
# iterating on docs/scripts.

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

SKIP_CONTRACT=0
ALLOW_EMPTY_SYMLINKS=0
for arg in "$@"; do
  case "$arg" in
    --skip-contract-tests) SKIP_CONTRACT=1 ;;
    --allow-empty-symlinks) ALLOW_EMPTY_SYMLINKS=1 ;;
    *) echo "Unknown arg: $arg" >&2; exit 2 ;;
  esac
done

echo "==> Symlinks"
if [[ "$ALLOW_EMPTY_SYMLINKS" -eq 1 ]]; then
  bash scripts/dev-harness/symlinks-check.sh --allow-empty
else
  bash scripts/dev-harness/symlinks-check.sh
fi

echo "==> Grep guards"
python3 scripts/dev-harness/grep-check.py

if [[ "$SKIP_CONTRACT" -eq 1 ]]; then
  echo "==> Skipping contract tests (--skip-contract-tests)"
  exit 0
fi

echo "==> Contract tests (JVM)"
./gradlew \
  :app:testDebugUnitTest :core:testDebugUnitTest :data:testDebugUnitTest \
  :downloader:testDebugUnitTest :plugin:testDebugUnitTest :player:testDebugUnitTest \
  :feature:home:testDebugUnitTest :feature:player-ui:testDebugUnitTest \
  :feature:search:testDebugUnitTest :feature:settings:testDebugUnitTest \
  --tests '*harness.contracts.*' --no-daemon
```

- [ ] **Step 2: Make executable and run minimal mode**

```bash
chmod +x scripts/dev-harness/check.sh
bash scripts/dev-harness/check.sh --allow-empty-symlinks --skip-contract-tests
```

Expected: `==> Symlinks` PASS + `==> Grep guards` PASS, exits 0.

- [ ] **Step 3: Commit scripts (1.14 + 1.15 + 1.16)**

```bash
git add scripts/dev-harness/grep-check.py scripts/dev-harness/symlinks-check.sh scripts/dev-harness/check.sh
git commit -m "$(cat <<'EOF'
chore(dev-harness): add grep-check, symlinks-check, and local check.sh

grep-check.py walks docs/dev-harness/<area>/incidents.md, extracts
status=active grep-type guards from H2 blocks, and fails if any
signature shell command produces matches in HEAD. symlinks-check.sh
validates skill symlinks (.claude/.codex -> .agents) and reference
links (rules/incidents.md -> docs/dev-harness/<area>/), with an
--allow-empty mode for PR 1 staging. check.sh chains both plus the
*harness.contracts.* gradle filter for local one-shot validation.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.17: Add `.github/workflows/dev-harness-gate.yml`

**Files:**
- Create: `.github/workflows/dev-harness-gate.yml`

- [ ] **Step 1: Write the workflow (PR 1 staged form)**

```yaml
name: Dev Harness Gate
on:
  push:
    branches: [ main ]
  pull_request:
    branches:
      - main
      - 'feat/**'
      - 'fix/**'

jobs:
  gate:
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
      - name: Symlinks check (allow-empty during PR 1)
        run: bash scripts/dev-harness/symlinks-check.sh --allow-empty
      - name: Grep guards
        run: python3 scripts/dev-harness/grep-check.py
      - name: Contract tests (JVM) — PR 1 placeholder
        run: |
          echo "Contract-test step is staged; activates in PR 3 once contract tests land."
          echo "See docs/superpowers/plans/2026-05-09-dev-harness-foundation.md Phase 3."
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/dev-harness-gate.yml
git commit -m "$(cat <<'EOF'
ci(dev-harness): add dev-harness-gate workflow with grep + symlink steps

Stage the contract-test step as a printout pending PR 3 activation;
symlinks-check runs with --allow-empty until PR 2 lands the skill
symlinks. Grep guards run live against incidents.md signatures.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.18: PR 1 local validation

**Files:** none (read-only checks)

- [ ] **Step 1: Run local check**

```bash
bash scripts/dev-harness/check.sh --allow-empty-symlinks --skip-contract-tests
```

Expected: both steps PASS.

- [ ] **Step 2: Smoke-build to confirm baseline**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Smoke-run existing baseline tests**

```bash
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verify**

Open in editor and skim:

- `AGENTS.md` shows the new sections.
- `docs/dev-harness/INDEX.md` lists 4 areas.
- `docs/dev-harness/incidents/index.md` lists 12 INC-XXXX.
- `docs/dev-harness/<area>/incidents.md` files exist with H2 blocks.

If anything is off, fix in additional commits before pushing.

### Task 1.19: Push branch and open PR 1

- [ ] **Step 1: Push**

```bash
git push -u origin feat/dev-harness-foundation
```

- [ ] **Step 2: Open PR via gh**

```bash
gh pr create --title "Dev Harness foundation: docs + scripts + CI gate skeleton" --body "$(cat <<'EOF'
## Summary

- Adds `docs/dev-harness/` total entry with INDEX, four area rules.md, four incidents.md, and the cross-area incidents/index.
- Migrates `docs/ui-harness/screen-chrome-rules.md` into `docs/dev-harness/ui/rules.md` with a one-paragraph redirect stub left in place.
- Augments `AGENTS.md` with `Dev Harness 强制入口` and `项目记忆与守门约束` sections; registers the new docs in `docs/DOCS_STATUS.md`.
- Adds `scripts/dev-harness/{grep-check.py, symlinks-check.sh, check.sh}` and a staged `.github/workflows/dev-harness-gate.yml` (grep + symlinks live; contract tests staged for PR 3).
- Seeds 12 v1 incidents (INC-2026-0001..0012) with structured fields and links to existing fix-spec evidence.

## Test plan

- [ ] `bash scripts/dev-harness/check.sh --allow-empty-symlinks --skip-contract-tests` passes locally
- [ ] `./gradlew :app:assembleDebug --no-daemon` passes
- [ ] `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest --no-daemon` passes
- [ ] CI `Dev Harness Gate / gate` job is green on this PR

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Wait for CI green. After merge, return to main and continue Phase 2.

---

## Phase 2 — PR 2: Skills

Branch: `feat/dev-harness-skills`. Worktree path: `.worktrees/feat-dev-harness-skills`.

### Task 2.1: Create the worktree

- [ ] **Step 1: Create from updated main**

Run from main checkout (after PR 1 merged):
```bash
git fetch origin
git checkout main
git pull --ff-only origin main
git worktree add .worktrees/feat-dev-harness-skills -b feat/dev-harness-skills main
cd .worktrees/feat-dev-harness-skills
```

Expected: HEAD now contains PR 1's `docs/dev-harness/` tree.

### Task 2.2: Scaffold five skill directories

- [ ] **Step 1: Create directories**

```bash
mkdir -p .agents/skills/{ui-harness-skill,plugin-system-skill,media-player-skill,test-stability-skill,harness-curator-skill}/references
mkdir -p .claude/skills .codex/skills
```

- [ ] **Step 2: Commit empty scaffolding**

`.gitkeep`-style: no commit yet; the next tasks fill these dirs.

### Task 2.3: Write `ui-harness-skill/SKILL.md`

**Files:**
- Create: `.agents/skills/ui-harness-skill/SKILL.md`

- [ ] **Step 1: Write file**

```markdown
---
name: ui-harness
description: >
  Use this skill whenever the task touches a Compose Screen, AppBar,
  navigation animation, status bar handling, MusicFreeScreenScaffold,
  MusicFreeTopAppBar, MusicFreeStatusBarChrome, FidelityAnchors, rpx
  sizing, immersive chrome, or any UI flow under feature/* / app/. Trigger
  phrases: "新建 Compose Screen", "改顶部栏", "状态栏", "切换动画",
  "Scaffold", "TopAppBar", "rpx", "FidelityAnchors", "沉浸式".
---

# UI Harness Skill

Cross-tool guidance for Compose Screen / Chrome work in MusicFreeAndroid.
Pairs with the UI rules + UI incidents to keep AI changes consistent
with the public Compose harness.

## 必读 gate

调起本 skill 前，必须 Read：

- [`docs/dev-harness/ui/rules.md`](references/rules.md)（软链 → 正本）
- [`docs/dev-harness/ui/incidents.md`](references/incidents.md)（软链 → 正本）

## Workflow checklist

1. 读 rules.md 与 incidents.md，识别本次改动落在哪条 rule（screen 切换动画 / 普通 AppBar / MainActivity 责任 / 沉浸式特殊 chrome / 设计原则）。
2. 普通页面默认走 `MusicFreeScreenScaffold(title, onBack) { ... }`；自定义顶部走 `MusicFreeTopAppBar` + `MusicFreeStatusBarChrome`。
3. 涉及导航动画时改集中入口 `app/.../navigation/MusicFreeNavTransitions.kt`；MUST NOT 在 Screen 内部用 `AnimatedContent` / `AnimatedVisibility` 伪装页面切换。
4. 涉及 `MainActivity` 顶部 inset 时只能减不能加；新增页面顶部 chrome 是 Screen 的责任。
5. 跑：`./gradlew :app:testDebugUnitTest --tests '*MusicFreeNavTransitionsTest' --no-daemon` 与 `*harness.contracts.*`（PR 3 起生效）。
6. 提交前检查 grep 守门：`python3 scripts/dev-harness/grep-check.py` 应全绿。
7. 若新增/修改 `FidelityAnchors`，同步更新 `docs/home-fidelity/`（见 references/fidelity-anchors.md）。

## 反例（rules.md 已禁止）

- 直接手写 `TopAppBar(colors = TopAppBarDefaults.topAppBarColors(...))`。
- `MainActivity` 在 Scaffold contentWindowInsets 中加 `WindowInsetsSides.Top`。
- 在 Screen 内用 `AnimatedContent` 伪装页面切换。
- 修改导航动画时长偏离 100ms。

## References

- [screen-scaffold-walkthrough.md](references/screen-scaffold-walkthrough.md)
- [navigation-animation.md](references/navigation-animation.md)
- [status-bar-immersive.md](references/status-bar-immersive.md)
- [fidelity-anchors.md](references/fidelity-anchors.md)
```

- [ ] **Step 2: Defer commit until 2.4–2.7 references are written**

### Task 2.4: Write `ui-harness-skill/references/*.md`

**Files:**
- Create: `.agents/skills/ui-harness-skill/references/screen-scaffold-walkthrough.md`
- Create: `.agents/skills/ui-harness-skill/references/navigation-animation.md`
- Create: `.agents/skills/ui-harness-skill/references/status-bar-immersive.md`
- Create: `.agents/skills/ui-harness-skill/references/fidelity-anchors.md`

(Symlinks to rules.md / incidents.md are added in Task 2.13.)

- [ ] **Step 1: Write `screen-scaffold-walkthrough.md`**

```markdown
# Screen Scaffold Walkthrough

普通页面默认结构：

```kotlin
@Composable
fun MyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MusicFreeScreenScaffold(
        title = "页面标题",
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MusicFreeTheme.colors.pageBackground),
        ) {
            // 内容
        }
    }
}
```

要点：

- `innerPadding` 已包含状态栏与 AppBar 高度；不要额外加顶部 padding。
- 背景色用 `MusicFreeTheme.colors.pageBackground`；AppBar 色由 scaffold 内部处理。
- 如需自定义 actions，使用 `actions: @Composable RowScope.() -> Unit` 参数。

特殊 chrome（`HomeRoute`、`SearchRoute`、`PlayerRoute`、`LocalRoute`）见 rules.md 对应小节，必须在 rules 文件登记原因 + 状态栏策略。
```

- [ ] **Step 2: Write `navigation-animation.md`**

```markdown
# Navigation Animation

集中入口：`app/src/main/java/com/hank/musicfree/navigation/MusicFreeNavTransitions.kt`。

约束：

- 常量 `MusicFreeScreenTransitionDurationMillis = 100`，不允许在 PR 内改写。
- `AppNavHost` 的 `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition` 全部引用 `musicFree*Transition()` helper。
- 特殊页面若需差异化动画，在 destination 注册时显式 override，并在 rules.md 的"特殊 Chrome 页面"段登记原因。

contract test 守门：`UiNavAnimationDurationContractTest`（PR 3 起生效，复用 `MusicFreeNavTransitionsTest` 同断言）。
```

- [ ] **Step 3: Write `status-bar-immersive.md`**

```markdown
# Status Bar Immersive

普通 AppBar 页面的状态栏：

- Activity 已 `enableEdgeToEdge()`，状态栏透明。
- `MusicFreeTopAppBar` 内部用 `MusicFreeStatusBarChrome` 把 `colors.appBar` 铺到状态栏后方。
- AppBar 内容高度 `rpx(88)`；状态栏高度由 `WindowInsets.statusBars` 提供。

特殊 chrome 自处理：

- `HomeScreen`：自定义 `HomeNavBar`，状态栏区域保持首页背景。
- `SearchScreen`：搜索栏自处理 `appBar` 色延伸。
- `PlayerScreen`：背景层可绘到状态栏后；内容层 MUST 用 `WindowInsets.statusBars` 显式避让（INC-2026-0011）。
- `LocalScreen`：无 AppBar，必须显式加顶部 status bar spacer。
```

- [ ] **Step 4: Write `fidelity-anchors.md`**

```markdown
# Fidelity Anchors

`core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt` 是首页 UI fidelity 与 contract 测试断言的 anchor 表。

约束：

- 修改 anchor 名称 / 删除 anchor 必须同步：`PluginSearchPlayAnchorContractTest`、`HomeFidelityHomeStructureTest`、`HomeDrawerBehaviorTest`、`docs/home-fidelity/homepage/README.md`。
- 新增 anchor 时优先复用既有 namespace（`Home.*` / `Player.*` / `Settings.*` / `Dialog.*`）。

首页 fidelity 取证规范在 `docs/home-fidelity/`，本 skill 仅引用，不复制规则。
```

- [ ] **Step 5: Commit ui-harness-skill (SKILL.md + references)**

```bash
git add .agents/skills/ui-harness-skill/
git commit -m "$(cat <<'EOF'
docs(skills): add ui-harness-skill with workflow, scaffold/animation refs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.5: Write `plugin-system-skill/SKILL.md` and references

**Files:**
- Create: `.agents/skills/plugin-system-skill/SKILL.md`
- Create: `.agents/skills/plugin-system-skill/references/{plugin-api-surface,quickjs-threading,mock-webserver-recipe,network-test-gate}.md`

- [ ] **Step 1: Write `SKILL.md`**

```markdown
---
name: plugin-system
description: >
  Use this skill for any change in :plugin (QuickJS engine, JsBridge,
  PluginApi 14 capabilities, PluginManager install/update orchestration,
  require shim, MockWebServer integration tests, network gate). Trigger
  phrases: "插件", "QuickJS", "JsBridge", "PluginApi", "require shim",
  "installFromUrl", "updatePlugin", "-Pintegration", "MockWebServer",
  "PluginManager".
---

# Plugin System Skill

Cross-tool guidance for the plugin engine, manager, and integration tests.

## 必读 gate

调起本 skill 前，必须 Read：

- [`docs/dev-harness/plugin/rules.md`](references/rules.md)
- [`docs/dev-harness/plugin/incidents.md`](references/incidents.md)
- 当涉及 instrumentation 测试隔离时还需 Read [`docs/dev-harness/test/rules.md`](../test-stability-skill/references/rules.md)

## Workflow checklist

1. 读 rules.md / incidents.md，确认改动域：QuickJS runtime / PluginManager / JsBridge / require shim / PluginApi 能力。
2. 任何涉及 QuickJS Context / JsBridge 的访问都必须在专用单线程 dispatcher 上（INC-2026-0009 / rule-quickjs-single-thread）。
3. 新增 androidTest：网络依赖类必须命名 `*NetworkIntegrationTest.kt` 且 `@Before` 调 `Assume.assumeTrue("...", arg == "true")`，参数从 `pluginNetworkTests` 读（rule-network-test-gated / INC-2026-0010）。
4. instrumentation 中手工 `PreferenceDataStoreFactory.create(...)` 必须 `produceFile = { File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb") }`（rule-datastore-per-instance-isolation / INC-2026-0004）。
5. 验证：默认 `./gradlew :plugin:connectedAndroidTest` 跳过真网用例；`-Pintegration` 启用全部用例。
6. 单测 `./gradlew :plugin:testDebugUnitTest --no-daemon`。

## References

- [plugin-api-surface.md](references/plugin-api-surface.md)
- [quickjs-threading.md](references/quickjs-threading.md)
- [mock-webserver-recipe.md](references/mock-webserver-recipe.md)
- [network-test-gate.md](references/network-test-gate.md)
```

- [ ] **Step 2: Write `references/plugin-api-surface.md`**

```markdown
# PluginApi Surface

当前 14 个核心能力（按 `../MusicFree/src/types/plugin.d.ts` 与 `:plugin/src/main/.../PluginApi.kt` 校对）：

1. `search`
2. `getMediaSource`
3. `getLyric`
4. `getMusicSheetInfo`
5. `getRecommendSheetsByTag`
6. `getMusicComments`
7. `getAlbumInfo`
8. `getArtistWorks`
9. `getTopLists`
10. `getTopListDetail`
11. `importMusicSheet`
12. `getMusicInfo`
13. `userVariables`（读写存储）
14. `subscription`（订阅源能力）

实际能力以 `:plugin/src/main/java/com/hank/musicfree/plugin/api/PluginApi.kt` 为准；本表是 RN 对齐参考，不覆盖代码。

require shim 支持：`cheerio`、`crypto-js`、`dayjs`、`axios`、`qs`、`he`、`big-integer`。
```

- [ ] **Step 3: Write `references/quickjs-threading.md`**

```markdown
# QuickJS Threading

约束（rule-quickjs-single-thread / INC-2026-0009）：

- `Context` 与 `JsBridge` 实例 NOT 线程安全。
- 所有 `Context.evaluate(...)` / `JsBridge.invoke(...)` 必须在 owning dispatcher（`quickJsDispatcher`，单线程 `CoroutineDispatcher`）上执行。
- 跨线程入口必须 `withContext(quickJsDispatcher) { ... }`。

guard 当前 manual：harness-curator-skill 巡检会显式列 INC-2026-0009 提醒人工复核；再现一次跨线程崩溃即升级为 contract-test。

实现入口：`:plugin/src/main/java/com/hank/musicfree/plugin/engine/`。
```

- [ ] **Step 4: Write `references/mock-webserver-recipe.md`**

```markdown
# MockWebServer Recipe

`PluginManagerHttpLifecycleTest`（在 `plugin/src/androidTest/.../manager/`）覆盖 `installFromUrl` 与 `updatePlugin` 的编排路径，无需触网。

模板：

```kotlin
@RunWith(AndroidJUnit4::class)
class PluginManagerHttpLifecycleTest {
    private lateinit var server: MockWebServer
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        runBlocking { pluginManager.uninstallAllPlugins() }
        dataStoreScope.cancel()
        server.shutdown()
    }

    @Test
    fun installFromUrl_writesPluginAndLoadsMeta() {
        server.enqueue(MockResponse().setBody(runtimeShimScript))
        val url = server.url("/wy.js").toString()
        // ... installFromUrl(url); assertion 包括 server.takeRequest().path == "/wy.js"
    }
}
```

DataStore 必须按 `produceFile = { File(appContext.cacheDir, "plugin-http-${UUID.randomUUID()}.preferences_pb") }`。
```

- [ ] **Step 5: Write `references/network-test-gate.md`**

```markdown
# Network Test Gate (-Pintegration)

`:plugin/build.gradle.kts` 把 `-Pintegration` 转换为 instrumentation runner 参数 `pluginNetworkTests`：

```kotlin
val pluginNetworkTestsEnabled = providers.gradleProperty("integration")
    .map { value -> value.isBlank() || value.toBooleanStrictOrNull() == true }
    .orElse(false)

android {
    defaultConfig {
        testInstrumentationRunnerArguments["pluginNetworkTests"] =
            pluginNetworkTestsEnabled.get().toString()
    }
}
```

测试侧：

```kotlin
@Before
fun gateNetwork() {
    val arg = InstrumentationRegistry.getArguments().getString("pluginNetworkTests")
    Assume.assumeTrue(
        "Skipping plugin network integration tests; pass -Pintegration to enable.",
        arg == "true",
    )
}
```

调用：

```bash
./gradlew :plugin:connectedAndroidTest                  # 网络用例 SKIPPED
./gradlew :plugin:connectedAndroidTest -Pintegration    # 全跑（需要稳定网络）
```

contract guard：`PluginNetworkTestGateContractTest` 在 PR 3 静态扫真域名出现的测试文件命名 + Assume 模式。
```

- [ ] **Step 6: Commit**

```bash
git add .agents/skills/plugin-system-skill/
git commit -m "$(cat <<'EOF'
docs(skills): add plugin-system-skill with quickjs/mockwebserver/gate refs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.6: Write `media-player-skill/SKILL.md` and references

**Files:**
- Create: `.agents/skills/media-player-skill/SKILL.md`
- Create: `.agents/skills/media-player-skill/references/{controller-connect,lyrics-follow-and-seek,immersive-statusbar-vs-content,playback-service-lifecycle}.md`

- [ ] **Step 1: Write `SKILL.md`**

```markdown
---
name: media-player
description: >
  Use this skill for any change in :player or feature/player-ui (Media3,
  ExoPlayer, MediaSessionService, PlayerController connection, lyric
  rendering / follow / seek overlay, mini player, immersive chrome).
  Trigger phrases: "Media3", "ExoPlayer", "MediaSession", "PlayerController",
  "connect", "主线程 runBlocking", "lyric", "状态栏沉浸", "mini player",
  "PlaybackService".
---

# Media Player Skill

Cross-tool guidance for player engine and player UI.

## 必读 gate

- [`docs/dev-harness/player/rules.md`](references/rules.md)
- [`docs/dev-harness/player/incidents.md`](references/incidents.md)
- 涉及 instrumentation 测试时还需 [`docs/dev-harness/test/rules.md`](../test-stability-skill/references/rules.md)

## Workflow checklist

1. 读 rules.md / incidents.md，确认改动落点：PlayerController 连接 / 沉浸式 chrome / 歌词跟随 / PlaybackService。
2. instrumentation 测试 MUST NOT 在主线程内 `runBlocking { controller.connect() }`；用 bounded `withTimeout(5_000L)`（INC-2026-0002 / rule-no-runblocking-mainthread-in-instrumentation）。
3. `PlayerScreen` 内容层 MUST 用 `WindowInsets.statusBars` 显式避让（INC-2026-0011 / rule-immersive-content-respects-statusbar）。
4. 改歌词跟随 / seek overlay 必须跑：`./gradlew :feature:player-ui:testDebugUnitTest --no-daemon`，含 `MiniPlayerContentTest`、`LyricFollow*Test`、`LyricSeekOverlay*Test`。
5. 改 PlaybackService 生命周期对照 `docs/superpowers/specs/2026-05-04-playback-notification-design.md`。

## References

- [controller-connect.md](references/controller-connect.md)
- [lyrics-follow-and-seek.md](references/lyrics-follow-and-seek.md)
- [immersive-statusbar-vs-content.md](references/immersive-statusbar-vs-content.md)
- [playback-service-lifecycle.md](references/playback-service-lifecycle.md)
```

- [ ] **Step 2: Write `references/controller-connect.md`**

```markdown
# PlayerController Connect

正确范式（instrumentation）：

```kotlin
@Before
fun setUp() = runBlocking {
    controller = PlayerController(context)
    withTimeout(5_000L) {
        controller.connect()
    }
}
```

helper（bounded await + 异常回传）：

```kotlin
private fun runOnAppThread(description: String = "main thread action", block: () -> Unit) {
    val latch = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    context.mainExecutor.execute {
        try { block() }
        catch (t: Throwable) { failure.set(t) }
        finally { latch.countDown() }
    }
    assertTrue("Timed out waiting for $description", latch.await(5, TimeUnit.SECONDS))
    failure.get()?.let { throw it }
}
```

不允许：`mainExecutor.execute { runBlocking { controller.connect() } }`、无界 `latch.await()`、无 timeout 的 connect。
```

- [ ] **Step 3: Write `references/lyrics-follow-and-seek.md`**

```markdown
# Lyrics Follow & Seek Overlay

互斥状态：

- 自动跟随仅在非手动滑动期间生效。
- seek overlay 进入条件由统一 helper 决定（不分散到多个 composable）。

PR 必跑测试：

- `MiniPlayerContentTest`
- `LyricFollow*Test`（如 `LyricAutoFollowTest`、`LyricFollowDebounceTest`）
- `LyricSeekOverlay*Test`

contract 守门（PR 3 起）：`LyricFollowDebounceContractTest`，验证关键测试类存在 + 关键断言函数被引用。
```

- [ ] **Step 4: Write `references/immersive-statusbar-vs-content.md`**

```markdown
# Immersive Status Bar vs Content

`PlayerScreen`：

- 背景层：`Image(...)`、`Box(Modifier.background(...))` 等 MAY 绘制到状态栏后方。
- 内容层：标题、控件、歌词卡片、按钮 MUST 用 `WindowInsets.statusBars` 显式避让。

实现要点：

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars), // 内容层避让
) {
    // ...
}
```

manual guard：harness-curator-skill 在巡检时显式列 INC-2026-0011 提醒人工核对截图。
```

- [ ] **Step 5: Write `references/playback-service-lifecycle.md`**

```markdown
# PlaybackService Lifecycle

参照 `docs/superpowers/specs/2026-05-04-playback-notification-design.md`。

要点：

- `PlaybackService` 继承 `MediaSessionService`，在 `onTaskRemoved`、`onDestroy` 中按 RN 行为处理通知 / 播放停止。
- `MediaSession` 实例由 `PlaybackService` 拥有；`PlayerController` 通过 `MediaController` 包装层访问。

本 rule 只要求改动 PR 对照该 spec；具体策略在该 spec 内已写明。
```

- [ ] **Step 6: Commit**

```bash
git add .agents/skills/media-player-skill/
git commit -m "$(cat <<'EOF'
docs(skills): add media-player-skill with connect/lyrics/inset/lifecycle refs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.7: Write `test-stability-skill/SKILL.md` and references

**Files:**
- Create: `.agents/skills/test-stability-skill/SKILL.md`
- Create: `.agents/skills/test-stability-skill/references/{runtest-idiom,bounded-await,datastore-isolation,androidtest-runner-baseline,ignore-policy}.md`

- [ ] **Step 1: Write `SKILL.md`**

```markdown
---
name: test-stability
description: >
  Use this skill whenever the task touches *Test.kt files, build.gradle.kts
  test wiring, gradle.properties JVM args, MainDispatcherRule, or any
  test stability concern. Trigger phrases: "单测 hang", "runBlocking",
  "runTest", "advanceUntilIdle", "Robolectric", "DataStore multiple active",
  "D8 OOM", "@Ignore", "instrumentation runner", "connectedAndroidTest".
---

# Test Stability Skill

Cross-tool guidance for unit / integration / instrumentation test
hygiene. Pairs with the test rules + 5 test incidents seeded in v1.

## 必读 gate

- [`docs/dev-harness/test/rules.md`](references/rules.md)
- [`docs/dev-harness/test/incidents.md`](references/incidents.md)

## Workflow checklist

1. 读 rules.md / incidents.md。
2. ViewModel 单测：`runTest(mainDispatcherRule.dispatcher) { ... advanceUntilIdle() ... }`，禁 `runBlocking + Flow.first { predicate }`（INC-2026-0001）。
3. instrumentation 单类初始化 / helper：bounded await + 异常回传（INC-2026-0002）。
4. instrumentation DataStore：每个 test class 实例用 `UUID.randomUUID()` 后缀的 prefs 文件；`@After` cancel scope（INC-2026-0004）。
5. feature 模块声明 runner = 必带 runner 依赖（INC-2026-0005）。
6. `gradle.properties` `-Xmx` ≥ 4096m（INC-2026-0003）。
7. `@Ignore` 必须随 incident 登记 + 升级方案；不允许悄悄添加。
8. 跑：`./gradlew <module>:testDebugUnitTest --no-daemon`；instrumentation 用 `:plugin:connectedAndroidTest`、`:player:connectedDebugAndroidTest`；CI 默认通道不跑真网。

## References

- [runtest-idiom.md](references/runtest-idiom.md)
- [bounded-await.md](references/bounded-await.md)
- [datastore-isolation.md](references/datastore-isolation.md)
- [androidtest-runner-baseline.md](references/androidtest-runner-baseline.md)
- [ignore-policy.md](references/ignore-policy.md)
```

- [ ] **Step 2: Write `references/runtest-idiom.md`**

```markdown
# runTest Idiom

模板：

```kotlin
@Test
fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
    val appPreferences = createAppPreferences()
    val viewModel = createViewModel(appPreferences)
    val treeUri = "content://..."

    viewModel.setStorageDirectory(treeUri)
    advanceUntilIdle()

    assertEquals(treeUri, appPreferences.storageDirectoryUri.first())
}
```

要点：

- `runTest` 复用 `mainDispatcherRule.dispatcher`，否则 `viewModelScope.launch` 走错 dispatcher，`advanceUntilIdle` 推不动。
- `Flow.first { predicate }` 在测试代码替换为 `advanceUntilIdle()` + `Flow.first()`。
- 不在 `runTest` 内嵌 `runBlocking`。
```

- [ ] **Step 3: Write `references/bounded-await.md`**

```markdown
# Bounded Await

instrumentation helper 模板：

```kotlin
private fun runOnAppThread(description: String, block: () -> Unit) {
    val latch = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    context.mainExecutor.execute {
        try { block() }
        catch (t: Throwable) { failure.set(t) }
        finally { latch.countDown() }
    }
    assertTrue("Timed out: $description", latch.await(5, TimeUnit.SECONDS))
    failure.get()?.let { throw it }
}
```

connect 模板：

```kotlin
@Before
fun setUp() = runBlocking {
    controller = PlayerController(context)
    withTimeout(5_000L) { controller.connect() }
}
```

不允许：无界 `latch.await()`；`mainExecutor.execute { runBlocking { ... } }`。
```

- [ ] **Step 4: Write `references/datastore-isolation.md`**

```markdown
# DataStore Isolation in Instrumentation Tests

模板：

```kotlin
private fun testPreferencesFile(prefix: String): File =
    File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb")

private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

val dataStore = PreferenceDataStoreFactory.create(
    scope = dataStoreScope,
    produceFile = { testPreferencesFile("plugin-runtime-local-it") },
)

@After
fun tearDown() {
    runBlocking { pluginManager.uninstallAllPlugins() }
    dataStoreScope.cancel()
}
```

不允许：固定 `*.preferences_pb` 文件名 + 多 `@Test` 方法 + 未关闭 scope。
```

- [ ] **Step 5: Write `references/androidtest-runner-baseline.md`**

```markdown
# AndroidTest Runner Baseline (feature 模块)

声明 runner 必带依赖：

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

contract guard（PR 3 起）：`FeatureAndroidTestRunnerBaselineContractTest` 静态扫 `feature/*/build.gradle.kts`。
```

- [ ] **Step 6: Write `references/ignore-policy.md`**

```markdown
# @Ignore Policy

约束：

- 仓库当前应保持 `@Ignore` 全部归零。
- 新增 `@Ignore` MUST 同步登记一条 incident，写明触发条件、临时绕过原因、升级方案。
- harness-curator-skill 巡检时会扫描 `*.kt` 中的 `@Ignore`，未登记 incident 的会进 REPORT。

验证：

```bash
grep -rn "@Ignore" --include="*.kt" 2>/dev/null | grep -v build/ | grep -v .worktrees/
```

预期：空输出。
```

- [ ] **Step 7: Commit**

```bash
git add .agents/skills/test-stability-skill/
git commit -m "$(cat <<'EOF'
docs(skills): add test-stability-skill with runtest/await/datastore/runner refs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.8: Write `harness-curator-skill/SKILL.md` and references

**Files:**
- Create: `.agents/skills/harness-curator-skill/SKILL.md`
- Create: `.agents/skills/harness-curator-skill/references/{curate-workflow,drift-detection,memory-promotion,report-template}.md`

- [ ] **Step 1: Write `SKILL.md`**

```markdown
---
name: harness-curator
description: >
  Use this skill to periodically audit the Dev Harness: detect drift,
  recurrence of indexed incidents, missing guards, and Claude Code
  user auto-memory entries that should be promoted to repo-level rules.
  Trigger phrases: "巡检 harness", "更新错误库", "盘点 incidents",
  "校核 rules", "校核 guard", "同步 user memory 项目级条目",
  "生成 harness 报告".

  This skill produces REPORT.md only — it does NOT modify
  incidents.md / rules.md / index.md directly. Patches in REPORT
  must be applied by a human reviewer.
---

# Harness Curator Skill

Periodic audit + drift detection for the Dev Harness.

## 必读 gate

- [`docs/dev-harness/INDEX.md`](../../../docs/dev-harness/INDEX.md)
- [`docs/dev-harness/incidents/index.md`](../../../docs/dev-harness/incidents/index.md)
- 每域 `rules.md` + `incidents.md`（按需）
- `~/.claude/projects/.../memory/MEMORY.md`（识别项目级、可 promote 条目；个人会话偏好留原位）

## 不变约束

本 skill 不直接修改 `incidents.md` / `rules.md` / `index.md`。仅产 REPORT.md，patch 由人合入。该约束由 PR 3 的 `harness-curator-skill/SKILL.md` 自身 contract test 守护。

## Workflow checklist

详见 [curate-workflow.md](references/curate-workflow.md)。

1. 创建 worktree：`git worktree add .worktrees/harness-curate-$(date +%F) -b harness/curate-$(date +%F) main`，遵循 AGENTS.md 路径约束。
2. 在 worktree 内执行盘点：见 references/drift-detection.md。
3. 挖掘候选 incidents（最近 commit + user memory promotion）：见 references/memory-promotion.md。
4. 输出 `REPORT.md`：见 references/report-template.md。
5. 不修改 incidents.md / rules.md / index.md，REPORT 中的 patch 等用户确认后再合并。

## References

- [curate-workflow.md](references/curate-workflow.md)
- [drift-detection.md](references/drift-detection.md)
- [memory-promotion.md](references/memory-promotion.md)
- [report-template.md](references/report-template.md)
```

- [ ] **Step 2: Write `references/curate-workflow.md`**

```markdown
# Curate Workflow

```bash
DATE=$(date +%F)
git worktree add .worktrees/harness-curate-$DATE -b harness/curate-$DATE main
cd .worktrees/harness-curate-$DATE

# 1. 跑现状基线
bash scripts/dev-harness/check.sh --skip-contract-tests || echo "现状基线已记入 REPORT"

# 2. 盘点最近 30 天 commit
git log --since="30 days ago" --pretty=format:'%h %ad %s' --date=short > /tmp/recent-commits.txt

# 3. 解析 incidents 索引
python3 scripts/dev-harness/grep-check.py || true   # 失败仅作信号
```

输出：worktree 内 `REPORT.md`，分节列 drift / recurrence / new candidates / memory promotion / 建议 diff。
```

- [ ] **Step 3: Write `references/drift-detection.md`**

```markdown
# Drift Detection

针对每条 `status=active` 的 incident：

1. **rule_ref 存在性**：anchor 在 rules.md 中是否找得到（grep `^## .*\{#<anchor>\}` 或显式 `# rule-...`）。
2. **guard.target 存在性**：contract-test 的 target 文件是否在工作树中。
3. **复发签名**：grep signature 跑出非空 → 报 recurrence，列文件:行。
4. **rules ↔ incidents 反向引用**：rules.md 每条 MUST 是否有 `implemented_by:` 行；缺则报 `rule without evidence`。
5. **DOCS_STATUS.md 登记**：dev-harness 文档全部登记为"当前规范"。
6. **symlinks**：跑 `bash scripts/dev-harness/symlinks-check.sh`。

每条问题落入 REPORT.md 的对应小节，附建议 diff（unified diff 块）。
```

- [ ] **Step 4: Write `references/memory-promotion.md`**

```markdown
# Memory Promotion

读取 `~/.claude/projects/-Users-zili-code-android-MusicFreeAndroid/memory/MEMORY.md`：

- **项目级 rule 候选**：与代码 / 架构 / 测试约束直接相关，应跨工具生效（例：DB schema during dev、UI 设计原则）。
- **个人会话偏好**：交互习惯、回答语气、输出长度等，仅本机 Claude Code 使用，留原位。

判别启发式：

- 出现 entity / migration / build / test / API / domain 名称 → 大概率项目级。
- 出现 "user prefers" / "我喜欢" / 回答风格 → 大概率个人级。

提议形式（写入 REPORT 的 memory promotion 小节）：

- `<条目标题>` → 提议 promote 到 `docs/dev-harness/<area>/rules.md` 的 `<section>` 段，并在 user memory 中删除原条目。

不允许：本 skill 直接修改 `~/.claude/projects/.../MEMORY.md` 或 repo 文件；用户确认后再 apply。
```

- [ ] **Step 5: Write `references/report-template.md`**

```markdown
# REPORT Template

```markdown
# Harness Curate Report — YYYY-MM-DD

## Drift

- (空) 或 incident-level 列表 + diff

## Recurrence

- (空) 或 grep / contract-test 失败 + 复发位置 + 建议修复

## New Candidates (from recent commits)

- 草稿 incident（id 占位 TBD-N）+ commit 引用

## Memory Promotion

- user memory 条目 → 提议 promote 到 docs/dev-harness/<area>/rules.md

## 建议 diff

```diff
--- a/docs/dev-harness/<area>/rules.md
+++ b/docs/dev-harness/<area>/rules.md
@@ ...
```
```

REPORT 不直接落到 main；由人合入对应 `.worktrees/feat-...` 或独立 PR。
```

- [ ] **Step 6: Commit**

```bash
git add .agents/skills/harness-curator-skill/
git commit -m "$(cat <<'EOF'
docs(skills): add harness-curator-skill with workflow/drift/memory/report refs

The skill explicitly produces REPORT.md only and does not modify
incidents.md / rules.md / index.md; PR 3 will add a contract test
asserting that this constraint remains in SKILL.md text.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.9: Add references symlinks (rules.md / incidents.md)

**Files (symlinks):**
- `.agents/skills/ui-harness-skill/references/rules.md` → `../../../../docs/dev-harness/ui/rules.md`
- `.agents/skills/ui-harness-skill/references/incidents.md` → `../../../../docs/dev-harness/ui/incidents.md`
- (same pattern for plugin-system / media-player / test-stability)

- [ ] **Step 1: Create symlinks**

```bash
for area in ui:ui-harness plugin:plugin-system player:media-player test:test-stability; do
  domain="${area%%:*}"
  skill="${area##*:}-skill"
  ln -s "../../../../docs/dev-harness/$domain/rules.md" \
        ".agents/skills/$skill/references/rules.md"
  ln -s "../../../../docs/dev-harness/$domain/incidents.md" \
        ".agents/skills/$skill/references/incidents.md"
done
```

(`harness-curator-skill` does NOT get rules/incidents symlinks; it reads via paths from SKILL.md.)

- [ ] **Step 2: Verify with readlink**

```bash
readlink .agents/skills/ui-harness-skill/references/rules.md
```

Expected: `../../../../docs/dev-harness/ui/rules.md`. Resolve check:

```bash
realpath .agents/skills/ui-harness-skill/references/rules.md
```

Expected: absolute path ending `docs/dev-harness/ui/rules.md`.

### Task 2.10: Add `.claude/skills/<name>` and `.codex/skills/<name>` symlinks

- [ ] **Step 1: Create the skill-level symlinks**

```bash
for skill in ui-harness-skill plugin-system-skill media-player-skill test-stability-skill harness-curator-skill; do
  ln -s "../../.agents/skills/$skill" ".claude/skills/$skill"
  ln -s "../../.agents/skills/$skill" ".codex/skills/$skill"
done
```

- [ ] **Step 2: Verify**

```bash
ls -la .claude/skills/ .codex/skills/
```

Expected: each entry shown with `-> ../../.agents/skills/<name>`.

- [ ] **Step 3: Commit symlinks (Task 2.9 + 2.10)**

```bash
git add .agents/skills/*/references/rules.md .agents/skills/*/references/incidents.md \
        .claude/skills/* .codex/skills/*
git commit -m "$(cat <<'EOF'
chore(skills): symlink skills into .claude/.codex and references to dev-harness

Single source of truth lives under .agents/skills; .claude/skills and
.codex/skills carry symlinks. Each skill's references/rules.md and
references/incidents.md also symlink back to docs/dev-harness/<area>/.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.11: Promote user auto-memory project-level entries

**Files:**
- Modify: `docs/dev-harness/ui/rules.md` (already has 设计原则 + 待开域 backlog after Task 1.3 — verify entries match)

- [ ] **Step 1: Verify Task 1.3 already wrote both promotion landing spots**

Run:
```bash
grep -n "MD3" docs/dev-harness/ui/rules.md
grep -n "DB schema during dev" docs/dev-harness/ui/rules.md
```

Expected: hits in 设计原则 (MD3 + RN layout) and 待开域 backlog (DB schema). If either is missing, add it now in a separate edit.

- [ ] **Step 2: Document promotion in REPORT-style memory note**

Add section in `.agents/skills/harness-curator-skill/references/memory-promotion.md` end (already done in Task 2.8 step 4 — verify the content is present).

- [ ] **Step 3: No user-memory file mutation in this PR**

The user's local `~/.claude/projects/.../MEMORY.md` lives outside the repo. This PR documents the promotion target; the user (out-of-band) deletes promoted entries at their convenience using Claude Code memory commands. Do NOT touch `~/.claude/`.

- [ ] **Step 4: No commit needed if Task 1.3 already wrote both entries**

If a fix-up edit was needed in Step 1, commit:

```bash
git add docs/dev-harness/ui/rules.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): finalize ui rules user-memory promotion entries

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.12: Tighten `dev-harness-gate.yml` to strict symlinks

**Files:**
- Modify: `.github/workflows/dev-harness-gate.yml`

- [ ] **Step 1: Replace the symlink step with strict mode**

Edit, `old_string`:

```yaml
      - name: Symlinks check (allow-empty during PR 1)
        run: bash scripts/dev-harness/symlinks-check.sh --allow-empty
```

`new_string`:

```yaml
      - name: Symlinks check
        run: bash scripts/dev-harness/symlinks-check.sh
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/dev-harness-gate.yml
git commit -m "$(cat <<'EOF'
ci(dev-harness): drop --allow-empty from symlinks-check now that skills exist

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 2.13: PR 2 local validation

- [ ] **Step 1: Run strict check**

```bash
bash scripts/dev-harness/check.sh --skip-contract-tests
```

Expected: symlinks PASS (strict), grep PASS.

- [ ] **Step 2: Smoke build**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual call harness-curator-skill (optional)**

Open a Claude Code / Codex session in this worktree, invoke the curator skill, confirm it produces a REPORT.md with 0 drift / 0 recurrence (it may legitimately list user-memory promotion items already done in PR 1; mark as resolved).

### Task 2.14: Push and open PR 2

- [ ] **Step 1: Push**

```bash
git push -u origin feat/dev-harness-skills
```

- [ ] **Step 2: Open PR**

```bash
gh pr create --title "Dev Harness skills: 5 SKILL.md + symlinks + memory promotion" --body "$(cat <<'EOF'
## Summary

- Adds 5 skills under `.agents/skills/` (ui-harness, plugin-system, media-player, test-stability, harness-curator), each with SKILL.md frontmatter triggers and a references/ folder.
- Symlinks every skill into `.claude/skills/` and `.codex/skills/`; references/rules.md and references/incidents.md symlink back to `docs/dev-harness/<area>/`.
- Tightens `dev-harness-gate.yml` symlinks step to strict mode.
- Confirms PR 1's `docs/dev-harness/ui/rules.md` already absorbs the user-memory project-level entries (MD3 + RN layout in 设计原则; DB schema during dev in 待开域 backlog).

## Test plan

- [ ] `bash scripts/dev-harness/check.sh --skip-contract-tests` passes
- [ ] `./gradlew :app:assembleDebug --no-daemon` passes
- [ ] CI `Dev Harness Gate / gate` is green

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 3 — PR 3: Contract tests

Branch: `feat/dev-harness-contracts`. Worktree path: `.worktrees/feat-dev-harness-contracts`.

All Phase 3 tasks follow strict TDD: write the test, run to confirm it fails (or passes if no violation exists), commit. Each test must include a top-line KDoc `Guards INC-XXXX. See docs/dev-harness/<area>/rules.md#<anchor>.`.

### Task 3.1: Create the worktree (after PR 2 merged)

- [ ] **Step 1**

```bash
git fetch origin
git checkout main
git pull --ff-only origin main
git worktree add .worktrees/feat-dev-harness-contracts -b feat/dev-harness-contracts main
cd .worktrees/feat-dev-harness-contracts
```

### Task 3.2: Add `TestRunTestIdiomContractTest` (Guards INC-2026-0001)

**Files:**
- Create: `app/src/test/java/com/hank/musicfree/harness/contracts/TestRunTestIdiomContractTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.hank.musicfree.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0001. See docs/dev-harness/test/rules.md#rule-runtest-mandatory.
 *
 * Forbids `runBlocking { ... .first { ... } ... }` self-spinning predicate
 * patterns inside *ViewModelTest.kt files. New violations must either be
 * rewritten with `runTest(mainDispatcherRule.dispatcher) { advanceUntilIdle() }`
 * or be added to the explicit allowlist with a justification.
 */
class TestRunTestIdiomContractTest {

    private val allowed: Set<String> = emptySet()

    private val pattern: Regex = Regex("""runBlocking[^{]*\{[^}]*\.first\s*\{""", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun no_runBlocking_first_predicate_in_viewmodel_tests() {
        val repoRoot = repoRoot()
        val violations = repoRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModelTest.kt") }
            .filterNot { it.path.contains("/build/") || it.path.contains("/.worktrees/") }
            .filter { file ->
                val rel = file.relativeTo(repoRoot).path
                rel !in allowed && pattern.containsMatchIn(file.readText())
            }
            .map { it.relativeTo(repoRoot).path }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0001 contract violated. See docs/dev-harness/test/rules.md#rule-runtest-mandatory.\n" +
                    "Use runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle() instead of runBlocking + Flow.first { ... }.\n" +
                    "Violations:\n" + violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.hank.musicfree.harness.contracts.TestRunTestIdiomContractTest' --no-daemon
```

Expected: PASS (since `2026-05-04-test-suite-rehab` already removed all violations).

If FAIL: open the listed file(s) and migrate them to the runTest idiom. Re-run until green.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hank/musicfree/harness/contracts/TestRunTestIdiomContractTest.kt
git commit -m "$(cat <<'EOF'
test(harness): add TestRunTestIdiomContractTest guarding INC-2026-0001

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.3: Add `PlayerControllerSetupContractTest` (Guards INC-2026-0002)

**Files:**
- Create: `app/src/test/java/com/hank/musicfree/harness/contracts/PlayerControllerSetupContractTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.hank.musicfree.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0002. See docs/dev-harness/test/rules.md#rule-no-runblocking-mainthread-in-instrumentation.
 *
 * Forbids `mainExecutor.execute { runBlocking { ... } }` and unbounded
 * `latch.await()` inside player androidTest sources. Use bounded
 * withTimeout / latch.await(timeout, unit) instead.
 */
class PlayerControllerSetupContractTest {

    private val mainRunBlocking: Regex = Regex("""mainExecutor\.execute\s*\{[^}]*runBlocking""", RegexOption.DOT_MATCHES_ALL)
    private val unboundedAwait: Regex = Regex("""\blatch\.await\(\s*\)""")

    @Test
    fun no_main_thread_runBlocking_or_unbounded_latch_in_player_androidtest() {
        val root = playerAndroidTestRoot()
        if (!root.exists()) return
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                val problems = mutableListOf<String>()
                if (mainRunBlocking.containsMatchIn(text)) {
                    problems += "${file.relativeTo(repoRoot()).path}: mainExecutor.execute { runBlocking { ... } }"
                }
                if (unboundedAwait.containsMatchIn(text)) {
                    problems += "${file.relativeTo(repoRoot()).path}: unbounded latch.await()"
                }
                problems.asSequence()
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0002 contract violated. See docs/dev-harness/test/rules.md#rule-no-runblocking-mainthread-in-instrumentation.\n" +
                    "Use bounded withTimeout / latch.await(seconds, TimeUnit.SECONDS).\n" +
                    "Violations:\n" + violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun playerAndroidTestRoot(): File =
        File(repoRoot(), "player/src/androidTest")

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
```

- [ ] **Step 2: Run and commit**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.hank.musicfree.harness.contracts.PlayerControllerSetupContractTest' --no-daemon
git add app/src/test/java/com/hank/musicfree/harness/contracts/PlayerControllerSetupContractTest.kt
git commit -m "$(cat <<'EOF'
test(harness): add PlayerControllerSetupContractTest guarding INC-2026-0002

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: PASS.

### Task 3.4: Add `FeatureAndroidTestRunnerBaselineContractTest` (Guards INC-2026-0005)

**Files:**
- Create: `app/src/test/java/com/hank/musicfree/harness/contracts/FeatureAndroidTestRunnerBaselineContractTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.hank.musicfree.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0005. See docs/dev-harness/test/rules.md#rule-feature-androidtest-baseline.
 *
 * Each feature/* module that declares testInstrumentationRunner must also
 * declare androidTestImplementation(libs.androidx.test.runner). Otherwise
 * full ./gradlew connectedAndroidTest crashes when the feature module's
 * test APK tries to instantiate the runner.
 */
class FeatureAndroidTestRunnerBaselineContractTest {

    private val runnerDecl: Regex = Regex("""testInstrumentationRunner\s*=\s*"androidx\.test\.runner\.AndroidJUnitRunner"""")
    private val runnerDep: Regex = Regex("""androidTestImplementation\s*\(\s*libs\.androidx\.test\.runner\s*\)""")

    @Test
    fun feature_modules_declaring_runner_must_also_declare_runner_dep() {
        val featureRoot = File(repoRoot(), "feature")
        val violations = featureRoot.listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { module -> File(module, "build.gradle.kts").takeIf { it.exists() } }
            ?.filter { build ->
                val text = build.readText()
                runnerDecl.containsMatchIn(text) && !runnerDep.containsMatchIn(text)
            }
            ?.map { it.relativeTo(repoRoot()).path }
            ?.toList()
            ?: emptyList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0005 contract violated. See docs/dev-harness/test/rules.md#rule-feature-androidtest-baseline.\n" +
                    "Add `androidTestImplementation(libs.androidx.test.runner)` to:\n" +
                    violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
```

- [ ] **Step 2: Run and commit**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.hank.musicfree.harness.contracts.FeatureAndroidTestRunnerBaselineContractTest' --no-daemon
git add app/src/test/java/com/hank/musicfree/harness/contracts/FeatureAndroidTestRunnerBaselineContractTest.kt
git commit -m "$(cat <<'EOF'
test(harness): add FeatureAndroidTestRunnerBaselineContractTest guarding INC-2026-0005

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: PASS.

### Task 3.5: Add `UiNavAnimationDurationContractTest` (Guards INC-2026-0006)

**Files:**
- Create: `app/src/test/java/com/hank/musicfree/harness/contracts/UiNavAnimationDurationContractTest.kt`

- [ ] **Step 1: Write the thin wrapper**

```kotlin
package com.hank.musicfree.harness.contracts

import com.hank.musicfree.navigation.MusicFreeScreenTransitionDurationMillis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards INC-2026-0006. See docs/dev-harness/ui/rules.md#rule-nav-animation-100ms.
 *
 * The screen transition duration constant must remain at 100ms to mirror
 * the RN original (../MusicFree/src/entry/index.tsx animationDuration: 100).
 *
 * This is a thin wrapper around the existing
 * MusicFreeNavTransitionsTest assertion so the harness gradle filter
 * (--tests '*harness.contracts.*') picks it up.
 */
class UiNavAnimationDurationContractTest {
    @Test
    fun ordinary_screen_transition_duration_matches_rn() {
        assertEquals(100, MusicFreeScreenTransitionDurationMillis)
    }
}
```

- [ ] **Step 2: Run and commit**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.hank.musicfree.harness.contracts.UiNavAnimationDurationContractTest' --no-daemon
git add app/src/test/java/com/hank/musicfree/harness/contracts/UiNavAnimationDurationContractTest.kt
git commit -m "$(cat <<'EOF'
test(harness): add UiNavAnimationDurationContractTest thin wrapper for INC-2026-0006

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: PASS.

### Task 3.6: Add `PluginDataStoreIsolationContractTest` (Guards INC-2026-0004)

**Files:**
- Create: `plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginDataStoreIsolationContractTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.hank.musicfree.plugin.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0004. See docs/dev-harness/test/rules.md#rule-datastore-per-instance-isolation.
 *
 * Any plugin/src/androidTest/* that calls PreferenceDataStoreFactory.create
 * must produce per-instance preferences files (UUID.randomUUID() in
 * produceFile). Static filenames trigger 'multiple active DataStores'
 * across AndroidJUnit4 test class instances.
 */
class PluginDataStoreIsolationContractTest {

    @Test
    fun every_PreferenceDataStoreFactory_create_uses_uuid_isolated_file() {
        val root = File(repoRoot(), "plugin/src/androidTest")
        if (!root.exists()) return
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                val createIndices = "PreferenceDataStoreFactory.create(".toRegex().findAll(text).map { it.range.first }
                val results = mutableListOf<String>()
                for (idx in createIndices) {
                    val window = text.substring(idx, minOf(idx + 600, text.length))
                    val hasUuid = window.contains("UUID.randomUUID()") || window.contains("testPreferencesFile(")
                    if (!hasUuid) {
                        results += "${file.relativeTo(repoRoot()).path}: PreferenceDataStoreFactory.create at offset $idx lacks UUID isolation"
                    }
                }
                results.asSequence()
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0004 contract violated. See docs/dev-harness/test/rules.md#rule-datastore-per-instance-isolation.\n" +
                    "Use produceFile = { File(appContext.cacheDir, \"\$prefix-\${UUID.randomUUID()}.preferences_pb\") }.\n" +
                    "Violations:\n" + violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
```

- [ ] **Step 2: Run and commit**

```bash
./gradlew :plugin:testDebugUnitTest --tests 'com.hank.musicfree.plugin.harness.contracts.PluginDataStoreIsolationContractTest' --no-daemon
git add plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginDataStoreIsolationContractTest.kt
git commit -m "$(cat <<'EOF'
test(harness): add PluginDataStoreIsolationContractTest guarding INC-2026-0004

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: PASS.

### Task 3.7: Add `PluginNetworkTestGateContractTest` (Guards INC-2026-0010)

**Files:**
- Create: `plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginNetworkTestGateContractTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.hank.musicfree.plugin.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0010. See docs/dev-harness/plugin/rules.md#rule-network-test-gated.
 *
 * Any androidTest source mentioning a known live host must live in a file
 * named *NetworkIntegrationTest.kt and reference Assume.assumeTrue with the
 * pluginNetworkTests runner argument.
 */
class PluginNetworkTestGateContractTest {

    private val liveHosts: List<String> = listOf("kstore.vip")

    @Test
    fun network_androidtest_files_must_be_gated_by_assume() {
        val root = File(repoRoot(), "plugin/src/androidTest")
        if (!root.exists()) return
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                liveHosts.any { host -> text.contains(host) }
            }
            .filter { file ->
                val text = file.readText()
                val nameOk = file.name.endsWith("NetworkIntegrationTest.kt")
                val gateOk = text.contains("Assume.assumeTrue") && text.contains("pluginNetworkTests")
                !(nameOk && gateOk)
            }
            .map { it.relativeTo(repoRoot()).path }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0010 contract violated. See docs/dev-harness/plugin/rules.md#rule-network-test-gated.\n" +
                    "Move live-host tests into *NetworkIntegrationTest.kt and gate with Assume.assumeTrue + pluginNetworkTests.\n" +
                    "Violations:\n" + violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
```

- [ ] **Step 2: Run and commit**

```bash
./gradlew :plugin:testDebugUnitTest --tests 'com.hank.musicfree.plugin.harness.contracts.PluginNetworkTestGateContractTest' --no-daemon
git add plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginNetworkTestGateContractTest.kt
git commit -m "$(cat <<'EOF'
test(harness): add PluginNetworkTestGateContractTest guarding INC-2026-0010

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: PASS.

### Task 3.8: Add `LyricFollowDebounceContractTest` (Guards INC-2026-0012)

**Files:**
- Create: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/harness/contracts/LyricFollowDebounceContractTest.kt`

- [ ] **Step 1: Write the thin wrapper**

```kotlin
package com.hank.musicfree.feature.playerui.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0012. See docs/dev-harness/player/rules.md#rule-lyric-follow-debounce.
 *
 * Asserts that the key lyric-related test files exist under
 * feature/player-ui/src/test/. If any file is renamed or removed, this
 * contract test fails so the change is forced into the lyric debounce
 * incident's review workflow.
 */
class LyricFollowDebounceContractTest {

    private val requiredTestFiles: List<String> = listOf(
        "MiniPlayerContentTest.kt",
        "LyricFollow", // any LyricFollow*Test.kt
        "LyricSeekOverlay", // any LyricSeekOverlay*Test.kt
    )

    @Test
    fun key_lyric_tests_present() {
        val root = File(repoRoot(), "feature/player-ui/src/test")
        if (!root.exists()) {
            throw AssertionError("Expected feature/player-ui/src/test to exist")
        }
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val missing = requiredTestFiles.filterNot { needle ->
            files.any { it.name.contains(needle) }
        }
        if (missing.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0012 contract violated. See docs/dev-harness/player/rules.md#rule-lyric-follow-debounce.\n" +
                    "Missing or renamed lyric guard tests:\n" +
                    missing.joinToString("\n") { "  - $it" } +
                    "\nIf intentional, update incidents.md and this contract together."
            )
        }
    }

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
```

- [ ] **Step 2: Verify the substring patterns match real files**

```bash
ls feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/ 2>&1 | head
find feature/player-ui/src/test -name '*LyricFollow*Test.kt'
find feature/player-ui/src/test -name '*LyricSeekOverlay*Test.kt'
find feature/player-ui/src/test -name 'MiniPlayerContentTest.kt'
```

If any returned path is empty, the contract will fail. Confirm the files exist (they do as of `2026-05-05` lyrics interaction fix). If the tests have different exact names, adjust `requiredTestFiles` substrings to match.

- [ ] **Step 3: Run and commit**

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests 'com.hank.musicfree.feature.playerui.harness.contracts.LyricFollowDebounceContractTest' --no-daemon
git add feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/harness/contracts/LyricFollowDebounceContractTest.kt
git commit -m "$(cat <<'EOF'
test(harness): add LyricFollowDebounceContractTest guarding INC-2026-0012

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: PASS.

### Task 3.9: Update incidents `guard.target` fields

**Files:**
- Modify: `docs/dev-harness/test/incidents.md`
- Modify: `docs/dev-harness/ui/incidents.md`
- Modify: `docs/dev-harness/plugin/incidents.md`
- Modify: `docs/dev-harness/player/incidents.md`

The `guard.target` fields were written in PR 1 with the final paths already filled in (per Tasks 1.8–1.11). Verify after PR 3 contract tests land that each path resolves.

- [ ] **Step 1: Verify guard.target paths**

```bash
grep -E '^- guard:|^    target:' docs/dev-harness/{test,ui,plugin,player}/incidents.md
```

- [ ] **Step 2: For each contract-test entry, confirm the file exists**

```bash
for path in \
  app/src/test/java/com/hank/musicfree/harness/contracts/TestRunTestIdiomContractTest.kt \
  app/src/test/java/com/hank/musicfree/harness/contracts/PlayerControllerSetupContractTest.kt \
  app/src/test/java/com/hank/musicfree/harness/contracts/FeatureAndroidTestRunnerBaselineContractTest.kt \
  app/src/test/java/com/hank/musicfree/harness/contracts/UiNavAnimationDurationContractTest.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginDataStoreIsolationContractTest.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/PluginNetworkTestGateContractTest.kt \
  feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/harness/contracts/LyricFollowDebounceContractTest.kt; do
  test -f "$path" && echo "OK: $path" || echo "MISSING: $path"
done
```

Expected: all `OK`. If any `MISSING`, fix the corresponding incident entry (`Edit` to update the path) before committing.

- [ ] **Step 3: If any guard.target needed updating, commit**

If no edits needed: skip. Otherwise:

```bash
git add docs/dev-harness/{test,ui,plugin,player}/incidents.md
git commit -m "$(cat <<'EOF'
docs(dev-harness): finalize incident guard.target paths after contract tests land

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.10: Activate the contract-test step in CI

**Files:**
- Modify: `.github/workflows/dev-harness-gate.yml`

- [ ] **Step 1: Replace the placeholder step with real Gradle invocation**

Edit, `old_string`:

```yaml
      - name: Contract tests (JVM) — PR 1 placeholder
        run: |
          echo "Contract-test step is staged; activates in PR 3 once contract tests land."
          echo "See docs/superpowers/plans/2026-05-09-dev-harness-foundation.md Phase 3."
```

`new_string`:

```yaml
      - name: Contract tests (JVM)
        run: |
          ./gradlew \
            :app:testDebugUnitTest :core:testDebugUnitTest :data:testDebugUnitTest \
            :downloader:testDebugUnitTest :plugin:testDebugUnitTest :player:testDebugUnitTest \
            :feature:home:testDebugUnitTest :feature:player-ui:testDebugUnitTest \
            :feature:search:testDebugUnitTest :feature:settings:testDebugUnitTest \
            --tests '*harness.contracts.*' --no-daemon
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/dev-harness-gate.yml
git commit -m "$(cat <<'EOF'
ci(dev-harness): activate contract-test step now that contracts exist

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 3.11: PR 3 local validation

- [ ] **Step 1: Full local check**

```bash
bash scripts/dev-harness/check.sh
```

Expected: all three steps PASS (symlinks strict, grep, contract tests).

- [ ] **Step 2: Smoke build**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Confirm `@Ignore` is still 0**

```bash
grep -rn "@Ignore" --include="*.kt" 2>/dev/null | grep -v build/ | grep -v .worktrees/
```

Expected: empty output.

### Task 3.12: Push and open PR 3

- [ ] **Step 1: Push**

```bash
git push -u origin feat/dev-harness-contracts
```

- [ ] **Step 2: Open PR**

```bash
gh pr create --title "Dev Harness contracts: 6 new + 1 thin wrapper + CI gate activation" --body "$(cat <<'EOF'
## Summary

- Adds 6 new JVM contract tests under `<module>/src/test/.../harness/contracts/` guarding INC-2026-0001/0002/0004/0005/0010 and 1 ID per test file.
- Adds `UiNavAnimationDurationContractTest` and `LyricFollowDebounceContractTest` thin wrappers guarding INC-2026-0006 and INC-2026-0012 respectively.
- Activates the `Contract tests (JVM)` step in `dev-harness-gate.yml` (was a placeholder in PR 1).
- Verifies all 12 v1 incidents now have valid `guard.target` paths or `manual` reasoning.
- Confirms `@Ignore` remains at zero across the repo.

## Test plan

- [ ] `bash scripts/dev-harness/check.sh` passes (strict symlinks + grep + contract tests)
- [ ] `./gradlew :app:assembleDebug --no-daemon` passes
- [ ] CI `Dev Harness Gate / gate` is green (all three steps live)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 4 — Operational follow-ups (no code, no commits)

Documented for completeness; not actionable inside this plan.

- Repo administrator enables `Dev Harness Gate / gate` as a required check in branch protection settings (`main`, `feat/**`, `fix/**`).
- Post-merge of all three PRs: bump `docs/superpowers/specs/2026-05-09-dev-harness-foundation-design.md` status from `当前规范` to `当前参考` and record the verification date in `docs/DOCS_STATUS.md`.
- Schedule a manual `harness-curator-skill` run at next release cut; capture the REPORT in the release notes.

---

## Self-Review

**1. Spec coverage:**

- §0 background / §1 memory boundary → AGENTS.md changes (1.12), DOCS_STATUS (1.13), and the user-memory promotion landing in 1.3 + verified in 2.11. ✓
- §2 directory & entry → 1.2 (skeleton), 1.3 (ui rules migration), 1.12 (AGENTS), 1.13 (DOCS_STATUS). ✓
- §3 incidents format → 1.7 (template + index), 1.8/1.9/1.10/1.11 (per-area seeds). ✓
- §4 five skills → 2.3–2.10 (SKILL.md + references + symlinks), 2.11 (memory promotion). ✓
- §5 contract tests + scripts + CI → 1.14/1.15/1.16 (scripts), 1.17 (CI staged), 2.12 (strict symlinks), 3.2–3.8 (contracts), 3.10 (activate gate). ✓
- §6 worktree + 3 PRs → Phase 1/2/3 entry tasks (1.1, 2.1, 3.1). ✓
- §7 acceptance gates → 1.18, 2.13, 3.11. ✓
- §8 risks → covered in spec; plan executes the mitigations. ✓
- §9 non-goals → no plan tasks for them (correct). ✓
- §10 plan handoff → this document.

**2. Placeholders:** Searched for "TBD" / "TODO" / "fill in" — Phase 1's `dev-harness-gate.yml` placeholder step has explicit text marking it as a staged echo; Task 3.10 replaces it with the real invocation. Nothing else flagged.

**3. Type / signature consistency:**
- `MusicFreeScreenTransitionDurationMillis` referenced in 3.5 ✓ exists at `app/src/main/.../navigation/MusicFreeNavTransitions.kt` (verified during writing-plans research).
- Package paths used for contract tests (`com.hank.musicfree.harness.contracts`, `...plugin.harness.contracts`, `...feature.playerui.harness.contracts`) match the gradle filter `*harness.contracts.*`.
- `pluginNetworkTests` runner arg referenced consistently in 3.7 (test) and `plugin/build.gradle.kts` (per spec §5.3).
- `MainDispatcherRule` left as-is (no dedup); plan does not introduce a new copy.

No outstanding inconsistencies.
