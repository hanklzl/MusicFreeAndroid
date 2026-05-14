# Parity Audit Agent Implementation Plan（管道 + Issue 创建）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md` 设计的 parity audit 流水线一次落到"管道全跑通 + 真正创建 Issue"状态——
- `mode=dry-run`：build RN+Android → 装两侧 APK → 跑 home_entry scenario → 抓截图+logcat → 输出 diff.json + issue.md 草稿 + REPORT.md + 更新 state.json，**不**真正调 `gh issue create`、**不**上传截图。
- `mode=audit`：在 dry-run 流程基础上，对 `verdict=diff_found && severity∈{major,critical}` 的 scenario，上传必要截图到 `parity-screenshots` release 资产桶 → 计算指纹 hash → `gh issue list` 查重 → 按"未命中创建 / 单匹配 open 评论 / 单匹配 closed reopen / 多匹配跳过"分支执行，并自动管理 label。

**Architecture:** Skill 是入口文档（位于 `.agents/skills/parity-audit-skill/`），背后的确定性步骤由 `scripts/parity-audit/*.{sh,py}` 承载，事件源来自 `:logging` 模块新增的 `ParityEventSink` + RN 默认 logcat 正则解析；Maestro flow 文件本期由人工手写（仅 home_entry 一个 scenario），Phase 1/2 自发生成 scenario 留给后续 plan。状态、scenario catalog、运行产物全部落在 `docs/parity-audit/` 下，便于 review 与历史回溯。Issue 创建侧的 `file_issue.py` 把"判定 action + 构造 gh 命令"与"实际 subprocess 执行"严格分层，可在 TDD 中只测前者。

**Tech Stack:** Bash + Python 3 (`scikit-image` / `opencv-python` for SSIM)、Maestro CLI、ADB、Kotlin (`:logging` 模块)、`gh` CLI（label 管理、release 上传、Issue 创建/评论/reopen 全走 gh）。

**Spec 参照**：`docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`（commit `524214c0` 之后版本）。本 plan 引用的 §x.y 编号均指 spec 编号。

---

## File Structure（本期范围）

```
.agents/skills/parity-audit-skill/
  SKILL.md                              # 入口文档（工具中性）
  references/
    event-taxonomy.md                   # 6 类事件 schema
    rn-logcat-parser.json               # RN 默认 logcat → taxonomy 子集映射
    failure-modes.md                    # 决策矩阵
    tool-host-notes.md                  # Claude Code / Codex 等价命令
    issue-template.md                   # GitHub Issue body 模板（含指纹占位）
    labels.json                         # 首次跑要创建的 label 清单（颜色 + 描述）

scripts/parity-audit/
  bootstrap.sh                          # 校验 adb / Maestro / gh / python deps
  build-rn.sh                           # cd ../MusicFree && yarn && cd android && ./gradlew assembleDebug
  build-android.sh                      # ./gradlew :app:assembleDebug
  install-both.sh                       # adb install -r 两侧 APK
  install-plugins.sh                    # 调 Maestro flow 装 parity-plugins.json 中插件
  run-scenario.sh                       # 跑指定 scenario 的 RN flow + Android flow，落产物
  screenshot_ssim.py                    # 计算 SSIM，输出 JSON
  parse_events.py                       # logcat raw → events.jsonl
  compare.py                            # 两路 events.jsonl + screenshots → diff.json
  state.py                              # 读写 state.json + queue.md
  ensure-labels.sh                      # 幂等创建 labels.json 中所有 label
  ensure-release.sh                     # 幂等创建 parity-screenshots release 资产桶
  upload-screenshots.sh                 # gh release upload parity-screenshots <files> --clobber
  file_issue.py                         # 指纹 / 模板渲染 / 查重 / 决定 action / gh 调用
  audit.sh                              # 顶层入口：解析 --scope/--mode/--limit/--device，串起以上

logging/src/main/java/com/zili/android/musicfreeandroid/logging/
  ParityEventSink.kt                    # 新增：把 MfLog 调用映射为 PARITY_EVT 结构化日志

docs/parity-audit/
  state.json                            # 初始空 catalog
  queue.md                              # 初始模板
  parity-plugins.json                   # v0 固定插件集（先 1 个）
  scenarios/
    home_entry.yaml                     # v0 唯一手写 scenario

maestro/flows/parity/
  _lib/
    mark_waypoint.js                    # adb shell log -t PARITY_MARK
    dump_hierarchy.yaml                 # uiautomator dump + adb pull
  _bootstrap/
    install-plugins.rn.yaml             # RN 侧装插件 flow
    install-plugins.android.yaml        # Android 侧装插件 flow
  home_entry.rn.yaml                    # v0 scenario flow（RN）
  home_entry.android.yaml               # v0 scenario flow（Android）
```

不在本期范围（明确推迟到后续 plan）：
- `references/scenario-authoring.md`（自发生成）
- Phase 1/2 自发生成 scenario
- 自愈侧"连续 N 轮 passed → comment 提醒"逻辑
- `mode=replay:<run-id>`
- RN parity logger 补丁 + patched-mode 探测（spec v3）
- `apply_rn_patch.sh`

---

## 前置准备（Task 0）

在开始 Task 1 前，确认本地已具备：

- macOS / Linux 开发机
- ADB 与至少一个 Android 模拟器或真机已连接 (`adb devices` 至少返回一行)
- Maestro CLI 已安装 (`curl -Ls "https://get.maestro.mobile.dev" | bash` 或同等方式)
- `gh` CLI 已安装并 `gh auth status` 通过
- Python 3.10+，且 `pip install scikit-image opencv-python-headless pyyaml` 可成功
- `../MusicFree` 目录存在且 `cd ../MusicFree && yarn` 可成功（首次跑 yarn 可能很慢）
- Android `./gradlew :app:assembleDebug` 在 main 上可成功

按 `AGENTS.md` 的 worktree 约定，本 plan 应在独立 worktree 跑：

```bash
git -C /Users/zili/code/android/MusicFreeAndroid worktree add .worktrees/parity-audit-v0 -b parity-audit-v0
cd /Users/zili/code/android/MusicFreeAndroid/.worktrees/parity-audit-v0
```

后续所有命令默认在该 worktree 的根目录执行。

---

## Task 1: Skill 骨架与 references 占位

**Files:**
- Create: `.agents/skills/parity-audit-skill/SKILL.md`
- Create: `.agents/skills/parity-audit-skill/references/event-taxonomy.md`
- Create: `.agents/skills/parity-audit-skill/references/rn-logcat-parser.json`
- Create: `.agents/skills/parity-audit-skill/references/failure-modes.md`
- Create: `.agents/skills/parity-audit-skill/references/tool-host-notes.md`

- [ ] **Step 1: 写 SKILL.md 入口文档**

完整内容（不留 TODO）：

````markdown
---
name: parity-audit-skill
description: 跨工具调用的 RN/Android parity 审计 sub-agent。触发短语："跑 parity audit"、"RN/Android 对齐扫描"、"对齐审计"、"parity-audit"。
---

# Parity Audit Skill

## 用途

把 RN MusicFree（参考实现）与 MusicFreeAndroid 在编译产物上的行为差异流水线化抓取并归集。`mode=audit` 时直接 `gh issue create`；`mode=dry-run` 时仅生成 `issue.md` 草稿。

## 调用契约

| 参数 | 取值 | 默认 |
|---|---|---|
| `--scope` | `core` / `non-core` / `all` / `page:<id>` | 读 `docs/parity-audit/state.json.next_recommended` |
| `--mode` | `audit` / `dry-run` | `audit`。后续 plan 会补 `replay:<run-id>` |
| `--limit` | 整数 | `5` |
| `--device` | adb serial | 取 `adb devices` 第一行 |

## 调用流程

1. 读 `docs/parity-audit/state.json` 与 `queue.md`
2. 检查当前 git 分支不是 `main`（在 `main` 上拒绝执行，提示切到 worktree）
3. 跑 `scripts/parity-audit/audit.sh --scope <...> --mode <...> --limit <...>`
4. audit.sh 内部串联：bootstrap → build → install → install-plugins → 循环 scenario(run-scenario → parse-events → screenshot-ssim → compare → 渲染 issue.md → 若 mode=audit 则上传截图+提交 Issue) → state 更新 → REPORT.md

## 强约束

- 任何 Maestro flow 缺失 / 构建失败 / 设备未连，必须把 scenario 标 `blocked_*` 写入 state，不能沉默跳过
- 在 `main` 分支拒绝执行
- `mode=dry-run` 阶段任何"创建 Issue"动作都视为 bug——必须只到 `issue.md` 草稿落盘为止
- 真实创建 Issue 前必须走指纹查重；同指纹 open Issue 只准追加评论，**禁止**重复创建

## 失败矩阵

详见 `references/failure-modes.md`。

## 工具差异点

详见 `references/tool-host-notes.md`，分 Claude Code 与 Codex 两段。
````

- [ ] **Step 2: 写 event-taxonomy.md**

```markdown
# Event Taxonomy v1

所有 parity diff 仅针对规范化事件。事件 JSON 行格式：

```json
{"kind": "<kind>", "ts_ms": <int>, "waypoint": "<id>", "side": "rn|android", "fields": {...}}
```

`waypoint` 由 `PARITY_MARK BEGIN <id>` / `PARITY_MARK END <id>` 锚点切分。

## 6 类事件

| kind | fields | 触发点 |
|---|---|---|
| `nav.enter` | `route`, `params_hash` | 进入页面 |
| `nav.leave` | `route` | 离开页面 |
| `plugin.method_called` | `plugin_id`, `method`, `args_hash` | PluginApi 14 个方法 |
| `plugin.method_returned` | `plugin_id`, `method`, `ok`, `result_summary`, `duration_ms` | 同上对应 |
| `net.request` | `url_template`, `method` | OkHttp / fetch 发起 |
| `net.response` | `url_template`, `method`, `status`, `duration_ms` | 同上回包 |
| `play.state_changed` | `from`, `to`, `track_id_hash` | ExoPlayer / RN trackPlayer |
| `error` | `domain`, `code`, `message_hash` | 异常 / Toast / 业务降级 |

## 字段哈希化

- `args_hash` / `params_hash` / `track_id_hash` / `message_hash`：用 `sha1(<canonical_string>)[:8]`
- `url_template`：把 query/path 里的随机或时间相关参数替换为占位（`?q=*` / `&_t=*`）

## Android 侧来源

`logging` 模块的 `ParityEventSink` 把 `MfLog.info/error/...` 调用按 taxonomy 映射为 `Log.println(Log.INFO, "PARITY_EVT", json)`。

## RN 侧来源

- 默认模式：`references/rn-logcat-parser.json` 用正则匹配 RN 自有 console.log
- patched 模式（v3）：RN 注入 `parityLogger.ts` 输出 `[PARITY_EVT]` 前缀
```

- [ ] **Step 3: 写 rn-logcat-parser.json**

```json
{
  "version": 1,
  "patterns": [
    {
      "kind": "plugin.method_called",
      "regex": "\\[PluginManager\\] call (?P<plugin_id>[^\\s]+)\\.(?P<method>[^\\s]+) with (?P<args>.+)",
      "fields_template": {"plugin_id": "$plugin_id", "method": "$method", "args_hash": "$args|sha1_8"}
    },
    {
      "kind": "play.state_changed",
      "regex": "\\[TrackPlayer\\] state transition: (?P<from>[A-Z_]+) -> (?P<to>[A-Z_]+)",
      "fields_template": {"from": "$from", "to": "$to"}
    },
    {
      "kind": "nav.enter",
      "regex": "\\[Navigation\\] navigate to (?P<route>[A-Za-z]+)(?: with (?P<params>.+))?",
      "fields_template": {"route": "$route", "params_hash": "$params|sha1_8"}
    }
  ],
  "notes": "v0 起步只覆盖三类；更多正则在 v1 真跑过 RN 后补。未匹配的 logcat 行被忽略。"
}
```

- [ ] **Step 4: 写 failure-modes.md**

```markdown
# Failure Modes

| 现象 | 状态码 | 处置 |
|---|---|---|
| `adb devices` 无设备 | exit 2 | audit.sh 立即终止，REPORT 写"no device" |
| Maestro / gh / python deps 缺失 | exit 2 | 同上 |
| `cd ../MusicFree && yarn` 失败 | exit 3 | `preflight_failed=rn_build`，本轮终止 |
| `./gradlew :app:assembleDebug` 失败 | exit 4 | `preflight_failed=android_build`，本轮终止 |
| `adb install -r` 失败 | exit 5 | `preflight_failed=install`，本轮终止 |
| install-plugins flow 失败 | exit 6 | `plugin_bootstrap_failed`，本轮终止 |
| 单 scenario Maestro flow timeout / 找不到元素 | scenario 标 `blocked_runtime`，继续下一个 | 不让整轮跑挂 |
| ADB 中途掉线 | abort 整轮 | exit 7 |
| RN 进程崩 | scenario 状态 `rn_baseline_unstable` | 不建 Issue，REPORT 高亮 |
| Android 进程崩 / ANR | scenario 状态 `diff_found severity=critical kind=crash` | `mode=audit` 时创建 Issue，`mode=dry-run` 时仅落 issue.md 草稿 |

`mode=dry-run` 时所有 Issue 触发条件等价于"生成 issue.md 草稿"。`mode=audit` 时按 spec §5.1 直接 `gh issue create`，并走指纹查重。
```

- [ ] **Step 5: 写 tool-host-notes.md**

```markdown
# Tool Host Notes

## Claude Code

- 用 Bash 工具执行 `scripts/parity-audit/audit.sh`
- 任务跟踪用 TaskCreate / TaskUpdate
- 长时间步骤（构建、Maestro 跑 flow）建议 `run_in_background=true`

## Codex CLI

- 同样直接 `bash scripts/parity-audit/audit.sh`
- 任务跟踪走 Codex 自带 TODO 机制
- 长时间步骤目前没有原生后台机制，用 `nohup ... &` + `wait`

无论哪个工具，决策全部走 `scripts/parity-audit/*` 的 CLI；LLM 只需读 stdout JSON。
```

- [ ] **Step 6: Commit**

```bash
git add .agents/skills/parity-audit-skill/
git commit -m "feat(parity-audit): scaffold parity-audit-skill 骨架与 references"
```

---

## Task 2: 软链 skill 到 .claude / .codex

**Files:**
- Create symlink: `.claude/skills/parity-audit-skill -> ../../.agents/skills/parity-audit-skill`
- Create symlink: `.codex/skills/parity-audit-skill -> ../../.agents/skills/parity-audit-skill`

- [ ] **Step 1: 验证 .codex/skills/ 目录存在或创建**

```bash
ls -la .codex/skills/ 2>/dev/null || mkdir -p .codex/skills
```

- [ ] **Step 2: 建立软链**

```bash
ln -sfn ../../.agents/skills/parity-audit-skill .claude/skills/parity-audit-skill
ln -sfn ../../.agents/skills/parity-audit-skill .codex/skills/parity-audit-skill
```

- [ ] **Step 3: 用现有 symlinks-check.sh 验证**

Run: `bash scripts/dev-harness/symlinks-check.sh`
Expected: 通过（exit 0）。如果脚本不识别 parity-audit-skill，按其规则补一条白名单或同等格式。

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/parity-audit-skill .codex/skills/parity-audit-skill
git commit -m "feat(parity-audit): symlink skill 到 .claude 与 .codex"
```

---

## Task 3: 初始化 docs/parity-audit/ 目录骨架

**Files:**
- Create: `docs/parity-audit/state.json`
- Create: `docs/parity-audit/queue.md`
- Create: `docs/parity-audit/parity-plugins.json`
- Create: `docs/parity-audit/README.md`

- [ ] **Step 1: 写 state.json 初始内容**

```json
{
  "schema_version": 1,
  "updated_at": "2026-05-15T00:00:00+08:00",
  "scenarios": {},
  "next_recommended": []
}
```

- [ ] **Step 2: 写 queue.md 模板**

```markdown
# Parity Audit Queue

> 由 `scripts/parity-audit/state.py` 每轮重写。手工修改会在下一轮被覆盖。

| scenario | priority | last_run | status | open_issues | next_up? |
|---|---|---|---|---|---|
| _(no runs yet)_ |  |  |  |  |  |
```

- [ ] **Step 3: 写 parity-plugins.json（v0 用 1 个最低成本插件）**

```json
{
  "schema_version": 1,
  "plugins": [
    {
      "id": "wy",
      "display_name": "网易云（v0 测试基线）",
      "subscription_url": "https://gitee.com/maotoumao/MusicFreePlugins/raw/master/dist/wy/index.js",
      "notes": "v0 仅用该插件验证 home 推荐位 + 搜索链路。"
    }
  ]
}
```

> 注意：上面这条 subscription_url 仅作为 v0 占位，**任务执行者必须在 Task 14 真跑 audit 前**用社区当前可用的镜像 URL 替换；若该插件 v0 期间已不可用，回退到 `app/src/main/.../bootstrap` 的默认插件 URL。

- [ ] **Step 4: 写 README.md 解释目录用途**

```markdown
# docs/parity-audit/

由 `parity-audit-skill` 管理的状态、scenario catalog 与运行产物。

- `state.json` — 机读状态（唯一可信源），由 `scripts/parity-audit/state.py` 读写
- `queue.md` — 人读队列，每轮重写
- `parity-plugins.json` — 双端共享固定插件集
- `scenarios/` — scenario YAML，v0 仅 `home_entry.yaml`
- `runs/<YYYY-MM-DD-HHMM>/` — 每轮运行产物，含截图、events.jsonl、diff.json、issue.md 草稿、REPORT.md

详见 spec：`docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`。
```

- [ ] **Step 5: Commit**

```bash
git add docs/parity-audit/
git commit -m "feat(parity-audit): 初始化 docs/parity-audit 目录骨架"
```

---

## Task 4: state.py 读写 state.json + queue.md（TDD）

**Files:**
- Create: `scripts/parity-audit/state.py`
- Create: `scripts/parity-audit/tests/test_state.py`

- [ ] **Step 1: 写失败测试**

```python
# scripts/parity-audit/tests/test_state.py
import json
import os
import pathlib
import tempfile
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import state


def test_load_initial_state(tmp_path):
    p = tmp_path / "state.json"
    p.write_text(json.dumps({
        "schema_version": 1,
        "updated_at": "2026-05-15T00:00:00+08:00",
        "scenarios": {},
        "next_recommended": []
    }))
    s = state.load(p)
    assert s.scenarios == {}
    assert s.next_recommended == []


def test_upsert_scenario_status(tmp_path):
    p = tmp_path / "state.json"
    p.write_text(json.dumps({
        "schema_version": 1,
        "updated_at": "2026-05-15T00:00:00+08:00",
        "scenarios": {},
        "next_recommended": []
    }))
    s = state.load(p)
    state.upsert_scenario(s, "home_entry",
                          priority="core",
                          last_run_id="2026-05-15-1200",
                          last_status="passed",
                          open_issue_numbers=[],
                          blocked_reason=None)
    state.save(s, p)

    raw = json.loads(p.read_text())
    assert raw["scenarios"]["home_entry"]["last_status"] == "passed"
    assert raw["scenarios"]["home_entry"]["priority"] == "core"
    assert raw["scenarios"]["home_entry"]["consecutive_pass_count"] == 1


def test_consecutive_pass_count_resets_on_diff(tmp_path):
    p = tmp_path / "state.json"
    p.write_text(json.dumps({
        "schema_version": 1,
        "updated_at": "2026-05-15T00:00:00+08:00",
        "scenarios": {
            "home_entry": {
                "priority": "core",
                "last_run_id": "2026-05-15-1100",
                "last_status": "passed",
                "open_issue_numbers": [],
                "blocked_reason": None,
                "consecutive_pass_count": 2
            }
        },
        "next_recommended": []
    }))
    s = state.load(p)
    state.upsert_scenario(s, "home_entry",
                          priority="core",
                          last_run_id="2026-05-15-1200",
                          last_status="diff_found",
                          open_issue_numbers=[],
                          blocked_reason=None)
    assert s.scenarios["home_entry"]["consecutive_pass_count"] == 0


def test_render_queue_md(tmp_path):
    p = tmp_path / "state.json"
    p.write_text(json.dumps({
        "schema_version": 1,
        "updated_at": "2026-05-15T00:00:00+08:00",
        "scenarios": {
            "home_entry": {
                "priority": "core",
                "last_run_id": "2026-05-15-1200",
                "last_status": "passed",
                "open_issue_numbers": [],
                "blocked_reason": None,
                "consecutive_pass_count": 1
            }
        },
        "next_recommended": ["home_entry"]
    }))
    s = state.load(p)
    md = state.render_queue_md(s)
    assert "home_entry" in md
    assert "core" in md
    assert "passed" in md
    assert "✓ next" in md or "next" in md
```

- [ ] **Step 2: 跑测试确认失败**

Run: `python3 -m pytest scripts/parity-audit/tests/test_state.py -v`
Expected: 全部 FAIL，模块 `state` 不存在。

- [ ] **Step 3: 实现 state.py**

```python
# scripts/parity-audit/state.py
"""Read / write docs/parity-audit/state.json and queue.md."""
from __future__ import annotations

import dataclasses
import datetime as dt
import json
import pathlib
from typing import Any


@dataclasses.dataclass
class State:
    schema_version: int
    updated_at: str
    scenarios: dict[str, dict[str, Any]]
    next_recommended: list[str]


def load(path: pathlib.Path) -> State:
    raw = json.loads(path.read_text())
    return State(
        schema_version=raw["schema_version"],
        updated_at=raw["updated_at"],
        scenarios=dict(raw.get("scenarios", {})),
        next_recommended=list(raw.get("next_recommended", [])),
    )


def save(state: State, path: pathlib.Path) -> None:
    state.updated_at = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(
        timespec="seconds"
    )
    payload = {
        "schema_version": state.schema_version,
        "updated_at": state.updated_at,
        "scenarios": state.scenarios,
        "next_recommended": state.next_recommended,
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def upsert_scenario(
    state: State,
    scenario_id: str,
    *,
    priority: str,
    last_run_id: str,
    last_status: str,
    open_issue_numbers: list[int],
    blocked_reason: str | None,
) -> None:
    prev = state.scenarios.get(scenario_id)
    prev_pass = (prev or {}).get("consecutive_pass_count", 0) if prev else 0
    if last_status == "passed":
        new_pass = prev_pass + 1
    else:
        new_pass = 0
    state.scenarios[scenario_id] = {
        "priority": priority,
        "last_run_id": last_run_id,
        "last_status": last_status,
        "open_issue_numbers": list(open_issue_numbers),
        "blocked_reason": blocked_reason,
        "consecutive_pass_count": new_pass,
    }


def render_queue_md(state: State) -> str:
    lines = [
        "# Parity Audit Queue",
        "",
        "> 由 `scripts/parity-audit/state.py` 每轮重写。手工修改会在下一轮被覆盖。",
        "",
        "| scenario | priority | last_run | status | open_issues | next_up? |",
        "|---|---|---|---|---|---|",
    ]
    next_set = set(state.next_recommended)
    if not state.scenarios:
        lines.append("| _(no runs yet)_ |  |  |  |  |  |")
    else:
        for sid in sorted(state.scenarios.keys()):
            sc = state.scenarios[sid]
            marker = "✓ next" if sid in next_set else ""
            issues = ", ".join(f"#{n}" for n in sc.get("open_issue_numbers", []))
            lines.append(
                f"| {sid} | {sc.get('priority','?')} | "
                f"{sc.get('last_run_id') or '—'} | {sc.get('last_status','?')} | "
                f"{issues or '—'} | {marker} |"
            )
    return "\n".join(lines) + "\n"
```

- [ ] **Step 4: 跑测试确认通过**

Run: `python3 -m pytest scripts/parity-audit/tests/test_state.py -v`
Expected: 4 个测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add scripts/parity-audit/state.py scripts/parity-audit/tests/test_state.py
git commit -m "feat(parity-audit): state.py 读写 state.json 与 queue.md（含 TDD）"
```

---

## Task 5: bootstrap.sh 环境校验

**Files:**
- Create: `scripts/parity-audit/bootstrap.sh`

- [ ] **Step 1: 写脚本**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/bootstrap.sh
# 校验 parity-audit 所需的本地工具与设备。失败立即 exit。

set -euo pipefail

err() { echo "ERROR: $*" >&2; exit 2; }

command -v adb     >/dev/null 2>&1 || err "adb not found in PATH"
command -v maestro >/dev/null 2>&1 || err "maestro not found in PATH; install: https://maestro.mobile.dev/"
command -v gh      >/dev/null 2>&1 || err "gh (GitHub CLI) not found"
command -v python3 >/dev/null 2>&1 || err "python3 not found"

python3 - <<'PY' || err "python deps missing; pip install scikit-image opencv-python-headless pyyaml"
import importlib
for m in ("skimage", "cv2", "yaml"):
    importlib.import_module(m)
PY

DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
[ -n "$DEVICES" ] || err "no adb device connected"

[ -d "../MusicFree" ] || err "../MusicFree (RN reference repo) not found"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" = "main" ]; then
  err "refuse to run audit on main; use git worktree (see AGENTS.md)"
fi

echo "OK adb=$(adb version | head -n1)"
echo "OK maestro=$(maestro --version 2>&1 | head -n1)"
echo "OK device(s): $DEVICES"
echo "OK branch=$BRANCH (not main)"
```

- [ ] **Step 2: 加可执行位**

```bash
chmod +x scripts/parity-audit/bootstrap.sh
```

- [ ] **Step 3: 手动验证**

Run: `bash scripts/parity-audit/bootstrap.sh`
Expected: 列出 adb / maestro / 设备 / branch 信息，exit 0。如缺工具或在 main 分支应 exit 2。

- [ ] **Step 4: Commit**

```bash
git add scripts/parity-audit/bootstrap.sh
git commit -m "feat(parity-audit): bootstrap.sh 校验本地工具与设备"
```

---

## Task 6: build-rn.sh / build-android.sh / install-both.sh

**Files:**
- Create: `scripts/parity-audit/build-rn.sh`
- Create: `scripts/parity-audit/build-android.sh`
- Create: `scripts/parity-audit/install-both.sh`

- [ ] **Step 1: 写 build-rn.sh**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/build-rn.sh
# 构建 RN MusicFree debug APK，输出路径打到 stdout。

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
RN_ROOT="$REPO_ROOT/../MusicFree"

[ -d "$RN_ROOT" ] || { echo "ERROR: $RN_ROOT not found" >&2; exit 3; }

cd "$RN_ROOT"
if [ ! -d node_modules ]; then
  yarn install --frozen-lockfile >&2
fi
cd android
./gradlew assembleDebug >&2

APK="$RN_ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERROR: built apk not found at $APK" >&2; exit 3; }
echo "$APK"
```

- [ ] **Step 2: 写 build-android.sh**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/build-android.sh

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
./gradlew :app:assembleDebug >&2

APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERROR: built apk not found at $APK" >&2; exit 4; }
echo "$APK"
```

- [ ] **Step 3: 写 install-both.sh**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/install-both.sh
# usage: install-both.sh <rn_apk> <android_apk> [device_serial]

set -euo pipefail

RN_APK="$1"
ANDROID_APK="$2"
DEVICE="${3:-}"

ADB="adb"
[ -n "$DEVICE" ] && ADB="adb -s $DEVICE"

$ADB install -r -t "$RN_APK" >&2
$ADB install -r -t "$ANDROID_APK" >&2

echo "installed:"
echo "  rn=fun.upup.musicfree"
echo "  android=com.zili.android.musicfreeandroid.debug"
```

- [ ] **Step 4: 加可执行位**

```bash
chmod +x scripts/parity-audit/build-rn.sh scripts/parity-audit/build-android.sh scripts/parity-audit/install-both.sh
```

- [ ] **Step 5: 手动验证两侧构建**

Run: `bash scripts/parity-audit/build-android.sh`
Expected: stdout 是 `app/build/outputs/apk/debug/app-debug.apk` 的绝对路径，exit 0。

Run: `bash scripts/parity-audit/build-rn.sh`
Expected: 第一次跑会很慢（yarn install），最终 stdout 是 `../MusicFree/android/app/build/outputs/apk/debug/app-debug.apk` 的绝对路径，exit 0。**若 RN 构建失败，必须排查到根因再继续；不要 fallback。**

Run: `bash scripts/parity-audit/install-both.sh <RN_APK_PATH> <ANDROID_APK_PATH>`
Expected: 两侧 APK 安装成功，stdout 列出 appId，exit 0。

- [ ] **Step 6: Commit**

```bash
git add scripts/parity-audit/build-rn.sh scripts/parity-audit/build-android.sh scripts/parity-audit/install-both.sh
git commit -m "feat(parity-audit): 构建与安装脚本（RN + Android debug APK）"
```

---

## Task 7: Maestro `_lib` 共享片段

**Files:**
- Create: `maestro/flows/parity/_lib/mark_waypoint.js`
- Create: `maestro/flows/parity/_lib/dump_hierarchy.yaml`

- [ ] **Step 1: 写 mark_waypoint.js**

```javascript
// maestro/flows/parity/_lib/mark_waypoint.js
// usage: runScript with env: { WAYPOINT, EDGE (BEGIN|END), DEVICE }
// 在 logcat 打稳定锚点，parse_events.py 据此切分 waypoint 窗口。

const waypoint = MAESTRO_ENV.WAYPOINT;
const edge     = MAESTRO_ENV.EDGE || "BEGIN";
const device   = MAESTRO_ENV.DEVICE ? `-s ${MAESTRO_ENV.DEVICE}` : "";

const cmd = `adb ${device} shell log -t PARITY_MARK "${edge} ${waypoint}"`;
const result = shell(cmd);
output.exitCode = result.exitCode;
```

- [ ] **Step 2: 写 dump_hierarchy.yaml**

```yaml
# maestro/flows/parity/_lib/dump_hierarchy.yaml
# usage: env: { WAYPOINT, SIDE (rn|android), RUN_ID, DEVICE, SCENARIO }

appId: "*"
---
- runScript:
    file: ./mark_waypoint.js
    env:
      WAYPOINT: "${WAYPOINT}"
      EDGE: "DUMP"
      DEVICE: "${DEVICE}"
- runScript:
    inline: |
      const device = MAESTRO_ENV.DEVICE ? `-s ${MAESTRO_ENV.DEVICE}` : "";
      const wp = MAESTRO_ENV.WAYPOINT;
      const side = MAESTRO_ENV.SIDE;
      const scenario = MAESTRO_ENV.SCENARIO;
      const runId = MAESTRO_ENV.RUN_ID;
      const dst = `docs/parity-audit/runs/${runId}/scenarios/${scenario}/${side}/waypoint-${wp}-hierarchy.xml`;
      const cmds = [
        `adb ${device} shell uiautomator dump /sdcard/parity-${wp}.xml`,
        `mkdir -p $(dirname ${dst})`,
        `adb ${device} pull /sdcard/parity-${wp}.xml ${dst}`,
        `adb ${device} shell rm /sdcard/parity-${wp}.xml`
      ];
      for (const c of cmds) {
        const r = shell(c);
        if (r.exitCode !== 0) {
          output.error = r.stderr;
          break;
        }
      }
```

- [ ] **Step 3: 手动 sanity check**

不真跑 Maestro，仅 lint：

Run: `maestro test --help` 验证 Maestro CLI 可用；
Run: `cat maestro/flows/parity/_lib/dump_hierarchy.yaml | head` 确认 YAML 结构。

无 Maestro 静态校验工具，先不真跑——后续 Task 10 / Task 14 在端到端流程里验证。

- [ ] **Step 4: Commit**

```bash
git add maestro/flows/parity/_lib/
git commit -m "feat(parity-audit): Maestro _lib 共享片段 (waypoint 锚点 + hierarchy dump)"
```

---

## Task 8: install-plugins 共享 flow + install-plugins.sh

**Files:**
- Create: `maestro/flows/parity/_bootstrap/install-plugins.rn.yaml`
- Create: `maestro/flows/parity/_bootstrap/install-plugins.android.yaml`
- Create: `scripts/parity-audit/install-plugins.sh`

> 说明：v0 这两个 flow 需要在真机上探查 RN/Android 安装插件的实际入口路径才能写对。本任务先落"骨架版"——以"打开 app → 进设置 → 找插件管理 → 粘贴 URL → 确认"为大纲，但具体 selector 留给任务执行者**在真机/模拟器上 dump hierarchy 后填**。

- [ ] **Step 1: 探查 Android 侧"插件管理 → 从订阅安装"路径**

在已装好两侧 APK 的模拟器上手动操作 Android 应用：
1. 启动 `com.zili.android.musicfreeandroid.debug`
2. 进入设置 → 插件管理 → 粘贴订阅 URL 安装

每步记录可用 selector（优先 `text:` 完全匹配，次选 `id:`）。把这份 selector 序列写到 `install-plugins.android.yaml`：

```yaml
# maestro/flows/parity/_bootstrap/install-plugins.android.yaml
# env: { PLUGIN_URL }
appId: com.zili.android.musicfreeandroid.debug
---
- launchApp:
    clearState: true
- waitForAnimationToEnd:
    timeout: 8000
# 以下 selector 需在真机/模拟器上根据 uiautomator dump 替换为实际可用项
- tapOn:
    text: "设置"
- tapOn:
    text: "插件管理"
- tapOn:
    text: "从订阅安装"
- inputText: "${PLUGIN_URL}"
- tapOn:
    text: "确认"
- waitForAnimationToEnd:
    timeout: 10000
- assertVisible:
    text: "安装成功"
    optional: true
```

如果实际入口与上述步骤不一致，**重写到真实匹配的 selector**，并把改动写一句到 commit message。

- [ ] **Step 2: 探查 RN 侧同样路径**

同上，启动 `fun.upup.musicfree`，找到"设置 → 插件管理 → 从订阅安装"对应路径，落 `install-plugins.rn.yaml`：

```yaml
# maestro/flows/parity/_bootstrap/install-plugins.rn.yaml
# env: { PLUGIN_URL }
appId: fun.upup.musicfree
---
- launchApp:
    clearState: true
- waitForAnimationToEnd:
    timeout: 8000
# 占位 selector，必须按真机实际 dump 改写
- tapOn:
    text: "设置"
- tapOn:
    text: "插件管理"
- tapOn:
    text: "从订阅安装"
- inputText: "${PLUGIN_URL}"
- tapOn:
    text: "确认"
- waitForAnimationToEnd:
    timeout: 10000
- assertVisible:
    text: "安装成功"
    optional: true
```

- [ ] **Step 3: 写 install-plugins.sh**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/install-plugins.sh
# 把 parity-plugins.json 中每个插件订阅 URL 喂给两侧 Maestro flow。

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
PLUGINS_JSON="$REPO_ROOT/docs/parity-audit/parity-plugins.json"
DEVICE="${1:-}"

DEVICE_ARG=""
[ -n "$DEVICE" ] && DEVICE_ARG="--device $DEVICE"

URLS=$(python3 -c '
import json, sys
data = json.load(open(sys.argv[1]))
for p in data.get("plugins", []):
    print(p["subscription_url"])
' "$PLUGINS_JSON")

while IFS= read -r url; do
  [ -z "$url" ] && continue
  echo "==> installing $url on RN side" >&2
  maestro $DEVICE_ARG test \
    -e PLUGIN_URL="$url" \
    maestro/flows/parity/_bootstrap/install-plugins.rn.yaml
  echo "==> installing $url on Android side" >&2
  maestro $DEVICE_ARG test \
    -e PLUGIN_URL="$url" \
    maestro/flows/parity/_bootstrap/install-plugins.android.yaml
done <<<"$URLS"
```

- [ ] **Step 4: 加可执行位**

```bash
chmod +x scripts/parity-audit/install-plugins.sh
```

- [ ] **Step 5: 手动跑一次确认**

Run: `bash scripts/parity-audit/install-plugins.sh`
Expected: Maestro 两侧 flow 都跑通，两 app 都装上 parity-plugins.json 中插件。任一 flow 失败 → 回到 Step 1 / 2 修 selector。

- [ ] **Step 6: Commit**

```bash
git add maestro/flows/parity/_bootstrap/ scripts/parity-audit/install-plugins.sh
git commit -m "feat(parity-audit): install-plugins 共享 flow 与编排脚本"
```

---

## Task 9: home_entry scenario YAML + Maestro flow（手写 v0 唯一 scenario）

**Files:**
- Create: `docs/parity-audit/scenarios/home_entry.yaml`
- Create: `maestro/flows/parity/home_entry.rn.yaml`
- Create: `maestro/flows/parity/home_entry.android.yaml`

- [ ] **Step 1: 写 scenario YAML**

```yaml
# docs/parity-audit/scenarios/home_entry.yaml
id: home_entry
title: 首页冷启动落地
priority: core
page: home
rn_appid: fun.upup.musicfree
android_appid: com.zili.android.musicfreeandroid.debug
rn_flow: maestro/flows/parity/home_entry.rn.yaml
android_flow: maestro/flows/parity/home_entry.android.yaml
waypoints:
  - id: 01_after_cold_start
    description: 冷启动后落地首页，等动画结束
  - id: 02_after_recommend_loaded
    description: 等推荐位完成首屏渲染（最长 8s）
expected_events: []   # v0 不强校验，先看输出是什么
diff_tolerance:
  screenshot_ssim_min: 0.92
  ignore_event_fields: [ts_ms, request_id]
authored_by: human   # v0 唯一手写的 scenario
authored_run_id: null
flow_health:
  rn: unknown
  android: unknown
  last_validated_run_id: null
notes: |
  v0 唯一手写 scenario。RN 入口 ../MusicFree/src/pages/home，Android 入口 feature:home。
  上层 audit.sh 调用 mode=dry-run，本 scenario 不期望建 Issue。
```

- [ ] **Step 2: 写 home_entry.rn.yaml**

> 同 Task 8 Step 1：以下 selector 是骨架，**任务执行者必须在真机/模拟器上 dump hierarchy 后核对/替换**。

```yaml
# maestro/flows/parity/home_entry.rn.yaml
# env: { RUN_ID, SCENARIO, DEVICE }
appId: fun.upup.musicfree
---
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "00_pre_launch", EDGE: "BEGIN", DEVICE: "${DEVICE}" }

- launchApp:
    clearState: false           # 假定 install-plugins 已装好；不清数据

- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "01_after_cold_start", EDGE: "BEGIN", DEVICE: "${DEVICE}" }
- waitForAnimationToEnd:
    timeout: 5000
- takeScreenshot: "01_after_cold_start"
- runFlow:
    file: _lib/dump_hierarchy.yaml
    env:
      WAYPOINT: "01_after_cold_start"
      SIDE: "rn"
      RUN_ID: "${RUN_ID}"
      DEVICE: "${DEVICE}"
      SCENARIO: "${SCENARIO}"
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "01_after_cold_start", EDGE: "END", DEVICE: "${DEVICE}" }

- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "02_after_recommend_loaded", EDGE: "BEGIN", DEVICE: "${DEVICE}" }
# 等推荐位首屏；若有更稳的 selector（例如某个推荐位标题文本），改成 assertVisible 等待
- waitForAnimationToEnd:
    timeout: 8000
- takeScreenshot: "02_after_recommend_loaded"
- runFlow:
    file: _lib/dump_hierarchy.yaml
    env:
      WAYPOINT: "02_after_recommend_loaded"
      SIDE: "rn"
      RUN_ID: "${RUN_ID}"
      DEVICE: "${DEVICE}"
      SCENARIO: "${SCENARIO}"
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "02_after_recommend_loaded", EDGE: "END", DEVICE: "${DEVICE}" }
```

- [ ] **Step 3: 写 home_entry.android.yaml（与 RN 完全对称，仅 appId 改）**

```yaml
# maestro/flows/parity/home_entry.android.yaml
appId: com.zili.android.musicfreeandroid.debug
---
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "00_pre_launch", EDGE: "BEGIN", DEVICE: "${DEVICE}" }
- launchApp:
    clearState: false
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "01_after_cold_start", EDGE: "BEGIN", DEVICE: "${DEVICE}" }
- waitForAnimationToEnd:
    timeout: 5000
- takeScreenshot: "01_after_cold_start"
- runFlow:
    file: _lib/dump_hierarchy.yaml
    env:
      WAYPOINT: "01_after_cold_start"
      SIDE: "android"
      RUN_ID: "${RUN_ID}"
      DEVICE: "${DEVICE}"
      SCENARIO: "${SCENARIO}"
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "01_after_cold_start", EDGE: "END", DEVICE: "${DEVICE}" }
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "02_after_recommend_loaded", EDGE: "BEGIN", DEVICE: "${DEVICE}" }
- waitForAnimationToEnd:
    timeout: 8000
- takeScreenshot: "02_after_recommend_loaded"
- runFlow:
    file: _lib/dump_hierarchy.yaml
    env:
      WAYPOINT: "02_after_recommend_loaded"
      SIDE: "android"
      RUN_ID: "${RUN_ID}"
      DEVICE: "${DEVICE}"
      SCENARIO: "${SCENARIO}"
- runScript:
    file: _lib/mark_waypoint.js
    env: { WAYPOINT: "02_after_recommend_loaded", EDGE: "END", DEVICE: "${DEVICE}" }
```

- [ ] **Step 4: 单独验证两侧 flow 各跑一次**

Run（带占位 env）:

```bash
RUN_ID=2026-05-15-debug
mkdir -p docs/parity-audit/runs/$RUN_ID/scenarios/home_entry/rn
mkdir -p docs/parity-audit/runs/$RUN_ID/scenarios/home_entry/android

maestro test \
  -e RUN_ID=$RUN_ID -e SCENARIO=home_entry -e DEVICE="$(adb devices | awk 'NR==2 {print $1}')" \
  maestro/flows/parity/home_entry.rn.yaml

maestro test \
  -e RUN_ID=$RUN_ID -e SCENARIO=home_entry -e DEVICE="$(adb devices | awk 'NR==2 {print $1}')" \
  maestro/flows/parity/home_entry.android.yaml
```

Expected: 两个 flow 都成功结束；`docs/parity-audit/runs/2026-05-15-debug/scenarios/home_entry/{rn,android}/` 下各落 2 个 `.xml` 与 2 个截图（Maestro screenshot 默认落 `~/.maestro` 下，会在 Task 10 由 run-scenario.sh 移到产物目录）。

- [ ] **Step 5: Commit**

```bash
git add docs/parity-audit/scenarios/home_entry.yaml maestro/flows/parity/home_entry.rn.yaml maestro/flows/parity/home_entry.android.yaml
git commit -m "feat(parity-audit): home_entry scenario YAML 与双侧 Maestro flow（v0 手写）"
```

---

## Task 10: run-scenario.sh（编排 Maestro + logcat + 截图归集）

**Files:**
- Create: `scripts/parity-audit/run-scenario.sh`

- [ ] **Step 1: 写脚本**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/run-scenario.sh
# usage: run-scenario.sh <scenario_id> <side> <run_id> [device]
#   scenario_id: e.g. home_entry
#   side:        rn | android
#   run_id:      e.g. 2026-05-15-1200
#   device:      optional adb serial

set -euo pipefail

SCENARIO="$1"
SIDE="$2"
RUN_ID="$3"
DEVICE="${4:-}"

REPO_ROOT="$(git rev-parse --show-toplevel)"
SCENARIO_YAML="$REPO_ROOT/docs/parity-audit/scenarios/${SCENARIO}.yaml"
[ -f "$SCENARIO_YAML" ] || { echo "ERROR: scenario yaml missing: $SCENARIO_YAML" >&2; exit 1; }

# 取 appId 与 flow path
APPID=$(python3 -c "import yaml,sys; d=yaml.safe_load(open(sys.argv[1])); print(d[sys.argv[2]+'_appid'])" "$SCENARIO_YAML" "$SIDE")
FLOW=$(python3 -c "import yaml,sys; d=yaml.safe_load(open(sys.argv[1])); print(d[sys.argv[2]+'_flow'])" "$SCENARIO_YAML" "$SIDE")

OUT="$REPO_ROOT/docs/parity-audit/runs/$RUN_ID/scenarios/$SCENARIO/$SIDE"
mkdir -p "$OUT"

# 清 app data 保证冷启动
ADB="adb"
[ -n "$DEVICE" ] && ADB="adb -s $DEVICE"
$ADB shell pm clear "$APPID" >/dev/null

# 起后台 logcat
PID_FILE="$OUT/logcat.pid"
$ADB logcat -c
# 用 --pid 需要 app 已启动；这里先全量记录，事后用 PARITY_MARK 锚点切片
$ADB logcat -v threadtime > "$OUT/logcat.raw.txt" &
echo $! > "$PID_FILE"
trap 'kill "$(cat "$PID_FILE")" 2>/dev/null || true' EXIT

# 跑 Maestro flow（同步阻塞）
MAESTRO_ARG=""
[ -n "$DEVICE" ] && MAESTRO_ARG="--device $DEVICE"

if ! maestro $MAESTRO_ARG test \
       -e RUN_ID="$RUN_ID" -e SCENARIO="$SCENARIO" -e DEVICE="$DEVICE" \
       "$REPO_ROOT/$FLOW"; then
  echo "MAESTRO_FAILED" > "$OUT/.status"
  exit 0     # 不让一个 scenario 失败拖死整轮；上层根据 .status 处理
fi

# 移动 Maestro 截图（默认在 ~/.maestro/tests/<dateTime>/screenshots/）到产物目录
MAESTRO_LATEST=$(ls -1dt "$HOME"/.maestro/tests/*/screenshots 2>/dev/null | head -n1 || true)
if [ -n "$MAESTRO_LATEST" ] && [ -d "$MAESTRO_LATEST" ]; then
  cp "$MAESTRO_LATEST"/*.png "$OUT"/ 2>/dev/null || true
fi

echo "OK" > "$OUT/.status"
echo "scenario=$SCENARIO side=$SIDE -> $OUT"
```

- [ ] **Step 2: 加可执行位**

```bash
chmod +x scripts/parity-audit/run-scenario.sh
```

- [ ] **Step 3: 端到端手动跑一次**

```bash
RUN_ID=$(date +%Y-%m-%d-%H%M)
bash scripts/parity-audit/run-scenario.sh home_entry rn "$RUN_ID"
bash scripts/parity-audit/run-scenario.sh home_entry android "$RUN_ID"
ls docs/parity-audit/runs/$RUN_ID/scenarios/home_entry/rn/
ls docs/parity-audit/runs/$RUN_ID/scenarios/home_entry/android/
```

Expected：两侧目录下各有 `logcat.raw.txt`、`*.png`、`waypoint-*-hierarchy.xml`、`.status` 内容为 `OK`。

- [ ] **Step 4: Commit**

```bash
git add scripts/parity-audit/run-scenario.sh
git commit -m "feat(parity-audit): run-scenario.sh 编排 Maestro + logcat + 截图归集"
```

---

## Task 11: ParityEventSink Kotlin 实现（TDD）

**Files:**
- Modify: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/MfLog.kt`
- Create: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/ParityEventSink.kt`
- Create: `logging/src/test/java/com/zili/android/musicfreeandroid/logging/ParityEventSinkTest.kt`

- [ ] **Step 1: 读 MfLog 当前接口**

Read: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/MfLogger.kt`
Read: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/MfLog.kt`

确认 `MfLog.info/error/warn` 的签名（参数大概率是 `event: String, fields: Map<String, Any?> = emptyMap()`），后续测试与实现以实际签名为准。

- [ ] **Step 2: 写失败测试**

`logging/src/test/java/com/zili/android/musicfreeandroid/logging/ParityEventSinkTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParityEventSinkTest {
    @Test
    fun `maps plugin method call to plugin_method_called event`() {
        val captured = mutableListOf<String>()
        val sink = ParityEventSink(emitter = { tag, json -> captured += "$tag|$json" })

        sink.emit(
            event = "plugin_method_called",
            fields = mapOf("plugin_id" to "wy", "method" to "search", "args" to "raw search query")
        )

        assertEquals(1, captured.size)
        val (tag, json) = captured.first().split("|", limit = 2)
        assertEquals("PARITY_EVT", tag)
        assertTrue(json.contains("\"kind\":\"plugin.method_called\""))
        assertTrue(json.contains("\"plugin_id\":\"wy\""))
        assertTrue(json.contains("\"method\":\"search\""))
        // args_hash 必须是 8 字符 hex
        val hashRegex = Regex("\"args_hash\":\"[0-9a-f]{8}\"")
        assertTrue(hashRegex.containsMatchIn(json))
    }

    @Test
    fun `ignores events outside taxonomy`() {
        val captured = mutableListOf<String>()
        val sink = ParityEventSink(emitter = { tag, json -> captured += "$tag|$json" })

        sink.emit(event = "user_clicked_random_button", fields = emptyMap())

        assertEquals(0, captured.size)
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `./gradlew :logging:testDebugUnitTest --tests "*ParityEventSinkTest*"`
Expected: 编译错或测试失败，因为 `ParityEventSink` 不存在。

- [ ] **Step 4: 实现 ParityEventSink**

`logging/src/main/java/com/zili/android/musicfreeandroid/logging/ParityEventSink.kt`：

```kotlin
package com.zili.android.musicfreeandroid.logging

import org.json.JSONObject
import java.security.MessageDigest

/**
 * 把 MfLog 业务事件按 parity event taxonomy 映射为单行 JSON，
 * 经 emitter 输出（默认 emitter = android.util.Log.println）。
 *
 * Spec: docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md §4.1
 */
class ParityEventSink(
    private val emitter: (String, String) -> Unit
) {
    fun emit(event: String, fields: Map<String, Any?>) {
        val kind = mapEventName(event) ?: return
        val canonical = canonicalize(kind, fields)
        val json = JSONObject().apply {
            put("kind", kind)
            put("ts_ms", System.currentTimeMillis())
            canonical.forEach { (k, v) -> put(k, v) }
        }.toString()
        emitter(TAG, json)
    }

    private fun mapEventName(event: String): String? = when (event) {
        "nav_enter"               -> "nav.enter"
        "nav_leave"               -> "nav.leave"
        "plugin_method_called"    -> "plugin.method_called"
        "plugin_method_returned"  -> "plugin.method_returned"
        "net_request"             -> "net.request"
        "net_response"            -> "net.response"
        "play_state_changed"      -> "play.state_changed"
        "error"                   -> "error"
        else -> null
    }

    private fun canonicalize(kind: String, fields: Map<String, Any?>): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for ((k, v) in fields) {
            if (v == null) continue
            when {
                k == "args" || k == "params" || k == "track_id" || k == "message" -> {
                    val hashKey = when (k) {
                        "args"     -> "args_hash"
                        "params"   -> "params_hash"
                        "track_id" -> "track_id_hash"
                        "message"  -> "message_hash"
                        else       -> "${k}_hash"
                    }
                    out[hashKey] = sha1Eight(v.toString())
                }
                k == "url" -> out["url_template"] = normalizeUrlTemplate(v.toString())
                else -> out[k] = v
            }
        }
        return out
    }

    private fun sha1Eight(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun normalizeUrlTemplate(url: String): String {
        // 把 query/path 里随机或时间相关参数替换为占位
        return url
            .replace(Regex("(\\?|&)([^=&]+)=[^&]+")) { mr ->
                "${mr.groupValues[1]}${mr.groupValues[2]}=*"
            }
    }

    companion object {
        const val TAG = "PARITY_EVT"
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `./gradlew :logging:testDebugUnitTest --tests "*ParityEventSinkTest*"`
Expected: 2 个测试 PASS。

- [ ] **Step 6: 在 MfLog 中接入 sink（按需开关）**

Read 当前 `MfLog.kt` 找到事件分发点，在 `info/error/warn` 中按"开关存在则同时调用 sink"的方式接入。开关用环境变量 + system property：

```kotlin
// 在 MfLog 类内部新增
private var paritySink: ParityEventSink? = null

@JvmStatic
fun enableParitySink(emitter: (String, String) -> Unit = { tag, json ->
    android.util.Log.println(android.util.Log.INFO, tag, json)
}) {
    paritySink = ParityEventSink(emitter)
}

// 在每个 info/warn/error 落点的末尾调用
paritySink?.emit(event, fields)
```

并在 `app/src/main/java/.../MusicFreeApp.kt`（或对应 Application）中只在 `BuildConfig.DEBUG && System.getenv("PARITY_AUDIT") == "1"` 时调用 `MfLog.enableParitySink()`。具体类名取真实代码，按当前签名调整。

- [ ] **Step 7: 跑全 logging 单测**

Run: `./gradlew :logging:testDebugUnitTest`
Expected: 全部 PASS。

- [ ] **Step 8: Commit**

```bash
git add logging/src/main/java/com/zili/android/musicfreeandroid/logging/ParityEventSink.kt \
        logging/src/main/java/com/zili/android/musicfreeandroid/logging/MfLog.kt \
        logging/src/test/java/com/zili/android/musicfreeandroid/logging/ParityEventSinkTest.kt \
        app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApp.kt
git commit -m "feat(parity-audit): ParityEventSink 把 MfLog 映射为 PARITY_EVT 单行 JSON"
```

> 若 Application 入口文件名不同，自行调整 `git add` 路径。

---

## Task 12: parse_events.py（logcat raw → events.jsonl, TDD）

**Files:**
- Create: `scripts/parity-audit/parse_events.py`
- Create: `scripts/parity-audit/tests/test_parse_events.py`
- Create: `scripts/parity-audit/tests/fixtures/sample_logcat_rn.txt`
- Create: `scripts/parity-audit/tests/fixtures/sample_logcat_android.txt`

- [ ] **Step 1: 落两份 fixture**

`scripts/parity-audit/tests/fixtures/sample_logcat_rn.txt`：

```
05-15 12:30:00.001  1234  1234 I PARITY_MARK: BEGIN 01_after_cold_start
05-15 12:30:00.500  1234  1234 I ReactNativeJS: [PluginManager] call wy.search with hello world
05-15 12:30:00.700  1234  1234 I ReactNativeJS: [TrackPlayer] state transition: IDLE -> LOADING
05-15 12:30:00.999  1234  1234 I PARITY_MARK: END 01_after_cold_start
05-15 12:30:01.100  1234  1234 I PARITY_MARK: BEGIN 02_after_recommend_loaded
05-15 12:30:01.200  1234  1234 I ReactNativeJS: [Navigation] navigate to MusicDetail with id=abc&from=home
05-15 12:30:01.999  1234  1234 I PARITY_MARK: END 02_after_recommend_loaded
```

`scripts/parity-audit/tests/fixtures/sample_logcat_android.txt`：

```
05-15 12:30:10.001  4321  4321 I PARITY_MARK: BEGIN 01_after_cold_start
05-15 12:30:10.500  4321  4321 I PARITY_EVT: {"kind":"plugin.method_called","ts_ms":1715749810500,"plugin_id":"wy","method":"search","args_hash":"5eb63bbb"}
05-15 12:30:10.700  4321  4321 I PARITY_EVT: {"kind":"play.state_changed","ts_ms":1715749810700,"from":"IDLE","to":"LOADING"}
05-15 12:30:10.999  4321  4321 I PARITY_MARK: END 01_after_cold_start
```

- [ ] **Step 2: 写失败测试**

```python
# scripts/parity-audit/tests/test_parse_events.py
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIXTURES = pathlib.Path(__file__).resolve().parent / "fixtures"
sys.path.insert(0, str(ROOT))

import parse_events


def test_parse_android_native_parity_evt():
    events = parse_events.parse_logcat(
        FIXTURES / "sample_logcat_android.txt",
        side="android",
        rn_parser_config=None,
    )
    assert len(events) == 2
    assert events[0]["kind"] == "plugin.method_called"
    assert events[0]["waypoint"] == "01_after_cold_start"
    assert events[0]["side"] == "android"
    assert events[1]["kind"] == "play.state_changed"
    assert events[1]["from"] == "IDLE" and events[1]["to"] == "LOADING"


def test_parse_rn_with_regex_config(tmp_path):
    cfg = ROOT / "../.agents/skills/parity-audit-skill/references/rn-logcat-parser.json"
    cfg_path = pathlib.Path(cfg).resolve()
    events = parse_events.parse_logcat(
        FIXTURES / "sample_logcat_rn.txt",
        side="rn",
        rn_parser_config=cfg_path,
    )
    # 期望抓到 1 plugin.method_called + 1 play.state_changed + 1 nav.enter
    kinds = sorted(e["kind"] for e in events)
    assert kinds == ["nav.enter", "play.state_changed", "plugin.method_called"]
    # 跨 waypoint 切片
    by_wp = {e["waypoint"]: e["kind"] for e in events}
    assert "01_after_cold_start" in by_wp
    assert "02_after_recommend_loaded" in by_wp
    # args_hash 必须是 8 字符 hex
    plugin_event = next(e for e in events if e["kind"] == "plugin.method_called")
    assert len(plugin_event["args_hash"]) == 8


def test_lines_outside_waypoint_window_are_dropped():
    events = parse_events.parse_logcat(
        FIXTURES / "sample_logcat_android.txt",
        side="android",
        rn_parser_config=None,
    )
    for e in events:
        assert e["waypoint"] is not None
```

- [ ] **Step 3: 跑测试确认失败**

Run: `python3 -m pytest scripts/parity-audit/tests/test_parse_events.py -v`
Expected: FAIL（模块或函数不存在）。

- [ ] **Step 4: 实现 parse_events.py**

```python
# scripts/parity-audit/parse_events.py
"""logcat raw text → events.jsonl

支持两种输入：
1. side=android：识别 PARITY_EVT 单行 JSON
2. side=rn：用 references/rn-logcat-parser.json 中正则匹配 RN 自有 console.log
"""
from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import pathlib
import re
import sys
from typing import Any

PARITY_MARK = re.compile(r"PARITY_MARK:\s+(?P<edge>BEGIN|END|DUMP)\s+(?P<wp>[\w-]+)")
PARITY_EVT  = re.compile(r"PARITY_EVT:\s*(?P<payload>\{.+\})")


def sha1_8(s: str) -> str:
    return hashlib.sha1(s.encode("utf-8")).hexdigest()[:8]


def parse_logcat(
    path: pathlib.Path,
    *,
    side: str,
    rn_parser_config: pathlib.Path | None,
) -> list[dict[str, Any]]:
    text = pathlib.Path(path).read_text(errors="replace")
    current_wp: str | None = None
    out: list[dict[str, Any]] = []

    rn_patterns = _load_rn_patterns(rn_parser_config) if side == "rn" and rn_parser_config else []

    for raw_line in text.splitlines():
        m = PARITY_MARK.search(raw_line)
        if m:
            edge = m.group("edge")
            wp = m.group("wp")
            if edge == "BEGIN":
                current_wp = wp
            elif edge == "END":
                current_wp = None
            # DUMP 不影响窗口
            continue

        if current_wp is None:
            continue

        if side == "android":
            mev = PARITY_EVT.search(raw_line)
            if not mev:
                continue
            try:
                payload = json.loads(mev.group("payload"))
            except json.JSONDecodeError:
                continue
            payload["waypoint"] = current_wp
            payload["side"] = "android"
            out.append(payload)
        else:
            # RN 走正则
            for pat in rn_patterns:
                m2 = re.search(pat["regex"], raw_line)
                if not m2:
                    continue
                fields = _materialize_fields(pat["fields_template"], m2.groupdict())
                event = {
                    "kind": pat["kind"],
                    "ts_ms": 0,  # v0 不解析时间；diff 用顺序而非精确时间
                    "waypoint": current_wp,
                    "side": "rn",
                    **fields,
                }
                out.append(event)
                break
    return out


def _load_rn_patterns(cfg: pathlib.Path) -> list[dict[str, Any]]:
    data = json.loads(pathlib.Path(cfg).read_text())
    return data.get("patterns", [])


def _materialize_fields(template: dict[str, str], groups: dict[str, str | None]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for k, v in template.items():
        if not isinstance(v, str):
            out[k] = v
            continue
        if v.startswith("$"):
            spec = v[1:]
            transforms: list[str] = []
            if "|" in spec:
                spec, *transforms = spec.split("|")
            raw = groups.get(spec)
            if raw is None:
                continue
            val: Any = raw
            for t in transforms:
                if t == "sha1_8":
                    val = sha1_8(str(val))
            out[k] = val
        else:
            out[k] = v
    return out


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--logcat", required=True)
    p.add_argument("--side", required=True, choices=("rn", "android"))
    p.add_argument("--rn-parser-config", default=None)
    p.add_argument("--out", required=True)
    args = p.parse_args()

    events = parse_logcat(
        pathlib.Path(args.logcat),
        side=args.side,
        rn_parser_config=pathlib.Path(args.rn_parser_config) if args.rn_parser_config else None,
    )
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w") as f:
        for ev in events:
            f.write(json.dumps(ev, ensure_ascii=False) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 5: 跑测试确认通过**

Run: `python3 -m pytest scripts/parity-audit/tests/test_parse_events.py -v`
Expected: 3 个测试 PASS。

- [ ] **Step 6: Commit**

```bash
git add scripts/parity-audit/parse_events.py scripts/parity-audit/tests/test_parse_events.py scripts/parity-audit/tests/fixtures/
git commit -m "feat(parity-audit): parse_events.py 把 logcat 切片为规范化 events.jsonl"
```

---

## Task 13: screenshot_ssim.py 与 compare.py（TDD）

**Files:**
- Create: `scripts/parity-audit/screenshot_ssim.py`
- Create: `scripts/parity-audit/compare.py`
- Create: `scripts/parity-audit/tests/test_screenshot_ssim.py`
- Create: `scripts/parity-audit/tests/test_compare.py`
- Create: `scripts/parity-audit/tests/fixtures/img_a.png` / `img_b.png` / `img_a_copy.png`

- [ ] **Step 1: 准备 fixture 图片**

```bash
python3 - <<'PY'
import numpy as np, cv2, pathlib
out = pathlib.Path("scripts/parity-audit/tests/fixtures")
out.mkdir(parents=True, exist_ok=True)
a = (np.ones((200,100,3), dtype=np.uint8) * 200)
b = a.copy(); b[50:80, 30:60] = 50  # 一块明显不同
cv2.imwrite(str(out/"img_a.png"), a)
cv2.imwrite(str(out/"img_a_copy.png"), a.copy())
cv2.imwrite(str(out/"img_b.png"), b)
PY
```

- [ ] **Step 2: 写 screenshot_ssim 失败测试**

```python
# scripts/parity-audit/tests/test_screenshot_ssim.py
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIX = pathlib.Path(__file__).resolve().parent / "fixtures"
sys.path.insert(0, str(ROOT))

import screenshot_ssim


def test_identical_images_have_ssim_one():
    s = screenshot_ssim.compute(FIX / "img_a.png", FIX / "img_a_copy.png")
    assert s["ssim"] > 0.99
    assert s["verdict"] == "match"


def test_different_images_have_low_ssim():
    s = screenshot_ssim.compute(FIX / "img_a.png", FIX / "img_b.png", threshold=0.92)
    assert s["ssim"] < 0.92
    assert s["verdict"] == "visual_diff"


def test_mismatched_dimensions_handled():
    import numpy as np, cv2, tempfile, pathlib as p
    with tempfile.TemporaryDirectory() as td:
        big = (p.Path(td) / "big.png")
        small = (p.Path(td) / "small.png")
        cv2.imwrite(str(big), (np.ones((400, 200, 3), dtype=np.uint8) * 200))
        cv2.imwrite(str(small), (np.ones((200, 100, 3), dtype=np.uint8) * 200))
        s = screenshot_ssim.compute(big, small)
        assert "resized" in s
        assert s["verdict"] in ("match", "visual_diff")
```

- [ ] **Step 3: 实现 screenshot_ssim.py**

```python
# scripts/parity-audit/screenshot_ssim.py
"""Compute SSIM between two screenshots; output JSON verdict."""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
from typing import Any

import cv2
import numpy as np
from skimage.metrics import structural_similarity as ssim


def _load_gray(path: pathlib.Path) -> np.ndarray:
    img = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if img is None:
        raise FileNotFoundError(path)
    return cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)


def compute(rn: pathlib.Path, android: pathlib.Path, threshold: float = 0.92) -> dict[str, Any]:
    a = _load_gray(rn)
    b = _load_gray(android)
    resized = False
    if a.shape != b.shape:
        target = (min(a.shape[1], b.shape[1]), min(a.shape[0], b.shape[0]))
        a = cv2.resize(a, target, interpolation=cv2.INTER_AREA)
        b = cv2.resize(b, target, interpolation=cv2.INTER_AREA)
        resized = True
    score = float(ssim(a, b))
    return {
        "ssim": round(score, 4),
        "threshold": threshold,
        "verdict": "match" if score >= threshold else "visual_diff",
        "resized": resized,
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--rn", required=True)
    p.add_argument("--android", required=True)
    p.add_argument("--threshold", type=float, default=0.92)
    args = p.parse_args()
    out = compute(pathlib.Path(args.rn), pathlib.Path(args.android), threshold=args.threshold)
    print(json.dumps(out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 4: 跑 screenshot_ssim 测试**

Run: `python3 -m pytest scripts/parity-audit/tests/test_screenshot_ssim.py -v`
Expected: 3 PASS.

- [ ] **Step 5: 写 compare 失败测试**

```python
# scripts/parity-audit/tests/test_compare.py
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import compare


def _ev(kind, waypoint, side, **fields):
    return {"kind": kind, "ts_ms": 0, "waypoint": waypoint, "side": side, **fields}


def test_passed_when_events_align(tmp_path):
    rn = tmp_path / "rn.jsonl"
    an = tmp_path / "android.jsonl"
    rn.write_text("\n".join(json.dumps(e) for e in [
        _ev("plugin.method_called", "01", "rn", plugin_id="wy", method="search", args_hash="a"*8),
        _ev("play.state_changed", "01", "rn", **{"from": "IDLE", "to": "LOADING"}),
    ]) + "\n")
    an.write_text("\n".join(json.dumps(e) for e in [
        _ev("plugin.method_called", "01", "android", plugin_id="wy", method="search", args_hash="a"*8),
        _ev("play.state_changed", "01", "android", **{"from": "IDLE", "to": "LOADING"}),
    ]) + "\n")

    diff = compare.run(
        scenario_id="t",
        rn_events=rn,
        android_events=an,
        screenshot_results=[],
    )
    assert diff["verdict"] == "passed"
    assert diff["severity"] == "minor"
    assert diff["event_diffs"] == []


def test_android_missing_event_triggers_diff_found(tmp_path):
    rn = tmp_path / "rn.jsonl"
    an = tmp_path / "android.jsonl"
    rn.write_text(json.dumps(_ev("plugin.method_called", "01", "rn", plugin_id="wy", method="search", args_hash="a"*8)) + "\n")
    an.write_text("")
    diff = compare.run(
        scenario_id="t",
        rn_events=rn,
        android_events=an,
        screenshot_results=[],
    )
    assert diff["verdict"] == "diff_found"
    assert diff["severity"] in ("major", "critical")
    assert any(d["verdict"] == "android_missing" for d in diff["event_diffs"])


def test_value_mismatch_in_play_state(tmp_path):
    rn = tmp_path / "rn.jsonl"
    an = tmp_path / "android.jsonl"
    rn.write_text(json.dumps(_ev("play.state_changed", "03", "rn", **{"from": "IDLE", "to": "PLAYING"})) + "\n")
    an.write_text(json.dumps(_ev("play.state_changed", "03", "android", **{"from": "IDLE", "to": "BUFFERING"})) + "\n")
    diff = compare.run(
        scenario_id="t",
        rn_events=rn,
        android_events=an,
        screenshot_results=[],
    )
    assert diff["verdict"] == "diff_found"
    assert any(d["verdict"] == "value_mismatch" and d["field"] == "to" for d in diff["event_diffs"])


def test_visual_diff_alone_yields_major(tmp_path):
    rn = tmp_path / "rn.jsonl"
    an = tmp_path / "android.jsonl"
    rn.write_text("")
    an.write_text("")
    diff = compare.run(
        scenario_id="t",
        rn_events=rn,
        android_events=an,
        screenshot_results=[{"waypoint": "01", "ssim": 0.5, "verdict": "visual_diff"}],
    )
    assert diff["verdict"] == "diff_found"
    assert diff["severity"] == "major"
    assert diff["screenshot_diffs"]
```

- [ ] **Step 6: 实现 compare.py**

```python
# scripts/parity-audit/compare.py
"""Compare RN & Android events.jsonl plus screenshot SSIM results into diff.json.

Spec §4.3
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
from collections import defaultdict
from typing import Any

CRITICAL_KINDS = {"error", "play.state_changed"}
EVENT_PRIORITY = [
    "error",
    "play.state_changed",
    "plugin.method_called",
    "plugin.method_returned",
    "net.request",
    "net.response",
    "nav.enter",
    "nav.leave",
]


def _load_events(path: pathlib.Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    out = []
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line:
            continue
        out.append(json.loads(line))
    return out


def run(
    *,
    scenario_id: str,
    rn_events: pathlib.Path,
    android_events: pathlib.Path,
    screenshot_results: list[dict[str, Any]],
    ignore_event_fields: list[str] | None = None,
) -> dict[str, Any]:
    ignore = set(ignore_event_fields or ["ts_ms", "request_id"])
    rn = _load_events(rn_events)
    an = _load_events(android_events)

    rn_bucket: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    an_bucket: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for e in rn:
        rn_bucket[(e["waypoint"], e["kind"])].append(e)
    for e in an:
        an_bucket[(e["waypoint"], e["kind"])].append(e)

    event_diffs: list[dict[str, Any]] = []
    keys = set(rn_bucket) | set(an_bucket)
    for waypoint, kind in sorted(keys):
        rn_list = rn_bucket.get((waypoint, kind), [])
        an_list = an_bucket.get((waypoint, kind), [])
        if rn_list and not an_list:
            event_diffs.append({
                "waypoint": waypoint, "kind": kind,
                "verdict": "android_missing",
                "rn_only": [_strip(e, ignore) for e in rn_list],
            })
        elif an_list and not rn_list:
            event_diffs.append({
                "waypoint": waypoint, "kind": kind,
                "verdict": "android_extra",
                "android_only": [_strip(e, ignore) for e in an_list],
            })
        else:
            # 同 kind 双方都有：逐对比较关键字段
            for rn_ev, an_ev in zip(rn_list, an_list):
                for field, rn_val in rn_ev.items():
                    if field in ("kind", "ts_ms", "waypoint", "side") or field in ignore:
                        continue
                    an_val = an_ev.get(field)
                    if an_val != rn_val:
                        event_diffs.append({
                            "waypoint": waypoint, "kind": kind,
                            "verdict": "value_mismatch",
                            "field": field, "rn": rn_val, "android": an_val,
                        })

    severity, verdict = _decide(event_diffs, screenshot_results)
    return {
        "scenario_id": scenario_id,
        "screenshot_diffs": screenshot_results,
        "event_diffs": event_diffs,
        "verdict": verdict,
        "severity": severity,
    }


def _strip(ev: dict[str, Any], ignore: set[str]) -> dict[str, Any]:
    return {k: v for k, v in ev.items() if k not in ignore and k not in ("ts_ms",)}


def _decide(event_diffs: list[dict[str, Any]], screenshot_results: list[dict[str, Any]]) -> tuple[str, str]:
    has_critical_event_diff = any(d["kind"] in CRITICAL_KINDS for d in event_diffs)
    has_event_diff = bool(event_diffs)
    has_visual_diff = any(s.get("verdict") == "visual_diff" for s in screenshot_results)

    if has_critical_event_diff:
        return ("major", "diff_found")
    if has_event_diff:
        return ("major", "diff_found")
    if has_visual_diff:
        return ("major", "diff_found")
    return ("minor", "passed")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--scenario-id", required=True)
    p.add_argument("--rn-events", required=True)
    p.add_argument("--android-events", required=True)
    p.add_argument("--screenshot-results", default="")
    p.add_argument("--out", required=True)
    args = p.parse_args()

    screenshot_results: list[dict[str, Any]] = []
    if args.screenshot_results:
        screenshot_results = json.loads(pathlib.Path(args.screenshot_results).read_text())

    diff = run(
        scenario_id=args.scenario_id,
        rn_events=pathlib.Path(args.rn_events),
        android_events=pathlib.Path(args.android_events),
        screenshot_results=screenshot_results,
    )
    pathlib.Path(args.out).write_text(json.dumps(diff, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 7: 跑 compare 测试**

Run: `python3 -m pytest scripts/parity-audit/tests/test_compare.py -v`
Expected: 4 PASS.

- [ ] **Step 8: Commit**

```bash
git add scripts/parity-audit/screenshot_ssim.py scripts/parity-audit/compare.py scripts/parity-audit/tests/test_screenshot_ssim.py scripts/parity-audit/tests/test_compare.py scripts/parity-audit/tests/fixtures/img_*.png
git commit -m "feat(parity-audit): screenshot_ssim.py + compare.py（含 TDD）"
```

---

## Task 14: audit.sh 顶层编排 + REPORT.md 生成（v0 端到端跑通）

**Files:**
- Create: `scripts/parity-audit/audit.sh`

- [ ] **Step 1: 写 audit.sh**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/audit.sh
# v0 顶层入口：bootstrap → build → install → install-plugins → run-scenario × N → diff → state 更新 → REPORT.md
# usage: audit.sh --scope page:home --mode dry-run --limit 1 [--device <serial>]

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
SCOPE=""
MODE="audit"
LIMIT=5
DEVICE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --scope)  SCOPE="$2";   shift 2 ;;
    --mode)   MODE="$2";    shift 2 ;;
    --limit)  LIMIT="$2";   shift 2 ;;
    --device) DEVICE="$2";  shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

# 本 Task 阶段 audit.sh 仅支持 dry-run；--mode audit 的 Issue 创建路径在 Task 19 接入。
if [ "$MODE" != "dry-run" ]; then
  echo "WARN: audit.sh in this stage only supports --mode dry-run; ignoring --mode=$MODE and continuing as dry-run" >&2
  MODE="dry-run"
fi

bash "$REPO_ROOT/scripts/parity-audit/bootstrap.sh"

RUN_ID=$(date +%Y-%m-%d-%H%M)
RUN_DIR="$REPO_ROOT/docs/parity-audit/runs/$RUN_ID"
mkdir -p "$RUN_DIR"

AGENT_LOG="$RUN_DIR/agent.log.jsonl"
log_event() {
  python3 -c '
import datetime, json, sys
d = {
  "ts": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
  **json.loads(sys.argv[1])
}
print(json.dumps(d, ensure_ascii=False))
' "$1" >> "$AGENT_LOG"
}

log_event '{"phase":"start","event":"run_started","run_id":"'"$RUN_ID"'","scope":"'"$SCOPE"'","mode":"'"$MODE"'"}'

# preflight: build + install + plugin bootstrap
log_event '{"phase":"preflight","event":"rn_build_started"}'
RN_APK=$(bash "$REPO_ROOT/scripts/parity-audit/build-rn.sh")
log_event '{"phase":"preflight","event":"rn_build_ok"}'

log_event '{"phase":"preflight","event":"android_build_started"}'
ANDROID_APK=$(bash "$REPO_ROOT/scripts/parity-audit/build-android.sh")
log_event '{"phase":"preflight","event":"android_build_ok"}'

bash "$REPO_ROOT/scripts/parity-audit/install-both.sh" "$RN_APK" "$ANDROID_APK" "$DEVICE"
log_event '{"phase":"preflight","event":"install_ok"}'

bash "$REPO_ROOT/scripts/parity-audit/install-plugins.sh" "$DEVICE"
log_event '{"phase":"preflight","event":"plugin_bootstrap_ok"}'

# scenarios 选择：v0 只支持 page:<x> 与 all
if [ -z "$SCOPE" ] || [ "$SCOPE" = "all" ]; then
  SCENARIO_IDS=$(ls "$REPO_ROOT/docs/parity-audit/scenarios/" | sed 's/\.yaml$//')
elif [[ "$SCOPE" == page:* ]]; then
  PAGE="${SCOPE#page:}"
  SCENARIO_IDS=$(python3 - <<PY
import os, yaml, pathlib
out = []
for p in pathlib.Path("$REPO_ROOT/docs/parity-audit/scenarios").glob("*.yaml"):
    data = yaml.safe_load(open(p))
    if data.get("page") == "$PAGE":
        out.append(data["id"])
print("\n".join(out))
PY
)
else
  echo "v0 doesn't yet support scope=$SCOPE; aborting" >&2
  exit 1
fi

REPORT="$RUN_DIR/REPORT.md"
{
  echo "# Parity Audit Run $RUN_ID"
  echo
  echo "- scope: \`$SCOPE\`  · mode: \`$MODE\`  · limit: $LIMIT"
  echo "- RN APK: \`$RN_APK\`"
  echo "- Android APK: \`$ANDROID_APK\`"
  echo
  echo "## Scenarios"
  echo
  echo "| scenario | verdict | severity | screenshot diff | event diff count |"
  echo "|---|---|---|---|---|"
} > "$REPORT"

N=0
for SID in $SCENARIO_IDS; do
  N=$((N+1))
  if [ "$N" -gt "$LIMIT" ]; then
    log_event '{"phase":"scenario","event":"limit_reached","limit":'"$LIMIT"'}'
    break
  fi
  log_event '{"phase":"scenario","scenario":"'"$SID"'","event":"started"}'

  bash "$REPO_ROOT/scripts/parity-audit/run-scenario.sh" "$SID" rn      "$RUN_ID" "$DEVICE"
  bash "$REPO_ROOT/scripts/parity-audit/run-scenario.sh" "$SID" android "$RUN_ID" "$DEVICE"

  SCEN_DIR="$RUN_DIR/scenarios/$SID"
  RN_LOGCAT="$SCEN_DIR/rn/logcat.raw.txt"
  AN_LOGCAT="$SCEN_DIR/android/logcat.raw.txt"
  RN_EVENTS="$SCEN_DIR/rn/events.jsonl"
  AN_EVENTS="$SCEN_DIR/android/events.jsonl"
  RN_PARSER="$REPO_ROOT/.agents/skills/parity-audit-skill/references/rn-logcat-parser.json"

  python3 "$REPO_ROOT/scripts/parity-audit/parse_events.py" \
    --logcat "$RN_LOGCAT" --side rn --rn-parser-config "$RN_PARSER" --out "$RN_EVENTS"
  python3 "$REPO_ROOT/scripts/parity-audit/parse_events.py" \
    --logcat "$AN_LOGCAT" --side android --out "$AN_EVENTS"

  # 截图 SSIM
  SSIM_RESULTS="$SCEN_DIR/screenshot_ssim.json"
  python3 - <<PY > "$SSIM_RESULTS"
import json, pathlib, sys
sys.path.insert(0, "$REPO_ROOT/scripts/parity-audit")
import screenshot_ssim
rn_dir = pathlib.Path("$SCEN_DIR/rn")
an_dir = pathlib.Path("$SCEN_DIR/android")
out = []
for rn_img in sorted(rn_dir.glob("waypoint-*.png")):
    wp = rn_img.stem.replace("waypoint-", "")
    an_img = an_dir / rn_img.name
    if not an_img.exists():
        continue
    r = screenshot_ssim.compute(rn_img, an_img)
    r["waypoint"] = wp
    out.append(r)
print(json.dumps(out))
PY

  DIFF_JSON="$SCEN_DIR/diff.json"
  python3 "$REPO_ROOT/scripts/parity-audit/compare.py" \
    --scenario-id "$SID" \
    --rn-events "$RN_EVENTS" \
    --android-events "$AN_EVENTS" \
    --screenshot-results "$SSIM_RESULTS" \
    --out "$DIFF_JSON"

  VERDICT=$(python3 -c "import json,sys; print(json.load(open('$DIFF_JSON'))['verdict'])")
  SEVERITY=$(python3 -c "import json,sys; print(json.load(open('$DIFF_JSON'))['severity'])")
  EVT_COUNT=$(python3 -c "import json,sys; print(len(json.load(open('$DIFF_JSON'))['event_diffs']))")
  SCREEN_DIFF=$(python3 -c "import json,sys; print(sum(1 for s in json.load(open('$DIFF_JSON'))['screenshot_diffs'] if s['verdict']=='visual_diff'))")
  PRIORITY=$(python3 -c "import yaml,sys; print(yaml.safe_load(open('$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml'))['priority'])")
  OPEN_ISSUES_JSON="[]"

  python3 - <<PY
import json, pathlib, sys
sys.path.insert(0, "$REPO_ROOT/scripts/parity-audit")
import state as state_mod
p = pathlib.Path("$REPO_ROOT/docs/parity-audit/state.json")
s = state_mod.load(p)
state_mod.upsert_scenario(
    s, "$SID",
    priority="$PRIORITY",
    last_run_id="$RUN_ID",
    last_status="$VERDICT",
    open_issue_numbers=json.loads("""$OPEN_ISSUES_JSON"""),
    blocked_reason=None,
)
state_mod.save(s, p)
pathlib.Path("$REPO_ROOT/docs/parity-audit/queue.md").write_text(state_mod.render_queue_md(s))
PY

  echo "| $SID | $VERDICT | $SEVERITY | $SCREEN_DIFF | $EVT_COUNT |" >> "$REPORT"
  log_event '{"phase":"scenario","scenario":"'"$SID"'","event":"diff_computed","verdict":"'"$VERDICT"'","severity":"'"$SEVERITY"'"}'
done

{
  echo
  echo "## Notes"
  echo
  echo "- dry-run 阶段不创建 GitHub Issue（issue.md 草稿生成留到 Task 18/19 接入）。"
  echo "- agent.log.jsonl: \`$AGENT_LOG\`"
} >> "$REPORT"

log_event '{"phase":"end","event":"run_finished"}'
echo "REPORT: $REPORT"
```

- [ ] **Step 2: 加可执行位**

```bash
chmod +x scripts/parity-audit/audit.sh
```

- [ ] **Step 3: 端到端 v0 验收跑**

Run（确保已在 worktree 分支、设备已连、两侧已构建）:

```bash
bash scripts/parity-audit/audit.sh --scope page:home --mode dry-run --limit 1
```

Expected:
- exit 0
- 落 `docs/parity-audit/runs/<run-id>/REPORT.md`
- `docs/parity-audit/runs/<run-id>/scenarios/home_entry/{rn,android}/` 各有 logcat.raw.txt、events.jsonl、2 张截图、2 份 hierarchy XML
- `docs/parity-audit/runs/<run-id>/scenarios/home_entry/diff.json` 存在且 verdict 在 `passed / diff_found` 之中
- `docs/parity-audit/state.json` 中新增 `home_entry` 条目，`last_run_id == <run-id>`
- `docs/parity-audit/queue.md` 重写为含 home_entry 行
- **绝不能出现 `gh issue create` 调用**——检查 `agent.log.jsonl` 无 `issue_created` 事件即可

如果跑挂在某一步，按 `references/failure-modes.md` 表查处置方式；不要 fallback 到 `--no-verify` / 静默跳过。

- [ ] **Step 4: 把首次 dry-run 验收结果落到 REPORT 后提交**

```bash
git add scripts/parity-audit/audit.sh
git add docs/parity-audit/runs/  # 首次 dry-run baseline 留档
git add docs/parity-audit/state.json docs/parity-audit/queue.md
git commit -m "feat(parity-audit): audit.sh 顶层编排 + 首次 dry-run 端到端验收落档"
```

---

## Task 15: references/issue-template.md + labels.json

**Files:**
- Create: `.agents/skills/parity-audit-skill/references/issue-template.md`
- Create: `.agents/skills/parity-audit-skill/references/labels.json`

- [ ] **Step 1: 写 issue-template.md**

```markdown
<!-- parity-fingerprint: {{fingerprint}} -->
<!-- parity-run-id: {{run_id}} -->
<!-- parity-scenario: {{scenario_id}} -->

## 现象
{{summary}}

## 对比截图
| RN（参考实现） | Android（当前） |
|---|---|
| ![rn]({{rn_screenshot_url}}) | ![android]({{android_screenshot_url}}) |

> waypoint: `{{waypoint}}` · SSIM = `{{ssim}}`

## 复现步骤
{{repro_steps}}

## 期望（对齐 RN 行为）
{{expected}}

## 实际（Android 当前行为）
{{actual}}

## Event Diff 关键片段
```json
{{event_diff_snippet}}
```

## 修复指向
{{fix_hints}}

## 元数据
- scenario: `{{scenario_id}}` (priority=`{{priority}}`)
- run: `{{run_id}}` · severity: `{{severity}}` · kind: `{{kind}}`
- Android commit: `{{android_sha}}` · RN commit: `{{rn_sha}}` · 设备: `{{device_model}}` API {{api_level}}
{{#rn_also_crashed}}- ⚠ rn_also_crashed=true（本轮 RN baseline 同样崩溃，仅作为元数据上下文，不影响 Android 侧 Issue 的处理）{{/rn_also_crashed}}
```

模板使用 `{{var}}` 占位与 `{{#flag}}...{{/flag}}` 条件块（最小子集，由 `file_issue.py` 自己渲染，不依赖外部模板引擎）。

- [ ] **Step 2: 写 labels.json**

```json
{
  "schema_version": 1,
  "labels": [
    { "name": "parity-audit",          "color": "5319e7", "description": "由 parity-audit-skill 自动产出/管理" },
    { "name": "priority:core",         "color": "b60205", "description": "parity 核心优先级" },
    { "name": "priority:non-core",     "color": "fbca04", "description": "parity 非核心优先级" },
    { "name": "area:home",             "color": "0e8a16", "description": "首页页面相关" },
    { "name": "area:search",           "color": "0e8a16", "description": "搜索页面相关" },
    { "name": "area:player",           "color": "0e8a16", "description": "播放器/迷你播放器相关" },
    { "name": "area:plugin",           "color": "0e8a16", "description": "插件管理 / 订阅相关" },
    { "name": "area:settings",         "color": "0e8a16", "description": "设置页相关" },
    { "name": "area:local",            "color": "0e8a16", "description": "本地音乐相关" },
    { "name": "area:sheet",            "color": "0e8a16", "description": "歌单/榜单相关" },
    { "name": "area:musicDetail",      "color": "0e8a16", "description": "音乐详情页相关" },
    { "name": "area:other",            "color": "0e8a16", "description": "其它/未归类区域" },
    { "name": "kind:ui-gap",           "color": "1d76db", "description": "RN/Android UI 视觉或布局差异" },
    { "name": "kind:logic-gap",        "color": "1d76db", "description": "业务逻辑/交互行为差异" },
    { "name": "kind:missing-feature",  "color": "1d76db", "description": "Android 侧缺失 RN 已有功能" },
    { "name": "kind:crash",            "color": "1d76db", "description": "Android 进程崩溃 / ANR" },
    { "name": "kind:perf",             "color": "1d76db", "description": "性能 / 耗时显著差异" },
    { "name": "kind:error-divergence", "color": "1d76db", "description": "错误处理或降级行为差异" },
    { "name": "severity:major",        "color": "d93f0b", "description": "需要修复" },
    { "name": "severity:critical",     "color": "b60205", "description": "崩溃或核心链路阻塞" },
    { "name": "needs-retry",           "color": "c5def5", "description": "需要下一轮 audit 再次确认稳定性" },
    { "name": "flaky-flow",            "color": "c5def5", "description": "Maestro flow 不稳，需修 selector" },
    { "name": "manual-dedup-required", "color": "c5def5", "description": "指纹命中多个 Issue，需人工裁决" }
  ]
}
```

- [ ] **Step 3: Commit**

```bash
git add .agents/skills/parity-audit-skill/references/issue-template.md \
        .agents/skills/parity-audit-skill/references/labels.json
git commit -m "feat(parity-audit): issue body 模板与 label 注册表"
```

---

## Task 16: ensure-labels.sh（幂等创建 label）

**Files:**
- Create: `scripts/parity-audit/ensure-labels.sh`

- [ ] **Step 1: 写脚本**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/ensure-labels.sh
# 幂等创建 references/labels.json 中所有 label。已存在的 label 静默跳过。

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
LABELS_JSON="$REPO_ROOT/.agents/skills/parity-audit-skill/references/labels.json"
[ -f "$LABELS_JSON" ] || { echo "ERROR: labels.json not found at $LABELS_JSON" >&2; exit 1; }

python3 - "$LABELS_JSON" <<'PY'
import json, subprocess, sys
data = json.load(open(sys.argv[1]))
for lbl in data["labels"]:
    name = lbl["name"]
    color = lbl["color"]
    desc = lbl.get("description", "")
    result = subprocess.run(
        ["gh", "label", "create", name, "--color", color, "--description", desc],
        capture_output=True, text=True,
    )
    if result.returncode == 0:
        print(f"created label: {name}")
    elif "already exists" in (result.stderr or "") or "already exists" in (result.stdout or ""):
        # 顺手把 color/description 拉到与 labels.json 一致
        edit = subprocess.run(
            ["gh", "label", "edit", name, "--color", color, "--description", desc],
            capture_output=True, text=True,
        )
        if edit.returncode == 0:
            print(f"updated label: {name}")
        else:
            print(f"could not update {name}: {edit.stderr.strip()}", file=sys.stderr)
    else:
        print(f"could not create {name}: {result.stderr.strip()}", file=sys.stderr)
        sys.exit(2)
PY
```

- [ ] **Step 2: 加可执行位**

```bash
chmod +x scripts/parity-audit/ensure-labels.sh
```

- [ ] **Step 3: 手动跑两次验证幂等**

Run: `bash scripts/parity-audit/ensure-labels.sh`
Expected: 第一次列出 created label：xxx 若干行；第二次输出全部 `updated label：xxx`，exit 0。

如未 `gh auth login`，先登录；本脚本直接对仓库 origin 操作。

- [ ] **Step 4: Commit**

```bash
git add scripts/parity-audit/ensure-labels.sh
git commit -m "feat(parity-audit): ensure-labels.sh 幂等创建 GitHub label"
```

---

## Task 17: ensure-release.sh + upload-screenshots.sh

**Files:**
- Create: `scripts/parity-audit/ensure-release.sh`
- Create: `scripts/parity-audit/upload-screenshots.sh`

- [ ] **Step 1: 写 ensure-release.sh**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/ensure-release.sh
# 幂等创建 parity-screenshots prerelease。已存在则跳过。

set -euo pipefail

TAG="parity-screenshots"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "release $TAG already exists"
  exit 0
fi

gh release create "$TAG" --prerelease \
  --title "Parity screenshot bucket" \
  --notes "Auto-managed by parity-audit-skill. Do not edit by hand. See docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md §5.6."

echo "created release $TAG"
```

- [ ] **Step 2: 写 upload-screenshots.sh**

```bash
#!/usr/bin/env bash
# scripts/parity-audit/upload-screenshots.sh
# usage: upload-screenshots.sh <run_id> <scenario_id> <waypoint> <rn_png> <android_png>
# 输出两行：rn_url 与 android_url（按调用顺序）

set -euo pipefail

RUN_ID="$1"
SCENARIO="$2"
WAYPOINT="$3"
RN_PNG="$4"
ANDROID_PNG="$5"

TAG="parity-screenshots"
REPO_ROOT="$(git rev-parse --show-toplevel)"
REMOTE_URL=$(git -C "$REPO_ROOT" remote get-url origin)
# 解析 owner/repo（兼容 git@github.com:owner/repo.git 与 https）
OWNER_REPO=$(echo "$REMOTE_URL" | sed -E 's#^.*[:/]([^:/]+/[^/]+)\.git$#\1#')

RN_NAME="${RUN_ID}__${SCENARIO}__${WAYPOINT}__rn.png"
AN_NAME="${RUN_ID}__${SCENARIO}__${WAYPOINT}__android.png"

# Maestro 默认截图名是 waypoint id；这里复制并改名以平铺到 release flat 命名空间
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp "$RN_PNG"      "$TMP/$RN_NAME"
cp "$ANDROID_PNG" "$TMP/$AN_NAME"

gh release upload "$TAG" "$TMP/$RN_NAME" "$TMP/$AN_NAME" --clobber >&2

echo "https://github.com/${OWNER_REPO}/releases/download/${TAG}/${RN_NAME}"
echo "https://github.com/${OWNER_REPO}/releases/download/${TAG}/${AN_NAME}"
```

- [ ] **Step 3: 加可执行位**

```bash
chmod +x scripts/parity-audit/ensure-release.sh scripts/parity-audit/upload-screenshots.sh
```

- [ ] **Step 4: 手动验证一次**

```bash
bash scripts/parity-audit/ensure-release.sh
# 用 Task 14 验收时落档的一对截图做 dry-run 测试
LATEST=$(ls -1dt docs/parity-audit/runs/* | head -n1)
bash scripts/parity-audit/upload-screenshots.sh \
  "$(basename $LATEST)" home_entry 01_after_cold_start \
  "$LATEST/scenarios/home_entry/rn/01_after_cold_start.png" \
  "$LATEST/scenarios/home_entry/android/01_after_cold_start.png"
```

Expected: ensure-release.sh 第一次 `created release parity-screenshots`、第二次 `release ... already exists`；upload-screenshots.sh stdout 输出 2 行 release download URL，并能在浏览器打开。

> 注：如果你不想把 Task 14 的 baseline 截图作为永久 release 资产，跑完 Step 4 后到 GitHub 把这一对资产删掉；这次仅为脚本本身验证。

- [ ] **Step 5: Commit**

```bash
git add scripts/parity-audit/ensure-release.sh scripts/parity-audit/upload-screenshots.sh
git commit -m "feat(parity-audit): release 截图桶创建与上传脚本"
```

---

## Task 18: file_issue.py（指纹 + 模板渲染 + 查重 + Action 决策，TDD）

**Files:**
- Create: `scripts/parity-audit/file_issue.py`
- Create: `scripts/parity-audit/tests/test_file_issue.py`

- [ ] **Step 1: 写失败测试**

```python
# scripts/parity-audit/tests/test_file_issue.py
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import file_issue


def _diff(verdict="diff_found", severity="major", event_diffs=None, screenshot_diffs=None):
    return {
        "scenario_id": "home_entry",
        "verdict": verdict,
        "severity": severity,
        "event_diffs": event_diffs or [],
        "screenshot_diffs": screenshot_diffs or [],
    }


def test_fingerprint_is_stable_for_same_primary_signal():
    d1 = _diff(event_diffs=[
        {"waypoint": "01", "kind": "play.state_changed", "verdict": "value_mismatch",
         "field": "to", "rn": "PLAYING", "android": "BUFFERING"},
        {"waypoint": "02", "kind": "nav.enter", "verdict": "android_missing",
         "rn_only": [{"route": "MusicDetail", "params_hash": "abc"}]},
    ])
    d2 = _diff(event_diffs=[
        {"waypoint": "01", "kind": "play.state_changed", "verdict": "value_mismatch",
         "field": "to", "rn": "PLAYING", "android": "BUFFERING"},
        # nav diff 顺序变化，但 primary_signal 来自 play.state_changed，所以指纹不变
    ])
    assert file_issue.fingerprint("home_entry", "logic-gap", d1) \
        == file_issue.fingerprint("home_entry", "logic-gap", d2)


def test_fingerprint_differs_when_primary_signal_differs():
    d1 = _diff(event_diffs=[
        {"waypoint": "01", "kind": "play.state_changed", "verdict": "value_mismatch",
         "field": "to", "rn": "PLAYING", "android": "BUFFERING"},
    ])
    d2 = _diff(event_diffs=[
        {"waypoint": "01", "kind": "play.state_changed", "verdict": "value_mismatch",
         "field": "from", "rn": "IDLE", "android": "LOADING"},
    ])
    assert file_issue.fingerprint("home_entry", "logic-gap", d1) \
        != file_issue.fingerprint("home_entry", "logic-gap", d2)


def test_decide_kind_prefers_crash_then_logic_then_ui():
    assert file_issue.decide_kind(_diff(event_diffs=[
        {"kind": "error", "waypoint": "01", "verdict": "android_extra",
         "android_only": [{"domain": "anr", "code": "ANR", "message_hash": "x"}]}
    ])) == "crash"
    assert file_issue.decide_kind(_diff(event_diffs=[
        {"kind": "play.state_changed", "waypoint": "01", "verdict": "value_mismatch"}
    ])) == "logic-gap"
    assert file_issue.decide_kind(_diff(
        screenshot_diffs=[{"waypoint": "01", "ssim": 0.5, "verdict": "visual_diff"}]
    )) == "ui-gap"


def test_render_title_under_70_chars():
    title = file_issue.render_title(
        page="home",
        kind="logic-gap",
        summary="推荐位首屏 5 个 tag 缺失",
    )
    assert title.startswith("[parity][home][logic-gap]")
    assert len(title) <= 70


def test_render_body_substitutes_all_placeholders():
    body = file_issue.render_body(
        template="<!-- parity-fingerprint: {{fingerprint}} -->\n## 现象\n{{summary}}\n",
        ctx={"fingerprint": "abc123def456", "summary": "首屏推荐缺失"},
        flags={},
    )
    assert "abc123def456" in body
    assert "首屏推荐缺失" in body
    assert "{{" not in body


def test_render_body_handles_conditional_block_present():
    body = file_issue.render_body(
        template="{{#rn_also_crashed}}- RN also crashed{{/rn_also_crashed}}",
        ctx={},
        flags={"rn_also_crashed": True},
    )
    assert "- RN also crashed" in body


def test_render_body_handles_conditional_block_absent():
    body = file_issue.render_body(
        template="{{#rn_also_crashed}}- RN also crashed{{/rn_also_crashed}}\nrest",
        ctx={},
        flags={"rn_also_crashed": False},
    )
    assert "RN also crashed" not in body
    assert "rest" in body


def test_decide_action_create_when_no_match():
    action = file_issue.decide_action(existing=[])
    assert action == ("create", None)


def test_decide_action_comment_when_single_open_match():
    action = file_issue.decide_action(existing=[
        {"number": 42, "state": "OPEN", "title": "..."}
    ])
    assert action == ("comment", 42)


def test_decide_action_reopen_when_single_closed_match():
    action = file_issue.decide_action(existing=[
        {"number": 42, "state": "CLOSED", "title": "..."}
    ])
    assert action == ("reopen", 42)


def test_decide_action_skip_when_multiple_matches():
    action = file_issue.decide_action(existing=[
        {"number": 42, "state": "OPEN", "title": "..."},
        {"number": 43, "state": "CLOSED", "title": "..."},
    ])
    assert action[0] == "skip_multiple"
    assert sorted(action[1]) == [42, 43]


def test_build_gh_create_command_includes_labels_and_body_file(tmp_path):
    body_file = tmp_path / "body.md"
    body_file.write_text("hello")
    cmd = file_issue.build_gh_create_command(
        title="[parity][home][logic-gap] X",
        body_file=body_file,
        labels=["parity-audit", "priority:core", "area:home", "kind:logic-gap", "severity:major"],
    )
    assert cmd[:3] == ["gh", "issue", "create"]
    assert "--title" in cmd and cmd[cmd.index("--title") + 1].startswith("[parity]")
    assert "--body-file" in cmd
    # labels 全部以 --label 形式传
    label_args = [cmd[i + 1] for i, v in enumerate(cmd) if v == "--label"]
    assert "parity-audit" in label_args and "priority:core" in label_args
```

- [ ] **Step 2: 跑测试确认失败**

Run: `python3 -m pytest scripts/parity-audit/tests/test_file_issue.py -v`
Expected: FAIL，模块或函数不存在。

- [ ] **Step 3: 实现 file_issue.py**

```python
# scripts/parity-audit/file_issue.py
"""Compose & file parity audit GitHub issues.

Spec §5.1–§5.5。本模块严格分两层：
- 纯函数（fingerprint / decide_kind / render_title / render_body / decide_action /
  build_gh_create_command 等）易于单测；
- subprocess 层（`query_existing` / `execute_action`）只做 shell-out，业务逻辑零。
"""
from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import pathlib
import re
import subprocess
import sys
from typing import Any

# ---------------- pure logic ----------------

KIND_PRIORITY = ["crash", "logic-gap", "perf", "error-divergence", "missing-feature", "ui-gap"]
EVENT_KIND_PRIORITY = [
    "error",
    "play.state_changed",
    "plugin.method_called",
    "plugin.method_returned",
    "net.request",
    "net.response",
    "nav.enter",
    "nav.leave",
]


def fingerprint(scenario_id: str, kind: str, diff_json: dict[str, Any]) -> str:
    sig = _primary_signal(diff_json)
    raw = f"{scenario_id}|{kind}|{sig}"
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:12]


def _primary_signal(diff_json: dict[str, Any]) -> str:
    events = diff_json.get("event_diffs", [])
    for kind in EVENT_KIND_PRIORITY:
        for d in events:
            if d.get("kind") == kind:
                return _stable_event_signature(d)
    screens = diff_json.get("screenshot_diffs", [])
    visual = [s for s in screens if s.get("verdict") == "visual_diff"]
    if visual:
        return f"ui_only|{visual[0]['waypoint']}"
    return "no_signal"


def _stable_event_signature(d: dict[str, Any]) -> str:
    kind = d.get("kind", "")
    verdict = d.get("verdict", "")
    field = d.get("field", "")
    return f"{kind}|{verdict}|{field}"


def decide_kind(diff_json: dict[str, Any]) -> str:
    events = diff_json.get("event_diffs", [])
    # crash 检测：error kind 且 message_hash 提示崩 / domain in ANR/Tombstone
    for d in events:
        if d.get("kind") == "error":
            payloads = d.get("android_only", []) + d.get("rn_only", [])
            for p in payloads:
                if p.get("domain") in ("anr", "tombstone", "crash"):
                    return "crash"
            return "error-divergence"
    # 业务事件差异
    for d in events:
        if d.get("kind") in ("play.state_changed", "plugin.method_called",
                              "plugin.method_returned", "net.request", "net.response",
                              "nav.enter", "nav.leave"):
            if d.get("verdict") == "android_missing":
                return "missing-feature"
            return "logic-gap"
    # 仅视觉差
    if any(s.get("verdict") == "visual_diff" for s in diff_json.get("screenshot_diffs", [])):
        return "ui-gap"
    return "logic-gap"


def render_title(*, page: str, kind: str, summary: str) -> str:
    prefix = f"[parity][{page}][{kind}] "
    budget = 70 - len(prefix)
    short = summary if len(summary) <= budget else summary[: max(budget - 1, 1)] + "…"
    return prefix + short


_VAR_RE = re.compile(r"{{\s*([A-Za-z0-9_]+)\s*}}")
_BLOCK_RE = re.compile(r"{{#\s*([A-Za-z0-9_]+)\s*}}(.*?){{/\s*\1\s*}}", re.DOTALL)


def render_body(*, template: str, ctx: dict[str, Any], flags: dict[str, bool]) -> str:
    def block_repl(m: re.Match[str]) -> str:
        flag = m.group(1)
        return m.group(2) if flags.get(flag) else ""
    body = _BLOCK_RE.sub(block_repl, template)
    body = _VAR_RE.sub(lambda m: str(ctx.get(m.group(1), "")), body)
    return body


def decide_action(*, existing: list[dict[str, Any]]) -> tuple[str, Any]:
    """Return (action, payload).

    action ∈ {create, comment, reopen, skip_multiple}
    payload: None for create; issue number for comment/reopen; list[int] for skip_multiple.
    """
    if len(existing) == 0:
        return ("create", None)
    if len(existing) == 1:
        e = existing[0]
        state = e.get("state", "").upper()
        if state in ("OPEN",):
            return ("comment", e["number"])
        if state in ("CLOSED",):
            return ("reopen", e["number"])
        return ("comment", e["number"])
    return ("skip_multiple", sorted(int(e["number"]) for e in existing))


def build_gh_create_command(
    *,
    title: str,
    body_file: pathlib.Path,
    labels: list[str],
) -> list[str]:
    cmd: list[str] = ["gh", "issue", "create", "--title", title, "--body-file", str(body_file)]
    for lbl in labels:
        cmd += ["--label", lbl]
    return cmd


def build_gh_comment_command(*, issue_number: int, body_file: pathlib.Path) -> list[str]:
    return ["gh", "issue", "comment", str(issue_number), "--body-file", str(body_file)]


def build_gh_reopen_command(*, issue_number: int) -> list[str]:
    return ["gh", "issue", "reopen", str(issue_number)]


# ---------------- subprocess layer ----------------

def query_existing(fingerprint_hash: str) -> list[dict[str, Any]]:
    result = subprocess.run(
        [
            "gh", "issue", "list",
            "--state", "all",
            "--search", f'"parity-fingerprint: {fingerprint_hash}" in:body',
            "--json", "number,state,title,labels",
        ],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout or "[]")


def execute_action(action: tuple[str, Any], *, title: str, body_file: pathlib.Path,
                    labels: list[str], comment_body_file: pathlib.Path | None = None) -> int | None:
    name, payload = action
    if name == "create":
        cmd = build_gh_create_command(title=title, body_file=body_file, labels=labels)
        out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout.strip()
        # gh issue create 返回 URL，解析末段为 issue 号
        last = out.rsplit("/", 1)[-1]
        return int(last) if last.isdigit() else None
    if name == "comment":
        body = comment_body_file or body_file
        subprocess.run(build_gh_comment_command(issue_number=payload, body_file=body),
                       check=True)
        return payload
    if name == "reopen":
        subprocess.run(build_gh_reopen_command(issue_number=payload), check=True)
        body = comment_body_file or body_file
        subprocess.run(build_gh_comment_command(issue_number=payload, body_file=body),
                       check=True)
        return payload
    if name == "skip_multiple":
        print(f"manual_dedup_required: matched {payload}", file=sys.stderr)
        return None
    raise ValueError(f"unknown action {name}")


# ---------------- CLI ----------------

@dataclasses.dataclass
class IssueComposeResult:
    title: str
    body_path: pathlib.Path
    labels: list[str]
    fingerprint: str
    action: tuple[str, Any]
    issue_number: int | None


def compose_and_file(
    *,
    diff_json_path: pathlib.Path,
    scenario_yaml_path: pathlib.Path,
    template_path: pathlib.Path,
    run_id: str,
    rn_sha: str,
    android_sha: str,
    device_model: str,
    api_level: str,
    rn_screenshot_url: str,
    android_screenshot_url: str,
    summary: str,
    repro_steps: str,
    expected: str,
    actual: str,
    fix_hints: str,
    rn_also_crashed: bool,
    output_dir: pathlib.Path,
    mode: str,
) -> IssueComposeResult:
    import yaml
    diff = json.loads(diff_json_path.read_text())
    scenario = yaml.safe_load(scenario_yaml_path.read_text())
    template = template_path.read_text()

    kind = decide_kind(diff)
    fp = fingerprint(scenario["id"], kind, diff)
    title = render_title(page=scenario["page"], kind=kind, summary=summary)

    visual = diff.get("screenshot_diffs", [])
    waypoint = visual[0]["waypoint"] if visual else "—"
    ssim = visual[0].get("ssim", "—") if visual else "—"

    severity = diff.get("severity", "major")
    event_snippet = json.dumps(diff.get("event_diffs", []), ensure_ascii=False, indent=2)

    body = render_body(
        template=template,
        ctx={
            "fingerprint": fp,
            "run_id": run_id,
            "scenario_id": scenario["id"],
            "summary": summary,
            "rn_screenshot_url": rn_screenshot_url,
            "android_screenshot_url": android_screenshot_url,
            "waypoint": waypoint,
            "ssim": ssim,
            "repro_steps": repro_steps,
            "expected": expected,
            "actual": actual,
            "event_diff_snippet": event_snippet,
            "fix_hints": fix_hints,
            "priority": scenario.get("priority", "core"),
            "severity": severity,
            "kind": kind,
            "android_sha": android_sha,
            "rn_sha": rn_sha,
            "device_model": device_model,
            "api_level": api_level,
        },
        flags={"rn_also_crashed": rn_also_crashed},
    )

    output_dir.mkdir(parents=True, exist_ok=True)
    body_path = output_dir / "issue.md"
    body_path.write_text(body)

    labels = [
        "parity-audit",
        f"priority:{scenario.get('priority', 'core')}",
        f"area:{scenario.get('page', 'other')}",
        f"kind:{kind}",
        f"severity:{severity}",
    ]
    if kind in ("perf", "error-divergence"):
        labels.append("needs-retry")

    if mode == "dry-run":
        return IssueComposeResult(title=title, body_path=body_path, labels=labels,
                                  fingerprint=fp, action=("dry_run", None),
                                  issue_number=None)

    existing = query_existing(fp)
    action = decide_action(existing=existing)
    issue_number = execute_action(action, title=title, body_file=body_path, labels=labels)
    return IssueComposeResult(title=title, body_path=body_path, labels=labels,
                              fingerprint=fp, action=action, issue_number=issue_number)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--diff", required=True)
    p.add_argument("--scenario-yaml", required=True)
    p.add_argument("--template", required=True)
    p.add_argument("--run-id", required=True)
    p.add_argument("--rn-sha", required=True)
    p.add_argument("--android-sha", required=True)
    p.add_argument("--device-model", required=True)
    p.add_argument("--api-level", required=True)
    p.add_argument("--rn-screenshot-url", required=True)
    p.add_argument("--android-screenshot-url", required=True)
    p.add_argument("--summary", required=True)
    p.add_argument("--repro-steps", required=True)
    p.add_argument("--expected", required=True)
    p.add_argument("--actual", required=True)
    p.add_argument("--fix-hints", required=True)
    p.add_argument("--rn-also-crashed", default="false")
    p.add_argument("--output-dir", required=True)
    p.add_argument("--mode", required=True, choices=("dry-run", "audit"))
    args = p.parse_args()

    res = compose_and_file(
        diff_json_path=pathlib.Path(args.diff),
        scenario_yaml_path=pathlib.Path(args.scenario_yaml),
        template_path=pathlib.Path(args.template),
        run_id=args.run_id,
        rn_sha=args.rn_sha,
        android_sha=args.android_sha,
        device_model=args.device_model,
        api_level=args.api_level,
        rn_screenshot_url=args.rn_screenshot_url,
        android_screenshot_url=args.android_screenshot_url,
        summary=args.summary,
        repro_steps=args.repro_steps,
        expected=args.expected,
        actual=args.actual,
        fix_hints=args.fix_hints,
        rn_also_crashed=(args.rn_also_crashed.lower() == "true"),
        output_dir=pathlib.Path(args.output_dir),
        mode=args.mode,
    )

    print(json.dumps({
        "title": res.title,
        "body_path": str(res.body_path),
        "labels": res.labels,
        "fingerprint": res.fingerprint,
        "action": res.action[0],
        "action_payload": res.action[1],
        "issue_number": res.issue_number,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 4: 跑测试确认通过**

Run: `python3 -m pytest scripts/parity-audit/tests/test_file_issue.py -v`
Expected: 11 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add scripts/parity-audit/file_issue.py scripts/parity-audit/tests/test_file_issue.py
git commit -m "feat(parity-audit): file_issue.py 指纹/模板/查重/Action 决策（含 TDD）"
```

---

## Task 19: 把 Issue 创建接入 audit.sh（支持 --mode audit）

**Files:**
- Modify: `scripts/parity-audit/audit.sh`

- [ ] **Step 1: 重写 audit.sh 顶部参数解析与 mode 处理**

把 Task 14 中"v0 仅支持 dry-run"那块替换为：

```bash
# audit.sh 入口仍支持 --mode audit | dry-run
if [ "$MODE" != "audit" ] && [ "$MODE" != "dry-run" ]; then
  echo "ERROR: --mode must be audit or dry-run" >&2; exit 1
fi

if [ "$MODE" = "audit" ]; then
  bash "$REPO_ROOT/scripts/parity-audit/ensure-labels.sh"
  bash "$REPO_ROOT/scripts/parity-audit/ensure-release.sh"
fi
```

- [ ] **Step 2: 把"diff 计算完毕后"分支扩展为：渲染 issue.md，必要时上传截图 + 创建/评论 Issue**

在 audit.sh 现有 `compare.py ... --out "$DIFF_JSON"` 之后、`VERDICT=$(...)` 之后、`python3 - <<PY ... state.upsert_scenario ... PY` 之前，插入：

```bash
  # 取额外元数据
  RN_SHA=$(git -C ../MusicFree rev-parse HEAD 2>/dev/null || echo "unknown")
  ANDROID_SHA=$(git rev-parse HEAD)
  DEVICE_MODEL=$(adb ${DEVICE:+-s $DEVICE} shell getprop ro.product.model | tr -d '\r')
  API_LEVEL=$(adb ${DEVICE:+-s $DEVICE} shell getprop ro.build.version.sdk | tr -d '\r')

  # 仅当 verdict=diff_found && severity ∈ {major, critical} 才走 Issue 路径
  SHOULD_FILE=$(python3 -c "
import json
d = json.load(open('$DIFF_JSON'))
print('yes' if d['verdict'] == 'diff_found' and d['severity'] in ('major','critical') else 'no')
")

  RN_URL=""
  AN_URL=""
  if [ "$SHOULD_FILE" = "yes" ] && [ "$MODE" = "audit" ]; then
    # 取 SSIM < 阈值的第一个 waypoint 截图作为 Issue body 引用
    WP=$(python3 -c "
import json
d = json.load(open('$DIFF_JSON'))
vis = [s for s in d.get('screenshot_diffs', []) if s.get('verdict') == 'visual_diff']
print(vis[0]['waypoint'] if vis else '')
")
    if [ -n "$WP" ]; then
      RN_PNG="$SCEN_DIR/rn/${WP}.png"
      AN_PNG="$SCEN_DIR/android/${WP}.png"
      [ -f "$RN_PNG" ] || RN_PNG=$(ls "$SCEN_DIR/rn"/*.png 2>/dev/null | head -n1)
      [ -f "$AN_PNG" ] || AN_PNG=$(ls "$SCEN_DIR/android"/*.png 2>/dev/null | head -n1)
      URLS=$(bash "$REPO_ROOT/scripts/parity-audit/upload-screenshots.sh" \
        "$RUN_ID" "$SID" "$WP" "$RN_PNG" "$AN_PNG")
      RN_URL=$(echo "$URLS" | sed -n '1p')
      AN_URL=$(echo "$URLS" | sed -n '2p')
    fi
  fi

  if [ "$SHOULD_FILE" = "yes" ]; then
    # 用本地 LLM-friendly 占位填 file_issue.py 的人写字段。v0 不让 LLM 真写叙述
    # （留给后续 plan 集成）；这里仅产稳定 fallback，保证 Issue body 不空。
    SUMMARY=$(python3 -c "
import json
d = json.load(open('$DIFF_JSON'))
parts = []
for e in d.get('event_diffs', [])[:3]:
    parts.append(f\"{e.get('waypoint','?')}/{e.get('kind','?')} {e.get('verdict','?')}\")
print('Android 与 RN 行为差异：' + '; '.join(parts) if parts else 'Android 与 RN 出现可视差异（见截图）')
")
    REPRO_STEPS=$(python3 -c "
import yaml
d = yaml.safe_load(open('$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml'))
lines = ['1. 冷启动 Android debug build']
for i, wp in enumerate(d.get('waypoints', []), start=2):
    lines.append(f\"{i}. 等到 waypoint: {wp['id']}（{wp.get('description','')}）\")
print('\n'.join(lines))
")
    EXPECTED="对齐 RN 同 waypoint 的事件流与视觉表现（详见 Event Diff 节）。"
    ACTUAL="见上方 Event Diff 与对比截图。"
    FIX_HINTS=$(python3 -c "
import yaml
d = yaml.safe_load(open('$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml'))
notes = d.get('notes', '').strip().split('\n')
print('\n'.join(f'- {n}' for n in notes if n))
")
    RN_ALSO=$(python3 -c "
import json, pathlib
log = pathlib.Path('$SCEN_DIR/rn/logcat.raw.txt').read_text(errors='replace') if pathlib.Path('$SCEN_DIR/rn/logcat.raw.txt').exists() else ''
crashed = 'FATAL EXCEPTION' in log or 'AndroidRuntime' in log
print('true' if crashed else 'false')
")

    FILE_ISSUE_OUT="$SCEN_DIR/issue_action.json"
    python3 "$REPO_ROOT/scripts/parity-audit/file_issue.py" \
      --diff "$DIFF_JSON" \
      --scenario-yaml "$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml" \
      --template "$REPO_ROOT/.agents/skills/parity-audit-skill/references/issue-template.md" \
      --run-id "$RUN_ID" \
      --rn-sha "$RN_SHA" \
      --android-sha "$ANDROID_SHA" \
      --device-model "$DEVICE_MODEL" \
      --api-level "$API_LEVEL" \
      --rn-screenshot-url "$RN_URL" \
      --android-screenshot-url "$AN_URL" \
      --summary "$SUMMARY" \
      --repro-steps "$REPRO_STEPS" \
      --expected "$EXPECTED" \
      --actual "$ACTUAL" \
      --fix-hints "$FIX_HINTS" \
      --rn-also-crashed "$RN_ALSO" \
      --output-dir "$SCEN_DIR" \
      --mode "$MODE" \
      > "$FILE_ISSUE_OUT"

    ISSUE_NUMBER=$(python3 -c "import json; print(json.load(open('$FILE_ISSUE_OUT')).get('issue_number') or '')")
    if [ -n "$ISSUE_NUMBER" ]; then
      OPEN_ISSUES_JSON="[$ISSUE_NUMBER]"
      log_event '{"phase":"issue","scenario":"'"$SID"'","event":"issue_done","number":'"$ISSUE_NUMBER"',"mode":"'"$MODE"'"}'
    fi
  fi
```

- [ ] **Step 3: 更新 REPORT 表头与末尾 Notes**

把 REPORT 表头改为：

```bash
{
  echo "# Parity Audit Run $RUN_ID"
  echo
  echo "- scope: \`$SCOPE\`  · mode: \`$MODE\`  · limit: $LIMIT"
  echo "- RN APK: \`$RN_APK\`"
  echo "- Android APK: \`$ANDROID_APK\`"
  echo
  echo "## Scenarios"
  echo
  echo "| scenario | verdict | severity | kind | screenshot diff | event diff count | issue |"
  echo "|---|---|---|---|---|---|---|"
} > "$REPORT"
```

并把每行 `echo "| $SID | ... | ... |" >> "$REPORT"` 改为：

```bash
  KIND=$(python3 -c "
import json, sys
sys.path.insert(0, '$REPO_ROOT/scripts/parity-audit')
import file_issue
print(file_issue.decide_kind(json.load(open('$DIFF_JSON'))))
" 2>/dev/null || echo "—")
  ISSUE_LINK=$(python3 -c "
import json, pathlib
p = pathlib.Path('$SCEN_DIR/issue_action.json')
if not p.exists():
    print('—')
else:
    d = json.load(open(p))
    n = d.get('issue_number')
    if d.get('action') == 'dry_run':
        print('(dry-run draft)')
    elif n:
        print(f'#{n} ({d.get(\"action\")})')
    elif d.get('action') == 'skip_multiple':
        print(f'skip ({d.get(\"action_payload\")})')
    else:
        print('—')
")
  echo "| $SID | $VERDICT | $SEVERITY | $KIND | $SCREEN_DIFF | $EVT_COUNT | $ISSUE_LINK |" >> "$REPORT"
```

把末尾 Notes 段改为：

```bash
{
  echo
  echo "## Notes"
  echo
  echo "- mode=\`$MODE\`：dry-run 仅生成 issue.md 草稿；audit 通过 \`gh issue create\` 提交。"
  echo "- agent.log.jsonl: \`$AGENT_LOG\`"
} >> "$REPORT"
```

- [ ] **Step 4: 跑 dry-run 端到端不应回归**

Run: `bash scripts/parity-audit/audit.sh --scope page:home --mode dry-run --limit 1`
Expected:
- exit 0
- REPORT 末尾有"## Notes"段，`mode=dry-run`
- 若 home_entry 跑出 `verdict=passed`，REPORT 的 issue 列应为 `—`；若跑出 `diff_found && severity in {major,critical}`，issue 列应为 `(dry-run draft)`，且 `docs/parity-audit/runs/<run-id>/scenarios/home_entry/issue.md` 与 `issue_action.json` 均存在
- 全程**不能**调 `gh issue create`（用 `agent.log.jsonl` 检查无 `issue_created` 事件）

- [ ] **Step 5: Commit**

```bash
git add scripts/parity-audit/audit.sh
git commit -m "feat(parity-audit): audit.sh 接入 Issue 创建/查重/上传截图链路（支持 --mode audit）"
```

---

## Task 20: 真实 `--mode audit` 端到端验收

> ⚠ 本任务会**真正创建一个 GitHub Issue**到当前仓库 origin。执行者请确认这是预期。如果不想往真仓库写，可以临时把 origin 改到 fork，跑完再切回。

**Files:** 仅运行验证，不新增文件。

- [ ] **Step 1: 选一个能稳定出 diff 的 scenario**

在已经验证过 dry-run 的 worktree 内，确认 home_entry 在当前实际仓库状态下能跑出 `verdict=diff_found && severity in {major,critical}`。如果 home_entry 当前是 `passed`，临时调高 `screenshot_ssim_min`（如 `0.99`）让 SSIM 必然不达标：

```bash
python3 - <<'PY'
import yaml, pathlib
p = pathlib.Path("docs/parity-audit/scenarios/home_entry.yaml")
d = yaml.safe_load(p.read_text())
d["diff_tolerance"]["screenshot_ssim_min"] = 0.99
p.write_text(yaml.safe_dump(d, allow_unicode=True))
PY
```

跑完 Step 4 验证 dedup 之后记得把阈值恢复 0.92。

- [ ] **Step 2: 跑一次真实 audit**

```bash
bash scripts/parity-audit/audit.sh --scope page:home --mode audit --limit 1
```

Expected：
- exit 0
- 在 GitHub Issue 列表里出现一个新 Issue，title 形如 `[parity][home][...] ...`，labels 含 `parity-audit / priority:core / area:home / kind:... / severity:...`
- Issue body 包含两张 release 资产截图（点开能看到）
- Issue body 顶部有 `<!-- parity-fingerprint: <12位 hex> -->`
- `agent.log.jsonl` 内有 `phase=issue, event=issue_done, mode=audit, number=<#>`

- [ ] **Step 3: 立刻再跑一次，验证 dedup**

```bash
bash scripts/parity-audit/audit.sh --scope page:home --mode audit --limit 1
```

Expected：
- 不创建新 Issue
- 同一 Issue 收到一条新评论（追加"本轮仍复现"内容）
- `agent.log.jsonl` 中第二轮 `phase=issue` 的 `action=comment`

- [ ] **Step 4: 收尾**

- 恢复 home_entry.yaml 中 `screenshot_ssim_min` 为 `0.92`
- 把 Step 2/3 跑出的真实 run 目录留档进 git（与 Task 14 一样作为 baseline）
- 把刚才创建的 Issue **不要 close** —— 自愈侧的提醒评论留给后续 plan 验证。仅在 Issue 本身明显是噪声（例如阈值临时调到 0.99 造成的非真实 diff）时，由人工手动 close 即可

```bash
git add docs/parity-audit/scenarios/home_entry.yaml docs/parity-audit/runs/ docs/parity-audit/state.json docs/parity-audit/queue.md
git commit -m "feat(parity-audit): 首次 --mode audit 端到端验收（真创建 + dedup 评论）"
```

---

## Task 21: 文档 cross-link 与 AGENTS.md 更新

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/dev-harness/INDEX.md`

- [ ] **Step 1: 在 AGENTS.md "项目记忆与守门约束" 段后追加 parity-audit 入口**

Read 当前 `AGENTS.md` 找 "项目记忆与守门约束" 段，在其后追加：

```markdown

## Parity Audit Skill 入口

跨工具调用的 RN/Android 对齐扫描 sub-agent，触发短语见 `.agents/skills/parity-audit-skill/SKILL.md`。状态文件 `docs/parity-audit/state.json` + `queue.md` 为唯一可信源。v0 仅支持 `mode=dry-run`，不会创建 Issue。设计 spec：`docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`。
```

- [ ] **Step 2: 在 dev-harness INDEX.md 末尾加一条"非 dev-harness 但相关"指引**

Read `docs/dev-harness/INDEX.md`，在文末追加：

```markdown

## Adjacent: Parity Audit Skill

并非 dev-harness 域，但产出物（Issue 草稿、scenario catalog、运行产物）与 dev-harness incidents 互补。详见 `.agents/skills/parity-audit-skill/SKILL.md` 与 `docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`。
```

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md docs/dev-harness/INDEX.md
git commit -m "docs(parity-audit): 在 AGENTS.md 与 dev-harness INDEX 补 parity-audit 入口"
```

---

## Task 22: Plan 收口自查与 squash 准备

- [ ] **Step 1: 跑全部 Python 单测一次**

Run: `python3 -m pytest scripts/parity-audit/tests/ -v`
Expected: 全部 PASS（state 4 + parse_events 3 + screenshot_ssim 3 + compare 4 + file_issue 11 = 25 个测试）。

- [ ] **Step 2: 跑 logging 模块单测**

Run: `./gradlew :logging:testDebugUnitTest`
Expected: 全部 PASS。

- [ ] **Step 3: dev-harness 自查**

Run: `bash scripts/dev-harness/check.sh`
Expected: 通过。如有报告 parity-audit 相关问题，按报告修正。

- [ ] **Step 4: 重新跑一次端到端 dry-run 验收**

```bash
bash scripts/parity-audit/audit.sh --scope page:home --mode dry-run --limit 1
```

Expected: 与 Task 14 / Task 19 Step 4 一致，全程 exit 0，REPORT.md 写出，无真实 `gh issue create`。

- [ ] **Step 5: 在 worktree 内确认所有 commit 干净**

Run: `git -C $(pwd) log --oneline main..HEAD`
Expected: 一组按 Task 顺序的 conventional commit 列表，每条 message 中文 ≤ 72 字符。

- [ ] **Step 6: 切回主 worktree 并 squash 合并**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git merge --squash parity-audit-v0
git commit -m "feat(parity-audit): 完整 parity audit 流水线（skill + 脚本 + 单 scenario + 真创建 Issue）"
```

按 `AGENTS.md` Git Worktree 开发约束，所有 commit squash 到单条；commit message 中文，conventional commit 范围 `parity-audit`。

- [ ] **Step 7: 清理 worktree**

```bash
git worktree remove .worktrees/parity-audit-v0
git branch -d parity-audit-v0
```

---

## Self-Review Notes

- **Spec coverage**：本 plan 覆盖 spec §1（skill 形态、路径、SKILL.md 强约束）、§2（state/queue/scenario catalog，但仅 1 个手写 scenario，自发生成留后续 plan）、§3（preflight + run-scenario + SSIM 预筛）、§4（taxonomy + Android `ParityEventSink` + RN parser，patched 模式留 v3）、§5（issue-template / 指纹 / 查重 / label 自动管理 / release 上传 / 真创建 Issue 全部接入）、§6.1（agent.log.jsonl）、§6.2（main 禁跑、scenario YAML 不动 priority 不删 scenario、agent 不 close Issue 等约束写进 SKILL.md 与 file_issue.py 行为）、§6.3（tool-host-notes.md）、§6.4（失败矩阵）。**未覆盖**（明确推迟到后续 plan）：§4.2 patched 模式、Phase 1/2 自发生成 scenario、自愈侧"连续 N 轮 passed → comment 提醒"、`mode=replay`。
- **Type 一致性**：`ParityEventSink.emit(event, fields)` 在测试与实现签名一致；`compare.run(...)` 在 test_compare 与 compare.py 签名一致；`state.upsert_scenario` 与 `state.render_queue_md` 在 test_state 与 state.py 签名一致；`file_issue.fingerprint / decide_kind / render_title / render_body / decide_action / build_gh_create_command` 在 test_file_issue 与 file_issue.py 签名一致。
- **Placeholder 扫描**：plan 内无 `TBD` / `TODO`。明确允许执行者改写的部分（不是隐藏工作，已显式标注）：Task 3 Step 3 的 `parity-plugins.json` 订阅 URL、Task 8/9 的 Maestro selector 骨架、Task 19 Step 2 中 LLM-friendly 字段当前用 Python fallback 拼装。
- **隐患 1（Maestro 静态校验缺失）**：flow 文件正确性只能在真机跑里验证。建议在 Task 8/9 的第一遍 selector 探查时先手动确认，不要等到 audit.sh 顶层跑挂再回溯。
- **隐患 2（Task 20 会真创建 Issue）**：Task 20 显式标注会真往 origin 写一个 Issue。如果执行者不愿往真仓库写，可先切 origin 到 fork 跑通再切回。
- **隐患 3（Issue body 当前由 Python fallback 拼装）**：Task 19 中 SUMMARY / REPRO / EXPECTED / ACTUAL / FIX_HINTS 当前是确定性 fallback，质量"够明确，但不够灵动"。后续 plan 把 LLM 叙述接入 SKILL.md 的"读 diff.json + 关键 hierarchy → 写 diff.md，然后再喂给 file_issue.py"链路。
