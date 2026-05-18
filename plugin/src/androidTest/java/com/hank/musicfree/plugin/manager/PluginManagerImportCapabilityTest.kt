package com.hank.musicfree.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.plugin.engine.WebDavShim
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.runtime.PluginAppVersionGate
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PluginManagerImportCapabilityTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(appContext.cacheDir, "plugin-import-capability-${UUID.randomUUID()}.preferences_pb") },
        )
        val prefsDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(appContext.cacheDir, "plugin-import-capability-prefs-${UUID.randomUUID()}.preferences_pb") },
        )
        val baseClient = OkHttpClient.Builder().build()
        pluginManager = PluginManager(
            appContext,
            PluginMetaStore(dataStore),
            stubMediaCacheRepository(),
            stubLyricRepository(),
            stubDownloadedTrackDao(),
            stubLocalFilePlugin(),
            PluginAppVersionGate(),
            "1.0.0",
            InMemoryPluginMetadataCacheGateway(),
            com.hank.musicfree.data.datastore.AppPreferences(prefsDataStore),
            baseClient,
            WebDavShim(baseClient),
        )
        clearPluginStorage()
    }

    @Test
    fun localPluginWithImportMusicSheet_exposesCapabilityAndBackfillsPlatform() = runBlocking {
        val pluginFile = File.createTempFile("import-capability-", ".js", appContext.cacheDir)
        pluginFile.writeText(importCapabilityScript)

        val plugin = pluginManager.installFromFile(pluginFile)

        assertNotNull("Local import-capable plugin should install", plugin)
        plugin ?: return@runBlocking
        assertTrue("Plugin should expose importMusicSheet", "importMusicSheet" in plugin.info.supportedMethods)

        val items = plugin.importMusicSheet("https://example.com/sheet").orEmpty()

        assertEquals(listOf("import-capability-it", "import-capability-it"), items.map { it.platform })
        assertEquals(listOf("Imported One", "Imported Two"), items.map { it.title })
    }

    private fun clearPluginStorage() = runBlocking {
        val pluginsDir = File(appContext.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { it.delete() }
        }
        pluginManager.loadAllPlugins()
    }

    private val importCapabilityScript = """
        module.exports = {
          platform: 'import-capability-it',
          version: '1.0.0',
          supportedSearchType: ['music'],
          async importMusicSheet(urlLike) {
            if (!urlLike) return [];
            return [
              {
                id: 'imported-1',
                platform: '',
                title: 'Imported One',
                artist: 'Artist',
                duration: 1
              },
              {
                id: 'imported-2',
                title: 'Imported Two',
                artist: 'Artist',
                duration: 2
              }
            ];
          }
        };
    """.trimIndent()
}
