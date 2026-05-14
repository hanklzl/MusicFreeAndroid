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
