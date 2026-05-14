package com.zili.android.musicfreeandroid.updater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.updater.model.UpdateInfo

@Composable
fun AvailableUpdateDialog(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                info.changeLog.take(8).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("下载并安装") } },
        dismissButton = {
            Column {
                TextButton(onClick = onSkip) { Text("跳过此版本") }
                TextButton(onClick = onDismiss) { Text("稍后再说") }
            }
        },
    )
}

@Composable
fun DownloadingDialog(
    info: UpdateInfo,
    bytes: Long,
    total: Long,
    fraction: Float,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在下载 v${info.version}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "${bytes / 1024} KB / ${total / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
fun ReadyToInstallDialog(
    info: UpdateInfo,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("下载完成 v${info.version}") },
        text = { Text("立即安装新版本？") },
        confirmButton = { TextButton(onClick = onInstall) { Text("立即安装") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("稍后") } },
    )
}

@Composable
fun InstallBlockedDialog(
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无法安装") },
        text = { Text("系统未允许本应用安装未知来源应用。请在系统设置中授权后重试。") },
        confirmButton = { TextButton(onClick = onGoSettings) { Text("前往设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun SchemaUnsupportedDialog(
    info: UpdateInfo,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.version}") },
        text = { Text("当前客户端无法理解新版本元数据，请前往 GitHub 下载新版。") },
        confirmButton = { TextButton(onClick = onOpenReleasePage) { Text("打开下载页") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
    )
}
