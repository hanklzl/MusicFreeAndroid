package com.hank.musicfree.bootstrap

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.startup.StartupTelemetry
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PluginPreloadCoordinatorTest {

    @After
    fun tearDown() {
        StartupTelemetry.resetForTest()
        MfLog.resetForTest()
    }

    @Test
    fun `preload eagerly ensures plugins loaded`() = runTest {
        val pluginManager = mock<PluginManager>()

        coordinator(pluginManager).preload()

        verify(pluginManager).ensurePluginsLoaded()
    }

    @Test
    fun `preload isolates plugin load failure without throwing`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val pluginManager = mock<PluginManager> {
            onBlocking { ensurePluginsLoaded() } doSuspendableAnswer {
                throw RuntimeException("boom")
            }
        }

        // Must not propagate: a preload failure cannot crash startup.
        coordinator(pluginManager).preload()

        assertTrue(logger.events.any { it.event == "plugin_preload_failed" })
    }

    private fun coordinator(pluginManager: PluginManager) = PluginPreloadCoordinator(
        pluginManager = pluginManager,
        applicationScope = mock<CoroutineScope>(),
    )

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
