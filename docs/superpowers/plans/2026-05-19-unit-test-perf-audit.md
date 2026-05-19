# 单元测试耗时审计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性扫描所有 JVM 单测（`testDebugUnitTest`），找出 `time > 2.0s` 的慢测，对每个慢测给出「成因初诊 + 必要性分类 + 处置建议」，落地为 `docs/test-perf-audit/REPORT.md`，不修改任何测试代码或 build 配置。

**Architecture:** Python 单文件 CLI 解析所有模块的 `build/test-results/testDebugUnitTest/TEST-*.xml`，产出 `slow-cases.json` + `module-totals.json`；AI 按 spec 中的判定流程（守门型 → 回归型 → 高价重复型 → 低价值型，逐级降级）逐个分类，写入 Markdown 报告。

**Tech Stack:** Python 3 stdlib（`xml.etree.ElementTree` / `json` / `argparse` / `pathlib` / `unittest`）、Gradle / JUnit XML、Markdown。

---

## Guardrails Already In Scope

实施前读：

- `docs/superpowers/specs/2026-05-19-unit-test-perf-audit-design.md`（本 plan 的 spec 源）
- `docs/dev-harness/test/rules.md`（理解"守门型"判定基线）
- `docs/dev-harness/incidents/index.md` 与各 area `incidents.md`（用于反查 `target:` 引用）
- `AGENTS.md`（commit message 与提交规范）

硬约束：

- 不修改任何 `*Test.kt` 与 `*.gradle.kts`；
- 不接入 `scripts/dev-harness/check.sh` 与 `rules.md`；
- 报告产物落 `docs/test-perf-audit/`，spec 不要再改；
- 不引入第三方 Python 依赖，脚本只用 stdlib；
- 不在本仓库 `.gitignore` 之外新增配置文件。

---

## File Structure

新增：

- `scripts/test-perf/collect-slow-cases.py` — JUnit XML 解析 CLI（单文件，stdlib only）
- `scripts/test-perf/test_collect_slow_cases.py` — 黑盒 subprocess 测试（stdlib unittest）
- `docs/test-perf-audit/REPORT.md` — 审计主报告（人读）
- `docs/test-perf-audit/slow-cases.json` — 解析产物（机器读 / 复核，提交进库）
- `docs/test-perf-audit/module-totals.json` — 解析产物（提交进库）

修改：无。

---

### Task 1: 编写 JUnit XML 解析脚本与测试

**Files:**
- Create: `scripts/test-perf/collect-slow-cases.py`
- Create: `scripts/test-perf/test_collect_slow_cases.py`

- [ ] **Step 1: 写黑盒测试**

写入 `scripts/test-perf/test_collect_slow_cases.py`：

```python
#!/usr/bin/env python3
"""Black-box test for collect-slow-cases.py via subprocess.

Run: python3 scripts/test-perf/test_collect_slow_cases.py
"""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parent / "collect-slow-cases.py"


def make_xml(path: Path, *testcases):
    """testcases: tuple of (classname, name, time_seconds, failure_message_or_None)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    body = []
    for classname, name, time_s, failure in testcases:
        if failure is None:
            body.append(
                f'  <testcase name="{name}" classname="{classname}" time="{time_s}"/>'
            )
        else:
            body.append(
                f'  <testcase name="{name}" classname="{classname}" time="{time_s}">'
                f'<failure message="{failure}" type="java.lang.AssertionError"/>'
                f'</testcase>'
            )
    xml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<testsuite>\n' + "\n".join(body) + "\n</testsuite>\n"
    )
    path.write_text(xml)


class CollectSlowCasesTest(unittest.TestCase):
    def test_parses_slow_and_failed_cases_and_aggregates(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-com.example.AppFastTest.xml",
                ("com.example.AppFastTest", "fastPasses", 0.05, None),
            )
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-com.example.AppSlowTest.xml",
                ("com.example.AppSlowTest", "isSlow", 3.21, None),
                ("com.example.AppSlowTest", "alsoFails", 0.10, "boom"),
            )
            make_xml(
                root / "feature/search/build/test-results/testDebugUnitTest/TEST-com.example.SearchTest.xml",
                ("com.example.SearchTest", "ok", 0.20, None),
            )
            # excluded: worktrees
            make_xml(
                root / ".worktrees/old/app/build/test-results/testDebugUnitTest/TEST-x.xml",
                ("x.Y", "z", 99.9, None),
            )

            out = root / "out"
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--input-root", str(root),
                    "--out-dir", str(out),
                    "--threshold", "2.0",
                ],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 2, msg=f"got: {slow}")
            self.assertEqual(slow[0]["method"], "isSlow")
            self.assertEqual(slow[0]["module"], ":app")
            self.assertEqual(slow[0]["status"], "passed")
            self.assertEqual(slow[1]["method"], "alsoFails")
            self.assertEqual(slow[1]["status"], "failed")
            self.assertEqual(slow[1]["failure_message"], "boom")

            totals = json.loads((out / "module-totals.json").read_text())
            modules = {t["module"]: t for t in totals}
            self.assertIn(":app", modules)
            self.assertIn(":feature:search", modules)
            for key in modules:
                self.assertFalse(key.startswith(".worktrees"))
            app = modules[":app"]
            self.assertEqual(app["testcase_count"], 3)
            self.assertEqual(app["slow_count"], 1)
            self.assertAlmostEqual(app["slow_total_seconds"], 3.21, places=2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
```

- [ ] **Step 2: 跑测试，确认 fail（脚本还没写）**

```bash
python3 scripts/test-perf/test_collect_slow_cases.py
```

Expected: `FileNotFoundError` 或 `subprocess` 非零退出（因为 `collect-slow-cases.py` 还不存在）。

- [ ] **Step 3: 写解析脚本**

写入 `scripts/test-perf/collect-slow-cases.py`：

```python
#!/usr/bin/env python3
"""Parse JUnit XML reports for slow JVM unit tests.

Outputs (in --out-dir):
  - slow-cases.json: testcases with time > threshold OR status != 'passed',
    sorted by time descending.
  - module-totals.json: per-module aggregate stats, sorted by
    module_total_seconds descending.

Module name is inferred from XML path: <segments>/build/test-results/...
maps to ':' + ':'.join(segments). E.g. 'feature/search/build/...' -> ':feature:search'.
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXCLUDED_TOP_SEGMENTS = {".worktrees", ".gradle"}


def _module_from_xml_path(xml_path: Path, input_root: Path) -> str:
    rel = xml_path.relative_to(input_root)
    parts = rel.parts
    if "build" not in parts:
        raise ValueError(f"unexpected path layout: {rel}")
    idx = parts.index("build")
    module_parts = parts[:idx]
    if not module_parts:
        raise ValueError(f"empty module prefix for: {rel}")
    return ":" + ":".join(module_parts)


def _testcase_status(tc):
    failure = tc.find("failure")
    if failure is not None:
        text = (failure.get("message") or failure.text or "").strip()
        first_line = text.splitlines()[0] if text else ""
        return "failed", first_line or None
    error = tc.find("error")
    if error is not None:
        text = (error.get("message") or error.text or "").strip()
        first_line = text.splitlines()[0] if text else ""
        return "error", first_line or None
    if tc.find("skipped") is not None:
        return "skipped", None
    return "passed", None


def collect(input_root: Path, threshold: float):
    pattern = "**/build/test-results/testDebugUnitTest/TEST-*.xml"
    slow_cases = []
    per_module = {}

    for xml_path in sorted(input_root.glob(pattern)):
        rel = xml_path.relative_to(input_root)
        if any(part in EXCLUDED_TOP_SEGMENTS for part in rel.parts):
            continue
        try:
            module = _module_from_xml_path(xml_path, input_root)
        except ValueError as exc:
            print(f"warn: {exc}", file=sys.stderr)
            continue
        bucket = per_module.setdefault(
            module,
            {
                "module": module,
                "testcase_count": 0,
                "module_total_seconds": 0.0,
                "slow_count": 0,
                "slow_total_seconds": 0.0,
            },
        )
        try:
            tree = ET.parse(xml_path)
        except ET.ParseError as exc:
            print(f"warn: failed to parse {xml_path}: {exc}", file=sys.stderr)
            continue
        for tc in tree.iter("testcase"):
            try:
                time_s = float(tc.get("time", "0") or "0")
            except ValueError:
                time_s = 0.0
            status, failure_msg = _testcase_status(tc)
            bucket["testcase_count"] += 1
            bucket["module_total_seconds"] += time_s
            is_slow = time_s > threshold
            if is_slow:
                bucket["slow_count"] += 1
                bucket["slow_total_seconds"] += time_s
            if is_slow or status != "passed":
                slow_cases.append(
                    {
                        "module": module,
                        "class": tc.get("classname", ""),
                        "method": tc.get("name", ""),
                        "time_seconds": round(time_s, 3),
                        "status": status,
                        "failure_message": failure_msg,
                    }
                )

    slow_cases.sort(key=lambda c: c["time_seconds"], reverse=True)
    totals = sorted(
        per_module.values(),
        key=lambda m: m["module_total_seconds"],
        reverse=True,
    )
    for m in totals:
        m["module_total_seconds"] = round(m["module_total_seconds"], 3)
        m["slow_total_seconds"] = round(m["slow_total_seconds"], 3)
    return slow_cases, totals


def main() -> int:
    parser = argparse.ArgumentParser(description="Parse JUnit XML for slow JVM unit tests.")
    parser.add_argument("--input-root", type=Path, default=Path("."))
    parser.add_argument("--out-dir", type=Path, default=Path("docs/test-perf-audit"))
    parser.add_argument("--threshold", type=float, default=2.0)
    args = parser.parse_args()

    input_root = args.input_root.resolve()
    out_dir = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    slow, totals = collect(input_root, args.threshold)
    (out_dir / "slow-cases.json").write_text(
        json.dumps(slow, indent=2, ensure_ascii=False) + "\n"
    )
    (out_dir / "module-totals.json").write_text(
        json.dumps(totals, indent=2, ensure_ascii=False) + "\n"
    )

    print(f"slow cases (time>{args.threshold}s or non-passed): {len(slow)}")
    print(f"modules covered: {len(totals)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 4: 跑测试，确认 pass**

```bash
python3 scripts/test-perf/test_collect_slow_cases.py
```

Expected: `OK` 且退出码 0。

- [ ] **Step 5: Commit**

```bash
git add scripts/test-perf/collect-slow-cases.py scripts/test-perf/test_collect_slow_cases.py
git commit -m "chore(test-perf): 添加 JVM 单测耗时解析脚本"
```

---

### Task 2: 执行全量 testDebugUnitTest 采集 XML

**Files:** 无（仅生成 `*/build/test-results/...` 临时产物，不入库）

- [ ] **Step 1: 后台跑全量单测**

由于 209 个测试文件全量跑 + `--rerun-tasks` 强制重跑，预计 5–15 分钟。**后台执行**，避免阻塞会话：

```bash
./gradlew testDebugUnitTest --rerun-tasks --continue
```

（用 Bash 工具的 `run_in_background=true`。）

- [ ] **Step 2: 等待完成**

后台命令完成后会自动通知。不要 sleep / 轮询。如果某些模块测试失败，由于 `--continue`，其他模块的 XML 仍会生成，下一步无需特殊处理。

- [ ] **Step 3: 验证 XML 已生成**

```bash
find . -path "*/build/test-results/testDebugUnitTest/TEST-*.xml" -not -path "./.worktrees/*" -not -path "./.gradle/*" | wc -l
```

Expected: 输出 > 100（覆盖大多数模块；具体数字与 testcase 数有关，不强制等值）。

如果输出 = 0，说明 gradle 跑失败到没生成任何 XML —— 此时检查 gradle 输出，修好后重跑 Step 1。

- [ ] **Step 4: 不 commit**

测试产物 (`build/`) 已被 `.gitignore` 忽略，无需 commit。

---

### Task 3: 生成 JSON 产物

**Files:**
- Create: `docs/test-perf-audit/slow-cases.json`
- Create: `docs/test-perf-audit/module-totals.json`

- [ ] **Step 1: 执行解析脚本**

```bash
python3 scripts/test-perf/collect-slow-cases.py
```

Expected: 打印 `slow cases (time>2.0s or non-passed): N` 与 `modules covered: M`，且两份 JSON 已生成。

- [ ] **Step 2: 验证 JSON 内容**

```bash
python3 -c "
import json, pathlib
slow = json.loads(pathlib.Path('docs/test-perf-audit/slow-cases.json').read_text())
totals = json.loads(pathlib.Path('docs/test-perf-audit/module-totals.json').read_text())
print(f'slow cases: {len(slow)}')
print(f'first 5 slow:')
for c in slow[:5]:
    print(f'  {c[\"time_seconds\"]:>6.2f}s  {c[\"module\"]}  {c[\"class\"].rsplit(\".\",1)[-1]}.{c[\"method\"]}')
print(f'module totals (top 5):')
for t in totals[:5]:
    print(f'  {t[\"module_total_seconds\"]:>7.2f}s  {t[\"module\"]}  cases={t[\"testcase_count\"]}  slow={t[\"slow_count\"]}')
"
```

Expected: 输出形如：
```
slow cases: 17
first 5 slow:
   12.34s  :plugin  QuickJsEngineSomeTest.fooBar
   ...
module totals (top 5):
   xx.xxs  :plugin  cases=xx  slow=x
   ...
```

确认数字合理（不是 0、不是全部慢测都来自单一文件而暴露解析 bug）。

- [ ] **Step 3: 不 commit**

JSON 留到 Task 7 与 REPORT.md 一起 commit。

---

### Task 4: 写 REPORT.md — 骨架 / 概览 / 模块表

**Files:**
- Create: `docs/test-perf-audit/REPORT.md`

- [ ] **Step 1: 起骨架**

写入 `docs/test-perf-audit/REPORT.md`（骨架，下面几个 Task 会逐节填）：

```markdown
# 单元测试耗时审计报告

> 文档状态：审计快照（一次性）
> 数据采集日期：YYYY-MM-DD
> 阈值：`testcase.time > 2.0s`
> Spec：[docs/superpowers/specs/2026-05-19-unit-test-perf-audit-design.md](../superpowers/specs/2026-05-19-unit-test-perf-audit-design.md)

## 1. 概览

（待填）

## 2. 模块耗时分布

（待填）

## 3. 慢测详表

（待填）

## 4. 快速优化清单

（待填）

## 5. 删除 / 合并候选清单

（待填）

## 6. 数据局限声明

（待填）
```

- [ ] **Step 2: 填日期与概览**

把 `YYYY-MM-DD` 替换为实际数据采集日期（用 `date +%Y-%m-%d`）。

读取 `slow-cases.json` 与 `module-totals.json`，计算并填入「1. 概览」：

```bash
python3 -c "
import json, pathlib
slow = json.loads(pathlib.Path('docs/test-perf-audit/slow-cases.json').read_text())
totals = json.loads(pathlib.Path('docs/test-perf-audit/module-totals.json').read_text())
total_cases = sum(t['testcase_count'] for t in totals)
total_seconds = sum(t['module_total_seconds'] for t in totals)
slow_count = sum(1 for c in slow if c['time_seconds'] > 2.0)
slow_seconds = sum(c['time_seconds'] for c in slow if c['time_seconds'] > 2.0)
share = (slow_seconds / total_seconds * 100) if total_seconds > 0 else 0
print(f'- JVM 单测总数：{total_cases}')
print(f'- JVM 单测总耗时：{total_seconds:.2f}s')
print(f'- > 2s 候选数：{slow_count}')
print(f'- 候选总耗时：{slow_seconds:.2f}s（占总耗时 {share:.1f}%）')
print(f'- 失败 / error 的 testcase 数（已附入慢测详表）：{sum(1 for c in slow if c[\"status\"] != \"passed\")}')
"
```

把输出的 5 行复制到「1. 概览」节，作为 markdown bullet list。

- [ ] **Step 3: 填模块耗时分布表**

读 `module-totals.json` 生成 Markdown 表：

```bash
python3 -c "
import json, pathlib
totals = json.loads(pathlib.Path('docs/test-perf-audit/module-totals.json').read_text())
print('| module | case 数 | 总耗时(s) | > 2s 数 | 模块占比 |')
print('|---|---:|---:|---:|---:|')
grand = sum(t['module_total_seconds'] for t in totals)
for t in totals:
    share = (t['module_total_seconds'] / grand * 100) if grand > 0 else 0
    m = t['module']; tc = t['testcase_count']; tot = t['module_total_seconds']; sc = t['slow_count']
    print(f'| {m} | {tc} | {tot:.2f} | {sc} | {share:.1f}% |')
"
```

把输出贴到「2. 模块耗时分布」节。之后另起一段：用一两句话点名 Top 5 模块（哪些是因为 case 多、哪些是因为单测重）。

- [ ] **Step 4: 不 commit**

整个 REPORT 完成后统一 commit。

---

### Task 5: 写 REPORT.md — 慢测详表（核心）

**Files:**
- Modify: `docs/test-perf-audit/REPORT.md`（第 3 节）

这是工作量主体。对 `slow-cases.json` 中 `time_seconds > 2.0` 的每个 case，按下述子流程处理一个。完成所有后再进入下一个 Task。

- [ ] **Step 1: 起表头**

在「3. 慢测详表」节写入：

```markdown
按 testcase 耗时降序。`必要性分类`：`守门 / 回归 / 高价重复 / 低价值`。

| # | module | class.method | time(s) | 成因初诊 | 必要性分类 | 处置建议 | evidence |
|---|---|---|---:|---|---|---|---|
```

每个慢测占一行。耗时落在 `2.0–2.5s` 的行在 `class.method` 旁加 `⚠噪声敏感` 标记。

- [ ] **Step 2: 对每个慢测，按下述 5 个子步骤处理**

对每个 case：

**子步骤 A — 读测试源码**

```bash
# 把 com.foo.SomeTest 转成文件路径并 grep 出来
grep -rn "class SomeTest" --include="*Test.kt" <module-path> | head
```

打开该测试文件，找到 `method` 对应的 `@Test fun`。

**子步骤 B — 成因初诊**

按 spec「成因初诊维度」checklist 找 1–2 个最可能成因。常见关键词：

- `Thread.sleep` / `delay\(` / `runBlocking \{` / `\.first \{` / `@RunWith\(Robolectric` / `Room\.databaseBuilder` / `PreferenceDataStoreFactory\.create` / `QuickJsEngine` / `JsBridge` / `MockWebServer` / `@RunWith\(Parameterized`

可以 grep：

```bash
grep -nE "Thread\.sleep|runBlocking|\\.first \\{|RobolectricTestRunner|MockWebServer|QuickJsEngine|JsBridge|Parameterized" <test-file-path>
```

成因栏写成 `sleep@L42` 或 `runBlocking 自旋@L18,L23` 的形式（关键词 + 行号）。

**子步骤 C — 必要性分类（按顺序逐级）**

1. 路径或 incident 反查：
   ```bash
   # 路径在 harness/contracts/ 下？
   echo <test-file-path> | grep -q "/harness/contracts/" && echo "守门型: harness/contracts"

   # 被 incidents 反查？
   grep -rn "$(basename <test-file-path>)" docs/dev-harness/
   ```
   命中即落「守门」，evidence 写 `harness/contracts/<file>` 或 `INC-YYYY-NNNN`。

2. 否则查 bugfix 痕迹：
   ```bash
   git log --oneline -- <test-file-path> | head -10
   ```
   测试名 / 注释 / commit message 命中 `fix(...)` 或 `INC-` 或 `#issue` 即落「回归」，evidence 写 commit hash 或 PR / issue 号。

3. 否则在同 class / 同模块内找等价更快测试（同断言、更低成本）：
   ```bash
   grep -nE "fun (test)?[A-Z]" <test-file-path>
   ```
   命中即落「高价重复」，evidence 写被等价覆盖的其他测试方法路径。

4. 都未命中 → 落「低价值」，evidence 简短说明：被测代码是否还存在 / 断言是否还有意义 / 删除后是否有兜底。

**子步骤 D — 处置建议**

- 守门型 / 回归型：默认「保留 + 优化耗时」，写出具体优化方向（如 `改用 runTest virtual time` / `Robolectric → 纯 JVM 拆分` / `复用 fixture`）。
- 高价重复型：默认「合并到 XYZ / 收敛参数化 / 删除其中 N 个`。
- 低价值型：默认「删除」。

**子步骤 E — 写一行 Markdown 表格**

```markdown
| 1 | :plugin | QuickJsEngineSomeTest.fooBar | 12.34 | QuickJS engine 启动@L18 | 守门 | 保留 + 引擎实例共享 | harness/contracts/QuickJsEngineContractTest.kt |
```

- [ ] **Step 3: 处理完所有慢测后，不 commit**

继续 Task 6。

---

### Task 6: 写 REPORT.md — 快速优化清单 / 删除合并清单 / 数据局限

**Files:**
- Modify: `docs/test-perf-audit/REPORT.md`（第 4–6 节）

- [ ] **Step 1: 填「4. 快速优化清单」**

从 Task 5 的详表里挑出「成因初诊为 sleep / runBlocking 自旋 / Robolectric 单测过重 / 大型 fixture」等**可机械改造**的 case，按"预计可省耗时"降序列出：

```markdown
按预计可省耗时降序。每条对应详表里的某个慢测；预计可省耗时是估算值，落地前应实测。

1. `:foo` `BarTest.baz` — 当前 12.34s，将 `Thread.sleep(5000)` 改 `advanceTimeBy(5_000)` 后预计可省 ≈ 5s。详表 #N。
2. ...
```

如果没有可机械改造的慢测，写一句 `本轮无明显机械可改造项`。

- [ ] **Step 2: 填「5. 删除 / 合并候选清单」**

从 Task 5 的详表里挑出必要性分类为「高价重复型」/「低价值型」的 case：

```markdown
低价值型（建议删除）：

1. `:foo` `BarTest.baz` — 删除理由：被测代码 `Bar.baz()` 已在 commit XXX 删除，断言失去对象。详表 #N。
2. ...

高价重复型（建议合并 / 收敛）：

1. `:foo` `BarTest.bazWithA / bazWithB` — 两个测试断言完全一致，建议合并为一个 parameterized。详表 #N。
2. ...
```

每个分类内部按耗时降序。如果某分类无候选，写一句 `本轮无该类候选`。

- [ ] **Step 3: 填「6. 数据局限声明」**

```markdown
- **单次运行噪声**：本报告基于一次 `./gradlew testDebugUnitTest --rerun-tasks --continue` 的输出。> 2s 阈值附近（`2.0–2.5s`）的 case 可能因 GC / JIT 抖动忽进忽出，详表中已用 `⚠噪声敏感` 标记，处置建议偏保守。
- **testcase time 不含 fork 启动**：Gradle worker 启动、class loading、Robolectric 首例 ResourceLoader 等成本不计入 `<testcase time="...">`。因此第 2 节模块级总耗时是补充信号，可能比详表总和大。
- **必要性判定为 AI + 启发式**：会有误判；本报告中的「删除 / 合并候选清单」**不会自动执行**，需人复核后通过后续 plan 落地。
- **本次完全不动测试代码 / build 配置**：报告落地不影响 CI / release / 已有 harness 检查。
```

- [ ] **Step 4: 不 commit**

下一个 Task 统一 commit。

---

### Task 7: 提交所有产物

**Files:** （提交以下 4 份产物）
- `docs/test-perf-audit/REPORT.md`
- `docs/test-perf-audit/slow-cases.json`
- `docs/test-perf-audit/module-totals.json`

- [ ] **Step 1: 检查 git 状态**

```bash
git status
git diff --stat docs/test-perf-audit/
```

Expected: 4 个新文件（REPORT + 两份 JSON + 目录），没有未预期的改动。

- [ ] **Step 2: Stage 并 commit**

```bash
git add docs/test-perf-audit/REPORT.md docs/test-perf-audit/slow-cases.json docs/test-perf-audit/module-totals.json
git commit -m "docs(test): JVM 单元测试耗时审计报告"
```

- [ ] **Step 3: 最终验证**

```bash
git log --oneline -3
git status
```

Expected: 最近两个 commit 是 `chore(test-perf): ...` 与 `docs(test): ...`；工作区 clean。

---

## 完成判定

全部 Task 完成后，仓库应处于以下状态：

1. `scripts/test-perf/collect-slow-cases.py` 与 `scripts/test-perf/test_collect_slow_cases.py` 已入库，测试通过。
2. `docs/test-perf-audit/{REPORT.md, slow-cases.json, module-totals.json}` 三份产物入库。
3. 没有任何 `*Test.kt` / `*.gradle.kts` / `gradle.properties` 改动。
4. 没有任何 `docs/dev-harness/` 改动。
5. `REPORT.md` 中每条慢测都有「成因初诊 + 必要性分类 + 处置建议 + evidence」。
