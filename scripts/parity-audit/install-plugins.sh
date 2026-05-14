#!/usr/bin/env bash
# scripts/parity-audit/install-plugins.sh
# 把 parity-plugins.json 中每个插件订阅 URL 喂给两侧 Maestro flow。

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
PLUGINS_JSON="$REPO_ROOT/docs/parity-audit/parity-plugins.json"
DEVICE="${1:-}"

DEVICE_ARG=""
[ -n "$DEVICE" ] && DEVICE_ARG="--device $DEVICE"

URLS=$(python3 -c '
import json, sys
data = json.load(open(sys.argv[1]))
for p in data.get("plugins", []):
    print(p["subscription_url"])
' "$PLUGINS_JSON")

while IFS= read -r url; do
  [ -z "$url" ] && continue
  echo "==> installing $url on RN side" >&2
  maestro $DEVICE_ARG test \
    -e PLUGIN_URL="$url" \
    maestro/flows/parity/_bootstrap/install-plugins.rn.yaml
  echo "==> installing $url on Android side" >&2
  maestro $DEVICE_ARG test \
    -e PLUGIN_URL="$url" \
    maestro/flows/parity/_bootstrap/install-plugins.android.yaml
done <<<"$URLS"
