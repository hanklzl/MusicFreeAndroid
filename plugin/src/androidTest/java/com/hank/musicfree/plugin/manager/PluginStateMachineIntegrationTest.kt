package com.hank.musicfree.plugin.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.plugin.local.LocalFilePluginConstants
import com.hank.musicfree.plugin.runtime.PluginErrorReason
import com.hank.musicfree.plugin.runtime.PluginState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * QuickJS-driven [PluginManager] state-machine tests that exercise paths only
 * reachable when a real JS engine is available (Robolectric unit tests can't
 * load `quickjs.so`, so the equivalent unit tests in
 * `PluginStateMachineTest` skip these branches).
 *
 * Covered transitions:
 *  - `installFromFile` with no `platform` field on `module.exports`:
 *    `Initializing -> Failed(MissingPlatform)`.
 *  - `installFromFile` with malformed JS that can't be evaluated:
 *    `Initializing -> Failed(CannotParse)`.
 *  - `retryEntry` happy path: `Failed -> Loading -> Mounted` after the
 *    on-disk file is fixed.
 *
 * Standard androidTest classification — this file does NOT call any real
 * network (per docs/dev-harness/plugin/rules.md rule-network-test-gated),
 * so it is NOT a `*NetworkIntegrationTest.kt`.
 */
@RunWith(AndroidJUnit4::class)
class PluginStateMachineIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var pluginManager: PluginManager

    @After
    fun tearDown() {
        if (::pluginManager.isInitialized) {
            runBlocking { pluginManager.uninstallAllPlugins() }
        }
    }

    @Test
    fun installFromFile_withMissingPlatform_yields_Failed_MissingPlatform() = runBlocking {
        pluginManager = makeTestManager(prefix = "state-machine-missing-platform")
        // `module.exports` has every other field but `platform`, so QuickJS
        // metadata extraction must throw MissingPlatformException internally
        // and the manager must surface a Failed(MissingPlatform) entry.
        val js = """
            module.exports = {
              version: '1.0.0',
              supportedSearchType: ['music'],
              async search() { return { isEnd: true, data: [] }; }
            };
        """.trimIndent()
        val tmp = tempFolder.newFile("noplat.js").apply { writeText(js) }

        val plugin = pluginManager.installFromFile(tmp)
        assertNull("Plugin without `platform` must not mount", plugin)

        val failed = userVisibleEntries().firstOrNull { it.state is PluginState.Failed }
        assertNotNull(
            "MissingPlatform install must record a Failed entry",
            failed,
        )
        assertEquals(
            PluginErrorReason.MissingPlatform,
            (failed!!.state as PluginState.Failed).reason,
        )
        assertTrue(
            "Failed entry must not retain a LoadedPlugin",
            failed.loaded == null,
        )
        // The Failed entry's filePath is the *target* location (pluginsDir/<name>),
        // not the original tmp path passed in, because the install pipeline
        // would have staged into pluginsDir before bailing on metadata extraction.
        assertTrue(
            "Failed entry filePath should reference plugins dir / source name",
            failed.filePath?.endsWith(tmp.name) == true,
        )
    }

    @Test
    fun installFromFile_with_mismatching_appVersion_yields_Failed_VersionNotMatch() = runBlocking {
        // Host app version is "1.0.0" (see makeTestManager default).
        pluginManager = makeTestManager(
            prefix = "state-machine-version-not-match",
            appVersion = "1.0.0",
        )
        val js = """
            module.exports = {
              platform: 'needs-future',
              version: '1.0.0',
              appVersion: '>=99.0.0',
              supportedSearchType: ['music'],
              async search() { return { isEnd: true, data: [] }; }
            };
        """.trimIndent()
        val tmp = tempFolder.newFile("future-only.js").apply { writeText(js) }

        val plugin = pluginManager.installFromFile(tmp)
        assertNull("Plugin with unsatisfied appVersion must not mount", plugin)

        val failed = userVisibleEntries().firstOrNull { it.state is PluginState.Failed }
        assertNotNull(
            "VersionNotMatch install must record a Failed entry",
            failed,
        )
        assertEquals(
            PluginErrorReason.VersionNotMatch,
            (failed!!.state as PluginState.Failed).reason,
        )
        assertTrue(
            "Failed entry must not retain a LoadedPlugin",
            failed.loaded == null,
        )
        // The staged file must NOT be left in pluginsDir; the atomic-replace
        // step is skipped when the gate trips.
        val targetPath = failed.filePath
        assertNotNull(
            "Failed entry must carry the would-be target filePath",
            targetPath,
        )
        assertTrue(
            "Failed entry should reference plugins dir / source name",
            targetPath!!.endsWith(tmp.name),
        )
        assertTrue(
            "Plugin file must not be retained when the appVersion gate trips",
            !java.io.File(targetPath).exists(),
        )
    }

    @Test
    fun installFromFile_with_malformed_js_yields_Failed_CannotParse() = runBlocking {
        pluginManager = makeTestManager(prefix = "state-machine-cannot-parse")
        // Deliberately invalid JS — QuickJS will throw on evaluation.
        val js = "this is { not + valid JS @@@"
        val tmp = tempFolder.newFile("malformed.js").apply { writeText(js) }

        val plugin = pluginManager.installFromFile(tmp)
        assertNull("Malformed JS must not yield a LoadedPlugin", plugin)

        val failed = userVisibleEntries().firstOrNull { it.state is PluginState.Failed }
        assertNotNull(
            "Malformed JS install must record a Failed entry",
            failed,
        )
        assertEquals(
            PluginErrorReason.CannotParse,
            (failed!!.state as PluginState.Failed).reason,
        )
        assertTrue(
            "Failed entry must not retain a LoadedPlugin",
            failed.loaded == null,
        )
    }

    @Test
    fun retryEntry_succeeds_after_fixing_file_content() = runBlocking {
        pluginManager = makeTestManager(prefix = "state-machine-retry-success")
        // Step 1: produce a Failed entry by installing malformed JS.
        val bad = tempFolder.newFile("plug.js").apply { writeText("syntax error @@") }
        assertNull(
            "Initial install must fail to mount",
            pluginManager.installFromFile(bad),
        )
        val failedEntry = userVisibleEntries().single { it.state is PluginState.Failed }
        val targetPath = failedEntry.filePath
        assertNotNull(
            "Failed entry must have a target filePath for retry to address",
            targetPath,
        )

        // Step 2: write valid JS at the staged target path so retry has
        // something to load. The Failed entry's filePath points to
        // pluginsDir/plug.js — that's the path retryEntry expects.
        val fixedJs = """
            module.exports = {
              platform: 'fixed-after-retry',
              version: '1.0.0',
              supportedSearchType: ['music'],
              async search() { return { isEnd: true, data: [] }; }
            };
        """.trimIndent()
        File(targetPath!!).apply {
            parentFile?.mkdirs()
            writeText(fixedJs)
        }

        // Step 3: retry → should transition Failed → Loading → Mounted.
        val retry = pluginManager.retryEntry(targetPath)
        assertEquals(
            "retryEntry must report exactly one success after fix",
            1,
            retry.successCount,
        )
        assertEquals(0, retry.failureCount)
        assertEquals(PluginOperationType.ADD, retry.operationType)

        val mounted = userVisibleEntries().single { it.filePath == targetPath }
        assertEquals(
            "Retry must transition to Mounted",
            PluginState.Mounted,
            mounted.state,
        )
        assertEquals(
            "Mounted entry must carry the now-valid platform",
            "fixed-after-retry",
            mounted.info?.platform,
        )
        assertNotNull("Mounted entry must carry a LoadedPlugin", mounted.loaded)
        // No leftover Failed entry for the same filePath.
        assertTrue(
            "Retry must drop the prior Failed entry",
            userVisibleEntries()
                .filter { it.filePath == targetPath }
                .none { it.state is PluginState.Failed },
        )
    }

    /** Hides the always-present built-in `本地` entry from assertions. */
    private fun userVisibleEntries(): List<PluginEntry> =
        pluginManager.allEntries.value
            .filter { it.info?.platform != LocalFilePluginConstants.PLATFORM }
}
