#!/usr/bin/env bash
# Local one-shot driver: symlinks check + grep guards + JVM contract tests.
#
# In PR 1 the contract-test step is a no-op until contract tests land.
# Pass --skip-contract-tests to bypass the gradle invocation while
# iterating on docs/scripts.

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

SKIP_CONTRACT=0
ALLOW_EMPTY_SYMLINKS=0
for arg in "$@"; do
  case "$arg" in
    --skip-contract-tests) SKIP_CONTRACT=1 ;;
    --allow-empty-symlinks) ALLOW_EMPTY_SYMLINKS=1 ;;
    *) echo "Unknown arg: $arg" >&2; exit 2 ;;
  esac
done

echo "==> Symlinks"
if [[ "$ALLOW_EMPTY_SYMLINKS" -eq 1 ]]; then
  bash scripts/dev-harness/symlinks-check.sh --allow-empty
else
  bash scripts/dev-harness/symlinks-check.sh
fi

echo "==> Grep guards"
python3 scripts/dev-harness/grep-check.py

if [[ "$SKIP_CONTRACT" -eq 1 ]]; then
  echo "==> Skipping contract tests (--skip-contract-tests)"
  exit 0
fi

echo "==> Compile-only test sources (all modules) — guards INC-2026-0016"
# Catch test fixture lag behind production VM constructors. assembleDebug skips
# test source compile; this step explicitly compiles every module's debug unit
# test sources so fixture drift fails the gate. Add new modules here.
./gradlew \
  :app:compileDebugUnitTestKotlin :core:compileDebugUnitTestKotlin :data:compileDebugUnitTestKotlin \
  :downloader:compileDebugUnitTestKotlin :plugin:compileDebugUnitTestKotlin :player:compileDebugUnitTestKotlin \
  :feature:home:compileDebugUnitTestKotlin :feature:player-ui:compileDebugUnitTestKotlin \
  :feature:search:compileDebugUnitTestKotlin :feature:settings:compileDebugUnitTestKotlin \
  :updater:compileDebugUnitTestKotlin \
  --no-daemon

echo "==> Contract tests (JVM)"
# Only invoke modules that currently host harness/contracts/ tests.
# Adding tests in other modules requires extending this list.
./gradlew \
  :app:testDebugUnitTest :plugin:testDebugUnitTest :feature:player-ui:testDebugUnitTest \
  :downloader:testDebugUnitTest :updater:testDebugUnitTest \
  --tests '*harness.contracts.*' --no-daemon
