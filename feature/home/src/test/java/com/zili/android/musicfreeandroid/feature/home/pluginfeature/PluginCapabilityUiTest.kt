package com.zili.android.musicfreeandroid.feature.home.pluginfeature

import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluginCapabilityUiTest {

    @Test
    fun `pluginsSupporting keeps order and filters by method`() {
        val unsupported = loadedPlugin("unsupported", setOf("search"))
        val first = loadedPlugin("first", setOf("getTopLists", "search"))
        val second = loadedPlugin("second", setOf("getTopLists"))

        val result = listOf(unsupported, first, second).pluginsSupporting("getTopLists")

        assertEquals(
            listOf(
                PluginCapabilityUiModel(platform = "first", label = "first"),
                PluginCapabilityUiModel(platform = "second", label = "second"),
            ),
            result,
        )
    }

    @Test
    fun `pluginsSupporting excludes blank platforms`() {
        val blank = loadedPlugin(" ", setOf("getRecommendSheetsByTag"))
        val valid = loadedPlugin("demo", setOf("getRecommendSheetsByTag"))

        val result = listOf(blank, valid).pluginsSupporting("getRecommendSheetsByTag")

        assertEquals(listOf(PluginCapabilityUiModel(platform = "demo", label = "demo")), result)
    }

    private fun loadedPlugin(platform: String, supportedMethods: Set<String>): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = "1.0.0",
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = listOf("music"),
                supportedMethods = supportedMethods,
            ),
        )
        return plugin
    }
}
