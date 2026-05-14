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
