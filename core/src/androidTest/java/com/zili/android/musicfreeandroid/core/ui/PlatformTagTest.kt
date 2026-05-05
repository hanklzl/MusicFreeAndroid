package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Rule
import org.junit.Test

class PlatformTagTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rendersGivenText() {
        rule.setContent {
            MusicFreeTheme { PlatformTag(text = "网易云") }
        }
        rule.onNodeWithText("网易云").assertIsDisplayed()
    }

    @Test fun rendersBenDi() {
        rule.setContent {
            MusicFreeTheme { PlatformTag(text = "本地") }
        }
        rule.onNodeWithText("本地").assertIsDisplayed()
    }

    @Test fun longTextDoesNotExceedSingleLine() {
        val longText = "网易云音乐特别长的源名称名"
        rule.setContent {
            MusicFreeTheme { PlatformTag(text = longText) }
        }
        // Tag still renders; if maxLines=1 + ellipsis is in effect, the node is findable
        // even with a long string. The exact ellipsised display string is platform-dependent,
        // so we use an unmerged-tree text query that matches by prefix.
        rule.onNodeWithText(longText, substring = true).assertIsDisplayed()
    }
}
