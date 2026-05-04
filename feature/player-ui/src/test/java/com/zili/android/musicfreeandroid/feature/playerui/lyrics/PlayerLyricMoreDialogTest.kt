package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerLyricMoreDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clear associated lyric action is exposed`() {
        var clearAssociatedClicks = 0

        composeRule.setContent {
            MusicFreeTheme {
                PlayerLyricMoreDialog(
                    onDismiss = {},
                    onImportRaw = {},
                    onImportTranslation = {},
                    onDeleteLocal = {},
                    onClearAssociated = { clearAssociatedClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("解除关联歌词").performClick()

        composeRule.runOnIdle {
            assertEquals(1, clearAssociatedClicks)
        }
    }
}
