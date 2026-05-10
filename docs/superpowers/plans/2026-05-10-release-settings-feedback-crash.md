# Release Settings Feedback Crash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the release APK crash when opening settings entries from the home drawer.

**Architecture:** Keep Logan storage and feedback sharing directories as separate concepts. `LoggingConfig.cacheDir` remains Logan's cache path; a new `feedbackShareRootDir` models the Android FileProvider cache root used by feedback zips.

**Tech Stack:** Kotlin, Hilt, Android FileProvider, Logan logging, JUnit.

---

### Task 1: Add Regression Coverage

**Files:**
- Modify: `logging/src/test/java/com/zili/android/musicfreeandroid/logging/FeedbackLogExporterTest.kt`

- [ ] **Step 1: Add a failing production-shape test**

Add a test that uses separate `files/logan-cache` and `cache/feedback` roots:

```kotlin
@Test
fun `createPackage allows feedbackDir under share cache when logan cache is separate`() = runBlocking {
    val logDir = tmp.newFolder("files", "logan")
    val loganCacheDir = tmp.newFolder("files", "logan-cache")
    val feedbackShareRootDir = tmp.newFolder("cache")
    val feedbackDir = File(feedbackShareRootDir, "feedback").apply { mkdirs() }
    createLogFile(logDir, "release.log", "release", System.currentTimeMillis())

    val config = LoggingConfig(
        cacheDir = loganCacheDir,
        logDir = logDir,
        feedbackDir = feedbackDir,
        feedbackShareRootDir = feedbackShareRootDir,
        aesKey16 = "0123456789abcdef",
        aesIv16 = "abcdef0123456789",
        appVersionName = "1.0.0",
        appVersionCode = 1L,
        applicationId = "com.example.musicfree",
        buildType = "release",
    )

    val exporter = FeedbackLogExporter(config, sessionIdProvider = { "session-release" })

    val pkg = exporter.createPackage()

    assertTrue(pkg.file.exists())
    assertTrue(pkg.file.toPath().normalize().startsWith(feedbackDir.toPath().normalize()))
}
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
./gradlew :logging:testDebugUnitTest --tests '*FeedbackLogExporterTest*' --no-daemon
```

Expected before the fix: fail with `feedbackDir must be within cacheDir/feedback for secure sharing`.

### Task 2: Separate Feedback Share Root

**Files:**
- Modify: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/LoggingConfig.kt`
- Modify: `logging/src/main/java/com/zili/android/musicfreeandroid/logging/FeedbackLogExporter.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/di/LoggingModule.kt`

- [ ] **Step 1: Extend `LoggingConfig`**

Add a field with a default that preserves existing tests:

```kotlin
val feedbackShareRootDir: File = cacheDir,
```

- [ ] **Step 2: Use the share root in `FeedbackLogExporter`**

Change the allowed root to:

```kotlin
val allowedFeedbackRoot = config.feedbackShareRootDir.resolve("feedback").toPath().normalize().toAbsolutePath()
```

- [ ] **Step 3: Pass Android cache root from app code**

In `MusicFreeApplication` and `LoggingModule`, pass:

```kotlin
feedbackShareRootDir = cacheDir
```

or:

```kotlin
feedbackShareRootDir = context.cacheDir
```

- [ ] **Step 4: Run unit tests to verify GREEN**

Run:

```bash
./gradlew :logging:testDebugUnitTest --no-daemon
./gradlew :feature:settings:testDebugUnitTest --no-daemon
```

Expected: both tasks pass.

### Task 3: Release Runtime Verification

**Files:**
- No production source edits after Task 2 unless verification exposes a new root cause.

- [ ] **Step 1: Run harness grep**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: all checks pass.

- [ ] **Step 2: Build release APK with local signing material**

Run:

```bash
ANDROID_RELEASE_KEYSTORE_PATH="$HOME/.android/debug.keystore" ANDROID_RELEASE_STORE_PASSWORD=android ANDROID_RELEASE_KEY_ALIAS=androiddebugkey ANDROID_RELEASE_KEY_PASSWORD=android LOGAN_AES_KEY=0123456789abcdef LOGAN_AES_IV=abcdef0123456789 ./gradlew :app:assembleRelease --no-daemon
```

Expected: build succeeds.

- [ ] **Step 3: Install and exercise the release APK**

Install `app/build/outputs/apk/release/app-release.apk`, clear logcat, open the app, open the drawer, and tap each settings entry:

- `基础设置`
- `插件管理`
- `主题设置`
- `备份与恢复`
- `关于 MusicFree`

Expected: each entry opens a settings page without `AndroidRuntime` crash.
