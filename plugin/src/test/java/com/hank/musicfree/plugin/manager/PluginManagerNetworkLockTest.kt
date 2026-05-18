package com.hank.musicfree.plugin.manager

import android.content.Context
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.plugin.engine.WebDavShim
import com.hank.musicfree.plugin.local.LocalFilePlugin
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.runtime.PluginAppVersionGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Regression test for the cold-start "没有插件" bug surfaced by the user
 * feedback bundle at 2026-05-16 11:21. Root cause: every plugin install /
 * update path used to hold [PluginManager]'s internal mutex while
 * downloading bytes over the network, so a 15s GitHub timeout under GFW
 * blocked every other lifecycle operation — including the Phase E lazy
 * load that promotes cached entries into the public `plugins` StateFlow.
 *
 * The fix routes every HTTP fetch through `downloadOutsideLock` BEFORE
 * acquiring the mutex; this test asserts that contract by holding the
 * download open and proving another lock-taking call still completes.
 */
class PluginManagerNetworkLockTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun installFromNetworkUrl_doesNotHoldMutexDuringDownload() = runBlocking {
        val manager = createManager()
        val downloadStarted = CompletableDeferred<Unit>()
        val releaseDownload = CompletableDeferred<Unit>()
        manager.downloadOverride = { _ ->
            downloadStarted.complete(Unit)
            releaseDownload.await()
            // Returning null forces a Failed entry; we only care about lock
            // behaviour, not the install outcome.
            null
        }

        val installJob = launch {
            manager.installFromNetworkUrl("https://example.invalid/wy.js")
        }
        downloadStarted.await()

        // If installFromNetworkUrl regresses to holding the mutex during
        // the download, uninstall() will block on `mutex.withLock` and the
        // withTimeoutOrNull will fire.
        val raceResult = withTimeoutOrNull(2_000) {
            manager.uninstall("nonexistent-platform")
            true
        }
        assertNotNull(
            "uninstall must complete while installFromNetworkUrl is parked in its download",
            raceResult,
        )

        releaseDownload.complete(Unit)
        installJob.join()
    }

    @Test
    fun updateAllPlugins_doesNotHoldMutexDuringDownload() = runBlocking {
        val manager = createManager()
        val downloadStarted = CompletableDeferred<Unit>()
        val releaseDownload = CompletableDeferred<Unit>()
        manager.downloadOverride = { _ ->
            downloadStarted.complete(Unit)
            releaseDownload.await()
            null
        }

        // No real plugins are installed, but updateAllPlugins still needs
        // to acquire the mutex; with zero targets the prefetch loop is
        // empty so we instead drive the race through installFromNetworkUrl.
        val installJob = launch {
            manager.installFromNetworkUrl("https://example.invalid/qq.js")
        }
        downloadStarted.await()

        val raceResult = withTimeoutOrNull(2_000) {
            manager.updateAllPlugins()
            true
        }
        assertNotNull(
            "updateAllPlugins must complete while another install is parked in its download",
            raceResult,
        )

        releaseDownload.complete(Unit)
        installJob.join()
    }

    private fun createManager(): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        val pluginMetaStore = mock<PluginMetaStore>()
        whenever(pluginMetaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        whenever(pluginMetaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(pluginMetaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        val appPreferences = mock<AppPreferences>()
        whenever(appPreferences.lazyLoadPlugins).thenReturn(flowOf(false))
        whenever(appPreferences.skipPluginVersionCheck).thenReturn(flowOf(false))
        val baseClient = OkHttpClient.Builder().build()
        return PluginManager(
            context,
            pluginMetaStore,
            mock<MediaCacheRepository>(),
            mock<LyricRepository>(),
            mock<DownloadedTrackDao>(),
            mock<LocalFilePlugin>(),
            PluginAppVersionGate(),
            "1.0.0",
            mock<PluginMetadataCacheGateway>(),
            appPreferences,
            baseClient,
            WebDavShim(baseClient),
        )
    }
}
