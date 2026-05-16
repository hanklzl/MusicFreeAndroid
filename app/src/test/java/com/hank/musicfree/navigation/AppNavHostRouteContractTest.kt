package com.hank.musicfree.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class AppNavHostRouteContractTest {

    private val projectRoot: Path = locateProjectRoot()

    @Test
    fun `settings entries navigate with typed SettingsRoute instance`() {
        val source = Files.readString(
            projectRoot.resolve("app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt"),
        )

        assertTrue(
            "Settings entries must navigate with the drawer-selected SettingsType.",
            Regex(
                """onNavigateToSettings\s*=\s*\{\s*type(?:\s*:\s*SettingsType)?\s*->\s*navController\.navigate\(\s*SettingsRoute\(\s*type\s*\)\s*\)\s*\}""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).containsMatchIn(source),
        )
        assertFalse(
            "Default settings entry must not navigate with bare SettingsRoute, which resolves to the companion.",
            Regex("""navController\.navigate\(\s*SettingsRoute\s*\)""").containsMatchIn(source),
        )
        assertFalse(
            "Settings drawer entries must not all collapse to the default Basic route.",
            Regex("""onNavigateToSettings\s*=\s*\{\s*navController\.navigate\(\s*SettingsRoute\(\)\s*\)\s*\}""").containsMatchIn(source),
        )
    }

    private fun locateProjectRoot(): Path {
        val userDir = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOfNotNull(userDir, userDir.parent)

        return candidates.firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate project root from $userDir")
    }
}
