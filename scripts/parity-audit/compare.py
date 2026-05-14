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
