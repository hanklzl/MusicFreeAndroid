package com.zili.android.musicfreeandroid.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEventFormatterTest {
    @Test
    fun `formats trace event as single json object`() {
        val line = LogEventFormatter.format(
            level = LogLevel.TRACE,
            category = LogCategory.PLUGIN,
            event = "plugin_api_call_success",
            sessionId = "session-1",
            traceId = "trace-1",
            durationMs = 42,
            result = "success",
            fields = mapOf("platform" to "test", "count" to 2),
            throwable = null,
        )

        val json = Json.parseToJsonElement(line) as JsonObject
        assertEquals("trace", (json["level"] as JsonPrimitive).content)
        assertEquals("plugin", (json["category"] as JsonPrimitive).content)
        assertEquals("plugin_api_call_success", (json["event"] as JsonPrimitive).content)
        assertEquals("session-1", (json["sessionId"] as JsonPrimitive).content)
        assertEquals("trace-1", (json["traceId"] as JsonPrimitive).content)
        assertEquals(42L, (json["durationMs"] as JsonPrimitive).content.toLong())
        assertEquals("success", (json["result"] as JsonPrimitive).content)
        val fields = json["fields"] as JsonObject
        assertEquals("test", (fields["platform"] as JsonPrimitive).content)
        assertEquals(2L, (fields["count"] as JsonPrimitive).content.toLong())
    }

    @Test
    fun `formats throwable details for error event`() {
        val error = IllegalStateException("broken")

        val line = LogEventFormatter.format(
            level = LogLevel.ERROR,
            category = LogCategory.APP,
            event = "uncaught_exception",
            sessionId = "session-1",
            traceId = null,
            durationMs = null,
            result = "failure",
            fields = emptyMap(),
            throwable = error,
        )

        val json = Json.parseToJsonElement(line) as JsonObject
        assertEquals("IllegalStateException", (json["errorClass"] as JsonPrimitive).content)
        assertEquals("broken", (json["errorMessage"] as JsonPrimitive).content)
        assertTrue((json["stackTrace"] as JsonPrimitive).content.contains("IllegalStateException"))
    }

    @Test
    fun `formats supported field types`() {
        assertEquals("success", LogFields.Result.SUCCESS)

        val line = LogEventFormatter.format(
            level = LogLevel.DETAIL,
            category = LogCategory.PLAYER,
            event = "field_type_test",
            sessionId = "session-1",
            traceId = null,
            durationMs = null,
            result = null,
            fields = mapOf(
                "nullable" to null,
                "boolean" to true,
                "number" to 42,
                "string" to "hello",
                "list" to listOf(1, "two", false),
                "map" to mapOf("innerBool" to true, "innerNull" to null),
                "object" to UnsupportedField("unsupported"),
            ),
            throwable = null,
        )

        val json = Json.parseToJsonElement(line) as JsonObject
        val fields = json["fields"] as JsonObject
        assertEquals(JsonNull, fields["nullable"])
        assertEquals(42L, (fields["number"] as JsonPrimitive).content.toLong())
        assertEquals(true, (fields["boolean"] as JsonPrimitive).content.toBoolean())
        assertEquals("hello", (fields["string"] as JsonPrimitive).content)
        assertEquals("[1,\"two\",false]", fields["list"].toString())
        val nested = fields["map"] as JsonObject
        assertEquals(true, (nested["innerBool"] as JsonPrimitive).content.toBoolean())
        assertEquals(JsonNull, nested["innerNull"])
        assertEquals("UnsupportedField(value=unsupported)", (fields["object"] as JsonPrimitive).content)
    }

    private data class UnsupportedField(val value: String)
}
