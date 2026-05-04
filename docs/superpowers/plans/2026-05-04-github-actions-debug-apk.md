# GitHub Actions Debug APK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal GitHub Actions workflow that builds and uploads a Debug APK on each push, with a Debug-only package name suffix.

**Architecture:** Keep the implementation to two production files: Gradle owns the Debug `applicationId` suffix, and GitHub Actions owns remote APK build/upload. The workflow uses one Ubuntu job and the repository Gradle wrapper; local validation checks the Debug manifest and APK output.

**Tech Stack:** Android Gradle Plugin 9.2.0, Gradle Wrapper 9.4.1, Kotlin JVM toolchain 21, GitHub Actions, `actions/checkout@v6`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v5`, `android-actions/setup-android@v3`, `actions/upload-artifact@v7`.

---

## File Structure

- Modify `app/build.gradle.kts`
  - Responsibility: Android app module build configuration.
  - Change: add `applicationIdSuffix = ".debug"` to the `debug` build type.
- Create `.github/workflows/android-debug-apk.yml`
  - Responsibility: CI workflow for push-triggered Debug APK builds and artifact upload.
- Keep `docs/superpowers/specs/2026-05-04-github-actions-debug-apk-design.md`
  - Responsibility: approved design input for this plan.

## Task 1: Add Debug Application ID Suffix

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Verify the current Debug manifest does not use the debug package**

Run:

```bash
./gradlew :app:processDebugMainManifest --no-daemon
rg -n 'package="com\.zili\.android\.musicfreeandroid\.debug"' app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: Gradle succeeds, then `rg` exits with no match. This confirms the desired Debug package is currently absent.

- [ ] **Step 2: Add the Debug build type suffix**

In `app/build.gradle.kts`, replace the current `buildTypes` block:

```kotlin
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

with:

```kotlin
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

- [ ] **Step 3: Verify the Debug manifest now uses the debug package**

Run:

```bash
./gradlew :app:processDebugMainManifest --no-daemon
rg -n 'package="com\.zili\.android\.musicfreeandroid\.debug"' app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: Gradle succeeds, then `rg` prints a line containing:

```text
package="com.zili.android.musicfreeandroid.debug"
```

- [ ] **Step 4: Commit the Gradle change**

Run:

```bash
git add app/build.gradle.kts
git commit -m "chore: give debug apk a separate package name"
```

Expected: commit succeeds and includes only `app/build.gradle.kts`.

## Task 2: Add GitHub Actions Debug APK Workflow

**Files:**
- Create: `.github/workflows/android-debug-apk.yml`

- [ ] **Step 1: Verify the workflow file does not already exist**

Run:

```bash
test ! -e .github/workflows/android-debug-apk.yml
```

Expected: command exits successfully. If it fails, inspect the existing file before continuing.

- [ ] **Step 2: Create the workflow file**

Create `.github/workflows/android-debug-apk.yml` with exactly:

```yaml
name: Android Debug APK

on:
  push:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build-debug-apk:
    name: Build Debug APK
    runs-on: ubuntu-latest

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
        uses: android-actions/setup-android@v3

      - name: Build Debug APK
        run: ./gradlew :app:assembleDebug --no-daemon

      - name: Upload Debug APK
        uses: actions/upload-artifact@v7
        with:
          name: MusicFreeAndroid-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
```

- [ ] **Step 3: Verify the workflow contains the required trigger, build command, and artifact upload**

Run:

```bash
rg -n '^  push:|^  workflow_dispatch:|./gradlew :app:assembleDebug --no-daemon|actions/upload-artifact@v7' .github/workflows/android-debug-apk.yml
```

Expected: output contains four matches:

```text
  push:
  workflow_dispatch:
        run: ./gradlew :app:assembleDebug --no-daemon
        uses: actions/upload-artifact@v7
```

- [ ] **Step 4: Commit the workflow**

Run:

```bash
git add .github/workflows/android-debug-apk.yml
git commit -m "ci: build debug apk on push"
```

Expected: commit succeeds and includes only `.github/workflows/android-debug-apk.yml`.

## Task 3: Final Local Verification

**Files:**
- Verify: `app/build.gradle.kts`
- Verify: `.github/workflows/android-debug-apk.yml`

- [ ] **Step 1: Build the Debug APK locally**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Verify the APK artifact exists locally**

Run:

```bash
ls app/build/outputs/apk/debug/*.apk
```

Expected: output includes:

```text
app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Verify the Debug manifest package remains suffixed**

Run:

```bash
rg -n 'package="com\.zili\.android\.musicfreeandroid\.debug"' app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: output contains:

```text
package="com.zili.android.musicfreeandroid.debug"
```

- [ ] **Step 4: Verify the branch is clean after commits**

Run:

```bash
git status --short --branch
```

Expected: output is:

```text
## ci/debug-apk
```

If the output shows only ignored build artifacts, no action is needed. If tracked files are modified, inspect and commit or fix before handing off.

## Task 4: Remote Verification After Push

**Files:**
- Verify: `.github/workflows/android-debug-apk.yml`

- [ ] **Step 1: Push the branch**

Run:

```bash
git push -u origin ci/debug-apk
```

Expected: branch push succeeds and GitHub starts the `Android Debug APK` workflow.

- [ ] **Step 2: Verify the GitHub Actions run**

Open the repository Actions tab and inspect the `Android Debug APK` run for branch `ci/debug-apk`.

Expected:

- The `Build Debug APK` job completes successfully.
- The run summary contains an artifact named `MusicFreeAndroid-debug-apk`.
- The artifact contains the Debug APK built from `app/build/outputs/apk/debug/*.apk`.
