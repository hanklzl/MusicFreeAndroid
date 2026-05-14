# Parity Audit Agent 设计

- 状态：草案，待 review
- 日期：2026-05-15
- 范围：跨工具（Claude Code + Codex）可调用的 sub-agent，自发对比 RN MusicFree（参考实现）与 MusicFreeAndroid（当前重写）在编译产物上的行为差异，按差异向 Android 仓库 GitHub Issues 归集"下阶段迭代重点"
- 关联文档：`AGENTS.md`、`docs/dev-harness/INDEX.md`、`maestro/flows/`、`logging/`

## 背景

MusicFreeAndroid 是 [MusicFree](https://github.com/maotoumao/MusicFree) 的 Android 原生重写版本。当前实现状态约 17/19 页面对齐 RN，但页面级 fidelity 不等于功能/交互/业务逻辑全等。已有的 `home-fidelity` 与零散 spec 都是手工对比，缺乏可重放、可持续追踪的差异审计机制。本 spec 设计一个 sub-agent，把"构建 → 双侧驱动 → 抓取 → 差异提取 → Issue 归集"流水线化，并保持自身可观测、可控、可跨工具。

## 目标

1. 让一次 `parity-audit` 调用自包含完成"build + run + diff + issue"全链路
2. 把"哪些场景跑过、哪些下次跑"的状态持久化到仓库，跨轮可追踪
3. 输出的 GitHub Issue 必须详细到工程师可直接动手修复（含 RN/Android 截图对比、事件 diff、复现步骤、修复指向）
4. agent 自发产出与维护 scenario，不依赖人工预写 catalog
5. Issue 只针对 Android 侧问题；RN 侧异常不打扰 Android 仓库 Issue 列表
6. 设计对 Claude Code 与 Codex 透明，载体走 `.agents/skills/` 软链约定

## 非目标

- 不替代 dev-harness 的 rules.md / incidents.md（contract test 与人工 review 仍是强约束）
- 不直接修复 Android 代码（agent 仅产 Issue，不动 `feature:*` / `core` 等业务模块）
- 不修改 `../MusicFree` 源码树（如需 RN 侧补丁，以 patch 文件形式留给人工 apply）
- v1 不覆盖性能基线学习；先用硬阈值

## §1 形态、目录与调用契约

### 载体

```
.agents/skills/parity-audit-skill/
  SKILL.md                      # 入口文档：触发场景、调用契约、决策树
  references/
    event-taxonomy.md           # 跨端规范事件类型与字段
    issue-template.md           # Issue body 模板（含指纹 hash 占位）
    scenario-authoring.md       # agent 自发生成 scenario 的步骤与约束
    rn-logcat-parser.json       # RN 默认 console.log 的正则映射表
    labels.json                 # 首次跑时需创建的 label 清单（颜色 + 描述）
    failure-modes.md            # 失败分类与处置矩阵
    tool-host-notes.md          # Claude Code / Codex 等价命令注解
scripts/parity-audit/
  bootstrap.sh                  # 校验 adb / Maestro / gh
  build-rn.sh                   # cd ../MusicFree && yarn && cd android && ./gradlew assembleDebug
  build-android.sh              # ./gradlew :app:assembleDebug
  install-both.sh               # adb install -r <rn.apk> <android.apk>
  run-scenario.sh               # 跑 RN flow + Android flow，落截图 / hierarchy / logcat
  screenshot_ssim.py            # SSIM 粗筛
  parse_events.py               # logcat -> events.jsonl（按 taxonomy）
  compare.py                    # 两侧 events + screenshots -> diff.json
  upload-screenshots.sh         # gh release upload parity-screenshots ...
  file_issue.py                 # 拼 body、指纹、查重、创建/评论
  state.py                      # state.json + queue.md 读写
  apply_rn_patch.sh             # 可选，把 rn-patches/*.patch apply 到 ../MusicFree
docs/parity-audit/
  state.json                    # 机读状态
  queue.md                      # 人读队列
  scenarios/                    # 由 agent 自发产出与维护
  runs/<run-id>/                # 每轮运行产物
  rn-patches/                   # 可选 RN parity logger 补丁
  parity-plugins.json           # 双端共享固定插件集
maestro/flows/parity/           # agent 生成的 scenario flow（RN/Android 各一）
  _lib/                         # mark_waypoint.js, dump_hierarchy.yaml 等共享片段
  _bootstrap/                   # install-plugins.{rn,android}.yaml
```

`.agents/skills/parity-audit-skill/` 通过仓库现有约定软链到 `.claude/skills/` 与 `.codex/skills/`。

### 调用契约

| 参数 | 取值 | 说明 |
|---|---|---|
| `scope` | `core` / `non-core` / `all` / `page:<id>` | 本轮跑哪个优先级；缺省读 `state.json.next_recommended` |
| `mode` | `audit`（默认）/ `dry-run` / `replay:<run-id>` | `dry-run` 不建 Issue、不上传 release；`replay` 仅重跑分析与 Issue，跳过构建/抓取 |
| `limit` | int，默认 5 | 单轮最多 scenario 数；防一次跑全集 |
| `device` | adb serial，可选 | 缺省取 `adb devices` 第一行；找不到设备 fail-fast |

### 触发短语

"跑 parity audit"、"RN/Android 对齐扫描"、"对齐审计"、"parity-audit"。写入 SKILL.md `description`，让 LLM 自动判定何时启用。

### SKILL.md 内强约束

- 启动前必须读 `state.json` 与 `queue.md`，否则不知本轮跑什么
- 写 Issue 前必须先在本地 `dry-run` 渲染 body（防指纹拼错）
- 任何 Maestro flow 缺失 / 构建失败 / 设备未连，必须把该 scenario 标 `blocked` 并写入 state，不能沉默跳过
- 当 `git rev-parse --abbrev-ref HEAD == main` 时拒绝执行 audit，要求人工切到 worktree 分支（遵循 `AGENTS.md` 的 worktree 约束）

## §2 状态文件、运行产物与 Scenario Catalog

### `docs/parity-audit/state.json`

机读，是唯一可信源：

```json
{
  "schema_version": 1,
  "updated_at": "2026-05-15T12:30:00+08:00",
  "scenarios": {
    "home_recommend_first_load": {
      "priority": "core",
      "last_run_id": "2026-05-15-1130",
      "last_status": "diff_found",
      "open_issue_numbers": [142],
      "blocked_reason": null,
      "consecutive_pass_count": 0
    }
  },
  "next_recommended": ["search_play_first_result", "music_detail_lyric_follow"]
}
```

`last_status` 枚举：`never_run` / `passed` / `diff_found` / `blocked_runtime` / `blocked_authoring` / `build_failed` / `rn_baseline_unstable` / `both_unstable`。

`next_recommended` 在本轮末由 agent 重算，按"久未跑 + 上次有 diff + priority=core" 排序。

### `docs/parity-audit/queue.md`

人读，agent 每轮重写，便于 review；列：`scenario` / `priority` / `last_run` / `status` / `open_issues` / `next_up?`。

### 单次运行产物

```
docs/parity-audit/runs/<run-id>/
  manifest.json              # 本轮 scope、scenario 列表、设备/构建信息、git SHA
  agent.log.jsonl            # agent 自己的运行事件流
  scenarios/<scenario-id>/
    rn/
      waypoint-XX.png
      waypoint-XX-hierarchy.xml
      logcat.raw.txt
      events.jsonl
    android/
      ... 对称结构 ...
    diff.json                # 机读差异
    diff.md                  # 人读叙述（LLM 写）
    issue.md                 # 待提交 Issue body 草稿（含指纹）
  REPORT.md                  # 整轮总结，每 scenario 一行 + 链接
```

`run-id` 格式：`YYYY-MM-DD-HHMM`（本地时区，对齐 `state.json.updated_at`）。

### Scenario Catalog（自发生成）

**Phase 1 — 首轮种子探查**：仓库第一次跑 audit 时触发。

1. 读 RN `../MusicFree/src/pages/` 枚举 19 个 page，再读各 page 的入口 tsx 抽取顶层动作（按钮、列表点击、tab 切换）作为候选交互
2. 读 Android `core/.../navigation/Routes.kt` 与对应 `feature:*` 模块的 NavHost composable，做同份动作清单
3. 取两侧交集，按"启动→列表→详情→操作"常见 depth 自动生成候选 scenario，每个 page 至少落 1 个 `entry`、1 个 `primary_action`
4. 启动两侧 APK，按 `adb shell am start` 或最小化 Maestro flow 跑到目标页，`uiautomator dump` 抓 hierarchy；挑稳定 selector（优先 `text` → `testTag`/`resource-id` → `accessibilityText`），合成两份 Maestro flow YAML
5. 立即 dry-run 该 flow；通过 → 写入 `docs/parity-audit/scenarios/`；失败 → 标 `blocked_authoring`，由后续轮次或人工补

**Phase 2 — 增量扩展**：每轮 audit 末尾，根据"未覆盖盲区 + 近期 RN PR" 自动补 ≤ 2 个新 scenario，走同一探查路径。

**Scenario YAML（agent 维护）**：

```yaml
id: home_recommend_first_load
title: 首页推荐位首屏加载
priority: core                  # core | non-core
page: home
rn_appid: fun.upup.musicfree
android_appid: com.zili.android.musicfreeandroid.debug
rn_flow: maestro/flows/parity/home_recommend_first_load.rn.yaml
android_flow: maestro/flows/parity/home_recommend_first_load.android.yaml
waypoints:
  - id: 01_after_cold_start
    description: 冷启动后落地首页 1s
  - id: 02_after_recommend_loaded
    description: 推荐位完成首屏渲染
expected_events:
  - plugin.method_called: { method: getRecommendSheetTags }
  - plugin.method_called: { method: getRecommendSheetsByTag }
  - net.request
diff_tolerance:
  screenshot_ssim_min: 0.92
  ignore_event_fields: [timestamp_ms, request_id]
authored_by: agent              # agent | human
authored_run_id: 2026-05-15-1130
flow_health:
  rn: passing                   # passing | flaky | blocked_authoring
  android: passing
  last_validated_run_id: 2026-05-15-1130
notes: |
  RN 首屏推荐位入口位于 ../MusicFree/src/pages/home/components/recommendPanel
  Android 入口 feature:home
```

agent 允许：新建 / 追加 waypoint / 修 selector / 调 `expected_events` / 调 `diff_tolerance`。
agent 禁止：删除已有 scenario（只能 `superseded_by: <id>`）/ 改 `priority`。

## §3 驱动 + 抓取管道

### 3.1 并发模型

**单设备，双侧顺序跑**。截图/hierarchy 只能反映前台 app，并发会抢前台。一个 scenario 的执行结构：

```
per scenario:
  ┌─ 重置共享态（pm clear 两侧 app data）
  ├─ 装/校验固定 plugin 集
  ├─ 跑 RN 侧 Maestro flow（5–60s）       ┐ 各自独占前台
  ├─ 跑 Android 侧 Maestro flow（5–60s）  ┘
  └─ 收集 → diff
```

`device` 缺省取 `adb devices` 第一行；找不到设备 fail-fast。

### 3.2 Preflight

| 步骤 | 命令 | 失败处置 |
|---|---|---|
| 校验 adb / Maestro / gh | `adb version` / `maestro --version` / `gh auth status` | fail-fast |
| 构建 RN APK | `cd ../MusicFree && yarn && cd android && ./gradlew assembleDebug` | `manifest.json.preflight_failed=rn_build`，本轮终止 |
| 构建 Android APK | `./gradlew :app:assembleDebug` | 同上，`android_build` |
| 安装两侧 APK | `adb install -r ...` | 同上 |
| 装固定 plugin 集 | 跑 `maestro/flows/parity/_bootstrap/install-plugins.{rn,android}.yaml`，拉 `docs/parity-audit/parity-plugins.json` 中 URL 列表 | `plugin_bootstrap_failed` |
| 记录设备/构建指纹 | `adb shell getprop ro.product.model` + 两仓库 `git rev-parse HEAD` | 写入 `manifest.json` |

### 3.3 单 scenario 执行

每个 waypoint 三件套：截图 + hierarchy + 同窗口 logcat 切片。Maestro flow（agent 生成时遵守）：

```yaml
appId: fun.upup.musicfree
---
- runScript: file: ../_lib/mark_waypoint.js
    env: { WAYPOINT: "01_after_cold_start" }
- launchApp:
    clearState: true
- waitForAnimationToEnd: { timeout: 5000 }
- takeScreenshot: waypoint-01
- runFlow: file: ../_lib/dump_hierarchy.yaml
    env: { WAYPOINT: "01_after_cold_start" }
# ... 后续 waypoint
```

- `mark_waypoint.js` → `adb shell log -t PARITY_MARK "BEGIN <waypoint-id>"`，在 logcat 里打稳定锚点
- `dump_hierarchy.yaml` → `adb shell uiautomator dump` + `adb pull` 到 `runs/<run-id>/scenarios/<id>/<side>/waypoint-XX-hierarchy.xml`

**Logcat 抓取**：scenario 启动前异步起后台进程：

```
adb logcat -v threadtime --pid=$(adb shell pidof <appId>) \
  *:V > runs/<run-id>/scenarios/<id>/<side>/logcat.raw.txt &
```

flow 结束后 SIGTERM。`PARITY_MARK` 锚点把 raw logcat 切成 waypoint 窗口，喂给 `parse_events.py`。

### 3.4 截图差异预筛

`screenshot_ssim.py` 用 SSIM 粗筛：

- SSIM ≥ `diff_tolerance.screenshot_ssim_min`（默认 0.92）→ `screenshot_match`，不进 LLM 上下文
- SSIM < 阈值 → 两张图作为对比输入交给 LLM 写叙述

依赖 `scikit-image` 或 `opencv-python`，纯本地，不上传任何图片。

### 3.5 失败模式（与 §6.4 联动）

| 现象 | 处置 |
|---|---|
| Maestro element not found / timeout | 同 scenario 最多重试 2 次；3 次仍失败 → `flow_health.<side>=flaky`，本轮 `blocked_runtime`，**不建 Issue** |
| ADB 中途掉线 | 重连 1 次；仍失败 → 本轮整体 abort |
| 网络异常 → 插件接口报错 | `events.jsonl` 标 `network_error=true`；两侧都报错 → `passed`；仅 Android 报错 → 建 Issue 加 `needs-retry` label |
| **RN 单边崩 / 卡 / 报错** | `rn_baseline_unstable`，REPORT 高亮，**不建 Issue** |
| **Android 单边崩** | 立即建 Issue，`kind=crash, severity=critical`，含 ANR/Tombstone 摘录 |
| **双边都崩** | `both_unstable`，REPORT 高亮，**不建 Issue**（人工裁决） |

## §4 功能日志 Diff

### 4.1 规范事件 Taxonomy

所有 diff 只针对**规范化事件**，logcat 原始流仅是来源。v1 收敛到 6 类（落在 `references/event-taxonomy.md`）：

| 类别 | event 名 | 关键字段 | 触发点 |
|---|---|---|---|
| 导航 | `nav.enter` / `nav.leave` | `route`, `params_hash` | 进入/离开页面 |
| 插件调用 | `plugin.method_called` | `plugin_id`, `method`, `args_hash` | PluginApi 14 个方法 |
| 插件结果 | `plugin.method_returned` | `plugin_id`, `method`, `ok`, `result_summary`, `duration_ms` | 同上对应 |
| 网络 | `net.request` / `net.response` | `url_template`, `method`, `status`, `duration_ms` | OkHttp / fetch |
| 播放 | `play.state_changed` | `from`, `to`, `track_id_hash` | ExoPlayer state machine / RN trackPlayer |
| 错误 | `error` | `domain`, `code`, `message_hash` | 异常 / Toast / 业务降级 |

字段哈希化：
- `*_hash`：归一两侧内部 id；`sha1(plugin_id + ":" + canonical_name)[:8]`
- `args_hash` / `url_template`：query/path 中随机或时间相关部分替换为占位（`?q=*` / `&_t=*`）

### 4.2 事件来源

**Android 侧**：天然结构化。`logging` 模块新增 `ParityEventSink`，把 `MfLog` 调用按 taxonomy 映射为 `Log.println(PARITY_TAG, json)`。logcat 用 `--regex 'PARITY_EVT'` 直接拉规范事件流。

**RN 侧**：

1. **默认（不改 RN）**：`references/rn-logcat-parser.json` 用正则匹配 RN 自有 `console.log` / trackPlayer / pluginManager 输出，尽力还原 taxonomy 子集，预计覆盖 ~60%
2. **patched 模式（推荐时机：发现 60% 覆盖率影响判读）**：往 `../MusicFree` 注入极小 `src/utils/parityLogger.ts`（≤ 30 行）+ 在 6 类触发点各 1 行调用，输出 `console.log("[PARITY_EVT]" + JSON.stringify(...))`。agent 用 `grep -l parityLogger ../MusicFree/src` 探测 patch 是否在位

patch 文件落 `docs/parity-audit/rn-patches/`，由人决定是否 apply。

### 4.3 Diff 算法

两路 `events.jsonl` 先各自归并到 (waypoint × event_kind) 桶，按 kind 做有序对比：

```
对每个 waypoint:
  对每种 event kind:
    1. 序列对齐（LCS，跳过 ignore_event_fields）
    2. 三类输出：
       - 仅 RN 有 → "android_missing"
       - 仅 Android 有 → "android_extra"
       - 双方都有但关键字段 hash 不同 → "value_mismatch"
    3. 容忍：
       - 同 kind 数量差 ≤ tolerance（默认 0）→ 记，但 severity=minor
       - duration_ms 差异 > 50% → severity=perf
       - error 频次/类型不一致 → severity=major
```

`diff.json`：

```json
{
  "scenario_id": "search_play_first_result",
  "screenshot_diffs": [{"waypoint": "02_results_loaded", "ssim": 0.81, "verdict": "visual_diff"}],
  "event_diffs": [
    {"waypoint": "02_results_loaded", "kind": "plugin.method_called",
     "verdict": "android_missing", "rn_only": [{"method": "search", "args_hash": "ab12"}]},
    {"waypoint": "03_after_play_tap", "kind": "play.state_changed",
     "verdict": "value_mismatch", "field": "to", "rn": "playing", "android": "buffering"}
  ],
  "verdict": "diff_found",
  "severity": "major"
}
```

`verdict` 枚举：`passed` / `diff_found` / `rn_baseline_unstable` / `both_unstable` / `blocked_runtime`。

### 4.4 LLM 在 diff 里的角色

`compare.py` 只产 `diff.json`（机读）。`diff.md` 由 SKILL.md 引导 LLM 写：

- 输入：`diff.json` + 仅 ssim < 阈值的截图 + 关键 hierarchy.xml 节点
- 输出：现象一句话 + 根因猜测 + 复现步骤 + 修复指向 Android 哪个模块（`feature:*` / `plugin` / `player`）
- 严禁臆造未在 diff.json 里出现的事件

## §5 Issue 生成、指纹与去重

### 5.1 触发条件

只有 `verdict == diff_found` 且 `severity ∈ {major, critical}` 直接建 Issue。`minor` 仅进 REPORT；`perf` / `error-divergence` 建但加 `needs-retry` label。

### 5.2 标题与 Body

**Title**（≤ 70 字符）：

```
[parity][<page>][<kind>] <一句话现象>
```

`<kind>` 枚举：`ui-gap` / `logic-gap` / `missing-feature` / `crash` / `perf` / `error-divergence`。

**Body 模板**（`references/issue-template.md`）：

````markdown
<!-- parity-fingerprint: <12位 hex> -->
<!-- parity-run-id: <run-id> -->
<!-- parity-scenario: <scenario-id> -->

## 现象
<LLM 写的一句话现象描述>

## 对比截图
| RN（参考实现） | Android（当前） |
|---|---|
| ![rn](<release-asset-url-rn>) | ![android](<release-asset-url-android>) |

> waypoint: `<waypoint-id>` · SSIM = `<value>`

## 复现步骤
1. 冷启动 Android（debug build）
2. ...

## 期望（对齐 RN 行为）
...

## 实际（Android 当前行为）
...

## Event Diff 关键片段
```json
<diff.json 节选>
```

## 修复指向
- 可疑入口：`feature:<module>/...`
- 关联 PluginApi 方法：`<method>`
- RN 对应实现：`../MusicFree/src/<path>`

## 元数据
- scenario: `<id>` (priority=`core|non-core`)
- run: `<run-id>` · severity: `<major|critical>`
- Android commit: `<sha>` · RN commit: `<sha>` · 设备: `<model>` API <level>
````

约束：顶部三行 HTML 注释**必须**存在，去重靠它们。LLM 不得在模板外加额外章节，"agent 备注"放进 `## 修复指向` bullet。

### 5.3 指纹 hash

```python
fingerprint = sha1(
    scenario_id + "|" +
    kind + "|" +
    primary_signal(diff_json)
)[:12]
```

`primary_signal` 取最具诊断价值的 diff 条目（优先级：`error > play.state > plugin.method > net > nav > ui_only`），用 `event_kind + verdict + field` 拼成稳定串。同 scenario 同类差异共享指纹（一个 bug 不拆成多 Issue）。

### 5.4 去重 / 复现 / 回归

```
gh issue list --state all \
  --search 'parity-fingerprint:<hash> in:body repo:<owner>/<repo>' \
  --json number,state,title,labels
```

| 匹配 | 动作 |
|---|---|
| 未命中 | `gh issue create` + 上传截图到 release + 写入 state |
| 命中 1 open | `gh issue comment <#>` 追加 "run <id> 仍复现"，含本轮截图 URL |
| 命中 1 closed | `gh issue reopen <#>` + comment "regression detected in run <id>" |
| 命中多个 | 全部跳过；REPORT 标 `manual_dedup_required` |

**自愈侧**：scenario 连续 2 轮 `passed` 且 `open_issue_numbers` 非空时，给每个 open Issue 评论"本 scenario 连续两轮通过（runs `<id1>`, `<id2>`），可能已修复，请人工 verify 后 close"。**agent 不自动 close**。

### 5.5 Labels

每个 Issue 必带：

| Label | 取值 |
|---|---|
| `parity-audit` | 总标签（固定） |
| `priority:<...>` | `priority:core` / `priority:non-core` |
| `area:<page>` | `area:home` / `area:search` / `area:player` / ... |
| `kind:<...>` | `kind:ui-gap` / `kind:logic-gap` / `kind:missing-feature` / `kind:crash` / `kind:perf` / `kind:error-divergence` |
| `severity:<...>` | `severity:major` / `severity:critical` |

按需附加：`needs-retry`、`flaky-flow`、`manual-dedup-required`。

agent 首次跑前用 `gh label create` 把缺失 label 自动创建（颜色模板写在 `references/labels.json`）。

### 5.6 截图上传

```bash
# 一次性（agent 检测不存在则创建）
gh release create parity-screenshots --prerelease \
  --title "Parity screenshot bucket" \
  --notes "Auto-managed by parity-audit-skill. Do not edit."

# 每轮：
gh release upload parity-screenshots \
  runs/<run-id>/scenarios/<id>/<side>/waypoint-XX.png \
  --clobber
```

文件名平铺：`<run-id>__<scenario-id>__<waypoint>__<side>.png`。URL：

```
https://github.com/<owner>/<repo>/releases/download/parity-screenshots/<filename>
```

只上传被 Issue body 真正引用的图（SSIM < 阈值的 waypoint），避免 release 资产无限膨胀。

## §6 自我可观测性、安全边界、跨工具兼容

### 6.1 agent.log.jsonl

每轮 `docs/parity-audit/runs/<run-id>/agent.log.jsonl` 落 agent 自己的事件流：

```jsonl
{"ts":"...","phase":"preflight","event":"rn_build_started"}
{"ts":"...","phase":"preflight","event":"rn_build_ok","duration_ms":48230}
{"ts":"...","phase":"scenario","scenario":"home_recommend_first_load","event":"flow_started","side":"rn"}
{"ts":"...","phase":"scenario","scenario":"...","event":"diff_computed","verdict":"diff_found","severity":"major"}
{"ts":"...","phase":"issue","event":"fingerprint_lookup","hash":"abc123def456","matched_count":0}
{"ts":"...","phase":"issue","event":"issue_created","number":143}
{"ts":"...","phase":"end","event":"state_persisted"}
```

REPORT.md 末尾自动嵌入"本轮 agent 操作摘要"。`mode=replay:<run-id>` 时可仅重跑分析/Issue 步骤。

### 6.2 安全边界

**允许**：
- 构建/安装/卸载 APK（限于 RN appId 与 Android debug appId）
- 写/修/追加 `docs/parity-audit/scenarios/` 与 `maestro/flows/parity/`
- 创建/上传 release 资产到 `parity-screenshots`
- 创建 Issue、追加评论、reopen Issue、创建 label
- 写 `docs/parity-audit/` 下任何文件并 commit
- 触发本地 ADB / Maestro 命令

**禁止**：
- close 任何 Issue（自愈侧只评论提醒）
- 删除已有 scenario 文件（只能 `superseded_by`）
- 改 scenario `priority`
- 写仓库以外的业务代码（`feature:*` / `core` / `data` / `player` / `plugin` 等都属禁区）
- 修改 `../MusicFree` 源码树（RN 补丁以 patch 文件形式留给人）
- `--no-verify` / `--force` 等绕过 git hook 的开关
- 在 `main` 分支上跑 audit（必须在 worktree 分支跑，遵守 `AGENTS.md` 的 worktree 约束）

### 6.3 跨工具兼容（Claude Code + Codex）

- SKILL.md 描述用工具中性词："read file" / "run shell" / "list files"，不写 "Read tool" / "Bash tool"
- 决定性步骤全部走 `scripts/parity-audit/*.sh|*.py`，CLI 入参与 stdout JSON 输出固定，LLM 不必关心实现
- Codex 通过 `.codex/skills/parity-audit-skill` 软链消费同一份 SKILL.md
- 工具差异点（task 跟踪、background process 等）写在 `references/tool-host-notes.md`，分两个 collapsible 子段列两边等价命令

### 6.4 失败兜底总览

| 触发位 | 分类 | agent 动作 |
|---|---|---|
| 构建失败 | `preflight_failed` | 终止本轮，state 不动，REPORT 写失败原因 |
| Maestro flow 缺/坏 | `blocked_authoring` | 标 scenario `flow_health=blocked_authoring`，建议人工介入；不建 Issue |
| 设备掉线 | abort run | 立即终止；下次重跑时优先级靠前 |
| 网络抖动 | `network_error` | scenario 标 `flaky`；连续 3 轮 flaky 才升级为 Issue（`kind=error-divergence` + `needs-retry`） |
| RN 单边崩 | `rn_baseline_unstable` | REPORT 高亮；不建 Issue |
| Android 单边崩 | `kind=crash, severity=critical` | 立即建 Issue，含 ANR/Tombstone 摘录 |
| 双边都崩 | `both_unstable` | REPORT 高亮；不建 Issue（人工裁决） |
| fingerprint 多匹配 | `manual_dedup_required` | 跳过；REPORT 列待清理 Issue 号 |

## 实现路线图（交接 writing-plans）

| 阶段 | 范围 | 验收 |
|---|---|---|
| v0 | SKILL.md 骨架 + state/queue 文件结构 + 一个手写 scenario（home 入口）端到端跑通 | `mode=dry-run scope=page:home limit=1` 跑出 `diff.json`，不建 Issue |
| v1 | Phase 1 自发生成 19 页 entry scenario；preflight + log diff + Issue 建立 + 去重 | 真实跑一轮 `scope=core`，至少产 3 条有效 Issue 草稿 |
| v2 | Phase 2 增量探查、自愈侧提醒评论、`mode=replay` | 连续 2 轮 passed 触发提醒评论；replay 跑通 |
| v3 | RN parity logger 补丁 + patched-mode 自动检测；Maestro 在 CI 上定时执行 | CI workflow 每周一/四定时跑 |

## Open Questions

- SSIM 0.92 阈值是经验猜测，需在 v1 跑 3 轮后用样本回调
- `parity-plugins.json` 初始集合需在 v0 验证哪些 RN 默认插件仍可用
- Maestro 自动合成 flow 的成功率未知；v0 需有"自动合成 < 60% scenario 成功时回滚到人工种子 1–2 个"应急
- v2 之前不实现 perf 类 diff 的"基线学习"；先按硬阈值
- agent 是否需要在 v1 内集成"运行成本预算上限"（如 token / wall-clock）以防失控；待 v0 实测后决定
