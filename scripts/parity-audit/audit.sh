#!/usr/bin/env bash
# scripts/parity-audit/audit.sh
# 顶层入口：bootstrap → build → install → install-plugins → run-scenario × N → diff → state 更新 → REPORT.md
# usage: audit.sh --scope page:home --mode dry-run --limit 1 [--device <serial>]
#
# 注意：本 Task 阶段 audit.sh 仅支持 dry-run；--mode audit 的 Issue 创建路径在 Task 19 接入。

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
SCOPE=""
MODE="audit"
LIMIT=5
DEVICE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --scope)  SCOPE="$2";   shift 2 ;;
    --mode)   MODE="$2";    shift 2 ;;
    --limit)  LIMIT="$2";   shift 2 ;;
    --device) DEVICE="$2";  shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

# audit.sh 入口仍支持 --mode audit | dry-run
if [ "$MODE" != "audit" ] && [ "$MODE" != "dry-run" ]; then
  echo "ERROR: --mode must be audit or dry-run" >&2; exit 1
fi

if [ "$MODE" = "audit" ]; then
  bash "$REPO_ROOT/scripts/parity-audit/ensure-labels.sh"
  bash "$REPO_ROOT/scripts/parity-audit/ensure-release.sh"
fi

bash "$REPO_ROOT/scripts/parity-audit/bootstrap.sh"

RUN_ID=$(date +%Y-%m-%d-%H%M)
RUN_DIR="$REPO_ROOT/docs/parity-audit/runs/$RUN_ID"
mkdir -p "$RUN_DIR"

AGENT_LOG="$RUN_DIR/agent.log.jsonl"
log_event() {
  python3 -c '
import datetime, json, sys
d = {
  "ts": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
  **json.loads(sys.argv[1])
}
print(json.dumps(d, ensure_ascii=False))
' "$1" >> "$AGENT_LOG"
}

log_event '{"phase":"start","event":"run_started","run_id":"'"$RUN_ID"'","scope":"'"$SCOPE"'","mode":"'"$MODE"'"}'

# preflight: build + install + plugin bootstrap
log_event '{"phase":"preflight","event":"rn_build_started"}'
RN_APK=$(bash "$REPO_ROOT/scripts/parity-audit/build-rn.sh")
log_event '{"phase":"preflight","event":"rn_build_ok"}'

log_event '{"phase":"preflight","event":"android_build_started"}'
ANDROID_APK=$(bash "$REPO_ROOT/scripts/parity-audit/build-android.sh")
log_event '{"phase":"preflight","event":"android_build_ok"}'

bash "$REPO_ROOT/scripts/parity-audit/install-both.sh" "$RN_APK" "$ANDROID_APK" "$DEVICE"
log_event '{"phase":"preflight","event":"install_ok"}'

bash "$REPO_ROOT/scripts/parity-audit/install-plugins.sh" "$DEVICE"
log_event '{"phase":"preflight","event":"plugin_bootstrap_ok"}'

# scenarios 选择：本 Task 阶段只支持 page:<x> 与 all
if [ -z "$SCOPE" ] || [ "$SCOPE" = "all" ]; then
  SCENARIO_IDS=$(ls "$REPO_ROOT/docs/parity-audit/scenarios/" | sed 's/\.yaml$//')
elif [[ "$SCOPE" == page:* ]]; then
  PAGE="${SCOPE#page:}"
  SCENARIO_IDS=$(python3 - <<PY
import os, yaml, pathlib
out = []
for p in pathlib.Path("$REPO_ROOT/docs/parity-audit/scenarios").glob("*.yaml"):
    data = yaml.safe_load(open(p))
    if data.get("page") == "$PAGE":
        out.append(data["id"])
print("\n".join(out))
PY
)
else
  echo "audit.sh doesn't yet support scope=$SCOPE; aborting" >&2
  exit 1
fi

REPORT="$RUN_DIR/REPORT.md"
{
  echo "# Parity Audit Run $RUN_ID"
  echo
  echo "- scope: \`$SCOPE\`  · mode: \`$MODE\`  · limit: $LIMIT"
  echo "- RN APK: \`$RN_APK\`"
  echo "- Android APK: \`$ANDROID_APK\`"
  echo
  echo "## Scenarios"
  echo
  echo "| scenario | verdict | severity | kind | screenshot diff | event diff count | issue |"
  echo "|---|---|---|---|---|---|---|"
} > "$REPORT"

N=0
for SID in $SCENARIO_IDS; do
  N=$((N+1))
  if [ "$N" -gt "$LIMIT" ]; then
    log_event '{"phase":"scenario","event":"limit_reached","limit":'"$LIMIT"'}'
    break
  fi
  log_event '{"phase":"scenario","scenario":"'"$SID"'","event":"started"}'

  bash "$REPO_ROOT/scripts/parity-audit/run-scenario.sh" "$SID" rn      "$RUN_ID" "$DEVICE"
  bash "$REPO_ROOT/scripts/parity-audit/run-scenario.sh" "$SID" android "$RUN_ID" "$DEVICE"

  SCEN_DIR="$RUN_DIR/scenarios/$SID"
  RN_LOGCAT="$SCEN_DIR/rn/logcat.raw.txt"
  AN_LOGCAT="$SCEN_DIR/android/logcat.raw.txt"
  RN_EVENTS="$SCEN_DIR/rn/events.jsonl"
  AN_EVENTS="$SCEN_DIR/android/events.jsonl"
  RN_PARSER="$REPO_ROOT/.agents/skills/parity-audit-skill/references/rn-logcat-parser.json"

  python3 "$REPO_ROOT/scripts/parity-audit/parse_events.py" \
    --logcat "$RN_LOGCAT" --side rn --rn-parser-config "$RN_PARSER" --out "$RN_EVENTS"
  python3 "$REPO_ROOT/scripts/parity-audit/parse_events.py" \
    --logcat "$AN_LOGCAT" --side android --out "$AN_EVENTS"

  # 截图 SSIM
  SSIM_RESULTS="$SCEN_DIR/screenshot_ssim.json"
  python3 - <<PY > "$SSIM_RESULTS"
import json, pathlib, sys
sys.path.insert(0, "$REPO_ROOT/scripts/parity-audit")
import screenshot_ssim
rn_dir = pathlib.Path("$SCEN_DIR/rn")
an_dir = pathlib.Path("$SCEN_DIR/android")
out = []
for rn_img in sorted(rn_dir.glob("waypoint-*.png")):
    wp = rn_img.stem.replace("waypoint-", "")
    an_img = an_dir / rn_img.name
    if not an_img.exists():
        continue
    r = screenshot_ssim.compute(rn_img, an_img)
    r["waypoint"] = wp
    out.append(r)
print(json.dumps(out))
PY

  DIFF_JSON="$SCEN_DIR/diff.json"
  python3 "$REPO_ROOT/scripts/parity-audit/compare.py" \
    --scenario-id "$SID" \
    --rn-events "$RN_EVENTS" \
    --android-events "$AN_EVENTS" \
    --screenshot-results "$SSIM_RESULTS" \
    --out "$DIFF_JSON"

  VERDICT=$(python3 -c "import json,sys; print(json.load(open('$DIFF_JSON'))['verdict'])")
  SEVERITY=$(python3 -c "import json,sys; print(json.load(open('$DIFF_JSON'))['severity'])")
  EVT_COUNT=$(python3 -c "import json,sys; print(len(json.load(open('$DIFF_JSON'))['event_diffs']))")
  SCREEN_DIFF=$(python3 -c "import json,sys; print(sum(1 for s in json.load(open('$DIFF_JSON'))['screenshot_diffs'] if s['verdict']=='visual_diff'))")
  PRIORITY=$(python3 -c "import yaml,sys; print(yaml.safe_load(open('$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml'))['priority'])")
  OPEN_ISSUES_JSON="[]"

  # 取额外元数据
  RN_SHA=$(git -C ../MusicFree rev-parse HEAD 2>/dev/null || echo "unknown")
  ANDROID_SHA=$(git rev-parse HEAD)
  DEVICE_MODEL=$(adb ${DEVICE:+-s $DEVICE} shell getprop ro.product.model | tr -d '\r')
  API_LEVEL=$(adb ${DEVICE:+-s $DEVICE} shell getprop ro.build.version.sdk | tr -d '\r')

  # 仅当 verdict=diff_found && severity ∈ {major, critical} 才走 Issue 路径
  SHOULD_FILE=$(python3 -c "
import json
d = json.load(open('$DIFF_JSON'))
print('yes' if d['verdict'] == 'diff_found' and d['severity'] in ('major','critical') else 'no')
")

  RN_URL=""
  AN_URL=""
  if [ "$SHOULD_FILE" = "yes" ] && [ "$MODE" = "audit" ]; then
    # 取 SSIM < 阈值的第一个 waypoint 截图作为 Issue body 引用
    WP=$(python3 -c "
import json
d = json.load(open('$DIFF_JSON'))
vis = [s for s in d.get('screenshot_diffs', []) if s.get('verdict') == 'visual_diff']
print(vis[0]['waypoint'] if vis else '')
")
    if [ -n "$WP" ]; then
      RN_PNG="$SCEN_DIR/rn/${WP}.png"
      AN_PNG="$SCEN_DIR/android/${WP}.png"
      [ -f "$RN_PNG" ] || RN_PNG=$(ls "$SCEN_DIR/rn"/*.png 2>/dev/null | head -n1)
      [ -f "$AN_PNG" ] || AN_PNG=$(ls "$SCEN_DIR/android"/*.png 2>/dev/null | head -n1)
      URLS=$(bash "$REPO_ROOT/scripts/parity-audit/upload-screenshots.sh" \
        "$RUN_ID" "$SID" "$WP" "$RN_PNG" "$AN_PNG")
      RN_URL=$(echo "$URLS" | sed -n '1p')
      AN_URL=$(echo "$URLS" | sed -n '2p')
    fi
  fi

  if [ "$SHOULD_FILE" = "yes" ]; then
    # 用本地 LLM-friendly 占位填 file_issue.py 的人写字段。当前不让 LLM 真写叙述
    # （留给后续 plan 集成）；这里仅产稳定 fallback，保证 Issue body 不空。
    SUMMARY=$(python3 -c "
import json
d = json.load(open('$DIFF_JSON'))
parts = []
for e in d.get('event_diffs', [])[:3]:
    parts.append(f\"{e.get('waypoint','?')}/{e.get('kind','?')} {e.get('verdict','?')}\")
print('Android 与 RN 行为差异：' + '; '.join(parts) if parts else 'Android 与 RN 出现可视差异（见截图）')
")
    REPRO_STEPS=$(python3 -c "
import yaml
d = yaml.safe_load(open('$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml'))
lines = ['1. 冷启动 Android debug build']
for i, wp in enumerate(d.get('waypoints', []), start=2):
    lines.append(f\"{i}. 等到 waypoint: {wp['id']}（{wp.get('description','')}）\")
print('\n'.join(lines))
")
    EXPECTED="对齐 RN 同 waypoint 的事件流与视觉表现（详见 Event Diff 节）。"
    ACTUAL="见上方 Event Diff 与对比截图。"
    FIX_HINTS=$(python3 -c "
import yaml
d = yaml.safe_load(open('$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml'))
notes = d.get('notes', '').strip().split('\n')
print('\n'.join(f'- {n}' for n in notes if n))
")
    RN_ALSO=$(python3 -c "
import json, pathlib
log = pathlib.Path('$SCEN_DIR/rn/logcat.raw.txt').read_text(errors='replace') if pathlib.Path('$SCEN_DIR/rn/logcat.raw.txt').exists() else ''
crashed = 'FATAL EXCEPTION' in log or 'AndroidRuntime' in log
print('true' if crashed else 'false')
")

    FILE_ISSUE_OUT="$SCEN_DIR/issue_action.json"
    python3 "$REPO_ROOT/scripts/parity-audit/file_issue.py" \
      --diff "$DIFF_JSON" \
      --scenario-yaml "$REPO_ROOT/docs/parity-audit/scenarios/$SID.yaml" \
      --template "$REPO_ROOT/.agents/skills/parity-audit-skill/references/issue-template.md" \
      --run-id "$RUN_ID" \
      --rn-sha "$RN_SHA" \
      --android-sha "$ANDROID_SHA" \
      --device-model "$DEVICE_MODEL" \
      --api-level "$API_LEVEL" \
      --rn-screenshot-url "$RN_URL" \
      --android-screenshot-url "$AN_URL" \
      --summary "$SUMMARY" \
      --repro-steps "$REPRO_STEPS" \
      --expected "$EXPECTED" \
      --actual "$ACTUAL" \
      --fix-hints "$FIX_HINTS" \
      --rn-also-crashed "$RN_ALSO" \
      --output-dir "$SCEN_DIR" \
      --mode "$MODE" \
      > "$FILE_ISSUE_OUT"

    ISSUE_NUMBER=$(python3 -c "import json; print(json.load(open('$FILE_ISSUE_OUT')).get('issue_number') or '')")
    if [ -n "$ISSUE_NUMBER" ]; then
      OPEN_ISSUES_JSON="[$ISSUE_NUMBER]"
      log_event '{"phase":"issue","scenario":"'"$SID"'","event":"issue_done","number":'"$ISSUE_NUMBER"',"mode":"'"$MODE"'"}'
    fi
  fi

  python3 - <<PY
import json, pathlib, sys
sys.path.insert(0, "$REPO_ROOT/scripts/parity-audit")
import state as state_mod
p = pathlib.Path("$REPO_ROOT/docs/parity-audit/state.json")
s = state_mod.load(p)
state_mod.upsert_scenario(
    s, "$SID",
    priority="$PRIORITY",
    last_run_id="$RUN_ID",
    last_status="$VERDICT",
    open_issue_numbers=json.loads("""$OPEN_ISSUES_JSON"""),
    blocked_reason=None,
)
state_mod.save(s, p)
pathlib.Path("$REPO_ROOT/docs/parity-audit/queue.md").write_text(state_mod.render_queue_md(s))
PY

  KIND=$(python3 -c "
import json, sys
sys.path.insert(0, '$REPO_ROOT/scripts/parity-audit')
import file_issue
print(file_issue.decide_kind(json.load(open('$DIFF_JSON'))))
" 2>/dev/null || echo "—")
  ISSUE_LINK=$(python3 -c "
import json, pathlib
p = pathlib.Path('$SCEN_DIR/issue_action.json')
if not p.exists():
    print('—')
else:
    d = json.load(open(p))
    n = d.get('issue_number')
    if d.get('action') == 'dry_run':
        print('(dry-run draft)')
    elif n:
        print(f'#{n} ({d.get(\"action\")})')
    elif d.get('action') == 'skip_multiple':
        print(f'skip ({d.get(\"action_payload\")})')
    else:
        print('—')
")
  echo "| $SID | $VERDICT | $SEVERITY | $KIND | $SCREEN_DIFF | $EVT_COUNT | $ISSUE_LINK |" >> "$REPORT"
  log_event '{"phase":"scenario","scenario":"'"$SID"'","event":"diff_computed","verdict":"'"$VERDICT"'","severity":"'"$SEVERITY"'"}'
done

{
  echo
  echo "## Notes"
  echo
  echo "- mode=\`$MODE\`：dry-run 仅生成 issue.md 草稿；audit 通过 \`gh issue create\` 提交。"
  echo "- agent.log.jsonl: \`$AGENT_LOG\`"
} >> "$REPORT"

log_event '{"phase":"end","event":"run_finished"}'
echo "REPORT: $REPORT"
