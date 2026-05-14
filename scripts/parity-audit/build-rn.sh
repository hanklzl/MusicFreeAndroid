#!/usr/bin/env bash
# scripts/parity-audit/build-rn.sh
# 构建 RN MusicFree debug APK，输出路径打到 stdout。

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
RN_ROOT="$REPO_ROOT/../MusicFree"

[ -d "$RN_ROOT" ] || { echo "ERROR: $RN_ROOT not found" >&2; exit 3; }

cd "$RN_ROOT"
if [ ! -d node_modules ]; then
  yarn install --frozen-lockfile >&2
fi
cd android
./gradlew assembleDebug >&2

APK="$RN_ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERROR: built apk not found at $APK" >&2; exit 3; }
echo "$APK"
