package com.zili.android.musicfreeandroid.updater.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import com.zili.android.musicfreeandroid.updater.checker.UpdateState
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.installer.InstallIntents
import kotlinx.coroutines.launch

/**
 * 用户从侧栏 / 设置主动点「检查更新」时打开的 dialog。
 * 与 [UpdateDialogHost] 共享同一 [UpdateChecker] state，但渲染所有状态（含 Checking / UpToDate / Failed）。
 *
 * 关闭只把 [onDismiss] 触发，不重置 [UpdateChecker.state]——红点 / 启动 dialog 由 state 自主驱动。
 */
@Composable
fun ManualUpdateDialog(
    checker: UpdateChecker,
    downloader: ApkDownloader,
    installer: ApkInstaller,
    localVersionName: String,
    onDismiss: () -> Unit,
    deviceAbiProvider: () -> String? = { Build.SUPPORTED_ABIS.firstOrNull() },
) {
    val context = LocalContext.current
    val state by checker.state.collectAsState()
    val scope = rememberCoroutineScope()

    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> {
            CheckingDialog(onDismiss = onDismiss)
        }
        is UpdateState.UpToDate -> {
            UpToDateDialog(localVersion = localVersionName, onDismiss = onDismiss)
        }
        is UpdateState.Available -> {
            // 主动检查忽略 skipped
            AvailableUpdateDialog(
                update = s.update,
                onDownload = {
                    scope.launch {
                        checker.transitionDownloading(s.update, 0f, 0L, s.update.variant.size)
                        val result = downloader.download(s.update) { bytes, total, fraction ->
                            checker.transitionDownloading(s.update, fraction, bytes, total)
                        }
                        when (result) {
                            is ApkDownloader.Result.Success -> checker.transitionReady(s.update, result.apkFile)
                            is ApkDownloader.Result.Failure -> {
                                if (result.cause == UpdateError.Canceled) {
                                    checker.transitionAvailable(s.update, skipped = false)
                                } else {
                                    checker.transitionFailed(s.update, result.cause)
                                }
                            }
                        }
                    }
                },
                onSkip = {
                    scope.launch { checker.markSkipped(s.update) }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        is UpdateState.Downloading -> {
            DownloadingDialog(
                update = s.update,
                bytes = s.bytes,
                total = s.total,
                fraction = s.progress,
                onCancel = { downloader.cancel() },
            )
        }
        is UpdateState.ReadyToInstall -> {
            ReadyToInstallDialog(
                update = s.update,
                onInstall = {
                    val result = installer.install(s.apkFile)
                    if (result is ApkInstaller.InstallResult.Blocked) {
                        checker.transitionFailed(s.update, result.cause)
                    }
                },
                onCancel = { checker.transitionAvailable(s.update, skipped = false) },
            )
        }
        is UpdateState.Failed -> {
            val update = s.update
            when (s.cause) {
                UpdateError.Network -> {
                    NetworkFailedDialog(
                        onRetry = { checker.checkManually() },
                        onDismiss = onDismiss,
                    )
                }
                UpdateError.SchemaUnsupported -> {
                    val notesUrl = update?.info?.releaseNotesUrl ?: GITHUB_RELEASES_LATEST
                    SchemaUnsupportedDialog(
                        version = update?.info?.version,
                        releaseNotesUrl = notesUrl,
                        onOpenReleasePage = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notesUrl)))
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                    )
                }
                UpdateError.UnsupportedAbi -> {
                    val notesUrl = update?.info?.releaseNotesUrl ?: GITHUB_RELEASES_LATEST
                    UnsupportedAbiDialog(
                        currentAbi = deviceAbiProvider(),
                        onOpenReleasePage = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notesUrl)))
                            onDismiss()
                        },
                        onDismiss = onDismiss,
                    )
                }
                UpdateError.InstallBlocked -> {
                    InstallBlockedDialog(
                        onGoSettings = {
                            context.startActivity(InstallIntents.manageUnknownAppSources(context.packageName))
                        },
                        onDismiss = {
                            if (update != null) checker.transitionAvailable(update, skipped = false)
                            onDismiss()
                        },
                    )
                }
                UpdateError.SizeMismatch,
                UpdateError.Sha256Mismatch -> {
                    // toast by LaunchedEffect below
                }
                UpdateError.Canceled -> Unit
            }
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.Failed && s.update != null) {
            val msg = when (s.cause) {
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
