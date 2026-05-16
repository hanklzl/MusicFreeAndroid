package com.hank.musicfree.bootstrap

import androidx.annotation.VisibleForTesting
import com.hank.musicfree.di.ApplicationScope
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.plugin.manager.PluginEntry
import com.hank.musicfree.plugin.manager.PluginInstallSourceType
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.runtime.PluginState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPluginsBootstrapper @Inject constructor(
    private val pluginManager: PluginManager,
    private val pluginMetaStore: PluginMetaStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun start() {
        if (DefaultPlugins.subscriptionUrls.isEmpty() && DefaultPlugins.pluginUrls.isEmpty()) {
            return
        }
        applicationScope.launch(Dispatchers.IO) {
            reconcile(
                subscriptionUrls = DefaultPlugins.subscriptionUrls,
                pluginUrls = DefaultPlugins.pluginUrls,
            )
        }
    }

    @VisibleForTesting
    internal suspend fun reconcile(
        subscriptionUrls: List<String>,
        pluginUrls: List<String>,
    ) {
        if (subscriptionUrls.isEmpty() && pluginUrls.isEmpty()) return

        val reconcileStartedAt = System.currentTimeMillis()
        var installedSubscriptionCount = 0
        var installedPluginCount = 0
        var skippedCount = 0
        var failedCount = 0

        pluginManager.ensurePluginsLoaded()
        // Read [allEntries] (not [plugins]) so Phase E cache-hit entries that
        // are still in [PluginState.Loading] also count as "already
        // installed" — otherwise the bootstrap would re-download every
        // default plugin on every cold start because lazy-load races the
        // reconcile read. Failed entries are intentionally NOT considered
        // present so the bootstrap can retry a previously-broken default
        // plugin.
        val presentEntries = pluginManager.allEntries.value
            .filter { it.state is PluginState.Mounted || it.state is PluginState.Loading }

        val existingSubscriptionUrls =
            pluginMetaStore.subscriptions.first().map { it.url.trim() }.toSet() +
                presentEntries.sourceUrlsOfType(PluginInstallSourceType.SUBSCRIPTION_URL)

        for (raw in subscriptionUrls) {
            val url = raw.trim()
            if (url.isEmpty()) continue
            if (url in existingSubscriptionUrls) {
                MfLog.detail(
                    category = LogCategory.PLUGIN,
                    event = "default_plugin_bootstrap_subscription_skipped",
                    fields = mapOf("url" to url),
                )
                skippedCount++
                continue
            }
            val startedAt = System.currentTimeMillis()
            runCatching { pluginManager.installFromSubscriptionUrl(url) }
                .onSuccess { result ->
                    installedSubscriptionCount++
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "default_plugin_bootstrap_subscription",
                        fields = mapOf(
                            "url" to url,
                            "successCount" to result.successfulInstalls,
                            "failureCount" to result.failedInstalls,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                        ),
                    )
                }
                .onFailure { t ->
                    failedCount++
                    MfLog.error(
                        category = LogCategory.PLUGIN,
                        event = "default_plugin_bootstrap_failed",
                        throwable = t,
                        fields = mapOf(
                            "stage" to "subscription",
                            "url" to url,
                            "errorClass" to t::class.java.name,
                        ),
                    )
                }
        }

        if (pluginUrls.isNotEmpty()) {
            val existingPluginUrls = presentEntries
                .mapNotNull { it.installSource?.value?.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            for (raw in pluginUrls) {
                val url = raw.trim()
                if (url.isEmpty()) continue
                if (url in existingPluginUrls) {
                    MfLog.detail(
                        category = LogCategory.PLUGIN,
                        event = "default_plugin_bootstrap_plugin_skipped",
                        fields = mapOf("url" to url),
                    )
                    skippedCount++
                    continue
                }
                val startedAt = System.currentTimeMillis()
                runCatching { pluginManager.installFromNetworkUrl(url) }
                    .onSuccess { result ->
                        installedPluginCount++
                        MfLog.detail(
                            category = LogCategory.PLUGIN,
                            event = "default_plugin_bootstrap_plugin",
                            fields = mapOf(
                                "url" to url,
                                "successCount" to result.successCount,
                                "failureCount" to result.failureCount,
                                "durationMs" to (System.currentTimeMillis() - startedAt),
                            ),
                        )
                    }
                    .onFailure { t ->
                        failedCount++
                        MfLog.error(
                            category = LogCategory.PLUGIN,
                            event = "default_plugin_bootstrap_failed",
                            throwable = t,
                            fields = mapOf(
                                "stage" to "plugin",
                                "url" to url,
                                "errorClass" to t::class.java.name,
                            ),
                        )
                    }
            }
        }

        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "default_plugin_bootstrap_completed",
            fields = mapOf(
                "installedSubscriptionCount" to installedSubscriptionCount,
                "installedPluginCount" to installedPluginCount,
                "skippedCount" to skippedCount,
                "failedCount" to failedCount,
                "totalDurationMs" to (System.currentTimeMillis() - reconcileStartedAt),
            ),
        )
    }

    private fun List<PluginEntry>.sourceUrlsOfType(type: PluginInstallSourceType): Set<String> =
        asSequence()
            .filter { it.installSource?.type == type }
            .mapNotNull { it.installSource?.value?.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
