#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FIXTURE_DIR="$ROOT_DIR/docs/convergence/home-fidelity/fixtures/rn"
MMKV_DIR="$FIXTURE_DIR/mmkv"
SEED_DIR="$FIXTURE_DIR/seed"
PACKAGE="fun.upup.musicfree"
ACTIVITY="$PACKAGE/.MainActivity"
EXTERNAL_MMKV_DIR="/storage/emulated/0/Android/data/fun.upup.musicfree/files/mmkv"
TMP_RKSTORAGE="/data/local/tmp/home-fidelity-rkstorage"
TMP_RKSTORAGE_JOURNAL="/data/local/tmp/home-fidelity-rkstorage-journal"

usage() {
  echo "Usage: $0 <device-id>" >&2
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

if ! find "$MMKV_DIR" -mindepth 1 -print -quit | grep -q .; then
  echo "RN MMKV fixture directory is empty. Refusing to clear device state without a restorable MMKV fixture." >&2
  exit 1
fi

adb -s "$DEVICE" reverse tcp:8081 tcp:8081 >/dev/null || true
adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell rm -rf "$EXTERNAL_MMKV_DIR"
adb -s "$DEVICE" shell mkdir -p "$EXTERNAL_MMKV_DIR"
adb -s "$DEVICE" push "$MMKV_DIR"/. "$EXTERNAL_MMKV_DIR"/ >/dev/null

if [[ -f "$SEED_DIR/RKStorage" ]]; then
  adb -s "$DEVICE" push "$SEED_DIR/RKStorage" "$TMP_RKSTORAGE" >/dev/null
  adb -s "$DEVICE" shell run-as "$PACKAGE" cp "$TMP_RKSTORAGE" databases/RKStorage
fi
if [[ -f "$SEED_DIR/RKStorage-journal" ]]; then
  adb -s "$DEVICE" push "$SEED_DIR/RKStorage-journal" "$TMP_RKSTORAGE_JOURNAL" >/dev/null
  adb -s "$DEVICE" shell run-as "$PACKAGE" cp "$TMP_RKSTORAGE_JOURNAL" databases/RKStorage-journal
fi

adb -s "$DEVICE" shell am start -S -n "$ACTIVITY" >/dev/null
