package com.hank.musicfree.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
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
import com.hank.musicfree.core.navigation.SettingsType
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.feature.settings.components.SettingSectionCard
import com.hank.musicfree.feature.settings.themesetting.ThemeSettingsContent
import com.hank.musicfree.feature.settings.themesetting.ThemeSettingsViewModel
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog

@Composable
fun SettingsScreen(
    type: SettingsType,
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToFileSelector: () -> Unit,
    onNavigateToSetCustomTheme: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToLocalFileSelector: () -> Unit = onNavigateToFileSelector,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val basicState by viewModel.basicSettingsUiState.collectAsStateWithLifecycle()
    val feedbackExportUiState by viewModel.feedbackExportUiState.collectAsStateWithLifecycle()
    val errorLogUiState by viewModel.errorLogUiState.collectAsStateWithLifecycle()
    val backupRestoreUiState by viewModel.backupRestoreUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showFeedbackConfirm by remember { mutableStateOf(false) }
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.createBackup(uri)
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.validateRestore(uri)
        }
    }

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
                onSilentUpdateDownloadEnabledChange = viewModel::setSilentUpdateDownloadEnabled,
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

            SettingsType.Theme -> {
                val themeVm: ThemeSettingsViewModel = hiltViewModel()
                val themeState by themeVm.state.collectAsStateWithLifecycle()
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                ThemeSettingsContent(
                    state = themeState,
                    onFollowSystemToggle = { themeVm.onFollowSystemToggle(it, systemDark) },
                    onSelectLight = { themeVm.onSelectTheme(SelectedTheme.P_LIGHT) },
                    onSelectDark = { themeVm.onSelectTheme(SelectedTheme.P_DARK) },
                    onSelectCustom = { themeVm.onSelectTheme(SelectedTheme.CUSTOM) },
                    onNavigateToSetCustomTheme = onNavigateToSetCustomTheme,
                    modifier = Modifier.padding(innerPadding),
                )
            }

            SettingsType.Backup -> BackupRestoreContent(
                state = backupRestoreUiState,
                onCreateBackup = {
                    createBackupLauncher.launch("MusicFree-backup-${java.time.LocalDate.now()}.mfbackup")
                },
                onRestoreBackup = {
                    restoreBackupLauncher.launch(
                        arrayOf("application/octet-stream", "application/zip", "*/*"),
                    )
                },
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

    if (backupRestoreUiState.restoreConfirmationVisible) {
        BackupRestoreConfirmDialog(
            state = backupRestoreUiState,
            onDismiss = viewModel::dismissRestoreConfirmation,
            onConfirm = viewModel::confirmRestore,
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
private fun BackupRestoreContent(
    state: BackupRestoreUiState,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Settings.BackupRoot)
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        item { Spacer(modifier = Modifier.height(rpx(24))) }
        item {
            SettingSectionCard(
                title = "备份与恢复",
                testTag = FidelityAnchors.Settings.BackupEntry,
            ) {
                Text(
                    text = "创建迁移包后，可在新包名版本中导入。恢复会覆盖当前应用内数据，系统权限需要重新授权。",
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(rpx(12)))
                Button(
                    onClick = onCreateBackup,
                    enabled = !state.inProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FidelityAnchors.Settings.BackupCreate),
                ) {
                    Text(text = "创建备份/迁移包")
                }
                Spacer(modifier = Modifier.height(rpx(8)))
                Button(
                    onClick = onRestoreBackup,
                    enabled = !state.inProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FidelityAnchors.Settings.BackupRestore),
                ) {
                    Text(text = "从备份恢复")
                }
                val status = state.message ?: state.errorMessage
                if (status != null) {
                    Spacer(modifier = Modifier.height(rpx(8)))
                    Text(
                        text = status,
                        fontSize = FontSizes.description,
                        color = MusicFreeTheme.colors.textSecondary,
                        modifier = Modifier.testTag(FidelityAnchors.Settings.BackupStatus),
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(rpx(24))) }
    }
}

@Composable
private fun BackupRestoreConfirmDialog(
    state: BackupRestoreUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "确认恢复备份") },
        text = {
            Text(
                text = "恢复会覆盖当前应用内数据，并需要重启应用后生效。\n\n" +
                    "来源：${state.restoreSourcePackageName ?: "未知"} ${state.restoreAppVersionName ?: ""}\n" +
                    "文件数：${state.restoreFileCount}\n\n" +
                    "系统权限不会继承，恢复后需要重新授权通知、媒体读取、悬浮窗和存储目录。",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.inProgress,
            ) {
                Text(text = "确认恢复")
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
