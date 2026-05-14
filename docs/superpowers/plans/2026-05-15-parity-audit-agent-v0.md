# Parity Audit Agent v0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md` 设计的 parity audit 流水线落到 v0 状态——一次 `mode=dry-run scope=page:home limit=1` 调用能完成"build RN+Android → 装两侧 APK → 跑 home_entry scenario → 抓截图+logcat → 输出 diff.json + REPORT.md + 更新 state.json"，但**不创建 Issue、不上传截图到 release**。

**Architecture:** Skill 是入口文档（位于 `.agents/skills/parity-audit-skill/`），背后的确定性步骤由 `scripts/parity-audit/*.{sh,py}` 承载，事件源来自 `:logging` 模块新增的 `ParityEventSink` + RN 默认 logcat 正则解析；Maestro flow 文件 v0 由人工手写（仅 home_entry 一个 scenario），Phase 1 自发生成留给 v1。状态、scenario catalog、运行产物全部落在 `docs/parity-audit/` 下，便于 review 与历史回溯。

**Tech Stack:** Bash + Python 3 (`scikit-image` / `opencv-python` for SSIM)、Maestro CLI、ADB、Kotlin (`:logging` 模块)、`gh` CLI（v0 仅用于环境校验，不真正创建 Issue）。

**Spec 参照**：`docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md`（commit `524214c0` 之后版本）。本 plan 引用的 §x.y 编号均指 spec 编号。

---

## File Structure（v0 范围）

```
.agents/skills/parity-audit-skill/
  SKILL.md                              # 入口文档（工具中性）
  references/
    event-taxonomy.md                   # v1 用到的 6 类事件 schema
    rn-logcat-parser.json               # RN 默认 logcat → taxonomy 子集映射
    failure-modes.md                    # 决策矩阵（v0 简版）
    tool-host-notes.md                  # Claude Code / Codex 等价命令

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

不在 v0 范围（明确推迟）：
- `references/issue-template.md`、`references/labels.json`、`references/scenario-authoring.md`（v1）
- `scripts/parity-audit/file_issue.py`、`upload-screenshots.sh`、`apply_rn_patch.sh`（v1）
- Phase 1 自发生成 scenario（v1）
- Issue 指纹与 dedup 逻辑（v1）
- Release 资产桶（v1）
- RN parity logger 补丁（v3）

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

# Parity Audit Skill (v0)

## 用途

把 RN MusicFree（参考实现）与 MusicFreeAndroid 在编译产物上的行为差异流水线化抓取并归集。v0 仅完成端到端管道，**不建 Issue、不上传截图**。

## 调用契约

| 参数 | 取值 | 默认 |
|---|---|---|
| `--scope` | `core` / `non-core` / `all` / `page:<id>` | 读 `docs/parity-audit/state.json.next_recommended` |
| `--mode` | `audit` / `dry-run` / `replay:<run-id>` | `audit`，v0 实际只跑 `dry-run` |
| `--limit` | 整数 | `5` |
| `--device` | adb serial | 取 `adb devices` 第一行 |

## 调用流程

1. 读 `docs/parity-audit/state.json` 与 `queue.md`
2. 检查当前 git 分支不是 `main`（在 `main` 上拒绝执行，提示切到 worktree）
3. 跑 `scripts/parity-audit/audit.sh --scope <...> --mode dry-run --limit <...>`
4. audit.sh 内部串联：bootstrap → build → install → install-plugins → 循环 scenario(run-scenario → parse-events → screenshot-ssim → compare) → state 更新 → REPORT.md
5. v0 末尾不调 `file_issue.py`，仅打印 `REPORT.md` 路径

## 强约束

- 任何 Maestro flow 缺失 / 构建失败 / 设备未连，必须把 scenario 标 `blocked_*` 写入 state，不能沉默跳过
- 在 `main` 分支拒绝执行
- v0 阶段任何"创建 Issue"动作都视为 bug——必须只到 `issue.md` 草稿落盘为止

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
# Failure Modes (v0)

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
| RN 进程崩 | scenario 状态 `rn_baseline_unstable` | v0 不建 Issue，REPORT 高亮 |
| Android 进程崩 / ANR | scenario 状态 `diff_found severity=critical kind=crash` | v0 同样不建 Issue，但 issue.md 落盘 |

v0 所有 Issue 触发条件等价于"生成 issue.md 草稿"，不真正调 `gh issue create`。
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

# v0 仅支持 dry-run
if [ "$MODE" != "dry-run" ]; then
  echo "WARN: v0 only supports --mode dry-run; ignoring --mode=$MODE and continuing as dry-run" >&2
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
  echo "## Notes (v0)"
  echo
  echo "- v0 不创建 GitHub Issue；issue.md 草稿生成留给 v1。"
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

- [ ] **Step 4: 把 v0 验收结果落到 REPORT 后提交**

```bash
git add scripts/parity-audit/audit.sh
git add docs/parity-audit/runs/  # 把首次 v0 run 当作 baseline 留档
git add docs/parity-audit/state.json docs/parity-audit/queue.md
git commit -m "feat(parity-audit): audit.sh 顶层编排 + 首次 v0 端到端验收落档"
```

---

## Task 15: 文档 cross-link 与 AGENTS.md 更新

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

## Task 16: Plan 收口自查与 squash 准备

- [ ] **Step 1: 跑全部 Python 单测一次**

Run: `python3 -m pytest scripts/parity-audit/tests/ -v`
Expected: 全部 PASS（state 4 + parse_events 3 + screenshot_ssim 3 + compare 4 = 14 个测试）。

- [ ] **Step 2: 跑 logging 模块单测**

Run: `./gradlew :logging:testDebugUnitTest`
Expected: 全部 PASS。

- [ ] **Step 3: dev-harness 自查**

Run: `bash scripts/dev-harness/check.sh`
Expected: 通过。如有报告 parity-audit 相关问题，按报告修正。

- [ ] **Step 4: 重新跑一次端到端验收**

```bash
bash scripts/parity-audit/audit.sh --scope page:home --mode dry-run --limit 1
```

Expected: 与 Task 14 Step 3 一致，全程 exit 0，REPORT.md 写出。

- [ ] **Step 5: 在 worktree 内确认所有 commit 干净**

Run: `git -C $(pwd) log --oneline main..HEAD`
Expected: 一组按 Task 顺序的 conventional commit 列表，每条 message 中文 ≤ 72 字符。

- [ ] **Step 6: 切回主 worktree 并 squash 合并**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git merge --squash parity-audit-v0
git commit -m "feat(parity-audit): v0 parity audit 流水线（skill + 脚本 + 单 scenario 端到端）"
```

按 `AGENTS.md` Git Worktree 开发约束，所有 commit squash 到单条；commit message 中文，conventional commit 范围 `parity-audit`。

- [ ] **Step 7: 清理 worktree**

```bash
git worktree remove .worktrees/parity-audit-v0
git branch -d parity-audit-v0
```

---

## Self-Review Notes

- **Spec coverage**：本 plan 覆盖 spec §1（skill 形态、路径、SKILL.md 强约束）、§2（state/queue/scenario catalog，但 v0 仅 1 个手写 scenario，自发生成留 v1）、§3（preflight + run-scenario + SSIM 预筛）、§4（taxonomy + Android `ParityEventSink` + RN parser，patched 模式留 v3）、§6.1（agent.log.jsonl）、§6.2（main 禁跑、scenario YAML 不动 priority 不删 scenario 等约束写进 SKILL.md）、§6.3（tool-host-notes.md）、§6.4（失败矩阵 v0 简版）。**未覆盖**（明确推迟）：§5（Issue 生成、指纹、dedup、label、release 上传）、§4.2 patched 模式、Phase 1/2 自发生成、自愈侧 reopen 提醒、`mode=replay`。
- **Type 一致性**：`ParityEventSink.emit(event, fields)` 在 §11 Step 2 测试与 §11 Step 4 实现签名一致；`compare.run(...)` 在 test_compare 与 compare.py 签名一致；`state.upsert_scenario` 与 `state.render_queue_md` 在 test_state 与 state.py 签名一致。
- **Placeholder 扫描**：plan 内无 `TBD` / `TODO`，但 Task 3 Step 3 标注 `parity-plugins.json` 中订阅 URL 是 v0 占位、Task 8/9 中 Maestro selector 是骨架——这两处明确要求任务执行者在真机上探查后替换，不是隐藏的工作。
- **唯一隐患**：Maestro 静态校验工具缺失，flow 文件正确性只能在 Task 9 Step 4 / Task 10 Step 3 / Task 14 Step 3 的真机跑里验证。建议任务执行者在 Task 8/9 的第一遍 selector 探查时**先停下来手动确认**，不要等到 audit.sh 顶层跑挂再回溯。
