package com.hank.musicfree.updater.installer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.ResolvedUpdate
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.checker.UpdateError
import com.hank.musicfree.updater.checker.UpdateState
import com.hank.musicfree.updater.model.ApkVariant
import com.hank.musicfree.updater.model.UpdateInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InstallStatusHandlerTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private val testVariant = ApkVariant(
        download = listOf("https://example.com/arm64.apk"),
        size = 1024L,
        sha256 = "deadbeef",
    )

    private val testInfo = UpdateInfo(
        schemaVersion = 2,
        version = "1.2.3",
        versionCode = 10203L,
        releasedAt = "2026-05-16T18:00:00Z",
        releaseNotesUrl = "https://example.com/notes",
        changeLog = emptyList(),
        variants = mapOf("arm64-v8a" to testVariant),
    )

    private val testUpdate = ResolvedUpdate(
        info = testInfo,
        abi = "arm64-v8a",
        variant = testVariant,
    )

    private val tmpFile = File("/tmp/test.apk")

    private fun mockChecker(state: UpdateState): UpdateChecker =
        mockk<UpdateChecker>(relaxed = true) {
            every { this@mockk.state } returns MutableStateFlow(state)
        }

    /**
     * Build a mockk Intent whose getIntExtra / getStringExtra / getParcelableExtra
     * return controlled values without touching unmocked Android framework methods.
     */
    private fun pendingUserActionIntent(confirmIntent: Intent): Intent = mockk(relaxed = true) {
        every { getIntExtra(PackageInstaller.EXTRA_STATUS, any()) } returns
            PackageInstaller.STATUS_PENDING_USER_ACTION
        @Suppress("DEPRECATION")
        every { getParcelableExtra<Intent>(Intent.EXTRA_INTENT) } returns confirmIntent
        every { getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) } returns null
    }

    private fun successIntent(): Intent = mockk(relaxed = true) {
        every { getIntExtra(PackageInstaller.EXTRA_STATUS, any()) } returns
            PackageInstaller.STATUS_SUCCESS
        every { getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) } returns "ok"
    }

    private fun failureIntent(
        packageName: String = "com.hank.musicfree",
        message: String = "err",
    ): Intent = mockk(relaxed = true) {
        every { getIntExtra(PackageInstaller.EXTRA_STATUS, any()) } returns
            PackageInstaller.STATUS_FAILURE
        every { getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) } returns message
        every { getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) } returns packageName
    }

    private fun abortedIntent(
        packageName: String = "com.hank.musicfree",
        message: String = "aborted",
    ): Intent = mockk(relaxed = true) {
        every { getIntExtra(PackageInstaller.EXTRA_STATUS, any()) } returns
            PackageInstaller.STATUS_FAILURE_ABORTED
        every { getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) } returns message
        every { getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) } returns packageName
    }

    // ─── Case: STATUS_PENDING_USER_ACTION ────────────────────────────────────────

    @Test
    fun `STATUS_PENDING_USER_ACTION starts confirm intent with FLAG_ACTIVITY_NEW_TASK`() {
        val confirmIntent = mockk<Intent>(relaxed = true) {
            // confirmIntent starts with flags = 0; addFlags should set FLAG_ACTIVITY_NEW_TASK
            var capturedFlags = 0
            every { addFlags(any()) } answers {
                capturedFlags = capturedFlags or firstArg()
                mockk(relaxed = true)
            }
            every { flags } answers { capturedFlags }
        }
        val incomingIntent = pendingUserActionIntent(confirmIntent)

        val checker = mockChecker(UpdateState.Idle)
        val handler = InstallStatusHandler(checker)

        val startedSlot = slot<Intent>()
        val context = mockk<Context>(relaxed = true) {
            every { startActivity(capture(startedSlot)) } returns Unit
        }

        handler.handle(context, incomingIntent)

        verify(exactly = 1) { context.startActivity(any()) }
        val launched = startedSlot.captured
        // The handler calls confirmIntent.addFlags(FLAG_ACTIVITY_NEW_TASK), then passes it to startActivity
        assertTrue(
            "Expected the started intent to be confirmIntent",
            launched === confirmIntent,
        )
        verify { confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        verify(exactly = 0) { checker.transitionFailed(any(), any()) }
    }

    // ─── Case: STATUS_SUCCESS ─────────────────────────────────────────────────────

    @Test
    fun `STATUS_SUCCESS does not transition state and does not start activity`() {
        val checker = mockChecker(UpdateState.ReadyToInstall(testUpdate, tmpFile))
        val handler = InstallStatusHandler(checker)
        val context = mockk<Context>(relaxed = true)

        handler.handle(context, successIntent())

        verify(exactly = 0) { checker.transitionFailed(any(), any()) }
        verify(exactly = 0) { context.startActivity(any()) }
    }

    // ─── Case: STATUS_FAILURE in ReadyToInstall ───────────────────────────────────

    @Test
    fun `STATUS_FAILURE in ReadyToInstall calls transitionFailed with InstallFailed`() {
        val checker = mockChecker(UpdateState.ReadyToInstall(testUpdate, tmpFile))
        val handler = InstallStatusHandler(checker)
        val context = mockk<Context>(relaxed = true)

        handler.handle(context, failureIntent())

        verify(exactly = 1) { checker.transitionFailed(testUpdate, UpdateError.InstallFailed) }
    }

    // ─── Case: STATUS_FAILURE in non-ReadyToInstall ───────────────────────────────

    @Test
    fun `STATUS_FAILURE outside ReadyToInstall does not call transitionFailed`() {
        val checker = mockChecker(UpdateState.Idle)
        val handler = InstallStatusHandler(checker)
        val context = mockk<Context>(relaxed = true)

        handler.handle(context, failureIntent())

        verify(exactly = 0) { checker.transitionFailed(any(), any()) }
    }

    // ─── Case: STATUS_FAILURE_ABORTED ────────────────────────────────────────────

    @Test
    fun `STATUS_FAILURE_ABORTED transitions to Available not Failed`() {
        val checker = mockChecker(UpdateState.ReadyToInstall(testUpdate, tmpFile))
        val handler = InstallStatusHandler(checker)
        val context = mockk<Context>(relaxed = true)

        handler.handle(context, abortedIntent())

        verify(exactly = 1) { checker.transitionAvailable(testUpdate, skipped = false) }
        verify(exactly = 0) { checker.transitionFailed(any(), any()) }
    }
}
