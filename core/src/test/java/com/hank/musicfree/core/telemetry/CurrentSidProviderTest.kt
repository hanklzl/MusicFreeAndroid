package com.hank.musicfree.core.telemetry

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentSidProviderTest {
    @Test fun `default sid is null`() = runTest {
        val provider = CurrentSidProvider()
        assertNull(provider.currentSid.first())
    }

    @Test fun `newSession emits a non-null short hex sid`() = runTest {
        val provider = CurrentSidProvider()
        val sid = provider.newSession()
        assertEquals(9, sid.length) // "ps_" + 6 hex
        assertEquals("ps_", sid.substring(0, 3))
        assertTrue("suffix must be lowercase hex, got: $sid",
            sid.drop(3).all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(sid, provider.currentSid.first())
    }

    @Test fun `successive sessions emit different sid`() = runTest {
        val provider = CurrentSidProvider()
        val a = provider.newSession()
        val b = provider.newSession()
        assertNotEquals(a, b)
    }
}
