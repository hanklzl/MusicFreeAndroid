package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.PlaybackMode
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `mode button shows queue mode semantics`() {
        setControls(mode = PlaybackMode.Queue)

        composeRule.onNodeWithContentDescription("列表循环").assertExists()
    }

    @Test
    fun `mode button shows shuffle mode semantics`() {
        setControls(mode = PlaybackMode.Shuffle)

        composeRule.onNodeWithContentDescription("随机播放").assertExists()
    }

    @Test
    fun `mode button shows single mode semantics`() {
        setControls(mode = PlaybackMode.Single)

        composeRule.onNodeWithContentDescription("单曲循环").assertExists()
    }

    @Test
    fun `mode button click invokes playback mode cycle callback`() {
        var clicks = 0
        setControls(
            mode = PlaybackMode.Queue,
            onCyclePlaybackMode = { clicks++ },
        )

        composeRule.onNodeWithTag(PlayerModeButtonTestTag).performClick()

        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    private fun setControls(
        mode: PlaybackMode,
        onCyclePlaybackMode: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                    PlayerControls(
                        isPlaying = false,
                        playbackMode = mode,
                        onTogglePlayPause = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onCyclePlaybackMode = onCyclePlaybackMode,
                    )
                }
            }
        }
    }
}
