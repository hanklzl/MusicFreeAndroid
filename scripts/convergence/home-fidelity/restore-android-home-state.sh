#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FIXTURE_DIR="$ROOT_DIR/docs/convergence/home-fidelity/fixtures/android"
PACKAGE="com.zili.android.musicfreeandroid"
ACTIVITY="$PACKAGE/.MainActivity"
TMP_DB="/data/local/tmp/home-fidelity-musicfree.db"
TMP_WAL="/data/local/tmp/home-fidelity-musicfree.db-wal"
TMP_SHM="/data/local/tmp/home-fidelity-musicfree.db-shm"

usage() {
  echo "Usage: $0 <device-id>" >&2
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

DEVICE="$1"
DB_FILE="$FIXTURE_DIR/musicfree.db"
WAL_FILE="$FIXTURE_DIR/musicfree.db-wal"
SHM_FILE="$FIXTURE_DIR/musicfree.db-shm"

if [[ ! -f "$DB_FILE" ]]; then
  echo "Missing Android fixture DB: $DB_FILE" >&2
  exit 1
fi

adb -s "$DEVICE" push "$DB_FILE" "$TMP_DB" >/dev/null
if [[ -f "$WAL_FILE" ]]; then
  adb -s "$DEVICE" push "$WAL_FILE" "$TMP_WAL" >/dev/null
fi
if [[ -f "$SHM_FILE" ]]; then
  adb -s "$DEVICE" push "$SHM_FILE" "$TMP_SHM" >/dev/null
fi

adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell run-as "$PACKAGE" mkdir databases >/dev/null 2>&1 || true
adb -s "$DEVICE" shell run-as "$PACKAGE" rm -f \
  databases/musicfree.db \
  databases/musicfree.db-wal \
  databases/musicfree.db-shm >/dev/null 2>&1 || true
adb -s "$DEVICE" shell run-as "$PACKAGE" cp "$TMP_DB" databases/musicfree.db
if [[ -f "$WAL_FILE" ]]; then
  adb -s "$DEVICE" shell run-as "$PACKAGE" cp "$TMP_WAL" databases/musicfree.db-wal
fi
if [[ -f "$SHM_FILE" ]]; then
  adb -s "$DEVICE" shell run-as "$PACKAGE" cp "$TMP_SHM" databases/musicfree.db-shm
fi

adb -s "$DEVICE" shell am start -S -n "$ACTIVITY" >/dev/null
