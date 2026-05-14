#!/usr/bin/env bash
# scripts/parity-audit/build-android.sh

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
./gradlew :app:assembleDebug >&2

APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERROR: built apk not found at $APK" >&2; exit 4; }
echo "$APK"
