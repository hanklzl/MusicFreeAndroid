package com.hank.musicfree.feature.home.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `playlist detail menu opens batch editor for current playlist`() {
        var targetPlaylistId: String? = null

        composeRule.setContent {
            MusicFreeTheme {
                PlaylistDetailContent(
                    state = PlaylistDetailUiState(
                        playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                        musics = emptyList(),
                        isLoading = false,
                    ),
                    sheetState = AddToPlaylistSheetState(),
                    allPlaylists = emptyList(),
                    favoriteResolver = { flowOf(false) },
                    onBack = {},
                    onNavigateToSearchMusicList = {},
                    onNavigateToMusicListEditorLite = { targetPlaylistId = it },
                    actions = PlaylistDetailActions.Noop,
                )
            }
        }

        composeRule.onNode(hasContentDescription("更多") and hasClickAction()).performClick()
        composeRule.onNode(hasText("批量编辑") and hasClickAction()).assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals("playlist-1", targetPlaylistId)
        }
    }

    @Test
    fun `playlist detail floating action button scrolls to current playing music`() {
        val songs = (0 until 40).map { index ->
            musicItem(id = "song-$index", title = "Song $index")
        }.toMutableList()
        songs[30] = songs[30].copy(title = "Target Song")
        val currentPlayingItem = songs[30].copy(title = "Player cached title")

        composeRule.setContent {
            MusicFreeTheme {
                Box(modifier = Modifier.height(360.dp)) {
                    PlaylistDetailContent(
                        state = PlaylistDetailUiState(
                            playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                            musics = songs,
                            isLoading = false,
                        ),
                        sheetState = AddToPlaylistSheetState(),
                        allPlaylists = emptyList(),
                        favoriteResolver = { flowOf(false) },
                        currentPlayingItem = currentPlayingItem,
                        onBack = {},
                        onNavigateToSearchMusicList = {},
                        onNavigateToMusicListEditorLite = {},
                        actions = PlaylistDetailActions.Noop,
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("Target Song").assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("定位到当前播放歌曲")).assertCountEquals(0)

        composeRule.onNodeWithTag("PlaylistDetail_list").performTouchInput {
            swipeUp()
        }

        composeRule.onNode(hasContentDescription("定位到当前播放歌曲") and hasClickAction())
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Target Song")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNode(hasText("Target Song")).assertIsDisplayed()
    }

    private fun musicItem(id: String, title: String): MusicItem = MusicItem(
        id = id,
        platform = "test",
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 180_000,
        url = null,
        artwork = null,
        qualities = null,
    )
}
