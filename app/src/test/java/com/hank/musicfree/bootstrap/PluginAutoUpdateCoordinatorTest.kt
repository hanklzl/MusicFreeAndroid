package com.hank.musicfree.bootstrap

import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.manager.PluginOperationResult
import com.hank.musicfree.plugin.manager.PluginOperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class PluginAutoUpdateCoordinatorTest {

    @Test
    fun `runIfDue skips when auto update is disabled`() = runTest {
        val appPreferences = mock<AppPreferences> {
            on { autoUpdatePlugins } doReturn flowOf(false)
            on { pluginAutoUpdateLastAtEpochMs } doReturn flowOf(0L)
        }
        val pluginManager = mock<PluginManager>()

        coordinator(appPreferences, pluginManager).runIfDue(nowMs = NOW)

        verify(pluginManager, never()).ensurePluginsLoaded()
        verify(pluginManager, never()).updateAllPlugins()
        verify(appPreferences, never()).setPluginAutoUpdateLastAtEpochMs(NOW)
    }

    @Test
    fun `runIfDue skips before twenty four hour interval elapses`() = runTest {
        val appPreferences = mock<AppPreferences> {
            on { autoUpdatePlugins } doReturn flowOf(true)
            on { pluginAutoUpdateLastAtEpochMs } doReturn flowOf(NOW - ONE_HOUR_MS)
        }
        val pluginManager = mock<PluginManager>()

        coordinator(appPreferences, pluginManager).runIfDue(nowMs = NOW)

        verify(pluginManager, never()).ensurePluginsLoaded()
        verify(pluginManager, never()).updateAllPlugins()
        verify(appPreferences, never()).setPluginAutoUpdateLastAtEpochMs(NOW)
    }

    @Test
    fun `runIfDue updates plugins and stores attempt time when due`() = runTest {
        val appPreferences = mock<AppPreferences> {
            on { autoUpdatePlugins } doReturn flowOf(true)
            on { pluginAutoUpdateLastAtEpochMs } doReturn flowOf(NOW - TWENTY_FIVE_HOURS_MS)
        }
        val pluginManager = mock<PluginManager> {
            onBlocking { updateAllPlugins() } doReturn PluginOperationResult(
                operationType = PluginOperationType.UPDATE_ALL,
                targetPlugins = listOf("wy"),
                successCount = 1,
                failureCount = 0,
                failures = emptyList(),
                startedAtEpochMs = NOW,
                finishedAtEpochMs = NOW + 10L,
            )
        }

        coordinator(appPreferences, pluginManager).runIfDue(nowMs = NOW)

        verify(pluginManager).ensurePluginsLoaded()
        verify(pluginManager).updateAllPlugins()
        verify(appPreferences).setPluginAutoUpdateLastAtEpochMs(NOW)
    }

    private fun coordinator(
        appPreferences: AppPreferences,
        pluginManager: PluginManager,
    ) = PluginAutoUpdateCoordinator(
        appPreferences = appPreferences,
        pluginManager = pluginManager,
        applicationScope = mock<CoroutineScope>(),
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ONE_HOUR_MS = 60L * 60L * 1000L
        const val TWENTY_FIVE_HOURS_MS = 25L * ONE_HOUR_MS
    }
}
