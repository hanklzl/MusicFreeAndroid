package com.hank.musicfree.logging

object RuntimeLogFields {
    const val OP_RESTORE = "runtime_restore"
    const val OP_PERSIST = "runtime_snapshot_persist"

    fun restoreStart(store: String, key: String): Map<String, Any?> = base(
        operation = OP_RESTORE,
        store = store,
        key = key,
        result = null,
        durationMs = null,
        reason = null,
    )

    fun restoreTerminal(
        store: String,
        key: String,
        result: String,
        durationMs: Long,
        reason: String?,
    ): Map<String, Any?> = base(
        operation = OP_RESTORE,
        store = store,
        key = key,
        result = result,
        durationMs = durationMs,
        reason = reason,
    )

    fun persistStart(store: String, key: String): Map<String, Any?> = base(
        operation = OP_PERSIST,
        store = store,
        key = key,
        result = null,
        durationMs = null,
        reason = null,
    )

    fun persistTerminal(
        store: String,
        key: String,
        result: String,
        durationMs: Long,
        reason: String?,
    ): Map<String, Any?> = base(
        operation = OP_PERSIST,
        store = store,
        key = key,
        result = result,
        durationMs = durationMs,
        reason = reason,
    )

    private fun base(
        operation: String,
        store: String,
        key: String,
        result: String?,
        durationMs: Long?,
        reason: String?,
    ): Map<String, Any?> = buildMap {
        put(LogFields.operation(operation).first, operation)
        put("store", store)
        put("key", key)
        if (result != null) put(LogFields.result(result).first, result)
        if (durationMs != null) put("durationMs", durationMs)
        if (reason != null) put(LogFields.reason(reason).first, reason)
    }
}
