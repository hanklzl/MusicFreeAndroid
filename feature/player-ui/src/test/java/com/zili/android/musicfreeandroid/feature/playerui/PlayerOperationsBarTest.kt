package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
class PlayerOperationsBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `cover operation row uses RN height and six fixed slots`() {
        setContent()

        val rowHeight = composeRule.onNodeWithTag(PlayerOperationsBarTestTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val slotHeights = composeRule.onAllNodesWithTag(PlayerOperationSlotTestTag)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
            .distinct()

        with(composeRule.density) {
            assertTrue(rowHeight.toDp() > 0.dp)
            assertEquals(1, slotHeights.size)
            assertTrue(slotHeights.single().toDp() > 0.dp)
            assertTrue(rowHeight.toDp() >= slotHeights.single().toDp())
        }
        assertEquals(6, composeRule.onAllNodesWithTag(PlayerOperationSlotTestTag).fetchSemanticsNodes().size)
    }

    @Test
    fun `cover operation visuals match RN icon and image sizes`() {
        setContent()

        val iconSizes = composeRule.onAllNodesWithTag(PlayerOperationIconVisualTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.size }
            .distinct()
        val imageSizes = composeRule.onAllNodesWithTag(PlayerOperationImageVisualTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.size }
            .distinct()

        assertEquals(1, iconSizes.size)
        assertEquals(1, imageSizes.size)
        assertTrue(imageSizes.single().isLargerThan(iconSizes.single()))
    }

    @Test
    fun `cover operation callbacks remain wired`() {
        var favoriteClicks = 0
        var lyricClicks = 0
        setContent(onToggleFav = { favoriteClicks++ }, onToggleLyrics = { lyricClicks++ })

        composeRule.onNodeWithContentDescription("收藏").performClick()
        composeRule.onNodeWithContentDescription("歌词").performClick()

        composeRule.runOnIdle {
            assertEquals(1, favoriteClicks)
            assertEquals(1, lyricClicks)
        }
    }

    private fun setContent(
        onToggleFav: () -> Unit = {},
        onToggleLyrics: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 360.dp, height = 120.dp)) {
                    PlayerOperationsBar(
                        isFav = false,
                        hasCurrentItem = true,
                        onToggleFav = onToggleFav,
                        onAddToPlaylist = {},
                        onToggleLyrics = onToggleLyrics,
                    )
                }
            }
        }
    }

    private fun Size.isLargerThan(other: Size): Boolean = width > other.width && height > other.height
}
