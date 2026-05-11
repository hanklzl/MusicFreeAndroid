package com.zili.android.musicfreeandroid.harness.contracts

import com.zili.android.musicfreeandroid.navigation.MusicFreeScreenTransitionDurationMillis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards INC-2026-0006. See docs/dev-harness/ui/rules.md#rule-nav-animation-rn-android.
 *
 * The screen transition duration constant must remain at 400ms to mirror
 * RN Android slide_from_right's medium animation duration.
 *
 * This is a thin wrapper around the existing
 * MusicFreeNavTransitionsTest assertion so the harness gradle filter
 * (--tests '*harness.contracts.*') picks it up.
 */
class UiNavAnimationDurationContractTest {
    @Test
    fun ordinary_screen_transition_duration_matches_rn_android_medium_animation() {
        assertEquals(400, MusicFreeScreenTransitionDurationMillis)
    }
}
