package com.hank.musicfree.updater.checker

import app.cash.turbine.test
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.updater.api.UpdateClient
import com.hank.musicfree.updater.model.ApkVariant
import com.hank.musicfree.updater.model.UpdateInfo
import com.hank.musicfree.updater.store.UpdatePreferences
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    private fun newInfo(
        version: String,
        code: Long,
        abis: List<String> = listOf("arm64-v8a", "x86_64"),
    ): UpdateInfo = UpdateInfo(
        schemaVersion = 2,
        version = version,
        versionCode = code,
        releasedAt = "2026-05-16T18:00:00Z",
        releaseNotesUrl = "https://example.com/notes",
        changeLog = emptyList(),
        variants = abis.associateWith {
            ApkVariant(
                download = listOf("https://example.com/$it.apk"),
                size = 1,
                sha256 = it,
            )
        },
    )

    private fun mockPrefs(skip: String? = null): UpdatePreferences = mockk(relaxed = true) {
        coEvery { getSkipVersion() } returns skip
    }

    private fun armResolver() = AbiResolver { listOf("arm64-v8a", "armeabi-v7a") }

    @Test
    fun `up to date when remote not newer`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        assertTrue(checker.state.value is UpdateState.UpToDate)
    }

    @Test
    fun `check on launch emits startup flow completion when startup fields are provided`() =
        runTest(StandardTestDispatcher()) {
            val logger = RecordingLogger()
            MfLog.install(logger)
            val client = mockk<UpdateClient> {
                coEvery { fetchLatest() } returns newInfo("1.0.0", 10000)
            }
            val checker = UpdateChecker(
                client = client,
                prefs = mockPrefs(),
                abiResolver = armResolver(),
                localCode = 10000L,
                localName = "1.0.0",
                scope = this,
            )

            checker.checkOnLaunch(
                startupFields = mapOf(
                    "operation" to "startup",
                    "flowName" to "update_check_on_launch",
                    "processStartId" to "process-1",
                ),
            )
            advanceUntilIdle()

            val complete = logger.events.single { it.event == "startup_flow_complete" }
            assertEquals(LogCategory.UPDATE, complete.category)
            assertEquals("update_check_on_launch", complete.fields["flowName"])
            assertEquals("process-1", complete.fields["processStartId"])
            assertEquals(LogFields.Result.SUCCESS, complete.fields["result"])
            assertTrue(complete.fields.containsKey("durationMs"))
        }

    @Test
    fun `available when remote newer and not skipped and abi matches`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(false, (state as UpdateState.Available).skipped)
        assertEquals("arm64-v8a", state.update.abi)
    }

    @Test
    fun `marks skipped when remote version equals skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val checker = UpdateChecker(client, mockPrefs(skip = "1.2.3"), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(true, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `manual check ignores skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val checker = UpdateChecker(client, mockPrefs(skip = "1.2.3"), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkManually()
        advanceUntilIdle()
        assertEquals(false, (checker.state.value as UpdateState.Available).skipped)
    }

    @Test
    fun `failed when client returns null`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns null }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.Network, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `unsupported schema marked failed`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> {
            coEvery { fetchLatest() } returns newInfo("1.2.3", 10203).copy(schemaVersion = 99)
        }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.SchemaUnsupported, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `empty variants marked failed as schema unsupported`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> {
            coEvery { fetchLatest() } returns newInfo("1.2.3", 10203).copy(variants = emptyMap())
        }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        assertEquals(
            UpdateError.SchemaUnsupported,
            (checker.state.value as UpdateState.Failed).cause,
        )
    }

    @Test
    fun `unsupported abi marked failed`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> {
            coEvery { fetchLatest() } returns newInfo("1.2.3", 10203, abis = listOf("x86_64"))
        }
        val checker = UpdateChecker(
            client,
            mockPrefs(),
            abiResolver = AbiResolver { listOf("armeabi-v7a") },
            localCode = 10000L,
            localName = "1.0.0",
            scope = this,
        )
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.UnsupportedAbi, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `state flow emits Checking before result`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val checker = UpdateChecker(client, mockPrefs(), armResolver(), localCode = 10000L, localName = "1.0.0", scope = this)
        checker.state.test {
            assertTrue(awaitItem() is UpdateState.Idle)
            checker.checkOnLaunch()
            assertTrue(awaitItem() is UpdateState.Checking)
            assertTrue(awaitItem() is UpdateState.UpToDate)
            cancelAndIgnoreRemainingEvents()
        }
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
