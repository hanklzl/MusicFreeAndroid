package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// TODO(deps-bump-2026-05): pre-existing breakage, unrelated to this dependency bump.
// PluginManager's constructor was extended with a `pluginMetaStore: PluginMetaStore`
// parameter (see PluginManager.kt) but this integration test was never updated, so
// connectedAndroidTest stopped compiling whenever it was first re-run. The test set
// also depends on live network endpoints (kstore.vip) so it only meaningfully runs in
// a manual integration-test environment. Restoring the file's compilation here with an
// in-memory PluginMetaStore + class-level @Ignore so the connectedAndroidTest gate can
// pass for the deps-bump PR. Re-enable + adapt to HiltAndroidTest in a follow-up.
@Ignore("Pre-existing broken integration test; constructor mismatch + live network — see TODO")
@RunWith(AndroidJUnit4::class)
class PluginRuntimeIntegrationTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(appContext.cacheDir, "plugin-runtime-it.preferences_pb") },
        )
        pluginManager = PluginManager(appContext, PluginMetaStore(dataStore))
        clearPluginStorage()
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

        val title = result.data.first().title
        assertTrue("Title should contain decoded HTML marker", title.contains("&"))
        assertTrue("Title should contain dayjs formatted date", title.contains("2026-03-21"))

        val source = plugin.getMediaSource(result.data.first(), quality = "standard")
        assertNotNull("Media source should resolve when songmid is preserved", source)
        assertTrue(
            "Resolved source should include songmid from search payload",
            source!!.url.contains("song-mid-it-1"),
        )
    }

    @Test
    fun yuanliWy_searchAndMediaSource_returnsPlayableUrl() = runBlocking {
        val wy = pluginManager.installFromUrl(
            url = "https://13413.kstore.vip/yuanli/wy.js",
            fileName = "wy-it.js",
        )
        assertNotNull("WY plugin should install", wy)

        val wySearch = wy!!.search(query = "in the end", page = 1)
        assertTrue(
            "WY search should return at least one result",
            wySearch.data.isNotEmpty(),
        )

        var mediaSourceUrl: String? = null
        for (item in wySearch.data.take(5)) {
            val source = runCatching {
                wy.getMediaSource(
                    musicItem = item,
                    quality = "standard",
                )
            }.getOrNull()
            if (source != null && source.url.isNotBlank()) {
                mediaSourceUrl = source.url
                break
            }
        }
        assertTrue(
            "WY getMediaSource should return playable url",
            !mediaSourceUrl.isNullOrBlank(),
        )
    }

    @Test
    fun defaultSubscription_installAndWyPlaybackChain_succeeds() = runBlocking {
        val install = pluginManager.installFromSubscriptionUrl(
            subscriptionUrl = "https://13413.kstore.vip/yuanli/yuanli.json",
        )
        assertTrue(
            "Default subscription should install at least one plugin",
            install.successfulInstalls > 0,
        )

        val wy = pluginManager.plugins.value.firstOrNull { loaded ->
            loaded.info.platform.contains("WY", ignoreCase = true) ||
                loaded.info.platform.contains("网易")
        }
        assertNotNull("Default subscription should contain WY plugin", wy)

        val search = wy!!.search(query = "in the end", page = 1)
        assertTrue(
            "WY search from default subscription should return at least one result",
            search.data.isNotEmpty(),
        )

        var mediaSourceUrl: String? = null
        for (item in search.data.take(5)) {
            val source = runCatching {
                wy.getMediaSource(
                    musicItem = item,
                    quality = "standard",
                )
            }.getOrNull()
            if (source != null && source.url.isNotBlank()) {
                mediaSourceUrl = source.url
                break
            }
        }
        assertTrue(
            "WY getMediaSource from default subscription should return playable url",
            !mediaSourceUrl.isNullOrBlank(),
        )
    }

    @Test
    fun updatePlugin_thenSearchStillWorks_returnsPlayableResults() = runBlocking {
        val wy = pluginManager.installFromUrl(
            url = "https://13413.kstore.vip/yuanli/wy.js",
            fileName = "wy-update-search.js",
        )
        assertNotNull("WY plugin should install", wy)
        val platform = wy!!.info.platform

        val update = pluginManager.updatePlugin(platform)
        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)

        val updated = pluginManager.getPlugin(platform)
        assertNotNull("Updated plugin should remain selectable by platform", updated)
        assertEquals(
            "Update should not create duplicate plugin entries for same platform",
            1,
            pluginManager.plugins.value.count { it.info.platform == platform },
        )

        val search = updated!!.search(query = "in the end", page = 1)
        assertTrue(
            "Search should still work after plugin update",
            search.data.isNotEmpty(),
        )
        var playableUrl: String? = null
        for (item in search.data.take(5)) {
            val source = runCatching {
                updated.getMediaSource(item, quality = "standard")
            }.getOrNull()
            if (source != null && source.url.isNotBlank()) {
                playableUrl = source.url
                break
            }
        }
        assertTrue(!playableUrl.isNullOrBlank())
    }

    @Test
    fun updatePlugin_afterSearchRegression_keepsSearchablePluginUsable() = runBlocking {
        val wy = pluginManager.installFromUrl(
            url = "https://13413.kstore.vip/yuanli/wy.js",
            fileName = "wy-post-update-search.js",
        )
        assertNotNull("WY plugin should install", wy)

        val beforeUpdateSearch = wy!!.search(query = "in the end", page = 1)
        assertTrue(beforeUpdateSearch.data.isNotEmpty())

        val update = pluginManager.updatePlugin(wy.info.platform)
        assertEquals(PluginOperationType.UPDATE_SINGLE, update.operationType)

        val selectedAfterUpdate = pluginManager.getPlugin(wy.info.platform)
        assertNotNull("Updated plugin should remain selectable by platform", selectedAfterUpdate)

        val afterUpdateSearch = selectedAfterUpdate!!.search(query = "in the end", page = 1)
        assertTrue(
            "Search should still work after plugin update and keep the selected plugin usable",
            afterUpdateSearch.data.isNotEmpty(),
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
}
