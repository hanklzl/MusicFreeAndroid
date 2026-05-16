package com.zili.android.musicfreeandroid.feature.home.detail

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollableHeaderSourceTest {
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
    fun `playlist detail header is inside the lazy list`() {
        val source = source("feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt")

        assertFalse(
            "PlaylistDetailScreen must not keep PlaylistDetailHeader in an outer fixed Column.",
            Regex("""(?m)^\s*Column\(modifier = Modifier\.fillMaxSize\(\)\.padding\(padding\)\)""").containsMatchIn(source),
        )
        assertContainsInOrder(
            source = source,
            first = "LazyColumn(modifier = Modifier.fillMaxSize().padding(padding))",
            second = "item(key = \"header\")",
            third = "PlaylistDetailHeader(",
        )
    }

    @Test
    fun `album detail header is inside the lazy list`() {
        val source = source("feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt")

        assertContainsInOrder(
            source = source,
            first = "LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding))",
            second = "item(key = \"header\")",
            third = "MusicSheetPageHeader(",
        )
    }

    @Test
    fun `plugin sheet and top list keep headers inside lazy lists`() {
        listOf(
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt",
            "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt",
        ).forEach { path ->
            val source = source(path)
            assertContainsInOrder(
                source = source,
                first = "LazyColumn(",
                second = "item(key = \"header\")",
                third = "MusicSheetPageHeader(",
            )
        }
    }

    private fun source(path: String): String {
        val file = repoRoot.resolve(path)
        assertTrue("Missing source file: $path", file.isFile)
        return file.readText()
    }

    private fun assertContainsInOrder(source: String, first: String, second: String, third: String) {
        val firstIndex = source.indexOf(first)
        val secondIndex = source.indexOf(second)
        val thirdIndex = source.indexOf(third)
        assertTrue("Missing marker: $first", firstIndex >= 0)
        assertTrue("Marker `$second` must appear after `$first`.", secondIndex > firstIndex)
        assertTrue("Marker `$third` must appear after `$second`.", thirdIndex > secondIndex)
    }
}
