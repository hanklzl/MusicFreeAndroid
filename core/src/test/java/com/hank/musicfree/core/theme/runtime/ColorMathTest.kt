package com.hank.musicfree.core.theme.runtime

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {

    @Test
    fun `grayRate of white is approximately zero`() {
        assertTrue(abs(grayRate(Color.White)) < 1e-3f)
    }

    @Test
    fun `grayRate of black is zero`() {
        assertEquals(0f, grayRate(Color.Black), 1e-3f)
    }

    @Test
    fun `grayRate of pure red is positive`() {
        // r=255 g=0 b=0 -> (255-0)/255 + (0-0)/255 = 1.0
        val gr = grayRate(Color(0xFFFF0000))
        assertTrue("expected gr > 0, was $gr", gr > 0f)
    }

    @Test
    fun `darken of mid-gray produces strictly darker color`() {
        val original = Color(0xFF888888)
        val darker = original.darken(0.5f)
        // Luminance-equivalent check: every channel should drop (gray channels equal).
        assertTrue("darker.red ${darker.red} should be < ${original.red}", darker.red < original.red)
    }

    @Test
    fun `darken with negative amount lightens`() {
        val original = Color(0xFF444444)
        val lighter = original.darken(-0.3f)
        assertTrue(lighter.red > original.red)
    }

    @Test
    fun `toHexString round-trips through parseHexColor`() {
        val original = Color(0xFFAABBCC)
        val hex = original.toHexString()
        assertEquals("#FFAABBCC", hex)
        val parsed = parseHexColor(hex)
        assertNotNull(parsed)
        assertEquals(original.red, parsed!!.red, 1e-3f)
        assertEquals(original.green, parsed.green, 1e-3f)
        assertEquals(original.blue, parsed.blue, 1e-3f)
        assertEquals(original.alpha, parsed.alpha, 1e-3f)
    }

    @Test
    fun `parseHexColor accepts short form`() {
        val parsed = parseHexColor("#ABC")
        assertNotNull(parsed)
        // #ABC -> #AABBCC
        assertEquals(0xAA / 255f, parsed!!.red, 1e-3f)
        assertEquals(0xBB / 255f, parsed.green, 1e-3f)
        assertEquals(0xCC / 255f, parsed.blue, 1e-3f)
    }

    @Test
    fun `parseHexColor accepts 6-digit form with full alpha`() {
        val parsed = parseHexColor("#FF0000")
        assertNotNull(parsed)
        assertEquals(1f, parsed!!.red, 1e-3f)
        assertEquals(0f, parsed.green, 1e-3f)
        assertEquals(1f, parsed.alpha, 1e-3f)
    }

    @Test
    fun `parseHexColor returns null on garbage`() {
        assertNull(parseHexColor("not a color"))
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("FF0000")) // missing #
        assertNull(parseHexColor("#GG0000"))
        assertNull(parseHexColor("#12345")) // odd length
    }

    @Test
    fun `saturate increases color saturation toward primary hue`() {
        // Near-gray color with a slight red tint should become more red after saturate.
        val nearGray = Color(red = 0.55f, green = 0.5f, blue = 0.5f, alpha = 1f)
        val saturated = nearGray.saturate(0.5f)
        // After increasing saturation, the red channel should be at least as far
        // from the other channels as before.
        val originalSpread = nearGray.red - nearGray.green
        val newSpread = saturated.red - saturated.green
        assertTrue("expected larger spread, before=$originalSpread after=$newSpread", newSpread >= originalSpread)
    }
}
