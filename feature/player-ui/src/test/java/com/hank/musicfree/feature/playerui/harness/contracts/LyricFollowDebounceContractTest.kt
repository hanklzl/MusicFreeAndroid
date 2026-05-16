package com.hank.musicfree.feature.playerui.harness.contracts

import org.junit.Test
import java.io.File

/**
 * Guards INC-2026-0012. See docs/dev-harness/player/rules.md#rule-lyric-follow-debounce.
 *
 * Asserts that the key lyric-related test files exist under
 * feature/player-ui/src/test. If any file is renamed or removed, this
 * contract test fails so the change is forced into the lyric debounce
 * incident's review workflow.
 */
class LyricFollowDebounceContractTest {

    private val requiredTestFiles: List<String> = listOf(
        "MiniPlayerContentTest.kt",
        // Auto-follow guard: PlayerLyricsInteractionTest covers
        // autoFollow* assertions (lyric follow debounce).
        "PlayerLyricsInteractionTest.kt",
        // Seek overlay guard: PlayerLyricsContentTest covers
        // seekOverlay* assertions (overlay alignment + visibility).
        "PlayerLyricsContentTest.kt",
    )

    @Test
    fun key_lyric_tests_present() {
        val root = File(repoRoot(), "feature/player-ui/src/test")
        if (!root.exists()) {
            throw AssertionError("Expected feature/player-ui/src/test to exist")
        }
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val missing = requiredTestFiles.filterNot { needle ->
            files.any { it.name.contains(needle) }
        }
        if (missing.isNotEmpty()) {
            throw AssertionError(
                "INC-2026-0012 contract violated. See docs/dev-harness/player/rules.md#rule-lyric-follow-debounce.\n" +
                    "Missing or renamed lyric guard tests:\n" +
                    missing.joinToString("\n") { "  - $it" } +
                    "\nIf intentional, update incidents.md and this contract together."
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
