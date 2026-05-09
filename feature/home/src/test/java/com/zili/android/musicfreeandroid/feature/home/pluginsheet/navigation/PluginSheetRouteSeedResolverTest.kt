package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PluginSheetRouteSeedResolverTest {

    @After
    fun tearDown() {
        PluginSheetSeedStore.clear()
    }

    @Test
    fun `resolve caches consumed seed for retry`() {
        val seed = musicSheet("sheet-1", raw = mapOf("id" to "sheet-1", "custom" to "kept"))
        val token = PluginSheetSeedStore.put(seed)
        val fallback = musicSheet("fallback")
        val resolver = PluginSheetRouteSeedResolver(token) { fallback }

        val first = resolver.resolve()
        val second = resolver.resolve()

        assertSame(seed, first)
        assertSame(seed, second)
        assertEquals(null, PluginSheetSeedStore.take(token))
    }

    @Test
    fun `resolve caches fallback seed when token is missing`() {
        var fallbackCalls = 0
        val fallback = musicSheet("fallback")
        val resolver = PluginSheetRouteSeedResolver(seedToken = null) {
            fallbackCalls += 1
            fallback
        }

        val first = resolver.resolve()
        val second = resolver.resolve()

        assertSame(fallback, first)
        assertSame(fallback, second)
        assertEquals(1, fallbackCalls)
    }

    private fun musicSheet(
        id: String,
        raw: Map<String, Any?> = mapOf("id" to id),
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
