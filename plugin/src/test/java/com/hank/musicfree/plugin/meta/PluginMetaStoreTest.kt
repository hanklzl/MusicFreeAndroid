package com.hank.musicfree.plugin.meta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.crypto.KeyGenerator
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
    fun `gateway credentials are removed from ordinary preferences and migrated to private storage`() = runBlocking {
        val secret = """{"appId":"synthetic-app","signSecret":"synthetic-secret","appVersion":"test","deviceId":"synthetic-device"}"""
        val key = KeyGenerator.getInstance("AES").generateKey()
        val secure = GatewayCredentialStore(tmpFolder.newFolder()) { key }
        val protectedStore = PluginMetaStore(dataStore, secure)
        protectedStore.setUserVariables("元力QQ", mapOf(GatewayCredentialStore.VARIABLE to secret, "other" to "value"))
        val preferenceKey = stringPreferencesKey("user_variables_元力QQ")
        assertFalse(dataStore.data.first()[preferenceKey]!!.contains("synthetic-secret"))
        assertFalse(dataStore.data.first()[preferenceKey]!!.contains(GatewayCredentialStore.VARIABLE))
        assertEquals(secret, protectedStore.getUserVariables("元力QQ").first()[GatewayCredentialStore.VARIABLE])
        protectedStore.setUserVariables("元力QQ", emptyMap())
        assertNull(secure.read("元力QQ"))

        // Simulate a pre-upgrade ordinary DataStore entry; migration must not export it again.
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(), mapOf(GatewayCredentialStore.VARIABLE to secret),
        )
        dataStore.edit { it[preferenceKey] = encoded }
        assertEquals(secret, protectedStore.getUserVariables("元力QQ").first()[GatewayCredentialStore.VARIABLE])
        assertFalse(dataStore.data.first()[preferenceKey]!!.contains("synthetic-secret"))
    }

    @Test
    fun `failed preference commit does not delete private credentials`() = runBlocking {
        val secret = syntheticCredentials()
        val key = KeyGenerator.getInstance("AES").generateKey()
        val secure = GatewayCredentialStore(tmpFolder.newFolder()) { key }
        secure.write("元力QQ", secret)
        val preferenceKey = stringPreferencesKey("user_variables_元力QQ")
        dataStore.edit { it[preferenceKey] = encodeVariables(mapOf(GatewayCredentialStore.VARIABLE to secret)) }
        val failingStore = object : DataStore<Preferences> by dataStore {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw java.io.IOException("synthetic write failure")
            }
        }
        try {
            PluginMetaStore(failingStore, secure).setUserVariables("元力QQ", emptyMap())
            fail("must fail")
        } catch (_: java.io.IOException) { }
        assertEquals(secret, secure.read("元力QQ"))
        // A successful retry clears both stores; subsequent reads cannot resurrect the legacy.
        val protectedStore = PluginMetaStore(dataStore, secure)
        protectedStore.setUserVariables("元力QQ", emptyMap())
        assertNull(protectedStore.getUserVariables("元力QQ").first()[GatewayCredentialStore.VARIABLE])
    }

    @Test
    fun `failed private replacement preserves old ciphertext and removes legacy plaintext`() = runBlocking {
        val key = KeyGenerator.getInstance("AES").generateKey()
        var failEncryption = false
        val secure = GatewayCredentialStore(tmpFolder.newFolder()) {
            check(!failEncryption) { "synthetic unavailable key" }
            key
        }
        val secret = syntheticCredentials()
        secure.write("元力QQ", secret)
        val preferenceKey = stringPreferencesKey("user_variables_元力QQ")
        dataStore.edit { it[preferenceKey] = encodeVariables(mapOf(GatewayCredentialStore.VARIABLE to secret)) }
        failEncryption = true
        try {
            PluginMetaStore(dataStore, secure).setUserVariables("元力QQ", mapOf(
                GatewayCredentialStore.VARIABLE to syntheticCredentials("synthetic-replacement"),
                "other" to "saved",
            ))
            fail("must fail")
        } catch (e: IllegalStateException) {
            assertEquals("GATEWAY_CREDENTIALS_WRITE_FAILED", e.message)
        }
        failEncryption = false
        assertEquals(secret, secure.read("元力QQ"))
        assertFalse(dataStore.data.first()[preferenceKey]!!.contains(GatewayCredentialStore.VARIABLE))
        secure.write("元力QQ", null)
        assertNull(PluginMetaStore(dataStore, secure).getUserVariables("元力QQ").first()[GatewayCredentialStore.VARIABLE])
    }

    @Test
    fun `retired platform legacy credentials are scrubbed without exposing them`() = runBlocking {
        val preferenceKey = stringPreferencesKey("user_variables_retired")
        dataStore.edit { it[preferenceKey] = encodeVariables(mapOf(
            GatewayCredentialStore.VARIABLE to syntheticCredentials(), "other" to "retained",
        )) }
        assertEquals(mapOf("other" to "retained"), store.getUserVariables("retired").first())
        assertFalse(dataStore.data.first()[preferenceKey]!!.contains(GatewayCredentialStore.VARIABLE))
    }

    private fun syntheticCredentials(secret: String = "synthetic-secret") =
        """{"appId":"synthetic-app","signSecret":"$secret","appVersion":"test","deviceId":"synthetic-device"}"""

    private fun encodeVariables(variables: Map<String, String>) = kotlinx.serialization.json.Json.encodeToString(
        kotlinx.serialization.serializer<Map<String, String>>(), variables,
    )

    @Test
    fun `gateway config cannot fall back to plaintext without private storage`() = runBlocking {
        try { store.setUserVariables("元力QQ", mapOf(GatewayCredentialStore.VARIABLE to "synthetic")); fail("must reject") }
        catch (e: IllegalStateException) { assertEquals("GATEWAY_CREDENTIAL_STORE_REQUIRED", e.message) }
        assertNull(dataStore.data.first()[stringPreferencesKey("user_variables_元力QQ")])
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
