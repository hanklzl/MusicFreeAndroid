package com.zili.android.musicfreeandroid.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class AppNavHostRouteContractTest {

    private val projectRoot: Path = locateProjectRoot()

    @Test
    fun `default settings entry navigates with SettingsRoute instance`() {
        val source = Files.readString(
            projectRoot.resolve("app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt"),
        )

        assertTrue(
            "Default settings entry must navigate to SettingsRoute() so the typed route instance is used.",
            Regex(
                """onNavigateToSettings\s*=\s*\{\s*navController\.navigate\(\s*SettingsRoute\(\)\s*\)\s*\}""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).containsMatchIn(source),
        )
        assertFalse(
            "Default settings entry must not navigate with bare SettingsRoute, which resolves to the companion.",
            Regex("""navController\.navigate\(\s*SettingsRoute\s*\)""").containsMatchIn(source),
        )
    }

    private fun locateProjectRoot(): Path {
        val userDir = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOfNotNull(userDir, userDir.parent)

        return candidates.firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate project root from $userDir")
    }
}
