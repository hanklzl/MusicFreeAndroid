package com.hank.musicfree.startup

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartupTelemetryTest {
    private val logger = RecordingLogger()

    @Before
    fun setUp() {
        logger.events.clear()
    }

    @After
    fun tearDown() {
        StartupTelemetry.resetForTest()
        MfLog.resetForTest()
    }

    @Test
    fun `first Activity create is cold process_start with id-0 and id-1 launch index 1`() {
        var time = 1_000_000L
        var id = 0
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { time += 1_000_000L; time },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)
        StartupTelemetry.markLoggingReady()

        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()

        val session = StartupTelemetry.beginActivityCreate()
        session.logInstant(
            event = "app_startup_activity_create_start",
            phase = "activity_create",
            result = LogFields.Result.SUCCESS,
        )

        val event = logger.events.single { it.event == "app_startup_activity_create_start" }
        assertEquals(LogCategory.APP, event.category)
        assertEquals("id-0", event.fields["processStartId"])
        assertEquals("id-1", event.fields["activityStartId"])
        assertEquals("id-1", event.fields["startupId"])
        assertEquals(1, event.fields["activityLaunchIndex"])
        assertEquals(true, event.fields["isFirstActivityInProcess"])
        assertEquals(StartupTelemetry.STARTUP_TYPE_COLD_PROCESS, event.fields["startupType"])
        assertEquals("activity_create", event.fields["phase"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
    }

    @Test
    fun `second Activity create uses warm_activity_recreate with launch index 2`() {
        var time = 2_000_000L
        var id = 0
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { time += 1_000_000L; time },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)
        StartupTelemetry.markLoggingReady()

        val firstSession = StartupTelemetry.beginActivityCreate()
        firstSession.logInstant(
            event = "app_startup_activity_create_start",
            phase = "activity_create",
            result = LogFields.Result.SUCCESS,
        )
        val secondSession = StartupTelemetry.beginActivityCreate()
        secondSession.logInstant(
            event = "app_startup_activity_create_start",
            phase = "activity_create",
            result = LogFields.Result.SUCCESS,
        )

        val event = logger.events
            .filter { it.event == "app_startup_activity_create_start" }
            .last()
        assertEquals("id-0", event.fields["processStartId"])
        assertEquals("id-2", event.fields["activityStartId"])
        assertEquals(2, event.fields["activityLaunchIndex"])
        assertEquals(false, event.fields["isFirstActivityInProcess"])
        assertEquals(StartupTelemetry.STARTUP_TYPE_WARM_ACTIVITY_RECREATE, event.fields["startupType"])
        assertEquals(secondSession.startupType, StartupTelemetry.STARTUP_TYPE_WARM_ACTIVITY_RECREATE)
    }

    @Test
    fun `recordActivityResume skips first call and records second call with startupType and ids`() {
        var id = 0
        StartupTelemetry.resetForTest(
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)

        val session = StartupTelemetry.beginActivityCreate()
        StartupTelemetry.recordActivityResume(session)
        StartupTelemetry.recordActivityResume(session)
        StartupTelemetry.markLoggingReady()

        assertEquals(1, logger.events.size)
        assertEquals(
            setOf("app_startup_activity_resume_existing"),
            logger.events.map { it.event }.toSet(),
        )
        val event = logger.events.last { it.event == "app_startup_activity_resume_existing" }
        assertEquals(LogCategory.APP, event.category)
        assertEquals("activity_resume_existing", event.fields["startupType"])
        assertEquals("id-0", event.fields["processStartId"])
        assertEquals("id-1", event.fields["activityStartId"])
        assertEquals(1, event.fields["activityLaunchIndex"])
        assertEquals(true, event.fields["isFirstActivityInProcess"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
    }

    @Test
    fun `recordActivityResume concurrent repeated calls skip only first call`() {
        var id = 0
        StartupTelemetry.resetForTest(
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)
        val repeatCount = 12

        val session = StartupTelemetry.beginActivityCreate()
        val jobs = (0 until repeatCount).map {
            thread { StartupTelemetry.recordActivityResume(session) }
        }
        jobs.forEach { it.join() }

        StartupTelemetry.markLoggingReady()
        assertEquals(repeatCount - 1, logger.events.count { it.event == "app_startup_activity_resume_existing" })
        assertEquals(0, logger.events.count { it.event == "app_startup_ready" })
    }

    @Test
    fun `completeFlow is idempotent and counts failure only once per token`() {
        var id = 0
        var tick = 1_000_000L
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { tick.also { tick += 1_000_000L } },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)

        val flow = StartupTelemetry.startFlow("startup_recover")
        StartupTelemetry.completeFlow(
            token = flow,
            result = LogFields.Result.FAILURE,
            reason = LogFields.Reason.UNKNOWN,
        )
        StartupTelemetry.completeFlow(
            token = flow,
            result = LogFields.Result.FAILURE,
            reason = LogFields.Reason.UNKNOWN,
        )

        StartupTelemetry.markLoggingReady()
        StartupTelemetry.markStartupReady()

        val failedEvents = logger.events.filter { it.event == "startup_flow_failed" }
        assertEquals(1, failedEvents.size)
        assertEquals(1L, logger.events.single { it.event == "app_startup_ready" }.fields["failedFlowCount"])
    }

    @Test
    fun `markContentSet and markFirstFrame without current activity session return false`() {
        StartupTelemetry.resetForTest()
        MfLog.install(logger)
        val contentSetSpan = StartupTelemetry.startApplicationPhase("activity_content_set")
        val firstFrameSpan = StartupTelemetry.startApplicationPhase("first_frame")

        assertFalse(StartupTelemetry.markContentSet(span = contentSetSpan, result = LogFields.Result.SUCCESS))
        assertFalse(StartupTelemetry.markFirstFrame(span = firstFrameSpan, result = LogFields.Result.SUCCESS))
        assertEquals(0, logger.events.size)
    }

    @Test
    fun `pending application events flush once without emitting startup ready`() {
        var id = 0
        StartupTelemetry.resetForTest(
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)

        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.completeApplicationStartup(
            extraFields = mapOf("extra" to "value"),
        )

        val pendingRestoreSpan = StartupTelemetry.startApplicationPhase("pending_restore")
        StartupTelemetry.completeApplicationPhase(
            event = "app_startup_pending_restore_complete",
            phase = "pending_restore",
            span = pendingRestoreSpan,
            result = LogFields.Result.SUCCESS,
            extraFields = mapOf("pending" to true),
        )

        StartupTelemetry.markLoggingReady()
        assertEquals(
            listOf(
                "app_startup_process_start",
                "app_startup_pending_restore_complete",
            ),
            logger.events.map { it.event },
        )
        StartupTelemetry.markLoggingReady()
        assertEquals(2, logger.events.size)
    }

    @Test
    fun `activity content set and first frame mark APIs emit terminal events with durationMs`() {
        var id = 0
        var tick = 1_000_000L
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { tick.also { tick += 1_000_000L } },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)
        StartupTelemetry.markLoggingReady()

        val session = StartupTelemetry.beginActivityCreate()
        val contentSetSpan = session.startPhase("activity_content_set")
        StartupTelemetry.markContentSet(
            session = session,
            span = contentSetSpan,
            result = LogFields.Result.SUCCESS,
        )

        val firstFrameSpan = session.startPhase("first_frame")
        StartupTelemetry.markFirstFrame(
            session = session,
            span = firstFrameSpan,
            result = LogFields.Result.SUCCESS,
        )

        val contentSetEvent = logger.events.single { it.event == "app_startup_activity_content_set" }
        val firstFrameEvent = logger.events.single { it.event == "app_startup_first_frame" }
        assertEquals("activity_content_set", contentSetEvent.fields["phase"])
        assertEquals("first_frame", firstFrameEvent.fields["phase"])
        assertEquals("id-0", contentSetEvent.fields["processStartId"])
        assertEquals("id-1", contentSetEvent.fields["activityStartId"])
        assertEquals(1L, contentSetEvent.fields["durationMs"])
        assertEquals(1L, firstFrameEvent.fields["durationMs"])
        assertEquals(LogFields.Result.SUCCESS, firstFrameEvent.fields["result"])
    }

    @Test
    fun `markApplicationPhase uses stable startup app_startup_ prefix`() {
        var tick = 3_000_000L
        var id = 0
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { tick.also { tick += 1_000_000L } },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)
        StartupTelemetry.markLoggingReady()

        val span = StartupTelemetry.startApplicationPhase("pending_restore")
        StartupTelemetry.markApplicationPhase(
            phase = "pending_restore",
            span = span,
            result = LogFields.Result.SUCCESS,
            extraFields = mapOf("targetCount" to 5),
        )

        val event = logger.events.single { it.event == "app_startup_pending_restore" }
        assertEquals("pending_restore", event.fields["phase"])
        assertEquals(5, event.fields["targetCount"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
    }

    @Test
    fun `startup flow start and complete should log and count completed flows in ready aggregate`() {
        var id = 0
        var tick = 2_000_000L
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { tick.also { tick += 1_000_000L } },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)

        val session = StartupTelemetry.beginActivityCreate()
        session.logInstant(
            event = "app_startup_activity_create_start",
            phase = "activity_create",
            result = LogFields.Result.SUCCESS,
        )
        val flow = StartupTelemetry.startFlow(
            flowName = "runtime_restore",
            extraFields = mapOf("targetCount" to 3),
        )
        StartupTelemetry.completeFlow(
            token = flow,
            result = LogFields.Result.SUCCESS,
            extraFields = mapOf("targetCount" to 3),
        )

        StartupTelemetry.markLoggingReady()
        StartupTelemetry.markStartupReady()

        val flowStarts = logger.events.filter { it.event == "startup_flow_start" }
        if (flowStarts.size != 1) {
            throw AssertionError("flowStarts size=${flowStarts.size}, all=${logger.events.map { it.event }}")
        }
        val flowCompleteEvents = logger.events.filter { it.event == "startup_flow_complete" }
        if (flowCompleteEvents.size != 1) {
            throw AssertionError("flowComplete size=${flowCompleteEvents.size}, all=${logger.events.map { it.event }}")
        }
        val flowStart = flowStarts.single()
        val flowComplete = flowCompleteEvents.single()
        assertEquals(LogCategory.APP, flowStart.category)
        assertEquals("runtime_restore", flowStart.fields["flowName"])
        assertEquals(3, flowStart.fields["targetCount"])
        assertEquals(LogCategory.APP, flowComplete.category)
        assertEquals("runtime_restore", flowComplete.fields["flowName"])
        assertEquals(3, flowComplete.fields["targetCount"])
        assertEquals(1L, flowComplete.fields["durationMs"])
        assertEquals(LogFields.Result.SUCCESS, flowComplete.fields["result"])
        assertEquals(session.processStartId, flowComplete.fields["processStartId"])
        assertEquals("id-1", flowComplete.fields["startupId"])

        val readyEvents = logger.events.filter { it.event == "app_startup_ready" }
        if (readyEvents.size != 1) {
            throw AssertionError("readyEvents size=${readyEvents.size}, all=${logger.events.map { it.event }}")
        }
        val readyEvent = readyEvents[0]
        assertEquals(1L, readyEvent.fields["flowCount"])
        assertEquals(0L, readyEvent.fields["failedFlowCount"])
    }

    @Test
    fun `flow terminal event maps result and failed count for failure skipped and cancelled`() {
        var id = 0
        var tick = 2_000_000L
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { tick.also { tick += 1_000_000L } },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)

        val failure = StartupTelemetry.startFlow("runtime_restore")
        StartupTelemetry.completeFlow(
            token = failure,
            result = LogFields.Result.FAILURE,
            reason = LogFields.Reason.UNKNOWN,
            extraFields = mapOf("errorCode" to 500),
        )

        val skipped = StartupTelemetry.startFlow("config_sync")
        StartupTelemetry.completeFlow(
            token = skipped,
            result = LogFields.Result.SKIPPED,
            reason = LogFields.Reason.DUPLICATE,
        )

        val cancelled = StartupTelemetry.startFlow("playback_resume")
        StartupTelemetry.completeFlow(
            token = cancelled,
            result = LogFields.Result.CANCELLED,
            reason = LogFields.Reason.CANCELLED,
        )

        StartupTelemetry.markLoggingReady()
        StartupTelemetry.markStartupReady()

        val failureEvent = logger.events.single { it.event == "startup_flow_failed" }
        val skippedEvent = logger.events.single { it.event == "startup_flow_skipped" }
        val cancelledEvent = logger.events.single { it.event == "startup_flow_cancelled" }
        assertEquals(LogFields.Result.FAILURE, failureEvent.fields["result"])
        assertEquals(LogFields.Reason.UNKNOWN, failureEvent.fields["reason"])
        assertEquals(LogFields.Result.SKIPPED, skippedEvent.fields["result"])
        assertEquals(LogFields.Result.CANCELLED, cancelledEvent.fields["result"])

        val readyEvent = logger.events.single { it.event == "app_startup_ready" }
        assertEquals(3L, readyEvent.fields["failedFlowCount"])
        assertEquals(3L, readyEvent.fields["flowCount"])
    }

    @Test
    fun `ready event keeps stable timing fields and pending flush still emits ready once`() {
        var tick = 1_000_000L
        var id = 0
        StartupTelemetry.resetForTest(
            nanoTimeProvider = { tick.also { tick += 1_000_000L } },
            idProvider = { "id-${id++}" },
        )
        MfLog.install(logger)

        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()
        StartupTelemetry.completeApplicationStartup(extraFields = mapOf("startupStage" to "cold"))

        val pending = StartupTelemetry.startApplicationPhase("pending_restore")
        StartupTelemetry.completeApplicationPhase(
            event = "app_startup_pending_restore_complete",
            phase = "pending_restore",
            span = pending,
            result = LogFields.Result.SUCCESS,
        )

        val flow = StartupTelemetry.startFlow("runtime_restore")
        StartupTelemetry.completeFlow(flow, result = LogFields.Result.SUCCESS)
        val failed = StartupTelemetry.startFlow("content_sync")
        StartupTelemetry.completeFlow(
            token = failed,
            result = LogFields.Result.FAILURE,
            reason = "restore_error",
        )

        val session = StartupTelemetry.beginActivityCreate()
        val content = session.startPhase("activity_content_set")
        StartupTelemetry.markContentSet(content, result = LogFields.Result.SUCCESS)
        val firstFrame = session.startPhase("first_frame")
        StartupTelemetry.markFirstFrame(firstFrame, result = LogFields.Result.SUCCESS)

        StartupTelemetry.markLoggingReady()

        val ready = logger.events.single { it.event == "app_startup_ready" }
        assertEquals("startup", ready.fields["operation"])
        assertEquals("id-1", ready.fields["startupId"])
        assertEquals("id-0", ready.fields["processStartId"])
        assertEquals("id-1", ready.fields["activityStartId"])
        assertEquals(1, ready.fields["activityLaunchIndex"])
        assertEquals(true, ready.fields["isFirstActivityInProcess"])
        assertEquals(StartupTelemetry.STARTUP_TYPE_COLD_PROCESS, ready.fields["startupType"])
        assertTrue(ready.fields["applicationAgeMs"] is Long)
        assertTrue(ready.fields["activityAgeMs"] is Long)
        assertTrue(ready.fields["contentSetMs"] is Long)
        assertTrue(ready.fields["firstFrameMs"] is Long)
        assertTrue(ready.fields["readyMs"] is Long)
        assertTrue(ready.fields["flowCount"] is Long)
        assertTrue(ready.fields["failedFlowCount"] is Long)
        assertEquals(2L, ready.fields["flowCount"])
        assertEquals(1L, ready.fields["failedFlowCount"])
    }

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
        val throwable: Throwable? = null,
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
            events += RecordedLogEvent(category, event, fields, throwable)
        }

        override fun flush() = Unit
    }
}
