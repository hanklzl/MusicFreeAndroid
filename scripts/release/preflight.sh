#!/usr/bin/env bash
# Usage: preflight.sh <vX.Y.Z>
# Local dry-run of CI release pipeline. Required to pass before pushing a real tag.
set -euo pipefail

TAG=${1:?"tag required, e.g. v1.2.3"}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT"

echo "[dry] Validate version consistency"
EXPECTED="${TAG#v}"
ACTUAL=$(awk -F= '/^versionName/{print $2}' version.properties | tr -d '[:space:]')
[[ "$EXPECTED" == "$ACTUAL" ]] || { echo "::error::tag $TAG vs versionName $ACTUAL mismatch"; exit 1; }
echo "OK: $TAG ↔ versionName=$ACTUAL"

echo "[dry] Build Release APK"
if [[ -z "${ANDROID_RELEASE_KEYSTORE_PATH:-}" ]]; then
    echo "::warning::ANDROID_RELEASE_KEYSTORE_PATH 未设置，跳过 Release 构建"
else
    ./gradlew clean :app:assembleRelease --no-daemon
    APK=app/build/outputs/apk/release/app-release.apk
    [[ -f "$APK" ]] || { echo "::error::APK not produced"; exit 1; }
fi

APK="${APK:-app/build/outputs/apk/release/app-release.apk}"
if [[ -f "$APK" ]]; then
    echo "[dry] Compute APK sha256 + size"
    if command -v sha256sum >/dev/null 2>&1; then
        SHA=$(sha256sum "$APK" | awk '{print $1}')
    else
        SHA=$(shasum -a 256 "$APK" | awk '{print $1}')
    fi
    echo "sha256=$SHA"
    echo "size=$(wc -c < "$APK")"
fi

echo "[dry] Generate release notes"
PREV=$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD | tail -1)
bash scripts/release/generate-notes.sh "$PREV" HEAD > /tmp/preflight-notes.md
echo "Wrote /tmp/preflight-notes.md"

echo "[dry] Prepend CHANGELOG.md (dry-run)"
bash scripts/release/prepend-changelog.sh /tmp/preflight-notes.md "$TAG" --dry-run \
    | diff CHANGELOG.md - || true

if [[ -f "$APK" ]]; then
    echo "[dry] Build version.json"
    VCODE=$(awk -F= '/^versionCode/{print $2}' version.properties | tr -d '[:space:]')
    bash scripts/release/build-version-json.sh \
        --version "$EXPECTED" \
        --version-code "$VCODE" \
        --tag "$TAG" \
        --apk "$APK" \
        --apk-name "MusicFreeAndroid-$TAG.apk" \
        --notes /tmp/preflight-notes.md \
        > /tmp/preflight-version.json
    jq . /tmp/preflight-version.json > /dev/null
    echo "Wrote /tmp/preflight-version.json"
fi

echo "Preflight OK"
