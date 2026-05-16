package com.hank.musicfree.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.plugin.api.musicItems
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.runtime.PluginAppVersionGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Local-only integration tests for PluginManager runtime shims.
 * No network access required — all plugins are loaded from temporary files.
 * Always runs in :plugin:connectedAndroidTest (CI default channel).
 */
@RunWith(AndroidJUnit4::class)
class PluginRuntimeLocalIntegrationTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testPreferencesFile("plugin-runtime-local-it") },
        )
        val prefsDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testPreferencesFile("plugin-runtime-local-it-prefs") },
        )
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
        )
        clearPluginStorage()
    }

    @After
    fun tearDown() {
        runBlocking {
            if (::pluginManager.isInitialized) {
                pluginManager.uninstallAllPlugins()
            }
        }
        dataStoreScope.cancel()
    }

    @Test
    fun localRuntimeShimPlugin_search_executesWithoutNotFunctionErrors() = runBlocking {
        val pluginFile = File.createTempFile("runtime-shim-it-", ".js", appContext.cacheDir)
        pluginFile.writeText(runtimeShimScript)

        val plugin = pluginManager.installFromFile(pluginFile)
        assertNotNull("Local runtime shim plugin should install", plugin)

        val result = plugin!!.search(query = "in the end", page = 1)
        assertTrue(
            "Search should return at least one item when runtime shims are valid",
            result.data.isNotEmpty(),
        )

        val musicItem = result.musicItems().first()
        val title = musicItem.title
        assertTrue("Title should contain decoded HTML marker", title.contains("&"))
        assertTrue("Title should contain dayjs formatted date", title.contains("2026-03-21"))

        val source = plugin.getMediaSource(musicItem, quality = "standard")
        assertNotNull("Media source should resolve when songmid is preserved", source)
        assertTrue(
            "Resolved source should include songmid from search payload",
            source!!.url.contains("song-mid-it-1"),
        )
    }

    @Test
    fun updatePlugin_withoutSource_returnsMissingSource_andKeepsPluginUsable() = runBlocking {
        val pluginFile = File.createTempFile("runtime-no-src-", ".js", appContext.cacheDir)
        pluginFile.writeText(runtimeShimScript)

        val plugin = pluginManager.installFromFile(pluginFile)
        assertNotNull("Runtime test plugin should install", plugin)

        val update = pluginManager.updatePlugin(plugin!!.info.platform)
        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)
        assertEquals(0, update.successCount)
        assertEquals(1, update.failureCount)
        assertEquals(
            PluginOperationErrorCode.MISSING_UPDATE_SOURCE,
            update.failures.first().errorCode,
        )

        val search = plugin.search(query = "in the end", page = 1)
        assertTrue(
            "Plugin should remain usable after update failure",
            search.data.isNotEmpty(),
        )
    }

    @Test
    fun updateAllPlugins_withoutSources_returnsFailureSummary() = runBlocking {
        val pluginFile1 = File.createTempFile("runtime-update-all-1-", ".js", appContext.cacheDir)
        pluginFile1.writeText(runtimeShimScript)
        val pluginFile2 = File.createTempFile("runtime-update-all-2-", ".js", appContext.cacheDir)
        pluginFile2.writeText(
            runtimeShimScript.replace(
                "runtime-shim-it",
                "runtime-shim-it-2",
            ),
        )

        val first = pluginManager.installFromFile(pluginFile1)
        val second = pluginManager.installFromFile(pluginFile2)
        assertNotNull("First runtime plugin should install", first)
        assertNotNull("Second runtime plugin should install", second)

        val result = pluginManager.updateAllPlugins()
        assertEquals(PluginOperationType.UPDATE_ALL, result.operationType)
        assertEquals(0, result.successCount)
        assertEquals(2, result.failureCount)
        assertTrue(
            "All failures should be missing source for local runtime plugins",
            result.failures.all { it.errorCode == PluginOperationErrorCode.MISSING_UPDATE_SOURCE },
        )
    }

    private fun clearPluginStorage() = runBlocking {
        val pluginsDir = File(appContext.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { it.delete() }
        }
        pluginManager.loadAllPlugins()
    }

    private val runtimeShimScript = """
        const axios = require('axios');
        const CryptoJS = require('crypto-js');
        const qs = require('qs');
        const he = require('he');
        const dayjs = require('dayjs');
        const bigInt = require('big-integer');

        module.exports = {
          platform: 'runtime-shim-it',
          version: '1.0.0',
          supportedSearchType: ['music'],
          async search(query, page, type) {
            const req = axios.default({ method: 'noop', url: 'https://example.com' });
            const q = qs.stringify({ q: query, page: page });
            const decoded = he.decode('&amp;');
            const date = dayjs('2026-03-21').format('YYYY-MM-DD');
            const mod = bigInt('2', 10).modPow(bigInt('5', 10), bigInt('13', 10)).toString(10);
            const encrypted = CryptoJS.AES.encrypt(
              CryptoJS.enc.Utf8.parse('abc'),
              CryptoJS.enc.Utf8.parse('0123456789abcdef'),
              {
                iv: CryptoJS.enc.Utf8.parse('0102030405060708'),
                mode: CryptoJS.mode.CBC
              }
            ).toString();
            return {
              isEnd: true,
              data: [{
                id: 'it-1',
                platform: 'runtime-shim-it',
                songmid: 'song-mid-it-1',
                title: decoded + '|' + q + '|' + date + '|' + mod + '|' + req.status + '|' + typeof encrypted,
                artist: 'integration',
                album: 'integration',
                duration: 1,
                url: 'https://example.com'
              }]
            };
          },
          async getMediaSource(musicItem, quality) {
            if (!musicItem.songmid) {
              throw new Error('songmid missing');
            }
            return {
              url: 'https://example.com/play/' + musicItem.songmid + '?q=' + quality
            };
          }
        };
    """.trimIndent()

    private fun testPreferencesFile(prefix: String): File =
        File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb")
}
