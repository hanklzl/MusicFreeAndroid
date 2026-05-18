package com.hank.musicfree.plugin.manager

import android.content.Context
import com.hank.musicfree.core.local.Mp3MetadataReader
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.plugin.engine.WebDavShim
import com.hank.musicfree.plugin.local.LocalFilePlugin
import com.hank.musicfree.plugin.local.LocalFilePluginConstants
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.runtime.PluginAppVersionGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B-2 contract: PluginManager.loadAllPlugins() must register a
 * [LocalLoadedPlugin] under platform "本地" so consumers can call
 * getMediaSource / getMusicInfo / getLyric / importMusicItem uniformly on
 * local audio. The local plugin must be:
 *   - lookupable via getPlugin("本地")
 *   - excluded from getSortedEnabledPlugins / getSearchablePlugins
 *     / getLyricSearchablePlugins (user-facing lists)
 *   - protected against uninstall (silent no-op)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PluginManagerLocalRegistrationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `loadAllPlugins registers LocalLoadedPlugin under 本地`() = runTest {
        val manager = makeManager()
        manager.ensurePluginsLoaded()

        val local = manager.getPlugin(LocalFilePluginConstants.PLATFORM)
        assertNotNull("Local plugin should be registered after setup", local)
        assertTrue("Local plugin should be a LocalLoadedPlugin", local is LocalLoadedPlugin)
        assertEquals(LocalFilePluginConstants.PLATFORM, local!!.info.platform)
        assertEquals(
            LocalFilePluginConstants.SUPPORTED_METHODS,
            local.info.supportedMethods,
        )
        // 本地 plugin opts out of all search types so it never appears in
        // search candidate lists, even before the platform-name filter.
        assertTrue(local.info.supportedSearchType.isEmpty())
        assertTrue(local.info.supportedSearchTypeDeclared)
    }

    @Test
    fun `getSortedEnabledPlugins filters out the local plugin`() = runTest {
        val manager = makeManager()
        manager.ensurePluginsLoaded()

        val enabled = manager.getSortedEnabledPlugins().first()

        assertTrue(
            "Local plugin must not appear in enabled-plugins list",
            enabled.none { it.info.platform == LocalFilePluginConstants.PLATFORM },
        )
    }

    @Test
    fun `getSearchablePlugins filters out the local plugin`() = runTest {
        val manager = makeManager()
        manager.ensurePluginsLoaded()

        val searchable = manager.getSearchablePlugins("music").first()
        val lyricSearchable = manager.getLyricSearchablePlugins().first()

        assertTrue(searchable.none { it.info.platform == LocalFilePluginConstants.PLATFORM })
        assertTrue(lyricSearchable.none { it.info.platform == LocalFilePluginConstants.PLATFORM })
    }

    @Test
    fun `uninstall 本地 is silent no-op and keeps it registered`() = runTest {
        val manager = makeManager()
        manager.ensurePluginsLoaded()
        assertNotNull(manager.getPlugin(LocalFilePluginConstants.PLATFORM))

        manager.uninstall(LocalFilePluginConstants.PLATFORM)

        // Local plugin must still be in the list — uninstall is a defensive no-op.
        assertNotNull(
            "Uninstall must not remove the local plugin",
            manager.getPlugin(LocalFilePluginConstants.PLATFORM),
        )
    }

    @Test
    fun `uninstallAllPlugins skips the local plugin`() = runTest {
        val manager = makeManager()
        manager.ensurePluginsLoaded()

        manager.uninstallAllPlugins()

        assertNotNull(manager.getPlugin(LocalFilePluginConstants.PLATFORM))
    }

    @Test
    fun `updatePlugin on 本地 reports MISSING_UPDATE_SOURCE without crashing`() = runTest {
        val manager = makeManager()
        manager.ensurePluginsLoaded()

        val result = manager.updatePlugin(LocalFilePluginConstants.PLATFORM)

        // The local plugin has no srcUrl AND no filePath; either way the
        // existing update path must report a structured failure, never crash.
        assertEquals(PluginOperationType.UPDATE_SINGLE, result.operationType)
        assertEquals(1, result.failureCount)
        assertEquals(
            PluginOperationErrorCode.MISSING_UPDATE_SOURCE,
            result.failures.single().errorCode,
        )
    }

    @Test
    fun `getPlugin returns null for unknown platforms but registers 本地`() = runTest {
        val manager = makeManager()
        manager.ensurePluginsLoaded()

        assertNull(manager.getPlugin("does-not-exist"))
        assertNotNull(manager.getPlugin(LocalFilePluginConstants.PLATFORM))
    }

    private fun makeManager(): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        whenever(context.packageName).thenReturn("com.test")
        val metaStore = mock<PluginMetaStore>()
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(metaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        whenever(metaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        val reader = mock<Mp3MetadataReader>()
        val localFilePlugin = LocalFilePlugin(reader)
        val appPreferences = mock<AppPreferences>()
        whenever(appPreferences.lazyLoadPlugins).thenReturn(flowOf(false))
        whenever(appPreferences.skipPluginVersionCheck).thenReturn(flowOf(false))
        val baseClient = OkHttpClient.Builder().build()
        return PluginManager(
            context,
            metaStore,
            mock<MediaCacheRepository>(),
            mock<LyricRepository>(),
            mock<DownloadedTrackDao>(),
            localFilePlugin,
            PluginAppVersionGate(),
            "1.0.0",
            mock<PluginMetadataCacheGateway>(),
            appPreferences,
            baseClient,
            WebDavShim(baseClient),
        )
    }
}
