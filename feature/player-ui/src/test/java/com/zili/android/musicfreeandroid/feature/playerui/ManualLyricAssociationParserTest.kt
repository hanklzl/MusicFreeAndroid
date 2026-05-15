package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualLyricAssociationParserTest {
    @Test
    fun `parses platform id media key`() {
        val fallback = music("source", "current")

        val parsed = ManualLyricAssociationParser.parse("kg@12345", fallback)

        requireNotNull(parsed)
        assertEquals("kg", parsed.platform)
        assertEquals("12345", parsed.id)
        assertEquals("Current", parsed.title)
        assertEquals("Artist", parsed.artist)
    }

    @Test
    fun `parses json music identity`() {
        val parsed = ManualLyricAssociationParser.parse(
            """{"platform":"wy","id":"9988","title":"Target","artist":"Singer","duration":321000}""",
        )

        requireNotNull(parsed)
        assertEquals("wy", parsed.platform)
        assertEquals("9988", parsed.id)
        assertEquals("Target", parsed.title)
        assertEquals("Singer", parsed.artist)
        assertEquals(321000L, parsed.duration)
    }

    @Test
    fun `rejects invalid manual association input`() {
        assertNull(ManualLyricAssociationParser.parse(""))
        assertNull(ManualLyricAssociationParser.parse("missing_separator"))
        assertNull(ManualLyricAssociationParser.parse("""{"platform":"wy"}"""))
    }

    private fun music(platform: String, id: String) = MusicItem(
        id = id,
        platform = platform,
        title = "Current",
        artist = "Artist",
        album = null,
        duration = 1000L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
