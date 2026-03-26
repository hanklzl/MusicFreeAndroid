#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FIXTURE_DIR="$ROOT_DIR/docs/convergence/home-fidelity/fixtures/rn"
MMKV_DIR="$FIXTURE_DIR/mmkv"
SEED_DIR="$FIXTURE_DIR/seed"
SEED_JSON="$FIXTURE_DIR/home-fidelity-seed.json"
PACKAGE="fun.upup.musicfree"
ACTIVITY="$PACKAGE/.MainActivity"
EXTERNAL_MMKV_DIR="/storage/emulated/0/Android/data/fun.upup.musicfree/files/mmkv"
SEED_TARGET="/storage/emulated/0/Android/data/fun.upup.musicfree/files/data/home-fidelity-seed.json"
TMP_RKSTORAGE="/data/local/tmp/home-fidelity-rkstorage"
TMP_RKSTORAGE_JOURNAL="/data/local/tmp/home-fidelity-rkstorage-journal"

usage() {
  echo "Usage: $0 <device-id>" >&2
}

has_real_mmkv_fixture() {
  find "$MMKV_DIR" -mindepth 1 -type f ! -name 'README.md' -print -quit | grep -q .
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

DEVICE="$1"

if [[ ! -d "$MMKV_DIR" ]]; then
  echo "Missing RN fixture directory: $MMKV_DIR" >&2
  exit 1
fi

if [[ ! -f "$SEED_JSON" ]] && ! has_real_mmkv_fixture; then
  echo "RN fixtures do not contain a real MMKV payload or a home-fidelity seed JSON." >&2
  exit 1
fi

adb -s "$DEVICE" reverse tcp:8081 tcp:8081 >/dev/null || true
adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell rm -rf "$EXTERNAL_MMKV_DIR"
adb -s "$DEVICE" shell mkdir -p "$EXTERNAL_MMKV_DIR"
adb -s "$DEVICE" shell mkdir -p "/storage/emulated/0/Android/data/fun.upup.musicfree/files/data"
adb -s "$DEVICE" shell rm -f "$SEED_TARGET"

if has_real_mmkv_fixture; then
  adb -s "$DEVICE" push "$MMKV_DIR"/. "$EXTERNAL_MMKV_DIR"/ >/dev/null
fi

if [[ -f "$SEED_JSON" ]]; then
  adb -s "$DEVICE" push "$SEED_JSON" "$SEED_TARGET" >/dev/null
fi

if [[ -f "$SEED_DIR/RKStorage" ]]; then
  adb -s "$DEVICE" push "$SEED_DIR/RKStorage" "$TMP_RKSTORAGE" >/dev/null
  adb -s "$DEVICE" shell run-as "$PACKAGE" cp "$TMP_RKSTORAGE" databases/RKStorage
fi
if [[ -f "$SEED_DIR/RKStorage-journal" ]]; then
  adb -s "$DEVICE" push "$SEED_DIR/RKStorage-journal" "$TMP_RKSTORAGE_JOURNAL" >/dev/null
  adb -s "$DEVICE" shell run-as "$PACKAGE" cp "$TMP_RKSTORAGE_JOURNAL" databases/RKStorage-journal
fi

adb -s "$DEVICE" shell am start -S -n "$ACTIVITY" >/dev/null
sleep 8
