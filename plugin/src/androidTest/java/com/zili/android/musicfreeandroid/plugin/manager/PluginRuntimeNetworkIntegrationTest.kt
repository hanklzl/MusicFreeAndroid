package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Live-network integration tests for PluginManager. Depends on
 * `https://13413.kstore.vip/yuanli/...` being reachable. CI default
 * channel SKIPS these via Assume.assumeTrue; pass `-Pintegration` to
 * Gradle to enable.
 */
@RunWith(AndroidJUnit4::class)
class PluginRuntimeNetworkIntegrationTest {

    private lateinit var appContext: Context
    private lateinit var pluginManager: PluginManager
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        val arg = InstrumentationRegistry.getArguments().getString("pluginNetworkTests")
        Assume.assumeTrue(
            "Skipping plugin network integration tests; pass -Pintegration to enable.",
            arg == "true",
        )

        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testPreferencesFile("plugin-runtime-network-it") },
        )
        pluginManager = PluginManager(appContext, PluginMetaStore(dataStore))
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
        assertEquals("Plugin update should succeed", 1, update.successCount)
        assertEquals("Plugin update should not report failures", 0, update.failureCount)

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
        assertEquals("Plugin update should succeed", 1, update.successCount)
        assertEquals("Plugin update should not report failures", 0, update.failureCount)

        val selectedAfterUpdate = pluginManager.getPlugin(wy.info.platform)
        assertNotNull("Updated plugin should remain selectable by platform", selectedAfterUpdate)

        val afterUpdateSearch = selectedAfterUpdate!!.search(query = "in the end", page = 1)
        assertTrue(
            "Search should still work after plugin update and keep the selected plugin usable",
            afterUpdateSearch.data.isNotEmpty(),
        )
    }

    private fun clearPluginStorage() = runBlocking {
        val pluginsDir = File(appContext.filesDir, "plugins")
        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.forEach { it.delete() }
        }
        pluginManager.loadAllPlugins()
    }

    private fun testPreferencesFile(prefix: String): File =
        File(appContext.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb")
}
