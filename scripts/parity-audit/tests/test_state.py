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
