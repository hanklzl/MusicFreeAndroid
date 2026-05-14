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
