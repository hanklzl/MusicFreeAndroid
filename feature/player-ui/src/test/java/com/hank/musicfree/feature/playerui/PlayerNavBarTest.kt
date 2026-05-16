package com.hank.musicfree.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w375dp-h812dp")
class PlayerNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `description row keeps RN height and displays Chinese artist with platform tag`() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 375.dp, height = 150.dp)) {
                    PlayerNavBar(
                        title = "孤勇者",
                        artist = "陈奕迅",
                        platform = "元力QQ",
                        onBack = {},
                        onShare = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PlayerNavDescriptionRowTestTag)
            .assertIsDisplayed()
            .assertHeightIsEqualTo(16.dp)
        composeRule.onNodeWithText("陈奕迅").assertIsDisplayed()
        composeRule.onNodeWithText("元力QQ").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerPlatformTagTestTag)
            .assertIsDisplayed()
            .assertHeightIsEqualTo(16.dp)
    }

    @Test
    fun `platform tag keeps RN height and remains visible with long artist`() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(width = 220.dp, height = 150.dp)) {
                    PlayerNavBar(
                        title = "Song",
                        artist = "Very very very very very long artist name",
                        platform = "元力QQ",
                        onBack = {},
                        onShare = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PlayerNavDescriptionRowTestTag)
            .assertIsDisplayed()
            .assertHeightIsEqualTo(16.dp)
        composeRule.onNodeWithText("元力QQ").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerPlatformTagTestTag)
            .assertIsDisplayed()
            .assertHeightIsEqualTo(16.dp)
    }
}
