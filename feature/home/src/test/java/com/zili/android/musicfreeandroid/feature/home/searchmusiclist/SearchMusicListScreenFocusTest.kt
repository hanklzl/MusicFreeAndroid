package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SearchMusicListScreenFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search music list input is focused on entry`() {
        val playerController = mock<PlayerController>()
        val musicRepository = mock<MusicRepository>()
        whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
            .thenReturn(MutableStateFlow(emptyList()))
        val viewModel = SearchMusicListViewModel(
            route = SearchMusicListRoute.localLibrary(),
            sourceLoader = SearchMusicListSourceLoader(
                playlistRepository = mock<PlaylistRepository>(),
                playerController = playerController,
                musicRepository = musicRepository,
            ),
            playerController = playerController,
        )

        composeRule.setContent {
            MusicFreeTheme {
                SearchMusicListScreen(
                    onBack = {},
                    onNavigateToPlayer = {},
                    viewModel = viewModel,
                )
            }
        }

        waitUntilFocused(FidelityAnchors.SearchMusicList.Input)
        composeRule.onNodeWithTag(FidelityAnchors.SearchMusicList.Input, useUnmergedTree = true)
            .assertIsFocused()
    }

    private fun waitUntilFocused(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.getOrElseNullable(SemanticsProperties.Focused) { null } == true
                }
        }
    }
}
