package com.hank.musicfree.plugin.engine

import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHttpLogSanitizerTest {
    @Test
    fun `headers hide credentials regardless of case and preserve content diagnostics`() {
        val headers = Headers.Builder()
            .add("aUtHoRiZaTiOn", "Bearer synthetic-auth")
            .add("X-Qing-Sign", "synthetic-signature")
            .add("X-Qing-Device-Id", "synthetic-device")
            .add("Set-Cookie", "session=synthetic-cookie")
            .add("X-Custom-Credential", "synthetic-custom")
            .add("Content-Type", "application/json")
            .build()
        val result = PluginHttpLogSanitizer.headers(headers, 120, 600)
        assertFalse(result.contains("synthetic-"))
        assertTrue(result.contains("Content-Type=application/json"))
        assertTrue(result.contains("X-Qing-Sign=[redacted]"))
    }

    @Test
    fun `URL diagnostics omit userinfo query values and fragment including unknown CDN fields`() {
        val result = PluginHttpLogSanitizer.url("https://synthetic-user:synthetic-pass@example.invalid/music?token=synthetic-token&provider_field=synthetic-value#synthetic-fragment")
        assertFalse(result.contains("synthetic-"))
        assertTrue(result.startsWith("https://example.invalid/music?"))
        assertTrue(result.contains("provider_field="))
        assertEquals("[invalid URL omitted]", PluginHttpLogSanitizer.url("invalid synthetic-secret"))
    }

    @Test
    fun `JSON redaction visits nested objects arrays and JSON encoded credentials before truncating`() {
        val body = """{"count":2,"data":[{"TiCkEt":"synthetic-ticket","nested":{"sign_secret":"synthetic-key","password":"synthetic-password"}}],"config":"{\"signSecret\":\"synthetic-config-key\"}","url":"https://example.invalid/a?unknown=synthetic-query"}"""
        val result = PluginHttpLogSanitizer.body("https://example.invalid/catalog", body, 2000)!!
        assertFalse(result.contains("synthetic-"))
        assertTrue(result.contains("\"count\":2"))
        assertTrue(result.contains("[redacted]"))
    }

    @Test
    fun `ticket and resolver bodies are entirely omitted even without known field names`() {
        for (path in listOf("/v1/gateway/ticket", "/v1/music/resolve-url/")) {
            assertEquals("[body omitted]", PluginHttpLogSanitizer.body("https://example.invalid$path", """{"unexpected":"synthetic-private"}""", 200))
        }
        assertEquals("[body omitted]", PluginHttpLogSanitizer.body("https://example.invalid/catalog", "token=synthetic-private", 200))
        assertEquals("[body omitted]", PluginHttpLogSanitizer.body("https://example.invalid/catalog", "{\"ticket\":\"synthetic-private", 200))
        assertNull(PluginHttpLogSanitizer.body("https://example.invalid/catalog", null, 200))
    }

    @Test
    fun `oversized and deeply nested bodies never fall back to raw content`() {
        assertEquals("[body omitted]", PluginHttpLogSanitizer.body("https://example.invalid/catalog", " ".repeat(65537) + "synthetic-private", 240))
        val deep = "[".repeat(25) + "\"synthetic-private\"" + "]".repeat(25)
        assertFalse(PluginHttpLogSanitizer.body("https://example.invalid/catalog", deep, 2000)!!.contains("synthetic-private"))
    }

    @Test
    fun `failure retains type but not original message causes or suppressed values`() {
        val error = IllegalArgumentException("synthetic-body", IllegalStateException("synthetic-cause"))
        error.addSuppressed(IllegalStateException("synthetic-suppressed"))
        val safe = PluginHttpLogSanitizer.failure(error)!!
        assertTrue(safe.message!!.contains("IllegalArgumentException"))
        assertFalse(safe.stackTraceToString().contains("synthetic-"))
        assertNull(safe.cause)
        assertTrue(safe.suppressed.isEmpty())
    }
}
