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
    fun `take resolves stored seed repeatedly`() {
        val seed = musicSheet("sheet-1")

        val token = PluginSheetSeedStore.put(seed)

        assertEquals(seed, PluginSheetSeedStore.take(token))
        assertEquals(seed, PluginSheetSeedStore.take(token))
    }

    @Test
    fun `put creates unique tokens for same platform and id`() {
        val sheetSeed = musicSheet("same-id", raw = mapOf("source" to "sheet"))
        val topListSeed = musicSheet("same-id", raw = mapOf("source" to "top_list"))

        val sheetToken = PluginSheetSeedStore.put(sheetSeed)
        val topListToken = PluginSheetSeedStore.put(topListSeed)

        assertEquals(false, sheetToken == topListToken)
        assertEquals(sheetSeed, PluginSheetSeedStore.take(sheetToken))
        assertEquals(topListSeed, PluginSheetSeedStore.take(topListToken))
    }

    @Test
    fun `take returns null for blank or unknown token`() {
        assertNull(PluginSheetSeedStore.take(null))
        assertNull(PluginSheetSeedStore.take(""))
        assertNull(PluginSheetSeedStore.take("missing"))
    }

    private fun musicSheet(
        id: String,
        raw: Map<String, Any?> = mapOf("id" to id, "custom" to "kept"),
    ): MusicSheetItemBase = MusicSheetItemBase(
        id = id,
        platform = "demo",
        title = "Title $id",
        artist = "Artist",
        description = "Description",
        coverImg = "https://example.com/$id.jpg",
        artwork = "https://example.com/$id-art.jpg",
        worksNum = 12,
        raw = raw,
    )
}
