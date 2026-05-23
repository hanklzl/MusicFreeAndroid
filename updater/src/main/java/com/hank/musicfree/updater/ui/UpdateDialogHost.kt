package com.hank.musicfree.updater.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.checker.UpdateCheckSource
import com.hank.musicfree.updater.checker.UpdateError
import com.hank.musicfree.updater.checker.UpdateState
import com.hank.musicfree.updater.downloader.UpdateDownloadManager
import com.hank.musicfree.updater.installer.ApkInstaller
import com.hank.musicfree.updater.installer.InstallIntents
import kotlinx.coroutines.launch

@Composable
fun UpdateDialogHost(
    checker: UpdateChecker,
    downloadManager: UpdateDownloadManager,
    installer: ApkInstaller,
) {
    val context = LocalContext.current
    val state by checker.state.collectAsState()
    val scope = rememberCoroutineScope()

    var dismissedAvailable by remember { mutableStateOf(false) }

    when (val s = state) {
        is UpdateState.Available -> {
            if (s.source == UpdateCheckSource.Launch) {
                LaunchedEffect(s.update.info.versionCode, s.skipped, s.source) {
                    if (!s.skipped) {
                        downloadManager.startSilentIfAllowed(s.update)
                    }
                }
            } else if (!s.skipped && !dismissedAvailable) {
                AvailableUpdateDialog(
                    update = s.update,
                    onDownload = {
                        dismissedAvailable = true
                        downloadManager.downloadNow(s.update)
                    },
                    onSkip = {
                        dismissedAvailable = true
                        scope.launch { checker.markSkipped(s.update) }
                    },
                    onDismiss = { dismissedAvailable = true },
                )
            }
        }
        is UpdateState.Downloading -> {
            DownloadingDialog(
                update = s.update,
                bytes = s.bytes,
                total = s.total,
                fraction = s.progress,
                onCancel = { downloadManager.cancelActiveDownload("update_dialog_cancel") },
            )
        }
        is UpdateState.ReadyToInstall -> {
            var installing by remember { mutableStateOf(false) }
            ReadyToInstallDialog(
                update = s.update,
                onInstall = {
                    if (installing) return@ReadyToInstallDialog
                    installing = true
                    scope.launch {
                        try {
                            val result = installer.install(s.apkFile)
                            if (result is ApkInstaller.InstallResult.Blocked) {
                                checker.transitionFailed(s.update, result.cause)
                            }
                        } finally {
                            installing = false
                        }
                    }
                },
                onCancel = { checker.transitionAvailable(s.update, skipped = false) },
            )
        }
        is UpdateState.Failed -> {
            val update = s.update
            when (s.cause) {
                UpdateError.InstallBlocked -> {
                    InstallBlockedDialog(
                        onGoSettings = {
                            context.startActivity(InstallIntents.manageUnknownAppSources(context.packageName))
                        },
                        onDismiss = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                        },
                    )
                }
                UpdateError.InstallFailed -> {
                    InstallFailedDialog(
                        onRetry = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                        },
                        onDismiss = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                        },
                    )
                }
                UpdateError.SchemaUnsupported -> {
                    val notesUrl = update?.info?.releaseNotesUrl ?: GITHUB_RELEASES_LATEST
                    SchemaUnsupportedDialog(
                        version = update?.info?.version,
                        releaseNotesUrl = notesUrl,
                        onOpenReleasePage = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, notesUrl.toUri()))
                        },
                        onDismiss = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                        },
                    )
                }
                UpdateError.UnsupportedAbi -> {
                    // 启动 dialog 不弹此分支；手动检查走 ManualUpdateDialog
                }
                UpdateError.Network,
                UpdateError.SizeMismatch,
                UpdateError.Sha256Mismatch -> {
                    // Toast + transition back handled by LaunchedEffect below
                }
                UpdateError.Canceled -> Unit
            }
        }
        else -> Unit
    }

    LaunchedEffect(state) {
        if (state !is UpdateState.Available) dismissedAvailable = false
        val s = state
        if (s is UpdateState.Failed && s.update != null) {
            val msg = when (s.cause) {
                UpdateError.Network -> "网络错误，稍后重试"
                UpdateError.SizeMismatch -> "安装包大小异常"
                UpdateError.Sha256Mismatch -> "安装包校验失败，请稍后重试"
                else -> null
            }
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                checker.transitionAvailable(s.update, skipped = false)
            }
        }
    }
}

internal const val GITHUB_RELEASES_LATEST: String =
    "https://github.com/hanklzl/MusicFreeAndroid/releases/latest"
