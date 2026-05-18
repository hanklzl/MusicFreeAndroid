package com.hank.musicfree.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.DesktopLyricAlignment
import com.hank.musicfree.core.model.LyricAssociationType
import com.hank.musicfree.core.model.MusicDetailDefaultPage
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.settings.components.SettingActionRow
import com.hank.musicfree.feature.settings.components.SettingSectionCard
import com.hank.musicfree.feature.settings.components.SettingSwitchRow
import com.hank.musicfree.feature.settings.components.SettingValueRow

private data class Choice<T>(
    val value: T,
    val label: String,
)

private enum class BasicSettingsDialog {
    MaxSearchHistoryLength,
    MusicDetailDefaultPage,
    LyricAssociationType,
    ClickMusicInSearch,
    ClickMusicInAlbum,
    MusicOrderInLocalSheet,
    DefaultPlayQuality,
    PlayQualityOrder,
    AudioInterruptionAction,
    AudioInterruptionDuckVolume,
    MaxMusicCacheSize,
    MaxDownload,
    DefaultDownloadQuality,
    DownloadQualityOrder,
    DesktopLyricAlignment,
    DesktopLyricTopPercent,
    DesktopLyricLeftPercent,
    DesktopLyricWidthPercent,
    DesktopLyricFontSizeSp,
    DesktopLyricTextColor,
    DesktopLyricBackgroundColor,
}

@Composable
fun BasicSettingsContent(
    state: BasicSettingsUiState,
    onMaxSearchHistoryLengthChange: (Int) -> Unit,
    onMusicDetailDefaultPageChange: (MusicDetailDefaultPage) -> Unit,
    onMusicDetailAwakeChange: (Boolean) -> Unit,
    onLyricAssociationTypeChange: (LyricAssociationType) -> Unit,
    onShowExitOnNotificationChange: (Boolean) -> Unit,
    onClickMusicInSearchChange: (SearchResultClickAction) -> Unit,
    onClickMusicInAlbumChange: (AlbumMusicClickAction) -> Unit,
    onMusicOrderInLocalSheetChange: (SortMode) -> Unit,
    onDefaultPlayQualityChange: (PlayQuality) -> Unit,
    onPlayQualityOrderChange: (QualityFallbackOrder) -> Unit,
    onAllowConcurrentPlaybackChange: (Boolean) -> Unit,
    onAutoPlayWhenAppStartChange: (Boolean) -> Unit,
    onTryChangeSourceWhenPlayFailChange: (Boolean) -> Unit,
    onAutoStopWhenErrorChange: (Boolean) -> Unit,
    onAudioInterruptionActionChange: (AudioInterruptionAction) -> Unit,
    onAudioInterruptionDuckVolumeChange: (Float) -> Unit,
    onMaxDownloadChange: (Int) -> Unit,
    onDefaultDownloadQualityChange: (PlayQuality) -> Unit,
    onDownloadQualityOrderChange: (QualityFallbackOrder) -> Unit,
    onUseCellularPlayChange: (Boolean) -> Unit,
    onUseCellularDownloadChange: (Boolean) -> Unit,
    onLyricAutoSearchEnabledChange: (Boolean) -> Unit,
    onDesktopLyricEnabledChange: (Boolean) -> Unit,
    onDesktopLyricAlignmentChange: (DesktopLyricAlignment) -> Unit,
    onDesktopLyricTopPercentChange: (Float) -> Unit,
    onDesktopLyricLeftPercentChange: (Float) -> Unit,
    onDesktopLyricWidthPercentChange: (Float) -> Unit,
    onDesktopLyricFontSizeSpChange: (Int) -> Unit,
    onDesktopLyricTextColorChange: (String) -> Unit,
    onDesktopLyricBackgroundColorChange: (String) -> Unit,
    onAutoUpdatePluginsChange: (Boolean) -> Unit,
    onSkipPluginVersionCheckChange: (Boolean) -> Unit,
    onLazyLoadPluginsChange: (Boolean) -> Unit,
    onMaxMusicCacheSizeMbChange: (Int) -> Unit,
    onClearMusicCache: () -> Unit,
    onClearLyricCache: () -> Unit,
    onClearImageCache: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onDebugErrorLogEnabledChange: (Boolean) -> Unit,
    onDebugTraceLogEnabledChange: (Boolean) -> Unit,
    onDebugDevLogEnabledChange: (Boolean) -> Unit,
    onViewErrorLog: () -> Unit,
    modifier: Modifier = Modifier,
    feedbackExportState: FeedbackExportUiState = FeedbackExportUiState(),
    onCreateFeedbackPackage: () -> Unit = {},
    onClearLogs: () -> Unit = {},
) {
    var activeDialog by remember { mutableStateOf<BasicSettingsDialog?>(null) }
    val isFeedbackActionInProgress = feedbackExportState.isOperationInProgress

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Settings.BasicRoot)
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        item { TopBottomSpacer() }
        item {
            SettingSectionCard("常规", testTag = FidelityAnchors.Settings.BasicSectionCommon) {
                SettingValueRow(
                    title = "历史记录最多保存条数",
                    value = state.maxSearchHistoryLength.toString(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicMaxSearchHistoryLength,
                    onClick = { activeDialog = BasicSettingsDialog.MaxSearchHistoryLength },
                )
                SettingValueRow(
                    title = "打开歌曲详情页时",
                    value = state.musicDetailDefaultPage.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicMusicDetailDefaultPage,
                    onClick = { activeDialog = BasicSettingsDialog.MusicDetailDefaultPage },
                )
                SettingSwitchRow(
                    title = "处于歌曲详情页时常亮",
                    checked = state.musicDetailAwake,
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicMusicDetailAwake,
                    onCheckedChange = onMusicDetailAwakeChange,
                )
                SettingValueRow(
                    title = "关联歌词方式",
                    value = state.lyricAssociationType.label(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.LyricAssociationType },
                )
                SettingSwitchRow(
                    title = "通知栏显示关闭按钮 (重启后生效)",
                    checked = state.showExitOnNotification,
                    enabled = true,
                    onCheckedChange = onShowExitOnNotificationChange,
                )
            }
        }
        item {
            SettingSectionCard("歌单&专辑", testTag = FidelityAnchors.Settings.BasicSectionSheetAlbum) {
                SettingValueRow(
                    title = "点击搜索结果内单曲时",
                    value = state.clickMusicInSearch.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicClickMusicInSearch,
                    onClick = { activeDialog = BasicSettingsDialog.ClickMusicInSearch },
                )
                SettingValueRow(
                    title = "点击专辑内单曲时",
                    value = state.clickMusicInAlbum.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicClickMusicInAlbum,
                    onClick = { activeDialog = BasicSettingsDialog.ClickMusicInAlbum },
                )
                SettingValueRow(
                    title = "新建歌单时默认歌曲排序",
                    value = state.musicOrderInLocalSheet.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicMusicOrderInLocalSheet,
                    onClick = { activeDialog = BasicSettingsDialog.MusicOrderInLocalSheet },
                )
            }
        }
        item {
            SettingSectionCard("插件", testTag = FidelityAnchors.Settings.BasicSectionPlugin) {
                SettingSwitchRow(
                    title = "软件启动时自动更新插件",
                    checked = state.autoUpdatePlugins,
                    enabled = true,
                    onCheckedChange = onAutoUpdatePluginsChange,
                )
                SettingSwitchRow(
                    title = "安装插件时不校验版本",
                    checked = state.skipPluginVersionCheck,
                    enabled = true,
                    onCheckedChange = onSkipPluginVersionCheckChange,
                )
                SettingSwitchRow(
                    title = "启用插件懒加载",
                    checked = state.lazyLoadPlugins,
                    enabled = true,
                    onCheckedChange = onLazyLoadPluginsChange,
                )
            }
        }
        item {
            SettingSectionCard("播放", testTag = FidelityAnchors.Settings.BasicSectionPlayback) {
                SettingSwitchRow(
                    title = "允许与其他应用同时播放",
                    checked = state.allowConcurrentPlayback,
                    enabled = true,
                    onCheckedChange = onAllowConcurrentPlaybackChange,
                )
                SettingSwitchRow(
                    title = "软件启动时自动播放歌曲",
                    checked = state.autoPlayWhenAppStart,
                    enabled = true,
                    onCheckedChange = onAutoPlayWhenAppStartChange,
                )
                SettingSwitchRow(
                    title = "播放失败时尝试更换音源",
                    checked = state.tryChangeSourceWhenPlayFail,
                    enabled = true,
                    onCheckedChange = onTryChangeSourceWhenPlayFailChange,
                )
                SettingSwitchRow(
                    title = "播放失败时自动暂停",
                    checked = state.autoStopWhenError,
                    enabled = true,
                    onCheckedChange = onAutoStopWhenErrorChange,
                )
                SettingValueRow(
                    title = "播放被暂时打断时",
                    value = state.audioInterruptionAction.label(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.AudioInterruptionAction },
                )
                if (state.audioInterruptionAction == AudioInterruptionAction.LowerVolume) {
                    SettingValueRow(
                        title = "被打断时音量",
                        value = state.audioInterruptionDuckVolume.volumeLabel(),
                        enabled = true,
                        onClick = { activeDialog = BasicSettingsDialog.AudioInterruptionDuckVolume },
                    )
                }
                SettingValueRow(
                    title = "默认播放音质",
                    value = state.defaultPlayQuality.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicDefaultPlayQuality,
                    onClick = { activeDialog = BasicSettingsDialog.DefaultPlayQuality },
                )
                SettingValueRow(
                    title = "默认播放音质缺失时",
                    value = state.playQualityOrder.playbackLabel(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicPlayQualityOrder,
                    onClick = { activeDialog = BasicSettingsDialog.PlayQualityOrder },
                )
            }
        }
        item {
            SettingSectionCard("下载", testTag = FidelityAnchors.Settings.BasicSectionDownload) {
                SettingValueRow(
                    title = "下载路径",
                    value = storageDirectoryLabel(state.storageAccessState),
                    enabled = true,
                    onClick = onNavigateToFileSelector,
                )
                SettingValueRow(
                    title = "最大同时下载数目",
                    value = state.maxDownload.toString(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicMaxDownload,
                    onClick = { activeDialog = BasicSettingsDialog.MaxDownload },
                )
                SettingValueRow(
                    title = "默认下载音质",
                    value = state.defaultDownloadQuality.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicDefaultDownloadQuality,
                    onClick = { activeDialog = BasicSettingsDialog.DefaultDownloadQuality },
                )
                SettingValueRow(
                    title = "默认下载音质缺失时",
                    value = state.downloadQualityOrder.downloadLabel(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicDownloadQualityOrder,
                    onClick = { activeDialog = BasicSettingsDialog.DownloadQualityOrder },
                )
            }
        }
        item {
            SettingSectionCard("网络", testTag = FidelityAnchors.Settings.BasicSectionNetwork) {
                SettingSwitchRow(
                    title = "使用移动网络播放",
                    checked = state.useCellularPlay,
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicUseCellularPlay,
                    onCheckedChange = onUseCellularPlayChange,
                )
                SettingSwitchRow(
                    title = "使用移动网络下载",
                    checked = state.useCellularDownload,
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicUseCellularDownload,
                    onCheckedChange = onUseCellularDownloadChange,
                )
            }
        }
        item {
            SettingSectionCard("歌词", testTag = FidelityAnchors.Settings.BasicSectionLyric) {
                SettingSwitchRow(
                    title = "歌词缺失时自动搜索歌词",
                    checked = state.lyricAutoSearchEnabled,
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicLyricAutoSearch,
                    onCheckedChange = onLyricAutoSearchEnabledChange,
                )
                SettingSwitchRow(
                    title = "开启桌面歌词",
                    checked = state.desktopLyricEnabled,
                    enabled = true,
                    onCheckedChange = onDesktopLyricEnabledChange,
                )
                SettingValueRow(
                    title = "桌面歌词对齐方式",
                    value = state.desktopLyricAlignment.label(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.DesktopLyricAlignment },
                )
                SettingValueRow(
                    title = "桌面歌词顶部位置",
                    value = state.desktopLyricTopPercent.percentLabel(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.DesktopLyricTopPercent },
                )
                SettingValueRow(
                    title = "桌面歌词左侧位置",
                    value = state.desktopLyricLeftPercent.percentLabel(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.DesktopLyricLeftPercent },
                )
                SettingValueRow(
                    title = "桌面歌词宽度",
                    value = state.desktopLyricWidthPercent.percentLabel(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.DesktopLyricWidthPercent },
                )
                SettingValueRow(
                    title = "桌面歌词字号",
                    value = "${state.desktopLyricFontSizeSp}sp",
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.DesktopLyricFontSizeSp },
                )
                SettingValueRow(
                    title = "桌面歌词文字颜色",
                    value = state.desktopLyricTextColor.colorLabel(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.DesktopLyricTextColor },
                )
                SettingValueRow(
                    title = "桌面歌词背景颜色",
                    value = state.desktopLyricBackgroundColor.colorLabel(),
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.DesktopLyricBackgroundColor },
                )
            }
        }
        item {
            SettingSectionCard("缓存", testTag = FidelityAnchors.Settings.BasicSectionCache) {
                SettingValueRow(
                    title = "音乐缓存上限",
                    value = "${state.maxMusicCacheSizeMb} MB",
                    enabled = true,
                    onClick = { activeDialog = BasicSettingsDialog.MaxMusicCacheSize },
                )
                SettingActionRow(
                    title = "清除音乐缓存",
                    enabled = !state.cacheActionInProgress,
                    trailingText = state.cacheActionMessage.orEmpty(),
                    onClick = onClearMusicCache,
                )
                SettingActionRow(
                    title = "清除歌词缓存",
                    enabled = !state.cacheActionInProgress,
                    onClick = onClearLyricCache,
                )
                SettingActionRow(
                    title = "清除图片缓存",
                    enabled = !state.cacheActionInProgress,
                    onClick = onClearImageCache,
                )
            }
        }
        item {
            SettingSectionCard("开发选项", testTag = FidelityAnchors.Settings.BasicSectionDeveloper) {
                SettingSwitchRow(
                    title = "记录错误日志",
                    checked = state.debugErrorLogEnabled,
                    enabled = true,
                    onCheckedChange = onDebugErrorLogEnabledChange,
                )
                SettingSwitchRow(
                    title = "记录详细日志",
                    checked = state.debugTraceLogEnabled,
                    enabled = true,
                    onCheckedChange = onDebugTraceLogEnabledChange,
                )
                SettingSwitchRow(
                    title = "调试面板",
                    checked = state.debugDevLogEnabled,
                    enabled = true,
                    onCheckedChange = onDebugDevLogEnabledChange,
                )
                SettingActionRow(
                    title = "查看错误日志",
                    enabled = true,
                    onClick = onViewErrorLog,
                )
                SettingActionRow(
                    title = "生成日志包并分享",
                    enabled = !isFeedbackActionInProgress,
                    trailingText = if (feedbackExportState.isExporting) "生成中" else "",
                    onClick = onCreateFeedbackPackage,
                )
                SettingActionRow(
                    title = "清空日志",
                    enabled = !isFeedbackActionInProgress,
                    trailingText = if (feedbackExportState.isClearing) "清理中" else "",
                    onClick = onClearLogs,
                )
            }
        }
        item { TopBottomSpacer() }
    }

    when (activeDialog) {
        BasicSettingsDialog.MaxSearchHistoryLength -> ChoiceDialog(
            title = "历史记录最多保存条数",
            choices = listOf(20, 50, 100, 200, 500).map { Choice(it, it.toString()) },
            onDismiss = { activeDialog = null },
            onSelected = { value ->
                onMaxSearchHistoryLengthChange(value)
                activeDialog = null
            },
        )

        BasicSettingsDialog.MusicDetailDefaultPage -> ChoiceDialog(
            title = "打开歌曲详情页时",
            choices = listOf(
                Choice(MusicDetailDefaultPage.Album, MusicDetailDefaultPage.Album.label()),
                Choice(MusicDetailDefaultPage.Lyric, MusicDetailDefaultPage.Lyric.label()),
            ),
            onDismiss = { activeDialog = null },
            onSelected = { page ->
                onMusicDetailDefaultPageChange(page)
                activeDialog = null
            },
        )

        BasicSettingsDialog.LyricAssociationType -> ChoiceDialog(
            title = "关联歌词方式",
            choices = listOf(
                Choice(LyricAssociationType.Search, LyricAssociationType.Search.label()),
                Choice(LyricAssociationType.Input, LyricAssociationType.Input.label()),
            ),
            onDismiss = { activeDialog = null },
            onSelected = { type ->
                onLyricAssociationTypeChange(type)
                activeDialog = null
            },
        )

        BasicSettingsDialog.ClickMusicInSearch -> ChoiceDialog(
            title = "点击搜索结果内单曲时",
            choices = listOf(
                Choice(SearchResultClickAction.PlayMusic, SearchResultClickAction.PlayMusic.label()),
                Choice(SearchResultClickAction.PlayMusicAndReplace, SearchResultClickAction.PlayMusicAndReplace.label()),
            ),
            onDismiss = { activeDialog = null },
            onSelected = { action ->
                onClickMusicInSearchChange(action)
                activeDialog = null
            },
        )

        BasicSettingsDialog.ClickMusicInAlbum -> ChoiceDialog(
            title = "点击专辑内单曲时",
            choices = listOf(
                Choice(AlbumMusicClickAction.PlayMusic, AlbumMusicClickAction.PlayMusic.label()),
                Choice(AlbumMusicClickAction.PlayAlbum, AlbumMusicClickAction.PlayAlbum.label()),
            ),
            onDismiss = { activeDialog = null },
            onSelected = { action ->
                onClickMusicInAlbumChange(action)
                activeDialog = null
            },
        )

        BasicSettingsDialog.MusicOrderInLocalSheet -> ChoiceDialog(
            title = "新建歌单时默认歌曲排序",
            choices = SortMode.entries.map { Choice(it, it.label()) },
            onDismiss = { activeDialog = null },
            onSelected = { sortMode ->
                onMusicOrderInLocalSheetChange(sortMode)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DefaultPlayQuality -> ChoiceDialog(
            title = "默认播放音质",
            choices = playQualityChoices(),
            onDismiss = { activeDialog = null },
            onSelected = { quality ->
                onDefaultPlayQualityChange(quality)
                activeDialog = null
            },
        )

        BasicSettingsDialog.PlayQualityOrder -> ChoiceDialog(
            title = "默认播放音质缺失时",
            choices = listOf(
                Choice(QualityFallbackOrder.Asc, QualityFallbackOrder.Asc.playbackLabel()),
                Choice(QualityFallbackOrder.Desc, QualityFallbackOrder.Desc.playbackLabel()),
            ),
            onDismiss = { activeDialog = null },
            onSelected = { order ->
                onPlayQualityOrderChange(order)
                activeDialog = null
            },
        )

        BasicSettingsDialog.AudioInterruptionAction -> ChoiceDialog(
            title = "播放被暂时打断时",
            choices = listOf(
                Choice(AudioInterruptionAction.Pause, AudioInterruptionAction.Pause.label()),
                Choice(AudioInterruptionAction.LowerVolume, AudioInterruptionAction.LowerVolume.label()),
            ),
            onDismiss = { activeDialog = null },
            onSelected = { action ->
                onAudioInterruptionActionChange(action)
                activeDialog = null
            },
        )

        BasicSettingsDialog.AudioInterruptionDuckVolume -> ChoiceDialog(
            title = "被打断时音量",
            choices = listOf(0.3f, 0.5f, 0.8f).map { Choice(it, it.volumeLabel()) },
            onDismiss = { activeDialog = null },
            onSelected = { volume ->
                onAudioInterruptionDuckVolumeChange(volume)
                activeDialog = null
            },
        )

        BasicSettingsDialog.MaxMusicCacheSize -> ChoiceDialog(
            title = "音乐缓存上限",
            choices = listOf(100, 256, 512, 1024, 2048, 4096, 8192).map { Choice(it, "$it MB") },
            onDismiss = { activeDialog = null },
            onSelected = { value ->
                onMaxMusicCacheSizeMbChange(value)
                activeDialog = null
            },
        )

        BasicSettingsDialog.MaxDownload -> ChoiceDialog(
            title = "最大同时下载数目",
            choices = listOf(1, 3, 5, 7).map { Choice(it, it.toString()) },
            onDismiss = { activeDialog = null },
            onSelected = { value ->
                onMaxDownloadChange(value)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DefaultDownloadQuality -> ChoiceDialog(
            title = "默认下载音质",
            choices = playQualityChoices(),
            onDismiss = { activeDialog = null },
            onSelected = { quality ->
                onDefaultDownloadQualityChange(quality)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DownloadQualityOrder -> ChoiceDialog(
            title = "默认下载音质缺失时",
            choices = listOf(
                Choice(QualityFallbackOrder.Asc, QualityFallbackOrder.Asc.downloadLabel()),
                Choice(QualityFallbackOrder.Desc, QualityFallbackOrder.Desc.downloadLabel()),
            ),
            onDismiss = { activeDialog = null },
            onSelected = { order ->
                onDownloadQualityOrderChange(order)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DesktopLyricAlignment -> ChoiceDialog(
            title = "桌面歌词对齐方式",
            choices = DesktopLyricAlignment.entries.map { Choice(it, it.label()) },
            onDismiss = { activeDialog = null },
            onSelected = { alignment ->
                onDesktopLyricAlignmentChange(alignment)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DesktopLyricTopPercent -> ChoiceDialog(
            title = "桌面歌词顶部位置",
            choices = listOf(0.02f, 0.08f, 0.16f, 0.24f, 0.32f).map { Choice(it, it.percentLabel()) },
            onDismiss = { activeDialog = null },
            onSelected = { topPercent ->
                onDesktopLyricTopPercentChange(topPercent)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DesktopLyricLeftPercent -> ChoiceDialog(
            title = "桌面歌词左侧位置",
            choices = listOf(0f, 0.08f, 0.16f, 0.24f).map { Choice(it, it.percentLabel()) },
            onDismiss = { activeDialog = null },
            onSelected = { leftPercent ->
                onDesktopLyricLeftPercentChange(leftPercent)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DesktopLyricWidthPercent -> ChoiceDialog(
            title = "桌面歌词宽度",
            choices = listOf(0.5f, 0.66f, 0.84f, 1f).map { Choice(it, it.percentLabel()) },
            onDismiss = { activeDialog = null },
            onSelected = { widthPercent ->
                onDesktopLyricWidthPercentChange(widthPercent)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DesktopLyricFontSizeSp -> ChoiceDialog(
            title = "桌面歌词字号",
            choices = listOf(14, 16, 18, 20, 24, 28, 32).map { Choice(it, "${it}sp") },
            onDismiss = { activeDialog = null },
            onSelected = { fontSize ->
                onDesktopLyricFontSizeSpChange(fontSize)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DesktopLyricTextColor -> ChoiceDialog(
            title = "桌面歌词文字颜色",
            choices = desktopLyricTextColorChoices(),
            onDismiss = { activeDialog = null },
            onSelected = { color ->
                onDesktopLyricTextColorChange(color)
                activeDialog = null
            },
        )

        BasicSettingsDialog.DesktopLyricBackgroundColor -> ChoiceDialog(
            title = "桌面歌词背景颜色",
            choices = desktopLyricBackgroundColorChoices(),
            onDismiss = { activeDialog = null },
            onSelected = { color ->
                onDesktopLyricBackgroundColorChange(color)
                activeDialog = null
            },
        )

        null -> Unit
    }
}

@Composable
private fun TopBottomSpacer() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(rpx(8)))
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rpx(72))
                            .testTag("settings.choice.${choice.label}")
                            .clickable { onSelected(choice.value) }
                            .padding(horizontal = rpx(8)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = false,
                            onClick = { onSelected(choice.value) },
                        )
                        Text(
                            text = choice.label,
                            modifier = Modifier
                                .padding(start = rpx(8))
                                .clickable { onSelected(choice.value) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun storageDirectoryLabel(state: StorageAccessState): String {
    return if (state.isConfigured) {
        "已配置：${state.selectedDirectory?.displayName.orEmpty()}"
    } else {
        "未配置 Android 存储目录"
    }
}

private fun playQualityChoices(): List<Choice<PlayQuality>> = listOf(
    Choice(PlayQuality.LOW, PlayQuality.LOW.label()),
    Choice(PlayQuality.STANDARD, PlayQuality.STANDARD.label()),
    Choice(PlayQuality.HIGH, PlayQuality.HIGH.label()),
    Choice(PlayQuality.SUPER, PlayQuality.SUPER.label()),
)

private fun desktopLyricTextColorChoices(): List<Choice<String>> = listOf(
    Choice("#FFFFFFFF", "#FFFFFFFF".colorLabel()),
    Choice("#FF222222", "#FF222222".colorLabel()),
    Choice("#FFFFD54F", "#FFFFD54F".colorLabel()),
    Choice("#FF4CAF50", "#FF4CAF50".colorLabel()),
)

private fun desktopLyricBackgroundColorChoices(): List<Choice<String>> = listOf(
    Choice("#00000000", "#00000000".colorLabel()),
    Choice("#66000000", "#66000000".colorLabel()),
    Choice("#99FFFFFF", "#99FFFFFF".colorLabel()),
    Choice("#CC222222", "#CC222222".colorLabel()),
)

private fun MusicDetailDefaultPage.label(): String = when (this) {
    MusicDetailDefaultPage.Album -> "默认展示专辑页"
    MusicDetailDefaultPage.Lyric -> "默认展示歌词页"
}

private fun LyricAssociationType.label(): String = when (this) {
    LyricAssociationType.Search -> "搜索歌词"
    LyricAssociationType.Input -> "手动输入歌曲信息"
}

private fun DesktopLyricAlignment.label(): String = when (this) {
    DesktopLyricAlignment.Left -> "左对齐"
    DesktopLyricAlignment.Center -> "居中"
    DesktopLyricAlignment.Right -> "右对齐"
}

private fun SearchResultClickAction.label(): String = when (this) {
    SearchResultClickAction.PlayMusic -> "播放歌曲"
    SearchResultClickAction.PlayMusicAndReplace -> "播放歌曲并替换播放列表"
}

private fun AlbumMusicClickAction.label(): String = when (this) {
    AlbumMusicClickAction.PlayMusic -> "播放歌曲"
    AlbumMusicClickAction.PlayAlbum -> "播放专辑"
}

private fun SortMode.label(): String = when (this) {
    SortMode.Manual -> "手动排序"
    SortMode.Title -> "按标题"
    SortMode.Artist -> "按歌手"
    SortMode.Album -> "按专辑"
    SortMode.Newest -> "按添加时间倒序"
    SortMode.Oldest -> "按添加时间正序"
}

private fun PlayQuality.label(): String = when (this) {
    PlayQuality.LOW -> "低音质"
    PlayQuality.STANDARD -> "标准音质"
    PlayQuality.HIGH -> "高音质"
    PlayQuality.SUPER -> "超高音质"
}

private fun AudioInterruptionAction.label(): String = when (this) {
    AudioInterruptionAction.Pause -> "暂停播放"
    AudioInterruptionAction.LowerVolume -> "降低音量"
}

private fun Float.percentLabel(): String = "${(this * 100).toInt()}%"

private fun Float.volumeLabel(): String = "${(this * 100).toInt()}%"

private fun String.colorLabel(): String = when (uppercase()) {
    "#FFFFFFFF" -> "白色"
    "#FF222222" -> "深色"
    "#FFFFD54F" -> "黄色"
    "#FF4CAF50" -> "绿色"
    "#00000000" -> "透明"
    "#66000000" -> "半透明黑"
    "#99FFFFFF" -> "半透明白"
    "#CC222222" -> "深色背景"
    else -> this
}

private fun QualityFallbackOrder.playbackLabel(): String = when (this) {
    QualityFallbackOrder.Asc -> "播放更高音质"
    QualityFallbackOrder.Desc -> "播放更低音质"
}

private fun QualityFallbackOrder.downloadLabel(): String = when (this) {
    QualityFallbackOrder.Asc -> "下载更高音质"
    QualityFallbackOrder.Desc -> "下载更低音质"
}
