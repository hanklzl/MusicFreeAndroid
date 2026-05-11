package com.zili.android.musicfreeandroid.bootstrap

import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import com.zili.android.musicfreeandroid.plugin.meta.SubscriptionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import com.zili.android.musicfreeandroid.plugin.manager.PluginInstallSource
import com.zili.android.musicfreeandroid.plugin.manager.PluginInstallSourceType
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationResult
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationType
import com.zili.android.musicfreeandroid.plugin.manager.SubscriptionInstallResult
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
            on { plugins } doReturn MutableStateFlow(emptyList())
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
            on { plugins } doReturn MutableStateFlow(emptyList())
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
            on { plugins } doReturn MutableStateFlow(emptyList())
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
        val installed = stubLoadedPluginWithSource("https://example.com/wy.js")
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(listOf(installed))
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
    fun reconcile_continuesAfterSubscriptionFailure() = runTest {
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(emptyList())
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
            on { plugins } doReturn MutableStateFlow(emptyList())
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
        // Regression test: if a plugin was installed via a subscription URL (so its
        // installSource.type == SUBSCRIPTION_URL) but pluginMetaStore.subscriptions is empty
        // (because installFromSubscriptionUrlLocked never calls addSubscription), the
        // bootstrapper must still dedupe via the installed plugin's installSource.value.
        val installed = stubLoadedPluginWithSubscriptionSource("https://example.com/sub.json")
        val pluginManager = mock<PluginManager> {
            on { plugins } doReturn MutableStateFlow(listOf(installed))
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

    private fun stubLoadedPluginWithSource(sourceUrl: String): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        val source = PluginInstallSource(
            type = PluginInstallSourceType.PLUGIN_URL,
            value = sourceUrl,
        )
        whenever(plugin.installSource).thenReturn(source)
        return plugin
    }

    private fun stubLoadedPluginWithSubscriptionSource(sourceUrl: String): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        val source = PluginInstallSource(
            type = PluginInstallSourceType.SUBSCRIPTION_URL,
            value = sourceUrl,
        )
        whenever(plugin.installSource).thenReturn(source)
        return plugin
    }
}
