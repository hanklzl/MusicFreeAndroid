package com.hank.musicfree.plugin.meta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PluginMetaStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PluginMetaStore
    private lateinit var logger: RecordingLogger

    @Before
    fun setup() {
        logger = RecordingLogger()
        MfLog.install(logger)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmpFolder.newFile("test_plugin_meta.preferences_pb")
        }
        store = PluginMetaStore(dataStore)
    }

    @After
    fun tearDown() {
        MfLog.resetForTest()
        scope.cancel()
    }

    @Test
    fun `new plugin is enabled by default`() = runBlocking {
        assertTrue(store.isPluginEnabled("netease").first())
        assertTrue(store.disabledPlugins.first().isEmpty())
    }

    @Test
    fun `disable and re-enable plugin`() = runBlocking {
        store.setPluginEnabled("netease", false)
        assertFalse(store.isPluginEnabled("netease").first())
        assertTrue(store.disabledPlugins.first().contains("netease"))

        store.setPluginEnabled("netease", true)
        assertTrue(store.isPluginEnabled("netease").first())
        assertFalse(store.disabledPlugins.first().contains("netease"))
    }

    @Test
    fun `disable multiple plugins independently`() = runBlocking {
        store.setPluginEnabled("netease", false)
        store.setPluginEnabled("qq", false)
        assertEquals(setOf("netease", "qq"), store.disabledPlugins.first())

        store.setPluginEnabled("netease", true)
        assertEquals(setOf("qq"), store.disabledPlugins.first())
    }

    @Test
    fun `plugin order defaults to empty`() = runBlocking {
        assertTrue(store.pluginOrder.first().isEmpty())
    }

    @Test
    fun `set and get plugin order`() = runBlocking {
        val order = listOf("qq", "netease", "kugou")
        store.setPluginOrder(order)
        assertEquals(order, store.pluginOrder.first())

        val event = logger.events.single { it.event == "plugin_metadata_write_success" }
        assertEquals("set_plugin_order", event.fields["operation"])
        assertEquals("success", event.fields["result"])
        assertEquals(3, event.fields["count"])
    }

    @Test
    fun `alternative plugin defaults to empty`() = runBlocking {
        assertTrue(store.alternativePlugins.first().isEmpty())
        assertNull(store.getAlternativePlugin("source").first())
    }

    @Test
    fun `set and clear alternative plugin`() = runBlocking {
        store.setAlternativePlugin("source", "target")
        assertEquals("target", store.getAlternativePlugin("source").first())
        assertEquals(mapOf("source" to "target"), store.alternativePlugins.first())

        store.setAlternativePlugin("source", null)
        assertNull(store.getAlternativePlugin("source").first())
        assertTrue(store.alternativePlugins.first().isEmpty())
    }

    @Test
    fun `self alternative plugin is stored as cleared`() = runBlocking {
        store.setAlternativePlugin("source", "source")
        assertNull(store.getAlternativePlugin("source").first())
        assertTrue(store.alternativePlugins.first().isEmpty())
    }

    @Test
    fun `user variables default to empty`() = runBlocking {
        assertTrue(store.getUserVariables("netease").first().isEmpty())
    }

    @Test
    fun `set and get user variables`() = runBlocking {
        val vars = mapOf("cookie" to "abc123", "token" to "xyz")
        store.setUserVariables("netease", vars)
        assertEquals(vars, store.getUserVariables("netease").first())

        val event = logger.events.single { it.event == "plugin_metadata_write_success" }
        assertEquals("set_user_variables", event.fields["operation"])
        assertEquals("success", event.fields["result"])
        assertEquals("netease", event.fields["platform"])
        assertEquals(2, event.fields["count"])
    }

    @Test
    fun `user variables are per-plugin`() = runBlocking {
        store.setUserVariables("netease", mapOf("a" to "1"))
        store.setUserVariables("qq", mapOf("b" to "2"))
        assertEquals(mapOf("a" to "1"), store.getUserVariables("netease").first())
        assertEquals(mapOf("b" to "2"), store.getUserVariables("qq").first())
    }

    @Test
    fun `subscriptions default to empty`() = runBlocking {
        assertTrue(store.subscriptions.first().isEmpty())
    }

    @Test
    fun `add subscription`() = runBlocking {
        store.addSubscription("默认源", "https://example.com/plugins.json")
        val subs = store.subscriptions.first()
        assertEquals(1, subs.size)
        assertEquals("默认源", subs[0].name)
        assertEquals("https://example.com/plugins.json", subs[0].url)
    }

    @Test
    fun `update subscription`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.updateSubscription(0, "源B", "https://b.com/p.json")
        val subs = store.subscriptions.first()
        assertEquals("源B", subs[0].name)
        assertEquals("https://b.com/p.json", subs[0].url)
    }

    @Test
    fun `remove subscription`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.addSubscription("源B", "https://b.com/p.json")
        store.removeSubscription(0)
        val subs = store.subscriptions.first()
        assertEquals(1, subs.size)
        assertEquals("源B", subs[0].name)
    }

    @Test
    fun `update out of range index is no-op`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.updateSubscription(5, "源X", "https://x.com/p.json")
        assertEquals(1, store.subscriptions.first().size)
        assertEquals("源A", store.subscriptions.first()[0].name)
    }

    @Test
    fun `remove out of range index is no-op`() = runBlocking {
        store.addSubscription("源A", "https://a.com/p.json")
        store.removeSubscription(5)
        assertEquals(1, store.subscriptions.first().size)
    }

    private data class RecordedEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = mutableListOf<RecordedEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedEvent(category, event, fields)
        }

        override fun flush() = Unit
    }
}
