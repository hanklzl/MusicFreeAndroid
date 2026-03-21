package com.zili.android.musicfreeandroid.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDrawerNavigationHelperTest {

    @Test
    fun `navigation callback executes even if close throws`() = runTest {
        val events = mutableListOf<String>()

        runHomeDrawerNavigation(
            navigate = { events += "navigate" },
            closeDrawer = {
                events += "close"
                error("close failed")
            },
        )

        assertEquals(listOf("navigate", "close"), events)
    }

    @Test
    fun `close is attempted after navigation in normal path`() = runTest {
        val events = mutableListOf<String>()

        runHomeDrawerNavigation(
            navigate = { events += "navigate" },
            closeDrawer = { events += "close" },
        )

        assertEquals(listOf("navigate", "close"), events)
    }
}
