package com.zili.android.musicfreeandroid.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold
import com.zili.android.musicfreeandroid.feature.settings.components.SettingSectionCard
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog

@Composable
fun SettingsScreen(
    type: SettingsType,
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val basicState by viewModel.basicSettingsUiState.collectAsStateWithLifecycle()
    val feedbackExportUiState by viewModel.feedbackExportUiState.collectAsStateWithLifecycle()
    val errorLogUiState by viewModel.errorLogUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showFeedbackConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(context, viewModel, feedbackExportUiState.pendingPackage) {
        feedbackExportUiState.pendingPackage?.let { packageItem ->
            val feedbackUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.feedback-files",
                packageItem.file,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                setType("application/zip")
                putExtra(Intent.EXTRA_STREAM, feedbackUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                context.startActivity(Intent.createChooser(shareIntent, "分享日志包"))
            } catch (error: ActivityNotFoundException) {
                MfLog.error(
                    LogCategory.FEEDBACK,
                    "feedback_package_share_failed",
                    error,
                )
                viewModel.onFeedbackExportError("未找到可用于分享日志包的应用")
            } catch (error: RuntimeException) {
                MfLog.error(
                    LogCategory.FEEDBACK,
                    "feedback_package_share_failed",
                    error,
                )
                viewModel.onFeedbackExportError("分享日志包失败，请稍后重试")
            } finally {
                viewModel.onFeedbackPackageShared()
            }
        }
    }

    LaunchedEffect(context, viewModel, feedbackExportUiState.errorMessage) {
        feedbackExportUiState.errorMessage?.let { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedbackError()
        }
    }

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
                feedbackExportState = feedbackExportUiState,
                onMaxSearchHistoryLengthChange = viewModel::setMaxSearchHistoryLength,
                onMusicDetailDefaultPageChange = viewModel::setMusicDetailDefaultPage,
                onMusicDetailAwakeChange = viewModel::setMusicDetailAwake,
                onLyricAssociationTypeChange = viewModel::setLyricAssociationType,
                onShowExitOnNotificationChange = viewModel::setShowExitOnNotification,
                onClickMusicInSearchChange = viewModel::setClickMusicInSearch,
                onClickMusicInAlbumChange = viewModel::setClickMusicInAlbum,
                onMusicOrderInLocalSheetChange = viewModel::setMusicOrderInLocalSheet,
                onDefaultPlayQualityChange = viewModel::setDefaultPlayQuality,
                onPlayQualityOrderChange = viewModel::setPlayQualityOrder,
                onAllowConcurrentPlaybackChange = viewModel::setAllowConcurrentPlayback,
                onAutoPlayWhenAppStartChange = viewModel::setAutoPlayWhenAppStart,
                onTryChangeSourceWhenPlayFailChange = viewModel::setTryChangeSourceWhenPlayFail,
                onAutoStopWhenErrorChange = viewModel::setAutoStopWhenError,
                onAudioInterruptionActionChange = viewModel::setAudioInterruptionAction,
                onAudioInterruptionDuckVolumeChange = viewModel::setAudioInterruptionDuckVolume,
                onMaxDownloadChange = viewModel::setMaxDownload,
                onDefaultDownloadQualityChange = viewModel::setDefaultDownloadQuality,
                onDownloadQualityOrderChange = viewModel::setDownloadQualityOrder,
                onUseCellularPlayChange = viewModel::setUseCellularPlay,
                onUseCellularDownloadChange = viewModel::setUseCellularDownload,
                onLyricAutoSearchEnabledChange = viewModel::setLyricAutoSearchEnabled,
                onDesktopLyricEnabledChange = { enabled ->
                    if (!enabled) {
                        viewModel.setDesktopLyricEnabled(false)
                    } else if (readPermissionsUiState(context).overlayGranted) {
                        viewModel.setDesktopLyricEnabled(true)
                    } else {
                        val opened = openOverlaySettings(context)
                        Toast.makeText(
                            context,
                            if (opened) "请授予悬浮窗权限后重新开启桌面歌词" else "无法打开悬浮窗权限设置",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onDesktopLyricAlignmentChange = viewModel::setDesktopLyricAlignment,
                onDesktopLyricTopPercentChange = viewModel::setDesktopLyricTopPercent,
                onDesktopLyricLeftPercentChange = viewModel::setDesktopLyricLeftPercent,
                onDesktopLyricWidthPercentChange = viewModel::setDesktopLyricWidthPercent,
                onDesktopLyricFontSizeSpChange = viewModel::setDesktopLyricFontSizeSp,
                onDesktopLyricTextColorChange = viewModel::setDesktopLyricTextColor,
                onDesktopLyricBackgroundColorChange = viewModel::setDesktopLyricBackgroundColor,
                onAutoUpdatePluginsChange = viewModel::setAutoUpdatePlugins,
                onSkipPluginVersionCheckChange = viewModel::setSkipPluginVersionCheck,
                onLazyLoadPluginsChange = viewModel::setLazyLoadPlugins,
                onMaxMusicCacheSizeMbChange = viewModel::setMaxMusicCacheSizeMb,
                onClearMusicCache = viewModel::clearMusicCache,
                onClearLyricCache = viewModel::clearLyricCache,
                onClearImageCache = viewModel::clearImageCache,
                onNavigateToFileSelector = onNavigateToFileSelector,
                onCreateFeedbackPackage = { showFeedbackConfirm = true },
                onClearLogs = viewModel::clearLogs,
                onDebugErrorLogEnabledChange = viewModel::setDebugErrorLogEnabled,
                onDebugTraceLogEnabledChange = viewModel::setDebugTraceLogEnabled,
                onDebugDevLogEnabledChange = viewModel::setDebugDevLogEnabled,
                onViewErrorLog = viewModel::showErrorLog,
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

            SettingsType.About -> AboutSettingsContent(
                modifier = Modifier.padding(innerPadding),
            )
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

    if (errorLogUiState.visible) {
        ErrorLogDialog(
            content = errorLogUiState.content,
            onCopy = {
                clipboardManager.setText(AnnotatedString(errorLogUiState.content))
                Toast.makeText(context, "错误日志已复制", Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::dismissErrorLog,
        )
    }
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
                text = "日志包可能包含搜索词、请求地址、插件返回内容以及设备信息。\n\n仅在需要排查问题时用于反馈。",
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
private fun ErrorLogDialog(
    content: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "错误日志") },
        text = {
            LazyColumn(modifier = Modifier.height(rpx(480))) {
                item {
                    Text(
                        text = content,
                        fontSize = FontSizes.description,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text(text = "复制")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        },
    )
}

@Composable
private fun AboutSettingsContent(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Settings.AboutRoot)
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        item { Spacer(modifier = Modifier.height(rpx(24))) }
        item {
            SettingSectionCard(
                "关于",
                testTag = FidelityAnchors.Settings.AboutEntry,
            ) {
                CheckUpdateRow()
            }
        }
        item { Spacer(modifier = Modifier.height(rpx(24))) }
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
    SettingsType.Theme -> "主题设置"
    SettingsType.Backup -> "备份与恢复"
    SettingsType.About -> "关于 MusicFree"
}
