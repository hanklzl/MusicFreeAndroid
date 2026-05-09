package com.zili.android.musicfreeandroid.feature.home.pluginsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginSheetMusicRowDisplayTest {
    @Test
    fun `platform tag text is null when platform is blank`() {
        assertNull(pluginSheetPlatformTagText(""))
        assertNull(pluginSheetPlatformTagText("   "))
    }

    @Test
    fun `platform tag text maps local platform`() {
        assertEquals("本地", pluginSheetPlatformTagText("local"))
    }

    @Test
    fun `platform tag text keeps non blank platform`() {
        assertEquals("网易", pluginSheetPlatformTagText(" 网易 "))
    }
}
