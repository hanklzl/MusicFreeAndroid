#!/usr/bin/env bash
# Usage: build-version-json.sh --version <semver> --version-code <int> --tag <vX.Y.Z>
#                              [--apk <path> | --sha256 <hex> --size <bytes>]
#                              --apk-name <filename> --notes <notes-md-file>
# Output: version.json on stdout
set -euo pipefail

VERSION="" VCODE="" TAG="" APK="" SHA="" SIZE="" APK_NAME="" NOTES=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --version)       VERSION=$2; shift 2 ;;
        --version-code)  VCODE=$2; shift 2 ;;
        --tag)           TAG=$2; shift 2 ;;
        --apk)           APK=$2; shift 2 ;;
        --sha256)        SHA=$2; shift 2 ;;
        --size)          SIZE=$2; shift 2 ;;
        --apk-name)      APK_NAME=$2; shift 2 ;;
        --notes)         NOTES=$2; shift 2 ;;
        *) echo "::error::unknown arg $1" >&2; exit 1 ;;
    esac
done

: "${VERSION:?version required}"
: "${VCODE:?version-code required}"
: "${TAG:?tag required}"
: "${APK_NAME:?apk-name required}"
: "${NOTES:?notes file required}"

if [[ -n "$APK" ]]; then
    if command -v sha256sum >/dev/null 2>&1; then
        SHA=$(sha256sum "$APK" | awk '{print $1}')
    else
        SHA=$(shasum -a 256 "$APK" | awk '{print $1}')
    fi
    SIZE=$(wc -c < "$APK")
fi
: "${SHA:?sha256 missing (provide --apk or --sha256)}"
: "${SIZE:?size missing (provide --apk or --size)}"

OWNER="hanklzl"
REPO="MusicFreeAndroid"
RELEASED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

DOWNLOAD_GH="https://github.com/$OWNER/$REPO/releases/download/$TAG/$APK_NAME"
DOWNLOAD_JSDELIVR="https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$TAG/release/$APK_NAME"
RELEASE_NOTES_URL="https://github.com/$OWNER/$REPO/releases/tag/$TAG"

# Extract changeLog lines: prefer the LLM summary block (everything between title and "### 变更详情")
# NB: do not pipe into `head -n N` — that closes the upstream pipe and trips SIGPIPE under set -o pipefail.
CHANGELOG_LINES=$(awk '
    /^## \[/ { inSummary = 1; next }
    /^### 变更详情/ { inSummary = 0 }
    inSummary { print }
' "$NOTES" | sed -E 's/^\s+|\s+$//g' | awk 'NF { print; if (++n >= 8) exit }')

if [[ -z "$CHANGELOG_LINES" ]]; then
    # fallback: take first 8 commit subject lines
    CHANGELOG_LINES=$(awk '/^- / {
        line = $0
        sub(/^- /, "", line)
        sub(/ \([a-f0-9]+\)$/, "", line)
        print line
        if (++n >= 8) exit
    }' "$NOTES")
fi

jq -n \
    --argjson schemaVersion 1 \
    --arg version "$VERSION" \
    --argjson versionCode "$VCODE" \
    --arg releasedAt "$RELEASED_AT" \
    --arg gh "$DOWNLOAD_GH" \
    --arg jd "$DOWNLOAD_JSDELIVR" \
    --argjson size "$SIZE" \
    --arg sha "$SHA" \
    --arg notes_url "$RELEASE_NOTES_URL" \
    --rawfile changelog <(printf '%s' "$CHANGELOG_LINES") \
    '
    {
        schemaVersion: $schemaVersion,
        version: $version,
        versionCode: $versionCode,
        releasedAt: $releasedAt,
        download: [$gh, $jd],
        size: $size,
        sha256: $sha,
        changeLog: ($changelog | split("\n") | map(select(length > 0))),
        releaseNotesUrl: $notes_url
    }'
