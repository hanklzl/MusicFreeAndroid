package com.hank.musicfree.plugin.media

import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.network.PluginNetworkStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies the local short-circuit logic introduced in Task 2:
 * when [MusicItem.localPath] is set and readable, [PluginMediaSourceService.resolve]
 * returns the local path without contacting the plugin.
 */
class PluginMediaSourceServiceLocalShortCircuitTest {

    @Test
    fun `resolve returns local path when localPath is set and readable`() = runTest {
        val localPath = "/sdcard/Music/track.mp3"
        val item = musicItem(localPath = localPath)
        val probe = LocalFileProbe { true }
        val plugin = pluginWithUrl("kuwo", "https://kuwo.example/stream.mp3")

        val service = service(
            plugins = listOf(plugin),
            localFileProbe = probe,
        )

        val result = service.resolve(item)!!

        assertEquals(localPath, result.item.url)
        assertEquals(localPath, result.source.url)
        assertEquals("kuwo", result.requestedPlatform)
        assertEquals("kuwo", result.resolverPlatform)
        // Plugin's getMediaSource must NOT have been called
        verify(plugin, never()).getMediaSource(any(), any())
    }

    @Test
    fun `resolve falls through to plugin when localPath is set but unreadable`() = runTest {
        val localPath = "/sdcard/Music/missing_track.mp3"
        val item = musicItem(localPath = localPath)
        val probe = LocalFileProbe { false }
        val musicRepo = mock<MusicRepository>()
        val plugin = pluginWithUrl("kuwo", "https://kuwo.example/stream.mp3")

        val service = service(
            plugins = listOf(plugin),
            localFileProbe = probe,
            musicRepository = musicRepo,
        )

        val result = service.resolve(item)

        // Should have called through to the plugin
        assertEquals("https://kuwo.example/stream.mp3", result?.item?.url)
        verify(plugin).getMediaSource(any(), any())
        // removeFromLocalLibrary must have been called to clean up the stale localPath
        verify(musicRepo).removeFromLocalLibrary(item)
    }

    /**
     * Bug fix (v1.2.5): when a plugin/search result hands us a MusicItem with no
     * `localPath`, the resolver must still consult the DB so a previously
     * downloaded track is served from disk rather than re-fetched over the
     * network. Reproduces the pluginSheetDetail / search-result hit path.
     */
    @Test
    fun `resolve uses local file when item localPath is null but track downloaded in db`() = runTest {
        val downloadedPath = "content://media/external/audio/media/4242"
        val itemFromPlugin = musicItem(localPath = null)
        val itemInDb = itemFromPlugin.copy(localPath = downloadedPath)
        val musicRepo = mock<MusicRepository> {
            onBlocking { getById(itemFromPlugin.id, itemFromPlugin.platform) } doReturn itemInDb
        }
        val probe = LocalFileProbe { true }
        val plugin = pluginWithUrl("kuwo", "https://kuwo.example/stream.mp3")

        val service = service(
            plugins = listOf(plugin),
            localFileProbe = probe,
            musicRepository = musicRepo,
        )

        val result = service.resolve(itemFromPlugin)!!

        assertEquals(downloadedPath, result.item.url)
        assertEquals(downloadedPath, result.source.url)
        verify(plugin, never()).getMediaSource(any(), any())
    }

    @Test
    fun `resolve falls through to plugin when item localPath is null and track not downloaded`() = runTest {
        val itemFromPlugin = musicItem(localPath = null)
        val musicRepo = mock<MusicRepository> {
            onBlocking { getById(itemFromPlugin.id, itemFromPlugin.platform) } doReturn null
        }
        val probe = LocalFileProbe { true }
        val plugin = pluginWithUrl("kuwo", "https://kuwo.example/stream.mp3")

        val service = service(
            plugins = listOf(plugin),
            localFileProbe = probe,
            musicRepository = musicRepo,
        )

        val result = service.resolve(itemFromPlugin)

        assertEquals("https://kuwo.example/stream.mp3", result?.item?.url)
        verify(plugin).getMediaSource(any(), any())
    }

    // ---------- Fixtures ----------

    private fun musicItem(localPath: String?) = MusicItem(
        id = "1",
        platform = "kuwo",
        title = "Song",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
        localPath = localPath,
    )

    private suspend fun pluginWithUrl(platform: String, url: String): LoadedPlugin {
        val p = mock<LoadedPlugin>()
        whenever(p.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = emptyList(),
                supportedMethods = setOf("getMediaSource"),
            ),
        )
        whenever(p.getMediaSource(any(), any())).thenReturn(
            com.hank.musicfree.core.model.MediaSourceResult(
                url = url,
                headers = null,
                userAgent = null,
                quality = null,
            ),
        )
        return p
    }

    private fun service(
        plugins: List<LoadedPlugin>,
        localFileProbe: LocalFileProbe = LocalFileProbe { false },
        musicRepository: MusicRepository = mock(),
    ): PluginMediaSourceService {
        val manager = mock<PluginManager>()
        val metaStore = mock<PluginMetaStore>()
        whenever(manager.plugins).thenReturn(MutableStateFlow(plugins))
        plugins.forEach { plugin ->
            whenever(manager.getPlugin(plugin.info.platform)).thenReturn(plugin)
        }
        whenever(metaStore.alternativePlugins).thenReturn(flowOf(emptyMap()))
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(manager.pluginMetaStore).thenReturn(metaStore)

        val cache = mock<MediaCacheRepository>()

        return PluginMediaSourceService(
            pluginManager = manager,
            mediaCacheRepository = cache,
            musicRepository = musicRepository,
            localFileProbe = localFileProbe,
            playbackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
            networkStateProvider = PluginNetworkStateProvider.AlwaysOnline,
            playCacheTelemetry = PlayCacheTelemetry(MfLog),
        )
    }
}
