package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
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
class PlayQueueSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun item(id: String, title: String = "Song $id", artist: String = "Artist") =
        MusicItem(
            id = id, platform = "test", title = title, artist = artist,
            album = null, duration = 1_000L, url = null, artwork = null, qualities = null,
        )

    private fun ui(
        items: List<MusicItem> = listOf(item("1"), item("2"), item("3")),
        currentIndex: Int = 1,
        repeatMode: RepeatMode = RepeatMode.OFF,
    ) = PlayQueueUiModel(items = items, currentIndex = currentIndex, repeatMode = repeatMode)

    @Test
    fun `header shows count`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(),
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onNodeWithText("播放列表 ").assertIsDisplayed()
        composeRule.onNodeWithText("(3首)").assertIsDisplayed()
    }

    @Test
    fun `current row renders the current marker`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(currentIndex = 0),
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithTag(
            FidelityAnchors.Player.Queue.CurrentMarker,
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    @Test
    fun `clicking a row calls onPlayIndex with row index`() {
        var clickedIndex = -1
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(currentIndex = 0),
                    onPlayIndex = { clickedIndex = it },
                    onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.Queue.Row)[1].performClick()
        composeRule.runOnIdle { assertEquals(1, clickedIndex) }
    }

    @Test
    fun `clicking remove on a row calls onRemove with that index`() {
        var removedIndex = -1
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(currentIndex = 0),
                    onPlayIndex = {}, onRemove = { removedIndex = it },
                    onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.Queue.RemoveButton)[2].performClick()
        composeRule.runOnIdle { assertEquals(2, removedIndex) }
    }

    @Test
    fun `clicking clear button calls onClear`() {
        var clearCount = 0
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(),
                    onPlayIndex = {}, onRemove = {},
                    onClear = { clearCount++ }, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.Player.Queue.ClearButton).performClick()
        composeRule.runOnIdle { assertEquals(1, clearCount) }
    }

    @Test
    fun `clicking repeat mode button calls onCycleRepeatMode`() {
        var cycleCount = 0
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(),
                    onPlayIndex = {}, onRemove = {}, onClear = {},
                    onCycleRepeatMode = { cycleCount++ },
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.Player.Queue.RepeatModeButton).performClick()
        composeRule.runOnIdle { assertEquals(1, cycleCount) }
    }

    @Test
    fun `repeat mode label updates with mode`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = ui(repeatMode = RepeatMode.ONE),
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onAllNodesWithText("单曲循环").onFirst().assertIsDisplayed()
    }

    @Test
    fun `empty state renders placeholder`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlayQueueSheetContent(
                    uiModel = PlayQueueUiModel.EMPTY,
                    onPlayIndex = {}, onRemove = {}, onClear = {}, onCycleRepeatMode = {},
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.Player.Queue.EmptyState).assertIsDisplayed()
        composeRule.onAllNodesWithTag(FidelityAnchors.Player.Queue.Row).assertCountEquals(0)
    }
}
