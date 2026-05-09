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
import com.zili.android.musicfreeandroid.core.model.PlayQuality
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

@Composable
fun BasicSettingsContent(
    state: BasicSettingsUiState,
    onMaxDownloadChange: (Int) -> Unit,
    onDefaultDownloadQualityChange: (PlayQuality) -> Unit,
    onUseCellularDownloadChange: (Boolean) -> Unit,
    onLyricAutoSearchEnabledChange: (Boolean) -> Unit,
    onNavigateToFileSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var maxDownloadDialogVisible by remember { mutableStateOf(false) }
    var downloadQualityDialogVisible by remember { mutableStateOf(false) }

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
                PendingValueRow("历史记录最多保存条数")
                PendingValueRow("打开歌曲详情页时")
                PendingValueRow("处于歌曲详情页时常亮")
                PendingValueRow("关联歌词方式")
                PendingValueRow("通知栏显示关闭按钮 (重启后生效)")
            }
        }
        item {
            SettingSectionCard("歌单&专辑", testTag = FidelityAnchors.Settings.BasicSectionSheetAlbum) {
                PendingValueRow("点击搜索结果内单曲时")
                PendingValueRow("点击专辑内单曲时")
                PendingValueRow("新建歌单时默认歌曲排序")
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
                PendingValueRow("默认播放音质")
                PendingValueRow("默认播放音质缺失时")
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
                    onClick = { maxDownloadDialogVisible = true },
                )
                SettingValueRow(
                    title = "默认下载音质",
                    value = state.defaultDownloadQuality.label(),
                    enabled = true,
                    testTag = FidelityAnchors.Settings.BasicDefaultDownloadQuality,
                    onClick = { downloadQualityDialogVisible = true },
                )
                PendingValueRow("默认下载音质缺失时")
            }
        }
        item {
            SettingSectionCard("网络", testTag = FidelityAnchors.Settings.BasicSectionNetwork) {
                PendingValueRow("使用移动网络播放")
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
                SettingActionRow("清空日志", enabled = false, onClick = {})
            }
        }
        item { TopBottomSpacer() }
    }

    if (maxDownloadDialogVisible) {
        ChoiceDialog(
            title = "最大同时下载数目",
            choices = listOf(1, 3, 5, 7).map { Choice(it, it.toString()) },
            onDismiss = { maxDownloadDialogVisible = false },
            onSelected = { value ->
                onMaxDownloadChange(value)
                maxDownloadDialogVisible = false
            },
        )
    }

    if (downloadQualityDialogVisible) {
        ChoiceDialog(
            title = "默认下载音质",
            choices = listOf(
                Choice(PlayQuality.LOW, "低音质"),
                Choice(PlayQuality.STANDARD, "标准音质"),
                Choice(PlayQuality.HIGH, "高音质"),
                Choice(PlayQuality.SUPER, "超高音质"),
            ),
            onDismiss = { downloadQualityDialogVisible = false },
            onSelected = { quality ->
                onDefaultDownloadQualityChange(quality)
                downloadQualityDialogVisible = false
            },
        )
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

private fun PlayQuality.label(): String = when (this) {
    PlayQuality.LOW -> "低音质"
    PlayQuality.STANDARD -> "标准音质"
    PlayQuality.HIGH -> "高音质"
    PlayQuality.SUPER -> "超高音质"
}
