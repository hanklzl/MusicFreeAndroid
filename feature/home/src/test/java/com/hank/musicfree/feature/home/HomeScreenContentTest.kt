package com.hank.musicfree.feature.home

import androidx.compose.material3.DrawerValue
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

    @Test
    fun `drawer sync closes in-flight opening drawer after back updates model to closed`() {
        assertTrue(
            shouldSynchronizeDrawerState(
                isDrawerOpen = false,
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Open,
            ),
        )
    }

    @Test
    fun `drawer sync skips already closed drawer`() {
        assertFalse(
            shouldSynchronizeDrawerState(
                isDrawerOpen = false,
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Closed,
            ),
        )
    }

    @Test
    fun `drawer back handler is enabled while drawer is opening even before model settles`() {
        assertTrue(
            shouldHandleDrawerBack(
                isDrawerOpen = false,
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Open,
                isAnimationRunning = true,
            ),
        )
    }

    @Test
    fun `drawer back handler is disabled when drawer is settled closed`() {
        assertFalse(
            shouldHandleDrawerBack(
                isDrawerOpen = false,
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Closed,
                isAnimationRunning = false,
            ),
        )
    }
}
