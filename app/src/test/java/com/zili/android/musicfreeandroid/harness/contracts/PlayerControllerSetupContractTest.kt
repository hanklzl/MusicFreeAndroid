package com.zili.android.musicfreeandroid.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0002. See docs/dev-harness/test/rules.md#rule-no-runblocking-mainthread-in-instrumentation.
 *
 * Forbids `mainExecutor.execute { runBlocking { ... } }` and unbounded
 * `latch.await()` inside player androidTest sources. Use bounded
 * withTimeout / latch.await(timeout, unit) instead.
 */
class PlayerControllerSetupContractTest {

    private val mainRunBlocking: Regex = Regex("""mainExecutor\.execute\s*\{[^}]*runBlocking""", RegexOption.DOT_MATCHES_ALL)
    private val unboundedAwait: Regex = Regex("""\blatch\.await\(\s*\)""")

    @Test
    fun no_main_thread_runBlocking_or_unbounded_latch_in_player_androidtest() {
        val root = playerAndroidTestRoot()
        if (!root.exists()) return
        val violations = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                val problems = mutableListOf<String>()
                if (mainRunBlocking.containsMatchIn(text)) {
                    problems += "${file.relativeTo(repoRoot()).path}: mainExecutor.execute { runBlocking { ... } }"
                }
                if (unboundedAwait.containsMatchIn(text)) {
                    problems += "${file.relativeTo(repoRoot()).path}: unbounded latch.await()"
                }
                problems.asSequence()
            }
            .toList()
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0002 contract violated. See docs/dev-harness/test/rules.md#rule-no-runblocking-mainthread-in-instrumentation.\n" +
                    "Use bounded withTimeout / latch.await(seconds, TimeUnit.SECONDS).\n" +
                    "Violations:\n" + violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun playerAndroidTestRoot(): File =
        File(repoRoot(), "player/src/androidTest")

    private fun repoRoot(): File {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
