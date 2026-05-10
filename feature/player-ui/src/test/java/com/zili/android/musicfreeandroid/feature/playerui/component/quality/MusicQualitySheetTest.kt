package com.zili.android.musicfreeandroid.feature.playerui.component.quality

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MusicQualitySheetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `lists all four qualities in order`() {
        rule.setContent {
            MusicFreeTheme {
                MusicQualitySheetContent(
                    current = PlayQuality.STANDARD,
                    mode = MusicQualitySheetMode.Play,
                    availableQualities = null,
                    onSelect = {},
                )
            }
        }
        rule.onNodeWithText("低音质").assertIsDisplayed()
        rule.onNodeWithText("标准音质").assertIsDisplayed()
        rule.onNodeWithText("高音质").assertIsDisplayed()
        rule.onNodeWithText("超高音质").assertIsDisplayed()
    }

    @Test
    fun `clicking quality calls onSelect with that quality`() {
        var selected: PlayQuality? = null
        rule.setContent {
            MusicFreeTheme {
                MusicQualitySheetContent(
                    current = PlayQuality.STANDARD,
                    mode = MusicQualitySheetMode.Play,
                    availableQualities = null,
                    onSelect = { selected = it },
                )
            }
        }
        rule.onNodeWithTag(MusicQualitySheetItemTestTagPrefix + "HIGH").performClick()
        assertEquals(PlayQuality.HIGH, selected)
    }

    @Test
    fun `download mode renders download title`() {
        rule.setContent {
            MusicFreeTheme {
                MusicQualitySheetContent(
                    current = null,
                    mode = MusicQualitySheetMode.Download,
                    availableQualities = null,
                    onSelect = {},
                )
            }
        }
        rule.onNodeWithText("选择下载音质").assertIsDisplayed()
    }

    @Test
    fun `shows size hint when availableQualities provides bytes`() {
        rule.setContent {
            MusicFreeTheme {
                MusicQualitySheetContent(
                    current = PlayQuality.STANDARD,
                    mode = MusicQualitySheetMode.Play,
                    availableQualities = mapOf(PlayQuality.HIGH to QualityInfo(url = null, size = 5_242_880L)),
                    onSelect = {},
                )
            }
        }
        rule.onNodeWithText("高音质 (5.0 MB)").assertIsDisplayed()
    }
}
