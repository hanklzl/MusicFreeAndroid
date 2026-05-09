package com.zili.android.musicfreeandroid.harness.contracts

import com.zili.android.musicfreeandroid.navigation.MusicFreeScreenTransitionDurationMillis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards INC-2026-0006. See docs/dev-harness/ui/rules.md#rule-nav-animation-100ms.
 *
 * The screen transition duration constant must remain at 100ms to mirror
 * the RN original (../MusicFree/src/entry/index.tsx animationDuration: 100).
 *
 * This is a thin wrapper around the existing
 * MusicFreeNavTransitionsTest assertion so the harness gradle filter
 * (--tests '*harness.contracts.*') picks it up.
 */
class UiNavAnimationDurationContractTest {
    @Test
    fun ordinary_screen_transition_duration_matches_rn() {
        assertEquals(100, MusicFreeScreenTransitionDurationMillis)
    }
}
