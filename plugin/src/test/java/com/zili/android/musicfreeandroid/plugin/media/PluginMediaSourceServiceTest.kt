package com.zili.android.musicfreeandroid.plugin.media

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluginMediaSourceServiceTest {
    @Test
    fun `uses alternative plugin and preserves original identity`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "https://source.example/1.mp3")
        val target = plugin("target", supportsMedia = true, url = "https://target.example/1.mp3")
        val service = service(
            plugins = listOf(source, target),
            alternatives = mapOf("source" to "target"),
        )

        val result = service.resolve(item("source"))!!

        assertEquals("source", result.requestedPlatform)
        assertEquals("target", result.resolverPlatform)
        assertTrue(result.redirected)
        assertEquals("source", result.item.platform)
        assertEquals("https://target.example/1.mp3", result.item.url)
    }

    @Test
    fun `falls back to source when alternative has no url`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "https://source.example/1.mp3")
        val target = plugin("target", supportsMedia = true, url = "")
        val service = service(
            plugins = listOf(source, target),
            alternatives = mapOf("source" to "target"),
        )

        val result = service.resolve(item("source"))!!

        assertEquals("source", result.resolverPlatform)
        assertFalse(result.redirected)
        assertEquals("https://source.example/1.mp3", result.item.url)
    }

    @Test
    fun `ignores disabled alternative plugin`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "https://source.example/1.mp3")
        val target = plugin("target", supportsMedia = true, url = "https://target.example/1.mp3")
        val service = service(
            plugins = listOf(source, target),
            alternatives = mapOf("source" to "target"),
            disabled = setOf("target"),
        )

        val result = service.resolve(item("source"))!!

        assertEquals("source", result.resolverPlatform)
        assertFalse(result.redirected)
    }

    private fun service(
        plugins: List<LoadedPlugin>,
        alternatives: Map<String, String>,
        disabled: Set<String> = emptySet(),
    ): PluginMediaSourceService {
        val manager = mock<PluginManager>()
        val metaStore = mock<PluginMetaStore>()
        whenever(manager.plugins).thenReturn(MutableStateFlow(plugins))
        plugins.forEach { plugin ->
            whenever(manager.getPlugin(plugin.info.platform)).thenReturn(plugin)
        }
        whenever(metaStore.alternativePlugins).thenReturn(flowOf(alternatives))
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(disabled))
        whenever(manager.pluginMetaStore).thenReturn(metaStore)
        return PluginMediaSourceService(manager)
    }

    private suspend fun plugin(platform: String, supportsMedia: Boolean, url: String): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = emptyList(),
                supportedMethods = if (supportsMedia) setOf("getMediaSource") else emptySet(),
            ),
        )
        whenever(plugin.getMediaSource(any(), any())).thenReturn(
            MediaSourceResult(url = url, headers = null, userAgent = null, quality = null),
        )
        return plugin
    }

    private fun item(platform: String) = MusicItem(
        id = "1",
        platform = platform,
        title = "Song",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
