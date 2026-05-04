package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * MockWebServer-backed lifecycle tests for PluginManager.installFromUrl
 * and updatePlugin. Covers the orchestration paths (HTTP fetch, disk
 * write, state-flow registration, refetch + replace) without depending
 * on real plugin scripts or real network. Always runs in
 * :plugin:connectedAndroidTest (CI default channel).
 */
@RunWith(AndroidJUnit4::class)
class PluginManagerHttpLifecycleTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(appContext.cacheDir, "plugin-http-lifecycle-it.preferences_pb") },
        )
        pluginManager = PluginManager(appContext, PluginMetaStore(dataStore))
        clearPluginStorage()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun installFromUrl_writesPluginAndLoadsMeta() = runBlocking {
        server.enqueue(MockResponse().setBody(scriptForVersion("1.0.0")))

        val plugin = pluginManager.installFromUrl(
            url = server.url("/mockws.js").toString(),
            fileName = "mockws-lifecycle.js",
        )

        assertNotNull("MockWebServer-served plugin should install", plugin)
        assertEquals(PLATFORM, plugin!!.info.platform)
        assertEquals("1.0.0", plugin.info.version)

        assertTrue(
            "Installed plugin file should be on disk",
            File(plugin.filePath).exists(),
        )
        assertNotNull(
            "Manager should expose the plugin via getPlugin(platform)",
            pluginManager.getPlugin(PLATFORM),
        )
        assertEquals(
            "plugins state-flow should contain exactly one entry for this platform",
            1,
            pluginManager.plugins.value.count { it.info.platform == PLATFORM },
        )
    }

    @Test
    fun updatePlugin_refetchesAndReplaces() = runBlocking {
        server.enqueue(MockResponse().setBody(scriptForVersion("1.0.0")))
        val installed = pluginManager.installFromUrl(
            url = server.url("/mockws.js").toString(),
            fileName = "mockws-update.js",
        )
        assertNotNull("Initial install should succeed", installed)
        assertEquals("1.0.0", installed!!.info.version)

        server.enqueue(MockResponse().setBody(scriptForVersion("1.0.1")))
        val update = pluginManager.updatePlugin(PLATFORM)

        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)
        assertEquals(
            "Update should report exactly one success",
            1,
            update.successCount,
        )

        val updated = pluginManager.getPlugin(PLATFORM)
        assertNotNull("Updated plugin should remain selectable by platform", updated)
        assertEquals(
            "Updated plugin should report the new version",
            "1.0.1",
            updated!!.info.version,
        )
        assertEquals(
            "Update should not create duplicate plugin entries",
            1,
            pluginManager.plugins.value.count { it.info.platform == PLATFORM },
        )
    }

    private fun clearPluginStorage() = runBlocking {
        val pluginsDir = File(appContext.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { it.delete() }
        }
        pluginManager.loadAllPlugins()
    }

    private fun scriptForVersion(version: String): String = """
        module.exports = {
          platform: '$PLATFORM',
          version: '$version',
          supportedSearchType: ['music'],
          async search(query, page) {
            return { isEnd: true, data: [] };
          },
          async getMediaSource(musicItem, quality) {
            return null;
          }
        };
    """.trimIndent()

    private companion object {
        const val PLATFORM = "mockws-lifecycle"
    }
}
