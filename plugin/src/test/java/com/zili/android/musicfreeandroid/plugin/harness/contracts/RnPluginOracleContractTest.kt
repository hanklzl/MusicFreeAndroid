package com.zili.android.musicfreeandroid.plugin.harness.contracts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Contract between the RN plugin runtime and the Android rewrite.
 *
 * The oracle JSON is generated from the sibling RN repo by
 * `scripts/plugin-parity/refresh-rn-plugin-oracle.sh`. This test intentionally
 * checks source-level surfaces instead of one happy-path plugin, so RN protocol
 * drift is visible before runtime smoke tests.
 */
class RnPluginOracleContractTest {

    private val repoRoot: Path = findRepoRoot()
    private val oracle: JsonObject = javaClass.classLoader!!
        .getResourceAsStream("rn-plugin-oracle.json")
        .use { stream ->
            requireNotNull(stream) { "Missing rn-plugin-oracle.json test resource" }
            Json.parseToJsonElement(stream.reader().readText()).jsonObject
        }

    @Test
    fun `Android PluginApi method surface matches RN oracle`() {
        val rnMethods = oracle.stringArray("pluginDefine", "methods").toSet()
        val androidMethods = source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt")
            .let { Regex("""suspend\s+fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""").findAll(it) }
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(
            "PluginApi method set must stay aligned with RN IPluginDefine function fields",
            rnMethods,
            androidMethods,
        )
    }

    @Test
    fun `Android PluginInfo and MediaSourceResult cover RN declared fields`() {
        val rnMetadata = oracle.stringArray("pluginDefine", "metadataFields").toSet()
        val androidInfoFields = kotlinValNames(
            "plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginInfo.kt",
        )
        assertTrue(
            "PluginInfo is missing RN metadata fields: ${rnMetadata - androidInfoFields}",
            androidInfoFields.containsAll(rnMetadata),
        )

        val rnMediaSourceFields = oracle.stringArray("pluginDefine", "mediaSourceResultFields").toSet()
        val androidMediaSourceFields = kotlinValNames(
            "core/src/main/java/com/zili/android/musicfreeandroid/core/model/MediaSourceResult.kt",
        )
        assertTrue(
            "MediaSourceResult is missing RN fields: ${rnMediaSourceFields - androidMediaSourceFields}",
            androidMediaSourceFields.containsAll(rnMediaSourceFields),
        )
    }

    @Test
    fun `runtime shims cover RN require modules and constants`() {
        val rnModules = oracle.stringArray("runtime", "requireModules").toSet()
        val deprecatedNoOps = oracle.stringArray("runtime", "deprecatedNoOpModules").toSet()
        val androidRequireModules = source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/RequireShim.kt")
            .let { Regex(""""([^"]+)"\s+to\s+"""").findAll(it) }
            .map { it.groupValues[1] }
            .toMutableSet()
            .apply { add("axios") }

        assertTrue(
            "RequireShim is missing RN modules: ${rnModules - deprecatedNoOps - androidRequireModules}",
            androidRequireModules.containsAll(rnModules - deprecatedNoOps),
        )

        val expectedTimeout = oracle.intValue("runtime", "defaultAxiosTimeoutMs")
        val androidTimeout = Regex("""DEFAULT_TIMEOUT_MS\s*=\s*(\d+)L""")
            .find(source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt"))
            ?.groupValues
            ?.get(1)
            ?.toInt()
        assertEquals("Axios default timeout must match RN", expectedTimeout, androidTimeout)

        assertTrue(
            "Android runtime must keep RN URL constructor support",
            source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/BootstrapShim.kt")
                .contains("url-polyfill.js"),
        )
        assertTrue(
            "Android runtime must keep RN user:pass@host Basic auth lifting",
            source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt")
                .contains("Authorization") &&
                source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt")
                    .contains("Basic"),
        )

        val expectedLang = oracle.stringValue("runtime", "envLang")
        assertTrue(
            "PluginManager must inject env.lang=$expectedLang like RN",
            source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt")
                .contains("""val lang = "$expectedLang""""),
        )

        val localPlugin = oracle.obj("runtime", "localPlugin")
        val localConstants = source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/local/LocalFilePluginConstants.kt")
        assertTrue(localConstants.contains("""PLATFORM = "${localPlugin.stringValue("platform")}""""))
        assertTrue(localConstants.contains("""HASH = "${localPlugin.stringValue("hash")}""""))
    }

    @Test
    fun `state and cache-control semantics include RN oracle values`() {
        val rnStates = oracle.stringArray("runtime", "states")
            .map { if (it == "Error") "Failed" else it }
            .toSet()
        val androidStates = source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/runtime/PluginState.kt")
            .let { Regex("""data\s+(?:object|class)\s+([A-Za-z_][A-Za-z0-9_]*)""").findAll(it) }
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("PluginState is missing RN states: ${rnStates - androidStates}", androidStates.containsAll(rnStates))

        val rnReasons = oracle.stringArray("runtime", "errorReasons").toSet()
        val androidReasons = source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/runtime/PluginErrorReason.kt")
            .let { Regex("""^\s{4}([A-Za-z_][A-Za-z0-9_]*),""", RegexOption.MULTILINE).findAll(it) }
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            "PluginErrorReason is missing RN reasons: ${rnReasons - androidReasons}",
            androidReasons.containsAll(rnReasons),
        )

        val rnCacheControl = oracle.stringArray("pluginDefine", "cacheControlValues").toSet()
        val androidCacheControl = source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/playback/CacheControlPolicy.kt")
            .let { Regex(""""([^"]+)"""").findAll(it) }
            .map { it.groupValues[1] }
            .filter { it in rnCacheControl }
            .toSet()
        assertEquals("CacheControl wire values must match RN", rnCacheControl, androidCacheControl)

        assertTrue(
            "RN oracle says no-cache can use cache while offline; Android policy must keep that branch",
            oracle.booleanValue("runtime", "cacheFallsBackWhenOffline") &&
                source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/playback/CacheControlPolicy.kt")
                    .contains("isOffline"),
        )
        assertTrue(
            "PluginMediaSourceService must use real network state for RN no-cache offline fallback",
            source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceService.kt")
                .contains("networkStateProvider.isOffline()"),
        )
    }

    @Test
    fun `PluginManager preserves RN manager semantics from oracle`() {
        val managerOracle = oracle.obj("manager")
        val pluginManager = source("plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt")

        assertTrue(
            "RN oracle says plugin lazy-load is configurable by ${managerOracle.stringValue("lazyLoadConfigKey")}",
            managerOracle.booleanValue("supportsLazyLoad") &&
                pluginManager.contains("appPreferences.lazyLoadPlugins.first()"),
        )
        assertTrue(
            "RN oracle says duplicate hash installs are silent no-ops",
            managerOracle.booleanValue("silentDuplicateHashInstall") &&
                pluginManager.contains("findMountedByHash") &&
                pluginManager.contains("hash-collision silent idempotent"),
        )
        assertTrue(
            "RN oracle says plugin metadata cache exists",
            managerOracle.booleanValue("hasPluginMetadataCache") &&
                pluginManager.contains("metadataCache"),
        )
        assertTrue(
            "RN oracle says uninstall clears platform-keyed media extras",
            managerOracle.booleanValue("clearsMediaExtraOnUninstall") &&
                pluginManager.contains("mediaCacheRepository.deleteByPlatform(platform)") &&
                pluginManager.contains("lyricRepository.deleteByPlatform(platform)") &&
                pluginManager.contains("downloadedTrackDao.deleteByPlatform(platform)"),
        )
    }

    private fun kotlinValNames(relativePath: String): Set<String> =
        source(relativePath)
            .let { Regex("""\bval\s+([A-Za-z_][A-Za-z0-9_]*)\s*:""").findAll(it) }
            .map { it.groupValues[1] }
            .toSet()

    private fun source(relativePath: String): String =
        Files.readString(repoRoot.resolve(relativePath))

    private fun JsonObject.obj(vararg path: String): JsonObject =
        path.fold(this) { current, segment -> current[segment]!!.jsonObject }

    private fun JsonObject.stringArray(vararg path: String): List<String> =
        path.dropLast(1).fold(this) { current, segment -> current[segment]!!.jsonObject }
            .let { it[path.last()] as JsonArray }
            .jsonArray
            .map { it.jsonPrimitive.content }

    private fun JsonObject.stringValue(vararg path: String): String =
        path.dropLast(1).fold(this) { current, segment -> current[segment]!!.jsonObject }
            .let { it[path.last()]!!.jsonPrimitive.content }

    private fun JsonObject.intValue(vararg path: String): Int =
        path.dropLast(1).fold(this) { current, segment -> current[segment]!!.jsonObject }
            .let { it[path.last()]!!.jsonPrimitive.content.toInt() }

    private fun JsonObject.booleanValue(vararg path: String): Boolean =
        path.dropLast(1).fold(this) { current, segment -> current[segment]!!.jsonObject }
            .let { it[path.last()]!!.jsonPrimitive.boolean }

    private fun findRepoRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (true) {
            if (Files.exists(current.resolve("settings.gradle.kts")) &&
                Files.exists(current.resolve("plugin/src/main/java"))
            ) {
                return current
            }
            current = current.parent ?: error("Cannot locate MusicFreeAndroid repo root")
        }
    }
}
