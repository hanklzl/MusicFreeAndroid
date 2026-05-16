package com.hank.musicfree.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.DesktopLyricAlignment
import com.hank.musicfree.core.model.LyricAssociationType
import com.hank.musicfree.core.model.MusicDetailDefaultPage
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchors
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
        var lyricAssociationType = LyricAssociationType.Search
        var clickMusicInSearch = SearchResultClickAction.PlayMusic
        var clickMusicInAlbum = AlbumMusicClickAction.PlayAlbum
        var musicOrderInLocalSheet = SortMode.Manual
        var defaultPlayQuality = PlayQuality.LOW
        var playQualityOrder = QualityFallbackOrder.Asc
        var audioInterruptionAction = AudioInterruptionAction.Pause
        var audioInterruptionDuckVolume = 0f
        var maxMusicCacheSizeMb = 0
        var maxDownload = 0
        var defaultDownloadQuality = PlayQuality.LOW
        var downloadQualityOrder = QualityFallbackOrder.Asc
        var desktopLyricAlignment = DesktopLyricAlignment.Center
        var desktopLyricTopPercent = 0f
        var desktopLyricLeftPercent = 0f
        var desktopLyricWidthPercent = 0f
        var desktopLyricFontSizeSp = 0
        var desktopLyricTextColor = ""
        var desktopLyricBackgroundColor = ""
        setContent(
            state = BasicSettingsUiState(audioInterruptionAction = AudioInterruptionAction.LowerVolume),
            onMaxSearchHistoryLengthChange = { maxSearchHistoryLength = it },
            onMusicDetailDefaultPageChange = { musicDetailDefaultPage = it },
            onLyricAssociationTypeChange = { lyricAssociationType = it },
            onClickMusicInSearchChange = { clickMusicInSearch = it },
            onClickMusicInAlbumChange = { clickMusicInAlbum = it },
            onMusicOrderInLocalSheetChange = { musicOrderInLocalSheet = it },
            onDefaultPlayQualityChange = { defaultPlayQuality = it },
            onPlayQualityOrderChange = { playQualityOrder = it },
            onAudioInterruptionActionChange = { audioInterruptionAction = it },
            onAudioInterruptionDuckVolumeChange = { audioInterruptionDuckVolume = it },
            onMaxMusicCacheSizeMbChange = { maxMusicCacheSizeMb = it },
            onMaxDownloadChange = { maxDownload = it },
            onDefaultDownloadQualityChange = { defaultDownloadQuality = it },
            onDownloadQualityOrderChange = { downloadQualityOrder = it },
            onDesktopLyricAlignmentChange = { desktopLyricAlignment = it },
            onDesktopLyricTopPercentChange = { desktopLyricTopPercent = it },
            onDesktopLyricLeftPercentChange = { desktopLyricLeftPercent = it },
            onDesktopLyricWidthPercentChange = { desktopLyricWidthPercent = it },
            onDesktopLyricFontSizeSpChange = { desktopLyricFontSizeSp = it },
            onDesktopLyricTextColorChange = { desktopLyricTextColor = it },
            onDesktopLyricBackgroundColorChange = { desktopLyricBackgroundColor = it },
        )

        scrollToTag(FidelityAnchors.Settings.BasicMaxSearchHistoryLength)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMaxSearchHistoryLength).performClick()
        composeRule.onNodeWithTag("settings.choice.100").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicMusicDetailDefaultPage)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMusicDetailDefaultPage).performClick()
        composeRule.onNodeWithTag("settings.choice.默认展示歌词页").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicSectionCommon)
        composeRule.onNodeWithText("关联歌词方式").performClick()
        composeRule.onNodeWithTag("settings.choice.手动输入歌曲信息").performClick()

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

        scrollToTag(FidelityAnchors.Settings.BasicSectionPlayback)
        composeRule.onNodeWithText("播放被暂时打断时").performClick()
        composeRule.onNodeWithTag("settings.choice.降低音量").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicSectionPlayback)
        composeRule.onNodeWithText("被打断时音量").performClick()
        composeRule.onNodeWithTag("settings.choice.80%").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicMaxDownload)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMaxDownload).performClick()
        composeRule.onNodeWithText("7").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicDefaultDownloadQuality)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicDefaultDownloadQuality).performClick()
        composeRule.onNodeWithText("超高音质").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicDownloadQualityOrder)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicDownloadQualityOrder).performClick()
        composeRule.onNodeWithTag("settings.choice.下载更低音质").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicSectionCache)
        composeRule.onNodeWithText("音乐缓存上限").performClick()
        composeRule.onNodeWithTag("settings.choice.1024 MB").performClick()

        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("桌面歌词对齐方式").performClick()
        composeRule.onNodeWithTag("settings.choice.右对齐").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("桌面歌词顶部位置").performClick()
        composeRule.onNodeWithTag("settings.choice.16%").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("桌面歌词左侧位置").performClick()
        composeRule.onNodeWithTag("settings.choice.24%").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("桌面歌词宽度").performClick()
        composeRule.onNodeWithTag("settings.choice.66%").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("桌面歌词字号").performClick()
        composeRule.onNodeWithTag("settings.choice.24sp").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("桌面歌词文字颜色").performClick()
        composeRule.onNodeWithTag("settings.choice.黄色").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("桌面歌词背景颜色").performClick()
        composeRule.onNodeWithTag("settings.choice.透明").performClick()

        composeRule.runOnIdle {
            assertEquals(100, maxSearchHistoryLength)
            assertEquals(MusicDetailDefaultPage.Lyric, musicDetailDefaultPage)
            assertEquals(LyricAssociationType.Input, lyricAssociationType)
            assertEquals(SearchResultClickAction.PlayMusicAndReplace, clickMusicInSearch)
            assertEquals(AlbumMusicClickAction.PlayMusic, clickMusicInAlbum)
            assertEquals(SortMode.Title, musicOrderInLocalSheet)
            assertEquals(PlayQuality.HIGH, defaultPlayQuality)
            assertEquals(QualityFallbackOrder.Desc, playQualityOrder)
            assertEquals(AudioInterruptionAction.LowerVolume, audioInterruptionAction)
            assertEquals(0.8f, audioInterruptionDuckVolume)
            assertEquals(1024, maxMusicCacheSizeMb)
            assertEquals(7, maxDownload)
            assertEquals(PlayQuality.SUPER, defaultDownloadQuality)
            assertEquals(QualityFallbackOrder.Desc, downloadQualityOrder)
            assertEquals(DesktopLyricAlignment.Right, desktopLyricAlignment)
            assertEquals(0.16f, desktopLyricTopPercent)
            assertEquals(0.24f, desktopLyricLeftPercent)
            assertEquals(0.66f, desktopLyricWidthPercent)
            assertEquals(24, desktopLyricFontSizeSp)
            assertEquals("#FFFFD54F", desktopLyricTextColor)
            assertEquals("#00000000", desktopLyricBackgroundColor)
        }
    }

    @Test
    fun `runtime backed switch rows invoke callbacks`() {
        var musicDetailAwake: Boolean? = null
        var autoUpdatePlugins: Boolean? = null
        var skipPluginVersionCheck: Boolean? = null
        var lazyLoadPlugins: Boolean? = null
        var allowConcurrentPlayback: Boolean? = null
        var autoPlayWhenAppStart: Boolean? = null
        var tryChangeSourceWhenPlayFail: Boolean? = null
        var autoStopWhenError: Boolean? = null
        var useCellularPlay: Boolean? = null
        var useCellularDownload: Boolean? = null
        var lyricAutoSearch: Boolean? = null
        var showExitOnNotification: Boolean? = null
        var desktopLyricEnabled: Boolean? = null
        var debugErrorLogEnabled: Boolean? = null
        var debugTraceLogEnabled: Boolean? = null
        var debugDevLogEnabled: Boolean? = null
        setContent(
            onMusicDetailAwakeChange = { musicDetailAwake = it },
            onShowExitOnNotificationChange = { showExitOnNotification = it },
            onAutoUpdatePluginsChange = { autoUpdatePlugins = it },
            onSkipPluginVersionCheckChange = { skipPluginVersionCheck = it },
            onLazyLoadPluginsChange = { lazyLoadPlugins = it },
            onAllowConcurrentPlaybackChange = { allowConcurrentPlayback = it },
            onAutoPlayWhenAppStartChange = { autoPlayWhenAppStart = it },
            onTryChangeSourceWhenPlayFailChange = { tryChangeSourceWhenPlayFail = it },
            onAutoStopWhenErrorChange = { autoStopWhenError = it },
            onUseCellularPlayChange = { useCellularPlay = it },
            onUseCellularDownloadChange = { useCellularDownload = it },
            onLyricAutoSearchEnabledChange = { lyricAutoSearch = it },
            onDesktopLyricEnabledChange = { desktopLyricEnabled = it },
            onDebugErrorLogEnabledChange = { debugErrorLogEnabled = it },
            onDebugTraceLogEnabledChange = { debugTraceLogEnabled = it },
            onDebugDevLogEnabledChange = { debugDevLogEnabled = it },
        )

        scrollToTag(FidelityAnchors.Settings.BasicMusicDetailAwake)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicMusicDetailAwake).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionCommon)
        composeRule.onNodeWithText("通知栏显示关闭按钮 (重启后生效)").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionPlugin)
        composeRule.onNodeWithText("软件启动时自动更新插件").performClick()
        composeRule.onNodeWithText("安装插件时不校验版本").performClick()
        composeRule.onNodeWithText("启用插件懒加载").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionPlayback)
        composeRule.onNodeWithText("允许与其他应用同时播放").performClick()
        composeRule.onNodeWithText("软件启动时自动播放歌曲").performClick()
        composeRule.onNodeWithText("播放失败时尝试更换音源").performClick()
        composeRule.onNodeWithText("播放失败时自动暂停").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicUseCellularPlay)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicUseCellularPlay).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicUseCellularDownload)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicUseCellularDownload).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicLyricAutoSearch)
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicLyricAutoSearch).performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionLyric)
        composeRule.onNodeWithText("开启桌面歌词").performClick()
        scrollToTag(FidelityAnchors.Settings.BasicSectionDeveloper)
        composeRule.onNodeWithText("记录错误日志").performClick()
        composeRule.onNodeWithText("记录详细日志").performClick()
        composeRule.onNodeWithText("调试面板").performClick()

        composeRule.runOnIdle {
            assertEquals(true, musicDetailAwake)
            assertEquals(true, showExitOnNotification)
            assertEquals(true, autoUpdatePlugins)
            assertEquals(true, skipPluginVersionCheck)
            assertEquals(true, lazyLoadPlugins)
            assertEquals(true, allowConcurrentPlayback)
            assertEquals(true, autoPlayWhenAppStart)
            assertEquals(true, tryChangeSourceWhenPlayFail)
            assertEquals(true, autoStopWhenError)
            assertEquals(true, useCellularPlay)
            assertEquals(true, useCellularDownload)
            assertEquals(false, lyricAutoSearch)
            assertEquals(true, desktopLyricEnabled)
            assertEquals(false, debugErrorLogEnabled)
            assertEquals(false, debugTraceLogEnabled)
            assertEquals(true, debugDevLogEnabled)
        }
    }

    @Test
    fun `basic settings has no pending marker`() {
        setContent()

        listOf(
            FidelityAnchors.Settings.BasicSectionCommon,
            FidelityAnchors.Settings.BasicSectionLyric,
            FidelityAnchors.Settings.BasicSectionDeveloper,
        ).forEach { tag ->
            scrollToTag(tag)
            composeRule.onAllNodesWithText("待接入", useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun setContent(
        state: BasicSettingsUiState = BasicSettingsUiState(
            maxSearchHistoryLength = 50,
            musicDetailDefaultPage = MusicDetailDefaultPage.Album,
            musicDetailAwake = false,
            lyricAssociationType = LyricAssociationType.Search,
            showExitOnNotification = false,
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
            desktopLyricEnabled = false,
            desktopLyricAlignment = DesktopLyricAlignment.Center,
            desktopLyricTopPercent = 0.08f,
            desktopLyricLeftPercent = 0.08f,
            desktopLyricWidthPercent = 0.84f,
            desktopLyricFontSizeSp = 18,
            desktopLyricTextColor = "#FFFFFFFF",
            desktopLyricBackgroundColor = "#66000000",
            debugErrorLogEnabled = true,
            debugTraceLogEnabled = true,
            debugDevLogEnabled = false,
        ),
        onMaxSearchHistoryLengthChange: (Int) -> Unit = {},
        onMusicDetailDefaultPageChange: (MusicDetailDefaultPage) -> Unit = {},
        onMusicDetailAwakeChange: (Boolean) -> Unit = {},
        onLyricAssociationTypeChange: (LyricAssociationType) -> Unit = {},
        onShowExitOnNotificationChange: (Boolean) -> Unit = {},
        onClickMusicInSearchChange: (SearchResultClickAction) -> Unit = {},
        onClickMusicInAlbumChange: (AlbumMusicClickAction) -> Unit = {},
        onMusicOrderInLocalSheetChange: (SortMode) -> Unit = {},
        onDefaultPlayQualityChange: (PlayQuality) -> Unit = {},
        onPlayQualityOrderChange: (QualityFallbackOrder) -> Unit = {},
        onAllowConcurrentPlaybackChange: (Boolean) -> Unit = {},
        onAutoPlayWhenAppStartChange: (Boolean) -> Unit = {},
        onTryChangeSourceWhenPlayFailChange: (Boolean) -> Unit = {},
        onAutoStopWhenErrorChange: (Boolean) -> Unit = {},
        onAudioInterruptionActionChange: (AudioInterruptionAction) -> Unit = {},
        onAudioInterruptionDuckVolumeChange: (Float) -> Unit = {},
        onMaxDownloadChange: (Int) -> Unit = {},
        onDefaultDownloadQualityChange: (PlayQuality) -> Unit = {},
        onDownloadQualityOrderChange: (QualityFallbackOrder) -> Unit = {},
        onUseCellularPlayChange: (Boolean) -> Unit = {},
        onUseCellularDownloadChange: (Boolean) -> Unit = {},
        onLyricAutoSearchEnabledChange: (Boolean) -> Unit = {},
        onDesktopLyricEnabledChange: (Boolean) -> Unit = {},
        onDesktopLyricAlignmentChange: (DesktopLyricAlignment) -> Unit = {},
        onDesktopLyricTopPercentChange: (Float) -> Unit = {},
        onDesktopLyricLeftPercentChange: (Float) -> Unit = {},
        onDesktopLyricWidthPercentChange: (Float) -> Unit = {},
        onDesktopLyricFontSizeSpChange: (Int) -> Unit = {},
        onDesktopLyricTextColorChange: (String) -> Unit = {},
        onDesktopLyricBackgroundColorChange: (String) -> Unit = {},
        onAutoUpdatePluginsChange: (Boolean) -> Unit = {},
        onSkipPluginVersionCheckChange: (Boolean) -> Unit = {},
        onLazyLoadPluginsChange: (Boolean) -> Unit = {},
        onMaxMusicCacheSizeMbChange: (Int) -> Unit = {},
        onClearMusicCache: () -> Unit = {},
        onClearLyricCache: () -> Unit = {},
        onClearImageCache: () -> Unit = {},
        onDebugErrorLogEnabledChange: (Boolean) -> Unit = {},
        onDebugTraceLogEnabledChange: (Boolean) -> Unit = {},
        onDebugDevLogEnabledChange: (Boolean) -> Unit = {},
        onViewErrorLog: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                BasicSettingsContent(
                    state = state,
                    onMaxSearchHistoryLengthChange = onMaxSearchHistoryLengthChange,
                    onMusicDetailDefaultPageChange = onMusicDetailDefaultPageChange,
                    onMusicDetailAwakeChange = onMusicDetailAwakeChange,
                    onLyricAssociationTypeChange = onLyricAssociationTypeChange,
                    onShowExitOnNotificationChange = onShowExitOnNotificationChange,
                    onClickMusicInSearchChange = onClickMusicInSearchChange,
                    onClickMusicInAlbumChange = onClickMusicInAlbumChange,
                    onMusicOrderInLocalSheetChange = onMusicOrderInLocalSheetChange,
                    onDefaultPlayQualityChange = onDefaultPlayQualityChange,
                    onPlayQualityOrderChange = onPlayQualityOrderChange,
                    onAllowConcurrentPlaybackChange = onAllowConcurrentPlaybackChange,
                    onAutoPlayWhenAppStartChange = onAutoPlayWhenAppStartChange,
                    onTryChangeSourceWhenPlayFailChange = onTryChangeSourceWhenPlayFailChange,
                    onAutoStopWhenErrorChange = onAutoStopWhenErrorChange,
                    onAudioInterruptionActionChange = onAudioInterruptionActionChange,
                    onAudioInterruptionDuckVolumeChange = onAudioInterruptionDuckVolumeChange,
                    onMaxDownloadChange = onMaxDownloadChange,
                    onDefaultDownloadQualityChange = onDefaultDownloadQualityChange,
                    onDownloadQualityOrderChange = onDownloadQualityOrderChange,
                    onUseCellularPlayChange = onUseCellularPlayChange,
                    onUseCellularDownloadChange = onUseCellularDownloadChange,
                    onLyricAutoSearchEnabledChange = onLyricAutoSearchEnabledChange,
                    onDesktopLyricEnabledChange = onDesktopLyricEnabledChange,
                    onDesktopLyricAlignmentChange = onDesktopLyricAlignmentChange,
                    onDesktopLyricTopPercentChange = onDesktopLyricTopPercentChange,
                    onDesktopLyricLeftPercentChange = onDesktopLyricLeftPercentChange,
                    onDesktopLyricWidthPercentChange = onDesktopLyricWidthPercentChange,
                    onDesktopLyricFontSizeSpChange = onDesktopLyricFontSizeSpChange,
                    onDesktopLyricTextColorChange = onDesktopLyricTextColorChange,
                    onDesktopLyricBackgroundColorChange = onDesktopLyricBackgroundColorChange,
                    onAutoUpdatePluginsChange = onAutoUpdatePluginsChange,
                    onSkipPluginVersionCheckChange = onSkipPluginVersionCheckChange,
                    onLazyLoadPluginsChange = onLazyLoadPluginsChange,
                    onMaxMusicCacheSizeMbChange = onMaxMusicCacheSizeMbChange,
                    onClearMusicCache = onClearMusicCache,
                    onClearLyricCache = onClearLyricCache,
                    onClearImageCache = onClearImageCache,
                    onNavigateToFileSelector = {},
                    onDebugErrorLogEnabledChange = onDebugErrorLogEnabledChange,
                    onDebugTraceLogEnabledChange = onDebugTraceLogEnabledChange,
                    onDebugDevLogEnabledChange = onDebugDevLogEnabledChange,
                    onViewErrorLog = onViewErrorLog,
                )
            }
        }
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }
}
