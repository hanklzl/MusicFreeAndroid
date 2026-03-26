#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PLATFORM_ROOT="$ROOT_DIR/docs/convergence/home-fidelity/rn"
HELPER="$ROOT_DIR/scripts/convergence/home-fidelity/assert_capture_state.py"
RESTORE_SCRIPT="$ROOT_DIR/scripts/convergence/home-fidelity/restore-rn-home-state.sh"
PACKAGE="fun.upup.musicfree"

usage() {
  echo "Usage: $0 <device-id> <state> <fragment>" >&2
}

if [[ $# -ne 3 ]]; then
  usage
  exit 1
fi

DEVICE="$1"
STATE="$2"
FRAGMENT="$3"
RAW_PNG="$PLATFORM_ROOT/raw/${STATE}-${FRAGMENT}.png"
CROPPED_PNG="$PLATFORM_ROOT/cropped/${STATE}-${FRAGMENT}.png"
DUMP_XML="$PLATFORM_ROOT/dumps/${STATE}-${FRAGMENT}.xml"
TMP_DIR="$(mktemp -d)"
TMP_XML="$TMP_DIR/current.xml"
REMOTE_XML="/sdcard/home-fidelity-rn-${STATE}-${FRAGMENT}.xml"
REMOTE_PNG="/sdcard/home-fidelity-rn-${STATE}-${FRAGMENT}.png"
LEFT_PX=0
TOP_PX=63
RIGHT_PX=1080
BOTTOM_PX=2337

cleanup() {
  rm -rf "$TMP_DIR"
  adb -s "$DEVICE" shell rm -f "$REMOTE_XML" "$REMOTE_PNG" >/dev/null 2>&1 || true
}
trap cleanup EXIT

dump_xml() {
  adb -s "$DEVICE" shell uiautomator dump "$REMOTE_XML" >/dev/null
  adb -s "$DEVICE" pull "$REMOTE_XML" "$TMP_XML" >/dev/null
}

wait_for_state() {
  local requested_state="${3:-$STATE}"
  local max_attempts="$1"
  local delay_seconds="$2"
  local attempt

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    dump_xml
    if python3 "$HELPER" --xml "$TMP_XML" --state "$requested_state" --fragment "$FRAGMENT"; then
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "Timed out waiting for RN state '$requested_state' for fragment '$FRAGMENT'." >&2
  return 1
}

tap_anchor_center() {
  local anchor="$1"
  local center

  center="$(python3 - "$TMP_XML" "$anchor" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, anchor = sys.argv[1:]
root = ET.parse(xml_path).getroot()
for node in root.iter():
    if node.attrib.get("resource-id") != anchor:
        continue
    bounds = node.attrib.get("bounds", "")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        break
    left, top, right, bottom = map(int, match.groups())
    print(f"{(left + right) // 2} {(top + bottom) // 2}")
    sys.exit(0)

print(f"Could not resolve bounds for anchor: {anchor}", file=sys.stderr)
sys.exit(1)
PY
)"
  adb -s "$DEVICE" shell input tap ${center}
}

perform_state_transition() {
  "$RESTORE_SCRIPT" "$DEVICE"
  adb -s "$DEVICE" reverse tcp:8081 tcp:8081 >/dev/null || true

  case "$STATE" in
    home-top)
      wait_for_state 20 1
      ;;
    home-sheets|home-scroll)
      wait_for_state 20 1 || true
      local swipe_count
      for ((swipe_count = 1; swipe_count <= 8; swipe_count++)); do
        if python3 "$HELPER" --xml "$TMP_XML" --state "$STATE" --fragment "$FRAGMENT"; then
          return 0
        fi
        adb -s "$DEVICE" shell input swipe 540 1900 540 700 300
        sleep 1
        dump_xml
      done
      python3 "$HELPER" --xml "$TMP_XML" --state "$STATE" --fragment "$FRAGMENT"
      ;;
    drawer-open)
      wait_for_state 20 1 home-top
      tap_anchor_center "home.navBar.menu"
      wait_for_state 10 1
      ;;
    *)
      echo "Unsupported RN capture state: $STATE" >&2
      return 1
      ;;
  esac
}

capture_artifacts() {
  adb -s "$DEVICE" shell screencap -p "$REMOTE_PNG" >/dev/null
  adb -s "$DEVICE" pull "$REMOTE_PNG" "$RAW_PNG" >/dev/null
  dump_xml
  python3 "$HELPER" --xml "$TMP_XML" --state "$STATE" --fragment "$FRAGMENT"
  cp "$TMP_XML" "$DUMP_XML"

  local width=$((RIGHT_PX - LEFT_PX))
  local height=$((BOTTOM_PX - TOP_PX))
  sips --cropOffset "$TOP_PX" "$LEFT_PX" -c "$height" "$width" "$RAW_PNG" --out "$CROPPED_PNG" >/dev/null
}

perform_state_transition
capture_artifacts
echo "Captured RN artifacts: $RAW_PNG, $CROPPED_PNG, $DUMP_XML"
