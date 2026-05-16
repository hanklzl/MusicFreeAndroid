package com.hank.musicfree.feature.home.pluginsheet.navigation

import com.hank.musicfree.plugin.api.MusicSheetItemBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginSheetSeedStoreTest {

    @After
    fun tearDown() {
        PluginSheetSeedStore.clear()
    }

    @Test
    fun `take returns stored seed once`() {
        val seed = musicSheet("sheet-1")

        val token = PluginSheetSeedStore.put(seed)

        assertEquals(seed, PluginSheetSeedStore.take(token))
        assertNull(PluginSheetSeedStore.take(token))
    }

    @Test
    fun `take returns null for blank or unknown token`() {
        assertNull(PluginSheetSeedStore.take(null))
        assertNull(PluginSheetSeedStore.take(""))
        assertNull(PluginSheetSeedStore.take("missing"))
    }

    private fun musicSheet(id: String): MusicSheetItemBase = MusicSheetItemBase(
        id = id,
        platform = "demo",
        title = "Title $id",
        artist = "Artist",
        description = "Description",
        coverImg = "https://example.com/$id.jpg",
        artwork = "https://example.com/$id-art.jpg",
        worksNum = 12,
        raw = mapOf("id" to id, "custom" to "kept"),
    )
}
