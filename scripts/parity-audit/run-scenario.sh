#!/usr/bin/env bash
# scripts/parity-audit/run-scenario.sh
# usage: run-scenario.sh <scenario_id> <side> <run_id> [device]
#   scenario_id: e.g. home_entry
#   side:        rn | android
#   run_id:      e.g. 2026-05-15-1200
#   device:      optional adb serial

set -euo pipefail

SCENARIO="$1"
SIDE="$2"
RUN_ID="$3"
DEVICE="${4:-}"

REPO_ROOT="$(git rev-parse --show-toplevel)"
SCENARIO_YAML="$REPO_ROOT/docs/parity-audit/scenarios/${SCENARIO}.yaml"
[ -f "$SCENARIO_YAML" ] || { echo "ERROR: scenario yaml missing: $SCENARIO_YAML" >&2; exit 1; }

# 取 appId 与 flow path
APPID=$(python3 -c "import yaml,sys; d=yaml.safe_load(open(sys.argv[1])); print(d[sys.argv[2]+'_appid'])" "$SCENARIO_YAML" "$SIDE")
FLOW=$(python3 -c "import yaml,sys; d=yaml.safe_load(open(sys.argv[1])); print(d[sys.argv[2]+'_flow'])" "$SCENARIO_YAML" "$SIDE")

OUT="$REPO_ROOT/docs/parity-audit/runs/$RUN_ID/scenarios/$SCENARIO/$SIDE"
mkdir -p "$OUT"

# 清 app data 保证冷启动
ADB="adb"
[ -n "$DEVICE" ] && ADB="adb -s $DEVICE"
$ADB shell pm clear "$APPID" >/dev/null

# 起后台 logcat
PID_FILE="$OUT/logcat.pid"
$ADB logcat -c
# 用 --pid 需要 app 已启动；这里先全量记录，事后用 PARITY_MARK 锚点切片
$ADB logcat -v threadtime > "$OUT/logcat.raw.txt" &
echo $! > "$PID_FILE"
trap 'kill "$(cat "$PID_FILE")" 2>/dev/null || true' EXIT

# 跑 Maestro flow（同步阻塞）
MAESTRO_ARG=""
[ -n "$DEVICE" ] && MAESTRO_ARG="--device $DEVICE"

if ! maestro $MAESTRO_ARG test \
       -e RUN_ID="$RUN_ID" -e SCENARIO="$SCENARIO" -e DEVICE="$DEVICE" \
       "$REPO_ROOT/$FLOW"; then
  echo "MAESTRO_FAILED" > "$OUT/.status"
  exit 0     # 不让一个 scenario 失败拖死整轮；上层根据 .status 处理
fi

# 移动 Maestro 截图（默认在 ~/.maestro/tests/<dateTime>/screenshots/）到产物目录
MAESTRO_LATEST=$(ls -1dt "$HOME"/.maestro/tests/*/screenshots 2>/dev/null | head -n1 || true)
if [ -n "$MAESTRO_LATEST" ] && [ -d "$MAESTRO_LATEST" ]; then
  cp "$MAESTRO_LATEST"/*.png "$OUT"/ 2>/dev/null || true
fi

echo "OK" > "$OUT/.status"
echo "scenario=$SCENARIO side=$SIDE -> $OUT"
