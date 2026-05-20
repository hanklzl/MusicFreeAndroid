package com.hank.musicfree.feature.home.albumdetail

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumFavoriteIconTintSourceTest {
    private val repoRoot = run {
        var current = File(requireNotNull(System.getProperty("user.dir")))
        while (current.parentFile != null) {
            val hasSettingsGradle = File(current, "settings.gradle").isFile
            val hasSettingsGradleKts = File(current, "settings.gradle.kts").isFile
            if (hasSettingsGradle || hasSettingsGradleKts) {
                break
            }
            current = requireNotNull(current.parentFile)
        }
        current
    }

    @Test
    fun `appbar favorite heart uses shared red tint when starred`() {
        val source = source("feature/home/src/main/java/com/hank/musicfree/feature/home/albumdetail/AlbumDetailScreen.kt")

        assertFalse(
            "Album AppBar favorite heart must not use primary color for the starred state.",
            source.contains("tint = if (isStarred) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.appBarText"),
        )
        assertTrue(
            "Album AppBar favorite heart should resolve tint through the shared favorite icon helper.",
            source.contains("tint = favoriteIconTint("),
        )
    }

    private fun source(path: String): String {
        val file = repoRoot.resolve(path)
        assertTrue("Missing source file: $path", file.isFile)
        return file.readText()
    }
}
