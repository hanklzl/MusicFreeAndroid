package com.zili.android.musicfreeandroid.logging

object MfLog : MfLogger {
    @Volatile
    private var delegate: MfLogger = NoOpLogger

    @Volatile
    private var paritySink: ParityEventSink? = null

    fun install(logger: MfLogger) {
        delegate = logger
    }

    fun resetForTest() {
        delegate = NoOpLogger
        paritySink = null
    }

    /**
     * 启用 parity event sink,把 MfLog 事件按 parity taxonomy 额外输出为单行 JSON。
     * 仅 parity-audit agent 抓取需要时启用(例如 BuildConfig.DEBUG + 环境变量 PARITY_AUDIT=1)。
     * 默认 emitter 用 `android.util.Log.println(INFO, TAG, json)`。
     *
     * Spec: docs/superpowers/specs/2026-05-15-parity-audit-agent-design.md §4.2
     */
    fun enableParitySink(
        emitter: (String, String) -> Unit = { tag, json ->
            android.util.Log.println(android.util.Log.INFO, tag, json)
        },
    ) {
        paritySink = ParityEventSink(emitter)
    }

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        delegate.trace(category, event, fields)
        paritySink?.emit(event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        delegate.detail(category, event, fields)
        paritySink?.emit(event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        delegate.error(category, event, throwable, fields)
        paritySink?.let { sink ->
            val enriched = if (throwable != null && "message" !in fields) {
                fields + ("message" to throwable.message.orEmpty())
            } else {
                fields
            }
            sink.emit(event, enriched)
        }
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
    ) {
        Unit
    }

    override fun flush() = Unit
}
