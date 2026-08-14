---
name: musicfreeandroid-worktree-feature-flow
description: Use for MusicFreeAndroid feature or parity work when the user asks for git worktree development, spec-first execution, direct spec+plan+implement delivery, or merge-back to main.
---

# MusicFreeAndroid worktree feature flow

Use this when:
1. The cwd is `/Users/zili/code/android/MusicFreeAndroid`.
2. The user asks for `git worktree`, `从spec开始`, `spec + plan + implement直接执行`, `使用sub-agent-driven-development`, or `合并回main`.
3. The task is a feature, parity pass, UI fix, or repo debugging change that should be isolated from the main checkout.
4. The user wants reviewed docs merged first and then a fresh-session handoff from the existing worktree.

Do not use this when:
1. The user explicitly asks to work in the main checkout.
2. The task is a trivial read-only question or a one-command inspection.
3. The repo state makes branch/worktree edits impossible and the user has not asked for a repo change.

## Inputs / context to gather

1. Confirm the repo root and branch:
   - `pwd`
   - `git status --short --branch`
   - `git worktree list`
2. Confirm the repo rules and current docs entrypoint:
   - `docs/DOCS_STATUS.md`
   - `AGENTS.md`
   - area-specific `docs/dev-harness/...` rules for the touched surface
3. Confirm `.worktrees/` is ignored before creating a new worktree:
   - `git check-ignore -q .worktrees && echo ".worktrees ignored"`
4. If the task is RN parity, confirm the RN reference path:
   - project-root relative: `../MusicFree`
   - nested worktree shells may need `../../../MusicFree/...`
5. Record the sub-agent expectation up front.
   - If the user asked for subagents or `sub-agent-driven-development`, select capability and reasoning effort based on task complexity and current availability.
   - If the user specified a concrete model, honor it explicitly.
   - If the user asks for `实现、测试、提交、自审，再做 spec compliance review 和 code quality review`, keep that exact multi-stage loop instead of collapsing it into a single implementation pass.
6. If the user wants docs reviewed on `main` before implementation, record the reviewed commit SHA and the exact worktree branch that should inherit it.

## Procedure

1. Decide whether this is a new worktree task or an existing worktree continuation.
   - New task: create `.worktrees/<branch-name>` before writing specs/plans.
   - Existing branch: inspect divergence from `main` or `origin/main` first.
   - If the user says `请继续`, resume the current worktree and verification state instead of rebuilding the task plan from scratch.
   - If the user says they want a fresh session to `清除旧记忆`, stop extending the old thread and prepare a minimal copy-paste handoff instead.
   - If subagents are in scope, choose worker capability deliberately before dispatch so the review/spec worker is neither underpowered nor wasteful.
2. For a new task, create the worktree:
   - `git worktree add .worktrees/<branch-name> -b <branch-name>`
3. Read the relevant docs before editing.
   - Always start with `docs/DOCS_STATUS.md` and `AGENTS.md`.
   - Add UI/player/plugin/test harness docs only for the touched surface.
4. If the user asked for spec/plan flow, write the spec and plan inside the worktree, not the main checkout.
5. If the user asked for RN parity, compare Android and RN before editing.
   - Search Android implementation first.
   - Then inspect the matching RN page/component in `../MusicFree`.
6. Implement inside the worktree.
   - Use absolute paths or worktree-prefixed paths for patches.
   - After every patch batch, verify the write target with:
     - `git -C /Users/zili/code/android/MusicFreeAndroid status --short`
     - `git -C /Users/zili/code/android/MusicFreeAndroid/.worktrees/<branch-name> status --short`
   - If the user requested Subagent-Driven execution plus staged reviews, keep the sequence: implement -> targeted tests -> commit/self-review -> spec compliance review -> code quality review.
7. Run targeted verification in the worktree.
   - Prefer touched-module tests first.
   - Then `python3 scripts/dev-harness/grep-check.py` when docs/UI harness contracts changed.
   - Then `git diff --check`.
   - Then `./gradlew :app:assembleDebug --no-daemon` if production code changed.
   - Do not default to `:app:build` or other Release-flavored finish gates unless the task explicitly involves release/signing validation.
8. If the user asked for merge-back, integrate to `main`.
   - Check both worktree and main status before merging.
   - Clean only rollout-attributable leftovers in `main`.
   - If `main` already has unrelated local dirt, stash only those paths before the squash merge and restore them after the merge commit lands.
   - Use the merge style that matches the task:
     - ordinary merge: `git merge <branch>`
     - explicit merge commit: `git merge --no-ff <branch> -m "..."`
     - squash merge when the user/repo flow calls for it
   - If the user asked to merge a reviewed `main` commit into the worktree, align the targeted docs/files to that reviewed commit and confirm with `git diff <reviewed-commit>..HEAD`.
9. Re-run or skip verification on merged `main` according to tree equality.
   - Before squash merge cleanup, save the verified worktree tree:
     `branch_tree="$(git rev-parse 'HEAD^{tree}')"`
   - After squash merge and commit on `main`, compute:
     `main_tree="$(git rev-parse 'HEAD^{tree}')"`
   - If `main_tree == branch_tree`, and the worktree already passed targeted tests, `bash scripts/dev-harness/check.sh`, and `./gradlew :app:assembleDebug --no-daemon`, skip duplicate `main` verification.
   - If the tree differs, merge involved manual conflict resolution, `main` added extra tracked content, or worktree verification was incomplete, run the relevant post-merge verification on `main`.
   - For ordinary feature completion, keep any required post-merge gate Debug-only unless the user explicitly asked for Release or signing checks.
   - For docs-only merge + handoff tasks, do not start Gradle unless the user explicitly asked for verification.
10. Clean up the worktree and branch.
   - `git worktree remove .worktrees/<branch-name>`
   - `git worktree prune`
   - `git branch -d <branch-name>`
   - After squash merge, if `git branch -d` refuses deletion, leave the branch in place by default; use `git branch -D <branch-name>` only if the user explicitly asked for force deletion and the content is already on `main`.

## Efficiency plan

1. Read only the docs for the touched area after `docs/DOCS_STATUS.md` / `AGENTS.md`.
2. Use `git worktree list`, `git status --short --branch`, and `git rev-list --left-right --count HEAD...origin/main` as the fast routing checks before deeper git inspection.
3. If the user requested RN parity, compare only the matching Android surface and the RN counterpart first; do not sweep the whole app.
4. Run targeted module tests before any full-suite command.
5. If main checkout dirtiness blocks confidence, use a detached verification worktree instead of spending time reasoning through unrelated local files.
6. If a broad harness/KSP failure points at symbols that still exist in source, rerun the normal dependency chain before editing code; the repo has recent evidence of incremental-state noise.
7. If the user wants a fresh-session continuation, provide only the worktree `cd` command, the startup prompt, and the minimal verification pair instead of a long recap.

## Pitfalls and fixes

1. Symptom: patch lands in the main checkout.
   - Cause: `apply_patch` has no `workdir`.
   - Fix: use absolute/worktree-prefixed paths and verify `git status` in both locations after patching.
2. Symptom: merge to `main` mixes user changes with rollout leftovers.
   - Cause: main checkout already has local dirt.
   - Fix: clean only clearly attributable leftovers before merge; leave unrelated user changes alone.
3. Symptom: branch is far behind `main` and conflicts everywhere.
   - Cause: long-lived worktree drift.
   - Fix: sync `main` or `origin/main` before deeper implementation; expect semantic conflicts in cross-cutting files.
4. Symptom: `git branch -d` refuses deletion after squash merge.
   - Cause: squash merge does not mark topology as merged.
   - Fix: verify content on `main`, remove the worktree, and leave the branch unless the user explicitly asked for force deletion.
5. Symptom: post-merge verification fails for a new file not touched in the branch.
   - Cause: `main` advanced and introduced a new blocker.
   - Fix: treat it as merge-time fallout and rerun the relevant gate after fixing the new blocker.
6. Symptom: Gradle or harness check reports a plugin/module issue that does not match the current file contents.
   - Cause: stale daemon/cache state.
   - Fix: inspect the file first, run `./gradlew --stop`, then rerun the exact command before editing code.
7. Symptom: extending a runtime/settings interface suddenly breaks unrelated plugin or test code.
   - Cause: fake/test-double drift after an interface method was added.
   - Fix: update every fake immediately before debugging the feature logic any further.
8. Symptom: a docs handoff kicks off Gradle even though the user only wanted merge + fresh-session bootstrap.
   - Cause: reusing the default feature verification flow.
   - Fix: skip Gradle for docs-only handoff tasks unless the user explicitly requests validation.
9. Symptom: the next agent restarts brainstorming instead of executing the approved plan.
   - Cause: the handoff prompt did not explicitly anchor the plan file and starting task.
   - Fix: tell the new session to stay in the current worktree, not create a new one, not redo brainstorming, and start from Task 1 of the saved plan.

## Verification checklist

1. The feature/spec/plan files are in the worktree, not the main checkout.
2. `git status --short` in the main checkout does not contain accidental edits from the worktree.
3. The touched module tests passed in the worktree.
4. `python3 scripts/dev-harness/grep-check.py` passed when docs or harness-sensitive UI changed.
5. `git diff --check` passed before merge.
6. Either the merged `main` tree matched the verified worktree tree and duplicate `main` verification was intentionally skipped, or the relevant post-merge verification passed on `main`.
7. The worktree was removed and the branch was deleted or intentionally preserved with a stated reason.
8. The finish command did not fail only because a Release-signing env var such as `ANDROID_RELEASE_KEYSTORE_PATH` was unnecessarily pulled into scope.
9. If this was a reviewed-docs handoff, `git diff <reviewed-commit>..HEAD` is empty and the startup prompt tells the next session not to redo brainstorming or create a new worktree.

## Minimal command example

```bash
git check-ignore -q .worktrees && echo ".worktrees ignored"
git worktree add .worktrees/feat-example -b feat-example
git -C .worktrees/feat-example status --short --branch
./gradlew :feature:settings:testDebugUnitTest --no-daemon
python3 scripts/dev-harness/grep-check.py
git diff --check
```
