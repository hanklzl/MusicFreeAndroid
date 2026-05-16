package com.hank.musicfree.feature.playerui.component.rate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.feature.playerui.component.rate.PlayRateSheetItemTestTagPrefix
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayRateSheetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `lists all defined rates`() {
        rule.setContent {
            MusicFreeTheme {
                PlayRateSheetContent(current = 1.0f, onSelect = {})
            }
        }
        PlaybackSpeeds.ALL.forEach { rate ->
            rule.onNodeWithTag(PlayRateSheetItemTestTagPrefix + rate.toString()).assertIsDisplayed()
        }
    }

    @Test
    fun `clicking a rate calls onSelect`() {
        var selected: Float? = null
        rule.setContent {
            MusicFreeTheme {
                PlayRateSheetContent(current = 1.0f, onSelect = { selected = it })
            }
        }
        rule.onNodeWithTag(PlayRateSheetItemTestTagPrefix + "1.5").performClick()
        assertEquals(1.5f, selected)
    }
}
