package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.player.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MiniPlayerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `mini player content routes taps to the intended callbacks`() {
        var openPlayerClicks = 0
        var playPauseClicks = 0
        var queueClicks = 0

        composeRule.setContent {
            MusicFreeTheme {
                MiniPlayerContent(
                    uiModel = MiniPlayerUiModel(
                        coverUri = null,
                        title = "夜空中最亮的星",
                        subtitle = "逃跑计划",
                        isPlaying = true,
                        showQueueButton = true,
                    ),
                    onOpenPlayer = { openPlayerClicks++ },
                    onTogglePlayPause = { playPauseClicks++ },
                    onOpenQueue = { queueClicks++ },
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

        composeRule.onAllNodesWithContentDescription("下一曲").assertCountEquals(0)
    }

    @Test
    fun `wrapper model hides queue button when queue callback is absent`() {
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
        )

        val withoutQueue = state.toMiniPlayerUiModel(onNavigateToQueue = null)
        val withQueue = state.toMiniPlayerUiModel(onNavigateToQueue = {})

        assertFalse(withoutQueue.showQueueButton)
        assertTrue(withQueue.showQueueButton)
        assertEquals("夜空中最亮的星", withoutQueue.title)
        assertEquals("逃跑计划", withoutQueue.subtitle)
    }

    @Test
    fun `mini player content hides queue node when queue button is disabled`() {
        composeRule.setContent {
            MusicFreeTheme {
                MiniPlayerContent(
                    uiModel = MiniPlayerUiModel(
                        coverUri = null,
                        title = "夜空中最亮的星",
                        subtitle = "逃跑计划",
                        isPlaying = false,
                        showQueueButton = false,
                    ),
                    onOpenPlayer = {},
                    onTogglePlayPause = {},
                    onOpenQueue = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(FidelityAnchors.Player.MiniQueue).assertCountEquals(0)
    }
}
