package com.zili.android.musicfreeandroid.logging

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.PrintWriter
import java.io.StringWriter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object LogEventFormatter {
    fun format(
        level: LogLevel,
        category: LogCategory,
        event: String,
        sessionId: String,
        traceId: String?,
        durationMs: Long?,
        result: String?,
        fields: Map<String, Any?>,
        throwable: Throwable?,
    ): String {
        val jsonObject = buildLogEvent(
            level = level,
            category = category,
            event = event,
            sessionId = sessionId,
            traceId = traceId,
            durationMs = durationMs,
            result = result,
            fields = fields,
            throwable = throwable,
        )
        return jsonObject.toString()
    }

    private fun buildLogEvent(
        level: LogLevel,
        category: LogCategory,
        event: String,
        sessionId: String,
        traceId: String?,
        durationMs: Long?,
        result: String?,
        fields: Map<String, Any?>,
        throwable: Throwable?,
    ): JsonObject {
        val resultObject = mutableMapOf<String, JsonElement>(
            "level" to JsonPrimitive(level.wireName),
            "category" to JsonPrimitive(category.wireName),
            "event" to JsonPrimitive(event),
            "timestamp" to JsonPrimitive(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
            "sessionId" to JsonPrimitive(sessionId),
            "fields" to fields.asJsonObject(),
        )
        if (traceId != null) {
            resultObject["traceId"] = JsonPrimitive(traceId)
        }
        if (durationMs != null) {
            resultObject["durationMs"] = JsonPrimitive(durationMs)
        }
        if (result != null) {
            resultObject["result"] = JsonPrimitive(result)
        }
        if (throwable != null) {
            resultObject["errorClass"] = JsonPrimitive(throwable::class.java.simpleName)
            resultObject["errorMessage"] = JsonPrimitive(throwable.message.orEmpty())
            resultObject["stackTrace"] = JsonPrimitive(throwable.stackTraceString())
        }

        return JsonObject(resultObject)
    }

    private fun Map<String, Any?>.asJsonObject(): JsonObject = JsonObject(
        mapValues { (_, value) -> value.toJsonValue() }
    )

    private fun Any?.toJsonValue(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Iterable<*> -> JsonArray(map { it.toJsonValue() })
        is Map<*, *> -> JsonObject(
            entries.associate { (key, value) -> key.toString() to value.toJsonValue() }
        )
        else -> JsonPrimitive(toString())
    }

    private fun Throwable.stackTraceString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
