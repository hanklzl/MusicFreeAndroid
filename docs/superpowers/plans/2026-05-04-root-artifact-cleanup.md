# Root Artifact Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove three unused top-level historical artifact directories from the Android repository.

**Architecture:** This is a repository hygiene change. It does not alter build configuration or application code; it removes directories that are outside all Gradle source sets and current evidence directories.

**Tech Stack:** Git worktree, Gradle Wrapper, Android Gradle Plugin, Kotlin/Compose modules.

---

## File Structure

- Delete: `commonMain/`
- Delete: `androidMain/`
- Delete: `screenshots/`
- Create: `docs/superpowers/specs/2026-05-04-root-artifact-cleanup-design.md`
- Create: `docs/superpowers/plans/2026-05-04-root-artifact-cleanup.md`

### Task 1: Confirm Baseline

- [x] **Step 1: Confirm worktree branch**

Run: `git status --short --branch`

Expected: branch is `cleanup-root-artifacts`; no unrelated working tree changes.

- [x] **Step 2: Run baseline build**

Run: `./gradlew :app:build`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Remove Top-Level Artifacts

- [ ] **Step 1: Remove the approved directories**

Run: `git rm -r commonMain androidMain screenshots`

Expected: Git stages deletions only under `commonMain/`, `androidMain/`, and `screenshots/`.

- [ ] **Step 2: Confirm deletion scope**

Run: `git status --short`

Expected: deleted entries under `commonMain/`, `androidMain/`, and `screenshots/`, plus this cleanup's docs.

### Task 3: Verify Cleanup

- [ ] **Step 1: Run post-cleanup build**

Run: `./gradlew :app:build`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Confirm target directories no longer exist**

Run: `find commonMain androidMain screenshots -maxdepth 0 -type d -print`

Expected: command reports those paths are missing.

- [ ] **Step 3: Review diff**

Run: `git diff --stat --staged`

Expected: staged diff contains only the three directory deletions and the cleanup docs.

### Task 4: Commit

- [ ] **Step 1: Commit design and plan**

Run: `git add docs/superpowers/specs/2026-05-04-root-artifact-cleanup-design.md docs/superpowers/plans/2026-05-04-root-artifact-cleanup.md`

Run: `git commit -m "docs: plan root artifact cleanup"`

Expected: commit succeeds.

- [ ] **Step 2: Commit cleanup**

Run: `git add -u commonMain androidMain screenshots`

Run: `git commit -m "chore: remove unused root artifacts"`

Expected: commit succeeds.
