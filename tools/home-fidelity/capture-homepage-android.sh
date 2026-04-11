#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:?device required}"
STATE="${2:?state required}"
FRAGMENT="${3:?fragment required}"
OUT_DIR="docs/home-fidelity/homepage/android"
EXPECTED_PACKAGE="${CAPTURE_EXPECTED_PACKAGE:-com.zili.android.musicfreeandroid}"
EXPECTED_ANCHOR="${4:-}"

if [[ -z "${EXPECTED_ANCHOR}" ]]; then
  case "${STATE}" in
    home-top)
      EXPECTED_ANCHOR="screen.home.root"
      ;;
    home-sheets)
      EXPECTED_ANCHOR="home.sheets.root"
      ;;
    drawer-open)
      EXPECTED_ANCHOR="home.drawer.root"
      ;;
  esac
fi

mkdir -p "$OUT_DIR"

CURRENT_FOCUS="$(adb -s "$DEVICE" shell dumpsys window | grep -m1 'mCurrentFocus' || true)"
if [[ "$CURRENT_FOCUS" != *"$EXPECTED_PACKAGE"* ]]; then
  echo "front app mismatch: expected $EXPECTED_PACKAGE, got: $CURRENT_FOCUS" >&2
  exit 1
fi

PNG_OUT="$OUT_DIR/${STATE}-${FRAGMENT}.png"
XML_OUT="$OUT_DIR/${STATE}-${FRAGMENT}.xml"
REMOTE_XML="/storage/emulated/0/${STATE}-${FRAGMENT}.xml"

adb -s "$DEVICE" exec-out screencap -p > "$PNG_OUT"
adb -s "$DEVICE" shell uiautomator dump "$REMOTE_XML" >/dev/null
adb -s "$DEVICE" pull "$REMOTE_XML" "$XML_OUT" >/dev/null
adb -s "$DEVICE" shell rm "$REMOTE_XML"

if [[ -n "$EXPECTED_ANCHOR" ]] && ! rg -Fq "$EXPECTED_ANCHOR" "$XML_OUT"; then
  echo "anchor missing: $EXPECTED_ANCHOR in $XML_OUT" >&2
  exit 1
fi
