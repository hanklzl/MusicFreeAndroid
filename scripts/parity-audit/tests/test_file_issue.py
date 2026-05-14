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
