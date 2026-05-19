#!/usr/bin/env python3
"""Black-box test for collect-slow-cases.py via subprocess.

Run: python3 scripts/test-perf/test_collect_slow_cases.py
"""

import json
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT = Path(__file__).parent / "collect-slow-cases.py"


def make_xml(path: Path, *testcases):
    """Build a JUnit XML file from testcase dicts.

    Each dict has:
      'classname': str
      'name': str
      'time': float (seconds)
      'kind': 'pass' | 'fail' | 'error' | 'skipped' | 'missing-time' | 'bad-time'
      'message': str | None (for fail/error; ignored for pass/skip)
      'message_via_text': bool = False (if True, put message in element text instead of @message)
    """
    path.parent.mkdir(parents=True, exist_ok=True)

    root = ET.Element("testsuite")

    for tc_dict in testcases:
        classname = tc_dict["classname"]
        name = tc_dict["name"]
        kind = tc_dict["kind"]
        message = tc_dict.get("message")
        message_via_text = tc_dict.get("message_via_text", False)

        tc_attrs = {"classname": classname, "name": name}

        # Handle time attribute
        if kind == "missing-time":
            # Omit time attribute entirely
            pass
        elif kind == "bad-time":
            tc_attrs["time"] = "N/A"
        else:
            tc_attrs["time"] = str(tc_dict["time"])

        tc = ET.SubElement(root, "testcase", tc_attrs)

        # Add child elements based on kind
        if kind == "fail":
            child_attrs = {"type": "java.lang.AssertionError"}
            if not message_via_text and message is not None:
                child_attrs["message"] = message
            fail = ET.SubElement(tc, "failure", child_attrs)
            if message_via_text and message is not None:
                fail.text = message
        elif kind == "error":
            child_attrs = {"type": "java.lang.Exception"}
            if not message_via_text and message is not None:
                child_attrs["message"] = message
            err = ET.SubElement(tc, "error", child_attrs)
            if message_via_text and message is not None:
                err.text = message
        elif kind == "skipped":
            ET.SubElement(tc, "skipped")

    tree = ET.ElementTree(root)
    tree.write(path, encoding="utf-8", xml_declaration=True)


class CollectSlowCasesTest(unittest.TestCase):
    def test_parses_slow_and_failed_cases_and_aggregates(self):
        """Happy path: passed, failed, and slow testcases are parsed and aggregated."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-com.example.AppFastTest.xml",
                {"classname": "com.example.AppFastTest", "name": "fastPasses", "time": 0.05, "kind": "pass"},
            )
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-com.example.AppSlowTest.xml",
                {"classname": "com.example.AppSlowTest", "name": "isSlow", "time": 3.21, "kind": "pass"},
                {"classname": "com.example.AppSlowTest", "name": "alsoFails", "time": 0.10, "kind": "fail", "message": "boom"},
            )
            make_xml(
                root / "feature/search/build/test-results/testDebugUnitTest/TEST-com.example.SearchTest.xml",
                {"classname": "com.example.SearchTest", "name": "ok", "time": 0.20, "kind": "pass"},
            )
            # excluded: worktrees
            make_xml(
                root / ".worktrees/old/app/build/test-results/testDebugUnitTest/TEST-x.xml",
                {"classname": "x.Y", "name": "z", "time": 99.9, "kind": "pass"},
            )

            out = root / "out"
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--input-root", str(root),
                    "--out-dir", str(out),
                    "--threshold", "2.0",
                ],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 2, msg=f"got: {slow}")
            self.assertEqual(slow[0]["method"], "isSlow")
            self.assertEqual(slow[0]["module"], ":app")
            self.assertEqual(slow[0]["status"], "passed")
            self.assertEqual(slow[1]["method"], "alsoFails")
            self.assertEqual(slow[1]["status"], "failed")
            self.assertEqual(slow[1]["failure_message"], "boom")

            totals = json.loads((out / "module-totals.json").read_text())
            modules = {t["module"]: t for t in totals}
            self.assertIn(":app", modules)
            self.assertIn(":feature:search", modules)
            for key in modules:
                self.assertFalse(key.startswith(".worktrees"))
            app = modules[":app"]
            self.assertEqual(app["testcase_count"], 3)
            self.assertEqual(app["slow_count"], 1)
            self.assertAlmostEqual(app["slow_total_seconds"], 3.21, places=2)

    def test_error_element(self):
        """<error> element is captured as status='error' with failure_message."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-ErrorTest.xml",
                {"classname": "com.example.ErrorTest", "name": "throwsException", "time": 0.5, "kind": "error", "message": "java.io.IOException: disk full"},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "0.1"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 1)
            self.assertEqual(slow[0]["status"], "error")
            self.assertEqual(slow[0]["failure_message"], "java.io.IOException: disk full")

    def test_skipped_element_slow(self):
        """<skipped> element with time > threshold is in slow-cases.json."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-SkipTest.xml",
                {"classname": "com.example.SkipTest", "name": "skippedSlow", "time": 3.5, "kind": "skipped"},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "2.0"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 1)
            self.assertEqual(slow[0]["status"], "skipped")
            self.assertIsNone(slow[0]["failure_message"])

            # Also check that module totals incremented slow_count
            totals = json.loads((out / "module-totals.json").read_text())
            app = totals[0]
            self.assertEqual(app["module"], ":app")
            self.assertEqual(app["slow_count"], 1)

    def test_failure_message_via_text(self):
        """Failure message from element text (no @message attribute)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-TextMsgTest.xml",
                {"classname": "com.example.TextMsgTest", "name": "failViaText", "time": 0.1, "kind": "fail", "message": "Expected: 42, got: 0", "message_via_text": True},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "2.0"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 1)
            self.assertEqual(slow[0]["status"], "failed")
            self.assertEqual(slow[0]["failure_message"], "Expected: 42, got: 0")

    def test_multiline_failure_text_truncated(self):
        """Multi-line failure text is truncated to first line."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-MultilineTest.xml",
                {"classname": "com.example.MultilineTest", "name": "multilineFail", "time": 0.1, "kind": "fail", "message": "Line 1: error\nLine 2: details\nLine 3: more", "message_via_text": True},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "2.0"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 1)
            self.assertEqual(slow[0]["failure_message"], "Line 1: error")

    def test_missing_time_attribute(self):
        """Missing time attribute defaults to 0.0, not slow, not in slow-cases."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-NoTimeTest.xml",
                {"classname": "com.example.NoTimeTest", "name": "noTime", "time": 0.0, "kind": "missing-time"},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "2.0"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 0)

            totals = json.loads((out / "module-totals.json").read_text())
            self.assertEqual(len(totals), 1)
            app = totals[0]
            self.assertEqual(app["testcase_count"], 1)
            self.assertEqual(app["slow_count"], 0)

    def test_malformed_time_attribute(self):
        """Malformed time attribute (e.g., 'N/A') defaults to 0.0."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-BadTimeTest.xml",
                {"classname": "com.example.BadTimeTest", "name": "badTime", "time": 0.0, "kind": "bad-time"},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "2.0"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 0)

            totals = json.loads((out / "module-totals.json").read_text())
            self.assertEqual(len(totals), 1)
            app = totals[0]
            self.assertEqual(app["testcase_count"], 1)
            self.assertEqual(app["module_total_seconds"], 0.0)

    def test_gradle_exclusion(self):
        """Files under .gradle/ are excluded entirely."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            # Included file
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-IncludedTest.xml",
                {"classname": "com.example.IncludedTest", "name": "included", "time": 0.1, "kind": "pass"},
            )
            # Excluded file under .gradle
            make_xml(
                root / ".gradle/subdir/app/build/test-results/testDebugUnitTest/TEST-ExcludedTest.xml",
                {"classname": "com.example.ExcludedTest", "name": "excluded", "time": 99.9, "kind": "pass"},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "2.0"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 0)

            totals = json.loads((out / "module-totals.json").read_text())
            self.assertEqual(len(totals), 1)
            app = totals[0]
            self.assertEqual(app["module"], ":app")
            self.assertEqual(app["testcase_count"], 1)

    def test_special_characters_in_message(self):
        """Special characters (quotes, ampersand, angle brackets, Unicode) are handled correctly."""
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            msg = 'Expected "foo" & <bar> with 中文'
            make_xml(
                root / "app/build/test-results/testDebugUnitTest/TEST-SpecialCharsTest.xml",
                {"classname": "com.example.SpecialCharsTest", "name": "specialChars", "time": 0.1, "kind": "fail", "message": msg},
            )

            out = root / "out"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input-root", str(root), "--out-dir", str(out), "--threshold", "2.0"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            slow = json.loads((out / "slow-cases.json").read_text())
            self.assertEqual(len(slow), 1)
            self.assertEqual(slow[0]["failure_message"], msg)


if __name__ == "__main__":
    unittest.main(verbosity=2)
