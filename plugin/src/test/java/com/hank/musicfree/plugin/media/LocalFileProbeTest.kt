package com.hank.musicfree.plugin.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the [LocalFileProbe] SAM contract.
 * No Android dependency — the SAM interface is tested via lambda implementations.
 */
class LocalFileProbeTest {

    @Test
    fun `isReadable returns true when probe lambda returns true`() {
        val probe = LocalFileProbe { true }
        assertTrue(probe.isReadable("/some/path/track.mp3"))
    }

    @Test
    fun `isReadable returns false when probe lambda returns false`() {
        val probe = LocalFileProbe { false }
        assertFalse(probe.isReadable("/some/nonexistent/track.mp3"))
    }

    @Test
    fun `isReadable can distinguish by uri`() {
        val readable = "/readable/track.mp3"
        val probe = LocalFileProbe { uri -> uri == readable }
        assertTrue(probe.isReadable(readable))
        assertFalse(probe.isReadable("/other/track.mp3"))
    }
}
