# Maestro Real-Network Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Maestro real-network smoke flows, a logcat-aware runner script, and an acceptance guide for validating MusicFreeAndroid core runtime journeys against the default debug plugins.

**Architecture:** Add a small set of nonvisual Compose test anchors first so Maestro can use stable resource IDs instead of coordinates. Then add layered Maestro flows under `maestro/flows/smoke/`, a single bash runner that builds/installs/runs/captures evidence, and documentation that explains how to interpret real-network failures with `logcat` and structured logs.

**Tech Stack:** Maestro YAML, Android Debug APK (`com.hank.musicfree.debug`), Bash, `adb`, `logcat`, Jetpack Compose `testTag` / `testTagsAsResourceId`, existing `MfLog` structured events.

---

## Context And Guardrails

- Approved spec: `docs/superpowers/specs/2026-05-13-maestro-real-network-smoke-design.md`.
- Test rules read: `docs/dev-harness/test/rules.md` and `docs/dev-harness/test/incidents.md`.
- UI rules read because this plan adds nonvisual Compose anchors: `docs/dev-harness/ui/rules.md` and `docs/dev-harness/ui/incidents.md`.
- Plugin/player rules were already read during brainstorming because these smoke flows exercise default plugins and playback.
- Maestro Android `inputText` does not reliably support non-ASCII input; use ASCII query `jay` in automated search flow. Chinese search terms remain for manual exploratory checks.
- These flows are real-network smoke checks. They are intentionally not hermetic and should not be added to default Gradle or CI required gates.

## File Structure

- Modify `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
  - Add stable IDs for Maestro-facing dynamic rows and plugin management root.
- Modify `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
  - Add `testTag` to music result rows, media result rows, and sheet result items.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`
  - Add `testTag` to recommendation sheet grid items.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`
  - Add `testTag` to top-list rows.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`
  - Add a root `testTag` and `testTagsAsResourceId` semantics.
- Modify `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt`
  - Extend uniqueness coverage for new anchors.
- Create `maestro/flows/smoke/core/01_launch_default_plugins.yaml`
- Create `maestro/flows/smoke/core/02_search_play.yaml`
- Create `maestro/flows/smoke/core/03_settings_feedback_logs.yaml`
- Create `maestro/flows/smoke/extended/recommend_sheets.yaml`
- Create `maestro/flows/smoke/extended/top_list_detail.yaml`
- Create `maestro/flows/smoke/extended/plugin_management.yaml`
- Create `maestro/flows/smoke/extended/player_queue.yaml`
- Create `scripts/maestro/run-smoke.sh`
- Create `docs/maestro-smoke-acceptance.md`

## Task 1: Add Stable Maestro Anchors

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt`

- [ ] **Step 1: Extend `FidelityAnchors` with Maestro-facing IDs**

Modify `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt` so the relevant objects include these constants:

```kotlin
object FidelityAnchors {
    object Screen {
        const val HomeRoot = "screen.home.root"
        const val SearchRoot = "screen.search.root"
        const val RecommendSheetsRoot = "screen.recommendSheets.root"
        const val TopListRoot = "screen.topList.root"
        const val HistoryRoot = "screen.history.root"
        const val SettingsRoot = "screen.settings.root"
        const val PermissionsRoot = "screen.permissions.root"
        const val LocalRoot = "screen.local.root"
        const val PluginListRoot = "screen.pluginList.root"
    }

    object Search {
        const val Input = "search.input"
        const val ResultMusicRow = "search.result.musicRow"
        const val ResultMediaRow = "search.result.mediaRow"
        const val ResultSheetItem = "search.result.sheetItem"
    }

    object RecommendSheets {
        const val Item = "recommendSheets.item"
    }

    object TopList {
        const val Item = "topList.item"
    }
}
```

Keep all existing constants in `FidelityAnchors.kt`; insert only the new constants into their matching objects. If the file has additional nested objects after the snippets above, leave them unchanged.

- [ ] **Step 2: Add search result tags**

In `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt`, update the `MediaResultItem`, `SheetResultItem`, and `MusicResultItem` composables.

Change the `MediaResultItem` root `Row` modifier to:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .height(rpx(120))
    .clickable(onClick = onClick)
    .testTag(FidelityAnchors.Search.ResultMediaRow)
    .padding(horizontal = rpx(24)),
```

Change the `SheetResultItem` root `Column` modifier to:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .clickable(onClick = onClick)
    .testTag(FidelityAnchors.Search.ResultSheetItem),
```

Change the `MusicResultItem` root `Row` modifier to:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .height(rpx(120))
    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    .testTag(FidelityAnchors.Search.ResultMusicRow)
    .padding(horizontal = rpx(24)),
```

`SearchScreen` already imports `testTag` and `FidelityAnchors`, so no new import is needed for this file.

- [ ] **Step 3: Add recommendation sheet item tag**

In `feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt`, update `RecommendSheetGridItem`.

Change the root `Column` modifier to:

```kotlin
modifier = Modifier
    .clickable(onClick = onClick)
    .testTag(FidelityAnchors.RecommendSheets.Item),
```

The file already imports `testTag` and `FidelityAnchors`.

- [ ] **Step 4: Add top-list row tag**

In `feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt`, update `TopListItemRow`.

Change the root `Row` modifier to:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .clickable(onClick = onClick)
    .testTag(FidelityAnchors.TopList.Item)
    .padding(horizontal = rpx(24), vertical = rpx(14)),
```

The file already imports `testTag` and `FidelityAnchors`.

- [ ] **Step 5: Add plugin management root tag**

In `feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt`, add imports:

```kotlin
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.hank.musicfree.core.ui.FidelityAnchors
```

Then change the `MusicFreeScreenScaffold` modifier from:

```kotlin
modifier = modifier,
```

to:

```kotlin
modifier = modifier
    .testTag(FidelityAnchors.Screen.PluginListRoot)
    .semantics { testTagsAsResourceId = true },
```

If `testTag` is not already imported in this file, add:

```kotlin
import androidx.compose.ui.platform.testTag
```

- [ ] **Step 6: Extend anchor contract test**

In `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt`, append the new anchors to the `expanded homepage fidelity anchors stay unique and non blank` list:

```kotlin
FidelityAnchors.Screen.PluginListRoot,
FidelityAnchors.Search.ResultMusicRow,
FidelityAnchors.Search.ResultMediaRow,
FidelityAnchors.Search.ResultSheetItem,
FidelityAnchors.RecommendSheets.Item,
FidelityAnchors.TopList.Item,
```

Keep the existing assertions:

```kotlin
assertEquals(anchors.size, anchors.toSet().size)
assertTrue(anchors.all { it.isNotBlank() })
```

- [ ] **Step 7: Run focused anchor test**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*HomeAnchorContractTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run UI harness grep guard**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: no violations. If the script prints existing unrelated violations, copy the exact output into the task notes before continuing.

- [ ] **Step 9: Commit anchor changes**

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt \
  feature/search/src/main/java/com/hank/musicfree/feature/search/SearchScreen.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/recommendsheets/RecommendSheetsScreen.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/toplist/TopListScreen.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/pluginlist/PluginListScreen.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt
git commit -m "test(maestro): 补充 smoke 用例稳定锚点"
```

## Task 2: Add Core Maestro Flows

**Files:**
- Create: `maestro/flows/smoke/core/01_launch_default_plugins.yaml`
- Create: `maestro/flows/smoke/core/02_search_play.yaml`
- Create: `maestro/flows/smoke/core/03_settings_feedback_logs.yaml`

- [ ] **Step 1: Create launch/default-plugin flow**

Create `maestro/flows/smoke/core/01_launch_default_plugins.yaml`:

```yaml
appId: com.hank.musicfree.debug
name: smoke_core_launch_default_plugins
tags:
  - smoke
  - core
  - launch
---
- launchApp:
    stopApp: true
- tapOn:
    text: "允许|Allow|Allow notifications"
    optional: true
- extendedWaitUntil:
    visible:
      id: ".*home\\.navBar\\.search"
    timeout: 30000
- assertVisible:
    id: ".*home\\.navBar\\.menu"
- waitForAnimationToEnd:
    timeout: 5000
- takeScreenshot: smoke_core_launch_home
```

- [ ] **Step 2: Create search/play flow**

Create `maestro/flows/smoke/core/02_search_play.yaml`:

```yaml
appId: com.hank.musicfree.debug
name: smoke_core_search_play
tags:
  - smoke
  - core
  - search
  - player
---
- launchApp:
    stopApp: true
- tapOn:
    text: "允许|Allow|Allow notifications"
    optional: true
- extendedWaitUntil:
    visible:
      id: ".*home\\.navBar\\.search"
    timeout: 30000
- tapOn:
    id: ".*home\\.navBar\\.search"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*screen\\.search\\.root"
    timeout: 15000
- tapOn:
    id: ".*search\\.input"
    retryTapIfNoChange: true
- eraseText
- inputText: "jay"
- tapOn:
    text: "搜索"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*search\\.result\\.musicRow"
    timeout: 45000
- tapOn:
    id: ".*search\\.result\\.musicRow"
    index: 0
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*player\\.mini\\.root"
    timeout: 45000
- takeScreenshot: smoke_core_search_play_mini_player
```

- [ ] **Step 3: Create settings feedback-log confirmation flow**

Create `maestro/flows/smoke/core/03_settings_feedback_logs.yaml`:

```yaml
appId: com.hank.musicfree.debug
name: smoke_core_settings_feedback_logs
tags:
  - smoke
  - core
  - settings
  - feedback
---
- launchApp:
    stopApp: true
- tapOn:
    text: "允许|Allow|Allow notifications"
    optional: true
- extendedWaitUntil:
    visible:
      id: ".*home\\.navBar\\.menu"
    timeout: 30000
- tapOn:
    id: ".*home\\.navBar\\.menu"
    retryTapIfNoChange: true
- waitForAnimationToEnd:
    timeout: 5000
- extendedWaitUntil:
    visible:
      id: ".*home\\.drawer\\.settings\\.basic"
    timeout: 15000
- tapOn:
    id: ".*home\\.drawer\\.settings\\.basic"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*settings\\.basic\\.root"
    timeout: 15000
- scrollUntilVisible:
    element:
      text: "生成日志包并分享"
    direction: DOWN
    timeout: 30000
    centerElement: true
- tapOn:
    text: "生成日志包并分享"
    retryTapIfNoChange: true
- assertVisible: "分享日志包"
- assertVisible:
    text: ".*搜索词、请求地址、插件返回内容以及设备信息.*"
- tapOn: "取消"
- assertNotVisible: "分享日志包"
- takeScreenshot: smoke_core_settings_feedback_logs
```

- [ ] **Step 4: Validate YAML files exist**

Run:

```bash
find maestro/flows/smoke/core -maxdepth 1 -type f -name '*.yaml' | sort
```

Expected output:

```text
maestro/flows/smoke/core/01_launch_default_plugins.yaml
maestro/flows/smoke/core/02_search_play.yaml
maestro/flows/smoke/core/03_settings_feedback_logs.yaml
```

- [ ] **Step 5: Commit core flow files**

```bash
git add maestro/flows/smoke/core/01_launch_default_plugins.yaml \
  maestro/flows/smoke/core/02_search_play.yaml \
  maestro/flows/smoke/core/03_settings_feedback_logs.yaml
git commit -m "test(maestro): 添加核心 smoke flows"
```

## Task 3: Add Extended Maestro Flows

**Files:**
- Create: `maestro/flows/smoke/extended/recommend_sheets.yaml`
- Create: `maestro/flows/smoke/extended/top_list_detail.yaml`
- Create: `maestro/flows/smoke/extended/plugin_management.yaml`
- Create: `maestro/flows/smoke/extended/player_queue.yaml`

- [ ] **Step 1: Create recommendation sheet flow**

Create `maestro/flows/smoke/extended/recommend_sheets.yaml`:

```yaml
appId: com.hank.musicfree.debug
name: smoke_extended_recommend_sheets
tags:
  - smoke
  - extended
  - recommend
---
- launchApp:
    stopApp: true
- tapOn:
    text: "允许|Allow|Allow notifications"
    optional: true
- extendedWaitUntil:
    visible:
      id: ".*home\\.operations\\.recommendSheets"
    timeout: 30000
- tapOn:
    id: ".*home\\.operations\\.recommendSheets"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*screen\\.recommendSheets\\.root"
    timeout: 15000
- extendedWaitUntil:
    visible:
      id: ".*recommendSheets\\.item"
    timeout: 60000
- tapOn:
    id: ".*recommendSheets\\.item"
    index: 0
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      text: ".*歌单|.*详情|播放全部|收藏|加载更多"
    timeout: 30000
- takeScreenshot: smoke_extended_recommend_sheets_detail
```

- [ ] **Step 2: Create top-list detail flow**

Create `maestro/flows/smoke/extended/top_list_detail.yaml`:

```yaml
appId: com.hank.musicfree.debug
name: smoke_extended_top_list_detail
tags:
  - smoke
  - extended
  - top-list
---
- launchApp:
    stopApp: true
- tapOn:
    text: "允许|Allow|Allow notifications"
    optional: true
- extendedWaitUntil:
    visible:
      id: ".*home\\.operations\\.topList"
    timeout: 30000
- tapOn:
    id: ".*home\\.operations\\.topList"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*screen\\.topList\\.root"
    timeout: 15000
- extendedWaitUntil:
    visible:
      id: ".*topList\\.item"
    timeout: 60000
- tapOn:
    id: ".*topList\\.item"
    index: 0
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      text: "播放全部|加载更多|暂无歌曲|.*失败.*"
    timeout: 30000
- takeScreenshot: smoke_extended_top_list_detail
```

- [ ] **Step 3: Create plugin-management flow**

Create `maestro/flows/smoke/extended/plugin_management.yaml`:

```yaml
appId: com.hank.musicfree.debug
name: smoke_extended_plugin_management
tags:
  - smoke
  - extended
  - plugin
---
- launchApp:
    stopApp: true
- tapOn:
    text: "允许|Allow|Allow notifications"
    optional: true
- extendedWaitUntil:
    visible:
      id: ".*home\\.navBar\\.menu"
    timeout: 30000
- tapOn:
    id: ".*home\\.navBar\\.menu"
    retryTapIfNoChange: true
- waitForAnimationToEnd:
    timeout: 5000
- extendedWaitUntil:
    visible:
      id: ".*home\\.drawer\\.settings\\.plugin"
    timeout: 15000
- tapOn:
    id: ".*home\\.drawer\\.settings\\.plugin"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*settings\\.pluginManagement\\.entry"
    timeout: 15000
- tapOn:
    id: ".*settings\\.pluginManagement\\.entry"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*screen\\.pluginList\\.root"
    timeout: 15000
- tapOn:
    text: "更多"
    retryTapIfNoChange: true
- assertVisible: "订阅设置"
- assertVisible: "排序"
- assertVisible: "卸载全部"
- back
- tapOn:
    text: "安装插件"
    retryTapIfNoChange: true
- assertVisible: "从本地安装"
- assertVisible: "从网络安装"
- assertVisible: "更新全部插件"
- assertVisible: "更新订阅"
- back
- takeScreenshot: smoke_extended_plugin_management
```

- [ ] **Step 4: Create player queue flow**

Create `maestro/flows/smoke/extended/player_queue.yaml`:

```yaml
appId: com.hank.musicfree.debug
name: smoke_extended_player_queue
tags:
  - smoke
  - extended
  - player
  - queue
---
- launchApp:
    stopApp: true
- tapOn:
    text: "允许|Allow|Allow notifications"
    optional: true
- extendedWaitUntil:
    visible:
      id: ".*player\\.mini\\.root"
    timeout: 15000
- tapOn:
    id: ".*player\\.mini\\.root"
    retryTapIfNoChange: true
- waitForAnimationToEnd:
    timeout: 5000
- assertVisible:
    text: "播放|暂停|列表循环|单曲循环|随机播放|歌词"
    optional: true
- back
- extendedWaitUntil:
    visible:
      id: ".*player\\.mini\\.queue"
    timeout: 15000
- tapOn:
    id: ".*player\\.mini\\.queue"
    retryTapIfNoChange: true
- extendedWaitUntil:
    visible:
      id: ".*player\\.queue\\.root"
    timeout: 15000
- takeScreenshot: smoke_extended_player_queue
```

This flow depends on a successful prior playback action. It should run after `02_search_play.yaml` in `--suite all`; when run alone, failure to find `player.mini.root` means there is no active playback state.

- [ ] **Step 5: Validate extended YAML files exist**

Run:

```bash
find maestro/flows/smoke/extended -maxdepth 1 -type f -name '*.yaml' | sort
```

Expected output:

```text
maestro/flows/smoke/extended/plugin_management.yaml
maestro/flows/smoke/extended/player_queue.yaml
maestro/flows/smoke/extended/recommend_sheets.yaml
maestro/flows/smoke/extended/top_list_detail.yaml
```

- [ ] **Step 6: Commit extended flow files**

```bash
git add maestro/flows/smoke/extended/recommend_sheets.yaml \
  maestro/flows/smoke/extended/top_list_detail.yaml \
  maestro/flows/smoke/extended/plugin_management.yaml \
  maestro/flows/smoke/extended/player_queue.yaml
git commit -m "test(maestro): 添加扩展 smoke flows"
```

## Task 4: Add Logcat-Aware Smoke Runner

**Files:**
- Create: `scripts/maestro/run-smoke.sh`

- [ ] **Step 1: Create runner script**

Create `scripts/maestro/run-smoke.sh`:

```bash
#!/usr/bin/env bash
set -u
set -o pipefail

APP_ID="com.hank.musicfree.debug"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
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

while [[ $# -gt 0 ]]; do
  case "$1" in
    --suite)
      SUITE="${2:-}"
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
      SERIAL="${2:-}"
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

  SERIAL="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
  if [[ -z "$SERIAL" ]]; then
    echo "No adb device in 'device' state." >&2
    adb devices >&2
    exit 1
  fi
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
  grep -E 'AndroidRuntime|MusicFree|MfLog|plugin_|search_|player_|feedback_' \
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
    FAILED_FLOWS+=("$flow")
    continue
  fi

  echo "Running $flow"
  adb_cmd logcat -c || true

  if maestro test \
    --test-output-dir="${flow_dir}/maestro-output" \
    --format junit \
    --output "${flow_dir}/junit.xml" \
    "$flow" > "${flow_dir}/maestro.log" 2>&1; then
    echo "PASS $flow" | tee "${flow_dir}/status.txt"
    PASSED_FLOWS+=("$flow")
  else
    echo "FAIL $flow" | tee "${flow_dir}/status.txt"
    FAILED=1
    FAILED_FLOWS+=("$flow")
  fi

  collect_logcat "$flow_dir"
done < <(flows_for_suite)

{
  echo "suite=$SUITE"
  echo "artifacts=$ARTIFACT_ROOT"
  echo "passed=${#PASSED_FLOWS[@]}"
  for flow in "${PASSED_FLOWS[@]}"; do
    echo "pass=$flow"
  done
  echo "failed=${#FAILED_FLOWS[@]}"
  for flow in "${FAILED_FLOWS[@]}"; do
    echo "fail=$flow"
  done
} > "${ARTIFACT_ROOT}/summary.txt"

cat "${ARTIFACT_ROOT}/summary.txt"
exit "$FAILED"
```

- [ ] **Step 2: Make script executable**

Run:

```bash
chmod +x scripts/maestro/run-smoke.sh
```

- [ ] **Step 3: Validate script syntax**

Run:

```bash
bash -n scripts/maestro/run-smoke.sh
```

Expected: no output and exit code `0`.

- [ ] **Step 4: Validate help output**

Run:

```bash
bash scripts/maestro/run-smoke.sh --help
```

Expected output includes:

```text
Usage: bash scripts/maestro/run-smoke.sh [options]
  --suite core|extended|all
  --skip-build
  --skip-install
  --clear-state
  --serial <adb-serial>
```

- [ ] **Step 5: Commit runner**

```bash
git add scripts/maestro/run-smoke.sh
git commit -m "test(maestro): 添加 smoke 运行脚本"
```

## Task 5: Add Acceptance Documentation

**Files:**
- Create: `docs/maestro-smoke-acceptance.md`

- [ ] **Step 1: Create acceptance guide**

Create `docs/maestro-smoke-acceptance.md`:

```markdown
# Maestro Smoke 验收指南

> 文档状态：当前规范
> 适用范围：Debug 包真实网络 smoke、默认插件/订阅链路、Maestro 运行证据与 logcat 判读
> 直接执行：是
> 当前入口：[DOCS_STATUS](./DOCS_STATUS.md)、[Maestro smoke 设计](./superpowers/specs/2026-05-13-maestro-real-network-smoke-design.md)
> 最后校验：2026-05-13

## 定位

这套 Maestro flow 是真实网络运行态 smoke，用来验证 Debug 包在设备或模拟器上的核心用户旅程，并把失败现场与 `logcat`、结构化日志和截图关联起来。

它不是 hermetic 自动化测试。默认插件、订阅源、DNS、证书、远端响应结构和网络质量都会影响结果。失败后必须结合日志判断，不应直接把所有失败归因于代码回归。

## 前置条件

1. 本机可执行 `adb`。
2. 本机已安装 Maestro CLI，可执行 `maestro`。
3. 至少一台设备或模拟器处于 `device` 状态。
4. 设备可访问默认插件和订阅源所在网络。
5. 当前分支可以构建 Debug APK。

确认设备：

```bash
adb devices
```

预期至少一行状态为 `device`。

## 执行命令

核心 smoke：

```bash
bash scripts/maestro/run-smoke.sh --suite core
```

扩展 smoke：

```bash
bash scripts/maestro/run-smoke.sh --suite extended
```

全部 smoke：

```bash
bash scripts/maestro/run-smoke.sh --suite all
```

跳过构建并复用已安装 APK：

```bash
bash scripts/maestro/run-smoke.sh --suite core --skip-build --skip-install
```

清空 app data 后运行：

```bash
bash scripts/maestro/run-smoke.sh --suite core --clear-state
```

指定设备：

```bash
bash scripts/maestro/run-smoke.sh --suite core --serial <adb-serial>
```

Debug 包名：

```text
com.hank.musicfree.debug
```

Debug APK 路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 证据目录

运行脚本会写入：

```text
build/maestro-smoke/<timestamp>/
  device.txt
  summary.txt
  <suite>/
    <flow-name>/
      status.txt
      maestro.log
      junit.xml
      logcat-full.txt
      logcat-filtered.txt
      maestro-output/
```

最终验收结论必须写明：

- 设备或模拟器名称。
- 执行的 suite。
- 通过和失败的 flow。
- 证据目录。
- 是否存在 `AndroidRuntime`。
- 关键结构化日志是否齐全。

## Flow 清单

### core/01_launch_default_plugins

目的：验证 Debug 包启动到首页不崩溃。

重点检查：

- 首页搜索入口可见。
- `logcat-filtered.txt` 无 `AndroidRuntime`。
- 日志包含 `app_start` 或 `main_activity_create_start`。

### core/02_search_play

目的：验证首页进入搜索、真实插件搜索、点击首个单曲并出现 mini player。

重点检查：

- `screen.search.root`、`search.input`、`search.result.musicRow` 可定位。
- 成功时出现 `player.mini.root`。
- 日志中能看到搜索、插件 API、音源解析或 player 事件。
- 搜索关键词使用 ASCII `jay`，避免 Maestro Android 非 ASCII 输入限制。

### core/03_settings_feedback_logs

目的：验证设置入口、基本设置滚动、反馈日志包隐私确认弹窗。

重点检查：

- 能从首页侧栏进入基本设置。
- “生成日志包并分享”可见。
- 弹窗文案包含“搜索词、请求地址、插件返回内容以及设备信息”。
- flow 点击“取消”，不进入系统分享面板。

### extended/recommend_sheets

目的：验证推荐歌单入口和真实插件推荐歌单列表加载。

重点检查：

- `screen.recommendSheets.root` 可见。
- 成功时 `recommendSheets.item` 可见并可进入详情。
- 失败时检查插件 API 和网络相关日志。

### extended/top_list_detail

目的：验证榜单入口、榜单列表和榜单详情链路。

重点检查：

- `screen.topList.root` 可见。
- 成功时 `topList.item` 可见并可进入详情。
- 失败时检查 `top_list_load_*`、`top_list_detail_load_*` 和插件 API 日志。

### extended/plugin_management

目的：验证从首页侧栏进入插件管理页，并确认入口菜单可见。

重点检查：

- `settings.pluginManagement.entry` 可见。
- `screen.pluginList.root` 可见。
- “订阅设置”“排序”“卸载全部”“从本地安装”“从网络安装”“更新全部插件”“更新订阅”可见。
- smoke 不执行卸载、更新和安装，避免污染状态。

### extended/player_queue

目的：在已有播放状态时验证 mini player、播放器页和队列入口。

前置条件：通常由 `core/02_search_play` 建立播放状态。单独运行可能因为没有当前播放而失败。

重点检查：

- `player.mini.root` 可见。
- `player.mini.queue` 可见。
- `player.queue.root` 可见。
- 日志中无 player 或 queue 相关崩溃。

## 日志判定

每个 flow 至少看三类证据：

1. Maestro 结果：`status.txt`、`maestro.log`、截图。
2. `logcat`：`logcat-full.txt` 与 `logcat-filtered.txt`。
3. 结构化日志：启动、默认插件、插件 API、搜索、播放、反馈日志包相关事件。

重点事件：

- 启动：`app_start`、`main_activity_create_start`、`edge_to_edge_enabled`
- 插件：`plugin_*`、`plugin_api_*`、`plugin_get_media_source_*`
- 搜索：`search_*`
- 首页详情：`recommend_*`、`top_list_load_*`、`top_list_detail_load_*`、`plugin_sheet_detail_*`
- 播放：`player_*`、`play_queue_*`
- 反馈：`feedback_package_*`、`feedback_logs_*`

## 失败分类

优先视为代码问题：

- `AndroidRuntime` 崩溃。
- 首页、搜索、设置等基础导航不可达。
- 反馈日志隐私确认弹窗缺失。
- 插件加载失败没有在 UI 或日志中体现。
- flow 成功但关键结构化日志完全缺失。

优先视为环境或远端问题：

- DNS、TLS、HTTP 超时。
- 默认插件远端文件无法访问。
- 远端插件返回结构变化。
- 搜索返回空结果。
- 设备网络不可用。

诊断结论用语：

- “运行态通过”：flow 通过，`logcat` 无 `AndroidRuntime`，关键日志齐全。
- “UI smoke 通过，日志验收不完整”：flow 通过，但关键结构化日志缺失。
- “运行态失败，诊断信息完整”：flow 失败，但日志清楚说明网络、插件或业务失败原因。
- “运行态失败，诊断信息不足”：flow 失败，且缺少可定位日志，需要补打点或补锚点。

## 常见问题

### 没有设备

现象：脚本输出 `No adb device in 'device' state.`

处理：

```bash
adb devices
```

确认设备不是 `unauthorized`、`offline` 或空列表。

### 未安装 Maestro

现象：脚本输出 `Required command not found: maestro`。

处理：安装 Maestro CLI 后重新运行。

### 通知权限弹窗阻塞

flow 已尝试点击 “允许|Allow|Allow notifications”。若厂商 ROM 文案不同，先在设备上手动允许通知权限，再使用 `--skip-install` 复跑。

### 搜索失败

先检查 `search_*`、`plugin_*`、`plugin_api_*`、`plugin_get_media_source_*`。若是网络或远端插件失败，结论写“运行态失败，诊断信息完整”。若没有任何搜索或插件日志，优先补日志或检查 UI 事件是否触发。

### 系统分享面板打开

`03_settings_feedback_logs.yaml` 只验证确认弹窗并点击“取消”。如果进入系统分享面板，说明 flow 或 UI 文案匹配错了，需要修正 flow，不要把分享面板纳入 smoke。
```

- [ ] **Step 2: Validate internal relative links**

Run:

```bash
python3 - <<'PY'
from pathlib import Path

path = Path("docs/maestro-smoke-acceptance.md")
text = path.read_text()
forbidden = [
    "/" + "Users" + "/",
    "file:" + "//",
    "../../../../",
]
hits = [item for item in forbidden if item in text]
if hits:
    raise SystemExit(f"{path} contains forbidden path marker(s): {hits}")
PY
```

Expected: no output and exit code `0`.

- [ ] **Step 3: Commit acceptance guide**

```bash
git add docs/maestro-smoke-acceptance.md
git commit -m "docs(maestro): 添加 smoke 验收指南"
```

## Task 6: Run Static And Runtime Verification

**Files:**
- No new files.
- Verify changed files from Tasks 1-5.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check HEAD~4..HEAD
```

Expected: no output.

- [ ] **Step 2: Run script syntax check**

Run:

```bash
bash -n scripts/maestro/run-smoke.sh
```

Expected: no output and exit code `0`.

- [ ] **Step 3: Run focused JVM tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*HomeAnchorContractTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run app debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run dev harness check**

Run:

```bash
bash scripts/dev-harness/check.sh
```

Expected: `BUILD SUCCESSFUL` or an existing documented failure unrelated to this branch. If it fails, copy the first failing task and error summary into the final implementation notes.

- [ ] **Step 6: Run core Maestro smoke if a device is available**

Run:

```bash
bash scripts/maestro/run-smoke.sh --suite core --skip-build
```

Expected when network and default plugins are healthy:

```text
passed=3
failed=0
```

If no device is available, record:

```text
运行态验收未执行：无可用 device 状态的设备或模拟器。
```

If Maestro is not installed, record:

```text
运行态验收未执行：本机未安装 maestro CLI。
```

If real network or plugin response fails, record the failing flow and evidence directory from `build/maestro-smoke/<timestamp>/summary.txt`.

- [ ] **Step 7: Run extended smoke if core passed and network is stable**

Run:

```bash
bash scripts/maestro/run-smoke.sh --suite extended --skip-build --skip-install
```

Expected when network, default plugins, and playback state are healthy enough for extended checks:

```text
failed=0
```

If `player_queue.yaml` fails when run as `extended` alone, rerun with:

```bash
bash scripts/maestro/run-smoke.sh --suite all --skip-build --skip-install
```

Then classify `player_queue.yaml` using the evidence directory and note whether `02_search_play.yaml` established `player.mini.root`.

- [ ] **Step 8: Final commit if runtime verification caused YAML adjustments**

If runtime verification required selector or timeout changes, commit them:

```bash
git add maestro/flows scripts/maestro/run-smoke.sh docs/maestro-smoke-acceptance.md
git commit -m "test(maestro): 调整 smoke 运行态选择器"
```

If no files changed after verification, do not create an empty commit.

## Self-Review Checklist

- Spec coverage:
  - Core flows: Task 2.
  - Extended flows: Task 3.
  - Runner script with logcat evidence: Task 4.
  - Acceptance documentation: Task 5.
  - UI anchors required to avoid coordinate taps: Task 1.
  - Static and runtime verification: Task 6.
- Placeholder scan:
  - No placeholder markers, deferred implementation notes, or vague “add broad coverage” steps.
  - All new files have complete contents in the task that creates them.
- Type and name consistency:
  - Debug package name is consistently `com.hank.musicfree.debug`.
  - New anchors match YAML selectors:
    - `search.result.musicRow`
    - `recommendSheets.item`
    - `topList.item`
    - `screen.pluginList.root`
  - Runner suite values are consistently `core`, `extended`, `all`.
