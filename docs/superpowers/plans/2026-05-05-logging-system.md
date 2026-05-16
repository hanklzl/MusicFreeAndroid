# Logan Logging System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Logan-backed app logging system, feedback log package sharing, local decode tooling, first-phase core-path instrumentation, and `AGENTS.md` logging rules.

**Architecture:** Add a standalone `:logging` module that exposes `MfLogger`/`MfLog` and hides Logan behind `LoganMfLogger`. `:app` initializes logging and provides FileProvider sharing; feature/data/player/plugin modules use the logging facade for structured JSON events.

**Tech Stack:** Kotlin, Android library module, Logan `com.dianping.android.sdk:logan:1.3.0`, Hilt where existing modules already use it, Jetpack Compose settings UI, FileProvider, JUnit/Robolectric, Gradle Kotlin DSL.

---

## File Structure

Create:

- `logging/build.gradle.kts`: Android library module for logging facade, Logan dependency, tests.
- `logging/src/main/java/com/hank/musicfree/logging/LogCategory.kt`: stable category enum.
- `logging/src/main/java/com/hank/musicfree/logging/LogLevel.kt`: `TRACE`, `DETAIL`, `ERROR`.
- `logging/src/main/java/com/hank/musicfree/logging/MfLogger.kt`: public logging interface.
- `logging/src/main/java/com/hank/musicfree/logging/MfLog.kt`: global facade with no-op fallback.
- `logging/src/main/java/com/hank/musicfree/logging/LogEventFormatter.kt`: JSON line formatter.
- `logging/src/main/java/com/hank/musicfree/logging/LoggingConfig.kt`: Logan paths, key/IV, retention, app metadata.
- `logging/src/main/java/com/hank/musicfree/logging/LoggingInitializer.kt`: Logan initialization and uncaught exception handler.
- `logging/src/main/java/com/hank/musicfree/logging/LoganMfLogger.kt`: Logan-backed logger.
- `logging/src/main/java/com/hank/musicfree/logging/FeedbackLogExporter.kt`: package export and cleanup service.
- `logging/src/main/java/com/hank/musicfree/logging/FeedbackPackage.kt`: exported package metadata.
- `logging/src/test/java/com/hank/musicfree/logging/LogEventFormatterTest.kt`: formatter tests.
- `logging/src/test/java/com/hank/musicfree/logging/LogPrunerTest.kt`: retention and total-size tests.
- `logging/src/test/java/com/hank/musicfree/logging/FeedbackLogExporterTest.kt`: zip/manifest tests.
- `app/src/main/res/xml/feedback_file_paths.xml`: FileProvider cache path.
- `tools/logan/README.md`: local decode instructions.
- `tools/logan/decode-logan.sh`: local decode script wrapper.

Modify:

- `settings.gradle.kts`: include `:logging`.
- `gradle/libs.versions.toml`: add Logan artifact.
- `app/build.gradle.kts`: add `:logging`, BuildConfig fields for Logan key/IV, Release fail-fast.
- `app/src/main/AndroidManifest.xml`: add `FileProvider`.
- `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`: initialize logging.
- `app/src/main/java/com/hank/musicfree/MainActivity.kt`: app lifecycle/permission logs.
- `feature/settings/build.gradle.kts`: add `:logging`.
- `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`: inject/export/clear logs.
- `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`: add share/clear entries and confirmation dialog.
- `plugin/build.gradle.kts`, `feature/search/build.gradle.kts`, `feature/home/build.gradle.kts`, `player/build.gradle.kts`, `data/build.gradle.kts`: add `:logging` where instrumentation is added.
- `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`: replace first-phase raw `Log.*` with `MfLog`.
- `plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt`: add plugin API call events.
- `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsEngine.kt`: add JS console and engine error events.
- `plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt`: add request/response events.
- `plugin/src/main/java/com/hank/musicfree/plugin/engine/RequireShim.kt`: add module registration and asset read events.
- `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`: add search/fallback/play resolution events.
- `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`: add controller events.
- `player/src/main/java/com/hank/musicfree/player/service/PlaybackService.kt`: add service/session events.
- `feature/home/src/main/java/com/hank/musicfree/feature/home/playlistimport/PlaylistImportViewModel.kt`: add import events.
- `AGENTS.md`: add logging rules.

---

### Task 1: Gradle Module And BuildConfig Wiring

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `logging/build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: module build files that will call logging: `plugin/build.gradle.kts`, `feature/search/build.gradle.kts`, `feature/home/build.gradle.kts`, `player/build.gradle.kts`, `feature/settings/build.gradle.kts`

- [ ] **Step 1: Add the logging module and Logan dependency**

In `settings.gradle.kts`, add:

```kotlin
include(":logging")
```

In `gradle/libs.versions.toml`, add:

```toml
[versions]
logan = "1.3.0"

[libraries]
logan = { group = "com.dianping.android.sdk", name = "logan", version.ref = "logan" }
```

- [ ] **Step 2: Create `logging/build.gradle.kts`**

Use this complete module file:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hank.musicfree.logging"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logan)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
```

- [ ] **Step 3: Add app BuildConfig fields and release fail-fast**

In `app/build.gradle.kts`, add:

```kotlin
val releaseLoganEnvironmentVariables = listOf(
    "LOGAN_AES_KEY",
    "LOGAN_AES_IV",
)

fun requiredReleaseLoganEnv(name: String): String =
    providers.environmentVariable(name).orNull
        ?: throw org.gradle.api.GradleException(
            "Missing release Logan environment variable: $name. " +
                "Set ${releaseLoganEnvironmentVariables.joinToString()} before running a release build."
        )

fun quotedBuildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
```

Inside the existing `android` block's `defaultConfig` block, add debug-safe defaults:

```kotlin
buildConfigField("String", "LOGAN_AES_KEY", quotedBuildConfigString("0123456789abcdef"))
buildConfigField("String", "LOGAN_AES_IV", quotedBuildConfigString("abcdef0123456789"))
```

Inside the existing `buildTypes` block's `release` block, override:

```kotlin
if (releaseSigningRequested) {
    buildConfigField(
        "String",
        "LOGAN_AES_KEY",
        quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_KEY")),
    )
    buildConfigField(
        "String",
        "LOGAN_AES_IV",
        quotedBuildConfigString(requiredReleaseLoganEnv("LOGAN_AES_IV")),
    )
}
```

Also add:

```kotlin
implementation(project(":logging"))
```

- [ ] **Step 4: Add logging dependency to instrumented modules**

Add `implementation(project(":logging"))` to:

```text
plugin/build.gradle.kts
feature/search/build.gradle.kts
feature/home/build.gradle.kts
player/build.gradle.kts
feature/settings/build.gradle.kts
```

- [ ] **Step 5: Verify Gradle sync/build**

Run:

```bash
./gradlew :logging:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml logging/build.gradle.kts app/build.gradle.kts plugin/build.gradle.kts feature/search/build.gradle.kts feature/home/build.gradle.kts player/build.gradle.kts feature/settings/build.gradle.kts
git commit -m "build: add logging module and Logan dependency"
```

---

### Task 2: Logging Facade And JSON Formatter

**Files:**
- Create: `logging/src/main/java/com/hank/musicfree/logging/LogCategory.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/LogLevel.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/MfLogger.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/MfLog.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/LogEventFormatter.kt`
- Test: `logging/src/test/java/com/hank/musicfree/logging/LogEventFormatterTest.kt`

- [ ] **Step 1: Write formatter tests**

Create `LogEventFormatterTest.kt`:

```kotlin
package com.hank.musicfree.logging

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEventFormatterTest {
    @Test
    fun `formats trace event as single json object`() {
        val line = LogEventFormatter.format(
            level = LogLevel.TRACE,
            category = LogCategory.PLUGIN,
            event = "plugin_api_call_success",
            sessionId = "session-1",
            traceId = "trace-1",
            durationMs = 42,
            result = "success",
            fields = mapOf("platform" to "test", "count" to 2),
            throwable = null,
        )

        val json = JSONObject(line)
        assertEquals("trace", json.getString("level"))
        assertEquals("plugin", json.getString("category"))
        assertEquals("plugin_api_call_success", json.getString("event"))
        assertEquals("session-1", json.getString("sessionId"))
        assertEquals("trace-1", json.getString("traceId"))
        assertEquals(42, json.getLong("durationMs"))
        assertEquals("success", json.getString("result"))
        assertEquals("test", json.getJSONObject("fields").getString("platform"))
        assertEquals(2, json.getJSONObject("fields").getInt("count"))
    }

    @Test
    fun `formats throwable details for error event`() {
        val error = IllegalStateException("broken")

        val line = LogEventFormatter.format(
            level = LogLevel.ERROR,
            category = LogCategory.APP,
            event = "uncaught_exception",
            sessionId = "session-1",
            traceId = null,
            durationMs = null,
            result = "failure",
            fields = emptyMap(),
            throwable = error,
        )

        val json = JSONObject(line)
        assertEquals("IllegalStateException", json.getString("errorClass"))
        assertEquals("broken", json.getString("errorMessage"))
        assertTrue(json.getString("stackTrace").contains("IllegalStateException"))
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :logging:testDebugUnitTest --tests '*LogEventFormatterTest'
```

Expected: FAIL because formatter classes do not exist.

- [ ] **Step 3: Add public enums and interface**

Create `LogLevel.kt`:

```kotlin
package com.hank.musicfree.logging

enum class LogLevel(val wireName: String, val loganType: Int) {
    TRACE("trace", 1),
    DETAIL("detail", 2),
    ERROR("error", 3),
}
```

Create `LogCategory.kt`:

```kotlin
package com.hank.musicfree.logging

enum class LogCategory(val wireName: String) {
    APP("app"),
    PLUGIN("plugin"),
    SEARCH("search"),
    PLAYER("player"),
    PLAYLIST_IMPORT("playlist_import"),
    FEEDBACK("feedback"),
}
```

Create `MfLogger.kt`:

```kotlin
package com.hank.musicfree.logging

interface MfLogger {
    fun trace(category: LogCategory, event: String, fields: Map<String, Any?> = emptyMap())
    fun detail(category: LogCategory, event: String, fields: Map<String, Any?> = emptyMap())
    fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    )
    fun flush()
}
```

- [ ] **Step 4: Add global facade**

Create `MfLog.kt`:

```kotlin
package com.hank.musicfree.logging

object MfLog : MfLogger {
    @Volatile
    private var delegate: MfLogger = NoOpLogger

    fun install(logger: MfLogger) {
        delegate = logger
    }

    fun resetForTest() {
        delegate = NoOpLogger
    }

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        delegate.trace(category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        delegate.detail(category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        delegate.error(category, event, throwable, fields)
    }

    override fun flush() {
        delegate.flush()
    }
}

private object NoOpLogger : MfLogger {
    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) = Unit
    override fun flush() = Unit
}
```

- [ ] **Step 5: Implement JSON formatter**

Create `LogEventFormatter.kt`:

```kotlin
package com.hank.musicfree.logging

import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object LogEventFormatter {
    fun format(
        level: LogLevel,
        category: LogCategory,
        event: String,
        sessionId: String,
        traceId: String?,
        durationMs: Long?,
        result: String?,
        fields: Map<String, Any?>,
        throwable: Throwable?,
    ): String {
        val json = JSONObject()
            .put("level", level.wireName)
            .put("category", category.wireName)
            .put("event", event)
            .put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("sessionId", sessionId)
            .put("fields", JSONObject(fields.mapValues { (_, value) -> value.toJsonValue() }))

        traceId?.let { json.put("traceId", it) }
        durationMs?.let { json.put("durationMs", it) }
        result?.let { json.put("result", it) }
        throwable?.let { error ->
            json.put("errorClass", error::class.java.simpleName)
            json.put("errorMessage", error.message ?: "")
            json.put("stackTrace", error.stackTraceString())
        }
        return json.toString()
    }

    private fun Any?.toJsonValue(): Any = when (this) {
        null -> JSONObject.NULL
        is Boolean, is Number, is String -> this
        is Iterable<*> -> JSONArray(this.map { it.toJsonValue() })
        is Map<*, *> -> JSONObject(this.entries.associate { (key, value) ->
            key.toString() to value.toJsonValue()
        })
        else -> toString()
    }

    private fun Throwable.stackTraceString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
```

- [ ] **Step 6: Run formatter tests**

Run:

```bash
./gradlew :logging:testDebugUnitTest --tests '*LogEventFormatterTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add logging/src/main/java/com/hank/musicfree/logging logging/src/test/java/com/hank/musicfree/logging/LogEventFormatterTest.kt
git commit -m "feat(logging): add structured logging facade"
```

---

### Task 3: Logan Backend, Initialization, Retention

**Files:**
- Create: `logging/src/main/java/com/hank/musicfree/logging/LoggingConfig.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/LoganMfLogger.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/LoggingInitializer.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/LogPruner.kt`
- Test: `logging/src/test/java/com/hank/musicfree/logging/LogPrunerTest.kt`

- [ ] **Step 1: Write retention tests**

Create `LogPrunerTest.kt`:

```kotlin
package com.hank.musicfree.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LogPrunerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `prune deletes files older than retention window`() {
        val dir = tmp.newFolder("logan")
        val old = dir.resolve("2026-04-20.log").apply { writeText("old") }
        val fresh = dir.resolve("2026-05-05.log").apply { writeText("fresh") }
        old.setLastModified(Instant.parse("2026-04-20T00:00:00Z").toEpochMilli())
        fresh.setLastModified(Instant.parse("2026-05-05T00:00:00Z").toEpochMilli())

        LogPruner.prune(
            logDir = dir,
            retentionDays = 7,
            maxTotalBytes = 50L * 1024L * 1024L,
            clock = Clock.fixed(Instant.parse("2026-05-05T00:00:00Z"), ZoneOffset.UTC),
        )

        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :logging:testDebugUnitTest --tests '*LogPrunerTest'
```

Expected: FAIL because `LogPruner` does not exist.

- [ ] **Step 3: Implement config and pruner**

Create `LoggingConfig.kt`:

```kotlin
package com.hank.musicfree.logging

import java.io.File

data class LoggingConfig(
    val cacheDir: File,
    val logDir: File,
    val feedbackDir: File,
    val aesKey16: String,
    val aesIv16: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val applicationId: String,
    val buildType: String,
    val retentionDays: Int = 7,
    val maxTotalBytes: Long = 50L * 1024L * 1024L,
)
```

Create `LogPruner.kt`:

```kotlin
package com.hank.musicfree.logging

import java.io.File
import java.time.Clock
import java.time.Duration

object LogPruner {
    fun prune(
        logDir: File,
        retentionDays: Int,
        maxTotalBytes: Long,
        clock: Clock = Clock.systemDefaultZone(),
    ) {
        val files = logDir.listFiles()?.filter { it.isFile } ?: return
        val cutoffMs = clock.millis() - Duration.ofDays(retentionDays.toLong()).toMillis()
        files.filter { it.lastModified() < cutoffMs }.forEach { it.delete() }

        val remaining = logDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = remaining.sumOf { it.length() }
        for (file in remaining) {
            if (total <= maxTotalBytes) return
            val size = file.length()
            if (file.delete()) {
                total -= size
            }
        }
    }
}
```

- [ ] **Step 4: Implement Logan-backed logger**

Create `LoganMfLogger.kt`:

```kotlin
package com.hank.musicfree.logging

import com.dianping.logan.Logan

class LoganMfLogger(
    private val sessionId: String,
) : MfLogger {
    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        write(LogLevel.TRACE, category, event, fields, throwable = null, result = null)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        write(LogLevel.DETAIL, category, event, fields, throwable = null, result = null)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        write(LogLevel.ERROR, category, event, fields, throwable = throwable, result = "failure")
    }

    override fun flush() {
        Logan.f()
    }

    private fun write(
        level: LogLevel,
        category: LogCategory,
        event: String,
        fields: Map<String, Any?>,
        throwable: Throwable?,
        result: String?,
    ) {
        val line = LogEventFormatter.format(
            level = level,
            category = category,
            event = event,
            sessionId = sessionId,
            traceId = fields["traceId"] as? String,
            durationMs = fields["durationMs"] as? Long,
            result = result ?: fields["result"] as? String,
            fields = fields,
            throwable = throwable,
        )
        Logan.w(line, level.loganType)
    }
}
```

- [ ] **Step 5: Implement initializer**

Create `LoggingInitializer.kt`:

```kotlin
package com.hank.musicfree.logging

import android.util.Log
import com.dianping.logan.Logan
import com.dianping.logan.LoganConfig
import java.util.UUID

object LoggingInitializer {
    private const val TAG = "LoggingInitializer"

    fun initialize(config: LoggingConfig): String {
        val sessionId = UUID.randomUUID().toString()
        return try {
            config.cacheDir.mkdirs()
            config.logDir.mkdirs()
            config.feedbackDir.mkdirs()

            Logan.init(
                LoganConfig.Builder()
                    .setCachePath(config.cacheDir.absolutePath)
                    .setPath(config.logDir.absolutePath)
                    .setEncryptKey16(config.aesKey16.toByteArray())
                    .setEncryptIV16(config.aesIv16.toByteArray())
                    .build(),
            )
            MfLog.install(LoganMfLogger(sessionId))
            installUncaughtExceptionHandler()
            LogPruner.prune(config.logDir, config.retentionDays, config.maxTotalBytes)
            MfLog.trace(
                LogCategory.APP,
                "app_start",
                mapOf(
                    "versionName" to config.appVersionName,
                    "versionCode" to config.appVersionCode,
                    "applicationId" to config.applicationId,
                    "buildType" to config.buildType,
                ),
            )
            sessionId
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to initialize logging", error)
            sessionId
        }
    }

    private fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            MfLog.error(
                LogCategory.APP,
                "uncaught_exception",
                throwable,
                mapOf("thread" to thread.name),
            )
            MfLog.flush()
            previous?.uncaughtException(thread, throwable)
        }
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```bash
./gradlew :logging:testDebugUnitTest --tests '*LogPrunerTest' --tests '*LogEventFormatterTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add logging/src/main/java/com/hank/musicfree/logging logging/src/test/java/com/hank/musicfree/logging/LogPrunerTest.kt
git commit -m "feat(logging): initialize Logan backend"
```

---

### Task 4: Feedback Package Export, FileProvider, Decode Tooling

**Files:**
- Create: `logging/src/main/java/com/hank/musicfree/logging/FeedbackPackage.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/FeedbackLogExporter.kt`
- Test: `logging/src/test/java/com/hank/musicfree/logging/FeedbackLogExporterTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/feedback_file_paths.xml`
- Create: `tools/logan/README.md`
- Create: `tools/logan/decode-logan.sh`

- [ ] **Step 1: Write exporter test**

Create `FeedbackLogExporterTest.kt`:

```kotlin
package com.hank.musicfree.logging

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class FeedbackLogExporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `createPackage zips manifest readme and raw logan files`() {
        val logDir = tmp.newFolder("logan")
        logDir.resolve("2026-05-05").writeText("encrypted")
        val feedbackDir = tmp.newFolder("feedback")
        val config = LoggingConfig(
            cacheDir = tmp.newFolder("cache"),
            logDir = logDir,
            feedbackDir = feedbackDir,
            aesKey16 = "0123456789abcdef",
            aesIv16 = "abcdef0123456789",
            appVersionName = "1.0",
            appVersionCode = 1,
            applicationId = "com.example.debug",
            buildType = "debug",
        )

        val pkg = FeedbackLogExporter(config, sessionId = "session-1").createPackage()

        assertTrue(pkg.file.exists())
        ZipFile(pkg.file).use { zip ->
            assertTrue(zip.getEntry("manifest.json") != null)
            assertTrue(zip.getEntry("README-decode.md") != null)
            assertTrue(zip.getEntry("logan/2026-05-05") != null)
        }
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
./gradlew :logging:testDebugUnitTest --tests '*FeedbackLogExporterTest'
```

Expected: FAIL because exporter classes do not exist.

- [ ] **Step 3: Implement package data and exporter**

Create `FeedbackPackage.kt`:

```kotlin
package com.hank.musicfree.logging

import java.io.File

data class FeedbackPackage(
    val file: File,
    val fileName: String,
    val sizeBytes: Long,
)
```

Create `FeedbackLogExporter.kt` with zip creation, manifest creation, cleanup, and pruning:

```kotlin
package com.hank.musicfree.logging

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FeedbackLogExporter(
    private val config: LoggingConfig,
    private val sessionId: String,
) {
    fun createPackage(): FeedbackPackage {
        MfLog.flush()
        pruneLogs()
        config.feedbackDir.mkdirs()
        val stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = File(config.feedbackDir, "musicfree-feedback-$stamp.zip")
        val logFiles = config.logDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putText("manifest.json", buildManifest(logFiles).toString(2))
            zip.putText("README-decode.md", decodeReadme())
            logFiles.forEach { file ->
                zip.putNextEntry(ZipEntry("logan/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        MfLog.trace(LogCategory.FEEDBACK, "feedback_package_created", mapOf("sizeBytes" to target.length()))
        return FeedbackPackage(target, target.name, target.length())
    }

    fun clearLogs() {
        config.logDir.deleteRecursively()
        config.feedbackDir.deleteRecursively()
        config.logDir.mkdirs()
        config.feedbackDir.mkdirs()
        MfLog.trace(LogCategory.FEEDBACK, "feedback_logs_cleared")
    }

    fun pruneLogs() {
        LogPruner.prune(config.logDir, config.retentionDays, config.maxTotalBytes)
    }

    private fun buildManifest(files: List<File>): JSONObject = JSONObject()
        .put("generatedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .put("sessionId", sessionId)
        .put("applicationId", config.applicationId)
        .put("versionName", config.appVersionName)
        .put("versionCode", config.appVersionCode)
        .put("buildType", config.buildType)
        .put("files", JSONArray(files.map { file ->
            JSONObject()
                .put("path", "logan/${file.name}")
                .put("sizeBytes", file.length())
                .put("lastModified", file.lastModified())
        }))

    private fun decodeReadme(): String =
        "Use tools/logan/decode-logan.sh with the matching Logan key and IV. " +
            "Debug logs use the repository development key. Release logs require LOGAN_AES_KEY and LOGAN_AES_IV."

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
```

- [ ] **Step 4: Add FileProvider wiring**

In `app/src/main/AndroidManifest.xml`, inside `<application>`, add:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.feedback-files"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/feedback_file_paths" />
</provider>
```

Create `app/src/main/res/xml/feedback_file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path
        name="feedback"
        path="feedback/" />
</paths>
```

- [ ] **Step 5: Add decode tooling**

Create executable `tools/logan/decode-logan.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

INPUT="${1:-}"
OUTPUT_DIR="${2:-tools/logan/out}"

if [[ -z "$INPUT" ]]; then
  echo "Usage: tools/logan/decode-logan.sh <feedback-zip-or-logan-dir> [output-dir]" >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"
echo "Decode input: $INPUT"
echo "Output dir: $OUTPUT_DIR"
echo "Release logs require LOGAN_AES_KEY and LOGAN_AES_IV in the environment."
echo "Use Logan's official decode tooling with the extracted files in this directory."
```

Create `tools/logan/README.md`:

```markdown
# Logan Decode Tooling

Debug logs use the development key configured in `app/build.gradle.kts`.

Release logs require:

```bash
export LOGAN_AES_KEY=your_release_key_16
export LOGAN_AES_IV=your_release_iv_16
```

Run:

```bash
tools/logan/decode-logan.sh path/to/musicfree-feedback.zip
```

The script prepares inputs for Logan decoding. Release secrets must not be committed.
```

- [ ] **Step 6: Run tests and build**

Run:

```bash
chmod +x tools/logan/decode-logan.sh
./gradlew :logging:testDebugUnitTest --tests '*FeedbackLogExporterTest' :app:assembleDebug
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add logging/src/main/java/com/hank/musicfree/logging/FeedbackPackage.kt logging/src/main/java/com/hank/musicfree/logging/FeedbackLogExporter.kt logging/src/test/java/com/hank/musicfree/logging/FeedbackLogExporterTest.kt app/src/main/AndroidManifest.xml app/src/main/res/xml/feedback_file_paths.xml tools/logan
git commit -m "feat(logging): export feedback log packages"
```

---

### Task 5: App And Settings Integration

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`
- Modify: `app/src/main/java/com/hank/musicfree/MainActivity.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add settings ViewModel tests**

Extend `SettingsViewModelTest.kt` with a fake exporter:

```kotlin
private class FakeFeedbackLogExporter : FeedbackLogExporterContract {
    var createCalls = 0
    var clearCalls = 0
    override suspend fun createPackage(): FeedbackPackage {
        createCalls++
        return FeedbackPackage(file = java.io.File("feedback.zip"), fileName = "feedback.zip", sizeBytes = 12)
    }
    override suspend fun clearLogs() {
        clearCalls++
    }
}
```

Add tests:

```kotlin
@Test
fun `create feedback package delegates to exporter`() = runTest(mainDispatcherRule.dispatcher) {
    val exporter = FakeFeedbackLogExporter()
    val viewModel = createViewModel(createAppPreferences(), exporter)

    viewModel.createFeedbackPackage()
    advanceUntilIdle()

    assertEquals(1, exporter.createCalls)
}

@Test
fun `clear logs delegates to exporter`() = runTest(mainDispatcherRule.dispatcher) {
    val exporter = FakeFeedbackLogExporter()
    val viewModel = createViewModel(createAppPreferences(), exporter)

    viewModel.clearLogs()
    advanceUntilIdle()

    assertEquals(1, exporter.clearCalls)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsViewModelTest'
```

Expected: FAIL because `FeedbackLogExporterContract` and ViewModel constructor changes do not exist.

- [ ] **Step 3: Initialize logging in Application**

Modify `MusicFreeApplication.kt`:

```kotlin
@HiltAndroidApp
class MusicFreeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LoggingInitializer.initialize(
            LoggingConfig(
                cacheDir = File(filesDir, "logan-cache"),
                logDir = File(filesDir, "logan"),
                feedbackDir = File(cacheDir, "feedback"),
                aesKey16 = BuildConfig.LOGAN_AES_KEY,
                aesIv16 = BuildConfig.LOGAN_AES_IV,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                applicationId = BuildConfig.APPLICATION_ID,
                buildType = BuildConfig.BUILD_TYPE,
            ),
        )
    }
}
```

- [ ] **Step 4: Add main activity trace logs**

In `MainActivity.onCreate`, add:

```kotlin
MfLog.trace(LogCategory.APP, "main_activity_create_start")
```

After `enableEdgeToEdge()`:

```kotlin
MfLog.trace(LogCategory.APP, "edge_to_edge_enabled")
```

Inside permission result callback:

```kotlin
MfLog.trace(
    LogCategory.APP,
    "notification_permission_result",
    mapOf("granted" to granted),
)
```

- [ ] **Step 5: Introduce exporter contract and ViewModel state**

If direct injection of `FeedbackLogExporter` is hard because it needs runtime config/session, create a small contract in `:logging`:

```kotlin
interface FeedbackLogExporterContract {
    suspend fun createPackage(): FeedbackPackage
    suspend fun clearLogs()
}
```

Update `FeedbackLogExporter` to implement it.

Change `SettingsViewModel` constructor:

```kotlin
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val feedbackLogExporter: FeedbackLogExporterContract,
) : ViewModel()
```

Add:

```kotlin
private val _feedbackPackage = MutableSharedFlow<FeedbackPackage>(extraBufferCapacity = 1)
val feedbackPackage: SharedFlow<FeedbackPackage> = _feedbackPackage.asSharedFlow()

fun createFeedbackPackage() {
    viewModelScope.launch {
        val pkg = feedbackLogExporter.createPackage()
        _feedbackPackage.emit(pkg)
    }
}

fun clearLogs() {
    viewModelScope.launch {
        feedbackLogExporter.clearLogs()
    }
}
```

- [ ] **Step 6: Wire settings UI share and clear actions**

In `SettingsScreen`, collect `feedbackPackage` and launch share intent:

```kotlin
val context = LocalContext.current
LaunchedEffect(Unit) {
    viewModel.feedbackPackage.collect { pkg ->
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.feedback-files",
            pkg.file,
        )
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "分享日志包"))
    }
}
```

Add entries:

```kotlin
SettingsEntryCard(
    title = "生成日志包并分享",
    description = "日志可能包含搜索词、请求地址、插件返回内容和设备信息。",
    actionText = "分享",
    onClick = { showFeedbackConfirm = true },
)

SettingsEntryCard(
    title = "清空日志",
    description = "删除本机已保存的反馈日志和临时日志包。",
    actionText = "清空",
    onClick = { viewModel.clearLogs() },
)
```

Add `AlertDialog` confirmation before `viewModel.createFeedbackPackage()`.

- [ ] **Step 7: Run tests and build**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest :app:assembleDebug
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt app/src/main/java/com/hank/musicfree/MainActivity.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsViewModel.kt feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsScreen.kt feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsViewModelTest.kt logging/src/main/java/com/hank/musicfree/logging
git commit -m "feat(logging): add app initialization and feedback sharing"
```

---

### Task 6: First-Phase Core Path Instrumentation

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/LoadedPlugin.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsEngine.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/engine/RequireShim.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/feature/search/SearchViewModel.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/service/PlaybackService.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/playlistimport/PlaylistImportViewModel.kt`

- [ ] **Step 1: Add a duration helper**

Create in `:logging`:

```kotlin
inline fun <T> timedFields(block: () -> T): Pair<T, Long> {
    val start = System.currentTimeMillis()
    val result = block()
    return result to (System.currentTimeMillis() - start)
}
```

For suspend calls, add:

```kotlin
suspend inline fun <T> timedSuspend(crossinline block: suspend () -> T): Pair<T, Long> {
    val start = System.currentTimeMillis()
    val result = block()
    return result to (System.currentTimeMillis() - start)
}
```

- [ ] **Step 2: Replace plugin raw logs with structured logs**

For each existing raw Android error log in plugin runtime, replace with structured logging:

```kotlin
MfLog.error(
    LogCategory.PLUGIN,
    "plugin_operation_failed",
    e,
    mapOf("operation" to "load_plugin", "fileName" to file.name),
)
```

For success paths, add:

```kotlin
MfLog.trace(
    LogCategory.PLUGIN,
    "plugin_loaded",
    mapOf("platform" to plugin.info.platform, "fileName" to file.name),
)
```

For `LoadedPlugin` API calls, use event names:

```text
plugin_api_call_start
plugin_api_call_success
plugin_api_call_failed
```

Fields:

```kotlin
mapOf(
    "platform" to info.platform,
    "method" to "search",
    "query" to query,
    "page" to page,
    "type" to type,
    "durationMs" to durationMs,
    "resultCount" to result.data.size,
)
```

- [ ] **Step 3: Add axios and JS engine logs**

In `AxiosShim`, log:

```kotlin
MfLog.detail(
    LogCategory.PLUGIN,
    "axios_request",
    mapOf("method" to "GET", "url" to fullUrl, "headers" to headersPreview(config)),
)
```

and:

```kotlin
MfLog.detail(
    LogCategory.PLUGIN,
    "axios_response",
    mapOf("method" to method, "url" to url, "status" to status, "body" to preview),
)
```

In `JsEngine`, map console methods to `MfLog.detail(LogCategory.PLUGIN, "js_console", mapOf("method" to "log", "message" to args.joinToString(" ")))` and use the actual console method name for `method`.

- [ ] **Step 4: Add search logs**

In `SearchViewModel.searchAll`, add:

```kotlin
MfLog.trace(
    LogCategory.SEARCH,
    "search_start",
    mapOf("query" to query, "mediaType" to mediaType.key),
)
```

On per-plugin success/failure, add `search_plugin_success` and `search_plugin_failed`. For WY fallback, add `playback_fallback_attempt`, `playback_fallback_success`, and `playback_fallback_failed`.

- [ ] **Step 5: Add player logs**

In `PlayerController.connect`, log `player_connect_start`, `player_connect_success`, and `player_connect_failed`.

In `setMediaItemAndPlay`, log:

```kotlin
MfLog.trace(
    LogCategory.PLAYER,
    "playback_start",
    mapOf("id" to item.id, "platform" to item.platform, "title" to item.title),
)
```

In catch blocks, log `playback_failed`.

In `PlaybackService`, log `playback_service_created`, `playback_service_destroyed`, `playback_session_connect`, and `playback_custom_command`.

- [ ] **Step 6: Add playlist import logs**

In `PlaylistImportViewModel`, log:

```text
playlist_import_opened
playlist_import_plugins_loaded
playlist_import_url_submitted
playlist_import_parse_success
playlist_import_parse_failed
playlist_import_items_added
playlist_import_rollback_failed
```

Fields should include `platform`, `itemCount`, `targetPlaylistId`, `added`, and `skipped` where available.

- [ ] **Step 7: Run focused tests and build**

Run:

```bash
./gradlew :plugin:testDebugUnitTest :feature:search:testDebugUnitTest :feature:home:testDebugUnitTest :player:testDebugUnitTest :app:assembleDebug
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add plugin/src/main/java feature/search/src/main/java player/src/main/java feature/home/src/main/java logging/src/main/java
git commit -m "feat(logging): instrument core diagnostics paths"
```

---

### Task 7: AGENTS Logging Rules And Final Verification

**Files:**
- Modify: `AGENTS.md`
- Test/verify: full relevant Gradle commands

- [ ] **Step 1: Update `AGENTS.md`**

Add a `### 日志记录规范` section under “核心设计约束”:

```markdown
### 日志记录规范

项目使用 `:logging` 模块和 `MfLogger` / `MfLog` 记录结构化日志，底层由美团 Logan 持久化。

- 新功能或 bugfix 涉及启动、插件、网络、播放、数据写入、导入导出、跨模块状态变化时，必须补结构化日志。
- 业务代码使用 `MfLogger` / `MfLog`，禁止新增直接 `android.util.Log.*` 和直接 Logan 调用。
- 日志事件命名使用稳定小写 snake_case，例如 `plugin_install_failed`。
- catch 后如果吞掉异常或转成用户 toast，必须记录 `error`。
- 耗时操作必须记录 `durationMs`，可使用 logging 模块提供的 timing helper。
- 日志字段使用稳定 key，避免把临时 UI 文案作为机器可读字段。
- 默认保留最近 7 天日志，并通过日志包分享能力提供用户反馈材料。
```

- [ ] **Step 2: Verify no new raw Android Log calls**

Run:

```bash
rg -n "import android\\.util\\.Log|Log\\.[vdiwe]" app core data player plugin feature logging --glob '!**/build/**' --glob '!**/src/main/assets/**'
```

Expected: no matches outside generated/build assets. If a match remains in code touched by this plan, replace it with `MfLog`.

- [ ] **Step 3: Run unit tests**

Run:

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run Debug build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Runtime smoke test**

Install and launch debug APK on available device/emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.hank.musicfree.debug 1
adb shell run-as com.hank.musicfree.debug ls files/logan
```

Expected: app launches and `files/logan` contains Logan output after startup.

- [ ] **Step 6: Commit**

```bash
git add AGENTS.md
git commit -m "docs: add logging development rules"
```

---

## Final Review Checklist

- [ ] `docs/superpowers/specs/2026-05-05-logging-system-design.md` requirements map to tasks above.
- [ ] `:logging` module builds and tests pass.
- [ ] Debug build passes.
- [ ] Settings page can generate and share a feedback zip.
- [ ] Zip contains `manifest.json`, `README-decode.md`, and raw Logan files.
- [ ] Startup, plugin, search, player, playlist import, and feedback paths write structured events.
- [ ] `AGENTS.md` contains the new logging rules.
- [ ] Remaining risks are documented in the final response.
