package com.zili.android.musicfreeandroid.plugin.meta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.plugin.di.PluginMetaDataStore
import kotlinx.coroutines.CancellationException
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
        val operation = "set_plugin_enabled"
        val startedAt = System.nanoTime()
        logWriteStart(operation, platform = platform, extraFields = mapOf("enabled" to enabled))
        try {
            var count = 0
            dataStore.edit { prefs ->
                val current = prefs[KEY_DISABLED_PLUGINS] ?: emptySet()
                val updated = if (enabled) {
                    current - platform
                } else {
                    current + platform
                }
                prefs[KEY_DISABLED_PLUGINS] = updated
                count = updated.size
            }
            logWriteSuccess(
                operation = operation,
                platform = platform,
                count = count,
                durationMs = elapsedMs(startedAt),
                extraFields = mapOf("enabled" to enabled),
            )
        } catch (e: CancellationException) {
            logWriteCancelled(operation, platform = platform, durationMs = elapsedMs(startedAt))
            throw e
        } catch (e: Exception) {
            logWriteFailure(operation, e, platform = platform, durationMs = elapsedMs(startedAt))
            throw e
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
        val operation = "set_plugin_order"
        val startedAt = System.nanoTime()
        logWriteStart(operation, count = order.size)
        try {
            dataStore.edit { prefs ->
                prefs[KEY_PLUGIN_ORDER] = json.encodeToString(order)
            }
            logWriteSuccess(operation, count = order.size, durationMs = elapsedMs(startedAt))
        } catch (e: CancellationException) {
            logWriteCancelled(operation, count = order.size, durationMs = elapsedMs(startedAt))
            throw e
        } catch (e: Exception) {
            logWriteFailure(operation, e, durationMs = elapsedMs(startedAt))
            throw e
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
        val operation = "set_alternative_plugin"
        val startedAt = System.nanoTime()
        val normalizedTarget = targetPlatform?.trim().orEmpty()
        logWriteStart(
            operation = operation,
            platform = sourcePlatform,
            extraFields = mapOf("targetPlatform" to normalizedTarget),
        )
        try {
            var count = 0
            dataStore.edit { prefs ->
                val current = currentAlternativePlugins(prefs).toMutableMap()
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
                count = current.size
            }
            logWriteSuccess(
                operation = operation,
                platform = sourcePlatform,
                count = count,
                durationMs = elapsedMs(startedAt),
                extraFields = mapOf("targetPlatform" to normalizedTarget),
            )
        } catch (e: CancellationException) {
            logWriteCancelled(operation, platform = sourcePlatform, durationMs = elapsedMs(startedAt))
            throw e
        } catch (e: Exception) {
            logWriteFailure(operation, e, platform = sourcePlatform, durationMs = elapsedMs(startedAt))
            throw e
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
        val operation = "set_user_variables"
        val startedAt = System.nanoTime()
        logWriteStart(operation, platform = platform, count = variables.size)
        try {
            dataStore.edit { prefs ->
                prefs[userVariablesKey(platform)] = json.encodeToString(variables)
            }
            logWriteSuccess(operation, platform = platform, count = variables.size, durationMs = elapsedMs(startedAt))
        } catch (e: CancellationException) {
            logWriteCancelled(operation, platform = platform, count = variables.size, durationMs = elapsedMs(startedAt))
            throw e
        } catch (e: Exception) {
            logWriteFailure(operation, e, platform = platform, durationMs = elapsedMs(startedAt))
            throw e
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
        val operation = "add_subscription"
        val startedAt = System.nanoTime()
        val host = LogFields.host(url)
        logWriteStart(operation, extraFields = mapOf("host" to host))
        try {
            var count = 0
            dataStore.edit { prefs ->
                val current = currentSubscriptions(prefs)
                val updated = current + SubscriptionItem(name, url)
                prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(updated)
                count = updated.size
            }
            logWriteSuccess(
                operation = operation,
                count = count,
                durationMs = elapsedMs(startedAt),
                extraFields = mapOf("host" to host),
            )
        } catch (e: CancellationException) {
            logWriteCancelled(operation, durationMs = elapsedMs(startedAt), extraFields = mapOf("host" to host))
            throw e
        } catch (e: Exception) {
            logWriteFailure(operation, e, durationMs = elapsedMs(startedAt), extraFields = mapOf("host" to host))
            throw e
        }
    }

    suspend fun updateSubscription(index: Int, name: String, url: String) {
        val operation = "update_subscription"
        val startedAt = System.nanoTime()
        val host = LogFields.host(url)
        logWriteStart(operation, extraFields = mapOf("index" to index, "host" to host))
        try {
            var count = 0
            var updatedExisting = false
            dataStore.edit { prefs ->
                val current = currentSubscriptions(prefs)
                count = current.size
                if (index in current.indices) {
                    val updated = current.toMutableList()
                    updated[index] = SubscriptionItem(name, url)
                    prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(updated)
                    count = updated.size
                    updatedExisting = true
                }
            }
            if (updatedExisting) {
                logWriteSuccess(
                    operation = operation,
                    count = count,
                    durationMs = elapsedMs(startedAt),
                    extraFields = mapOf("index" to index, "host" to host),
                )
            } else {
                logWriteSkipped(
                    operation,
                    reason = LogFields.Reason.NOT_FOUND,
                    count = count,
                    durationMs = elapsedMs(startedAt),
                )
            }
        } catch (e: CancellationException) {
            logWriteCancelled(operation, durationMs = elapsedMs(startedAt), extraFields = mapOf("index" to index, "host" to host))
            throw e
        } catch (e: Exception) {
            logWriteFailure(operation, e, durationMs = elapsedMs(startedAt), extraFields = mapOf("index" to index, "host" to host))
            throw e
        }
    }

    suspend fun removeSubscription(index: Int) {
        val operation = "remove_subscription"
        val startedAt = System.nanoTime()
        logWriteStart(operation, extraFields = mapOf("index" to index))
        try {
            var count = 0
            var removed = false
            dataStore.edit { prefs ->
                val current = currentSubscriptions(prefs)
                count = current.size
                if (index in current.indices) {
                    val updated = current.toMutableList()
                    updated.removeAt(index)
                    prefs[KEY_SUBSCRIPTIONS] = json.encodeToString(updated)
                    count = updated.size
                    removed = true
                }
            }
            if (removed) {
                logWriteSuccess(
                    operation = operation,
                    count = count,
                    durationMs = elapsedMs(startedAt),
                    extraFields = mapOf("index" to index),
                )
            } else {
                logWriteSkipped(
                    operation,
                    reason = LogFields.Reason.NOT_FOUND,
                    count = count,
                    durationMs = elapsedMs(startedAt),
                )
            }
        } catch (e: CancellationException) {
            logWriteCancelled(operation, durationMs = elapsedMs(startedAt), extraFields = mapOf("index" to index))
            throw e
        } catch (e: Exception) {
            logWriteFailure(operation, e, durationMs = elapsedMs(startedAt), extraFields = mapOf("index" to index))
            throw e
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
                "result" to LogFields.Result.FAILURE,
                "reason" to "decode_failed",
            ) + extraFields,
        )
    }

    private fun logWriteStart(
        operation: String,
        platform: String? = null,
        count: Int? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_metadata_write_start",
            fields = writeFields(
                operation = operation,
                status = "start",
                platform = platform,
                count = count,
                extraFields = extraFields,
            ),
        )
    }

    private fun logWriteSuccess(
        operation: String,
        platform: String? = null,
        count: Int? = null,
        durationMs: Long? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_metadata_write_success",
            fields = writeFields(
                operation = operation,
                status = "success",
                result = LogFields.Result.SUCCESS,
                platform = platform,
                count = count,
                durationMs = durationMs,
                extraFields = extraFields,
            ),
        )
    }

    private fun logWriteSkipped(
        operation: String,
        reason: String,
        platform: String? = null,
        count: Int? = null,
        durationMs: Long? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_metadata_write_skipped",
            fields = writeFields(
                operation = operation,
                status = "skipped",
                result = LogFields.Result.SKIPPED,
                reason = reason,
                platform = platform,
                count = count,
                durationMs = durationMs,
                extraFields = extraFields,
            ),
        )
    }

    private fun logWriteCancelled(
        operation: String,
        platform: String? = null,
        count: Int? = null,
        durationMs: Long? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "plugin_metadata_write_cancelled",
            fields = writeFields(
                operation = operation,
                status = "cancelled",
                result = LogFields.Result.CANCELLED,
                reason = LogFields.Reason.CANCELLED,
                platform = platform,
                count = count,
                durationMs = durationMs,
                extraFields = extraFields,
            ),
        )
    }

    private fun logWriteFailure(
        operation: String,
        throwable: Throwable,
        platform: String? = null,
        count: Int? = null,
        durationMs: Long? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        MfLog.error(
            category = LogCategory.PLUGIN,
            event = "plugin_metadata_write_failed",
            throwable = throwable,
            fields = writeFields(
                operation = operation,
                status = "failed",
                result = LogFields.Result.FAILURE,
                reason = "datastore_write_failed",
                platform = platform,
                count = count,
                durationMs = durationMs,
                extraFields = extraFields,
            ),
        )
    }

    private fun writeFields(
        operation: String,
        status: String,
        result: String? = null,
        reason: String? = null,
        platform: String? = null,
        count: Int? = null,
        durationMs: Long? = null,
        extraFields: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        return mapOf(
            "operation" to operation,
            "status" to status,
        ) +
            platform?.let { mapOf("platform" to it) }.orEmpty() +
            count?.let { mapOf("count" to it) }.orEmpty() +
            durationMs?.let { mapOf("durationMs" to it) }.orEmpty() +
            result?.let { mapOf("result" to it) }.orEmpty() +
            reason?.let { mapOf("reason" to it) }.orEmpty() +
            extraFields
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        val KEY_DISABLED_PLUGINS = stringSetPreferencesKey("disabled_plugins")
        val KEY_PLUGIN_ORDER = stringPreferencesKey("plugin_order")
        val KEY_ALTERNATIVE_PLUGINS = stringPreferencesKey("alternative_plugins")
        val KEY_SUBSCRIPTIONS = stringPreferencesKey("subscriptions")
    }
}
