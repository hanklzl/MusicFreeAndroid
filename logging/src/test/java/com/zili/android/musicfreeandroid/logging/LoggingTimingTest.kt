package com.zili.android.musicfreeandroid.logging

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingTimingTest {
    @Test
    fun `timedFields returns value and duration`() {
        val (value, durationMs) = timedFields {
            42
        }

        assertEquals(42, value)
        assertTrue(durationMs >= 0)
    }

    @Test
    fun `timedSuspend returns value and duration`() = runBlocking {
        val (value, durationMs) = timedSuspend {
            "done"
        }

        assertEquals("done", value)
        assertTrue(durationMs >= 0)
    }
}
