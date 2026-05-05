package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
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

        composeRule.waitForIdle()
        composeRule.onNodeWithText("第一行").performTouchInput {
            click(center)
        }

        composeRule.runOnIdle {
            assertEquals(0, backToCoverClicks)
        }
    }

    @Test
    fun `tapping lyric blank area returns to cover`() {
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

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerLyricsContentTestTag).performTouchInput {
            click(Offset(x = centerX, y = bottom - 8f))
        }

        composeRule.runOnIdle {
            assertEquals(1, backToCoverClicks)
        }
    }

    @Test
    fun `drag overlay blocks lyric blank tap from returning to cover`() {
        assertFalse(
            shouldHandleLyricBackTap(
                tapY = 8f,
                visibleItemBounds = emptyList(),
                dragSeekOverlayVisible = true,
            ),
        )
    }

    @Test
    fun `auto follow only runs when not scrolling and drag overlay is hidden`() {
        assertEquals(
            true,
            shouldAutoFollowLyricLine(
                isScrollInProgress = false,
                dragSeekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isScrollInProgress = true,
                dragSeekOverlayVisible = false,
            ),
        )
        assertFalse(
            shouldAutoFollowLyricLine(
                isScrollInProgress = false,
                dragSeekOverlayVisible = true,
            ),
        )
    }

    @Test
    fun noLyricStateShowsSeparateStatusAndSearchAction() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PlayerLyricsContent(
                        state = readyState().copy(
                            loadState = LyricLoadState.NoLyric(readyState().music()),
                            document = null,
                            currentLineIndex = null,
                        ),
                        durationMs = 10_000L,
                        onBackToCover = {},
                        onSeekToLine = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerLyricsNoLyricTextTestTag, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(PlayerLyricsSearchTextTestTag, useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("暂无歌词\n搜索歌词").assertCountEquals(0)
    }

    @Test
    fun searchActionDoesNotTriggerBackToCover() {
        var backToCoverClicks = 0

        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    PlayerLyricsContent(
                        state = readyState().copy(
                            loadState = LyricLoadState.NoLyric(readyState().music()),
                            document = null,
                            currentLineIndex = null,
                        ),
                        durationMs = 10_000L,
                        onBackToCover = { backToCoverClicks++ },
                        onSeekToLine = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PlayerLyricsSearchTextTestTag, useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals(0, backToCoverClicks)
        }
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

    private fun PlayerLyricsUiState.music(): MusicItem =
        (loadState as LyricLoadState.Ready).music
}
