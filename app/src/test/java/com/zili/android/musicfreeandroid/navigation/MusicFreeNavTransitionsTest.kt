package com.zili.android.musicfreeandroid.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFreeNavTransitionsTest {
    @Test
    fun ordinaryScreenTransitionDurationMatchesRn() {
        assertEquals(100, MusicFreeScreenTransitionDurationMillis)
    }
}
