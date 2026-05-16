package com.hank.musicfree.feature.home.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginMusicRowDisplayTest {
    @Test
    fun `platform tag text is null when platform is blank`() {
        assertNull(pluginPlatformTagText(""))
        assertNull(pluginPlatformTagText("   "))
    }

    @Test
    fun `platform tag text maps local platform`() {
        assertEquals("本地", pluginPlatformTagText("local"))
    }

    @Test
    fun `platform tag text keeps non blank platform`() {
        assertEquals("网易", pluginPlatformTagText(" 网易 "))
    }
}
