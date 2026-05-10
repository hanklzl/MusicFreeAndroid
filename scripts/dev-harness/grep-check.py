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
