#!/usr/bin/env bash

set -euo pipefail

ROOT_ANDROID="/Users/zili/code/android/MusicFreeAndroid"
ROOT_RN="/Users/zili/code/android/MusicFree"

COMPOSE_PKG="com.zili.android.musicfreeandroid/.MainActivity"
RN_PKG="fun.upup.musicfree/.MainActivity"

SUBSCRIPTION_URL="https://13413.kstore.vip/yuanli/yuanli.json"

usage() {
  cat <<EOF
Usage: $0 [compose|rn|both]

compose : build/install/launch Compose app
rn      : launch Original RN app (assumes metro is running)
both    : execute compose then rn

Notes:
- For RN first-time setup, run in another terminal:
    cd $ROOT_RN && npm install && npm start
- Subscription baseline used in parity tests:
    $SUBSCRIPTION_URL
EOF
}

run_compose() {
  echo "[compose] building and installing..."
  (cd "$ROOT_ANDROID" && ./gradlew installDebug)
  echo "[compose] launching..."
  adb shell am start -S -n "$COMPOSE_PKG" >/dev/null
  echo "[compose] done"
}

run_rn() {
  echo "[rn] building and installing..."
  (cd "$ROOT_RN" && npm run android)
  echo "[rn] launching..."
  adb shell am start -S -n "$RN_PKG" >/dev/null
  echo "[rn] done"
}

MODE="${1:-both}"

case "$MODE" in
  compose)
    run_compose
    ;;
  rn)
    run_rn
    ;;
  both)
    run_compose
    run_rn
    ;;
  *)
    usage
    exit 1
    ;;
esac
