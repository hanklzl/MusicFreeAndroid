# Release APK Signing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build signed side-loadable Release APKs in GitHub Actions without storing release signing material in the source repository.

**Architecture:** `:app` reads release signing values from `ANDROID_RELEASE_*` environment variables only when a release build is requested. A new GitHub Actions workflow decodes the Environment secret keystore into `$RUNNER_TEMP`, builds `:app:assembleRelease`, uploads manual builds as short-lived artifacts, and publishes `v*` tag builds to GitHub Releases.

**Tech Stack:** Gradle Kotlin DSL, Android Gradle Plugin 9.2.0, GitHub Actions, GitHub Environment secrets, GitHub CLI, JDK 21.

---

## File Structure

- Modify `app/build.gradle.kts`: add release signing environment validation and attach signingConfig to the existing `release` build type.
- Create `.github/workflows/android-release-apk.yml`: build signed Release APKs from GitHub `release` Environment secrets; upload manual artifacts and publish tag builds.
- No production Kotlin files change.
- No repository file may contain `.jks`, base64 keystore content, keystore passwords, or `keystore.properties`.

## Pre-Implementation Setup

Implementation should happen in a dedicated worktree so the main workspace stays clean.

- [ ] **Step 1: Confirm `.worktrees/` is ignored**

Run:

```bash
git check-ignore .worktrees/release-apk-signing
```

Expected: prints `.worktrees/release-apk-signing`. If it prints nothing, stop and add `.worktrees/` to `.gitignore` before creating the worktree.

- [ ] **Step 2: Create implementation worktree**

Run:

```bash
git worktree add .worktrees/release-apk-signing -b feat/release-apk-signing
```

Expected: worktree is created at `.worktrees/release-apk-signing` on branch `feat/release-apk-signing`.

- [ ] **Step 3: Switch commands into the worktree**

Run all implementation commands below from:

```bash
cd .worktrees/release-apk-signing
```

Expected: `pwd` ends with `.worktrees/release-apk-signing`.

### Task 1: Add Gradle Release Signing Config

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Characterize current release build behavior**

Run:

```bash
env -u ANDROID_RELEASE_KEYSTORE_PATH \
  -u ANDROID_RELEASE_STORE_PASSWORD \
  -u ANDROID_RELEASE_KEY_ALIAS \
  -u ANDROID_RELEASE_KEY_PASSWORD \
  ./gradlew :app:assembleRelease --no-daemon
```

Expected before this task is implemented: the output does not contain `Missing release signing environment variable`. This confirms the new validation message is introduced by this task.

- [ ] **Step 2: Add release signing helpers near the top of `app/build.gradle.kts`**

Insert this code after the `plugins { ... }` block and before `android { ... }`:

```kotlin
val releaseSigningEnvironmentVariables = listOf(
    "ANDROID_RELEASE_KEYSTORE_PATH",
    "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS",
    "ANDROID_RELEASE_KEY_PASSWORD",
)

val releaseSigningRequested = gradle.startParameter.taskNames.any { taskName ->
    val normalizedTaskName = taskName.substringAfterLast(':')
    normalizedTaskName.equals("assembleRelease", ignoreCase = true) ||
        normalizedTaskName.equals("bundleRelease", ignoreCase = true) ||
        normalizedTaskName.equals("packageRelease", ignoreCase = true) ||
        normalizedTaskName.equals("build", ignoreCase = true) ||
        normalizedTaskName.endsWith("Release", ignoreCase = true)
}

fun requiredReleaseSigningEnv(name: String): String =
    providers.environmentVariable(name).orNull
        ?: throw org.gradle.api.GradleException(
            "Missing release signing environment variable: $name. " +
                "Set ${releaseSigningEnvironmentVariables.joinToString()} before running a release build."
        )
```

- [ ] **Step 3: Add `release` signingConfig inside `android { ... }`**

Insert this block inside `android { ... }`, before `buildTypes { ... }`:

```kotlin
    signingConfigs {
        create("release") {
            if (releaseSigningRequested) {
                storeFile = file(requiredReleaseSigningEnv("ANDROID_RELEASE_KEYSTORE_PATH"))
                storePassword = requiredReleaseSigningEnv("ANDROID_RELEASE_STORE_PASSWORD")
                keyAlias = requiredReleaseSigningEnv("ANDROID_RELEASE_KEY_ALIAS")
                keyPassword = requiredReleaseSigningEnv("ANDROID_RELEASE_KEY_PASSWORD")
            }
        }
    }
```

- [ ] **Step 4: Attach signingConfig to the existing `release` build type**

Update the existing `release { ... }` block in `app/build.gradle.kts` to include `signingConfig = signingConfigs.getByName("release")`:

```kotlin
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
```

- [ ] **Step 5: Verify debug build does not require signing env**

Run:

```bash
env -u ANDROID_RELEASE_KEYSTORE_PATH \
  -u ANDROID_RELEASE_STORE_PASSWORD \
  -u ANDROID_RELEASE_KEY_ALIAS \
  -u ANDROID_RELEASE_KEY_PASSWORD \
  ./gradlew :app:assembleDebug --no-daemon
```

Expected: build succeeds and produces `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6: Verify release build fails clearly without signing env**

Run:

```bash
env -u ANDROID_RELEASE_KEYSTORE_PATH \
  -u ANDROID_RELEASE_STORE_PASSWORD \
  -u ANDROID_RELEASE_KEY_ALIAS \
  -u ANDROID_RELEASE_KEY_PASSWORD \
  ./gradlew :app:assembleRelease --no-daemon
```

Expected: build fails during Gradle configuration or early task execution with:

```text
Missing release signing environment variable: ANDROID_RELEASE_KEYSTORE_PATH
```

- [ ] **Step 7: Commit Gradle signing config**

Run:

```bash
git add app/build.gradle.kts
git commit -m "build(release): read signing config from environment"
```

Expected: commit succeeds with only `app/build.gradle.kts` changed.

### Task 2: Add GitHub Actions Release APK Workflow

**Files:**
- Create: `.github/workflows/android-release-apk.yml`

- [ ] **Step 1: Create workflow file**

Create `.github/workflows/android-release-apk.yml` with this complete content:

```yaml
name: Android Release APK

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: android-release-apk-${{ github.ref }}
  cancel-in-progress: false

jobs:
  build-release-apk:
    name: Build Release APK
    runs-on: ubuntu-latest
    environment: release
    permissions:
      contents: read
    outputs:
      apk-name: ${{ steps.name-apk.outputs.apk_name }}

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Set up Android SDK
        uses: android-actions/setup-android@v4.0.1

      - name: Validate release signing secrets
        env:
          ANDROID_RELEASE_KEYSTORE_BASE64: ${{ secrets.ANDROID_RELEASE_KEYSTORE_BASE64 }}
          ANDROID_RELEASE_STORE_PASSWORD: ${{ secrets.ANDROID_RELEASE_STORE_PASSWORD }}
          ANDROID_RELEASE_KEY_ALIAS: ${{ secrets.ANDROID_RELEASE_KEY_ALIAS }}
          ANDROID_RELEASE_KEY_PASSWORD: ${{ secrets.ANDROID_RELEASE_KEY_PASSWORD }}
        run: |
          missing=0
          for name in \
            ANDROID_RELEASE_KEYSTORE_BASE64 \
            ANDROID_RELEASE_STORE_PASSWORD \
            ANDROID_RELEASE_KEY_ALIAS \
            ANDROID_RELEASE_KEY_PASSWORD
          do
            if [ -z "${!name}" ]; then
              echo "::error::$name is not configured in the GitHub release Environment"
              missing=1
            fi
          done
          exit "$missing"

      - name: Decode release keystore
        env:
          ANDROID_RELEASE_KEYSTORE_BASE64: ${{ secrets.ANDROID_RELEASE_KEYSTORE_BASE64 }}
        run: |
          printf '%s' "$ANDROID_RELEASE_KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/release.jks"
          chmod 600 "$RUNNER_TEMP/release.jks"

      - name: Build Release APK
        env:
          ANDROID_RELEASE_KEYSTORE_PATH: ${{ runner.temp }}/release.jks
          ANDROID_RELEASE_STORE_PASSWORD: ${{ secrets.ANDROID_RELEASE_STORE_PASSWORD }}
          ANDROID_RELEASE_KEY_ALIAS: ${{ secrets.ANDROID_RELEASE_KEY_ALIAS }}
          ANDROID_RELEASE_KEY_PASSWORD: ${{ secrets.ANDROID_RELEASE_KEY_PASSWORD }}
        run: ./gradlew :app:assembleRelease --no-daemon

      - name: Name APK
        id: name-apk
        run: |
          if [ "$GITHUB_REF_TYPE" = "tag" ]; then
            apk_name="MusicFreeAndroid-${GITHUB_REF_NAME}.apk"
          else
            apk_name="MusicFreeAndroid-manual-${GITHUB_RUN_NUMBER}.apk"
          fi
          cp app/build/outputs/apk/release/app-release.apk "$RUNNER_TEMP/$apk_name"
          echo "apk_name=$apk_name" >> "$GITHUB_OUTPUT"

      - name: Upload Release APK artifact
        uses: actions/upload-artifact@v7
        with:
          name: MusicFreeAndroid-release-apk
          path: ${{ runner.temp }}/${{ steps.name-apk.outputs.apk_name }}
          if-no-files-found: error
          retention-days: 14

  publish-github-release:
    name: Publish GitHub Release
    needs: build-release-apk
    if: github.event_name == 'push' && github.ref_type == 'tag'
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - name: Download Release APK artifact
        uses: actions/download-artifact@v7
        with:
          name: MusicFreeAndroid-release-apk
          path: release-apk

      - name: Upload APK to GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          GH_REPO: ${{ github.repository }}
        run: |
          apk_path="release-apk/${{ needs.build-release-apk.outputs.apk-name }}"
          if gh release view "$GITHUB_REF_NAME" >/dev/null 2>&1; then
            gh release upload "$GITHUB_REF_NAME" "$apk_path" --clobber
          else
            gh release create "$GITHUB_REF_NAME" "$apk_path" \
              --title "$GITHUB_REF_NAME" \
              --generate-notes
          fi
```

- [ ] **Step 2: Run static YAML sanity check**

Run:

```bash
python3 - <<'PY'
from pathlib import Path
path = Path(".github/workflows/android-release-apk.yml")
text = path.read_text()
required = [
    "environment: release",
    "ANDROID_RELEASE_KEYSTORE_BASE64",
    "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS",
    "ANDROID_RELEASE_KEY_PASSWORD",
    "contents: write",
    "gh release upload",
    "gh release create",
]
missing = [item for item in required if item not in text]
if missing:
    raise SystemExit(f"Missing workflow fragments: {missing}")
print("workflow static check passed")
PY
```

Expected:

```text
workflow static check passed
```

- [ ] **Step 3: Commit release workflow**

Run:

```bash
git add .github/workflows/android-release-apk.yml
git commit -m "ci(release): build signed APK artifacts"
```

Expected: commit succeeds with only `.github/workflows/android-release-apk.yml` changed.

### Task 3: Verify Local Signed Release Build

**Files:**
- No source file changes.

- [ ] **Step 1: Export local signing environment from Keychain**

Run from the implementation worktree:

```bash
export ANDROID_RELEASE_KEYSTORE_PATH="$HOME/.musicfree-android-signing/musicfree-release.jks"
export ANDROID_RELEASE_STORE_PASSWORD="$(security find-generic-password -w -a musicfree-release -s com.zili.android.musicfreeandroid.release.storePassword)"
export ANDROID_RELEASE_KEY_ALIAS="musicfree-release"
export ANDROID_RELEASE_KEY_PASSWORD="$(security find-generic-password -w -a musicfree-release -s com.zili.android.musicfreeandroid.release.keyPassword)"
```

Expected: commands produce no output and do not print passwords.

- [ ] **Step 2: Build signed Release APK locally**

Run:

```bash
./gradlew :app:assembleRelease --no-daemon
```

Expected: build succeeds and produces:

```text
app/build/outputs/apk/release/app-release.apk
```

- [ ] **Step 3: Verify APK signature**

Run:

```bash
APKSIGNER="$(ls -1 "$ANDROID_HOME"/build-tools/*/apksigner | tail -1)"
"$APKSIGNER" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Expected output includes:

```text
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
V2 Signer: certificate SHA-256 digest:
```

- [ ] **Step 4: Clear signing environment from the shell**

Run:

```bash
unset ANDROID_RELEASE_KEYSTORE_PATH
unset ANDROID_RELEASE_STORE_PASSWORD
unset ANDROID_RELEASE_KEY_ALIAS
unset ANDROID_RELEASE_KEY_PASSWORD
```

Expected: commands produce no output.

### Task 4: Validate GitHub Actions Secret Wiring

**Files:**
- No source file changes.

- [ ] **Step 1: Confirm Environment secret names exist**

Run:

```bash
gh secret list --env release --repo hanklzl/MusicFreeAndroid
```

Expected output contains all four names:

```text
ANDROID_RELEASE_KEYSTORE_BASE64
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
ANDROID_RELEASE_STORE_PASSWORD
```

- [ ] **Step 2: Push implementation branch**

Run:

```bash
git push -u origin feat/release-apk-signing
```

Expected: branch is pushed to `origin/feat/release-apk-signing`.

- [ ] **Step 3: Trigger manual release APK workflow**

Run:

```bash
gh workflow run "Android Release APK" --ref feat/release-apk-signing --repo hanklzl/MusicFreeAndroid
```

Expected: GitHub queues a workflow_dispatch run. If GitHub rejects the run because the workflow is not present on the default branch yet, skip to Task 5 and validate the workflow after the PR is merged.

- [ ] **Step 4: Watch manual workflow run**

Run:

```bash
gh run list --workflow "Android Release APK" --repo hanklzl/MusicFreeAndroid --limit 1
```

Expected after the run completes: status is `completed` and conclusion is `success`. If the run waits for the `release` Environment approval, approve it in GitHub and rerun this command.

### Task 5: Final Review and PR Preparation

**Files:**
- Modify only if review finds an implementation defect: `app/build.gradle.kts`, `.github/workflows/android-release-apk.yml`

- [ ] **Step 1: Review changed files**

Run:

```bash
git diff main...HEAD -- app/build.gradle.kts .github/workflows/android-release-apk.yml
```

Expected: diff contains no keystore content, no passwords, no base64 blobs, and no `keystore.properties`.

- [ ] **Step 2: Run whitespace check**

Run:

```bash
git diff --check main...HEAD
```

Expected: no output.

- [ ] **Step 3: Confirm Debug workflow remains unchanged**

Run:

```bash
git diff main...HEAD -- .github/workflows/android-debug-apk.yml
```

Expected: no output.

- [ ] **Step 4: Push latest branch state**

Run:

```bash
git push
```

Expected: `origin/feat/release-apk-signing` is up to date.

- [ ] **Step 5: Open draft PR**

Run:

```bash
gh pr create \
  --repo hanklzl/MusicFreeAndroid \
  --base main \
  --head feat/release-apk-signing \
  --draft \
  --title "Configure signed Release APK workflow" \
  --body "Implements the release APK signing design in docs/superpowers/specs/2026-05-05-release-apk-signing-design.md.\n\nValidation:\n- ./gradlew :app:assembleDebug --no-daemon\n- env -u ANDROID_RELEASE_KEYSTORE_PATH -u ANDROID_RELEASE_STORE_PASSWORD -u ANDROID_RELEASE_KEY_ALIAS -u ANDROID_RELEASE_KEY_PASSWORD ./gradlew :app:assembleRelease --no-daemon\n- ./gradlew :app:assembleRelease --no-daemon with local Keychain-backed signing env\n- apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk"
```

Expected: GitHub returns a draft PR URL.
