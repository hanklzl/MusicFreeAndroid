#!/usr/bin/env bash
# scripts/parity-audit/install-both.sh
# usage: install-both.sh <rn_apk> <android_apk> [device_serial]

set -euo pipefail

RN_APK="$1"
ANDROID_APK="$2"
DEVICE="${3:-}"

ADB="adb"
[ -n "$DEVICE" ] && ADB="adb -s $DEVICE"

$ADB install -r -t "$RN_APK" >&2
$ADB install -r -t "$ANDROID_APK" >&2

echo "installed:"
echo "  rn=fun.upup.musicfree"
echo "  android=com.hank.musicfree.debug"
