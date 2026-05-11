package com.zili.android.musicfreeandroid.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFreeNavTransitionsTest {
    @Test
    fun ordinaryScreenTransitionDurationMatchesRnAndroidMediumAnimation() {
        assertEquals(400, MusicFreeScreenTransitionDurationMillis)
    }
}
