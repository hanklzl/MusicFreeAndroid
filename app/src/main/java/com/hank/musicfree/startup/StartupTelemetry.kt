package com.hank.musicfree.startup

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import java.util.ArrayDeque
import java.util.UUID

object StartupTelemetry {
    const val STARTUP_TYPE_COLD_PROCESS = "cold_process_start"
    const val STARTUP_TYPE_WARM_ACTIVITY_RECREATE = "warm_activity_recreate"
    const val STARTUP_TYPE_ACTIVITY_RESUME_EXISTING = "activity_resume_existing"

    private const val FIELD_OPERATION = "operation"
    private const val FIELD_STARTUP_TYPE = "startupType"
    private const val FIELD_STARTUP_ID = "startupId"
    private const val FIELD_PROCESS_START_ID = "processStartId"
    private const val FIELD_ACTIVITY_START_ID = "activityStartId"
    private const val FIELD_ACTIVITY_LAUNCH_INDEX = "activityLaunchIndex"
    private const val FIELD_IS_FIRST_ACTIVITY_IN_PROCESS = "isFirstActivityInProcess"
    private const val FIELD_PHASE = "phase"
    private const val FIELD_FLOW_NAME = "flowName"
    private const val FIELD_DURATION_MS = "durationMs"
    private const val FIELD_RESULT = "result"
    private const val FIELD_REASON = "reason"
    private const val FIELD_APPLICATION_AGE_MS = "applicationAgeMs"
    private const val FIELD_ACTIVITY_AGE_MS = "activityAgeMs"
    private const val FIELD_CONTENT_SET_MS = "contentSetMs"
    private const val FIELD_FIRST_FRAME_MS = "firstFrameMs"
    private const val FIELD_READY_MS = "readyMs"
    private const val FIELD_FLOW_COUNT = "flowCount"
    private const val FIELD_FAILED_FLOW_COUNT = "failedFlowCount"
    private const val OPERATION_STARTUP = "startup"

    private const val PHASE_ACTIVITY_CONTENT_SET = "activity_content_set"
    private const val PHASE_FIRST_FRAME = "first_frame"

    private const val EVENT_APP_STARTUP_PROCESS_START = "app_startup_process_start"
    private const val EVENT_ACTIVITY_RESUME_EXISTING = "app_startup_activity_resume_existing"
    private const val EVENT_STARTUP_FLOW_START = "startup_flow_start"
    private const val EVENT_STARTUP_FLOW_COMPLETE = "startup_flow_complete"
    private const val EVENT_STARTUP_FLOW_FAILED = "startup_flow_failed"
    private const val EVENT_STARTUP_FLOW_CANCELLED = "startup_flow_cancelled"
    private const val EVENT_STARTUP_FLOW_SKIPPED = "startup_flow_skipped"
    private const val EVENT_ACTIVITY_CONTENT_SET = "app_startup_activity_content_set"
    private const val EVENT_FIRST_FRAME = "app_startup_first_frame"
    private const val EVENT_APP_STARTUP_READY = "app_startup_ready"

    private const val NOISE_LIMIT = 16
    private const val FLOW_CONTEXT_LIMIT = 64

    private data class PendingLogEvent(val event: String, val fields: Map<String, Any?>)

    private val lock = Any()
    private var nanoTimeProvider = { System.nanoTime() }
    private var idProvider = { UUID.randomUUID().toString() }
    private var loggingReady = false
    private var pendingFlushed = false
    private var readyEmitted = false
    private val pendingEvents = ArrayDeque<PendingLogEvent>()
    private val pendingFlowContexts = LinkedHashMap<StartupFlowToken, FlowContext>()

    private var processStartId = ""
    private var activityLaunchIndex = 0
    private var attachBaseContextSpan: StartupSpan? = null
    private var applicationOnCreateSpan: StartupSpan? = null
    private var currentActivitySession: StartupActivitySession? = null
    private var processStartedAtNano = 0L
    private var totalFlowCount = 0
    private var totalFailedFlowCount = 0

    init {
        resetForTest()
    }

    private fun now() = nanoTimeProvider()
    private fun toDurationMs(startedAtNano: Long): Long = (now() - startedAtNano) / 1_000_000L

    fun resetForTest(
        nanoTimeProvider: () -> Long = { System.nanoTime() },
        idProvider: () -> String = { UUID.randomUUID().toString() },
    ) {
        synchronized(lock) {
            this.nanoTimeProvider = nanoTimeProvider
            this.idProvider = idProvider
            loggingReady = false
            pendingFlushed = false
            readyEmitted = false
            pendingEvents.clear()
            pendingFlowContexts.clear()
            processStartId = this.idProvider()
            activityLaunchIndex = 0
            attachBaseContextSpan = null
            applicationOnCreateSpan = null
            currentActivitySession = null
            processStartedAtNano = 0L
            totalFlowCount = 0
            totalFailedFlowCount = 0
        }
    }

    fun attachBaseContextStart(): StartupSpan = synchronized(lock) {
        val span = StartupSpan("attachBaseContext", now())
        processStartedAtNano = span.startedAtNano
        attachBaseContextSpan = span
        span
    }

    fun applicationOnCreateStart(): StartupSpan = synchronized(lock) {
        val span = StartupSpan("applicationOnCreate", now())
        applicationOnCreateSpan = span
        span
    }

    fun startApplicationPhase(name: String): StartupSpan = synchronized(lock) {
        StartupSpan(name, now())
    }

    fun completeApplicationPhase(
        event: String,
        phase: String,
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val fields = startupContextFields(
            phase = phase,
            durationMs = toDurationMs(span.startedAtNano),
            result = result,
            reason = reason,
            extraFields = extraFields,
        )
        emitOrQueueLog(event, fields)
    }

    fun markApplicationPhase(
        phase: String,
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        completeApplicationPhase(
            event = "app_startup_$phase",
            phase = phase,
            span = span,
            result = result,
            reason = reason,
            extraFields = extraFields,
        )
    }

    fun completeApplicationStartup(
        result: String = LogFields.Result.SUCCESS,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val startedAt = synchronized(lock) { applicationOnCreateSpan?.startedAtNano }
        val durationMs = startedAt?.let { toDurationMs(it) }
        val fields = startupContextFields(
            phase = "process",
            durationMs = durationMs,
            result = result,
            reason = reason,
            extraFields = extraFields,
        )
        emitOrQueueLog(EVENT_APP_STARTUP_PROCESS_START, fields)
    }

    fun markLoggingReady(durationMs: Long? = null, extraFields: Map<String, Any?> = emptyMap()) {
        val queue = synchronized(lock) {
            loggingReady = true
            if (pendingFlushed) {
                emptyList()
            } else {
                pendingFlushed = true
                buildList {
                    while (pendingEvents.isNotEmpty()) {
                        add(pendingEvents.removeFirst())
                    }
                }
            }
        }
        queue.forEach { emitEventNow(it.event, it.fields) }

        if (durationMs != null || extraFields.isNotEmpty()) {
            val fields = startupContextFields(
                phase = "logging_initialized",
                durationMs = durationMs,
                result = LogFields.Result.SUCCESS,
                extraFields = extraFields,
            )
            emitOrQueueLog("app_startup_logging_initialized", fields)
        }
    }

    fun markStartupReady(extraFields: Map<String, Any?> = emptyMap()): Boolean {
        val shouldEmit = synchronized(lock) {
            if (readyEmitted) {
                false
            } else {
                readyEmitted = true
                true
            }
        }
        if (!shouldEmit) return false
        emitOrQueueLog(EVENT_APP_STARTUP_READY, startupReadyContextFields(extraFields))
        return true
    }

    fun beginActivityCreate(): StartupActivitySession = synchronized(lock) {
        activityLaunchIndex += 1
        val startupType = if (activityLaunchIndex == 1) {
            STARTUP_TYPE_COLD_PROCESS
        } else {
            STARTUP_TYPE_WARM_ACTIVITY_RECREATE
        }
        val startupId = idProvider()
        val session = StartupActivitySession(
            startupId = startupId,
            processStartId = processStartId,
            activityStartId = startupId,
            activityLaunchIndex = activityLaunchIndex,
            startupType = startupType,
            isFirstActivityInProcess = activityLaunchIndex == 1,
            startedAtNano = now(),
        )
        currentActivitySession = session
        session
    }

    fun recordActivityResume(session: StartupActivitySession) {
        if (!session.shouldRecordResume()) return
        val fields = session.startupContextFieldsWithPhase(
            phase = "activity_resume_existing",
            durationMs = null,
            result = LogFields.Result.SUCCESS,
            startupTypeOverride = STARTUP_TYPE_ACTIVITY_RESUME_EXISTING,
        )
        emitOrQueueLog(EVENT_ACTIVITY_RESUME_EXISTING, fields)
    }

    fun startFlow(
        flowName: String,
        extraFields: Map<String, Any?> = emptyMap(),
    ): StartupFlowToken = synchronized(lock) {
        totalFlowCount += 1
        val context = currentContext()
        val token = StartupFlowToken(
            flowName = flowName,
            startedAtNano = now(),
            processStartId = processStartId,
        )
        while (pendingFlowContexts.size >= FLOW_CONTEXT_LIMIT) {
            pendingFlowContexts.keys.firstOrNull()?.let { oldestKey ->
                pendingFlowContexts.remove(oldestKey)
            } ?: break
        }
        pendingFlowContexts[token] = FlowContext(
            startupId = context.startupId,
            startupType = context.startupType,
            processStartId = context.processStartId,
            activityStartId = context.activityStartId,
            activityLaunchIndex = context.activityLaunchIndex,
            isFirstActivityInProcess = context.isFirstActivityInProcess,
        )
        val fields = startupContextFields(
            context = context,
            flowName = flowName,
            extraFields = extraFields,
        )
        emitOrQueueLog(EVENT_STARTUP_FLOW_START, fields)
        token
    }

    fun completeFlow(
        token: StartupFlowToken,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val context = synchronized(lock) {
            pendingFlowContexts.remove(token)?.let {
                CurrentContext(
                    startupType = it.startupType,
                    startupId = it.startupId,
                    processStartId = it.processStartId,
                    activityStartId = it.activityStartId,
                    activityLaunchIndex = it.activityLaunchIndex,
                    isFirstActivityInProcess = it.isFirstActivityInProcess,
                )
            } ?: return
        }
        val terminalEvent = when (result) {
            LogFields.Result.SKIPPED -> {
                increaseFailedFlowCount()
                EVENT_STARTUP_FLOW_SKIPPED
            }
            LogFields.Result.FAILURE -> {
                increaseFailedFlowCount()
                EVENT_STARTUP_FLOW_FAILED
            }
            LogFields.Result.CANCELLED -> {
                increaseFailedFlowCount()
                EVENT_STARTUP_FLOW_CANCELLED
            }
            else -> EVENT_STARTUP_FLOW_COMPLETE
        }
        val fields = startupContextFields(
            context = context.copy(processStartId = token.processStartId, startupId = context.startupId),
            flowName = token.flowName,
            durationMs = (now() - token.startedAtNano) / 1_000_000L,
            result = result,
            reason = reason,
            extraFields = extraFields,
        )
        emitOrQueueLog(terminalEvent, fields)
    }

    fun markContentSet(
        session: StartupActivitySession,
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        session.markContentSet(span = span, result = result, reason = reason, extraFields = extraFields)
    }

    fun markContentSet(
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Boolean {
        val session = currentActivitySession() ?: return false
        markContentSet(session = session, span = span, result = result, reason = reason, extraFields = extraFields)
        return true
    }

    fun markFirstFrame(
        session: StartupActivitySession,
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        session.markFirstFrame(span = span, result = result, reason = reason, extraFields = extraFields)
    }

    fun markFirstFrame(
        span: StartupSpan,
        result: String,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Boolean {
        val session = currentActivitySession() ?: return false
        markFirstFrame(session = session, span = span, result = result, reason = reason, extraFields = extraFields)
        return true
    }

    fun startupContextFields(
        flowName: String? = null,
        phase: String? = null,
        durationMs: Long? = null,
        result: String? = null,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val context = currentContext()
        return startupContextFields(
            context = context,
            flowName = flowName,
            phase = phase,
            durationMs = durationMs,
            result = result,
            reason = reason,
            extraFields = extraFields,
        )
    }

    private fun increaseFailedFlowCount() = synchronized(lock) {
        totalFailedFlowCount += 1
    }

    private fun startupReadyContextFields(extraFields: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val snapshot = synchronized(lock) {
            val readyNano = now()
            val context = currentContext()
            val session = currentActivitySession
            val sessionStartedAtNano = session?.startedAtNano
            val applicationStartedAtNano = applicationOnCreateSpan?.startedAtNano
                ?: attachBaseContextSpan?.startedAtNano
            val processStartedAt = processStartedAtNano.takeIf { it > 0L } ?: readyNano
            val (contentSetMs, firstFrameMs) = session?.contentTimingSnapshot() ?: (0L to 0L)
            val applicationAgeMs = if (applicationOnCreateSpan == null && attachBaseContextSpan == null) {
                0L
            } else {
                val applicationEndNano = applicationStartedAtNano ?: readyNano
                (readyNano - applicationEndNano).coerceAtLeast(0L) / 1_000_000L
            }
            val activityAgeMs = if (session == null || sessionStartedAtNano == null) {
                0L
            } else {
                (readyNano - sessionStartedAtNano).coerceAtLeast(0L) / 1_000_000L
            }
            val fields = LinkedHashMap<String, Any?>(extraFields.size + 16)
            fields[FIELD_OPERATION] = OPERATION_STARTUP
            fields[FIELD_STARTUP_TYPE] = context.startupType
            fields[FIELD_STARTUP_ID] = context.startupId
            fields[FIELD_PROCESS_START_ID] = context.processStartId
            fields[FIELD_ACTIVITY_START_ID] = context.activityStartId
            fields[FIELD_ACTIVITY_LAUNCH_INDEX] = context.activityLaunchIndex
            fields[FIELD_IS_FIRST_ACTIVITY_IN_PROCESS] = context.isFirstActivityInProcess
            fields[FIELD_APPLICATION_AGE_MS] = applicationAgeMs
            fields[FIELD_ACTIVITY_AGE_MS] = activityAgeMs
            fields[FIELD_CONTENT_SET_MS] = contentSetMs
            fields[FIELD_FIRST_FRAME_MS] = firstFrameMs
            fields[FIELD_READY_MS] = (readyNano - processStartedAt).coerceAtLeast(0L) / 1_000_000L
            fields[FIELD_FLOW_COUNT] = totalFlowCount.toLong()
            fields[FIELD_FAILED_FLOW_COUNT] = totalFailedFlowCount.toLong()
            fields.putAll(extraFields)
            fields
        }
        return snapshot
    }

    private fun startupContextFields(
        context: CurrentContext,
        flowName: String? = null,
        phase: String? = null,
        durationMs: Long? = null,
        result: String? = null,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val fields = LinkedHashMap<String, Any?>(extraFields.size + 12)
        fields[FIELD_OPERATION] = OPERATION_STARTUP
        fields[FIELD_STARTUP_TYPE] = context.startupType
        fields[FIELD_STARTUP_ID] = context.startupId
        fields[FIELD_PROCESS_START_ID] = context.processStartId
        fields[FIELD_ACTIVITY_START_ID] = context.activityStartId
        fields[FIELD_ACTIVITY_LAUNCH_INDEX] = context.activityLaunchIndex
        fields[FIELD_IS_FIRST_ACTIVITY_IN_PROCESS] = context.isFirstActivityInProcess
        if (phase != null) {
            fields[FIELD_PHASE] = phase
        }
        if (flowName != null) {
            fields[FIELD_FLOW_NAME] = flowName
        }
        if (durationMs != null) {
            fields[FIELD_DURATION_MS] = durationMs
        }
        if (result != null) {
            fields[FIELD_RESULT] = result
        }
        if (reason != null) {
            fields[FIELD_REASON] = reason
        }
        fields.putAll(extraFields)
        return fields
    }

    private fun currentContext(
        startupType: String? = null,
        startupId: String? = null,
        processStartId: String? = null,
        activityStartId: String? = null,
        activityLaunchIndex: Int? = null,
        isFirstActivityInProcess: Boolean? = null,
    ): CurrentContext = synchronized(lock) {
        CurrentContext(
            startupType = startupType ?: currentActivitySession?.startupType ?: STARTUP_TYPE_COLD_PROCESS,
            startupId = startupId ?: currentActivitySession?.startupId ?: this.processStartId,
            processStartId = processStartId ?: this.processStartId,
            activityStartId = activityStartId ?: currentActivitySession?.activityStartId.orEmpty(),
            activityLaunchIndex = activityLaunchIndex ?: currentActivitySession?.activityLaunchIndex ?: 0,
            isFirstActivityInProcess = isFirstActivityInProcess
                ?: currentActivitySession?.isFirstActivityInProcess ?: false,
        )
    }

    private fun currentActivitySession(): StartupActivitySession? = synchronized(lock) {
        currentActivitySession
    }

    private data class CurrentContext(
        val startupType: String,
        val startupId: String,
        val processStartId: String,
        val activityStartId: String,
        val activityLaunchIndex: Int,
        val isFirstActivityInProcess: Boolean,
    )

    private data class FlowContext(
        val startupId: String,
        val startupType: String,
        val processStartId: String,
        val activityStartId: String,
        val activityLaunchIndex: Int,
        val isFirstActivityInProcess: Boolean,
    )

    private fun emitOrQueueLog(event: String, fields: Map<String, Any?>) {
        val shouldQueue = synchronized(lock) {
            if (loggingReady) {
                false
            } else {
                if (pendingEvents.size >= NOISE_LIMIT) {
                    pendingEvents.removeFirst()
                }
                pendingEvents.addLast(PendingLogEvent(event, fields))
                true
            }
        }
        if (!shouldQueue) {
            emitEventNow(event, fields)
        }
    }

    private fun emitEventNow(event: String, fields: Map<String, Any?>) {
        runCatching {
            MfLog.detail(LogCategory.APP, event, fields)
        }
    }

    private fun StartupActivitySession.startupContextFieldsWithPhase(
        phase: String,
        durationMs: Long? = null,
        result: String,
        startupTypeOverride: String? = null,
        reason: String? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = startupContextFields(
        flowName = null,
        phase = phase,
        durationMs = durationMs,
        result = result,
        reason = reason,
        extraFields = extraFields,
        context = currentContext(
            startupType = startupTypeOverride ?: startupType,
            startupId = startupId,
            processStartId = processStartId,
            activityStartId = activityStartId,
            activityLaunchIndex = activityLaunchIndex,
            isFirstActivityInProcess = isFirstActivityInProcess,
        ),
    )

    data class StartupSpan(val name: String, val startedAtNano: Long)

    data class StartupFlowToken(
        val flowName: String,
        val startedAtNano: Long,
        val processStartId: String,
    )

    class StartupActivitySession(
        val startupId: String,
        val processStartId: String,
        val activityStartId: String,
        val activityLaunchIndex: Int,
        val startupType: String,
        val isFirstActivityInProcess: Boolean,
        val startedAtNano: Long,
    ) {
        private val sessionLock = Any()
        private var resumeRecorded = false
        private var contentSetDurationMs: Long = 0L
        private var firstFrameDurationMs: Long = 0L

        fun startPhase(name: String): StartupSpan = StartupSpan(name, now())

        fun completePhase(
            event: String,
            phase: String,
            span: StartupSpan,
            result: String,
            reason: String? = null,
            extraFields: Map<String, Any?> = emptyMap(),
        ) {
            val durationMs = (now() - span.startedAtNano) / 1_000_000L
            when (phase) {
                PHASE_ACTIVITY_CONTENT_SET -> synchronized(sessionLock) {
                    contentSetDurationMs = durationMs
                }

                PHASE_FIRST_FRAME -> synchronized(sessionLock) {
                    firstFrameDurationMs = durationMs
                }
            }
            val fields = startupContextFields(
                context = currentContext(
                    startupId = startupId,
                    startupType = startupType,
                    processStartId = processStartId,
                    activityStartId = activityStartId,
                    activityLaunchIndex = activityLaunchIndex,
                    isFirstActivityInProcess = isFirstActivityInProcess,
                ),
                phase = phase,
                durationMs = durationMs,
                result = result,
                reason = reason,
                extraFields = extraFields,
            )
            emitOrQueueLog(event, fields)
            if (phase == PHASE_FIRST_FRAME) {
                markStartupReady()
            }
        }

        fun markContentSet(
            span: StartupSpan,
            result: String,
            reason: String? = null,
            extraFields: Map<String, Any?> = emptyMap(),
        ) {
            completePhase(
                event = EVENT_ACTIVITY_CONTENT_SET,
                phase = PHASE_ACTIVITY_CONTENT_SET,
                span = span,
                result = result,
                reason = reason,
                extraFields = extraFields,
            )
        }

        fun markFirstFrame(
            span: StartupSpan,
            result: String,
            reason: String? = null,
            extraFields: Map<String, Any?> = emptyMap(),
        ) {
            completePhase(
                event = EVENT_FIRST_FRAME,
                phase = PHASE_FIRST_FRAME,
                span = span,
                result = result,
                reason = reason,
                extraFields = extraFields,
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
            val fields = startupContextFields(
                context = currentContext(
                    startupType = startupTypeOverride ?: startupType,
                    startupId = startupId,
                    processStartId = processStartId,
                    activityStartId = activityStartId,
                    activityLaunchIndex = activityLaunchIndex,
                    isFirstActivityInProcess = isFirstActivityInProcess,
                ),
                phase = phase,
                result = result,
                reason = reason,
                extraFields = extraFields,
            )
            emitOrQueueLog(event, fields)
        }

        internal fun shouldRecordResume(): Boolean {
            synchronized(sessionLock) {
                if (!resumeRecorded) {
                    resumeRecorded = true
                    return false
                }
            }
            return true
        }

        fun contentTimingSnapshot(): Pair<Long, Long> = synchronized(sessionLock) {
            contentSetDurationMs to firstFrameDurationMs
        }
    }
}
