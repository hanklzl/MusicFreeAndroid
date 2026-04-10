#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:?device required}"
STATE="${2:?state required}"
FRAGMENT="${3:?fragment required}"
OUT_DIR="docs/home-fidelity/homepage/android"

mkdir -p "$OUT_DIR"
adb -s "$DEVICE" exec-out screencap -p > "$OUT_DIR/${STATE}-${FRAGMENT}.png"
adb -s "$DEVICE" shell uiautomator dump "/sdcard/${STATE}-${FRAGMENT}.xml" >/dev/null
adb -s "$DEVICE" pull "/sdcard/${STATE}-${FRAGMENT}.xml" "$OUT_DIR/${STATE}-${FRAGMENT}.xml" >/dev/null
adb -s "$DEVICE" shell rm "/sdcard/${STATE}-${FRAGMENT}.xml"
