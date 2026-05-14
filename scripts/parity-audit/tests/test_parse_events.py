import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIXTURES = pathlib.Path(__file__).resolve().parent / "fixtures"
sys.path.insert(0, str(ROOT))

import parse_events


def test_parse_android_native_parity_evt():
    events = parse_events.parse_logcat(
        FIXTURES / "sample_logcat_android.txt",
        side="android",
        rn_parser_config=None,
    )
    assert len(events) == 2
    assert events[0]["kind"] == "plugin.method_called"
    assert events[0]["waypoint"] == "01_after_cold_start"
    assert events[0]["side"] == "android"
    assert events[1]["kind"] == "play.state_changed"
    assert events[1]["from"] == "IDLE" and events[1]["to"] == "LOADING"


def test_parse_rn_with_regex_config(tmp_path):
    cfg = ROOT / "../../.agents/skills/parity-audit-skill/references/rn-logcat-parser.json"
    cfg_path = pathlib.Path(cfg).resolve()
    events = parse_events.parse_logcat(
        FIXTURES / "sample_logcat_rn.txt",
        side="rn",
        rn_parser_config=cfg_path,
    )
    # 期望抓到 1 plugin.method_called + 1 play.state_changed + 1 nav.enter
    kinds = sorted(e["kind"] for e in events)
    assert kinds == ["nav.enter", "play.state_changed", "plugin.method_called"]
    # 跨 waypoint 切片
    by_wp = {e["waypoint"]: e["kind"] for e in events}
    assert "01_after_cold_start" in by_wp
    assert "02_after_recommend_loaded" in by_wp
    # args_hash 必须是 8 字符 hex
    plugin_event = next(e for e in events if e["kind"] == "plugin.method_called")
    assert len(plugin_event["args_hash"]) == 8


def test_lines_outside_waypoint_window_are_dropped():
    events = parse_events.parse_logcat(
        FIXTURES / "sample_logcat_android.txt",
        side="android",
        rn_parser_config=None,
    )
    for e in events:
        assert e["waypoint"] is not None
