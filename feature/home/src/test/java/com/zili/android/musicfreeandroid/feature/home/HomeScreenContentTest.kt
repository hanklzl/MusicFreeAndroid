package com.zili.android.musicfreeandroid.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeScreenContentTest {

    @Test
    fun `drawer entry click closes drawer before delegating action`() {
        val state = HomeScreenState()
        val events = mutableListOf<String>()

        state.openDrawer()

        handleDrawerEntryClick(
            state = state,
            action = HomeDrawerAction.OpenSettings,
        ) {
            events += if (state.isDrawerOpen) "delegate-open" else "delegate-closed"
        }

        assertFalse(state.isDrawerOpen)
        assertEquals(listOf("delegate-closed"), events)
    }
}
