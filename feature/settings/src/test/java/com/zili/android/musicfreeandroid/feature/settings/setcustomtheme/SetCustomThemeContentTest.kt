package com.zili.android.musicfreeandroid.feature.settings.setcustomtheme

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.theme.DarkMusicFreeColors
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.runtime.CONFIGURABLE_COLOR_KEYS
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SetCustomThemeContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `color grid renders all configurable color keys`() {
        composeRule.setContent {
            MusicFreeTheme {
                ConfigurableColorGrid(
                    colors = DarkMusicFreeColors,
                    onColorClicked = {},
                )
            }
        }
        CONFIGURABLE_COLOR_KEYS.forEach { key ->
            composeRule
                .onNodeWithTag(FidelityAnchors.SetCustomTheme.ColorItemPrefix + key)
                .assertExists()
        }
    }

    @Test
    fun `color grid click triggers callback with key`() {
        var clicked: String? = null
        composeRule.setContent {
            MusicFreeTheme {
                ConfigurableColorGrid(
                    colors = DarkMusicFreeColors,
                    onColorClicked = { clicked = it },
                )
            }
        }
        composeRule
            .onNodeWithTag(FidelityAnchors.SetCustomTheme.ColorItemPrefix + "primary")
            .performClick()
        assert(clicked == "primary") { "expected primary, got $clicked" }
    }

    @Test
    fun `blur opacity sliders are displayed`() {
        composeRule.setContent {
            MusicFreeTheme {
                BlurOpacitySliders(
                    blur = 10f,
                    opacity = 0.5f,
                    onBlurChange = {},
                    onOpacityChange = {},
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.SetCustomTheme.SliderBlur).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.SetCustomTheme.SliderOpacity).assertIsDisplayed()
    }

    @Test
    fun `background picker renders placeholder when no url`() {
        composeRule.setContent {
            MusicFreeTheme {
                BackgroundPickerSection(
                    currentUrl = null,
                    onImagePicked = {},
                )
            }
        }
        composeRule.onNodeWithTag(FidelityAnchors.SetCustomTheme.Image).assertIsDisplayed()
    }
}
