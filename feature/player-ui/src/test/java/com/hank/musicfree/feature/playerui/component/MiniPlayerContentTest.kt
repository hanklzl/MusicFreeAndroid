package com.hank.musicfree.feature.playerui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.playerui.PlayerViewModel
import com.hank.musicfree.player.model.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MiniPlayerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildTestUiModel(
        isPlaying: Boolean = false,
        progress: Float = 0f,
        title: String = "Test Song",
        artist: String = "Test Artist",
    ) = MiniPlayerUiModel(
        coverUri = null,
        title = title,
        artist = artist,
        isPlaying = isPlaying,
        progress = progress,
        hasPrev = true,
        hasNext = true,
        prevTitle = null,
        nextTitle = null,
    )

    private fun playerStateWithMedia() = PlayerState.EMPTY.copy(
        currentItem = MusicItem(
            id = "1",
            platform = "local",
            title = "Test Song",
            artist = "Test Artist",
            album = null,
            duration = 180_000L,
            url = null,
            artwork = null,
            qualities = null,
        ),
        duration = 180_000L,
        position = 42_000L,
    )

    @Test
    fun `mini player content routes taps to the intended callbacks`() {
        var openPlayerClicks = 0
        var playPauseClicks = 0
        var queueClicks = 0

        composeRule.setContent {
            MusicFreeTheme {
                MiniPlayerContent(
                    uiModel = buildTestUiModel(isPlaying = true, progress = 0.5f),
                    onOpenPlayer = { openPlayerClicks++ },
                    onTogglePlayPause = { playPauseClicks++ },
                    onOpenQueue = { queueClicks++ },
                    onSkipNext = {},
                    onSkipPrev = {},
                )
            }
        }

        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniRoot).performClick()
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniPlayPause, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniQueue, useUnmergedTree = true)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, openPlayerClicks)
            assertEquals(1, playPauseClicks)
            assertEquals(1, queueClicks)
        }
    }

    @Test
    fun `mini player wrapper routes horizontal swipes to skip commands`() {
        val playerState = MutableStateFlow(playerStateWithMedia())
        val viewModel = mock<PlayerViewModel> {
            on { this.playerState } doReturn playerState
        }

        composeRule.setContent {
            MusicFreeTheme {
                MiniPlayer(
                    onNavigateToPlayer = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.waitForIdle()
        val root = composeRule.onNodeWithTag(FidelityAnchors.Player.MiniRoot)
        root.assertIsDisplayed()

        root.performTouchInput {
            swipe(
                start = Offset(centerX - width * 0.2f, centerY),
                end = Offset(centerX + width * 0.2f, centerY),
                durationMillis = 200,
            )
        }
        composeRule.runOnIdle {
            verify(viewModel).skipToPrevious()
        }

        root.performTouchInput {
            swipe(
                start = Offset(centerX + width * 0.2f, centerY),
                end = Offset(centerX - width * 0.2f, centerY),
                durationMillis = 200,
            )
        }
        composeRule.runOnIdle {
            verify(viewModel).skipToNext()
        }
    }

    @Test
    fun `wrapper model maps player state to ui model`() {
        val state = PlayerState.EMPTY.copy(
            currentItem = MusicItem(
                id = "1",
                platform = "local",
                title = "夜空中最亮的星",
                artist = "逃跑计划",
                album = null,
                duration = 180_000L,
                url = null,
                artwork = "https://example.com/cover.jpg",
                qualities = null,
            ),
            isPlaying = true,
            duration = 180_000L,
            position = 36_000L,
        )

        val uiModel = state.toMiniPlayerUiModel()

        assertEquals("夜空中最亮的星", uiModel.title)
        assertEquals("逃跑计划", uiModel.artist)
        assertEquals(36_000f / 180_000f, uiModel.progress, 0.001f)
    }

    @Test
    fun `mini player content queue button is always visible`() {
        composeRule.setContent {
            MusicFreeTheme {
                MiniPlayerContent(
                    uiModel = buildTestUiModel(),
                    onOpenPlayer = {},
                    onTogglePlayPause = {},
                    onOpenQueue = {},
                    onSkipNext = {},
                    onSkipPrev = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(FidelityAnchors.Player.MiniQueue).assertCountEquals(1)
    }

    @Test
    fun `restored state maps to non-playing ui model with progress at saved position`() {
        val state = PlayerState.EMPTY.copy(
            currentItem = MusicItem(
                id = "42",
                platform = "local",
                title = "Restored Song",
                artist = "Artist X",
                album = null,
                duration = 180_000L,
                url = null,
                artwork = null,
                qualities = null,
            ),
            isPlaying = false,
            duration = 180_000L,
            position = 90_000L,
        )

        val uiModel = state.toMiniPlayerUiModel()

        assertEquals("Restored Song", uiModel.title)
        assertEquals("Artist X", uiModel.artist)
        assertEquals(false, uiModel.isPlaying)
        assertEquals(0.5f, uiModel.progress, 0.0001f)
    }

    @Test
    fun `mini player keeps long title in a shared one line text node`() {
        val longTitle = "我们都是这样长大的一首需要共享歌手剩余空间的完整歌曲标题"

        composeRule.setContent {
            MusicFreeTheme {
                MiniPlayerContent(
                    uiModel = buildTestUiModel(
                        title = longTitle,
                        artist = "Eason",
                    ),
                    onOpenPlayer = {},
                    onTogglePlayPause = {},
                    onOpenQueue = {},
                    onSkipNext = {},
                    onSkipPrev = {},
                )
            }
        }

        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniRoot).assertIsDisplayed()
        composeRule.onNodeWithText(longTitle, substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.MiniQueue, useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniQueue, useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
