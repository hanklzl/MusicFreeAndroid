package com.hank.musicfree.feature.home.pluginsheet

import com.hank.musicfree.core.model.StarredKind
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginSheetStarredMapperTest {

    @Test
    fun `plugin sheet maps to starred sheet preserving seed fields`() {
        val item = musicSheetItem()

        val starred = item.toStarredSheet()

        assertEquals(item.id, starred.id)
        assertEquals(item.platform, starred.platform)
        assertEquals(item.title, starred.title)
        assertEquals(item.artist, starred.artist)
        assertEquals(item.coverImg, starred.coverUri)
        assertEquals(item.description, starred.description)
        assertEquals(item.artwork, starred.artwork)
        assertEquals(item.worksNum, starred.worksNum)
        assertEquals(item.raw, starred.raw)
        assertEquals(StarredKind.SHEET, starred.kind)
    }

    @Test
    fun `starred sheet maps back to plugin sheet seed preserving fields`() {
        val starred = StarredSheet(
            id = "sheet-1",
            platform = "demo",
            title = "Demo Sheet",
            artist = "Demo Artist",
            coverUri = "https://example.com/cover.jpg",
            sourceUrl = "https://example.com/sheet",
            description = "A demo sheet",
            artwork = "https://example.com/artwork.jpg",
            worksNum = 64,
            raw = mapOf("id" to "sheet-1", "source" to "demo"),
        )

        val item = starred.toMusicSheetItemBase()

        assertEquals(starred.id, item.id)
        assertEquals(starred.platform, item.platform)
        assertEquals(starred.title, item.title)
        assertEquals(starred.artist, item.artist)
        assertEquals(starred.coverUri, item.coverImg)
        assertEquals(starred.description, item.description)
        assertEquals(starred.artwork, item.artwork)
        assertEquals(starred.worksNum, item.worksNum)
        assertEquals(mapOf("id" to "sheet-1", "source" to "demo", "sourceUrl" to "https://example.com/sheet"), item.raw)
    }

    private fun musicSheetItem() = MusicSheetItemBase(
        id = "sheet-1",
        platform = "demo",
        title = "Demo Sheet",
        artist = "Demo Artist",
        description = "A demo sheet",
        coverImg = "https://example.com/cover.jpg",
        artwork = "https://example.com/artwork.jpg",
        worksNum = 64,
        raw = mapOf("id" to "sheet-1", "source" to "demo"),
    )
}
