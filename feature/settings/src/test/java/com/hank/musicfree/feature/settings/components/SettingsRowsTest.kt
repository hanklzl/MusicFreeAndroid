package com.hank.musicfree.feature.settings.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `enabled value row invokes click`() {
        var clicks = 0
        composeRule.setContent {
            MusicFreeTheme {
                SettingValueRow(
                    title = "最大同时下载数目",
                    value = "3",
                    enabled = true,
                    testTag = "row.maxDownload",
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("row.maxDownload").performClick()

        composeRule.runOnIdle {
            assertEquals(1, clicks)
        }
    }

    @Test
    fun `disabled value row shows pending label and ignores click`() {
        var clicks = 0
        composeRule.setContent {
            MusicFreeTheme {
                SettingValueRow(
                    title = "软件启动时自动更新插件",
                    value = "待接入",
                    enabled = false,
                    testTag = "row.autoUpdatePlugin",
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithText("待接入").assertIsDisplayed()
        composeRule.onNodeWithTag("row.autoUpdatePlugin").performClick()

        composeRule.runOnIdle {
            assertEquals(0, clicks)
        }
    }

    @Test
    fun `section card displays title and content`() {
        composeRule.setContent {
            MusicFreeTheme {
                SettingSectionCard(
                    title = "下载",
                    testTag = "section.download",
                ) {
                    SettingValueRow(
                        title = "默认下载音质",
                        value = "标准音质",
                        enabled = true,
                        onClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("section.download").assertIsDisplayed()
        composeRule.onNodeWithText("下载").assertIsDisplayed()
        composeRule.onNodeWithText("默认下载音质").assertIsDisplayed()
        composeRule.onNodeWithText("不存在").assertDoesNotExist()
    }
}
