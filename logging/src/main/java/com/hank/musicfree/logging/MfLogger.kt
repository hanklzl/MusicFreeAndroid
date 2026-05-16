package com.hank.musicfree.logging

interface MfLogger {
    fun trace(
        category: LogCategory,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    )

    fun detail(
        category: LogCategory,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    )

    fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable? = null,
        fields: Map<String, Any?> = emptyMap(),
    )

    fun flush()
}
