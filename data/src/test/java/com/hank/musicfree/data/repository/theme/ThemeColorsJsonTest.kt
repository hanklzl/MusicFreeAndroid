package com.hank.musicfree.data.repository.theme

import com.hank.musicfree.core.theme.runtime.CONFIGURABLE_COLOR_KEYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsJsonTest {

    @Test
    fun `encode empty map returns empty json object`() {
        assertEquals("{}", ThemeColorsJson.encode(emptyMap()))
    }

    @Test
    fun `decode null returns empty map`() {
        assertTrue(ThemeColorsJson.decode(null).isEmpty())
    }

    @Test
    fun `decode empty string returns empty map`() {
        assertTrue(ThemeColorsJson.decode("").isEmpty())
        assertTrue(ThemeColorsJson.decode("   ").isEmpty())
    }

    @Test
    fun `decode malformed json returns empty map`() {
        assertTrue(ThemeColorsJson.decode("not-json").isEmpty())
        assertTrue(ThemeColorsJson.decode("{").isEmpty())
        assertTrue(ThemeColorsJson.decode("[1,2,3]").isEmpty())
    }

    @Test
    fun `encode decode round-trip preserves all configurable entries`() {
        val sample = CONFIGURABLE_COLOR_KEYS.withIndex().associate { (idx, key) ->
            // Synthesize a unique #AARRGGBB per key.
            key to "#FF%06X".format(idx * 0x111111)
        }
        // CONFIGURABLE_COLOR_KEYS has 12 entries today; future-proof check.
        assertEquals(CONFIGURABLE_COLOR_KEYS.size, sample.size)

        val encoded = ThemeColorsJson.encode(sample)
        val decoded = ThemeColorsJson.decode(encoded)

        assertEquals(sample, decoded)
    }

    @Test
    fun `encode is stable for typical small payload`() {
        val map = mapOf("primary" to "#FFFF0000", "text" to "#FFFFFFFF")
        val encoded = ThemeColorsJson.encode(map)
        val decoded = ThemeColorsJson.decode(encoded)
        assertEquals(map, decoded)
    }
}
