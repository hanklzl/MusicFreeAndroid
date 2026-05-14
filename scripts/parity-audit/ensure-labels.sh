#!/usr/bin/env bash
# scripts/parity-audit/ensure-labels.sh
# 幂等创建 references/labels.json 中所有 label。已存在的 label 静默跳过。

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
LABELS_JSON="$REPO_ROOT/.agents/skills/parity-audit-skill/references/labels.json"
[ -f "$LABELS_JSON" ] || { echo "ERROR: labels.json not found at $LABELS_JSON" >&2; exit 1; }

python3 - "$LABELS_JSON" <<'PY'
import json, subprocess, sys
data = json.load(open(sys.argv[1]))
for lbl in data["labels"]:
    name = lbl["name"]
    color = lbl["color"]
    desc = lbl.get("description", "")
    result = subprocess.run(
        ["gh", "label", "create", name, "--color", color, "--description", desc],
        capture_output=True, text=True,
    )
    if result.returncode == 0:
        print(f"created label: {name}")
    elif "already exists" in (result.stderr or "") or "already exists" in (result.stdout or ""):
        # 顺手把 color/description 拉到与 labels.json 一致
        edit = subprocess.run(
            ["gh", "label", "edit", name, "--color", color, "--description", desc],
            capture_output=True, text=True,
        )
        if edit.returncode == 0:
            print(f"updated label: {name}")
        else:
            print(f"could not update {name}: {edit.stderr.strip()}", file=sys.stderr)
    else:
        print(f"could not create {name}: {result.stderr.strip()}", file=sys.stderr)
        sys.exit(2)
PY
