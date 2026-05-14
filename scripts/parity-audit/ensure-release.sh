#!/usr/bin/env bash
# scripts/parity-audit/ensure-release.sh
# 幂等创建 parity-screenshots prerelease。已存在则跳过。

set -euo pipefail

TAG="parity-screenshots"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "release $TAG already exists"
  exit 0
fi

gh release create "$TAG" --prerelease \
  --title "Parity screenshot bucket" \
  --notes "Auto-managed by parity-audit-skill. Do not edit by hand. See docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md §5.6."

echo "created release $TAG"
