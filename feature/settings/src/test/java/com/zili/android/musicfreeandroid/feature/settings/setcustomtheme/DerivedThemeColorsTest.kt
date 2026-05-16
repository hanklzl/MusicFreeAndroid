package com.zili.android.musicfreeandroid.feature.settings.setcustomtheme

import androidx.compose.ui.graphics.Color
import com.zili.android.musicfreeandroid.core.theme.runtime.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DerivedThemeColorsTest {

    @Test
    fun `strong blue primary triggers gray-less-than-minus-0_4 branch with tabBar`() {
        // RGB (10, 60, 220): grayRate = (10-60)/255 + (60-220)/255 ~= -0.823 < -0.4
        val primary = Color(red = 10, green = 60, blue = 220, alpha = 255)
        val derived = deriveCustomColors(PaletteColors(primary, primary, primary))

        assertEquals(5, derived.size)
        assertTrue("tabBar key must be present in gray<-0.4 branch", derived.containsKey("tabBar"))
        assertEquals(primary.toHexString(), derived["appBar"])
        assertEquals(primary.toHexString(), derived["musicBar"])
        assertEquals("#33000000", derived["card"])
    }

    @Test
    fun `strong red primary triggers gray-greater-than-0_4 branch without tabBar`() {
        // RGB (230, 30, 30): grayRate = (230-30)/255 + (30-30)/255 ~= 0.784 > 0.4
        val primary = Color(red = 230, green = 30, blue = 30, alpha = 255)
        val derived = deriveCustomColors(PaletteColors(primary, primary, primary))

        assertEquals(4, derived.size)
        assertFalse("tabBar must not appear in gray>0.4 branch", derived.containsKey("tabBar"))
        assertEquals(primary.toHexString(), derived["appBar"])
        assertEquals(primary.toHexString(), derived["musicBar"])
        assertEquals("#33000000", derived["card"])
    }

    @Test
    fun `near-grey primary takes else branch with saturate transform`() {
        // RGB (128, 130, 132): grayRate ~= -0.0156, in [-0.4, 0.4]
        val primary = Color(red = 128, green = 130, blue = 132, alpha = 255)
        val derived = deriveCustomColors(PaletteColors(primary, primary, primary))

        assertEquals(4, derived.size)
        assertFalse("tabBar must not appear in else branch", derived.containsKey("tabBar"))
        assertEquals(primary.toHexString(), derived["appBar"])
        assertEquals(primary.toHexString(), derived["musicBar"])
        assertEquals("#33000000", derived["card"])
    }

    @Test
    fun `primary key always exists and is a hex string`() {
        val primary = Color(red = 63, green = 163, blue = 181, alpha = 255)
        val derived = deriveCustomColors(PaletteColors(primary, primary, primary))

        val primaryHex = derived["primary"]
        assertTrue("primary present", primaryHex != null)
        assertTrue("primary starts with #", primaryHex!!.startsWith("#"))
        assertEquals(9, primaryHex.length) // #AARRGGBB
    }
}
