package com.zili.android.musicfreeandroid.feature.settings

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold
import kotlinx.coroutines.flow.collectLatest

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
    val context = LocalContext.current
    var showFeedbackConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.feedbackPackage.collectLatest { packageItem ->
            val feedbackUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.feedback-files",
                packageItem.file,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, feedbackUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "分享日志包"))
        }
    }

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
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "生成日志包并分享",
                    description = "创建包含日志与运行环境信息的压缩包，用于问题反馈。",
                    actionText = "生成",
                    onClick = { showFeedbackConfirm = true },
                )
            }

            item {
                Spacer(modifier = Modifier.height(rpx(16)))
                SettingsEntryCard(
                    title = "清空日志",
                    description = "清理本地日志缓存与历史日志导出文件。",
                    actionText = "清空",
                    onClick = { viewModel.clearLogs() },
                )
            }
        }
    }

    if (showFeedbackConfirm) {
        FeedbackExportConfirmDialog(
            onDismiss = { showFeedbackConfirm = false },
            onConfirm = {
                showFeedbackConfirm = false
                viewModel.createFeedbackPackage()
            },
        )
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

@Composable
private fun FeedbackExportConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "分享日志包")
        },
        text = {
            Text(
                text = "日志包可能包含搜索词、请求地址、插件返回内容以及设备信息。\\n\\n仅在需要排查问题时用于反馈。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "继续")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
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
