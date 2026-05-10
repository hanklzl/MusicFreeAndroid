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
import com.zili.android.musicfreeandroid.core.model.AlbumMusicClickAction
import com.zili.android.musicfreeandroid.core.model.MusicDetailDefaultPage
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import com.zili.android.musicfreeandroid.core.model.SearchResultClickAction
import com.zili.android.musicfreeandroid.core.model.SortMode
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
        var maxSearchHistoryLength = 0
        var musicDetailDefaultPage = MusicDetailDefaultPage.Album
        var clickMusicInSearch = SearchResultClickAction.PlayMusic
        var clickMusicInAlbum = AlbumMusicClickAction.PlayAlbum
        var musicOrderInLocalSheet = SortMode.Manual
        var defaultPlayQuality = PlayQuality.LOW
        var playQualityOrder = QualityFallbackOrder.Asc
        var maxDownload = 0
        var defaultDownloadQuality = PlayQuality.LOW
        var downloadQualityOrder = QualityFallbackOrder.Asc
        setContent(
            onMaxSearchHistoryLengthChange = { maxSearchHistoryLength = it },
            onMusicDetailDefaultPageChange = { musicDetailDefaultPage = it },
            onClickMusicInSearchChange = { clickMusicInSearch = it },
            onClickMusicInAlbumChange = { clickMusicInAlbum = it },
            onMusicOrderInLocalSheetChange = { musicOrderInLocalSheet = it },
            onDefaultPlayQualityChange = { defaultPlayQuality = it },
            onPlayQualityOrderChange = { playQualityOrder = it },
            onMaxDownloadChange = { maxDownload = it },
            onDefaultDownloadQualityChange = { defaultDownloadQuality = it },
            onDownloadQualityOrderChange = { downloadQualityOrder = it },
        )

        scrollToTag(FidelityAnchors.Settings.BasicMaxSearchHistoryLength)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMaxSearchHistoryLength).performClick()
        composeRule.onNodeWithTag("settings.choice.100").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicMusicDetailDefaultPage)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMusicDetailDefaultPage).performClick()
        composeRule.onNodeWithTag("settings.choice.默认展示歌词页").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicClickMusicInSearch)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicClickMusicInSearch).performClick()
        composeRule.onNodeWithTag("settings.choice.播放歌曲并替换播放列表").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicClickMusicInAlbum)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicClickMusicInAlbum).performClick()
        composeRule.onNodeWithTag("settings.choice.播放歌曲").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicMusicOrderInLocalSheet)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMusicOrderInLocalSheet).performClick()
        composeRule.onNodeWithTag("settings.choice.按标题").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicDefaultPlayQuality)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicDefaultPlayQuality).performClick()
        composeRule.onNodeWithTag("settings.choice.高音质").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicPlayQualityOrder)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicPlayQualityOrder).performClick()
        composeRule.onNodeWithTag("settings.choice.播放更低音质").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicMaxDownload)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMaxDownload).performClick()
        composeRule.onNodeWithText("7").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicDefaultDownloadQuality)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicDefaultDownloadQuality).performClick()
        composeRule.onNodeWithText("超高音质").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicDownloadQualityOrder)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicDownloadQualityOrder).performClick()
        composeRule.onNodeWithTag("settings.choice.下载更低音质").performClick()

        composeRule.runOnIdle {
            assertEquals(100, maxSearchHistoryLength)
            assertEquals(MusicDetailDefaultPage.Lyric, musicDetailDefaultPage)
            assertEquals(SearchResultClickAction.PlayMusicAndReplace, clickMusicInSearch)
            assertEquals(AlbumMusicClickAction.PlayMusic, clickMusicInAlbum)
            assertEquals(SortMode.Title, musicOrderInLocalSheet)
            assertEquals(PlayQuality.HIGH, defaultPlayQuality)
            assertEquals(QualityFallbackOrder.Desc, playQualityOrder)
            assertEquals(7, maxDownload)
            assertEquals(PlayQuality.SUPER, defaultDownloadQuality)
            assertEquals(QualityFallbackOrder.Desc, downloadQualityOrder)
        }
    }

    @Test
    fun `runtime backed switch rows invoke callbacks`() {
        var musicDetailAwake: Boolean? = null
        var useCellularPlay: Boolean? = null
        var useCellularDownload: Boolean? = null
        var lyricAutoSearch: Boolean? = null
        setContent(
            onMusicDetailAwakeChange = { musicDetailAwake = it },
            onUseCellularPlayChange = { useCellularPlay = it },
            onUseCellularDownloadChange = { useCellularDownload = it },
            onLyricAutoSearchEnabledChange = { lyricAutoSearch = it },
        )

        scrollToTag(FidelityAnchors.Settings.BasicMusicDetailAwake)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMusicDetailAwake).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicUseCellularPlay)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicUseCellularPlay).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicUseCellularDownload)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicUseCellularDownload).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicLyricAutoSearch)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicLyricAutoSearch).performClick()

        composeRule.runOnIdle {
            assertEquals(true, musicDetailAwake)
            assertEquals(true, useCellularPlay)
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
        onMaxSearchHistoryLengthChange: (Int) -> Unit = {},
        onMusicDetailDefaultPageChange: (MusicDetailDefaultPage) -> Unit = {},
        onMusicDetailAwakeChange: (Boolean) -> Unit = {},
        onClickMusicInSearchChange: (SearchResultClickAction) -> Unit = {},
        onClickMusicInAlbumChange: (AlbumMusicClickAction) -> Unit = {},
        onMusicOrderInLocalSheetChange: (SortMode) -> Unit = {},
        onDefaultPlayQualityChange: (PlayQuality) -> Unit = {},
        onPlayQualityOrderChange: (QualityFallbackOrder) -> Unit = {},
        onMaxDownloadChange: (Int) -> Unit = {},
        onDefaultDownloadQualityChange: (PlayQuality) -> Unit = {},
        onDownloadQualityOrderChange: (QualityFallbackOrder) -> Unit = {},
        onUseCellularPlayChange: (Boolean) -> Unit = {},
        onUseCellularDownloadChange: (Boolean) -> Unit = {},
        onLyricAutoSearchEnabledChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                BasicSettingsContent(
                    state = BasicSettingsUiState(
                        maxSearchHistoryLength = 50,
                        musicDetailDefaultPage = MusicDetailDefaultPage.Album,
                        musicDetailAwake = false,
                        clickMusicInSearch = SearchResultClickAction.PlayMusic,
                        clickMusicInAlbum = AlbumMusicClickAction.PlayAlbum,
                        musicOrderInLocalSheet = SortMode.Manual,
                        defaultPlayQuality = PlayQuality.STANDARD,
                        playQualityOrder = QualityFallbackOrder.Asc,
                        maxDownload = 3,
                        defaultDownloadQuality = PlayQuality.STANDARD,
                        downloadQualityOrder = QualityFallbackOrder.Asc,
                        useCellularPlay = false,
                        useCellularDownload = false,
                        lyricAutoSearchEnabled = true,
                    ),
                    onMaxSearchHistoryLengthChange = onMaxSearchHistoryLengthChange,
                    onMusicDetailDefaultPageChange = onMusicDetailDefaultPageChange,
                    onMusicDetailAwakeChange = onMusicDetailAwakeChange,
                    onClickMusicInSearchChange = onClickMusicInSearchChange,
                    onClickMusicInAlbumChange = onClickMusicInAlbumChange,
                    onMusicOrderInLocalSheetChange = onMusicOrderInLocalSheetChange,
                    onDefaultPlayQualityChange = onDefaultPlayQualityChange,
                    onPlayQualityOrderChange = onPlayQualityOrderChange,
                    onMaxDownloadChange = onMaxDownloadChange,
                    onDefaultDownloadQualityChange = onDefaultDownloadQualityChange,
                    onDownloadQualityOrderChange = onDownloadQualityOrderChange,
                    onUseCellularPlayChange = onUseCellularPlayChange,
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
