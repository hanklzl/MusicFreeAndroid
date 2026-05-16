package com.hank.musicfree.plugin.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0010. See docs/dev-harness/plugin/rules.md#rule-network-test-gated.
 *
 * Any androidTest source mentioning a known live host must live in a file
 * named *NetworkIntegrationTest.kt and reference Assume.assumeTrue with the
 * pluginNetworkTests runner argument.
 */
class PluginNetworkTestGateContractTest {

    private val liveHosts: List<String> = listOf("kstore.vip")

    @Test
    fun network_androidtest_files_must_be_gated_by_assume() {
        val root = File(repoRoot(), "plugin/src/androidTest")
        if (!root.exists()) return
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                liveHosts.any { host -> text.contains(host) }
            }
            .filter { file ->
                val text = file.readText()
                val nameOk = file.name.endsWith("NetworkIntegrationTest.kt")
                val gateOk = text.contains("Assume.assumeTrue") && text.contains("pluginNetworkTests")
                !(nameOk && gateOk)
            }
            .map { it.relativeTo(repoRoot()).path }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0010 contract violated. See docs/dev-harness/plugin/rules.md#rule-network-test-gated.\n" +
                    "Move live-host tests into *NetworkIntegrationTest.kt and gate with Assume.assumeTrue + pluginNetworkTests.\n" +
                    "Violations:\n" + violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
