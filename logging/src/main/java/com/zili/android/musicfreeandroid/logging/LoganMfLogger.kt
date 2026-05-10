package com.zili.android.musicfreeandroid.logging

import com.dianping.logan.Logan

class LoganMfLogger(
    private val sessionId: String,
) : MfLogger {
    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        write(
            level = LogLevel.TRACE,
            category = category,
            event = event,
            fields = fields,
            throwable = null,
            result = null,
        )
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        write(
            level = LogLevel.DETAIL,
            category = category,
            event = event,
            fields = fields,
            throwable = null,
            result = null,
        )
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        write(
            level = LogLevel.ERROR,
            category = category,
            event = event,
            fields = fields,
            throwable = throwable,
            result = "failure",
        )
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
