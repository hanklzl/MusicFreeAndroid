package com.zili.android.musicfreeandroid.plugin.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0004. See docs/dev-harness/test/rules.md#rule-datastore-per-instance-isolation.
 *
 * Any plugin/src/androidTest source that calls PreferenceDataStoreFactory.create
 * must produce per-instance preferences files (UUID.randomUUID() in
 * produceFile). Static filenames trigger 'multiple active DataStores'
 * across AndroidJUnit4 test class instances.
 */
class PluginDataStoreIsolationContractTest {

    @Test
    fun every_PreferenceDataStoreFactory_create_uses_uuid_isolated_file() {
        val root = File(repoRoot(), "plugin/src/androidTest")
        if (!root.exists()) return
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                val createIndices = Regex.escape("PreferenceDataStoreFactory.create(").toRegex().findAll(text).map { it.range.first }
                val results = mutableListOf<String>()
                for (idx in createIndices) {
                    val window = text.substring(idx, minOf(idx + 600, text.length))
                    val hasUuid = window.contains("UUID.randomUUID()") || window.contains("testPreferencesFile(")
                    if (!hasUuid) {
                        results += "${file.relativeTo(repoRoot()).path}: PreferenceDataStoreFactory.create at offset $idx lacks UUID isolation"
                    }
                }
                results.asSequence()
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0004 contract violated. See docs/dev-harness/test/rules.md#rule-datastore-per-instance-isolation.\n" +
                    "Use produceFile = { File(appContext.cacheDir, \"\$prefix-\${UUID.randomUUID()}.preferences_pb\") }.\n" +
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
