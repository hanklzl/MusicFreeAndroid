#!/usr/bin/env bash
# scripts/parity-audit/bootstrap.sh
# 校验 parity-audit 所需的本地工具与设备。失败立即 exit。

set -euo pipefail

err() { echo "ERROR: $*" >&2; exit 2; }

command -v adb     >/dev/null 2>&1 || err "adb not found in PATH"
command -v maestro >/dev/null 2>&1 || err "maestro not found in PATH; install: https://maestro.mobile.dev/"
command -v gh      >/dev/null 2>&1 || err "gh (GitHub CLI) not found"
command -v python3 >/dev/null 2>&1 || err "python3 not found"

python3 - <<'PY' || err "python deps missing; pip install scikit-image opencv-python-headless pyyaml"
import importlib
for m in ("skimage", "cv2", "yaml"):
    importlib.import_module(m)
PY

DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
[ -n "$DEVICES" ] || err "no adb device connected"

[ -d "../MusicFree" ] || err "../MusicFree (RN reference repo) not found"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" = "main" ]; then
  err "refuse to run audit on main; use git worktree (see AGENTS.md)"
fi

echo "OK adb=$(adb version | head -n1)"
echo "OK maestro=$(maestro --version 2>&1 | head -n1)"
echo "OK device(s): $DEVICES"
echo "OK branch=$BRANCH (not main)"
