package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.R as CoreR
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerOperationsBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var expectedSizes: OperationExpectedSizes

    @Test
    fun `quality operation image follows current quality`() {
        assertEquals(CoreR.drawable.ic_quality_low, playerQualityImage(PlayQuality.LOW))
        assertEquals(CoreR.drawable.ic_quality_standard, playerQualityImage(PlayQuality.STANDARD))
        assertEquals(CoreR.drawable.ic_quality_high, playerQualityImage(PlayQuality.HIGH))
        assertEquals(CoreR.drawable.ic_quality_super, playerQualityImage(PlayQuality.SUPER))
    }

    @Test
    fun `rate operation image follows current speed`() {
        assertEquals(CoreR.drawable.ic_rate_050, playerRateImage(0.5f))
        assertEquals(CoreR.drawable.ic_rate_075, playerRateImage(0.75f))
        assertEquals(CoreR.drawable.ic_rate_100, playerRateImage(1.0f))
        assertEquals(CoreR.drawable.ic_rate_125, playerRateImage(1.25f))
        assertEquals(CoreR.drawable.ic_rate_150, playerRateImage(1.5f))
        assertEquals(CoreR.drawable.ic_rate_175, playerRateImage(1.75f))
        assertEquals(CoreR.drawable.ic_rate_200, playerRateImage(2.0f))
        assertEquals(CoreR.drawable.ic_rate_100, playerRateImage(1.1f))
    }

    @Test
    fun `cover operation row uses RN height and six fixed slots`() {
        setContent()

        val rowBounds = composeRule.onNodeWithTag(PlayerOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
        val slotBounds = composeRule.onAllNodesWithTag(CoverOperationSlotTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }

        assertPxEquals("row height", expectedSizes.rowHeightPx, rowBounds.height)
        assertEquals(6, slotBounds.size)
        slotBounds.forEach { bounds ->
            assertPxEquals("slot width", expectedSizes.slotSizePx, bounds.width)
            assertPxEquals("slot height", expectedSizes.slotSizePx, bounds.height)
        }
        assertSpaceAroundWithoutHorizontalPadding(
            rowBounds = rowBounds,
            slotBounds = slotBounds,
            itemCount = 6,
        )
    }

    @Test
    fun `cover operation visuals match RN icon and image sizes`() {
        setContent()

        val iconSizes = composeRule.onAllNodesWithTag(CoverOperationIconVisualTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.size }
        val imageSizes = composeRule.onAllNodesWithTag(CoverOperationImageVisualTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.size }

        assertEquals(4, iconSizes.size)
        assertEquals(2, imageSizes.size)
        iconSizes.forEach { size ->
            assertSizeEquals(expectedSizes.iconSizePx, size)
        }
        imageSizes.forEach { size ->
            assertSizeEquals(expectedSizes.imageSizePx, size)
        }
        assertTrue(imageSizes.first().isLargerThan(iconSizes.first()))
    }

    @Test
    fun `cover operation callbacks remain wired`() {
        var favoriteClicks = 0
        var lyricClicks = 0
        var moreClicks = 0
        setContent(
            onToggleFav = { favoriteClicks++ },
            onToggleLyrics = { lyricClicks++ },
            onMoreClick = { moreClicks++ },
        )

        composeRule.onNodeWithContentDescription("收藏").performClick()
        composeRule.onNodeWithContentDescription("歌词").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()

        composeRule.runOnIdle {
            assertEquals(1, favoriteClicks)
            assertEquals(1, lyricClicks)
            assertEquals(1, moreClicks)
        }
    }

    @Test
    fun `lyric operations bottom spacer uses RN margin`() {
        composeRule.setContent {
            MusicFreeTheme {
                val density = LocalDensity.current
                expectedSizes = with(density) {
                    OperationExpectedSizes(
                        rowHeightPx = rpx(80).toPx(),
                        slotSizePx = rpx(64).toPx(),
                        iconSizePx = IconSizes.normal.toPx(),
                        imageSizePx = rpx(52).toPx(),
                        lyricBottomSpacerHeightPx = rpx(24).toPx(),
                    )
                }
                PlayerLyricsOperationsBottomSpacer()
            }
        }

        val spacerHeight = composeRule.onNodeWithTag(PlayerLyricsOperationsBottomSpacerTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertPxEquals("lyric operations bottom spacer", expectedSizes.lyricBottomSpacerHeightPx, spacerHeight)
    }

    private fun setContent(
        onToggleFav: () -> Unit = {},
        onToggleLyrics: () -> Unit = {},
        onMoreClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                val density = LocalDensity.current
                expectedSizes = with(density) {
                    OperationExpectedSizes(
                        rowHeightPx = rpx(80).toPx(),
                        slotSizePx = rpx(64).toPx(),
                        iconSizePx = IconSizes.normal.toPx(),
                        imageSizePx = rpx(52).toPx(),
                        lyricBottomSpacerHeightPx = rpx(24).toPx(),
                    )
                }
                Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                    PlayerOperationsBar(
                        isFav = false,
                        hasCurrentItem = true,
                        currentQuality = PlayQuality.STANDARD,
                        isDownloaded = false,
                        currentSpeed = 1.0f,
                        onToggleFav = onToggleFav,
                        onToggleLyrics = onToggleLyrics,
                        onQualityClick = {},
                        onDownloadClick = {},
                        onSpeedClick = {},
                        onMoreClick = onMoreClick,
                    )
                }
            }
        }
    }

    private fun assertSizeEquals(expectedPx: Float, actual: Size) {
        assertPxEquals("width", expectedPx, actual.width)
        assertPxEquals("height", expectedPx, actual.height)
    }

    private fun assertPxEquals(label: String, expected: Float, actual: Float) {
        assertEquals(label, expected, actual, SizeTolerancePx)
    }

    private fun assertSpaceAroundWithoutHorizontalPadding(
        rowBounds: Rect,
        slotBounds: List<Rect>,
        itemCount: Int,
    ) {
        val rowWidth = rowBounds.width
        val slotSize = expectedSizes.slotSizePx
        val space = (rowWidth - itemCount * slotSize) / itemCount
        val expectedLeading = space / 2f
        val firstLeft = slotBounds.first().left - rowBounds.left
        val lastRight = slotBounds.last().right - rowBounds.left

        assertPxEquals("first slot leading", expectedLeading, firstLeft)
        assertPxEquals("last slot trailing", rowWidth - expectedLeading, lastRight)
    }

    private fun Size.isLargerThan(other: Size): Boolean = width > other.width && height > other.height

    private data class OperationExpectedSizes(
        val rowHeightPx: Float,
        val slotSizePx: Float,
        val iconSizePx: Float,
        val imageSizePx: Float,
        val lyricBottomSpacerHeightPx: Float,
    )

    private companion object {
        private const val CoverOperationSlotTestTag = "player.operations.slot"
        private const val CoverOperationIconVisualTestTag = "player.operations.iconVisual"
        private const val CoverOperationImageVisualTestTag = "player.operations.imageVisual"
        private const val SizeTolerancePx = 1f
    }
}
