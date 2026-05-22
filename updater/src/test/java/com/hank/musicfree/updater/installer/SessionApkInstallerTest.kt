package com.hank.musicfree.updater.installer

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.provider.Settings
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateError
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 31, 34])
class SessionApkInstallerTest {

    private lateinit var tmpFile: File

    @Before
    fun setUp() {
        tmpFile = File.createTempFile("test", ".apk").apply {
            writeBytes(ByteArray(16) { 0x42 })
        }
    }

    @After
    fun tearDown() {
        MfLog.resetForTest()
        tmpFile.delete()
    }

    // --- Case (a): canRequestPackageInstalls=false → Blocked + settings intent ---

    @Test
    fun `canRequestPackageInstalls false returns Blocked and launches settings`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val shadowApp = Shadows.shadowOf(app)

        val mockPm = mockk<PackageManager>(relaxed = true)
        every { mockPm.canRequestPackageInstalls() } returns false

        val context = spyk(app) {
            every { packageManager } returns mockPm
            every { packageName } returns "com.hank.musicfree.test"
        }

        val installer = ApkInstaller(context)
        val result = installer.install(tmpFile)

        assertTrue(
            "Expected Blocked but got $result",
            result is ApkInstaller.InstallResult.Blocked,
        )
        assertEquals(
            UpdateError.InstallBlocked,
            (result as ApkInstaller.InstallResult.Blocked).cause,
        )

        val started = shadowApp.nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, started?.action)
    }

    // --- Case (b): happy path → createSession + openSession + openWrite + commit → Started ---

    @Test
    fun `happy path calls session api in order and returns Started`() = runTest {
        val app = RuntimeEnvironment.getApplication()

        val fakeOut = ByteArrayOutputStream()

        val mockSession = mockk<PackageInstaller.Session>(relaxed = true) {
            every { openWrite("base.apk", 0, tmpFile.length()) } returns fakeOut
        }

        val mockPkgInstaller = mockk<PackageInstaller>(relaxed = true) {
            every { createSession(any()) } returns 42
            every { openSession(42) } returns mockSession
        }

        val mockPm = mockk<PackageManager>(relaxed = true) {
            every { canRequestPackageInstalls() } returns true
            every { packageInstaller } returns mockPkgInstaller
        }

        val context = spyk(app) {
            every { packageManager } returns mockPm
            every { packageName } returns "com.hank.musicfree.test"
        }

        // Stub buildStatusPendingIntent so PendingIntent.intentSender is accessible
        val fakePendingIntent = mockk<PendingIntent>(relaxed = true)

        val installer = spyk(ApkInstaller(context)) {
            every { buildStatusPendingIntent(any()) } returns fakePendingIntent
        }

        val result = installer.install(tmpFile)

        assertTrue(
            "Expected Started but got $result",
            result is ApkInstaller.InstallResult.Started,
        )

        verifyOrder {
            mockPkgInstaller.createSession(any())
            mockPkgInstaller.openSession(42)
            mockSession.openWrite("base.apk", 0, tmpFile.length())
            mockSession.commit(any())
        }
    }

    // --- Case (c): createSession throws IOException → Blocked(InstallFailed) ---

    @Test
    fun `createSession IOException returns Blocked with InstallFailed`() = runTest {
        val app = RuntimeEnvironment.getApplication()

        val mockPkgInstaller = mockk<PackageInstaller>(relaxed = true) {
            every { createSession(any()) } throws IOException("disk full")
        }

        val mockPm = mockk<PackageManager>(relaxed = true) {
            every { canRequestPackageInstalls() } returns true
            every { packageInstaller } returns mockPkgInstaller
        }

        val context = spyk(app) {
            every { packageManager } returns mockPm
            every { packageName } returns "com.hank.musicfree.test"
        }

        val installer = ApkInstaller(context)
        val result = installer.install(tmpFile)

        assertTrue(
            "Expected Blocked but got $result",
            result is ApkInstaller.InstallResult.Blocked,
        )
        assertEquals(
            UpdateError.InstallFailed,
            (result as ApkInstaller.InstallResult.Blocked).cause,
        )
    }

    // --- Case (d): commit throws IOException → abandonSession called + Blocked(InstallFailed) ---

    @Test
    fun `commit IOException triggers abandonSession and returns Blocked InstallFailed`() = runTest {
        val app = RuntimeEnvironment.getApplication()

        val fakeOut = ByteArrayOutputStream()

        val mockSession = mockk<PackageInstaller.Session>(relaxed = true) {
            every { openWrite("base.apk", 0, tmpFile.length()) } returns fakeOut
            every { commit(any()) } throws IOException("commit failed")
        }

        val mockPkgInstaller = mockk<PackageInstaller>(relaxed = true) {
            every { createSession(any()) } returns 42
            every { openSession(42) } returns mockSession
        }

        val mockPm = mockk<PackageManager>(relaxed = true) {
            every { canRequestPackageInstalls() } returns true
            every { packageInstaller } returns mockPkgInstaller
        }

        val context = spyk(app) {
            every { packageManager } returns mockPm
            every { packageName } returns "com.hank.musicfree.test"
        }

        val fakePendingIntent = mockk<PendingIntent>(relaxed = true)

        val installer = spyk(ApkInstaller(context)) {
            every { buildStatusPendingIntent(any()) } returns fakePendingIntent
        }

        val result = installer.install(tmpFile)

        assertTrue(
            "Expected Blocked but got $result",
            result is ApkInstaller.InstallResult.Blocked,
        )
        assertEquals(
            UpdateError.InstallFailed,
            (result as ApkInstaller.InstallResult.Blocked).cause,
        )
        verify(exactly = 1) { mockPkgInstaller.abandonSession(42) }
    }
}
