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
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doThrow
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PluginManagerUserVariablesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `loads user variable declarations and filters missing keys`() = runTest {
        val userVariables = parsePluginUserVariables(
            """
                [
                  { "key": "cookie", "name": "Cookie", "hint": "输入 Cookie" },
                  { "name": "No Key" }
                ]
            """.trimIndent(),
        )

        assertEquals(1, userVariables.size)
        assertEquals("cookie", userVariables.first().key)
        assertEquals("Cookie", userVariables.first().name)
        assertEquals("输入 Cookie", userVariables.first().hint)
    }

    @Test
    fun `malformed user variable declarations are ignored`() = runTest {
        assertEquals(emptyList<Any>(), parsePluginUserVariables("[not-json"))
    }

    @Test
    fun `setUserVariables persists and refreshes loaded runtime`() = runTest {
        val metaStore = mock<PluginMetaStore>()
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(metaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        whenever(metaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        val manager = manager(metaStore)
        val plugin = plugin("vars")
        manager.setLoadedPluginsForTest(listOf(plugin))

        manager.setUserVariables("vars", mapOf("cookie" to "abc"))

        verify(metaStore).setUserVariables("vars", mapOf("cookie" to "abc"))
        verify(plugin).updateUserVariables(mapOf("cookie" to "abc"))
    }

    @Test
    fun `setUserVariables fails when runtime refresh fails`() = runTest {
        val metaStore = mock<PluginMetaStore>()
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(metaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        whenever(metaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        val manager = manager(metaStore)
        val plugin = plugin("vars")
        whenever(plugin.updateUserVariables(any())).doThrow(IllegalStateException("refresh failed"))
        manager.setLoadedPluginsForTest(listOf(plugin))

        try {
            manager.setUserVariables("vars", mapOf("cookie" to "abc"))
            fail("setUserVariables should fail when runtime refresh fails")
        } catch (e: IllegalStateException) {
            assertEquals("refresh failed", e.message)
        }
        verify(metaStore, never()).setUserVariables("vars", mapOf("cookie" to "abc"))
    }

    private fun manager(metaStore: PluginMetaStore): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        whenever(context.packageName).thenReturn("com.test")
        val appPreferences = mock<AppPreferences>()
        whenever(appPreferences.lazyLoadPlugins).thenReturn(flowOf(false))
        whenever(appPreferences.skipPluginVersionCheck).thenReturn(flowOf(false))
        return PluginManager(
            context,
            metaStore,
            mock<MediaCacheRepository>(),
            mock<LyricRepository>(),
            mock<DownloadedTrackDao>(),
            mock<LocalFilePlugin>(),
            PluginAppVersionGate(),
            "1.0.0",
            mock<PluginMetadataCacheGateway>(),
            appPreferences,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun PluginManager.setLoadedPluginsForTest(plugins: List<LoadedPlugin>) {
        val field = PluginManager::class.java.getDeclaredField("_plugins")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<LoadedPlugin>>).value = plugins
    }

    private fun plugin(platform: String): LoadedPlugin {
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
        return plugin
    }
}
