package com.hank.musicfree.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSearchPlayAnchorContractTest {
    @Test
    fun `pilot anchors are unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Settings.ThemeEntry,
            FidelityAnchors.Settings.BackupEntry,
            FidelityAnchors.Settings.AboutEntry,
            FidelityAnchors.Player.MiniRoot,
            FidelityAnchors.Player.MiniPlayPause,
        )
        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }

    @Test
    fun `theme settings anchors are unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Screen.SetCustomThemeRoot,
            FidelityAnchors.Settings.ThemeRoot,
            FidelityAnchors.Settings.ThemeSectionMode,
            FidelityAnchors.Settings.ThemeSectionTheme,
            FidelityAnchors.Settings.ThemeFollowSystemSwitch,
            FidelityAnchors.Settings.ThemeCardLight,
            FidelityAnchors.Settings.ThemeCardDark,
            FidelityAnchors.Settings.ThemeCardCustom,
            FidelityAnchors.SetCustomTheme.Image,
            FidelityAnchors.SetCustomTheme.SliderBlur,
            FidelityAnchors.SetCustomTheme.SliderOpacity,
            FidelityAnchors.SetCustomTheme.ColorItemPrefix,
            FidelityAnchors.SetCustomTheme.ColorPickerSheet,
            FidelityAnchors.SetCustomTheme.ColorPickerConfirm,
        )
        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }
}
