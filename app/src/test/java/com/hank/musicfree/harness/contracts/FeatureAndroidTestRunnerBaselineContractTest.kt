package com.hank.musicfree.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0005. See docs/dev-harness/test/rules.md#rule-feature-androidtest-baseline.
 *
 * Each feature/<name> module that declares testInstrumentationRunner must also
 * declare androidTestImplementation(libs.androidx.test.runner). Otherwise
 * full ./gradlew connectedAndroidTest crashes when the feature module's
 * test APK tries to instantiate the runner.
 */
class FeatureAndroidTestRunnerBaselineContractTest {

    private val runnerDecl: Regex = Regex("""testInstrumentationRunner\s*=\s*"androidx\.test\.runner\.AndroidJUnitRunner"""")
    private val runnerDep: Regex = Regex("""androidTestImplementation\s*\(\s*libs\.androidx\.test\.runner\s*\)""")

    @Test
    fun feature_modules_declaring_runner_must_also_declare_runner_dep() {
        val featureRoot = File(repoRoot(), "feature")
        val violations = featureRoot.listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { module -> File(module, "build.gradle.kts").takeIf { it.exists() } }
            ?.filter { build ->
                val text = build.readText()
                runnerDecl.containsMatchIn(text) && !runnerDep.containsMatchIn(text)
            }
            ?.map { it.relativeTo(repoRoot()).path }
            ?.toList()
            ?: emptyList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0005 contract violated. See docs/dev-harness/test/rules.md#rule-feature-androidtest-baseline.\n" +
                    "Add `androidTestImplementation(libs.androidx.test.runner)` to:\n" +
                    violations.joinToString("\n") { "  - $it" }
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
