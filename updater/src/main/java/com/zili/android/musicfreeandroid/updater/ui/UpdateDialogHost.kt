package com.zili.android.musicfreeandroid.updater.ui

import android.content.Intent
import android.net.Uri
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
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.checker.UpdateState
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.installer.InstallIntents
import kotlinx.coroutines.launch

@Composable
fun UpdateDialogHost(
    checker: UpdateChecker,
    downloader: ApkDownloader,
    installer: ApkInstaller,
) {
    val context = LocalContext.current
    val state by checker.state.collectAsState()
    val scope = rememberCoroutineScope()

    var dismissedAvailable by remember { mutableStateOf(false) }

    when (val s = state) {
        is UpdateState.Available -> {
            if (!s.skipped && !dismissedAvailable) {
                AvailableUpdateDialog(
                    info = s.info,
                    onDownload = {
                        dismissedAvailable = true
                        scope.launch {
                            checker.transitionDownloading(s.info, 0f, 0L, s.info.size)
                            val result = downloader.download(s.info) { bytes, total, fraction ->
                                checker.transitionDownloading(s.info, fraction, bytes, total)
                            }
                            when (result) {
                                is ApkDownloader.Result.Success -> checker.transitionReady(s.info, result.apkFile)
                                is ApkDownloader.Result.Failure -> {
                                    if (result.cause == UpdateError.Canceled) {
                                        checker.transitionAvailable(s.info, skipped = false)
                                    } else {
                                        checker.transitionFailed(s.info, result.cause)
                                    }
                                }
                            }
                        }
                    },
                    onSkip = {
                        dismissedAvailable = true
                        scope.launch { checker.markSkipped(s.info) }
                    },
                    onDismiss = { dismissedAvailable = true },
                )
            }
        }
        is UpdateState.Downloading -> {
            DownloadingDialog(
                info = s.info,
                bytes = s.bytes,
                total = s.total,
                fraction = s.progress,
                onCancel = { downloader.cancel() },
            )
        }
        is UpdateState.ReadyToInstall -> {
            ReadyToInstallDialog(
                info = s.info,
                onInstall = {
                    val result = installer.install(s.apkFile)
                    if (result is ApkInstaller.InstallResult.Blocked) {
                        checker.transitionFailed(s.info, result.cause)
                    }
                },
                onCancel = { checker.transitionAvailable(s.info, skipped = false) },
            )
        }
        is UpdateState.Failed -> {
            when (s.cause) {
                UpdateError.InstallBlocked -> {
                    val info = s.info
                    InstallBlockedDialog(
                        onGoSettings = {
                            context.startActivity(InstallIntents.manageUnknownAppSources(context.packageName))
                        },
                        onDismiss = {
                            if (info != null) checker.transitionAvailable(info, skipped = false)
                        },
                    )
                }
                UpdateError.SchemaUnsupported -> {
                    val info = s.info
                    if (info != null) {
                        SchemaUnsupportedDialog(
                            info = info,
                            onOpenReleasePage = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseNotesUrl))
                                )
                            },
                            onDismiss = { checker.transitionAvailable(info, skipped = false) },
                        )
                    }
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
        if (s is UpdateState.Failed && s.info != null) {
            val msg = when (s.cause) {
                UpdateError.Network -> "网络错误，稍后重试"
                UpdateError.SizeMismatch -> "安装包大小异常"
                UpdateError.Sha256Mismatch -> "安装包校验失败，请稍后重试"
                else -> null
            }
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                checker.transitionAvailable(s.info, skipped = false)
            }
        }
    }
}
