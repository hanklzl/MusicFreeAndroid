package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
    onNavigateToPluginList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val storageAccessState by viewModel.storageAccessState.collectAsStateWithLifecycle()
    val maxDownload by viewModel.maxDownload.collectAsStateWithLifecycle()
    val useCellularDownload by viewModel.useCellularDownload.collectAsStateWithLifecycle()
    val defaultDownloadQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle()

    MusicFreeScreenScaffold(
        title = "设置",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.SettingsRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = rpx(24)),
        ) {
            item {
                Spacer(modifier = Modifier.height(rpx(24)))
                SettingsEntryCard(
                    title = "插件管理",
                    description = "管理已安装的插件，安装新插件，管理订阅",
                    actionText = "进入",
                    onClick = onNavigateToPluginList,
                    modifier = Modifier.testTag(FidelityAnchors.Settings.PluginManagementEntry),
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "主题设置",
                    description = "主题选项将显示在这里。",
                    modifier = Modifier.testTag(FidelityAnchors.Settings.ThemeEntry),
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "备份",
                    description = "备份与恢复入口将显示在这里。",
                    modifier = Modifier.testTag(FidelityAnchors.Settings.BackupEntry),
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "关于",
                    description = "应用信息与版本详情将显示在这里。",
                    modifier = Modifier.testTag(FidelityAnchors.Settings.AboutEntry),
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "权限管理",
                    description = "管理悬浮窗和存储/音频读取权限",
                    actionText = "进入",
                    onClick = onNavigateToPermissions,
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "存储目录",
                    description = storageDirectoryDescription(storageAccessState),
                    actionText = if (storageAccessState.isConfigured) "更换" else "选择",
                    onClick = onNavigateToFileSelector,
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(24)))
                Text(
                    text = "下载",
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = rpx(4), vertical = rpx(8)),
                )
            }

            item {
                DownloadSettingsCard(
                    maxDownload = maxDownload,
                    useCellularDownload = useCellularDownload,
                    defaultDownloadQuality = defaultDownloadQuality,
                    onMaxDownloadChange = { viewModel.setMaxDownload(it) },
                    onUseCellularDownloadChange = { viewModel.setUseCellularDownload(it) },
                    onDefaultDownloadQualityChange = { viewModel.setDefaultDownloadQuality(it) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(24)))
            }
        }
    }
}

private fun storageDirectoryDescription(state: StorageAccessState): String {
    val prefix = if (state.isConfigured) {
        "已配置目录：${state.selectedDirectory?.displayName}"
    } else {
        "未配置目录"
    }
    return "$prefix，用于后续下载、备份和本地导入能力。"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadSettingsCard(
    maxDownload: Int,
    useCellularDownload: Boolean,
    defaultDownloadQuality: PlayQuality,
    onMaxDownloadChange: (Int) -> Unit,
    onUseCellularDownloadChange: (Boolean) -> Unit,
    onDefaultDownloadQualityChange: (PlayQuality) -> Unit,
) {
    val qualities = listOf(
        PlayQuality.LOW to "低品",
        PlayQuality.STANDARD to "标准",
        PlayQuality.HIGH to "高品",
        PlayQuality.SUPER to "超品",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rpx(16)),
        colors = CardDefaults.cardColors(containerColor = MusicFreeTheme.colors.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rpx(24), vertical = rpx(20)),
            verticalArrangement = Arrangement.spacedBy(rpx(16)),
        ) {
            // Row 1: max concurrent downloads
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "同时下载数：$maxDownload",
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
                Slider(
                    value = maxDownload.toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChange = { onMaxDownloadChange(it.toInt()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Row 2: cellular toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "使用移动网络下载",
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
                Switch(
                    checked = useCellularDownload,
                    onCheckedChange = onUseCellularDownloadChange,
                )
            }

            // Row 3: default download quality
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "默认下载音质",
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
                Spacer(modifier = Modifier.height(rpx(8)))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    qualities.forEachIndexed { i, (q, label) ->
                        SegmentedButton(
                            selected = defaultDownloadQuality == q,
                            onClick = { onDefaultDownloadQualityChange(q) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = qualities.size),
                        ) {
                            Text(text = label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    description: String,
    actionText: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val action = onClick
    val label = actionText
    val hasAction = label != null && action != null
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rpx(16)),
        colors = CardDefaults.cardColors(
            containerColor = MusicFreeTheme.colors.card,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rpx(24), vertical = rpx(20)),
            horizontalArrangement = if (hasAction) Arrangement.SpaceBetween else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
                Spacer(modifier = Modifier.height(rpx(6)))
                Text(
                    text = description,
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
            if (hasAction) {
                TextButton(onClick = action, enabled = enabled) {
                    Text(text = label)
                }
            }
        }
    }
}
