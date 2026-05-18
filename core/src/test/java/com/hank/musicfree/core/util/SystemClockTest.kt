package com.hank.musicfree.core.util

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemClockTest {
    @Test fun now_returns_system_currentTimeMillis_within_1_second() {
        val before = System.currentTimeMillis()
        val v = SystemClock().now()
        val after = System.currentTimeMillis()
        assertTrue(v in before..after)
    }
}
