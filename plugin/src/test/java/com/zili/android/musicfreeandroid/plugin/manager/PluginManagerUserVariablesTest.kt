package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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

    private fun manager(metaStore: PluginMetaStore): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        whenever(context.packageName).thenReturn("com.test")
        return PluginManager(context, metaStore)
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
