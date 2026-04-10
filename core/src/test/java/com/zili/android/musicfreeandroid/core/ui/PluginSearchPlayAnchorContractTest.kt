package com.zili.android.musicfreeandroid.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSearchPlayAnchorContractTest {
    @Test
    fun `pilot anchors are unique and non blank`() {
        val anchors = listOf(
            FidelityAnchors.Settings.DefaultSubscriptionImport,
            FidelityAnchors.Settings.InstallStateSummary,
            FidelityAnchors.Search.QueryInput,
            FidelityAnchors.Search.SubmitButton,
            FidelityAnchors.Player.Root,
            FidelityAnchors.Player.PlayPause,
        )
        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(anchors.all { it.isNotBlank() })
    }
}
