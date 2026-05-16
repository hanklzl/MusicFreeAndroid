package com.hank.musicfree.logging

import java.net.URI

object LogFields {
    object Result {
        const val SUCCESS = "success"
        const val FAILURE = "failure"
        const val CANCELLED = "cancelled"
        const val STALE = "stale"
        const val SKIPPED = "skipped"
    }

    object Reason {
        const val CANCELLED = "cancelled"
        const val STALE_GENERATION = "stale_generation"
        const val EMPTY_INPUT = "empty_input"
        const val NOT_FOUND = "not_found"
        const val DUPLICATE = "duplicate"
        const val NETWORK_UNAVAILABLE = "network_unavailable"
        const val CELLULAR_BLOCKED = "cellular_blocked"
        const val UNSUPPORTED = "unsupported"
        const val INVALID_URL = "invalid_url"
        const val UNKNOWN = "unknown"
    }

    fun operation(name: String): Pair<String, String> = "operation" to name

    fun screen(name: String): Pair<String, String> = "screen" to name

    fun result(value: String): Pair<String, String> = "result" to value

    fun reason(value: String): Pair<String, String> = "reason" to value

    fun platform(value: String?): Pair<String, String> = "platform" to value.orEmpty()

    fun item(id: String?, name: String? = null): Map<String, Any?> = mapOf(
        "itemId" to id.orEmpty(),
        "itemName" to name.orEmpty(),
    )

    fun host(url: String?): String = runCatching {
        val parsed = URI(url ?: return "")
        parsed.host.orEmpty()
    }.getOrDefault("")

    fun preview(value: String?, maxLength: Int = 256): String {
        if (value.isNullOrEmpty()) return ""
        if (maxLength <= 0) return ""
        if (value.length <= maxLength) return value
        if (maxLength <= ELLIPSIS.length) return ELLIPSIS.take(maxLength)
        return value.take(maxLength - ELLIPSIS.length) + ELLIPSIS
    }

    private const val ELLIPSIS = "..."
}
