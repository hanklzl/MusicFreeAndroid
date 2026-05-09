package com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation

import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginSheetRouteSeedTest {

    @Test
    fun `plugin sheet route fallback keeps lightweight fields`() {
        val seed = PluginSheetDetailRoute(
            pluginPlatform = "demo",
            sheetId = "sheet-1",
            title = "推荐歌单",
            artist = "编辑",
            description = "描述",
            coverImg = "https://example.com/cover.jpg",
            artwork = "https://example.com/art.jpg",
            worksNum = 30,
            seedToken = null,
        ).fallbackSheetSeed()

        assertEquals("sheet-1", seed.id)
        assertEquals("demo", seed.platform)
        assertEquals("推荐歌单", seed.title)
        assertEquals("编辑", seed.artist)
        assertEquals("描述", seed.description)
        assertEquals("https://example.com/cover.jpg", seed.coverImg)
        assertEquals("https://example.com/art.jpg", seed.artwork)
        assertEquals(30, seed.worksNum)
        assertEquals("sheet-1", seed.raw["id"])
        assertEquals("demo", seed.raw["platform"])
        assertEquals("描述", seed.raw["description"])
    }

    @Test
    fun `top list route fallback keeps lightweight fields`() {
        val seed = TopListDetailRoute(
            pluginPlatform = "demo",
            topListId = "top-1",
            title = "飙升榜",
            artist = "官方",
            description = "每日更新",
            coverImg = "https://example.com/top.jpg",
            artwork = "https://example.com/top-art.jpg",
            worksNum = 100,
            seedToken = null,
        ).fallbackTopListSeed()

        assertEquals("top-1", seed.id)
        assertEquals("demo", seed.platform)
        assertEquals("飙升榜", seed.title)
        assertEquals("官方", seed.artist)
        assertEquals("每日更新", seed.description)
        assertEquals("https://example.com/top.jpg", seed.coverImg)
        assertEquals("https://example.com/top-art.jpg", seed.artwork)
        assertEquals(100, seed.worksNum)
        assertEquals("top-1", seed.raw["id"])
        assertEquals("demo", seed.raw["platform"])
        assertEquals("每日更新", seed.raw["description"])
    }
}
