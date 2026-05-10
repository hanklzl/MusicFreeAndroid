package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.LyricDocument
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.ParsedLyricLine
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import org.junit.Assert.assertEquals
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

    private lateinit var expectedSizes: OperationExpectedSizes

    @Test
    fun `lyric operation row uses RN height and five fixed slots`() {
        setContent()

        val rowHeight = composeRule.onNodeWithTag(LyricOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val slotBounds = composeRule.onAllNodesWithTag(LyricOperationSlotTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }

        assertPxEquals("row height", expectedSizes.rowHeightPx, rowHeight)
        assertEquals(5, slotBounds.size)
        slotBounds.forEach { bounds ->
            assertPxEquals("slot width", expectedSizes.slotSizePx, bounds.width)
            assertPxEquals("slot height", expectedSizes.slotSizePx, bounds.height)
        }
    }

    @Test
    fun `lyric operation icons share one RN visual size`() {
        setContent()

        val iconSizes = composeRule.onAllNodesWithTag(
            LyricOperationIconVisualTestTag,
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.size }

        assertEquals(5, iconSizes.size)
        iconSizes.forEach { size ->
            assertSizeEquals(expectedSizes.iconSizePx, size)
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
                val density = LocalDensity.current
                expectedSizes = with(density) {
                    OperationExpectedSizes(
                        rowHeightPx = rpx(80).toPx(),
                        slotSizePx = rpx(64).toPx(),
                        iconSizePx = IconSizes.normal.toPx(),
                    )
                }
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

    private fun assertSizeEquals(expectedPx: Float, actual: Size) {
        assertPxEquals("width", expectedPx, actual.width)
        assertPxEquals("height", expectedPx, actual.height)
    }

    private fun assertPxEquals(label: String, expected: Float, actual: Float) {
        assertEquals(label, expected, actual, SizeTolerancePx)
    }

    private data class OperationExpectedSizes(
        val rowHeightPx: Float,
        val slotSizePx: Float,
        val iconSizePx: Float,
    )

    private companion object {
        private const val LyricOperationsBarTestTag = "player.lyrics.operations.bar"
        private const val LyricOperationSlotTestTag = "player.lyrics.operations.slot"
        private const val LyricOperationIconVisualTestTag = "player.lyrics.operations.iconVisual"
        private const val SizeTolerancePx = 0.5f
    }
}
