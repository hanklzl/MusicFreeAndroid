package com.hank.musicfree.updater.bootstrap

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.updater.checker.UpdateChecker
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckCoordinatorTest {
    private val logger = RecordingLogger()

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun debugBuildLogsStartupFlowSkipped() = runTest {
        MfLog.install(logger)
        val checker = mockk<UpdateChecker>(relaxed = true)
        val coordinator = UpdateCheckCoordinator(
            checker = checker,
            localAppVersion = localVersion(isDebug = true),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start(startupFields = mapOf("processStartId" to "process-1"))

        val skipped = logger.events.single { it.event == "startup_flow_skipped" }
        assertEquals(LogCategory.UPDATE, skipped.category)
        assertEquals("update_check_on_launch", skipped.fields["flowName"])
        assertEquals(LogFields.Result.SKIPPED, skipped.fields["result"])
        assertEquals("debug_build", skipped.fields["reason"])
        assertEquals("process-1", skipped.fields["processStartId"])
        assertNotNull(skipped.fields["durationMs"])
    }

    @Test
    fun releaseBuildPassesStartupFieldsToChecker() = runTest {
        MfLog.install(logger)
        val checker = mockk<UpdateChecker>(relaxed = true)
        val coordinator = UpdateCheckCoordinator(
            checker = checker,
            localAppVersion = localVersion(isDebug = false),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.start(startupFields = mapOf("processStartId" to "process-1"))
        advanceUntilIdle()

        verify {
            checker.checkOnLaunch(
                startupFields = match {
                    it["processStartId"] == "process-1" &&
                        it["flowName"] == "update_check_on_launch"
                },
            )
        }
        assertEquals(1, logger.events.count { it.event == "startup_flow_start" })
    }

    private fun localVersion(isDebug: Boolean) = object : LocalAppVersion {
        override val versionName: String = if (isDebug) "debug" else "release"
        override val versionCode: Long = 1L
        override val isDebugBuild: Boolean = isDebug
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
