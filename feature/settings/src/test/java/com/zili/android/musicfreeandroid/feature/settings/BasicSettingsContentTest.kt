package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BasicSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows all rn basic setting sections`() {
        setContent()

        listOf(
            FidelityAnchors.Settings.BasicSectionCommon,
            FidelityAnchors.Settings.BasicSectionSheetAlbum,
            FidelityAnchors.Settings.BasicSectionPlugin,
            FidelityAnchors.Settings.BasicSectionPlayback,
            FidelityAnchors.Settings.BasicSectionDownload,
            FidelityAnchors.Settings.BasicSectionNetwork,
            FidelityAnchors.Settings.BasicSectionLyric,
            FidelityAnchors.Settings.BasicSectionCache,
            FidelityAnchors.Settings.BasicSectionDeveloper,
        ).forEach { tag ->
            scrollToTag(tag)
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun `runtime backed value rows open dialogs and invoke callbacks`() {
        var maxDownload = 0
        var quality = PlayQuality.LOW
        setContent(
            onMaxDownloadChange = { maxDownload = it },
            onDefaultDownloadQualityChange = { quality = it },
        )

        scrollToTag(FidelityAnchors.Settings.BasicMaxDownload)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMaxDownload).performClick()
        composeRule.onNodeWithText("7").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicDefaultDownloadQuality)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicDefaultDownloadQuality).performClick()
        composeRule.onNodeWithText("超高音质").performClick()

        composeRule.runOnIdle {
            assertEquals(7, maxDownload)
            assertEquals(PlayQuality.SUPER, quality)
        }
    }

    @Test
    fun `runtime backed switch rows invoke callbacks`() {
        var useCellularDownload: Boolean? = null
        var lyricAutoSearch: Boolean? = null
        setContent(
            onUseCellularDownloadChange = { useCellularDownload = it },
            onLyricAutoSearchEnabledChange = { lyricAutoSearch = it },
        )

        scrollToTag(FidelityAnchors.Settings.BasicUseCellularDownload)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicUseCellularDownload).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicLyricAutoSearch)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicLyricAutoSearch).performClick()

        composeRule.runOnIdle {
            assertEquals(true, useCellularDownload)
            assertEquals(false, lyricAutoSearch)
        }
    }

    @Test
    fun `disabled rows show pending marker`() {
        setContent()

        scrollToTag(FidelityAnchors.Settings.BasicSectionPlugin)
        composeRule.onNodeWithText("软件启动时自动更新插件").assertIsDisplayed()
        composeRule.onNode(
            hasText("待接入") and hasAnySibling(hasText("软件启动时自动更新插件")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    private fun setContent(
        onMaxDownloadChange: (Int) -> Unit = {},
        onDefaultDownloadQualityChange: (PlayQuality) -> Unit = {},
        onUseCellularDownloadChange: (Boolean) -> Unit = {},
        onLyricAutoSearchEnabledChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                BasicSettingsContent(
                    state = BasicSettingsUiState(
                        maxDownload = 3,
                        defaultDownloadQuality = PlayQuality.STANDARD,
                        useCellularDownload = false,
                        lyricAutoSearchEnabled = true,
                    ),
                    onMaxDownloadChange = onMaxDownloadChange,
                    onDefaultDownloadQualityChange = onDefaultDownloadQualityChange,
                    onUseCellularDownloadChange = onUseCellularDownloadChange,
                    onLyricAutoSearchEnabledChange = onLyricAutoSearchEnabledChange,
                    onNavigateToFileSelector = {},
                )
            }
        }
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }
}
