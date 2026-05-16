package com.hank.musicfree.feature.home.toplist

import com.hank.musicfree.plugin.api.MusicSheetItemBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopListDisplayTextTest {

    @Test
    fun `subtitle prefers description over artist`() {
        assertEquals(
            "每日更新",
            topListSubtitle(
                item(description = "每日更新", artist = "官方"),
            ),
        )
    }

    @Test
    fun `subtitle falls back to artist`() {
        assertEquals("官方", topListSubtitle(item(description = null, artist = "官方")))
    }

    @Test
    fun `subtitle returns null when both values are blank`() {
        assertNull(topListSubtitle(item(description = " ", artist = "")))
    }

    private fun item(description: String?, artist: String?): MusicSheetItemBase = MusicSheetItemBase(
        id = "top-1",
        platform = "demo",
        title = "飙升榜",
        artist = artist,
        description = description,
        coverImg = null,
        artwork = null,
        worksNum = null,
        raw = emptyMap(),
    )
}
