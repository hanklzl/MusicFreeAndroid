#!/usr/bin/env python3
"""Parse JUnit XML reports for slow JVM unit tests.

Outputs (in --out-dir):
  - slow-cases.json: testcases with time > threshold OR status != 'passed',
    sorted by time descending.
  - module-totals.json: per-module aggregate stats, sorted by
    module_total_seconds descending.

Module name is inferred from XML path: <segments>/build/test-results/...
maps to ':' + ':'.join(segments). E.g. 'feature/search/build/...' -> ':feature:search'.

Only the first <failure> or <error> per testcase is captured; Gradle's standard
output emits at most one. Assumes a single <testsuite> per file (Gradle's TEST-*.xml
convention).
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
