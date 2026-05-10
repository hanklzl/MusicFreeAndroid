package com.zili.android.musicfreeandroid.logging

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
    ) {
        Unit
    }

    override fun flush() = Unit
}
