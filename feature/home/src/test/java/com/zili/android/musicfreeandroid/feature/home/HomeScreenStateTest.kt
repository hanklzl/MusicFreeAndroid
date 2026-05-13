package com.zili.android.musicfreeandroid.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenStateTest {

    @Test
    fun `back action closes drawer before dismissing transient surfaces`() {
        val state = HomeScreenState()

        state.openDrawer()
        assertTrue(state.onBackPressedConsumed())
        assertFalse(state.isDrawerOpen)

        state.showTimingCloseDialog()
        assertTrue(state.onBackPressedConsumed())
        assertFalse(state.isTimingCloseVisible)
    }
}
