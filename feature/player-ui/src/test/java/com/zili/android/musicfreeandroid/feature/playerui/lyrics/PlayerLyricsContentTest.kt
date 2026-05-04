package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerLyricsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping lyric text does not return to cover`() {
        var backToCoverClicks = 0

        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PlayerLyricsContent(
                        state = readyState(),
                        durationMs = 10_000L,
                        onBackToCover = { backToCoverClicks++ },
                        onSeekToLine = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("第一行").performClick()

        composeRule.runOnIdle {
            assertEquals(0, backToCoverClicks)
        }
    }

    @Test
    fun `auto follow stays paused while drag overlay is visible`() {
        assertFalse(
            shouldAutoFollowLyricLine(
                isScrollInProgress = false,
                dragSeekOverlayVisible = true,
            ),
        )
    }

    private fun readyState(): PlayerLyricsUiState {
        val music = MusicItem(
            id = "1",
            platform = "demo",
            title = "测试歌曲",
            artist = "测试歌手",
            album = null,
            duration = 10_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
        val document = LyricDocument(
            musicId = music.id,
            musicPlatform = music.platform,
            lines = listOf(
                ParsedLyricLine(index = 0, timeMs = 1_000L, text = "第一行"),
                ParsedLyricLine(index = 1, timeMs = 2_000L, text = "第二行"),
            ),
            source = LyricSourceInfo.Plugin(music.platform),
        )
        return PlayerLyricsUiState(
            loadState = LyricLoadState.Ready(music, document, userOffsetMs = 0L),
            document = document,
            currentLineIndex = 0,
        )
    }
}
