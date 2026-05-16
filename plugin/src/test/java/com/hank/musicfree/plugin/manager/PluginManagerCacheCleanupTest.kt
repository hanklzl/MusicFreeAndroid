package com.hank.musicfree.plugin.manager

import android.content.Context
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.local.LocalFilePlugin
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.runtime.PluginAppVersionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PluginManagerCacheCleanupTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `uninstall triggers deleteByPlatform on cache lyric and downloaded stores`() = runTest {
        val mediaCacheRepository = mock<MediaCacheRepository>()
        val lyricRepository = mock<LyricRepository>()
        val downloadedTrackDao = mock<DownloadedTrackDao>()
        val manager = manager(
            mediaCacheRepository = mediaCacheRepository,
            lyricRepository = lyricRepository,
            downloadedTrackDao = downloadedTrackDao,
        )
        // Seed an installed plugin "X" backed by an actual file inside pluginsDir.
        val pluginFile = pluginsDir().resolve("X.js").apply { writeText("// noop") }
        manager.setLoadedPluginsForTest(listOf(plugin("X", pluginFile.absolutePath)))

        manager.uninstall("X")

        verify(mediaCacheRepository).deleteByPlatform("X")
        verify(lyricRepository).deleteByPlatform("X")
        verify(downloadedTrackDao).deleteByPlatform("X")
    }

    @Test
    fun `uninstall does not propagate when cache cleanup throws`() = runTest {
        val mediaCacheRepository = mock<MediaCacheRepository>()
        val lyricRepository = mock<LyricRepository>()
        val downloadedTrackDao = mock<DownloadedTrackDao>()
        whenever(mediaCacheRepository.deleteByPlatform("X"))
            .thenThrow(RuntimeException("db locked"))
        val manager = manager(
            mediaCacheRepository = mediaCacheRepository,
            lyricRepository = lyricRepository,
            downloadedTrackDao = downloadedTrackDao,
        )
        val pluginFile = pluginsDir().resolve("X.js").apply { writeText("// noop") }
        manager.setLoadedPluginsForTest(listOf(plugin("X", pluginFile.absolutePath)))

        // Should swallow the exception (logged as plugin_uninstall_cache_cleanup_failed).
        manager.uninstall("X")

        // The failing call still happens. Subsequent deletes may or may not run
        // depending on runCatching grouping — we only assert no exception propagated.
        verify(mediaCacheRepository).deleteByPlatform("X")
    }

    @Test
    fun `uninstall on unknown platform does not touch cache lyric or downloaded stores`() = runTest {
        val mediaCacheRepository = mock<MediaCacheRepository>()
        val lyricRepository = mock<LyricRepository>()
        val downloadedTrackDao = mock<DownloadedTrackDao>()
        val manager = manager(
            mediaCacheRepository = mediaCacheRepository,
            lyricRepository = lyricRepository,
            downloadedTrackDao = downloadedTrackDao,
        )
        // No plugin seeded — uninstall should early-return without cleanup.
        manager.uninstall("ghost")

        verify(mediaCacheRepository, never()).deleteByPlatform(any())
        verify(lyricRepository, never()).deleteByPlatform(any())
        verify(downloadedTrackDao, never()).deleteByPlatform(any())
    }

    private fun manager(
        mediaCacheRepository: MediaCacheRepository,
        lyricRepository: LyricRepository,
        downloadedTrackDao: DownloadedTrackDao,
    ): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        whenever(context.packageName).thenReturn("com.test")
        val metaStore = mock<PluginMetaStore>()
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(metaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        whenever(metaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        val appPreferences = mock<AppPreferences>()
        whenever(appPreferences.lazyLoadPlugins).thenReturn(flowOf(false))
        whenever(appPreferences.skipPluginVersionCheck).thenReturn(flowOf(false))
        return PluginManager(
            context,
            metaStore,
            mediaCacheRepository,
            lyricRepository,
            downloadedTrackDao,
            mock<LocalFilePlugin>(),
            PluginAppVersionGate(),
            "1.0.0",
            mock<PluginMetadataCacheGateway>(),
            appPreferences,
        )
    }

    private fun pluginsDir(): File {
        val dir = File(tempFolder.root, "plugins").apply { mkdirs() }
        return dir
    }

    @Suppress("UNCHECKED_CAST")
    private fun PluginManager.setLoadedPluginsForTest(plugins: List<LoadedPlugin>) {
        val field = PluginManager::class.java.getDeclaredField("_plugins")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<LoadedPlugin>>).value = plugins
    }

    private fun plugin(platform: String, filePath: String): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = emptyList(),
            ),
        )
        whenever(plugin.filePath).thenReturn(filePath)
        return plugin
    }
}
