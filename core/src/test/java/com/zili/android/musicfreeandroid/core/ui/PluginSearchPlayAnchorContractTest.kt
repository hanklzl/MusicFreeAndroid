package com.zili.android.musicfreeandroid.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSearchPlayAnchorContractTest {
    @Test
    fun `pilot anchors are unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Settings.PluginManagementEntry,
            FidelityAnchors.Settings.ThemeEntry,
            FidelityAnchors.Settings.BackupEntry,
            FidelityAnchors.Settings.AboutEntry,
            FidelityAnchors.Player.MiniRoot,
            FidelityAnchors.Player.MiniPlayPause,
        )
        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }
}
