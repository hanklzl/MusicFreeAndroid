package com.zili.android.musicfreeandroid.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0001. See docs/dev-harness/test/rules.md#rule-runtest-mandatory.
 *
 * Forbids `runBlocking { ... .first { ... } ... }` self-spinning predicate
 * patterns inside *ViewModelTest.kt files. New violations must either be
 * rewritten with `runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle()`
 * or be added to the explicit allowlist with a justification.
 */
class TestRunTestIdiomContractTest {

    private val allowed: Set<String> = emptySet()

    private val pattern: Regex = Regex("""runBlocking[^{]*\{[^}]*\.first\s*\{""", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun no_runBlocking_first_predicate_in_viewmodel_tests() {
        val repoRoot = repoRoot()
        val violations = repoRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModelTest.kt") }
            .filterNot { it.path.contains("/build/") || it.path.contains("/.worktrees/") }
            .filter { file ->
                val rel = file.relativeTo(repoRoot).path
                rel !in allowed && pattern.containsMatchIn(file.readText())
            }
            .map { it.relativeTo(repoRoot).path }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0001 contract violated. See docs/dev-harness/test/rules.md#rule-runtest-mandatory.\n" +
                    "Use runTest(mainDispatcherRule.dispatcher) + advanceUntilIdle() instead of runBlocking + Flow.first { ... }.\n" +
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
