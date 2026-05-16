package com.hank.musicfree.plugin.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `returns block result on first success without delay`() = runTest {
        var calls = 0
        val r = retryOnceOnException(delayMs = 150L) { calls++; "ok" }
        assertEquals("ok", r)
        assertEquals(1, calls)
    }

    @Test
    fun `returns null without retry when block returns null`() = runTest {
        var calls = 0
        val r: String? = retryOnceOnException(delayMs = 150L) { calls++; null }
        assertNull(r)
        assertEquals(1, calls)  // null is NOT a retry trigger
    }

    @Test
    fun `retries once on exception then returns success`() = runTest {
        var calls = 0
        val r = retryOnceOnException(delayMs = 150L) {
            calls++
            if (calls == 1) throw RuntimeException("transient") else "second"
        }
        assertEquals("second", r)
        assertEquals(2, calls)
    }

    @Test
    fun `returns null when both attempts throw`() = runTest {
        var calls = 0
        val r: String? = retryOnceOnException(delayMs = 150L) {
            calls++; throw RuntimeException("always-fails")
        }
        assertNull(r)
        assertEquals(2, calls)
    }

    @Test
    fun `propagates CancellationException without retrying`() = runTest {
        var calls = 0
        try {
            retryOnceOnException<String>(delayMs = 150L) {
                calls++; throw CancellationException("cancelled")
            }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // ok
        }
        assertEquals(1, calls)
    }
}
