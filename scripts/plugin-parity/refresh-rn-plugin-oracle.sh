#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"

if [[ -n "${RN_MUSICFREE_ROOT:-}" ]]; then
  RN_ROOT="$RN_MUSICFREE_ROOT"
elif [[ -f "$ROOT/../MusicFree/scripts/plugin-oracle/generate-plugin-oracle.js" ]]; then
  RN_ROOT="$ROOT/../MusicFree"
elif [[ -f "$ROOT/../../../MusicFree/scripts/plugin-oracle/generate-plugin-oracle.js" ]]; then
  RN_ROOT="$ROOT/../../../MusicFree"
elif [[ -f "$ROOT/../.worktrees/MusicFree-plugin-parity-oracle/scripts/plugin-oracle/generate-plugin-oracle.js" ]]; then
  RN_ROOT="$ROOT/../.worktrees/MusicFree-plugin-parity-oracle"
elif [[ -f "$ROOT/../../../.worktrees/MusicFree-plugin-parity-oracle/scripts/plugin-oracle/generate-plugin-oracle.js" ]]; then
  RN_ROOT="$ROOT/../../../.worktrees/MusicFree-plugin-parity-oracle"
else
  echo "Cannot find RN plugin oracle generator. Set RN_MUSICFREE_ROOT to the MusicFree checkout." >&2
  exit 1
fi

node "$RN_ROOT/scripts/plugin-oracle/generate-plugin-oracle.js" \
  --out "$ROOT/plugin/src/test/resources/rn-plugin-oracle.json" \
  --check
