#!/usr/bin/env bash

set -euo pipefail

DEFAULT_MATRIX="/Users/zili/code/android/MusicFreeAndroid/specs/001-plugin-parity-search/parity-matrix.md"
DEFAULT_EVIDENCE="/Users/zili/code/android/MusicFreeAndroid/specs/001-plugin-parity-search/evidence-log.md"

self_test() {
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  local matrix="$tmp_dir/matrix.md"
  local evidence="$tmp_dir/evidence.md"

  cat >"$matrix" <<'EOF'
| Capability ID | Stage | Capability | Original RN | Compose Current | Gap Status | Target Definition | Priority | Evidence Refs |
|---|---|---|---|---|---|---|---|---|
| plugin.add.url | add | Add plugin from URL | Supported | Supported | Aligned | x | P1 | E-001 |
| plugin.update.single | update | Update single plugin | Supported | Supported | Aligned | x | P1 | E-002 |
EOF

  cat >"$evidence" <<'EOF'
| Evidence ID | Capability ID | Type | Compose Steps | Original RN Steps | Result | Artifact Path | Notes |
|---|---|---|---|---|---|---|---|
| E-001 | plugin.add.url | manual | a | b | aligned | docs/a.md | ok |
| E-002 | plugin.update.single | automated | a | b | aligned | docs/b.md | ok |
EOF

  "$0" "$matrix" "$evidence"
  rm -rf "$tmp_dir"
}

if [[ "${1:-}" == "--self-test" ]]; then
  self_test
  echo "Self-test passed"
  exit 0
fi

MATRIX_FILE="${1:-$DEFAULT_MATRIX}"
EVIDENCE_FILE="${2:-$DEFAULT_EVIDENCE}"

if [[ ! -f "$MATRIX_FILE" ]]; then
  echo "ERROR: matrix file not found: $MATRIX_FILE" >&2
  exit 1
fi

if [[ ! -f "$EVIDENCE_FILE" ]]; then
  echo "ERROR: evidence file not found: $EVIDENCE_FILE" >&2
  exit 1
fi

p1_missing=0
p1_not_aligned=0
p1_missing_evidence=0

while IFS='|' read -r _ capability_id stage capability original compose gap target priority evidence _; do
  capability_id="$(echo "$capability_id" | xargs)"
  gap="$(echo "$gap" | xargs)"
  priority="$(echo "$priority" | xargs)"
  evidence="$(echo "$evidence" | xargs)"

  [[ -z "$capability_id" ]] && continue
  [[ "$capability_id" == "Capability ID" ]] && continue
  [[ "$capability_id" == "---" ]] && continue
  [[ "$priority" != "P1" ]] && continue

  if [[ "$gap" != "Aligned" ]]; then
    echo "ERROR: P1 capability not aligned: $capability_id ($gap)" >&2
    p1_not_aligned=$((p1_not_aligned + 1))
  fi

  if [[ -z "$evidence" || "$evidence" == "TBD" ]]; then
    echo "ERROR: P1 capability missing evidence refs: $capability_id" >&2
    p1_missing_evidence=$((p1_missing_evidence + 1))
    continue
  fi

  if ! grep -q "| ${capability_id} |" "$EVIDENCE_FILE"; then
    echo "ERROR: P1 capability missing evidence entry: $capability_id" >&2
    p1_missing=$((p1_missing + 1))
  fi
done < <(grep '^|' "$MATRIX_FILE")

if [[ $p1_missing -gt 0 || $p1_not_aligned -gt 0 || $p1_missing_evidence -gt 0 ]]; then
  echo "Release gate failed: missing=$p1_missing not_aligned=$p1_not_aligned missing_evidence=$p1_missing_evidence" >&2
  exit 1
fi

echo "Release gate passed (P1 capabilities aligned with evidence)"
