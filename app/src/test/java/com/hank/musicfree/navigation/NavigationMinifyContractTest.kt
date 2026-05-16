package com.hank.musicfree.navigation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class NavigationMinifyContractTest {

    private val projectRoot: Path = locateProjectRoot()

    @Test
    fun `SettingsType keeps its class name for minified typed navigation`() {
        val source = Files.readString(
            projectRoot.resolve("core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt"),
        )

        assertTrue(
            "SettingsType is reflected by Navigation's enum NavType in minified builds and must keep its class name.",
            Regex(
                """@(?:androidx\.annotation\.)?Keep\s*@Serializable\s*enum\s+class\s+SettingsType|@Serializable\s*@(?:androidx\.annotation\.)?Keep\s*enum\s+class\s+SettingsType""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).containsMatchIn(source),
        )
    }

    private fun locateProjectRoot(): Path {
        val userDir = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOfNotNull(userDir, userDir.parent)

        return candidates.firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate project root from $userDir")
    }
}
