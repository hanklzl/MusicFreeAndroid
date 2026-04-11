package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
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
    fun `mini player content exposes canonical anchors and omits next track action`() {
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
                    onOpenPlayer = {},
                    onTogglePlayPause = { playPauseClicks++ },
                    onOpenQueue = { queueClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniPlayPause, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(FidelityAnchors.Player.MiniQueue, useUnmergedTree = true)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, playPauseClicks)
            assertEquals(1, queueClicks)
        }

        composeRule.onAllNodesWithContentDescription("下一曲").assertCountEquals(0)
    }
}
