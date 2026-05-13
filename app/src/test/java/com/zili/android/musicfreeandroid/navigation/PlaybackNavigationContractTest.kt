package com.zili.android.musicfreeandroid.navigation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PlaybackNavigationContractTest {

    private val projectRoot: Path = locateProjectRoot()

    @Test
    fun `song list playback callbacks do not navigate to player`() {
        val forbiddenNavigation = listOf(
            ForbiddenPlaybackNavigation(
                relativePath = "feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt",
                normalizedSnippet = "_playEvent.emit(PlayEvent.NavigateToPlayer)",
                description = "emits NavigateToPlayer after search playback success",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt",
                normalizedSnippet = "isSearchViewModel.PlayEvent.NavigateToPlayer->onNavigateToPlayer()",
                description = "handles search playback success by navigating to player",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/searchmusiclist/SearchMusicListScreen.kt",
                normalizedSnippet = "if(viewModel.playFilteredItem(index)){onNavigateToPlayer()}",
                description = "navigates after filtered song-list playback",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt",
                normalizedSnippet = "viewModel.playItem(item,items)onNavigateToPlayer()",
                description = "navigates after local song-list playback",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt",
                normalizedSnippet = "onClick={if(viewModel.playAt(index))onNavigateToPlayer()}",
                description = "navigates after history song-list playback",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt",
                normalizedSnippet = "onPlayAll={viewModel.playAll()onNavigateToPlayer()}",
                description = "navigates after playlist play-all",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt",
                normalizedSnippet = "onClick={viewModel.playAll(startIndex=index)onNavigateToPlayer()}",
                description = "navigates after playlist row playback",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt",
                normalizedSnippet = "if(ok)onNavigateToPlayer()",
                description = "navigates after plugin sheet playback success",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt",
                normalizedSnippet = "if(ok){onNavigateToPlayer()}",
                description = "navigates after top list playback success",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt",
                normalizedSnippet = "if(ok)onNavigateToPlayer()",
                description = "navigates after album playback success",
            ),
            ForbiddenPlaybackNavigation(
                relativePath = "feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/artistdetail/ArtistDetailScreen.kt",
                normalizedSnippet = "if(ok)onNavigateToPlayer()",
                description = "navigates after artist playback success",
            ),
        )

        val violations = forbiddenNavigation.mapNotNull { forbidden ->
            val source = normalizedSource(forbidden.relativePath)
            val snippet = forbidden.normalizedSnippet.filterNot { it.isWhitespace() }
            if (source.contains(snippet)) {
                "${forbidden.relativePath}: ${forbidden.description}"
            } else {
                null
            }
        }

        assertTrue(
            "Song list playback must stay on the current screen; violations: $violations",
            violations.isEmpty(),
        )
    }

    private fun normalizedSource(relativePath: String): String =
        Files.readString(projectRoot.resolve(relativePath)).filterNot { it.isWhitespace() }

    private fun locateProjectRoot(): Path {
        val userDir = Paths.get("").toAbsolutePath().normalize()
        val candidates = generateSequence(userDir) { it.parent }.take(6).toList()
        return candidates.firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate project root from $userDir")
    }

    private data class ForbiddenPlaybackNavigation(
        val relativePath: String,
        val normalizedSnippet: String,
        val description: String,
    )
}
