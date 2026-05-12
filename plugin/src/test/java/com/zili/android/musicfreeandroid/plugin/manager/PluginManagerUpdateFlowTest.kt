package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import com.zili.android.musicfreeandroid.data.repository.MediaCacheRepository
import com.zili.android.musicfreeandroid.data.repository.PluginMetadataCacheGateway
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.local.LocalFilePlugin
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import com.zili.android.musicfreeandroid.plugin.runtime.PluginAppVersionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluginManagerUpdateFlowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createManager(): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        val pluginMetaStore = mock<PluginMetaStore>()
        whenever(pluginMetaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        whenever(pluginMetaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(pluginMetaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        val appPreferences = mock<AppPreferences>()
        whenever(appPreferences.lazyLoadPlugins).thenReturn(flowOf(false))
        return PluginManager(
            context,
            pluginMetaStore,
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

    @Test
    fun `updatePlugin returns internal error when plugin is missing`() = runTest {
        val manager = createManager()

        val result = manager.updatePlugin("missing-plugin")

        assertEquals(PluginOperationType.UPDATE_SINGLE, result.operationType)
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(PluginOperationErrorCode.INTERNAL_ERROR, result.failures.first().errorCode)
    }

    @Test
    fun `updateFromSubscriptionUrl returns source invalid when url is blank`() = runTest {
        val manager = createManager()

        val result = manager.updateFromSubscriptionUrl("   ")

        assertEquals(PluginOperationType.UPDATE_SUBSCRIPTION, result.operationType)
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(PluginOperationErrorCode.SOURCE_INVALID, result.failures.first().errorCode)
    }

    @Test
    fun `updateAllPlugins succeeds with empty target set`() = runTest {
        val manager = createManager()

        val result = manager.updateAllPlugins()

        assertEquals(PluginOperationType.UPDATE_ALL, result.operationType)
        assertTrue(result.targetPlugins.isEmpty())
        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `getLyricSearchablePlugins includes lyric and legacy plugins`() = runTest {
        val manager = createManager()
        manager.setLoadedPluginsForTest(
            listOf(
                plugin("music-only", listOf("music")),
                plugin("lyric-only", listOf("lyric")),
                plugin("legacy", emptyList()),
                plugin("declared-empty", emptyList(), supportedSearchTypeDeclared = true),
            ),
        )

        val searchable = manager.getLyricSearchablePlugins().first()

        assertEquals(listOf("lyric-only", "legacy"), searchable.map { it.info.platform })
    }

    @Suppress("UNCHECKED_CAST")
    private fun PluginManager.setLoadedPluginsForTest(plugins: List<LoadedPlugin>) {
        val field = PluginManager::class.java.getDeclaredField("_plugins")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<LoadedPlugin>>).value = plugins
    }

    private fun plugin(
        platform: String,
        supportedSearchType: List<String>,
        supportedSearchTypeDeclared: Boolean = supportedSearchType.isNotEmpty(),
    ): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = supportedSearchType,
                supportedSearchTypeDeclared = supportedSearchTypeDeclared,
            ),
        )
        return plugin
    }
}
