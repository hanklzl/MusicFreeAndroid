package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.MusicItem
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicItemOptionsSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun hidesLocalLibraryRemovalActionWhenCallbackIsAbsent() {
        rule.setContent {
            MusicItemOptionsSheet(
                item = item,
                onDismiss = {},
                onDownload = {},
            )
        }

        rule.onNodeWithText("下载").assertIsDisplayed()
        rule.onNodeWithText("从本地音乐移除").assertDoesNotExist()
    }

    @Test
    fun localLibraryRemovalActionEmitsItemAndDismisses() {
        var removedItem: MusicItem? = null
        var dismissed = false
        rule.setContent {
            MusicItemOptionsSheet(
                item = item,
                onDismiss = { dismissed = true },
                onDownload = {},
                onRemoveFromLocalLibrary = { removedItem = it },
            )
        }

        rule.onNodeWithText("从本地音乐移除").performClick()

        rule.runOnIdle {
            assertSame(item, removedItem)
            assertTrue(dismissed)
        }
    }

    private companion object {
        val item = MusicItem(
            id = "local-1",
            platform = "local",
            title = "Local Song",
            artist = "Local Artist",
            album = "Local Album",
            duration = 180_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
    }
}
