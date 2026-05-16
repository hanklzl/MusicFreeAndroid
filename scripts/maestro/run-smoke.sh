#!/usr/bin/env bash
set -euo pipefail

APP_ID="com.hank.musicfree.debug"
APK_PATH="app/build/outputs/apk/debug/MusicFreeAndroid-arm64-v8a-debug.apk"
SUITE="core"
SKIP_BUILD=0
SKIP_INSTALL=0
CLEAR_STATE=0
SERIAL="${ANDROID_SERIAL:-}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_ROOT="build/maestro-smoke/${TIMESTAMP}"

CORE_FLOWS=(
  "maestro/flows/smoke/core/01_launch_default_plugins.yaml"
  "maestro/flows/smoke/core/02_search_play.yaml"
  "maestro/flows/smoke/core/03_settings_feedback_logs.yaml"
)

EXTENDED_FLOWS=(
  "maestro/flows/smoke/extended/recommend_sheets.yaml"
  "maestro/flows/smoke/extended/top_list_detail.yaml"
  "maestro/flows/smoke/extended/plugin_management.yaml"
  "maestro/flows/smoke/extended/player_queue.yaml"
)

usage() {
  cat <<'EOF'
Usage: bash scripts/maestro/run-smoke.sh [options]

Options:
  --suite core|extended|all   Flow suite to run. Default: core
  --skip-build                Do not run ./gradlew :app:assembleDebug
  --skip-install              Do not install the debug APK before running
  --clear-state               Clear app data before the first flow
  --serial <adb-serial>       Target adb device serial
  --help                      Show this help
EOF
}

require_option_value() {
  local option="$1"
  local value="${2:-}"
  if [[ -z "$value" || "$value" == --* ]]; then
    echo "Missing value for $option" >&2
    usage >&2
    exit 2
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --suite)
      require_option_value "$1" "${2:-}"
      SUITE="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --skip-install)
      SKIP_INSTALL=1
      shift
      ;;
    --clear-state)
      CLEAR_STATE=1
      shift
      ;;
    --serial)
      require_option_value "$1" "${2:-}"
      SERIAL="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

case "$SUITE" in
  core|extended|all) ;;
  *)
    echo "Invalid --suite value: $SUITE" >&2
    usage >&2
    exit 2
    ;;
esac

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 127
  fi
}

adb_cmd() {
  if [[ -n "$SERIAL" ]]; then
    adb -s "$SERIAL" "$@"
  else
    adb "$@"
  fi
}

select_device() {
  if [[ -n "$SERIAL" ]]; then
    if ! adb devices | awk 'NR > 1 && $1 == serial && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }' serial="$SERIAL"; then
      echo "Device is not available: $SERIAL" >&2
      adb devices >&2
      exit 1
    fi
    return
  fi

  local devices=()
  while IFS= read -r device; do
    [[ -n "$device" ]] && devices+=("$device")
  done < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')

  case "${#devices[@]}" in
    0)
      echo "No adb device in 'device' state." >&2
      adb devices >&2
      exit 1
      ;;
    1)
      SERIAL="${devices[0]}"
      ;;
    *)
      echo "Multiple adb devices are in 'device' state; pass --serial <adb-serial>." >&2
      adb devices >&2
      exit 1
      ;;
  esac
}

flows_for_suite() {
  case "$SUITE" in
    core)
      printf '%s\n' "${CORE_FLOWS[@]}"
      ;;
    extended)
      printf '%s\n' "${EXTENDED_FLOWS[@]}"
      ;;
    all)
      printf '%s\n' "${CORE_FLOWS[@]}" "${EXTENDED_FLOWS[@]}"
      ;;
  esac
}

flow_name() {
  basename "$1" .yaml
}

write_device_info() {
  local output_file="$1"
  {
    echo "serial=$SERIAL"
    echo "model=$(adb_cmd shell getprop ro.product.model | tr -d '\r')"
    echo "manufacturer=$(adb_cmd shell getprop ro.product.manufacturer | tr -d '\r')"
    echo "sdk=$(adb_cmd shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "release=$(adb_cmd shell getprop ro.build.version.release | tr -d '\r')"
    echo "suite=$SUITE"
    echo "appId=$APP_ID"
    echo "apk=$APK_PATH"
  } > "$output_file"
}

collect_logcat() {
  local flow_dir="$1"
  adb_cmd logcat -d > "${flow_dir}/logcat-full.txt" || true
  grep -E 'AndroidRuntime|MusicFree|MfLog|default_plugin_bootstrap|plugin_|search_|player_|feedback_' \
    "${flow_dir}/logcat-full.txt" > "${flow_dir}/logcat-filtered.txt" || true
}

require_command adb
require_command maestro
select_device

mkdir -p "$ARTIFACT_ROOT"
write_device_info "${ARTIFACT_ROOT}/device.txt"

echo "Maestro smoke suite: $SUITE"
echo "ADB device: $SERIAL"
echo "Artifacts: $ARTIFACT_ROOT"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  ./gradlew :app:assembleDebug --no-daemon
fi

if [[ "$SKIP_INSTALL" -eq 0 ]]; then
  if [[ ! -f "$APK_PATH" ]]; then
    echo "Debug APK not found: $APK_PATH" >&2
    exit 1
  fi
  adb_cmd install -r "$APK_PATH"
fi

if [[ "$CLEAR_STATE" -eq 1 ]]; then
  adb_cmd shell pm clear "$APP_ID"
fi

FAILED=0
PASSED_COUNT=0
FAILED_COUNT=0
PASSED_FLOWS=()
FAILED_FLOWS=()

while IFS= read -r flow; do
  [[ -n "$flow" ]] || continue
  name="$(flow_name "$flow")"
  flow_dir="${ARTIFACT_ROOT}/${SUITE}/${name}"
  mkdir -p "$flow_dir"

  if [[ ! -f "$flow" ]]; then
    echo "Missing flow: $flow" | tee "${flow_dir}/maestro.log"
    FAILED=1
    FAILED_COUNT=$((FAILED_COUNT + 1))
    FAILED_FLOWS+=("$flow")
    continue
  fi

  echo "Running $flow"
  adb_cmd logcat -c || true

  if maestro test \
    --udid="$SERIAL" \
    --test-output-dir="${flow_dir}/maestro-output" \
    --format JUNIT \
    --output "${flow_dir}/junit.xml" \
    "$flow" > "${flow_dir}/maestro.log" 2>&1; then
    echo "PASS $flow" | tee "${flow_dir}/status.txt"
    PASSED_COUNT=$((PASSED_COUNT + 1))
    PASSED_FLOWS+=("$flow")
  else
    echo "FAIL $flow" | tee "${flow_dir}/status.txt"
    FAILED=1
    FAILED_COUNT=$((FAILED_COUNT + 1))
    FAILED_FLOWS+=("$flow")
  fi

  collect_logcat "$flow_dir"
done < <(flows_for_suite)

{
  echo "suite=$SUITE"
  echo "artifacts=$ARTIFACT_ROOT"
  echo "passed=$PASSED_COUNT"
  if [[ "$PASSED_COUNT" -gt 0 ]]; then
    for flow in "${PASSED_FLOWS[@]}"; do
      echo "pass=$flow"
    done
  fi
  echo "failed=$FAILED_COUNT"
  if [[ "$FAILED_COUNT" -gt 0 ]]; then
    for flow in "${FAILED_FLOWS[@]}"; do
      echo "fail=$flow"
    done
  fi
} > "${ARTIFACT_ROOT}/summary.txt"

cat "${ARTIFACT_ROOT}/summary.txt"
exit "$FAILED"
