package com.hank.musicfree.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MusicItemMoreMenuTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsPlayNextAndAddToPlaylist_whenInActionSet() {
        rule.setContent {
            MusicItemMoreMenu(
                actions = setOf(MusicItemAction.PlayNext, MusicItemAction.AddToPlaylist),
                isFavorite = false,
                onAction = {},
                triggerIcon = ColorPainter(Color.Black),
            )
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithText("下一首播放").assertIsDisplayed()
        rule.onNodeWithText("加入歌单").assertIsDisplayed()
    }

    @Test fun toggleFavoriteLabelFlipsByIsFavorite() {
        rule.setContent {
            MusicItemMoreMenu(
                actions = setOf(MusicItemAction.ToggleFavorite),
                isFavorite = true,
                onAction = {},
                triggerIcon = ColorPainter(Color.Black),
            )
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithText("取消收藏").assertIsDisplayed()
    }

    @Test fun emitsCorrectActionOnClick() {
        var captured: MusicItemAction? = null
        rule.setContent {
            MusicItemMoreMenu(
                actions = setOf(MusicItemAction.PlayNext, MusicItemAction.AddToPlaylist),
                isFavorite = false,
                onAction = { captured = it },
                triggerIcon = ColorPainter(Color.Black),
            )
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithTag("MusicItemMoreMenu_AddToPlaylist").performClick()
        assertEquals(MusicItemAction.AddToPlaylist, captured)
    }
}
