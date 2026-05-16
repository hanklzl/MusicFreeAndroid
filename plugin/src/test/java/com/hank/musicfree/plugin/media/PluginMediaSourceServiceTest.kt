package com.hank.musicfree.plugin.media

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.network.PluginNetworkStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
    fun `resolves plugin item even when it already has stale url`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "https://source.example/fresh.mp3")
        val service = service(plugins = listOf(source), alternatives = emptyMap())

        val result = service.resolve(item("source").copy(url = "https://source.example/stale.mp3"))!!

        assertEquals("https://source.example/fresh.mp3", result.item.url)
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

    @Test
    fun `implicit quality uses configured default and fallback order`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "")
        whenever(source.getMediaSource(any(), eq("super"))).thenReturn(
            MediaSourceResult(url = "https://source.example/super.mp3", headers = null, userAgent = null, quality = null),
        )
        val service = service(
            plugins = listOf(source),
            alternatives = emptyMap(),
            settings = FakePlaybackRuntimeSettings(
                quality = PlayQuality.STANDARD,
                order = QualityFallbackOrder.Asc,
            ),
        )

        val result = service.resolve(item("source"))!!

        assertEquals("https://source.example/super.mp3", result.item.url)
    }

    @Test
    fun `explicit quality does not apply fallback order`() = runTest {
        val source = plugin("source", supportsMedia = true, url = "")
        whenever(source.getMediaSource(any(), eq("super"))).thenReturn(
            MediaSourceResult(url = "https://source.example/super.mp3", headers = null, userAgent = null, quality = null),
        )
        val service = service(
            plugins = listOf(source),
            alternatives = emptyMap(),
            settings = FakePlaybackRuntimeSettings(
                quality = PlayQuality.STANDARD,
                order = QualityFallbackOrder.Asc,
            ),
        )

        val result = service.resolve(item("source"), quality = "high")

        assertEquals(null, result)
    }

    private fun service(
        plugins: List<LoadedPlugin>,
        alternatives: Map<String, String>,
        disabled: Set<String> = emptySet(),
        settings: PlaybackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
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
        // These existing tests don't declare cacheControl on their plugins, so
        // they fall into the default `no-cache` policy (which never reads cache).
        // A null-stubbed mock is sufficient.
        val cache = mock<MediaCacheRepository>()
        return PluginMediaSourceService(
            pluginManager = manager,
            mediaCacheRepository = cache,
            playbackRuntimeSettings = settings,
            networkStateProvider = PluginNetworkStateProvider.AlwaysOnline,
        )
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

    private class FakePlaybackRuntimeSettings(
        private val quality: PlayQuality,
        private val order: QualityFallbackOrder,
    ) : PlaybackRuntimeSettings {
        override suspend fun defaultPlayQuality(): PlayQuality = quality

        override suspend fun playQualityOrder(): QualityFallbackOrder = order

        override suspend fun useCellularPlay(): Boolean = false

        override suspend fun allowConcurrentPlayback(): Boolean = false

        override suspend fun autoPlayWhenAppStart(): Boolean = false

        override suspend fun tryChangeSourceWhenPlayFail(): Boolean = false

        override suspend fun autoStopWhenError(): Boolean = false

        override suspend fun audioInterruptionAction(): AudioInterruptionAction = AudioInterruptionAction.Pause

        override suspend fun audioInterruptionDuckVolume(): Float = 0.5f

        override suspend fun showExitOnNotification(): Boolean = false
    }
}
