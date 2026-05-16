package com.hank.musicfree.logging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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

    @Test
    fun `timedFields rethrows failures`() {
        val error = IllegalStateException("boom")

        val thrown = assertThrows(IllegalStateException::class.java) {
            timedFields {
                throw error
            }
        }

        assertSame(error, thrown)
    }

    @Test
    fun `timedSuspend rethrows cancellation`() {
        val cancellation = CancellationException("cancelled")

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                timedSuspend {
                    throw cancellation
                }
            }
        }

        assertSame(cancellation, thrown)
    }
}
