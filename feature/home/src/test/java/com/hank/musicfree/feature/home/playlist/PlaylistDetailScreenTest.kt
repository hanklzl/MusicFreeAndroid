package com.hank.musicfree.feature.home.playlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
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
}
