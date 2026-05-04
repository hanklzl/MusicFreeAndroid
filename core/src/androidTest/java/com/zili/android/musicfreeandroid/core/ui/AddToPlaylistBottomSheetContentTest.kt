package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddToPlaylistBottomSheetContentTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rendersFavoriteOnTop_andOtherRows() {
        val playlists = listOf(
            Playlist(id = "favorite", name = "我喜欢", coverUri = null),
            Playlist(id = "p1", name = "通勤", coverUri = null, worksNum = 12),
        )
        rule.setContent {
            AddToPlaylistBottomSheetContent(
                playlists = playlists,
                onSelect = {},
                onCreateNew = {},
                folderPlusIcon = ColorPainter(Color.Black),
                favoriteCoverIcon = ColorPainter(Color.Red),
            )
        }
        rule.onNodeWithText("新建歌单").assertIsDisplayed()
        rule.onNodeWithText("我喜欢").assertIsDisplayed()
        rule.onNodeWithText("通勤").assertIsDisplayed()
    }

    @Test fun selectionEmitsSelectedPlaylist() {
        var selected: Playlist? = null
        val playlists = listOf(
            Playlist(id = "favorite", name = "我喜欢", coverUri = null),
            Playlist(id = "p1", name = "通勤", coverUri = null, worksNum = 12),
        )
        rule.setContent {
            AddToPlaylistBottomSheetContent(
                playlists = playlists,
                onSelect = { selected = it },
                onCreateNew = {},
                folderPlusIcon = ColorPainter(Color.Black),
                favoriteCoverIcon = ColorPainter(Color.Red),
            )
        }
        rule.onNodeWithTag("AddToPlaylist_Row_p1").performClick()
        assertEquals("p1", selected?.id)
    }

    @Test fun createNewClickFires() {
        var fired = false
        rule.setContent {
            AddToPlaylistBottomSheetContent(
                playlists = emptyList(),
                onSelect = {},
                onCreateNew = { fired = true },
                folderPlusIcon = ColorPainter(Color.Black),
                favoriteCoverIcon = ColorPainter(Color.Red),
            )
        }
        rule.onNodeWithTag("AddToPlaylist_CreateNew").performClick()
        assertTrue(fired)
    }
}
