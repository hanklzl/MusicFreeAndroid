#!/usr/bin/env bash
# scripts/parity-audit/upload-screenshots.sh
# usage: upload-screenshots.sh <run_id> <scenario_id> <waypoint> <rn_png> <android_png>
# 输出两行：rn_url 与 android_url（按调用顺序）

set -euo pipefail

RUN_ID="$1"
SCENARIO="$2"
WAYPOINT="$3"
RN_PNG="$4"
ANDROID_PNG="$5"

TAG="parity-screenshots"
REPO_ROOT="$(git rev-parse --show-toplevel)"
REMOTE_URL=$(git -C "$REPO_ROOT" remote get-url origin)
# 解析 owner/repo（兼容 git@github.com:owner/repo.git 与 https）
OWNER_REPO=$(echo "$REMOTE_URL" | sed -E 's#^.*[:/]([^:/]+/[^/]+)\.git$#\1#')

RN_NAME="${RUN_ID}__${SCENARIO}__${WAYPOINT}__rn.png"
AN_NAME="${RUN_ID}__${SCENARIO}__${WAYPOINT}__android.png"

# Maestro 默认截图名是 waypoint id；这里复制并改名以平铺到 release flat 命名空间
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp "$RN_PNG"      "$TMP/$RN_NAME"
cp "$ANDROID_PNG" "$TMP/$AN_NAME"

gh release upload "$TAG" "$TMP/$RN_NAME" "$TMP/$AN_NAME" --clobber >&2

echo "https://github.com/${OWNER_REPO}/releases/download/${TAG}/${RN_NAME}"
echo "https://github.com/${OWNER_REPO}/releases/download/${TAG}/${AN_NAME}"
