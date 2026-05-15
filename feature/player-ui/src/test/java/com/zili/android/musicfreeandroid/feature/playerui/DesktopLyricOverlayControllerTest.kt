package com.zili.android.musicfreeandroid.feature.playerui

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopLyricOverlayControllerTest {
    @Test
    fun `parse android color falls back on invalid input`() {
        assertEquals(Color.BLACK, parseAndroidColor("invalid", Color.BLACK))
    }
}
