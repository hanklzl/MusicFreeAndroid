package com.hank.musicfree.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogFieldsTest {
    @Test
    fun runtimeCategoryUsesStableWireName() {
        assertEquals("runtime", LogCategory.RUNTIME.wireName)
    }

    @Test
    fun restoreTerminalFieldsContainStoreKeyResultAndDuration() {
        val fields = RuntimeLogFields.restoreTerminal(
            store = "search_session",
            key = "search:music:hash",
            result = LogFields.Result.SUCCESS,
            durationMs = 42,
            reason = null,
        )

        assertEquals("runtime_restore", fields["operation"])
        assertEquals("search_session", fields["store"])
        assertEquals("search:music:hash", fields["key"])
        assertEquals("success", fields["result"])
        assertEquals(42L, fields["durationMs"])
        assertTrue("reason should be absent on success", !fields.containsKey("reason"))
    }

    @Test
    fun persistFailureFieldsCarryReason() {
        val fields = RuntimeLogFields.persistTerminal(
            store = "detail_session",
            key = "detail:sheet:demo:1",
            result = LogFields.Result.FAILURE,
            durationMs = 7,
            reason = "json_decode_failed",
        )

        assertEquals("runtime_snapshot_persist", fields["operation"])
        assertEquals("detail_session", fields["store"])
        assertEquals("detail:sheet:demo:1", fields["key"])
        assertEquals("failure", fields["result"])
        assertEquals(7L, fields["durationMs"])
        assertEquals("json_decode_failed", fields["reason"])
    }
}
