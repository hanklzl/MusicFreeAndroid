package com.zili.android.musicfreeandroid.feature.settings

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
import com.zili.android.musicfreeandroid.core.model.AlbumMusicClickAction
import com.zili.android.musicfreeandroid.core.model.MusicDetailDefaultPage
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import com.zili.android.musicfreeandroid.core.model.SearchResultClickAction
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.settings.components.SettingActionRow
import com.zili.android.musicfreeandroid.feature.settings.components.SettingSectionCard
import com.zili.android.musicfreeandroid.feature.settings.components.SettingSwitchRow
import com.zili.android.musicfreeandroid.feature.settings.components.SettingValueRow

private data class Choice<T>(
    val value: T,
    val label: String,
)

private enum class BasicSettingsDialog {
    MaxSearchHistoryLength,
    MusicDetailDefaultPage,
    ClickMusicInSearch,
    ClickMusicInAlbum,
    MusicOrderInLocalSheet,
    DefaultPlayQuality,
    PlayQualityOrder,
    MaxDownload,
    DefaultDownloadQuality,
    DownloadQualityOrder,
}

@Composable
fun BasicSettingsContent(
    state: BasicSettingsUiState,
    feedbackExportState: FeedbackExportUiState = FeedbackExportUiState(),
    onMaxSearchHistoryLengthChange: (Int) -> Unit,
    onMusicDetailDefaultPageChange: (MusicDetailDefaultPage) -> Unit,
    onMusicDetailAwakeChange: (Boolean) -> Unit,
    onClickMusicInSearchChange: (SearchResultClickAction) -> Unit,
    onClickMusicInAlbumChange: (AlbumMusicClickAction) -> Unit,
    onMusicOrderInLocalSheetChange: (SortMode) -> Unit,
    onDefaultPlayQualityChange: (PlayQuality) -> Unit,
    onPlayQualityOrderChange: (QualityFallbackOrder) -> Unit,
    onMaxDownloadChange: (Int) -> Unit,
    onDefaultDownloadQualityChange: (PlayQuality) -> Unit,
    onDownloadQualityOrderChange: (QualityFallbackOrder) -> Unit,
    onUseCellularPlayChange: (Boolean) -> Unit,
    onUseCellularDownloadChange: (Boolean) -> Unit,
    onLyricAutoSearchEnabledChange: (Boolean) -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onCreateFeedbackPackage: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    modifier: Modifier = Modifier,
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
                PendingValueRow("关联歌词方式")
                PendingValueRow("通知栏显示关闭按钮 (重启后生效)")
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
                PendingValueRow("软件启动时自动更新插件")
                PendingValueRow("安装插件时不校验版本")
                PendingValueRow("启用插件懒加载")
            }
        }
        item {
            SettingSectionCard("播放", testTag = FidelityAnchors.Settings.BasicSectionPlayback) {
                PendingValueRow("允许与其他应用同时播放")
                PendingValueRow("软件启动时自动播放歌曲")
                PendingValueRow("播放失败时尝试更换音源")
                PendingValueRow("播放失败时自动暂停")
                PendingValueRow("播放被暂时打断时")
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
                PendingValueRow("开启桌面歌词")
                PendingValueRow("桌面歌词位置/样式")
            }
        }
        item {
            SettingSectionCard("缓存", testTag = FidelityAnchors.Settings.BasicSectionCache) {
                PendingValueRow("音乐缓存上限")
                SettingActionRow("清除音乐缓存", enabled = false, onClick = {})
                SettingActionRow("清除歌词缓存", enabled = false, onClick = {})
                SettingActionRow("清除图片缓存", enabled = false, onClick = {})
            }
        }
        item {
            SettingSectionCard("开发选项", testTag = FidelityAnchors.Settings.BasicSectionDeveloper) {
                PendingValueRow("记录错误日志")
                PendingValueRow("记录详细日志")
                PendingValueRow("调试面板")
                SettingActionRow("查看错误日志", enabled = false, onClick = {})
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

        null -> Unit
    }
}

@Composable
private fun PendingValueRow(title: String) {
    SettingValueRow(
        title = title,
        value = "待接入",
        enabled = false,
        onClick = {},
    )
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

private fun MusicDetailDefaultPage.label(): String = when (this) {
    MusicDetailDefaultPage.Album -> "默认展示专辑页"
    MusicDetailDefaultPage.Lyric -> "默认展示歌词页"
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

private fun QualityFallbackOrder.playbackLabel(): String = when (this) {
    QualityFallbackOrder.Asc -> "播放更高音质"
    QualityFallbackOrder.Desc -> "播放更低音质"
}

private fun QualityFallbackOrder.downloadLabel(): String = when (this) {
    QualityFallbackOrder.Asc -> "下载更高音质"
    QualityFallbackOrder.Desc -> "下载更低音质"
}
