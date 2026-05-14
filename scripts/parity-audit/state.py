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
