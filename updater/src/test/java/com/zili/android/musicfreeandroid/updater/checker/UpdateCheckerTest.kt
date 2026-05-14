package com.zili.android.musicfreeandroid.updater.checker

import app.cash.turbine.test
import com.zili.android.musicfreeandroid.updater.api.UpdateClient
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo
import com.zili.android.musicfreeandroid.updater.store.UpdatePreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckerTest {

    private fun newInfo(version: String, code: Long) = UpdateInfo(
        schemaVersion = 1,
        version = version,
        versionCode = code,
        releasedAt = "2026-05-13T18:00:00Z",
        download = listOf("https://example.com/a.apk"),
        size = 1,
        sha256 = "x",
        changeLog = emptyList(),
        releaseNotesUrl = "https://example.com/notes",
    )

    private fun mockPrefs(skip: String? = null): UpdatePreferences = mockk(relaxed = true) {
        coEvery { getSkipVersion() } returns skip
    }

    @Test
    fun `up to date when remote not newer`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        assertTrue(checker.state.value is UpdateState.UpToDate)
    }

    @Test
    fun `available when remote newer and not skipped`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(false, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `marks skipped when remote version equals skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val prefs = mockPrefs(skip = "1.2.3")
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(true, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `manual check ignores skip`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.2.3", 10203) }
        val prefs = mockPrefs(skip = "1.2.3")
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkManually()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(false, (state as UpdateState.Available).skipped)
    }

    @Test
    fun `failed when client returns null`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns null }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0", scope = this)
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
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0", scope = this)
        checker.checkOnLaunch()
        advanceUntilIdle()
        val state = checker.state.value
        assertTrue(state is UpdateState.Failed)
        assertEquals(UpdateError.SchemaUnsupported, (state as UpdateState.Failed).cause)
    }

    @Test
    fun `state flow emits Checking before result`() = runTest(StandardTestDispatcher()) {
        val client = mockk<UpdateClient> { coEvery { fetchLatest() } returns newInfo("1.0.0", 10000) }
        val prefs = mockPrefs()
        val checker = UpdateChecker(client, prefs, localCode = 10000L, localName = "1.0.0", scope = this)
        checker.state.test {
            assertTrue(awaitItem() is UpdateState.Idle)
            checker.checkOnLaunch()
            assertTrue(awaitItem() is UpdateState.Checking)
            assertTrue(awaitItem() is UpdateState.UpToDate)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
