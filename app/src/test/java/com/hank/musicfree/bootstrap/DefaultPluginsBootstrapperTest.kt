package com.hank.musicfree.bootstrap

import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.PluginEntry
import com.hank.musicfree.plugin.manager.PluginInstallSource
import com.hank.musicfree.plugin.manager.PluginInstallSourceType
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.manager.PluginOperationResult
import com.hank.musicfree.plugin.manager.PluginOperationType
import com.hank.musicfree.plugin.manager.SubscriptionInstallResult
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.meta.SubscriptionItem
import com.hank.musicfree.plugin.runtime.PluginErrorReason
import com.hank.musicfree.plugin.runtime.PluginState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class DefaultPluginsBootstrapperTest {

    private fun bootstrapper(
        pluginManager: PluginManager,
        pluginMetaStore: PluginMetaStore,
    ): DefaultPluginsBootstrapper = DefaultPluginsBootstrapper(
        pluginManager = pluginManager,
        pluginMetaStore = pluginMetaStore,
        applicationScope = mock(), // unused in reconcile() direct calls
    )

    @Test
    fun reconcile_emptyLists_isNoOp() = runTest {
        val pluginManager = mock<PluginManager>()
        val pluginMetaStore = mock<PluginMetaStore>()

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = emptyList(),
        )

        verify(pluginManager, never()).installFromSubscriptionUrl(any())
        verify(pluginManager, never()).installFromNetworkUrl(any())
        verify(pluginMetaStore, never()).subscriptions
    }

    @Test
    fun reconcile_installsMissingSubscription() = runTest {
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(emptyList())
            on { installFromSubscriptionUrl(any()) } doReturn SubscriptionInstallResult(
                totalEntries = 1,
                successfulInstalls = 1,
                failedInstalls = 0,
            )
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = listOf("https://example.com/sub.json"),
            pluginUrls = emptyList(),
        )

        verify(pluginManager).installFromSubscriptionUrl("https://example.com/sub.json")
    }

    @Test
    fun reconcile_skipsAlreadyInstalledSubscription() = runTest {
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(emptyList())
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(
                listOf(SubscriptionItem(name = "sub", url = "https://example.com/sub.json"))
            )
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = listOf("https://example.com/sub.json"),
            pluginUrls = emptyList(),
        )

        verify(pluginManager, never()).installFromSubscriptionUrl(any())
    }

    @Test
    fun reconcile_installsMissingPlugin() = runTest {
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(emptyList())
            on { installFromNetworkUrl(any()) } doReturn PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = listOf("wy"),
                successCount = 1,
                failureCount = 0,
                failures = emptyList(),
                startedAtEpochMs = 0L,
                finishedAtEpochMs = 1L,
            )
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://example.com/wy.js"),
        )

        verify(pluginManager).ensurePluginsLoaded()
        verify(pluginManager).installFromNetworkUrl("https://example.com/wy.js")
    }

    @Test
    fun reconcile_skipsAlreadyInstalledPlugin() = runTest {
        val installed = mountedEntry(
            filePath = "/plugins/wy.js",
            platform = "wy",
            sourceUrl = "https://example.com/wy.js",
            sourceType = PluginInstallSourceType.PLUGIN_URL,
        )
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(listOf(installed))
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://example.com/wy.js"),
        )

        verify(pluginManager, never()).installFromNetworkUrl(any())
    }

    @Test
    fun reconcile_skipsPluginEntryStillLoading() = runTest {
        // Regression for the bug behind the user feedback at 2026-05-16 11:21
        // (root cause: DefaultPluginsBootstrapper read `plugins.value` before
        // Phase E lazy load grabbed the mutex, so the entry's URL was missing
        // from `existingPluginUrls` and `installFromNetworkUrl` was called
        // again on every cold start). With this fix, a Loading entry whose
        // installSource matches the URL must already count as "installed".
        val loading = loadingEntry(
            filePath = "/plugins/wy.js",
            platform = "wy",
            sourceUrl = "https://example.com/wy.js",
            sourceType = PluginInstallSourceType.PLUGIN_URL,
        )
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(listOf(loading))
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://example.com/wy.js"),
        )

        verify(pluginManager, never()).installFromNetworkUrl(any())
    }

    @Test
    fun reconcile_retriesPluginEntryInFailedState() = runTest {
        // Mounted + Loading entries count as "installed"; Failed must not,
        // otherwise a previously-broken default plugin can never be
        // recovered automatically.
        val failed = failedEntry(
            filePath = "/plugins/wy.js",
            sourceUrl = "https://example.com/wy.js",
            sourceType = PluginInstallSourceType.PLUGIN_URL,
        )
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(listOf(failed))
            on { installFromNetworkUrl(any()) } doReturn PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = listOf("wy"),
                successCount = 1,
                failureCount = 0,
                failures = emptyList(),
                startedAtEpochMs = 0L,
                finishedAtEpochMs = 1L,
            )
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://example.com/wy.js"),
        )

        verify(pluginManager).installFromNetworkUrl("https://example.com/wy.js")
    }

    @Test
    fun reconcile_continuesAfterSubscriptionFailure() = runTest {
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(emptyList())
            on { installFromSubscriptionUrl("https://bad/sub.json") }
                .thenThrow(RuntimeException("boom"))
            on { installFromSubscriptionUrl("https://good/sub.json") } doReturn SubscriptionInstallResult(
                totalEntries = 1,
                successfulInstalls = 1,
                failedInstalls = 0,
            )
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = listOf(
                "https://bad/sub.json",
                "https://good/sub.json",
            ),
            pluginUrls = emptyList(),
        )

        verify(pluginManager).installFromSubscriptionUrl("https://bad/sub.json")
        verify(pluginManager).installFromSubscriptionUrl("https://good/sub.json")
    }

    @Test
    fun reconcile_continuesAfterPluginFailure() = runTest {
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(emptyList())
            on { installFromNetworkUrl("https://bad/wy.js") }
                .thenThrow(RuntimeException("boom"))
            on { installFromNetworkUrl("https://good/wy.js") } doReturn PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = listOf("wy"),
                successCount = 1,
                failureCount = 0,
                failures = emptyList(),
                startedAtEpochMs = 0L,
                finishedAtEpochMs = 1L,
            )
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = emptyList(),
            pluginUrls = listOf("https://bad/wy.js", "https://good/wy.js"),
        )

        verify(pluginManager).installFromNetworkUrl("https://bad/wy.js")
        verify(pluginManager).installFromNetworkUrl("https://good/wy.js")
    }

    @Test
    fun reconcile_skipsSubscriptionAlreadyInstalledAsPluginSource() = runTest {
        // Regression: a plugin installed via a subscription URL must dedupe
        // even when pluginMetaStore.subscriptions is empty (because
        // installFromSubscriptionUrlLocked never calls addSubscription).
        val installed = mountedEntry(
            filePath = "/plugins/sub-wy.js",
            platform = "wy",
            sourceUrl = "https://example.com/sub.json",
            sourceType = PluginInstallSourceType.SUBSCRIPTION_URL,
        )
        val pluginManager = mock<PluginManager> {
            on { allEntries } doReturn MutableStateFlow(listOf(installed))
        }
        val pluginMetaStore = mock<PluginMetaStore> {
            on { subscriptions } doReturn flowOf(emptyList())
        }

        bootstrapper(pluginManager, pluginMetaStore).reconcile(
            subscriptionUrls = listOf("https://example.com/sub.json"),
            pluginUrls = emptyList(),
        )

        verify(pluginManager, never()).installFromSubscriptionUrl(any())
    }

    private fun mountedEntry(
        filePath: String,
        platform: String,
        sourceUrl: String,
        sourceType: PluginInstallSourceType,
    ): PluginEntry = PluginEntry(
        filePath = filePath,
        state = PluginState.Mounted,
        info = stubInfo(platform = platform, sourceUrl = sourceUrl),
        loaded = null,
        installSource = PluginInstallSource(type = sourceType, value = sourceUrl),
        attemptedPlatform = platform,
    )

    private fun loadingEntry(
        filePath: String,
        platform: String,
        sourceUrl: String,
        sourceType: PluginInstallSourceType,
    ): PluginEntry = PluginEntry(
        filePath = filePath,
        state = PluginState.Loading,
        info = stubInfo(platform = platform, sourceUrl = sourceUrl),
        loaded = null,
        installSource = PluginInstallSource(type = sourceType, value = sourceUrl),
        attemptedPlatform = platform,
    )

    private fun failedEntry(
        filePath: String,
        sourceUrl: String,
        sourceType: PluginInstallSourceType,
    ): PluginEntry = PluginEntry(
        filePath = filePath,
        state = PluginState.Failed(PluginErrorReason.DownloadFailed, "test"),
        info = null,
        loaded = null,
        installSource = PluginInstallSource(type = sourceType, value = sourceUrl),
        attemptedPlatform = null,
    )

    private fun stubInfo(platform: String, sourceUrl: String): PluginInfo = PluginInfo(
        platform = platform,
        version = "1.0.0",
        author = null,
        description = null,
        srcUrl = sourceUrl,
        supportedSearchType = emptyList(),
    )
}
