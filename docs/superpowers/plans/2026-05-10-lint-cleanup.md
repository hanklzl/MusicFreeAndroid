# Lint Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run repository lint in an isolated git worktree and repair lint findings without polluting the main worktree.

**Architecture:** The implementation is a data-driven cleanup workflow. Create a dedicated worktree from current `main`, collect the full `./gradlew lint` report, triage findings by severity and risk, make minimal edits only to files named by the report, then rerun lint until it passes.

**Tech Stack:** Gradle Wrapper 9.4.1, Android Gradle Plugin 9.2.0, Kotlin 2.3.21, Android lint, git worktree.

---

## File Structure

The exact source files to modify are determined by the first lint report generated in Task 2. This plan intentionally avoids preselecting app files because doing so would guess before lint has identified the failing rules.

- Create or reuse: `.worktrees/fix-lint-issues`
  - Dedicated worktree for branch `fix/lint-issues`.
- Read: `docs/superpowers/specs/2026-05-10-lint-cleanup-design.md`
  - Approved scope and acceptance criteria.
- Read: generated `*/build/reports/lint-results*.xml`, `*/build/reports/lint-results*.txt`, or `*/build/reports/lint-results*.html`
  - Source of truth for lint issue ids, severities, file paths, and line numbers.
- Modify: only files named by lint reports, plus narrowly related files needed to keep the reported fix compiling.
  - Do not edit generated files under `build/`.
  - Do not edit vendored/minified assets unless lint identifies a checked-in source issue and no narrower project-side fix exists.
- Optional modify: `docs/superpowers/plans/2026-05-10-lint-cleanup.md`
  - Only to mark completed checkboxes while executing this plan.

## Task 1: Create Isolated Worktree

**Files:**
- Verify: `.gitignore`
- Create or reuse: `.worktrees/fix-lint-issues`

- [ ] **Step 1: Confirm `.worktrees/` is ignored**

Run from repository root:

```bash
git check-ignore -q .worktrees/ && echo ".worktrees ignored"
```

Expected: prints `.worktrees ignored`.

- [ ] **Step 2: Check whether the target worktree path already exists**

Run:

```bash
test -e .worktrees/fix-lint-issues && git -C .worktrees/fix-lint-issues status --short --branch || echo "target worktree path is available"
```

Expected when no previous worktree exists: prints `target worktree path is available`.

If the path exists and shows branch `fix/lint-issues` with no unrelated work, reuse it. If it exists for another branch or has unrelated changes, stop and choose a new path such as `.worktrees/fix-lint-issues-2`.

- [ ] **Step 3: Create the worktree and branch**

Run:

```bash
git worktree add -b fix/lint-issues .worktrees/fix-lint-issues HEAD
```

Expected: git creates `.worktrees/fix-lint-issues` and checks out branch `fix/lint-issues`.

- [ ] **Step 4: Confirm the worktree is clean**

Run:

```bash
git -C .worktrees/fix-lint-issues status --short --branch
```

Expected: output starts with `## fix/lint-issues` and has no modified files.

## Task 2: Run Baseline Lint And Collect Reports

**Files:**
- Read: `.worktrees/fix-lint-issues/*/build/reports/lint-results*`
- Read: `.worktrees/fix-lint-issues/build/reports/lint-results*`

- [ ] **Step 1: Run full repository lint**

Run from the worktree:

```bash
cd .worktrees/fix-lint-issues
./gradlew lint
```

Expected: either PASS, or FAIL with Android lint output and generated report paths.

- [ ] **Step 2: List generated lint reports**

Run from the worktree:

```bash
rg --files . | rg '/build/reports/lint-results.*\.(xml|txt|html)$'
```

Expected: one or more lint report files, usually module-scoped paths such as `app/build/reports/lint-results-debug.xml`.

- [ ] **Step 3: Extract issue ids, severities, and file references from XML reports**

Run from the worktree:

```bash
for report in $(rg --files . | rg '/build/reports/lint-results.*\.xml$'); do
  echo "REPORT $report"
  rg -n '<issue|severity=|id=|file=|line=' "$report"
done
```

Expected: enough structured output to identify failing lint issue ids and source file locations.

- [ ] **Step 4: Triage the baseline**

Create a short working note in the conversation with these groups:

```text
Baseline lint triage
- error/fatal:
- low-risk warning:
- deferred warning:
```

Expected: each lint finding is assigned to one group before code edits begin. If `./gradlew lint` passes with no findings, skip to Task 5.

## Task 3: Repair Blocking Lint Findings

**Files:**
- Modify: only source, resource, manifest, Gradle, or test files named by Task 2 lint reports.
- Do not modify: files under any `build/` directory.

- [ ] **Step 1: Open each error/fatal source location**

For each error/fatal reported in Task 2, open a narrow range around the reported line. For example, if lint reports line 40 in `app/src/main/AndroidManifest.xml`, run:

```bash
sed -n '30,55p' app/src/main/AndroidManifest.xml
```

Expected: local context for the exact reported issue.

- [ ] **Step 2: Apply the smallest valid fix for each blocking issue**

Use `apply_patch` for manual edits. Choose the fix shape by lint category:

```text
Missing resource metadata: add the specific required resource attribute or string resource.
Wrong API usage: guard by SDK version or replace with the recommended compatible API.
Manifest issue: correct only the reported manifest node or attribute.
Compose or Kotlin issue: adjust the reported call site without restructuring unrelated code.
False positive: use the narrowest local @SuppressLint or tools:ignore with a reason in nearby code when the report cannot be fixed honestly.
```

Expected: each blocking lint issue has a code-level fix or a narrowly justified local suppression.

- [ ] **Step 3: Do not add a lint baseline**

Run from the worktree:

```bash
git diff --name-only | rg 'lint-baseline|baseline.*xml' || true
```

Expected: no new lint baseline file appears unless the conversation explicitly approved one after a proven false-positive analysis.

- [ ] **Step 4: Review the diff for unrelated churn**

Run:

```bash
git diff --stat
git diff --check
```

Expected: changed files match lint report scope, and `git diff --check` reports no whitespace errors.

## Task 4: Decide Warning Scope

**Files:**
- Modify: only files named by Task 2 warning reports if they are selected for this round.

- [ ] **Step 1: Count remaining warnings after blocking fixes**

Run from the worktree:

```bash
./gradlew lint
```

Expected: either PASS or lint output listing remaining warnings.

- [ ] **Step 2: If lint passes, do not expand scope automatically**

If the command passes and remaining warnings do not block lint, proceed to Task 5. Do not chase non-blocking warnings unless they are few, local, and clearly safe.

- [ ] **Step 3: If lint still fails on warnings-as-errors, fix the failing warning rules**

Use the same source-location workflow from Task 3:

```bash
rg --files . | rg '/build/reports/lint-results.*\.xml$'
```

Expected: all warnings that are configured as build-blocking are repaired or narrowly suppressed.

## Task 5: Verification

**Files:**
- Read: final lint reports under `.worktrees/fix-lint-issues`
- Read: `git diff --stat`

- [ ] **Step 1: Run final lint**

Run from the worktree:

```bash
./gradlew lint
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run compile verification if production Kotlin, Gradle, manifest, or resource files changed**

Run from the worktree when applicable:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

Skip this step only if the final diff is limited to documentation or lint-only metadata that does not affect build inputs.

- [ ] **Step 3: Check final repository status**

Run from the worktree:

```bash
git status --short
git diff --stat
```

Expected: only intentional lint-fix files are modified.

## Task 6: Commit And Report

**Files:**
- Modify: none beyond already fixed files.

- [ ] **Step 1: Commit lint fixes on the worktree branch**

Run from the worktree:

```bash
git add -A
git commit -m "fix: resolve lint issues"
```

Expected: one commit on branch `fix/lint-issues`.

- [ ] **Step 2: Capture final summary**

Prepare final response with:

```text
Worktree: .worktrees/fix-lint-issues
Branch: fix/lint-issues
Initial lint result:
Fixed files:
Verification:
Residual warnings or risks:
Commit:
```

Expected: user can see exactly what changed, what passed, and what remains.
