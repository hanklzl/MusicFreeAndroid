package com.zili.android.musicfreeandroid.core.theme.runtime

import androidx.compose.ui.graphics.Color
import com.zili.android.musicfreeandroid.core.theme.DarkMusicFreeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyOverridesTest {

    @Test
    fun `empty map returns base unchanged`() {
        val result = applyOverrides(DarkMusicFreeColors, emptyMap())
        assertEquals(DarkMusicFreeColors, result)
    }

    @Test
    fun `single key overrides only that field`() {
        val result = applyOverrides(DarkMusicFreeColors, mapOf("primary" to "#FFFF0000"))
        assertEquals(Color(0xFFFF0000), result.primary)
        assertEquals(DarkMusicFreeColors.text, result.text)
        assertEquals(DarkMusicFreeColors.appBar, result.appBar)
    }

    @Test
    fun `all configurable keys are overridable`() {
        // Each key gets a unique hex so we can detect any silent collisions.
        val hexes = listOf(
            "#FFAA0000", "#FF00AA00", "#FF0000AA",
            "#FFAAAA00", "#FFAA00AA", "#FF00AAAA",
            "#FFCCCCCC", "#FF111111", "#FF222222",
            "#FF333333", "#FF444444", "#FF555555",
        )
        assertEquals(CONFIGURABLE_COLOR_KEYS.size, hexes.size)
        val overrides = CONFIGURABLE_COLOR_KEYS.zip(hexes).toMap()
        val result = applyOverrides(DarkMusicFreeColors, overrides)
        CONFIGURABLE_COLOR_KEYS.forEachIndexed { i, key ->
            val expected = parseHexColor(hexes[i])!!
            val actual = when (key) {
                "primary" -> result.primary
                "text" -> result.text
                "appBar" -> result.appBar
                "appBarText" -> result.appBarText
                "musicBar" -> result.musicBar
                "musicBarText" -> result.musicBarText
                "pageBackground" -> result.pageBackground
                "backdrop" -> result.backdrop
                "card" -> result.card
                "placeholder" -> result.placeholder
                "tabBar" -> result.tabBar
                "notification" -> result.notification
                else -> error("unhandled key $key")
            }
            assertEquals("mismatch on key=$key", expected, actual)
        }
    }

    @Test
    fun `unknown keys are ignored`() {
        val result = applyOverrides(
            DarkMusicFreeColors,
            mapOf(
                "primary" to "#FFFF0000",
                "totallyMadeUp" to "#FF00FF00",
                "anotherFakeKey" to "#FF0000FF",
            ),
        )
        assertEquals(Color(0xFFFF0000), result.primary)
        // Non-configurable fields should retain base values.
        assertEquals(DarkMusicFreeColors.background, result.background)
        assertEquals(DarkMusicFreeColors.shadow, result.shadow)
        assertEquals(DarkMusicFreeColors.divider, result.divider)
    }

    @Test
    fun `invalid hex preserves base value for that field`() {
        val result = applyOverrides(
            DarkMusicFreeColors,
            mapOf(
                "primary" to "not a color",
                "text" to "#FF00FF00",
            ),
        )
        assertEquals(DarkMusicFreeColors.primary, result.primary)
        assertEquals(Color(0xFF00FF00), result.text)
    }

    @Test
    fun `configurable color keys list has expected size`() {
        // Guard against accidental drift; plan calls for 12 keys.
        assertEquals(12, CONFIGURABLE_COLOR_KEYS.size)
        assertTrue("must contain primary", CONFIGURABLE_COLOR_KEYS.contains("primary"))
        assertTrue("must contain notification", CONFIGURABLE_COLOR_KEYS.contains("notification"))
    }
}
