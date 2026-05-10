package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackMode
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerCoverLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `operations bar stays close to seek bar`() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        PlayerCoverPageContent(
                            artworkUrl = null,
                            isFav = false,
                            hasCurrentItem = true,
                            currentQuality = PlayQuality.STANDARD,
                            isDownloaded = false,
                            currentSpeed = 1.0f,
                            onToggleFav = {},
                            onAddToPlaylist = {},
                            onToggleLyrics = {},
                            onQualityClick = {},
                            onDownloadClick = {},
                            onSpeedClick = {},
                            modifier = Modifier.weight(1f),
                        )
                        PlayerSeekBar(
                            position = 0L,
                            duration = 180_000L,
                            onSeek = {},
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .testTag(PlayerSeekBarTestTag),
                        )
                        PlayerControls(
                            isPlaying = false,
                            playbackMode = PlaybackMode.Queue,
                            onTogglePlayPause = {},
                            onSkipPrevious = {},
                            onSkipNext = {},
                            onCyclePlaybackMode = {},
                            onOpenQueue = {},
                        )
                    }
                }
            }
        }

        val operationsBounds = composeRule.onNodeWithTag(PlayerOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val seekBounds = composeRule.onNodeWithTag(PlayerSeekBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot

        val gapDp = with(composeRule.density) {
            (seekBounds.top - operationsBounds.bottom).toDp()
        }
        assertTrue(
            "Expected operations and seek bar gap to be between 0.dp and 20.dp, was $gapDp",
            gapDp >= 0.dp && gapDp <= 20.dp,
        )
    }
}
