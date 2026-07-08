package com.hank.musicfree.feature.home.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
                        currentPlaybackItemState = CurrentPlaybackItemState(
                            item = currentPlayingItem,
                            isPlaying = true,
                        ),
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

    @Test
    fun `playlist detail marks current playing row when media identity matches`() {
        val songs = listOf(
            musicItem(id = "song-1", platform = "other", title = "Wrong Platform Song"),
            musicItem(id = "song-1", platform = "test", title = "Target Song"),
        )
        val currentState = CurrentPlaybackItemState(
            item = songs[1].copy(title = "Player cached title"),
            isPlaying = true,
        )

        composeRule.setContent {
            MusicFreeTheme {
                Box(modifier = Modifier.height(640.dp)) {
                    PlaylistDetailContent(
                        state = PlaylistDetailUiState(
                            playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                            musics = songs,
                            isLoading = false,
                        ),
                        sheetState = AddToPlaylistSheetState(),
                        allPlaylists = emptyList(),
                        favoriteResolver = { flowOf(false) },
                        currentPlaybackItemState = currentState,
                        onBack = {},
                        onNavigateToSearchMusicList = {},
                        onNavigateToMusicListEditorLite = {},
                        actions = PlaylistDetailActions.Noop,
                    )
                }
            }
        }

        composeRule.onAllNodes(
            hasText("Target Song") and hasAnySibling(hasTestTag("MusicItemRow_current_playing_wave")),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onAllNodes(
            hasText("Wrong Platform Song") and hasAnySibling(hasTestTag("MusicItemRow_current_playing_wave")),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        composeRule.onAllNodesWithTag("MusicItemRow_current_playing_wave", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag("MusicItemRow_current_paused_wave", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `playlist detail keeps current row marked while paused`() {
        val songs = listOf(
            musicItem(id = "song-1", platform = "other", title = "Wrong Platform Song"),
            musicItem(id = "song-1", platform = "test", title = "Target Song"),
        )
        val currentState = CurrentPlaybackItemState(
            item = songs[1].copy(title = "Player cached title"),
            isPlaying = false,
        )

        composeRule.setContent {
            MusicFreeTheme {
                Box(modifier = Modifier.height(640.dp)) {
                    PlaylistDetailContent(
                        state = PlaylistDetailUiState(
                            playlist = Playlist(id = "playlist-1", name = "Road Trip", coverUri = null),
                            musics = songs,
                            isLoading = false,
                        ),
                        sheetState = AddToPlaylistSheetState(),
                        allPlaylists = emptyList(),
                        favoriteResolver = { flowOf(false) },
                        currentPlaybackItemState = currentState,
                        onBack = {},
                        onNavigateToSearchMusicList = {},
                        onNavigateToMusicListEditorLite = {},
                        actions = PlaylistDetailActions.Noop,
                    )
                }
            }
        }

        composeRule.onAllNodes(
            hasText("Target Song") and hasAnySibling(hasTestTag("MusicItemRow_current_paused_wave")),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onAllNodes(
            hasText("Wrong Platform Song") and hasAnySibling(hasTestTag("MusicItemRow_current_paused_wave")),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        composeRule.onAllNodesWithTag("MusicItemRow_current_paused_wave", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag("MusicItemRow_current_playing_wave", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `playlist detail absent current song keeps row unmarked and fab hidden after user scroll`() {
        val songs = (0 until 40).map { index ->
            musicItem(id = "song-$index", title = "Song $index")
        }
        val currentState = CurrentPlaybackItemState(
            item = musicItem(id = "outside-song", title = "Outside Song"),
            isPlaying = true,
        )

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
                        currentPlaybackItemState = currentState,
                        onBack = {},
                        onNavigateToSearchMusicList = {},
                        onNavigateToMusicListEditorLite = {},
                        actions = PlaylistDetailActions.Noop,
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("MusicItemRow_current_playing_wave", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("MusicItemRow_current_paused_wave", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("定位到当前播放歌曲")).assertCountEquals(0)

        composeRule.onNodeWithTag("PlaylistDetail_list").performTouchInput {
            swipeUp()
        }

        composeRule.onAllNodes(hasContentDescription("定位到当前播放歌曲") and hasClickAction())
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("MusicItemRow_current_playing_wave", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("MusicItemRow_current_paused_wave", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    private fun musicItem(
        id: String,
        title: String,
        platform: String = "test",
    ): MusicItem = MusicItem(
        id = id,
        platform = platform,
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 180_000,
        url = null,
        artwork = null,
        qualities = null,
    )
}
