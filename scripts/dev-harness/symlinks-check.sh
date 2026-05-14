#!/usr/bin/env bash
# Validate dev-harness skill symlinks.
#
# Verifies:
#   .claude/skills/<name>     -> .agents/skills/<name>
#   .codex/skills/<name>      -> .agents/skills/<name>
#   .agents/skills/<name>/references/rules.md     -> docs/dev-harness/<area>/rules.md
#   .agents/skills/<name>/references/incidents.md -> docs/dev-harness/<area>/incidents.md
#
# In --allow-empty mode (used during PR 1 before skills exist), missing
# .agents/skills/<area>-skill directories are tolerated; existing
# symlinks are still checked.

set -euo pipefail

ALLOW_EMPTY=0
if [[ "${1:-}" == "--allow-empty" ]]; then
  ALLOW_EMPTY=1
fi

cd "$(git rev-parse --show-toplevel)"

SKILLS=(
  ui-harness-skill:ui
  plugin-system-skill:plugin
  media-player-skill:player
  test-stability-skill:test
  harness-curator-skill:_curator
  parity-audit-skill:_curator
)

errors=0

check_link() {
  local link="$1" expected="$2"
  if [[ ! -L "$link" ]]; then
    echo "ERROR: $link is not a symlink (expected -> $expected)" >&2
    return 1
  fi
  local target
  target="$(readlink "$link")"
  local resolved
  resolved="$(cd "$(dirname "$link")" && cd "$(dirname "$target")" && pwd)/$(basename "$target")"
  local expected_resolved
  expected_resolved="$(cd "$(dirname "$expected")" && pwd)/$(basename "$expected")"
  if [[ "$resolved" != "$expected_resolved" ]]; then
    echo "ERROR: $link -> $resolved, expected -> $expected_resolved" >&2
    return 1
  fi
}

for entry in "${SKILLS[@]}"; do
  name="${entry%%:*}"
  area="${entry##*:}"
  agents_dir=".agents/skills/$name"

  if [[ ! -d "$agents_dir" ]]; then
    if [[ "$ALLOW_EMPTY" -eq 1 ]]; then
      continue
    fi
    echo "ERROR: $agents_dir is missing" >&2
    errors=$((errors + 1))
    continue
  fi

  for tool_root in .claude/skills .codex/skills; do
    link="$tool_root/$name"
    expected="$agents_dir"
    check_link "$link" "$expected" || errors=$((errors + 1))
  done

  if [[ "$area" != "_curator" ]]; then
    for ref in rules incidents; do
      link="$agents_dir/references/$ref.md"
      expected="docs/dev-harness/$area/$ref.md"
      if [[ -e "$link" || -L "$link" ]]; then
        check_link "$link" "$expected" || errors=$((errors + 1))
      else
        if [[ "$ALLOW_EMPTY" -eq 0 ]]; then
          echo "ERROR: $link is missing" >&2
          errors=$((errors + 1))
        fi
      fi
    done
  fi
done

if [[ "$errors" -gt 0 ]]; then
  echo "Dev-harness symlink check failed with $errors error(s)." >&2
  exit 1
fi

echo "All dev-harness symlinks valid."
