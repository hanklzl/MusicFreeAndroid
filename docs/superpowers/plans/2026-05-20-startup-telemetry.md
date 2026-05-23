# Startup Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add structured startup telemetry that splits cold process start, warm Activity recreate, Activity resume, first-frame timing, and non-blocking startup background flows.

**Architecture:** Add a small `StartupTelemetry` object in `:app` because it must be callable before Hilt injection in `Application.attachBaseContext()`. `MusicFreeApplication` and `MainActivity` record application and visible startup phases; app-module coordinators call telemetry directly, while `:updater` receives plain startup field maps to avoid reverse dependencies. Background flows remain asynchronous and keep their business logs.

**Tech Stack:** Kotlin, Android Application / ComponentActivity lifecycle, AndroidX SplashScreen, ViewTreeObserver pre-draw callback, `MfLog` / Logan structured logs, Kotlin coroutines, JUnit JVM tests, Hilt instrumentation startup smoke.

---

## File Structure

- Create `app/src/main/java/com/hank/musicfree/startup/StartupTelemetry.kt`
  - Owns process / Activity startup IDs, startup type classification, early pending events, phase logging, and startup flow logging.
- Create `app/src/test/java/com/hank/musicfree/startup/StartupTelemetryTest.kt`
  - Locks classification, field shape, pending flush, and resume behavior.
- Create `app/src/test/java/com/hank/musicfree/StartupApplicationContractTest.kt`
  - Source-level guard for `StartupBackupRestore.applyIfPending()` staying in `attachBaseContext()` and before app startup completion logging.
- Modify `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`
  - Records attach, pending restore, logging initialization, coordinator scheduling, and application complete.
- Modify `app/src/main/java/com/hank/musicfree/MainActivity.kt`
  - Records Activity startup phases, first frame, and resume-existing events without changing the SplashScreen order.
- Modify `app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt`
  - Extends existing source contract to guard startup telemetry placement around SplashScreen.
- Modify `app/src/androidTest/java/com/hank/musicfree/MainActivityStartupTest.kt`
  - Keeps launch smoke coverage for the first-frame hook.
- Modify `app/src/main/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinator.kt`
  - Adds aggregate `runtime_restore` startup flow events around existing per-store logs.
- Modify `app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt`
  - Verifies aggregate flow logs and failure isolation.
- Modify `app/src/main/java/com/hank/musicfree/bootstrap/PluginAutoUpdateCoordinator.kt`
  - Adds startup flow terminal logs for disabled, interval skipped, success, partial failure, failure, and cancellation branches.
- Modify `app/src/test/java/com/hank/musicfree/bootstrap/PluginAutoUpdateCoordinatorTest.kt`
  - Verifies startup flow fields for skipped and success branches.
- Modify `updater/src/main/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinator.kt`
  - Adds startup flow events using plain `Map<String, Any?>` fields from `:app`.
- Create `updater/src/test/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinatorTest.kt`
  - Verifies debug skipped terminal event and release dispatch terminal event shape with a fake checker.
- Modify `docs/dev-harness/startup/rules.md`
  - Only if implementation reveals a better exact validation command; otherwise leave docs unchanged.

## Task 1: StartupTelemetry Core

**Files:**
- Create: `app/src/main/java/com/hank/musicfree/startup/StartupTelemetry.kt`
- Create: `app/src/test/java/com/hank/musicfree/startup/StartupTelemetryTest.kt`

- [ ] **Step 1: Write failing StartupTelemetry tests**

Create `app/src/test/java/com/hank/musicfree/startup/StartupTelemetryTest.kt` with this test content:

```kotlin
package com.hank.musicfree.startup

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartupTelemetryTest {
    private val logger = RecordingLogger()
    private var now = 1_000_000_000L
    private var nextId = 0

    @Before
    fun setUp() {
        MfLog.resetForTest()
        MfLog.install(logger)
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { now },
            idProvider = { "id-${nextId++}" },
        )
    }

    @After
    fun tearDown() {
        StartupTelemetry.resetForTest()
        MfLog.resetForTest()
    }

    @Test
    fun firstActivityCreateIsColdProcessStart() {
        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()

        val session = StartupTelemetry.beginActivityCreate()

        assertEquals("cold_process_start", session.startupType)
        assertEquals(1, session.activityLaunchIndex)
        assertEquals("id-0", session.processStartId)
        assertEquals("id-1", session.activityStartId)
        assertEquals(true, session.isFirstActivityInProcess)
    }

    @Test
    fun secondActivityCreateInSameProcessIsWarmRecreate() {
        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()
        val first = StartupTelemetry.beginActivityCreate()
        val second = StartupTelemetry.beginActivityCreate()

        assertEquals("cold_process_start", first.startupType)
        assertEquals("warm_activity_recreate", second.startupType)
        assertEquals(first.processStartId, second.processStartId)
        assertNotEquals(first.activityStartId, second.activityStartId)
        assertEquals(2, second.activityLaunchIndex)
        assertEquals(false, second.isFirstActivityInProcess)
    }

    @Test
    fun resumeExistingSkipsInitialResumeAndLogsSubsequentResume() {
        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()
        StartupTelemetry.markLoggingReady()
        val session = StartupTelemetry.beginActivityCreate()

        StartupTelemetry.recordActivityResume(session)
        assertTrue(logger.events.none { it.event == "app_startup_activity_resume_existing" })

        now += 7_000_000L
        StartupTelemetry.recordActivityResume(session)

        val event = logger.events.single { it.event == "app_startup_activity_resume_existing" }
        assertEquals(LogCategory.APP, event.category)
        assertEquals("activity_resume_existing", event.fields["startupType"])
        assertEquals("id-0", event.fields["processStartId"])
        assertEquals("id-1", event.fields["activityStartId"])
        assertEquals(1, event.fields["activityLaunchIndex"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
    }

    @Test
    fun pendingEventsFlushOnceAfterLoggingIsReady() {
        StartupTelemetry.attachBaseContextStart()
        val pendingRestore = StartupTelemetry.startApplicationPhase("pending_restore")
        now += 5_000_000L
        StartupTelemetry.completeApplicationPhase(
            event = "app_startup_pending_restore_complete",
            phase = "pending_restore",
            span = pendingRestore,
            result = LogFields.Result.SUCCESS,
        )

        assertEquals(0, logger.events.size)

        StartupTelemetry.markLoggingReady()

        assertEquals(2, logger.events.size)
        assertEquals("app_startup_process_start", logger.events[0].event)
        assertEquals("app_startup_pending_restore_complete", logger.events[1].event)

        StartupTelemetry.markLoggingReady()
        assertEquals(2, logger.events.size)
    }

    @Test
    fun phaseTerminalFieldsContainDurationAndStartupIds() {
        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()
        StartupTelemetry.markLoggingReady()
        val session = StartupTelemetry.beginActivityCreate()
        val phase = session.startPhase("first_frame")
        now += 9_000_000L

        session.completePhase(
            event = "app_startup_first_frame",
            phase = "first_frame",
            span = phase,
            result = LogFields.Result.SUCCESS,
        )

        val event = logger.events.single { it.event == "app_startup_first_frame" }
        assertEquals(9L, event.fields["durationMs"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
        assertEquals("cold_process_start", event.fields["startupType"])
        assertEquals("id-0", event.fields["processStartId"])
        assertEquals("id-1", event.fields["activityStartId"])
    }

    @Test
    fun startupFlowTerminalIncludesFlowNameAndResult() {
        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()
        StartupTelemetry.markLoggingReady()
        val flow = StartupTelemetry.startFlow("runtime_restore")
        now += 12_000_000L

        StartupTelemetry.completeFlow(
            token = flow,
            result = LogFields.Result.SUCCESS,
            extraFields = mapOf("targetCount" to 3),
        )

        val start = logger.events.single { it.event == "startup_flow_start" }
        val complete = logger.events.single { it.event == "startup_flow_complete" }
        assertEquals("runtime_restore", start.fields["flowName"])
        assertEquals("runtime_restore", complete.fields["flowName"])
        assertEquals(12L, complete.fields["durationMs"])
        assertEquals(3, complete.fields["targetCount"])
        assertEquals(LogFields.Result.SUCCESS, complete.fields["result"])
    }

    private data class Event(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = CopyOnWriteArrayList<Event>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += Event(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += Event(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += Event(category, event, fields)
        }

        override fun flush() = Unit
    }
}
```

- [ ] **Step 2: Run the new tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.startup.StartupTelemetryTest --no-daemon
```

Expected: FAIL because `StartupTelemetry` does not exist.

- [ ] **Step 3: Implement StartupTelemetry**

Create `app/src/main/java/com/hank/musicfree/startup/StartupTelemetry.kt`:

```kotlin
package com.hank.musicfree.startup

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import java.util.UUID

object StartupTelemetry {
    const val STARTUP_TYPE_COLD_PROCESS = "cold_process_start"
    const val STARTUP_TYPE_WARM_ACTIVITY_RECREATE = "warm_activity_recreate"
    const val STARTUP_TYPE_ACTIVITY_RESUME_EXISTING = "activity_resume_existing"

    private const val OPERATION_STARTUP = "startup"
    private const val PHASE_PROCESS = "process"
    private const val EVENT_PROCESS_START = "app_startup_process_start"

    private val lock = Any()
    private var nanoTimeProvider: () -> Long = { System.nanoTime() }
    private var idProvider: () -> String = { UUID.randomUUID().toString() }
    private var processStartId: String = idProvider()
    private var processStartedAtNano: Long = nanoTimeProvider()
    private var applicationOnCreateSeen: Boolean = false
    private var loggingReady: Boolean = false
    private var processStartRecorded: Boolean = false
    private var activityLaunchIndex: Int = 0
    private val pendingEvents = ArrayDeque<PendingLogEvent>()

    fun attachBaseContextStart(): StartupSpan {
        val now = nanoTimeProvider()
        val event = synchronized(lock) {
            processStartedAtNano = now
            processStartRecorded = true
            PendingLogEvent(
                event = EVENT_PROCESS_START,
                fields = baseFieldsLocked(
                    startupType = STARTUP_TYPE_COLD_PROCESS,
                    activityStartId = null,
                    activityLaunchIndex = 0,
                    isFirstActivityInProcess = true,
                    phase = PHASE_PROCESS,
                    flowName = null,
                    durationMs = null,
                    result = null,
                    reason = null,
                    extraFields = emptyMap(),
                ),
            )
        }
        emit(event)
        return StartupSpan(name = "attach_base_context", startedAtNano = now)
    }

    fun applicationOnCreateStart(): StartupSpan {
        val now = nanoTimeProvider()
        synchronized(lock) {
            applicationOnCreateSeen = true
            if (!processStartRecorded) {
                processStartRecorded = true
                processStartedAtNano = now
                emitLocked(
                    PendingLogEvent(
                        event = EVENT_PROCESS_START,
                        fields = baseFieldsLocked(
                            startupType = STARTUP_TYPE_COLD_PROCESS,
                            activityStartId = null,
                            activityLaunchIndex = 0,
                            isFirstActivityInProcess = true,
                            phase = PHASE_PROCESS,
                            flowName = null,
                            durationMs = null,
                            result = null,
                            reason = null,
                            extraFields = emptyMap(),
                        ),
                    ),
                )
            }
        }
        return StartupSpan(name = "application", startedAtNano = now)
    }

    fun startApplicationPhase(name: String): StartupSpan =
        StartupSpan(name = name, startedAtNano = nanoTimeProvider())

    fun completeApplicationPhase(
        event: String,
        phase: String,
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val durationMs = elapsedMs(span.startedAtNano)
        val logEvent = synchronized(lock) {
            PendingLogEvent(
                event = event,
                fields = baseFieldsLocked(
                    startupType = STARTUP_TYPE_COLD_PROCESS,
                    activityStartId = null,
                    activityLaunchIndex = 0,
                    isFirstActivityInProcess = true,
                    phase = phase,
                    flowName = null,
                    durationMs = durationMs,
                    result = result,
                    reason = reason,
                    extraFields = extraFields,
                ),
            )
        }
        emit(logEvent)
    }

    fun completeApplicationStartup(
        result: String = LogFields.Result.SUCCESS,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val durationMs = synchronized(lock) {
            ((nanoTimeProvider() - processStartedAtNano) / 1_000_000L).coerceAtLeast(0L)
        }
        val logEvent = synchronized(lock) {
            PendingLogEvent(
                event = "app_startup_application_complete",
                fields = baseFieldsLocked(
                    startupType = STARTUP_TYPE_COLD_PROCESS,
                    activityStartId = null,
                    activityLaunchIndex = 0,
                    isFirstActivityInProcess = true,
                    phase = "application",
                    flowName = null,
                    durationMs = durationMs,
                    result = result,
                    reason = reason,
                    extraFields = extraFields,
                ),
            )
        }
        emit(logEvent)
    }

    fun markLoggingReady(
        durationMs: Long? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val eventsToFlush = synchronized(lock) {
            if (loggingReady) return
            loggingReady = true
            val copied = pendingEvents.toList()
            pendingEvents.clear()
            copied
        }
        eventsToFlush.forEach { logSafely(it) }
        if (durationMs != null) {
            completeApplicationPhase(
                event = "app_startup_logging_initialized",
                phase = "logging_initialized",
                span = StartupSpan("logging_initialized", nanoTimeProvider() - durationMs * 1_000_000L),
                result = LogFields.Result.SUCCESS,
                extraFields = extraFields,
            )
        }
    }

    fun beginActivityCreate(): StartupActivitySession {
        val now = nanoTimeProvider()
        return synchronized(lock) {
            activityLaunchIndex += 1
            val first = activityLaunchIndex == 1
            StartupActivitySession(
                processStartId = processStartId,
                activityStartId = idProvider(),
                activityLaunchIndex = activityLaunchIndex,
                startupType = if (first && applicationOnCreateSeen) {
                    STARTUP_TYPE_COLD_PROCESS
                } else {
                    STARTUP_TYPE_WARM_ACTIVITY_RECREATE
                },
                isFirstActivityInProcess = first,
                activityStartedAtNano = now,
                nanoTimeProvider = nanoTimeProvider,
                emit = { event, fields -> emit(PendingLogEvent(event, fields)) },
            )
        }
    }

    fun recordActivityResume(session: StartupActivitySession) {
        if (session.consumeInitialResume()) return
        session.logInstant(
            event = "app_startup_activity_resume_existing",
            phase = "activity_resume",
            startupTypeOverride = STARTUP_TYPE_ACTIVITY_RESUME_EXISTING,
            result = LogFields.Result.SUCCESS,
        )
    }

    fun startFlow(
        flowName: String,
        extraFields: Map<String, Any?> = emptyMap(),
    ): StartupFlowToken {
        val token = synchronized(lock) {
            StartupFlowToken(
                flowName = flowName,
                startedAtNano = nanoTimeProvider(),
                processStartId = processStartId,
            )
        }
        emit(
            PendingLogEvent(
                event = "startup_flow_start",
                fields = startupContextFields(
                    flowName = flowName,
                    phase = null,
                    durationMs = null,
                    result = null,
                    reason = null,
                    extraFields = extraFields,
                ),
            ),
        )
        return token
    }

    fun completeFlow(
        token: StartupFlowToken,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val event = when (result) {
            LogFields.Result.SKIPPED -> "startup_flow_skipped"
            LogFields.Result.FAILURE -> "startup_flow_failed"
            LogFields.Result.CANCELLED -> "startup_flow_cancelled"
            else -> "startup_flow_complete"
        }
        emit(
            PendingLogEvent(
                event = event,
                fields = startupContextFields(
                    flowName = token.flowName,
                    phase = null,
                    durationMs = elapsedMs(token.startedAtNano),
                    result = result,
                    reason = reason,
                    extraFields = extraFields,
                ),
            ),
        )
    }

    fun startupContextFields(
        flowName: String? = null,
        phase: String? = null,
        durationMs: Long? = null,
        result: String? = null,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = synchronized(lock) {
        baseFieldsLocked(
            startupType = STARTUP_TYPE_COLD_PROCESS,
            activityStartId = null,
            activityLaunchIndex = 0,
            isFirstActivityInProcess = true,
            phase = phase,
            flowName = flowName,
            durationMs = durationMs,
            result = result,
            reason = reason,
            extraFields = extraFields,
        )
    }

    fun resetForTest(
        nanoTimeProvider: () -> Long = { System.nanoTime() },
        idProvider: () -> String = { UUID.randomUUID().toString() },
    ) {
        synchronized(lock) {
            this.nanoTimeProvider = nanoTimeProvider
            this.idProvider = idProvider
            processStartId = idProvider()
            processStartedAtNano = nanoTimeProvider()
            applicationOnCreateSeen = false
            loggingReady = false
            processStartRecorded = false
            activityLaunchIndex = 0
            pendingEvents.clear()
        }
    }

    private fun emit(event: PendingLogEvent) {
        val shouldLog = synchronized(lock) {
            if (loggingReady) {
                true
            } else {
                pendingEvents.add(event)
                false
            }
        }
        if (shouldLog) logSafely(event)
    }

    private fun emitLocked(event: PendingLogEvent) {
        if (loggingReady) {
            logSafely(event)
        } else {
            pendingEvents.add(event)
        }
    }

    private fun logSafely(event: PendingLogEvent) {
        runCatching {
            MfLog.detail(
                category = LogCategory.APP,
                event = event.event,
                fields = event.fields,
            )
        }
    }

    private fun baseFieldsLocked(
        startupType: String,
        activityStartId: String?,
        activityLaunchIndex: Int,
        isFirstActivityInProcess: Boolean,
        phase: String?,
        flowName: String?,
        durationMs: Long?,
        result: String?,
        reason: String?,
        extraFields: Map<String, Any?>,
    ): Map<String, Any?> = buildMap {
        put("operation", OPERATION_STARTUP)
        put("startupType", startupType)
        put("processStartId", processStartId)
        put("activityStartId", activityStartId.orEmpty())
        put("activityLaunchIndex", activityLaunchIndex)
        put("isFirstActivityInProcess", isFirstActivityInProcess)
        if (phase != null) put("phase", phase)
        if (flowName != null) put("flowName", flowName)
        if (durationMs != null) put("durationMs", durationMs)
        if (result != null) put("result", result)
        if (reason != null) put("reason", reason)
        putAll(extraFields)
    }

    private fun elapsedMs(startedAtNano: Long): Long =
        ((nanoTimeProvider() - startedAtNano) / 1_000_000L).coerceAtLeast(0L)

    private data class PendingLogEvent(
        val event: String,
        val fields: Map<String, Any?>,
    )
}

data class StartupSpan(
    val name: String,
    val startedAtNano: Long,
)

data class StartupFlowToken(
    val flowName: String,
    val startedAtNano: Long,
    val processStartId: String,
)

class StartupActivitySession internal constructor(
    val processStartId: String,
    val activityStartId: String,
    val activityLaunchIndex: Int,
    val startupType: String,
    val isFirstActivityInProcess: Boolean,
    private val activityStartedAtNano: Long,
    private val nanoTimeProvider: () -> Long,
    private val emit: (String, Map<String, Any?>) -> Unit,
) {
    private var initialResumeConsumed = false

    fun startPhase(name: String): StartupSpan =
        StartupSpan(name = name, startedAtNano = nanoTimeProvider())

    fun completePhase(
        event: String,
        phase: String,
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        emit(
            event,
            fields(
                phase = phase,
                durationMs = elapsedMs(span.startedAtNano),
                result = result,
                reason = reason,
                extraFields = extraFields,
            ),
        )
    }

    fun logInstant(
        event: String,
        phase: String,
        startupTypeOverride: String? = null,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        emit(
            event,
            fields(
                phase = phase,
                durationMs = null,
                result = result,
                reason = reason,
                extraFields = extraFields,
                startupTypeOverride = startupTypeOverride,
            ),
        )
    }

    internal fun consumeInitialResume(): Boolean {
        if (!initialResumeConsumed) {
            initialResumeConsumed = true
            return true
        }
        return false
    }

    private fun fields(
        phase: String,
        durationMs: Long?,
        result: String?,
        reason: String?,
        extraFields: Map<String, Any?>,
        startupTypeOverride: String? = null,
    ): Map<String, Any?> = buildMap {
        put("operation", "startup")
        put("startupType", startupTypeOverride ?: startupType)
        put("processStartId", processStartId)
        put("activityStartId", activityStartId)
        put("activityLaunchIndex", activityLaunchIndex)
        put("isFirstActivityInProcess", isFirstActivityInProcess)
        put("phase", phase)
        if (durationMs != null) put("durationMs", durationMs)
        if (result != null) put("result", result)
        if (reason != null) put("reason", reason)
        putAll(extraFields)
    }

    private fun elapsedMs(startedAtNano: Long): Long =
        ((nanoTimeProvider() - startedAtNano) / 1_000_000L).coerceAtLeast(0L)
}
```

- [ ] **Step 4: Run StartupTelemetry tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.startup.StartupTelemetryTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/startup/StartupTelemetry.kt app/src/test/java/com/hank/musicfree/startup/StartupTelemetryTest.kt
git commit -m "feat(startup): 添加启动遥测核心"
```

## Task 2: Application Startup Phases

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`
- Create: `app/src/test/java/com/hank/musicfree/StartupApplicationContractTest.kt`

- [ ] **Step 1: Write failing source contract**

Create `app/src/test/java/com/hank/musicfree/StartupApplicationContractTest.kt`:

```kotlin
package com.hank.musicfree

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupApplicationContractTest {
    private val projectRoot: Path = locateProjectRoot()
    private val source = Files.readString(
        projectRoot.resolve("app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt"),
    )

    @Test
    fun `pending restore remains in attachBaseContext and before onCreate startup complete`() {
        val attachBody = extractFunctionBody(source, "attachBaseContext")
        val onCreateBody = extractFunctionBody(source, "onCreate")

        assertTrue(
            "attachBaseContext should start StartupTelemetry before pending restore",
            attachBody.indexOf("StartupTelemetry.attachBaseContextStart()") >= 0,
        )
        assertTrue(
            "pending restore must remain in attachBaseContext",
            attachBody.indexOf("StartupBackupRestore.applyIfPending(this)") >= 0,
        )
        assertTrue(
            "Application complete should stay in onCreate",
            onCreateBody.indexOf("StartupTelemetry.completeApplicationStartup(") >= 0,
        )
    }

    @Test
    fun `logging ready happens before startup coordinators are scheduled`() {
        val onCreateBody = extractFunctionBody(source, "onCreate")
        val loggingReady = onCreateBody.indexOf("StartupTelemetry.markLoggingReady(")
        val runtimeStart = onCreateBody.indexOf("runtimeRestoreCoordinator.start()")
        val applicationComplete = onCreateBody.indexOf("StartupTelemetry.completeApplicationStartup(")

        assertTrue("StartupTelemetry.markLoggingReady should be present", loggingReady >= 0)
        assertTrue("runtimeRestoreCoordinator.start should be present", runtimeStart >= 0)
        assertTrue("application complete should be present", applicationComplete >= 0)
        assertTrue("logging should be ready before startup coordinators", loggingReady < runtimeStart)
        assertTrue("application complete should be after coordinators are scheduled", runtimeStart < applicationComplete)
    }

    private fun extractFunctionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName")
        require(signatureIndex >= 0) { "Function $functionName not found" }
        val openBrace = source.indexOf('{', signatureIndex)
        require(openBrace >= 0) { "Function $functionName has no body" }
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(openBrace + 1, index)
                }
            }
        }
        error("Function $functionName body was not closed")
    }

    private fun locateProjectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) return current
            current = current.parent
        }
        error("Could not locate project root")
    }
}
```

- [ ] **Step 2: Run contract to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.StartupApplicationContractTest --no-daemon
```

Expected: FAIL because `MusicFreeApplication` does not call `StartupTelemetry`.

- [ ] **Step 3: Wire StartupTelemetry into MusicFreeApplication**

Modify `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`.

Add import:

```kotlin
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.startup.StartupTelemetry
```

Replace `attachBaseContext` with:

```kotlin
override fun attachBaseContext(base: Context) {
    StartupTelemetry.attachBaseContextStart()
    super.attachBaseContext(base)
    val pendingRestore = StartupTelemetry.startApplicationPhase("pending_restore")
    StartupBackupRestore.applyIfPending(this)
    StartupTelemetry.completeApplicationPhase(
        event = "app_startup_pending_restore_complete",
        phase = "pending_restore",
        span = pendingRestore,
        result = LogFields.Result.SUCCESS,
    )
}
```

At the start of `onCreate`, add:

```kotlin
val applicationStartup = StartupTelemetry.applicationOnCreateStart()
```

Wrap logging initialization with a phase:

```kotlin
val loggingStartup = StartupTelemetry.startApplicationPhase("logging_initialized")
LoggingInitializer.initialize(
    LoggingConfig(
        cacheDir = File(filesDir, "logan-cache"),
        logDir = File(filesDir, "logan"),
        feedbackDir = File(cacheDir, "feedback"),
        feedbackShareRootDir = cacheDir,
        aesKey16 = BuildConfig.LOGAN_AES_KEY,
        aesIv16 = BuildConfig.LOGAN_AES_IV,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        applicationId = BuildConfig.APPLICATION_ID,
        buildType = BuildConfig.BUILD_TYPE,
    ),
)
StartupTelemetry.markLoggingReady()
StartupTelemetry.completeApplicationPhase(
    event = "app_startup_logging_initialized",
    phase = "logging_initialized",
    span = loggingStartup,
    result = LogFields.Result.SUCCESS,
)
```

After `startLoggingPreferenceBridge()` add:

```kotlin
val preferenceBridgeFlow = StartupTelemetry.startFlow("logging_preference_bridge")
StartupTelemetry.completeFlow(
    token = preferenceBridgeFlow,
    result = LogFields.Result.SUCCESS,
    reason = "scheduled",
)
```

After `playbackStartupCoordinator.start()` add:

```kotlin
val playbackObserversFlow = StartupTelemetry.startFlow("playback_startup_observers")
StartupTelemetry.completeFlow(
    token = playbackObserversFlow,
    result = LogFields.Result.SUCCESS,
    reason = "scheduled",
)
```

At the end of `onCreate`, add:

```kotlin
StartupTelemetry.completeApplicationStartup(
    result = LogFields.Result.SUCCESS,
    extraFields = mapOf(
        "applicationOnCreateDurationMs" to
            ((System.nanoTime() - applicationStartup.startedAtNano) / 1_000_000L).coerceAtLeast(0L),
    ),
)
```

- [ ] **Step 4: Run application contract and startup telemetry tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.StartupApplicationContractTest --tests com.hank.musicfree.startup.StartupTelemetryTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt app/src/test/java/com/hank/musicfree/StartupApplicationContractTest.kt
git commit -m "feat(startup): 记录 Application 启动阶段"
```

## Task 3: MainActivity Content and First Frame Timing

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/MainActivity.kt`
- Modify: `app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt`
- Modify: `app/src/androidTest/java/com/hank/musicfree/MainActivityStartupTest.kt`

- [ ] **Step 1: Extend SplashScreen contract before implementation**

Add this test to `app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt`:

```kotlin
@Test
fun `main activity startup telemetry keeps splash before super and content timing after edge to edge`() {
    val source = Files.readString(
        projectRoot.resolve("app/src/main/java/com/hank/musicfree/MainActivity.kt"),
    )
    val onCreateBody = extractFunctionBody(source, "onCreate")
    val telemetryIndex = onCreateBody.indexOf("StartupTelemetry.beginActivityCreate()")
    val installIndex = onCreateBody.indexOf("installSplashScreen()")
    val superIndex = onCreateBody.indexOf("super.onCreate")
    val edgeIndex = onCreateBody.indexOf("enableEdgeToEdge()")
    val setContentIndex = onCreateBody.indexOf("setContent")
    val contentSetIndex = onCreateBody.indexOf("app_startup_activity_content_set")
    val firstFrameIndex = onCreateBody.indexOf("reportFirstFrame")

    assertTrue("startup telemetry should start before splash install", telemetryIndex >= 0)
    assertTrue("installSplashScreen() must remain before super.onCreate", installIndex < superIndex)
    assertTrue("edge-to-edge should remain before setContent", edgeIndex < setContentIndex)
    assertTrue("content set telemetry should be after setContent call", contentSetIndex > setContentIndex)
    assertTrue("first frame reporting should be registered after content timing", firstFrameIndex > contentSetIndex)
}
```

- [ ] **Step 2: Run the contract to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.SplashScreenResourceContractTest --no-daemon
```

Expected: FAIL because `MainActivity` does not use `StartupTelemetry`.

- [ ] **Step 3: Implement Activity phase timing**

Modify `app/src/main/java/com/hank/musicfree/MainActivity.kt`.

Add imports:

```kotlin
import android.view.View
import android.view.ViewTreeObserver
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.startup.StartupActivitySession
import com.hank.musicfree.startup.StartupTelemetry
```

Add a property inside `MainActivity`:

```kotlin
private var startupSession: StartupActivitySession? = null
```

At the top of `onCreate`, before `installSplashScreen()`, add:

```kotlin
val startup = StartupTelemetry.beginActivityCreate()
startupSession = startup
startup.logInstant(
    event = "app_startup_activity_create_start",
    phase = "activity_create_start",
    result = LogFields.Result.SUCCESS,
)
val splashPhase = startup.startPhase("splash_installed")
installSplashScreen()
startup.completePhase(
    event = "app_startup_splash_installed",
    phase = "splash_installed",
    span = splashPhase,
    result = LogFields.Result.SUCCESS,
)
```

Replace the existing `installSplashScreen()` line with the block above. Keep `super.onCreate(savedInstanceState)` immediately after the splash telemetry block.

Before `enableEdgeToEdge()`, add:

```kotlin
val edgeToEdgePhase = startup.startPhase("edge_to_edge_enabled")
```

After `enableEdgeToEdge()`, add:

```kotlin
startup.completePhase(
    event = "app_startup_edge_to_edge_enabled",
    phase = "edge_to_edge_enabled",
    span = edgeToEdgePhase,
    result = LogFields.Result.SUCCESS,
)
```

Before `setContent {`, add:

```kotlin
val contentSetPhase = startup.startPhase("activity_content_set")
```

Immediately after the `setContent` call returns, add:

```kotlin
startup.completePhase(
    event = "app_startup_activity_content_set",
    phase = "activity_content_set",
    span = contentSetPhase,
    result = LogFields.Result.SUCCESS,
)
reportFirstFrame(window.decorView, startup)
```

Add `onResume` below `onCreate`:

```kotlin
override fun onResume() {
    super.onResume()
    startupSession?.let(StartupTelemetry::recordActivityResume)
}
```

Add this private helper near `DebugPanelOverlay`:

```kotlin
private fun reportFirstFrame(rootView: View, startup: StartupActivitySession) {
    val firstFramePhase = startup.startPhase("first_frame")
    val observer = rootView.viewTreeObserver
    val listener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            val currentObserver = rootView.viewTreeObserver
            if (currentObserver.isAlive) {
                currentObserver.removeOnPreDrawListener(this)
            } else if (observer.isAlive) {
                observer.removeOnPreDrawListener(this)
            }
            startup.completePhase(
                event = "app_startup_first_frame",
                phase = "first_frame",
                span = firstFramePhase,
                result = LogFields.Result.SUCCESS,
            )
            return true
        }
    }
    observer.addOnPreDrawListener(listener)
}
```

- [ ] **Step 4: Keep legacy trace events for compatibility**

Leave these existing events in place for Maestro / older smoke references:

```kotlin
MfLog.trace(LogCategory.APP, "main_activity_create_start")
MfLog.trace(LogCategory.APP, "edge_to_edge_enabled")
```

Do not rename or remove them in this task.

- [ ] **Step 5: Run unit and instrumentation smoke**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.SplashScreenResourceContractTest --no-daemon
```

Expected: PASS.

Run when an emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.MainActivityStartupTest --no-daemon
```

Expected: PASS. If no emulator is connected, record the exact Gradle error in the final verification notes and continue with `:app:assembleDebug`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/MainActivity.kt app/src/test/java/com/hank/musicfree/SplashScreenResourceContractTest.kt app/src/androidTest/java/com/hank/musicfree/MainActivityStartupTest.kt
git commit -m "feat(startup): 记录 Activity 首屏耗时"
```

## Task 4: Runtime Restore Startup Flow

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinator.kt`
- Modify: `app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt`

- [ ] **Step 1: Write failing aggregate flow test**

Add this test to `RuntimeRestoreCoordinatorTest`:

```kotlin
@Test
fun runtimeRestoreLogsAggregateStartupFlow() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val logger = RecordingLogger()
    MfLog.install(logger)
    StartupTelemetry.resetForTest(
        nanoTimeProvider = { testScheduler.currentTime * 1_000_000L },
        idProvider = { "startup-id" },
    )
    StartupTelemetry.attachBaseContextStart()
    StartupTelemetry.applicationOnCreateStart()
    StartupTelemetry.markLoggingReady()
    val playback = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
    val plugin = FakeRuntimeStore("plugin", RuntimeRestoreResult.Skipped("empty"))
    val coordinator = RuntimeRestoreCoordinator(
        applicationScope = backgroundScope,
        registry = RuntimeStoreRegistry(setOf(playback, plugin)),
        workerDispatcher = dispatcher,
    )

    coordinator.start()
    runCurrent()

    val start = logger.events.single { it.event == "startup_flow_start" }
    val complete = logger.events.single { it.event == "startup_flow_complete" }
    assertEquals("runtime_restore", start.fields["flowName"])
    assertEquals("runtime_restore", complete.fields["flowName"])
    assertEquals(LogFields.Result.SUCCESS, complete.fields["result"])
    assertEquals(2, complete.fields["targetCount"])
    assertNotNull(complete.fields["durationMs"])
}
```

Add this import to the test file:

```kotlin
import com.hank.musicfree.startup.StartupTelemetry
```

Update `tearDown()`:

```kotlin
@After
fun tearDown() {
    StartupTelemetry.resetForTest()
    MfLog.resetForTest()
}
```

- [ ] **Step 2: Run RuntimeRestoreCoordinator test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.runtime.RuntimeRestoreCoordinatorTest --no-daemon
```

Expected: FAIL because aggregate `startup_flow_*` events are not emitted.

- [ ] **Step 3: Implement aggregate runtime restore flow**

Modify `app/src/main/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinator.kt`.

Add imports:

```kotlin
import com.hank.musicfree.startup.StartupTelemetry
```

Replace the `launch { restoreAll() }` block in `start()` with:

```kotlin
launch {
    val targetCount = registry.stores.count { it.restoreOnStartup }
    val flow = StartupTelemetry.startFlow(
        flowName = "runtime_restore",
        extraFields = mapOf("targetCount" to targetCount),
    )
    try {
        restoreAll()
        StartupTelemetry.completeFlow(
            token = flow,
            result = LogFields.Result.SUCCESS,
            extraFields = mapOf("targetCount" to targetCount),
        )
    } catch (error: CancellationException) {
        StartupTelemetry.completeFlow(
            token = flow,
            result = LogFields.Result.CANCELLED,
            reason = LogFields.Reason.CANCELLED,
            extraFields = mapOf("targetCount" to targetCount),
        )
        throw error
    } catch (error: Throwable) {
        StartupTelemetry.completeFlow(
            token = flow,
            result = LogFields.Result.FAILURE,
            reason = "exception",
            extraFields = mapOf("targetCount" to targetCount),
        )
        throw error
    }
}
```

- [ ] **Step 4: Run runtime restore tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.runtime.RuntimeRestoreCoordinatorTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinator.kt app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt
git commit -m "feat(startup): 记录运行态恢复聚合耗时"
```

## Task 5: Plugin Auto Update Startup Flow

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/bootstrap/PluginAutoUpdateCoordinator.kt`
- Modify: `app/src/test/java/com/hank/musicfree/bootstrap/PluginAutoUpdateCoordinatorTest.kt`

- [ ] **Step 1: Add failing tests for plugin auto update startup flow**

Add equivalent logger imports and `tearDown()` to `PluginAutoUpdateCoordinatorTest`, then add:

```kotlin
@Test
fun `runIfDue logs startup flow skipped when disabled`() = runTest {
    val logger = RecordingLogger()
    MfLog.install(logger)
    StartupTelemetry.resetForTest(idProvider = { "startup-id" })
    StartupTelemetry.attachBaseContextStart()
    StartupTelemetry.applicationOnCreateStart()
    StartupTelemetry.markLoggingReady()
    val appPreferences = mock<AppPreferences> {
        on { autoUpdatePlugins } doReturn flowOf(false)
        on { pluginAutoUpdateLastAtEpochMs } doReturn flowOf(0L)
    }
    val pluginManager = mock<PluginManager>()

    coordinator(appPreferences, pluginManager).runIfDue(nowMs = NOW)

    val skipped = logger.events.single { it.event == "startup_flow_skipped" }
    assertEquals("plugin_auto_update", skipped.fields["flowName"])
    assertEquals(LogFields.Result.SKIPPED, skipped.fields["result"])
    assertEquals("disabled", skipped.fields["reason"])
    assertNotNull(skipped.fields["durationMs"])
}
```

`RecordedLogEvent` 和 `RecordingLogger` 复用与 `PluginAutoUpdateCoordinatorTest` 中相同的模式（参考 `UpdateCheckCoordinatorTest` 的同名内部类写法）。

- [ ] **Step 2: Run plugin startup tests to verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.bootstrap.PluginAutoUpdateCoordinatorTest --no-daemon
```

Expected: FAIL because startup flow events are not emitted.

- [ ] **Step 3: Implement PluginAutoUpdateCoordinator telemetry**

Modify `PluginAutoUpdateCoordinator.kt`.

Add import:

```kotlin
import com.hank.musicfree.startup.StartupTelemetry
```

At the start of `runIfDue`, after `val startedAt = System.currentTimeMillis()`, add:

```kotlin
val startupFlow = StartupTelemetry.startFlow("plugin_auto_update")
```

Before each `return@withContext` in skipped branches, add:

```kotlin
StartupTelemetry.completeFlow(
    token = startupFlow,
    result = LogFields.Result.SKIPPED,
    reason = "disabled",
)
```

and for interval skipped:

```kotlin
StartupTelemetry.completeFlow(
    token = startupFlow,
    result = LogFields.Result.SKIPPED,
    reason = "interval_not_elapsed",
)
```

After `plugin_auto_update_completed`, add:

```kotlin
StartupTelemetry.completeFlow(
    token = startupFlow,
    result = if (result.failureCount == 0) LogFields.Result.SUCCESS else LogFields.Result.FAILURE,
    reason = if (result.failureCount == 0) null else "partial_failure",
    extraFields = mapOf(
        "targetCount" to result.targetPlugins.size,
        "successCount" to result.successCount,
        "failureCount" to result.failureCount,
    ),
)
```

In cancellation and failure catch blocks, add matching `completeFlow` before logging / rethrow:

```kotlin
StartupTelemetry.completeFlow(
    token = startupFlow,
    result = LogFields.Result.CANCELLED,
    reason = LogFields.Reason.CANCELLED,
)
```

and:

```kotlin
StartupTelemetry.completeFlow(
    token = startupFlow,
    result = LogFields.Result.FAILURE,
    reason = "exception",
)
```

- [ ] **Step 4: Run plugin startup tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.bootstrap.PluginAutoUpdateCoordinatorTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/bootstrap/PluginAutoUpdateCoordinator.kt app/src/test/java/com/hank/musicfree/bootstrap/PluginAutoUpdateCoordinatorTest.kt
git commit -m "feat(startup): 记录插件启动后台流程"
```

## Task 6: Update Check Startup Flow

**Files:**
- Modify: `updater/src/main/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinator.kt`
- Modify: `updater/src/main/java/com/hank/musicfree/updater/checker/UpdateChecker.kt`
- Modify: `updater/src/test/java/com/hank/musicfree/updater/checker/UpdateCheckerTest.kt`
- Create: `updater/src/test/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinatorTest.kt`
- Modify: `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`

- [ ] **Step 1: Write failing UpdateCheckCoordinator tests**

Create `updater/src/test/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinatorTest.kt`:

```kotlin
package com.hank.musicfree.updater.bootstrap

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.updater.checker.UpdateChecker
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckCoordinatorTest {
    private val logger = RecordingLogger()

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun debugBuildLogsStartupFlowSkipped() = runTest {
        MfLog.install(logger)
        val checker = mockk<UpdateChecker>(relaxed = true)
        val coordinator = UpdateCheckCoordinator(
            checker = checker,
            localAppVersion = localVersion(isDebug = true),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start(startupFields = mapOf("processStartId" to "process-1"))

        val skipped = logger.events.single { it.event == "startup_flow_skipped" }
        assertEquals(LogCategory.UPDATE, skipped.category)
        assertEquals("update_check_on_launch", skipped.fields["flowName"])
        assertEquals(LogFields.Result.SKIPPED, skipped.fields["result"])
        assertEquals("debug_build", skipped.fields["reason"])
        assertEquals("process-1", skipped.fields["processStartId"])
        assertNotNull(skipped.fields["durationMs"])
    }

    @Test
    fun releaseBuildPassesStartupFieldsToChecker() = runTest {
        MfLog.install(logger)
        val checker = mockk<UpdateChecker>(relaxed = true)
        val coordinator = UpdateCheckCoordinator(
            checker = checker,
            localAppVersion = localVersion(isDebug = false),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start(startupFields = mapOf("processStartId" to "process-1"))
        advanceUntilIdle()

        verify {
            checker.checkOnLaunch(
                startupFields = match {
                    it["processStartId"] == "process-1" &&
                        it["flowName"] == "update_check_on_launch"
                },
            )
        }
        assertEquals(1, logger.events.count { it.event == "startup_flow_start" })
    }

    private fun localVersion(isDebug: Boolean) = object : LocalAppVersion {
        override val versionName: String = if (isDebug) "debug" else "release"
        override val versionCode: Long = 1L
        override val isDebugBuild: Boolean = isDebug
    }

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = CopyOnWriteArrayList<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun flush() = Unit
    }
}
```

- [ ] **Step 2: Run updater test to verify it fails**

Run:

```bash
./gradlew :updater:testDebugUnitTest --tests com.hank.musicfree.updater.bootstrap.UpdateCheckCoordinatorTest --no-daemon
```

Expected: FAIL because `UpdateCheckCoordinator` has no dispatcher constructor and no startup fields parameter.

- [ ] **Step 3: Write failing UpdateChecker startup completion test**

Add this test to `updater/src/test/java/com/hank/musicfree/updater/checker/UpdateCheckerTest.kt`:

```kotlin
@Test
fun `check on launch emits startup flow completion when startup fields are provided`() = runTest(StandardTestDispatcher()) {
    val logger = RecordingLogger()
    MfLog.install(logger)
    val client = mockk<UpdateClient> {
        coEvery { fetchLatest() } returns newInfo("1.0.0", 10000)
    }
    val checker = UpdateChecker(
        client = client,
        prefs = mockPrefs(),
        abiResolver = armResolver(),
        localCode = 10000L,
        localName = "1.0.0",
        scope = this,
    )

    checker.checkOnLaunch(
        startupFields = mapOf(
            "operation" to "startup",
            "flowName" to "update_check_on_launch",
            "processStartId" to "process-1",
        ),
    )
    advanceUntilIdle()

    val complete = logger.events.single { it.event == "startup_flow_complete" }
    assertEquals(LogCategory.UPDATE, complete.category)
    assertEquals("update_check_on_launch", complete.fields["flowName"])
    assertEquals("process-1", complete.fields["processStartId"])
    assertEquals(LogFields.Result.SUCCESS, complete.fields["result"])
    assertTrue(complete.fields.containsKey("durationMs"))
}
```

Add these imports to `UpdateCheckerTest.kt`:

```kotlin
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
```

Add teardown and recording logger to `UpdateCheckerTest`:

```kotlin
@After
fun tearDown() {
    MfLog.resetForTest()
}

private data class RecordedLogEvent(
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
)

private class RecordingLogger : MfLogger {
    val events = CopyOnWriteArrayList<RecordedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += RecordedLogEvent(category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += RecordedLogEvent(category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += RecordedLogEvent(category, event, fields)
    }

    override fun flush() = Unit
}
```

- [ ] **Step 4: Run updater tests to verify failure**

Run:

```bash
./gradlew :updater:testDebugUnitTest --tests com.hank.musicfree.updater.bootstrap.UpdateCheckCoordinatorTest --tests com.hank.musicfree.updater.checker.UpdateCheckerTest --no-daemon
```

Expected: FAIL because `UpdateChecker.checkOnLaunch` does not accept `startupFields` and does not emit startup flow terminal events.

- [ ] **Step 5: Implement UpdateCheckCoordinator startup dispatch**

Modify `updater/src/main/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinator.kt`.

Replace the class with:

```kotlin
package com.hank.musicfree.updater.bootstrap

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateChecker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class UpdateCheckCoordinator internal constructor(
    private val checker: UpdateChecker,
    private val localAppVersion: LocalAppVersion,
    dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        checker: UpdateChecker,
        localAppVersion: LocalAppVersion,
    ) : this(
        checker = checker,
        localAppVersion = localAppVersion,
        dispatcher = Dispatchers.IO,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun start(startupFields: Map<String, Any?> = emptyMap()) {
        val startedAt = System.nanoTime()
        val baseFields = startupFields + mapOf(
            "operation" to "startup",
            "flowName" to "update_check_on_launch",
        )
        MfLog.detail(
            category = LogCategory.UPDATE,
            event = "startup_flow_start",
            fields = baseFields,
        )
        if (localAppVersion.isDebugBuild) {
            MfLog.trace(
                category = LogCategory.UPDATE,
                event = "update_check_skipped_debug",
                fields = baseFields + mapOf("reason" to "debug_build"),
            )
            MfLog.detail(
                category = LogCategory.UPDATE,
                event = "startup_flow_skipped",
                fields = baseFields + terminalFields(
                    startedAt = startedAt,
                    result = LogFields.Result.SKIPPED,
                    reason = "debug_build",
                ),
            )
            return
        }
        scope.launch {
            checker.checkOnLaunch(startupFields = baseFields)
        }
    }

    private fun terminalFields(
        startedAt: Long,
        result: String,
        reason: String?,
    ): Map<String, Any?> = buildMap {
        put("durationMs", ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L))
        put("result", result)
        if (reason != null) put("reason", reason)
    }
}
```

- [ ] **Step 6: Implement UpdateChecker terminal flow logging**

Modify `updater/src/main/java/com/hank/musicfree/updater/checker/UpdateChecker.kt`.

Change public launch methods to:

```kotlin
fun checkOnLaunch(startupFields: Map<String, Any?> = emptyMap()) {
    check(respectSkip = true, startupFields = startupFields)
}

fun checkManually() {
    check(respectSkip = false, startupFields = emptyMap())
}
```

Change the private signature to:

```kotlin
private fun check(
    respectSkip: Boolean,
    startupFields: Map<String, Any?>,
) {
    val startedAt = System.nanoTime()
    scope.launch {
        mutex.withLock {
            val baseFields = startupFields + mapOf("respectSkip" to respectSkip)
```

Inside every terminal branch that currently logs `update_check_complete`, also call:

```kotlin
logStartupFlowTerminal(
    startupFields = startupFields,
    event = "startup_flow_complete",
    result = LogFields.Result.SUCCESS,
    reason = null,
    startedAt = startedAt,
)
```

For `newer_available` where skipped is true, use:

```kotlin
logStartupFlowTerminal(
    startupFields = startupFields,
    event = if (isSkipped) "startup_flow_skipped" else "startup_flow_complete",
    result = if (isSkipped) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
    reason = if (isSkipped) "version_skipped" else null,
    startedAt = startedAt,
)
```

Inside every failure branch that currently logs `update_check_failed`, also call:

```kotlin
logStartupFlowTerminal(
    startupFields = startupFields,
    event = "startup_flow_failed",
    result = LogFields.Result.FAILURE,
    reason = UpdateError.Network.name,
    startedAt = startedAt,
)
```

Use the matching failure reason already known in that branch: `UpdateError.Network.name`, `UpdateError.SchemaUnsupported.name`, or `UpdateError.UnsupportedAbi.name`.

Add this helper inside `UpdateChecker`:

```kotlin
private fun logStartupFlowTerminal(
    startupFields: Map<String, Any?>,
    event: String,
    result: String,
    reason: String?,
    startedAt: Long,
) {
    if (startupFields.isEmpty()) return
    MfLog.detail(
        category = LogCategory.UPDATE,
        event = event,
        fields = startupFields + buildMap {
            put("durationMs", ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L))
            put("result", result)
            if (reason != null) put("reason", reason)
        },
    )
}
```

When you adjust `update_check_start`, include startup fields without losing the existing `respectSkip` field:

```kotlin
MfLog.trace(
    category = LogCategory.UPDATE,
    event = "update_check_start",
    fields = baseFields,
)
```

- [ ] **Step 7: Pass startup fields from MusicFreeApplication**

Modify the existing `updateCheckCoordinator.start()` call in `MusicFreeApplication.kt` to:

```kotlin
updateCheckCoordinator.start(
    startupFields = StartupTelemetry.startupContextFields(
        flowName = "update_check_on_launch",
    ),
)
```

- [ ] **Step 8: Run updater and app compile tests**

Run:

```bash
./gradlew :updater:testDebugUnitTest --tests com.hank.musicfree.updater.bootstrap.UpdateCheckCoordinatorTest --no-daemon
./gradlew :updater:testDebugUnitTest --tests com.hank.musicfree.updater.checker.UpdateCheckerTest --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: all commands PASS.

- [ ] **Step 9: Commit**

```bash
git add updater/src/main/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinator.kt updater/src/main/java/com/hank/musicfree/updater/checker/UpdateChecker.kt updater/src/test/java/com/hank/musicfree/updater/bootstrap/UpdateCheckCoordinatorTest.kt updater/src/test/java/com/hank/musicfree/updater/checker/UpdateCheckerTest.kt app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt
git commit -m "feat(startup): 记录启动更新检查耗时"
```

## Task 7: Final Verification and Startup Evidence

**Files:**
- Modify only if needed after verification: `docs/dev-harness/startup/rules.md`
- No production code changes unless a prior test reveals a bug.

- [ ] **Step 1: Run focused JVM tests**

Run:

```bash
./gradlew :app:testDebugUnitTest :updater:testDebugUnitTest :logging:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run dev harness**

Run:

```bash
bash scripts/dev-harness/check.sh
```

Expected: PASS.

- [ ] **Step 3: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Capture startup log evidence when a debug emulator is available**

Install and cold start:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.zili.android.musicfreeandroid.debug
adb shell monkey -p com.zili.android.musicfreeandroid.debug 1
sleep 5
```

Pull Logan files:

```bash
rm -rf tools/logan/out/startup-telemetry
mkdir -p tools/logan/out/startup-telemetry
adb shell run-as com.zili.android.musicfreeandroid.debug sh -c 'cp -r files/logan cache/logan-copy'
adb exec-out run-as com.zili.android.musicfreeandroid.debug tar -C cache -cf - logan-copy > tools/logan/out/startup-telemetry/logan-copy.tar
tar -C tools/logan/out/startup-telemetry -xf tools/logan/out/startup-telemetry/logan-copy.tar
tools/logan/decode-logan.sh tools/logan/out/startup-telemetry/logan-copy
```

Expected decoded events include:

```text
app_startup_process_start
app_startup_application_complete
app_startup_activity_content_set
app_startup_first_frame
startup_flow_start
startup_flow_complete
```

If no emulator is available, record `adb devices` output and state that runtime log evidence was not captured.

- [ ] **Step 5: Review main-thread startup budget**

From decoded logs, compare the first available cold-start sample against the new events:

```text
app_startup_activity_content_set.durationMs
app_startup_first_frame.durationMs
```

If no pre-change baseline exists in this branch, state that this implementation only adds O(1) ID/time/Map operations on the main startup path and provide the observed values as the first baseline for future Startup Harness checks.

- [ ] **Step 6: Final review**

Run:

```bash
git status --short --branch
git log --oneline -8
git diff "$(git merge-base main HEAD)..HEAD" --stat
```

Expected:

- Worktree clean except generated `tools/logan/out/` files, which are gitignored.
- Recent commits match the task sequence.
- Diff is limited to startup telemetry, startup tests, updater startup flow, and startup docs.

- [ ] **Step 7: Commit any final docs-only correction**

Only if verification updates docs:

```bash
git add docs/dev-harness/startup/rules.md docs/superpowers/specs/2026-05-19-startup-telemetry-design.md
git commit -m "docs(startup): 补充启动遥测验收说明"
```

## Self-Review

Spec coverage:

- Cold / warm / resume classification: Task 1 and Task 3.
- Application phases and pending restore order: Task 2.
- Activity content set and first frame: Task 3.
- Runtime restore background flow: Task 4.
- Plugin auto-update flow: Task 5.
- Update check flow without `:updater` depending on `:app`: Task 6.
- Startup Harness budget and evidence: Task 7.

Risk notes:

- `StartupTelemetry` is an object because `attachBaseContext()` runs before Hilt injection. Keep it small and reset it in tests.
- Do not move `StartupBackupRestore.applyIfPending()` out of `attachBaseContext()`.
- Do not remove legacy `main_activity_create_start` and `edge_to_edge_enabled` events because existing smoke docs reference them.
- Do not let `:player`, `:plugin`, `:data`, or `:updater` depend on `:app`.
