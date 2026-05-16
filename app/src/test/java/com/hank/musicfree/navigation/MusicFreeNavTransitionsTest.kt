package com.hank.musicfree.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFreeNavTransitionsTest {
    @Test
    fun ordinaryScreenTransitionDurationMatchesRnAndroidMediumAnimation() {
        assertEquals(400, MusicFreeScreenTransitionDurationMillis)
    }
}
