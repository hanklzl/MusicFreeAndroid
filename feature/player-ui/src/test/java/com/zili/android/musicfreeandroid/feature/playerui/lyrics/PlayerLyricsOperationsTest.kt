package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerLyricsOperationsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `lyric operation row uses RN height and five fixed slots`() {
        setContent()

        val rowHeight = composeRule.onNodeWithTag(PlayerLyricsOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val slotHeights = composeRule.onAllNodesWithTag(PlayerLyricsOperationSlotTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
            .distinct()

        with(composeRule.density) {
            assertTrue(rowHeight.toDp() > 0.dp)
            assertEquals(1, slotHeights.size)
            assertTrue(slotHeights.single().toDp() > 0.dp)
            assertTrue(rowHeight.toDp() >= slotHeights.single().toDp())
        }
        assertEquals(5, composeRule.onAllNodesWithTag(PlayerLyricsOperationSlotTestTag).fetchSemanticsNodes().size)
    }

    @Test
    fun `lyric operation icons share one RN visual size`() {
        setContent()

        val iconSizes = composeRule.onAllNodesWithTag(
            PlayerLyricsOperationIconVisualTestTag,
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.size }
            .distinct()

        with(composeRule.density) {
            assertEquals(1, iconSizes.size)
            assertTrue(iconSizes.single().height.toDp() > 0.dp)
            assertTrue(iconSizes.single().width.toDp() > 0.dp)
        }
    }

    @Test
    fun `lyric operation callbacks remain wired`() {
        var fontClicks = 0
        var offsetClicks = 0
        var searchClicks = 0
        var translationClicks = 0
        var moreClicks = 0
        setContent(
            onFontSize = { fontClicks++ },
            onOffset = { offsetClicks++ },
            onSearch = { searchClicks++ },
            onToggleTranslation = { translationClicks++ },
            onMore = { moreClicks++ },
        )

        composeRule.onNodeWithContentDescription("调整歌词字号").performClick()
        composeRule.onNodeWithContentDescription("调整歌词进度").performClick()
        composeRule.onNodeWithContentDescription("搜索歌词").performClick()
        composeRule.onNodeWithContentDescription("切换歌词翻译").performClick()
        composeRule.onNodeWithContentDescription("歌词更多").performClick()

        composeRule.runOnIdle {
            assertEquals(1, fontClicks)
            assertEquals(1, offsetClicks)
            assertEquals(1, searchClicks)
            assertEquals(1, translationClicks)
            assertEquals(1, moreClicks)
        }
    }

    private fun setContent(
        onFontSize: () -> Unit = {},
        onOffset: () -> Unit = {},
        onSearch: () -> Unit = {},
        onToggleTranslation: () -> Unit = {},
        onMore: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                    PlayerLyricsOperations(
                        state = PlayerLyricsUiState(
                            document = translatedDocument(),
                            showTranslation = true,
                        ),
                        onFontSize = onFontSize,
                        onOffset = onOffset,
                        onSearch = onSearch,
                        onToggleTranslation = onToggleTranslation,
                        onMore = onMore,
                    )
                }
            }
        }
    }

    private fun translatedDocument(): LyricDocument = LyricDocument(
        musicId = "music-1",
        musicPlatform = "demo",
        lines = listOf(
            ParsedLyricLine(
                index = 0,
                timeMs = 1_000L,
                text = "第一行",
                translation = "First line",
            ),
        ),
        source = LyricSourceInfo.Plugin("demo"),
    )
}
