package com.hank.musicfree.plugin.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Log-only transformations: never modify the request, response or exception delivered to JS. */
internal object PluginHttpLogSanitizer {
    private const val REDACTED = "[redacted]"
    private const val OMITTED = "[body omitted]"
    private const val MAX_BODY_CHARS = 64 * 1024
    private val safeHeaders = setOf(
        "content-type", "content-length", "content-encoding", "accept", "accept-encoding",
        "cache-control", "date", "server", "connection", "transfer-encoding",
    )

    fun url(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val parsed = raw.toHttpUrlOrNull() ?: return "[invalid URL omitted]"
        val clean = parsed.newBuilder().username("").password("").fragment(null).query(null)
        // Signed CDN URLs use provider-specific key names. Hide every query value, not only known tokens.
        for (name in parsed.queryParameterNames) clean.addQueryParameter(name, REDACTED)
        return clean.build().toString()
    }

    fun headers(headers: Headers, maxValueChars: Int, maxChars: Int): String =
        headers.names().joinToString(",") { name ->
            val value = if (name.lowercase() in safeHeaders) {
                headers.values(name).joinToString("|") { it.take(maxValueChars) }
            } else REDACTED
            "$name=$value"
        }.take(maxChars)

    fun body(url: String?, body: String?, maxChars: Int): String? {
        if (body == null) return null
        if (isGateway(url) || body.length > MAX_BODY_CHARS) return OMITTED
        // Never truncate before parsing: a truncated token field can evade structural redaction.
        val parsed = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return OMITTED
        if (parsed !is JsonObject && parsed !is JsonArray) return OMITTED
        return sanitize(parsed, 0).toString().take(maxChars)
    }

    fun failure(throwable: Throwable?): Throwable? = throwable?.let {
        // Throwable.message/cause may contain request bodies, header values or signed URLs.
        // Preserve only the exception type, never retain the original as a cause/suppressed exception.
        IllegalStateException("HTTP request failed (${it.javaClass.simpleName})")
    }

    private fun isGateway(url: String?): Boolean {
        val path = url?.toHttpUrlOrNull()?.pathSegments?.joinToString("/", prefix = "/")?.trimEnd('/') ?: return true
        return path.endsWith("/gateway/ticket") || path.endsWith("/music/resolve-url")
    }

    private fun sensitive(key: String): Boolean {
        val normalized = key.lowercase().filter { it.isLetterOrDigit() }
        return normalized in setOf("auth", "authorization", "cookie", "setcookie", "key", "apikey", "privatekey", "signingkey", "session", "sessionid", "deviceid", "appid", "cek", "ekey") ||
            listOf("secret", "password", "passwd", "ticket", "token", "credential", "signature").any { it in normalized } ||
            normalized.startsWith("xqing") || normalized == "sign" || normalized == "finalekey"
    }

    private fun sanitize(element: JsonElement, depth: Int): JsonElement {
        if (depth > 20) return JsonPrimitive(OMITTED)
        return when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                if (sensitive(key)) JsonPrimitive(REDACTED) else sanitize(value, depth + 1)
            })
            is JsonArray -> JsonArray(element.map { sanitize(it, depth + 1) })
            is JsonPrimitive -> if (element.isString) {
                val value = element.content
                when {
                    value.startsWith("https://", true) || value.startsWith("http://", true) -> JsonPrimitive(url(value))
                    value.startsWith("Bearer ", true) || value.startsWith("Basic ", true) -> JsonPrimitive(REDACTED)
                    // An embedded JSON string is common for private plugin user variables.
                    value.trimStart().startsWith("{") || value.trimStart().startsWith("[") ->
                        JsonPrimitive(runCatching { sanitize(Json.parseToJsonElement(value), depth + 1).toString() }.getOrDefault(OMITTED))
                    else -> element
                }
            } else element
        }
    }
}
