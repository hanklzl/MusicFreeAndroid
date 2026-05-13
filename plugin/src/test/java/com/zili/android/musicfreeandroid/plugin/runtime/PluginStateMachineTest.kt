package com.zili.android.musicfreeandroid.plugin.runtime

import android.content.Context
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import com.zili.android.musicfreeandroid.data.repository.MediaCacheRepository
import com.zili.android.musicfreeandroid.data.repository.PluginMetadataCacheGateway
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.local.LocalFilePlugin
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginEntry
import com.zili.android.musicfreeandroid.plugin.manager.PluginInstallSource
import com.zili.android.musicfreeandroid.plugin.manager.PluginInstallSourceType
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationErrorCode
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Phase C state-machine contract tests for [PluginManager].
 *
 * These tests verify the [PluginState] transitions produced by [PluginManager]
 * for branches that are reachable without driving the QuickJS engine
 * (Robolectric unit tests can't load the native quickjs.so). Branches that
 * REQUIRE real JS evaluation (MissingPlatform via real plugin parse,
 * CannotParse via malformed JS, Mounted-on-retry) are covered by the
 * androidTest sibling `PluginManagerStateMachineAndroidTest`.
 *
 * Construction template borrowed from [PluginManagerCacheCleanupTest]: mock
 * everything, seed internal `MutableStateFlow`s via reflection where needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PluginStateMachineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -- Test 3 (from brief) ------------------------------------------------
    //
    // installFromUrl flow on HTTP 500 must NOT mount anything and must record a
    // Failed/DownloadFailed PluginEntry; the temp file must be cleaned up.

    @Test
    fun `installFromUrl returns DownloadFailed on HTTP 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val manager = manager()

        val plugin = manager.installFromUrl(
            url = server.url("/plugin.js").toString(),
            fileName = "from-500.js",
        )

        assertNull("HTTP 500 download must NOT yield a LoadedPlugin", plugin)
        assertTrue(
            "No new plugins may be added to _plugins on download failure",
            manager.plugins.value.isEmpty(),
        )

        val failedEntry = manager.allEntries.value.firstOrNull {
            it.state is PluginState.Failed
        }
        assertNotNull(
            "Download failure must produce a Failed entry in allEntries",
            failedEntry,
        )
        val failedState = failedEntry!!.state as PluginState.Failed
        assertEquals(PluginErrorReason.DownloadFailed, failedState.reason)
        assertEquals(
            File(File(tempFolder.root, "plugins"), "from-500.js").absolutePath,
            failedEntry.filePath,
        )

        // installFromUrl writes only to a staged tmp file inside pluginsDir;
        // if the download fails we never reach staged-file population, so the
        // target file path must not exist on disk.
        val pluginsDir = File(tempFolder.root, "plugins")
        val leftovers = pluginsDir.listFiles { f -> f.name.endsWith(".tmp") || f.name == "from-500.js" }
            ?: emptyArray()
        assertEquals(
            "installFromUrl HTTP failure must not leave staged/target files on disk",
            0,
            leftovers.size,
        )
    }

    // -- Test 3 sibling (installFromNetworkUrl)  ----------------------------
    //
    // installFromNetworkUrl is the structured-result public API. HTTP 500
    // there must also record a Failed/DownloadFailed entry, mirroring the
    // installFromUrl behaviour above.

    @Test
    fun `installFromNetworkUrl records Failed DownloadFailed on HTTP 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
        val manager = manager()

        val result = manager.installFromNetworkUrl(server.url("/p.js").toString())

        assertEquals(1, result.failureCount)
        assertEquals(0, result.successCount)
        assertEquals(
            PluginOperationErrorCode.SOURCE_UNREACHABLE,
            result.failures.single().errorCode,
        )

        val failed = manager.allEntries.value.firstOrNull { it.state is PluginState.Failed }
        assertNotNull("installFromNetworkUrl HTTP failure must emit a Failed entry", failed)
        assertEquals(
            PluginErrorReason.DownloadFailed,
            (failed!!.state as PluginState.Failed).reason,
        )
    }

    // -- Test 4 (from brief) -------------------------------------------------
    //
    // Re-installing a plugin whose byte hash matches an already-mounted plugin
    // must succeed silently (no DUPLICATE error) and must NOT add a second
    // entry. This is the hash-idempotency RN-alignment contract from Phase C5.

    @Test
    fun `installFromFile with hash matching a mounted plugin is silently idempotent`() = runTest {
        val jsBytes = "module.exports = { platform: 'x' };".toByteArray()
        val hash = sha256Hex(jsBytes)
        val existing = mockPlugin(platform = "x", hash = hash, filePath = pluginsDir().resolve("x.js").absolutePath)

        val manager = manager()
        // Seed the existing mounted plugin via reflection; the
        // pre-condition for this test is "x is already installed".
        manager.seedPlugins(listOf(existing))
        // Also seed an entry so allEntries returns it.
        manager.seedEntries(
            listOf(
                PluginEntry(
                    filePath = existing.filePath,
                    state = PluginState.Mounted,
                    info = existing.info,
                    loaded = existing,
                    installSource = null,
                    attemptedPlatform = "x",
                ),
            ),
        )

        val sourceFile = File.createTempFile("plg", ".js").apply { writeBytes(jsBytes) }
        val result = manager.installFromFile(sourceFile)

        // Must return the already-installed instance (idempotent success).
        assertNotNull(
            "Hash-match install should return the existing mounted plugin",
            result,
        )
        assertEquals("x", result!!.info.platform)

        // Must not add a second Mounted entry for the same platform.
        val mountedCount = manager.allEntries.value.count {
            it.state == PluginState.Mounted && it.info?.platform == "x"
        }
        assertEquals(
            "Hash-idempotent install must not introduce a duplicate entry",
            1,
            mountedCount,
        )

        // Must not produce any Failed entry.
        assertTrue(
            "Hash-idempotent install must not emit a Failed entry",
            manager.allEntries.value.none { it.state is PluginState.Failed },
        )
    }

    // -- Test 5 partial (from brief) -----------------------------------------
    //
    // Real retryEntry success path requires QuickJS; covered by androidTest.
    // Here we exercise the structured failure paths of retryEntry that DON'T
    // need a working engine.

    @Test
    fun `retryEntry returns INTERNAL_ERROR when entry is unknown`() = runTest {
        val manager = manager()

        val result = manager.retryEntry("/does/not/exist.js")

        assertEquals(1, result.failureCount)
        assertEquals(0, result.successCount)
        assertEquals(
            PluginOperationErrorCode.INTERNAL_ERROR,
            result.failures.single().errorCode,
        )
    }

    @Test
    fun `retryEntry transitions to Failed CannotParse when file is missing`() = runTest {
        val manager = manager()
        val ghostPath = pluginsDir().resolve("ghost.js").absolutePath
        manager.seedEntries(
            listOf(
                PluginEntry(
                    filePath = ghostPath,
                    state = PluginState.Failed(
                        PluginErrorReason.CannotParse,
                        "previous attempt",
                    ),
                    info = null,
                    loaded = null,
                    installSource = PluginInstallSource(
                        type = PluginInstallSourceType.LOCAL_FILE,
                        value = ghostPath,
                    ),
                    attemptedPlatform = null,
                ),
            ),
        )

        val result = manager.retryEntry(ghostPath)

        assertEquals(1, result.failureCount)
        assertEquals(
            PluginOperationErrorCode.SOURCE_INVALID,
            result.failures.single().errorCode,
        )

        val entry = manager.allEntries.value.single { it.filePath == ghostPath }
        val state = entry.state
        assertTrue(
            "retryEntry on missing file must leave entry in Failed state",
            state is PluginState.Failed,
        )
        assertEquals(
            PluginErrorReason.CannotParse,
            (state as PluginState.Failed).reason,
        )
        assertNull(
            "Failed entry must not retain a stale LoadedPlugin",
            entry.loaded,
        )
    }

    // -- Fixture -------------------------------------------------------------

    private fun manager(): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        whenever(context.packageName).thenReturn("com.test")
        val metaStore = mock<PluginMetaStore>()
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(emptySet()))
        whenever(metaStore.pluginOrder).thenReturn(flowOf(emptyList()))
        whenever(metaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        val appPreferences = mock<AppPreferences>()
        whenever(appPreferences.lazyLoadPlugins).thenReturn(flowOf(false))
        whenever(appPreferences.skipPluginVersionCheck).thenReturn(flowOf(false))
        return PluginManager(
            context,
            metaStore,
            mock<MediaCacheRepository>(),
            mock<LyricRepository>(),
            mock<DownloadedTrackDao>(),
            mock<LocalFilePlugin>(),
            PluginAppVersionGate(),
            "1.0.0",
            mock<PluginMetadataCacheGateway>(),
            appPreferences,
        )
    }

    private fun pluginsDir(): File {
        val dir = File(tempFolder.root, "plugins").apply { mkdirs() }
        return dir
    }

    @Suppress("UNCHECKED_CAST")
    private fun PluginManager.seedPlugins(plugins: List<LoadedPlugin>) {
        val field = PluginManager::class.java.getDeclaredField("_plugins")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<LoadedPlugin>>).value = plugins
    }

    @Suppress("UNCHECKED_CAST")
    private fun PluginManager.seedEntries(entries: List<PluginEntry>) {
        val field = PluginManager::class.java.getDeclaredField("_entries")
        field.isAccessible = true
        (field.get(this) as MutableStateFlow<List<PluginEntry>>).value = entries
    }

    private fun mockPlugin(
        platform: String,
        hash: String?,
        filePath: String,
    ): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = emptyList(),
                hash = hash,
            ),
        )
        whenever(plugin.filePath).thenReturn(filePath)
        return plugin
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte ->
            val v = byte.toInt() and 0xFF
            "%02x".format(v)
        }
    }
}
