package com.hank.musicfree.bootstrap

import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.manager.PluginOperationResult
import com.hank.musicfree.plugin.manager.PluginOperationType
import com.hank.musicfree.startup.StartupTelemetry
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class PluginAutoUpdateCoordinatorTest {

    @After
    fun tearDown() {
        StartupTelemetry.resetForTest()
        MfLog.resetForTest()
    }

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
    fun `runIfDue logs startup flow skipped when disabled`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        StartupTelemetry.resetForTest(idProvider = { "startup-id" })
        StartupTelemetry.attachBaseContextStart()
        StartupTelemetry.applicationOnCreateStart()
        StartupTelemetry.markLoggingReady()
        val appPreferences = mock<AppPreferences> {
            on { autoUpdatePlugins } doReturn flowOf(false)
            on { pluginAutoUpdateLastAtEpochMs } doReturn flowOf(0L)
        }
        val pluginManager = mock<PluginManager>()

        coordinator(appPreferences, pluginManager).runIfDue(nowMs = NOW)

        val skipped = logger.events.single { it.event == "startup_flow_skipped" }
        assertEquals(LogCategory.APP, skipped.category)
        assertEquals("plugin_auto_update", skipped.fields["flowName"])
        assertEquals(LogFields.Result.SKIPPED, skipped.fields["result"])
        assertEquals("disabled", skipped.fields["reason"])
        assertNotNull(skipped.fields["durationMs"])
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

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = CopyOnWriteArrayList<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun flush() = Unit
    }
}
