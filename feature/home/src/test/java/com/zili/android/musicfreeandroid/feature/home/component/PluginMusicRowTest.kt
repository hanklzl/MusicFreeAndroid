package com.zili.android.musicfreeandroid.feature.home.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PluginMusicRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `plugin music row shows platform tag and artist album subtitle`() {
        composeRule.setContent {
            MusicFreeTheme {
                PluginMusicRow(
                    index = 0,
                    item = track(),
                    isFavorite = false,
                    onClick = {},
                    onLongClick = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("夜空中最亮的星").assertIsDisplayed()
        composeRule.onNodeWithText("网易云").assertIsDisplayed()
        composeRule.onNodeWithText("逃跑计划 - 世界").assertIsDisplayed()
    }

    private fun track() = MusicItem(
        id = "song-1",
        platform = "网易云",
        title = "夜空中最亮的星",
        artist = "逃跑计划",
        album = "世界",
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
