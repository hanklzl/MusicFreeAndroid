#!/usr/bin/env bash

set -euo pipefail

MATRIX_FILE="${1:-/Users/zili/code/android/MusicFreeAndroid/specs/001-plugin-parity-search/parity-matrix.md}"

if [[ ! -f "$MATRIX_FILE" ]]; then
  echo "ERROR: matrix file not found: $MATRIX_FILE" >&2
  exit 1
fi

row_errors=0
p1_errors=0

while IFS='|' read -r _ capability_id stage capability original compose gap target priority evidence _; do
  capability_id="$(echo "$capability_id" | xargs)"
  stage="$(echo "$stage" | xargs)"
  capability="$(echo "$capability" | xargs)"
  gap="$(echo "$gap" | xargs)"
  priority="$(echo "$priority" | xargs)"
  evidence="$(echo "$evidence" | xargs)"

  [[ -z "$capability_id" ]] && continue
  [[ "$capability_id" == "Capability ID" ]] && continue
  [[ "$capability_id" == "---" ]] && continue

  if [[ -z "$stage" || -z "$capability" || -z "$gap" || -z "$priority" ]]; then
    echo "ERROR: incomplete row for capability '$capability_id'" >&2
    row_errors=$((row_errors + 1))
  fi

  if [[ "$priority" == "P1" && ( -z "$evidence" || "$evidence" == "TBD" ) ]]; then
    echo "ERROR: P1 capability '$capability_id' missing evidence refs" >&2
    p1_errors=$((p1_errors + 1))
  fi
done < <(grep '^|' "$MATRIX_FILE")

if [[ $row_errors -gt 0 || $p1_errors -gt 0 ]]; then
  echo "Matrix validation failed: row_errors=$row_errors, p1_errors=$p1_errors" >&2
  exit 1
fi

echo "Matrix validation passed: $MATRIX_FILE"
