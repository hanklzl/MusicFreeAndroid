package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold

@Composable
fun SettingsScreen(
    type: SettingsType,
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
    onNavigateToPluginList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val basicState by viewModel.basicSettingsUiState.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        title = type.title(),
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.SettingsRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        when (type) {
            SettingsType.Basic -> BasicSettingsContent(
                state = basicState,
                onMaxDownloadChange = viewModel::setMaxDownload,
                onDefaultDownloadQualityChange = viewModel::setDefaultDownloadQuality,
                onUseCellularDownloadChange = viewModel::setUseCellularDownload,
                onLyricAutoSearchEnabledChange = viewModel::setLyricAutoSearchEnabled,
                onNavigateToFileSelector = onNavigateToFileSelector,
                modifier = Modifier.padding(innerPadding),
            )

            SettingsType.Plugin -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.PluginRoot,
                title = "插件管理",
                description = "管理已安装的插件，安装新插件，管理订阅",
                entryTag = FidelityAnchors.Settings.PluginManagementEntry,
                actionText = "进入",
                onClick = onNavigateToPluginList,
                modifier = Modifier.padding(innerPadding),
            )

            SettingsType.Theme -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.ThemeRoot,
                title = "主题设置",
                description = "主题选项将显示在这里。",
                entryTag = FidelityAnchors.Settings.ThemeEntry,
                actionText = "待接入",
                onClick = null,
                modifier = Modifier.padding(innerPadding),
            )

            SettingsType.Backup -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.BackupRoot,
                title = "备份与恢复",
                description = "备份与恢复入口将显示在这里。",
                entryTag = FidelityAnchors.Settings.BackupEntry,
                actionText = "待接入",
                onClick = null,
                modifier = Modifier.padding(innerPadding),
            )

            SettingsType.About -> SettingsTypeEntryContent(
                rootTag = FidelityAnchors.Settings.AboutRoot,
                title = "关于 MusicFree",
                description = "应用信息与版本详情将显示在这里。",
                entryTag = FidelityAnchors.Settings.AboutEntry,
                actionText = "待接入",
                onClick = null,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun SettingsTypeEntryContent(
    rootTag: String,
    title: String,
    description: String,
    entryTag: String,
    actionText: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(rootTag)
            .padding(horizontal = rpx(24)),
    ) {
        item {
            Spacer(modifier = Modifier.height(rpx(24)))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(entryTag),
                shape = RoundedCornerShape(rpx(16)),
                colors = CardDefaults.cardColors(containerColor = MusicFreeTheme.colors.card),
            ) {
                Column(modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(20))) {
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
                    Spacer(modifier = Modifier.height(rpx(12)))
                    TextButton(onClick = onClick ?: {}, enabled = onClick != null) {
                        Text(actionText)
                    }
                }
            }
        }
    }
}

private fun SettingsType.title(): String = when (this) {
    SettingsType.Basic -> "基本设置"
    SettingsType.Plugin -> "插件管理"
    SettingsType.Theme -> "主题设置"
    SettingsType.Backup -> "备份与恢复"
    SettingsType.About -> "关于 MusicFree"
}
