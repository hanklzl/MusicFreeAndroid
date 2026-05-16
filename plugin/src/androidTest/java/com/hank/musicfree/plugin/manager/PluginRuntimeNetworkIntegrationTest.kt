package com.hank.musicfree.plugin.manager

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.plugin.api.musicItems
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
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
    private lateinit var logger: RecordingLogger
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        val arg = InstrumentationRegistry.getArguments().getString("pluginNetworkTests")
        Assume.assumeTrue(
            "Skipping plugin network integration tests; pass -Pintegration to enable.",
            arg == "true",
        )

        appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        logger = RecordingLogger()
        MfLog.install(logger)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testPreferencesFile("plugin-runtime-network-it") },
        )
        val prefsDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testPreferencesFile("plugin-runtime-network-it-prefs") },
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
        MfLog.resetForTest()
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

        val mediaSource = firstPlayableMediaSource(
            plugin = wy,
            items = wySearch.musicItems().take(5),
            quality = "standard",
        )
        assertTrue(
            failureMessage("WY getMediaSource should return playable url", mediaSource),
            !mediaSource.playableUrl.isNullOrBlank(),
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

        val mediaSource = firstPlayableMediaSource(
            plugin = wy,
            items = search.musicItems().take(5),
            quality = "standard",
        )
        assertTrue(
            failureMessage("WY getMediaSource from default subscription should return playable url", mediaSource),
            !mediaSource.playableUrl.isNullOrBlank(),
        )
    }

    @Test
    fun defaultSubscription_healthyRnMatrixPlugins_returnPlayableUrls() = runBlocking {
        val install = pluginManager.installFromSubscriptionUrl(
            subscriptionUrl = "https://13413.kstore.vip/yuanli/yuanli.json",
        )
        assertTrue(
            "Default subscription should install at least one plugin",
            install.successfulInstalls > 0,
        )

        val expectedHealthyPlatforms = listOf("元力KW", "元力KG", "元力QQ", "bilibili")
        val pluginsByPlatform = pluginManager.plugins.value.associateBy { it.info.platform }
        for (platform in expectedHealthyPlatforms) {
            val plugin = pluginsByPlatform[platform]
            assertNotNull("Default subscription should contain $platform", plugin)

            val search = plugin!!.search(query = "in the end", page = 1)
            assertTrue(
                "$platform search should return at least one result",
                search.data.isNotEmpty(),
            )

            val mediaSource = firstPlayableMediaSource(
                plugin = plugin,
                items = search.musicItems().take(2),
                quality = "standard",
            )
            assertTrue(
                failureMessage("$platform getMediaSource should return playable url", mediaSource),
                !mediaSource.playableUrl.isNullOrBlank(),
            )
        }
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
        val mediaSource = firstPlayableMediaSource(
            plugin = updated,
            items = search.musicItems().take(5),
            quality = "standard",
        )
        assertTrue(
            failureMessage("Updated WY getMediaSource should return playable url", mediaSource),
            !mediaSource.playableUrl.isNullOrBlank(),
        )
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

    private suspend fun firstPlayableMediaSource(
        plugin: LoadedPlugin,
        items: List<MusicItem>,
        quality: String,
    ): MediaSourceProbe {
        val attempts = mutableListOf<MediaSourceAttempt>()
        for (item in items) {
            val outcome = runCatching {
                plugin.getMediaSource(
                    musicItem = item,
                    quality = quality,
                )
            }
            val source = outcome.getOrNull()
            attempts += MediaSourceAttempt(
                id = item.id,
                title = item.title,
                url = source?.url,
                hasUrl = source?.url?.isNotBlank() == true,
                error = outcome.exceptionOrNull()?.let { "${it::class.java.simpleName}: ${it.message}" },
            )
            if (source != null && source.url.isNotBlank()) {
                return MediaSourceProbe(playableUrl = source.url, attempts = attempts)
            }
        }
        return MediaSourceProbe(playableUrl = null, attempts = attempts)
    }

    private fun failureMessage(prefix: String, probe: MediaSourceProbe): String {
        return buildString {
            appendLine(prefix)
            appendLine("media attempts:")
            probe.attempts.forEach { attempt ->
                appendLine(
                    "  id=${attempt.id} title=${attempt.title.take(80)} " +
                        "hasUrl=${attempt.hasUrl} url=${attempt.url?.take(120)} error=${attempt.error}",
                )
            }
            appendLine("diagnostic logs:")
            appendLine(logger.diagnosticTail())
        }
    }

    private data class MediaSourceProbe(
        val playableUrl: String?,
        val attempts: List<MediaSourceAttempt>,
    )

    private data class MediaSourceAttempt(
        val id: String,
        val title: String,
        val url: String?,
        val hasUrl: Boolean,
        val error: String?,
    )

    private data class RecordedEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
        val throwable: Throwable?,
    )

    private class RecordingLogger : MfLogger {
        private val events = mutableListOf<RecordedEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedEvent(category, event, fields, throwable = null)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedEvent(category, event, fields, throwable = null)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedEvent(category, event, fields, throwable)
        }

        override fun flush() = Unit

        fun diagnosticTail(): String {
            val relevant = setOf(
                "axios_request",
                "axios_response",
                "axios_request_failed",
                "plugin_api_call_success",
                "plugin_api_call_failed",
            )
            return events
                .filter { it.event in relevant }
                .takeLast(32)
                .joinToString("\n") { recorded ->
                    val fields = recorded.fields.entries.joinToString(" ") { (key, value) ->
                        "$key=${value.toString().replace('\n', ' ').take(220)}"
                    }
                    val throwable = recorded.throwable?.let { " throwable=${it::class.java.simpleName}:${it.message}" }
                        .orEmpty()
                    "${recorded.event} $fields$throwable"
                }
                .ifBlank { "<no diagnostic logs captured>" }
        }
    }
}
