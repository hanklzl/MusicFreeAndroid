package com.zili.android.musicfreeandroid.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParityEventSinkTest {
    @Test
    fun `maps plugin method call to plugin_method_called event`() {
        val captured = mutableListOf<String>()
        val sink = ParityEventSink(emitter = { tag, json -> captured += "$tag|$json" })

        sink.emit(
            event = "plugin_method_called",
            fields = mapOf("plugin_id" to "wy", "method" to "search", "args" to "raw search query"),
        )

        assertEquals(1, captured.size)
        val (tag, json) = captured.first().split("|", limit = 2)
        assertEquals("PARITY_EVT", tag)
        assertTrue(json.contains("\"kind\":\"plugin.method_called\""))
        assertTrue(json.contains("\"plugin_id\":\"wy\""))
        assertTrue(json.contains("\"method\":\"search\""))
        // args_hash 必须是 8 字符 hex
        val hashRegex = Regex("\"args_hash\":\"[0-9a-f]{8}\"")
        assertTrue(hashRegex.containsMatchIn(json))
    }

    @Test
    fun `ignores events outside taxonomy`() {
        val captured = mutableListOf<String>()
        val sink = ParityEventSink(emitter = { tag, json -> captured += "$tag|$json" })

        sink.emit(event = "user_clicked_random_button", fields = emptyMap())

        assertEquals(0, captured.size)
    }

    @Test
    fun `url field normalizes to url_template with query placeholders`() {
        val captured = mutableListOf<String>()
        val sink = ParityEventSink(emitter = { tag, json -> captured += "$tag|$json" })

        sink.emit(
            event = "net_request",
            fields = mapOf("url" to "https://api.example.com/search?q=hello&page=1&empty=", "method" to "GET")
        )

        assertEquals(1, captured.size)
        val json = captured.first().split("|", limit = 2)[1]
        assertTrue(json.contains("\"kind\":\"net.request\""))
        assertTrue(json.contains("\"method\":\"GET\""))
        // url_template 必须存在，url 原 key 不存在
        assertTrue(json.contains("\"url_template\":"))
        assertTrue(!json.contains("\"url\":\"https"))
        // 所有 query value 替换为 *，包括空值
        assertTrue(json.contains("?q=*"))
        assertTrue(json.contains("&page=*"))
        assertTrue(json.contains("&empty=*"))
    }

    @Test
    fun `error event merges throwable message hash`() {
        val captured = mutableListOf<String>()
        val sink = ParityEventSink(emitter = { tag, json -> captured += "$tag|$json" })

        // 直接模拟 MfLog.error 调用 sink 的入参（throwable.message 已被 caller 合并进 fields）
        sink.emit(
            event = "error",
            fields = mapOf("domain" to "net", "message" to "Connection refused")
        )

        assertEquals(1, captured.size)
        val json = captured.first().split("|", limit = 2)[1]
        assertTrue(json.contains("\"kind\":\"error\""))
        assertTrue(json.contains("\"domain\":\"net\""))
        // message 应被哈希为 message_hash
        val hashRegex = Regex("\"message_hash\":\"[0-9a-f]{8}\"")
        assertTrue(hashRegex.containsMatchIn(json))
        // 原始 message 不能出现
        assertTrue(!json.contains("Connection refused"))
    }
}
