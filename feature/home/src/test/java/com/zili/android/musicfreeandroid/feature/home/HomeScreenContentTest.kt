package com.zili.android.musicfreeandroid.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenContentTest {

    @Test
    fun `drawer entry click closes drawer before delegating action`() {
        val state = HomeScreenState()
        val events = mutableListOf<String>()

        state.openDrawer()

        handleDrawerEntryClick(
            state = state,
            action = HomeDrawerAction.OpenSettingsRoot,
            onTriggerManualUpdateCheck = {},
        ) {
            events += if (state.isDrawerOpen) "delegate-open" else "delegate-closed"
        }

        assertFalse(state.isDrawerOpen)
        assertEquals(listOf("delegate-closed"), events)
    }

    @Test
    fun `drawer transient action closes drawer and opens local surface without delegating`() {
        val state = HomeScreenState()
        var delegateCalled = false

        state.openDrawer()

        handleDrawerEntryClick(
            state = state,
            action = HomeDrawerAction.ShowScheduleClosePanel,
            onTriggerManualUpdateCheck = {},
        ) {
            delegateCalled = true
        }

        assertFalse(state.isDrawerOpen)
        assertTrue(state.isTimingCloseVisible)
        assertFalse(delegateCalled)
    }
}
