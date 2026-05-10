package com.zili.android.musicfreeandroid.plugin.meta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.plugin.di.PluginMetaDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@kotlinx.serialization.Serializable
data class SubscriptionItem(val name: String, val url: String)

@Singleton
class PluginMetaStore @Inject constructor(
    @PluginMetaDataStore private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── 启用/禁用 ──

    val disabledPlugins: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_DISABLED_PLUGINS] ?: emptySet()
    }

    suspend fun setPluginEnabled(platform: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_PLUGINS] ?: emptySet()
            prefs[KEY_DISABLED_PLUGINS] = if (enabled) {
                current - platform
            } else {
                current + platform
            }
        }
    }

    fun isPluginEnabled(platform: String): Flow<Boolean> = dataStore.data.map { prefs ->
        platform !in (prefs[KEY_DISABLED_PLUGINS] ?: emptySet())
    }

    // ── 排序 ──

    val pluginOrder: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_PLUGIN_ORDER]?.let { jsonStr ->
            runCatching { json.decodeFromString<List<String>>(jsonStr) }
                .onFailure { logDecodeFailure("plugin_order", it) }
                .getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun setPluginOrder(order: List<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_PLUGIN_ORDER] = json.encodeToString(order)
        }
    }

    // ── 音源重定向 ──

    val alternativePlugins: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[KEY_ALTERNATIVE_PLUGINS]?.let { jsonStr ->
            runCatching { json.decodeFromString<Map<String, String>>(jsonStr) }
                .onFailure { logDecodeFailure("alternative_plugins", it) }
                .getOrDefault(emptyMap())
                .filterValues { it.isNotBlank() }
        } ?: emptyMap()
    }

    fun getAlternativePlugin(platform: String): Flow<String?> =
        alternativePlugins.map { alternatives -> alternatives[platform] }

    suspend fun setAlternativePlugin(sourcePlatform: String, targetPlatform: String?) {
        dataStore.edit { prefs ->
            val current = currentAlternativePlugins(prefs).toMutableMap()
            val normalizedTarget = targetPlatform?.trim().orEmpty()
            if (normalizedTarget.isBlank() || normalizedTarget == sourcePlatform) {
                current.remove(sourcePlatform)
            } else {
                current[sourcePlatform] = normalizedTarget
            }
            if (current.isEmpty()) {
                prefs.remove(KEY_ALTERNATIVE_PLUGINS)
            } else {
                prefs[KEY_ALTERNATIVE_PLUGINS] = json.encodeToString(current)
            }
        }
    }

    private fun currentAlternativePlugins(prefs: Preferences): Map<String, String> =
        prefs[KEY_ALTERNATIVE_PLUGINS]?.let { jsonStr ->
            runCatching { json.decodeFromString<Map<String, String>>(jsonStr) }
                .onFailure { logDecodeFailure("alternative_plugins", it) }
                .getOrDefault(emptyMap())
                .filterValues { it.isNotBlank() }
        } ?: emptyMap()

    // ── 用户变量 ──

    fun getUserVariables(platform: String): Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[userVariablesKey(platform)]?.let { jsonStr ->
            runCatching { json.decodeFromString<Map<String, String>>(jsonStr) }
                .onFailure { logDecodeFailure("user_variables", it, mapOf("platform" to platform)) }
                .getOrDefault(emptyMap())
        } ?: emptyMap()
    }

    suspend fun setUserVariables(platform: String, variables: Map<String, String>) {
        dataStore.edit { prefs ->
            prefs[userVariablesKey(platform)] = json.encodeToString(variables)
        }
    }

    // ── 订阅源 ──

    val subscriptions: Flow<List<SubscriptionItem>> = dataStore.data.map { prefs ->
        prefs[KEY_SUBSCRIPTIONS]?.let { jsonStr ->
            runCatching { json.decodeFromString<List<SubscriptionItem>>(jsonStr) }
                .onFailure { logDecodeFailure("subscriptions", it) }
                .getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun addSubscription(name: String, url: String) {
        dataStore.edit { prefs ->
            val current = currentSubscriptions(prefs)
            prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(current + SubscriptionItem(name, url))
        }
    }

    suspend fun updateSubscription(index: Int, name: String, url: String) {
        dataStore.edit { prefs ->
            val current = currentSubscriptions(prefs)
            if (index in current.indices) {
                val updated = current.toMutableList()
                updated[index] = SubscriptionItem(name, url)
                prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(updated)
            }
        }
    }

    suspend fun removeSubscription(index: Int) {
        dataStore.edit { prefs ->
            val current = currentSubscriptions(prefs)
            if (index in current.indices) {
                val updated = current.toMutableList()
                updated.removeAt(index)
                prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(updated)
            }
        }
    }

    private fun currentSubscriptions(prefs: Preferences): List<SubscriptionItem> =
        prefs[KEY_SUBSCRIPTIONS]?.let { jsonStr ->
            runCatching { json.decodeFromString<List<SubscriptionItem>>(jsonStr) }
                .onFailure { logDecodeFailure("subscriptions", it) }
                .getOrDefault(emptyList())
        } ?: emptyList()

    private fun userVariablesKey(platform: String) =
        stringPreferencesKey("user_variables_$platform")

    private fun logDecodeFailure(
        key: String,
        throwable: Throwable,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.error(
            category = LogCategory.PLUGIN,
            event = "plugin_metadata_decode_failed",
            throwable = throwable,
            fields = mapOf(
                "key" to key,
                "status" to "failed",
            ) + extraFields,
        )
    }

    private companion object {
        val KEY_DISABLED_PLUGINS = stringSetPreferencesKey("disabled_plugins")
        val KEY_PLUGIN_ORDER = stringPreferencesKey("plugin_order")
        val KEY_ALTERNATIVE_PLUGINS = stringPreferencesKey("alternative_plugins")
        val KEY_SUBSCRIPTIONS = stringPreferencesKey("subscriptions")
    }
}
