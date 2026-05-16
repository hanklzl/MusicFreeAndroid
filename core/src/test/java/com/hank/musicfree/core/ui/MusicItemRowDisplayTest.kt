package com.hank.musicfree.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicItemRowDisplayTest {
    @Test
    fun `platform tag text is null when platform is blank`() {
        assertNull(platformTagText(""))
        assertNull(platformTagText("   "))
    }

    @Test
    fun `platform tag text maps local platform`() {
        assertEquals("本地", platformTagText("local"))
    }

    @Test
    fun `platform tag text keeps non blank platform`() {
        assertEquals("网易", platformTagText(" 网易 "))
    }
}
