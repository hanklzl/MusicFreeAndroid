package com.hank.musicfree.core.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicItemRowTest {
    @get:Rule val rule = createComposeRule()

    private fun item(
        id: String = "m1",
        platform: String = "网易云",
        title: String = "夜空中最亮的星",
        artist: String = "逃跑计划",
        album: String? = "世界",
    ) = MusicItem(
        id = id, platform = platform, title = title, artist = artist,
        album = album, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun rendersTitleAndPlatformTag() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(platform = "网易云"),
                    isFavorite = false,
                    actions = setOf(MusicItemAction.PlayNext),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("夜空中最亮的星").assertIsDisplayed()
        rule.onNodeWithText("网易云").assertIsDisplayed()
    }

    @Test fun mapsLocalPlatformToBenDi() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(platform = "local"),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("本地").assertIsDisplayed()
    }

    @Test fun descriptionShowsArtistDashAlbumWhenAlbumPresent() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(artist = "逃跑计划", album = "世界"),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("逃跑计划 - 世界").assertIsDisplayed()
    }

    @Test fun descriptionShowsArtistOnlyWhenAlbumBlank() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(artist = "逃跑计划", album = null),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("逃跑计划").assertIsDisplayed()
    }

    @Test fun rowClickFiresOnClick() {
        var clicked = false
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = { clicked = true },
                    onAction = {},
                )
            }
        }
        rule.onNodeWithTag("MusicItemRow_root").performClick()
        assertTrue(clicked)
    }

    @Test fun overflowMenuTransparentlyEmitsAction() {
        var captured: MusicItemAction? = null
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(),
                    isFavorite = false,
                    actions = setOf(MusicItemAction.PlayNext, MusicItemAction.AddToPlaylist),
                    onClick = {},
                    onAction = { captured = it },
                )
            }
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithTag("MusicItemMoreMenu_AddToPlaylist").performClick()
        assertEquals(MusicItemAction.AddToPlaylist, captured)
    }

    @Test fun currentPlayingShowsWaveAndStateDescription() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                    playbackState = MusicItemRowPlaybackState.CurrentPlaying,
                )
            }
        }

        rule.onNodeWithTag("MusicItemRow_current_playing_wave").assertIsDisplayed()
        rule.onAllNodesWithTag("MusicItemRow_current_wave_bar").assertCountEquals(3)
        rule.onNodeWithTag("MusicItemRow_current_paused_wave").assertDoesNotExist()
        rule.onNodeWithTag("MusicItemRow_root").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "当前歌曲，播放中",
            ),
        )
    }

    @Test fun currentPausedShowsSameWaveShapeAsStaticState() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                    playbackState = MusicItemRowPlaybackState.CurrentPaused,
                )
            }
        }

        rule.onNodeWithTag("MusicItemRow_current_paused_wave").assertIsDisplayed()
        rule.onAllNodesWithTag("MusicItemRow_current_wave_bar").assertCountEquals(3)
        rule.onNodeWithTag("MusicItemRow_current_playing_wave").assertDoesNotExist()
        rule.onNodeWithTag("MusicItemRow_root").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "当前歌曲，已暂停",
            ),
        )
    }

    @Test fun normalRowDoesNotExposeCurrentPlaybackWave() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                )
            }
        }

        rule.onAllNodesWithTag("MusicItemRow_current_playing_wave").assertCountEquals(0)
        rule.onAllNodesWithTag("MusicItemRow_current_paused_wave").assertCountEquals(0)
        rule.onAllNodesWithTag("MusicItemRow_current_wave_bar").assertCountEquals(0)
    }
}
