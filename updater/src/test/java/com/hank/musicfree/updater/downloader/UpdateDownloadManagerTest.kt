package com.hank.musicfree.updater.downloader

import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.updater.api.UpdateClient
import com.hank.musicfree.updater.checker.AbiResolver
import com.hank.musicfree.updater.checker.ResolvedUpdate
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.checker.UpdateState
import com.hank.musicfree.updater.model.ApkVariant
import com.hank.musicfree.updater.model.UpdateInfo
import com.hank.musicfree.updater.store.UpdatePreferences
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateDownloadManagerTest {

    @Test
    fun `silent download starts only on wifi when preference is enabled`() = runTest(StandardTestDispatcher()) {
        val update = resolvedUpdate()
        val downloader = FakeApkDownloader(ApkDownloader.Result.Success(File("ready.apk")))
        val checker = checker(prefs())
        var serviceStarts = 0
        val manager = manager(
            checker = checker,
            downloader = downloader,
            prefs = prefs(),
            networkType = { NetworkType.WIFI },
            serviceStarter = { serviceStarts++ },
        )

        manager.startSilentIfAllowed(update)
        advanceUntilIdle()

        assertEquals(1, downloader.downloadCalls)
        assertEquals(1, serviceStarts)
        assertTrue(checker.state.value is UpdateState.ReadyToInstall)
    }

    @Test
    fun `silent download is skipped on cellular`() = runTest(StandardTestDispatcher()) {
        val update = resolvedUpdate()
        val downloader = FakeApkDownloader(ApkDownloader.Result.Success(File("ready.apk")))
        val checker = checker(prefs())
        var serviceStarts = 0
        val manager = manager(
            checker = checker,
            downloader = downloader,
            prefs = prefs(),
            networkType = { NetworkType.CELLULAR },
            serviceStarter = { serviceStarts++ },
        )

        manager.startSilentIfAllowed(update)
        advanceUntilIdle()

        assertEquals(0, downloader.downloadCalls)
        assertEquals(0, serviceStarts)
        assertFalse(checker.state.value is UpdateState.ReadyToInstall)
    }

    @Test
    fun `manual download ignores silent preference and network type`() = runTest(StandardTestDispatcher()) {
        val update = resolvedUpdate()
        val downloader = FakeApkDownloader(ApkDownloader.Result.Success(File("ready.apk")))
        val checker = checker(prefs(silentEnabled = false))
        var serviceStarts = 0
        val manager = manager(
            checker = checker,
            downloader = downloader,
            prefs = prefs(silentEnabled = false),
            networkType = { NetworkType.CELLULAR },
            serviceStarter = { serviceStarts++ },
        )

        manager.downloadNow(update)
        advanceUntilIdle()

        assertEquals(1, downloader.downloadCalls)
        assertEquals(1, serviceStarts)
        assertTrue(checker.state.value is UpdateState.ReadyToInstall)
    }

    private fun TestScope.manager(
        checker: UpdateChecker,
        downloader: ApkDownloader,
        prefs: UpdatePreferences,
        networkType: () -> NetworkType,
        serviceStarter: () -> Unit,
    ): UpdateDownloadManager = UpdateDownloadManager(
        checker = checker,
        downloader = downloader,
        prefs = prefs,
        networkType = networkType,
        scope = this,
        serviceStarter = serviceStarter,
    )

    private fun checker(prefs: UpdatePreferences): UpdateChecker = UpdateChecker(
        client = object : UpdateClient {
            override suspend fun fetchLatest(): UpdateInfo? = null
        },
        prefs = prefs,
        abiResolver = AbiResolver { listOf("arm64-v8a") },
        localCode = 1L,
        localName = "1.0.0",
    )

    private fun prefs(silentEnabled: Boolean = true): UpdatePreferences = mockk(relaxed = true) {
        every { silentUpdateDownloadEnabled } returns flowOf(silentEnabled)
        coEvery { getSilentDownloadCanceledVersion() } returns null
        coJustRun { setSilentDownloadCanceledVersion(any()) }
        coJustRun { clearSilentDownloadCanceledVersion() }
    }

    private fun resolvedUpdate(): ResolvedUpdate {
        val variant = ApkVariant(
            download = listOf("https://example.com/app.apk"),
            size = 1L,
            sha256 = "sha",
        )
        val info = UpdateInfo(
            schemaVersion = 2,
            version = "1.2.3",
            versionCode = 10203,
            releasedAt = "2026-05-16T18:00:00Z",
            releaseNotesUrl = "https://example.com/notes",
            changeLog = emptyList(),
            variants = mapOf("arm64-v8a" to variant),
        )
        return ResolvedUpdate(info = info, abi = "arm64-v8a", variant = variant)
    }

    private class FakeApkDownloader(
        private val result: ApkDownloader.Result,
    ) : ApkDownloader {
        var downloadCalls = 0

        override suspend fun download(
            update: ResolvedUpdate,
            onProgress: (bytes: Long, total: Long, fraction: Float) -> Unit,
        ): ApkDownloader.Result {
            downloadCalls++
            onProgress(update.variant.size, update.variant.size, 1f)
            return result
        }

        override fun cancel() = Unit
    }
}
