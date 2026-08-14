---
name: musicfreeandroid-issue-automation
description: Use for hanklzl/MusicFreeAndroid issue polling or issue-fix automation when you need to check open issues, respect /confirm-issue-N approval gates, fall back from the GitHub connector to gh, and update automation memory.
---

# MusicFreeAndroid issue automation

Use this when:
1. The task is a GitHub issue polling, triage, or fix-delivery run for `hanklzl/MusicFreeAndroid`.
2. You need to decide whether to stop at triage, post a plan, or implement after explicit issue-thread confirmation.
3. The workflow depends on the central issue history, `gh issue list`, `gh issue view`, or GitHub connector checks.

Do not use this when:
1. The task is a normal repo feature request that did not originate from issue automation.
2. The user already asked for a direct local code change with no GitHub issue workflow.
3. You cannot access the repo or the issue state and the task is purely conceptual.

## Inputs / context to gather

1. Resolve the repo and checkout state.
   - `git remote -v`
   - `git status --short --branch`
2. Read the durable automation memory first.
   - `/Users/zili/code/agent_data/knowledge/projects/musicfreeandroid/issue-history.md`
3. Read the repo entry docs before edits.
   - `docs/DOCS_STATUS.md`
   - `AGENTS.md`
   - `docs/dev-harness/INDEX.md`
4. Identify the live open issues.
   - Prefer GitHub connector plus `gh issue list`
   - If connector fails, switch immediately to `gh`
5. If an issue already has a proposed plan comment, record the exact confirmation token and the exclusion anchor.
   - Example: `/confirm-issue-8`
   - Exclude the original plan comment id/time from approval checks

## Procedure

1. Start with de-duplication.
   - Read `/Users/zili/code/agent_data/knowledge/projects/musicfreeandroid/issue-history.md`.
   - Identify issues already handled, waiting for confirmation, or previously closed.
2. Query open issues.
   - Try the GitHub connector first if available.
   - Cross-check with `gh issue list --repo hanklzl/MusicFreeAndroid --state open ...`.
   - If needed, use `gh issue list --state all` to confirm whether a recent issue is now `CLOSED` / `COMPLETED`.
   - If label-based routing matters, use `gh label list --repo hanklzl/MusicFreeAndroid --limit 200` to confirm the current label vocabulary before assuming `feature-request` exists.
   - If you use `gh search issues`, remember it only accepts `--state open|closed` and uses `commentsCount` rather than `comments`.
3. If there are no actionable open issues:
   - Record the no-open-issues result in automation memory.
   - Stop without code changes, merge, or push.
4. If an issue is already fixed in `main` but still open on GitHub:
   - Verify the fix/merge/push evidence from issue comments plus the real main checkout.
   - Close the issue as `completed`.
   - Re-run `gh issue list --state open` and record the result in automation memory.
5. For each open issue, classify before editing.
   - Already handled and still closed: stop.
   - Waiting for confirmation: fetch comments and look for the exact `/confirm-issue-N` reply from `hanklzl`.
   - Missing confirmation: stop at triage and do not duplicate the prior analysis reply.
   - Confirmed by the user: move into implementation.
6. If the issue requires feedback-zip or Logan analysis before a plan:
   - Inspect the archive contents first for `manifest.json`, `README-decode.md`, `logan/*`, and `logan/readable-errors.log`.
   - If `logan/readable-errors.log` is present, do not feed the whole directory into the Logan decoder; isolate numeric Logan files first.
   - Use `tools/logan/decode-logan.sh` plus keys obtained from the repo's current authoritative implementation when needed; never copy key values into this knowledge repository. Inspect a narrow event window instead of trusting broad grep output.
7. If implementation is approved:
   - Verify the checkout/worktree is suitable for edits.
   - Reproduce or inspect the bug on current code.
   - Implement the narrow fix.
   - Run targeted verification first, then repo guards such as `bash scripts/dev-harness/check.sh` or `python3 scripts/dev-harness/grep-check.py` when relevant, then `./gradlew :app:assembleDebug --no-daemon`.
8. Finish the issue loop.
   - Merge or squash-merge back to local `main` if the workflow requires it.
   - Push if the workflow/user instruction requires it.
   - Reply on the issue with the fix summary and verification.
   - Update `/Users/zili/code/agent_data/knowledge/projects/musicfreeandroid/issue-history.md` with the new status.
   - If you need a timestamp for the memory append on this shell, use `date '+%Y-%m-%dT%H:%M:%S%z'`.
9. If the user explicitly flips issue state later:
   - Treat the latest instruction as authoritative.
   - Update the GitHub issue state and the automation memory together.

## Efficiency plan

1. Read the central issue history before any repo or issue deep dive.
2. Use `gh issue list` and `gh issue view` as the default fallback instead of debugging a broken connector.
3. Only open the issue that is currently actionable; do not inspect old closed issues unless memory/state conflicts.
4. If search or connector results look stale, trust realtime `gh issue list --state open` as the final open-queue check.
5. Stop immediately when the confirmation gate is missing.
6. For feedback zips, inspect archive structure before decoding and keep only numeric Logan files for the decoder path.
7. Keep verification targeted to the touched area before any broader harness gate.
8. Keep `--repo hanklzl/MusicFreeAndroid` separate from the free-text query when using `gh search issues`; malformed combined queries can fail with `Invalid search query`.

## Pitfalls and fixes

1. Symptom: automation reprocesses a finished issue.
   - Cause: skipped automation memory or live issue-state check.
   - Fix: read `/Users/zili/code/agent_data/knowledge/projects/musicfreeandroid/issue-history.md` first, then fetch current issue state.
2. Symptom: approval self-confirms from the plan comment.
   - Cause: token search ignored comment author/time.
   - Fix: require a later user-authored confirmation comment and exclude the original plan comment id/time.
3. Symptom: connector-backed issue queries fail.
   - Cause: connector startup/auth failure.
   - Fix: fall back immediately to `gh issue list` and `gh issue view`.
4. Symptom: `gh issue list` errors with `unknown flag: --order`.
   - Cause: unsupported flag.
   - Fix: remove `--order`; sort outside the command only if needed.
5. Symptom: code changes start while the issue is still waiting for `/confirm-issue-N`.
   - Cause: confirmation gate treated as optional.
   - Fix: stop at triage and leave the prior plan reply as the latest action.
6. Symptom: GitHub still shows an already-fixed issue as open.
   - Cause: stale search/index state or the close step never happened.
   - Fix: verify the fix on the real main checkout, close the issue as `completed`, then re-run `gh issue list --state open`.
7. Symptom: Logan decoding fails with EOF or invalid-block noise.
   - Cause: the feedback zip mixed encrypted numeric Logan files with plain `logan/readable-errors.log`.
   - Fix: decode only the numeric files, then inspect the precise event window that matches the issue timeline.
8. Symptom: timestamp generation fails with `date -Is`.
   - Cause: this shell does not support that flag combination.
   - Fix: use `date '+%Y-%m-%dT%H:%M:%S%z'` for automation-memory entries instead.
9. Symptom: `gh search issues --state all` fails.
   - Cause: `gh search issues` only accepts `open|closed`.
   - Fix: split the search by state or switch to `gh issue list --state all` plus targeted `gh issue view`.
10. Symptom: `gh search issues` fails with `Invalid search query`.
   - Cause: the query text was composed so the CLI parsed it like a repo qualifier.
   - Fix: pass `--repo` separately and keep only actual search qualifiers in the query string.

## Verification checklist

1. Automation memory was read before issue decisions.
2. Live issue state was checked with GitHub connector or `gh`.
3. If no issue was actionable, the run stopped without edits.
4. If confirmation was required, the approving comment was user-authored and newer than the plan comment.
5. If code changed, targeted tests plus repo guard/build commands passed.
6. GitHub issue reply/state and automation memory agree on the final status.
7. If a feedback zip was analyzed, the decoder input excluded plain `readable-errors.log` and the root-cause summary cites the concrete event sequence used as evidence.

## Minimal command example

```bash
gh issue list --repo hanklzl/MusicFreeAndroid --state open --limit 50 --json number,title,updatedAt,url
gh issue view 8 --repo hanklzl/MusicFreeAndroid --comments --json number,title,state,comments,updatedAt
gh search issues --repo hanklzl/MusicFreeAndroid --state open 'updated:>=2026-07-10' --json number,title,updatedAt,commentsCount,url
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```
