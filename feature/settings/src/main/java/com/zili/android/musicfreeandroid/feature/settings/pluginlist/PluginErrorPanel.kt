package com.zili.android.musicfreeandroid.feature.settings.pluginlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.plugin.runtime.PluginErrorReason
import com.zili.android.musicfreeandroid.plugin.runtime.PluginState

/**
 * Error panel for a Failed [PluginUiEntry] row. Used by [PluginListScreen] when
 * the user taps the red status badge on a plugin row.
 *
 * Surfaces:
 *  - A localized title for the [PluginErrorReason] (e.g. "插件缺少 platform 字段").
 *  - The free-form `detail` string from `PluginState.Failed.detail` for
 *    debugging context.
 *  - Three actions: retry, copy error text to clipboard, uninstall.
 */
@Composable
fun PluginErrorPanel(
    entry: PluginUiEntry,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
    onCopyError: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("插件加载失败") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = reasonLabel(entry.state),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(rpx(8)))
                Text(
                    text = entry.platform,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!entry.detail.isNullOrBlank()) {
                    Spacer(Modifier.height(rpx(12)))
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRetry()
                    onDismiss()
                },
                enabled = entry.filePath != null,
            ) { Text("重试") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopyError) { Text("复制") }
                TextButton(onClick = {
                    onUninstall()
                    onDismiss()
                }) { Text("卸载") }
            }
        },
    )
}

/**
 * Localized error reason for a `PluginState.Failed` state. Defaults to a
 * generic message when the state is unexpectedly non-Failed (defensive: the
 * panel should only be shown for Failed entries, but the type system doesn't
 * narrow until the caller checks).
 */
internal fun reasonLabel(state: PluginState): String {
    val failed = state as? PluginState.Failed ?: return "未知错误"
    return when (failed.reason) {
        PluginErrorReason.VersionNotMatch -> "应用版本不匹配"
        PluginErrorReason.CannotParse -> "无法解析插件代码"
        PluginErrorReason.MissingPlatform -> "插件缺少 platform 字段"
        PluginErrorReason.DownloadFailed -> "下载失败"
        PluginErrorReason.UserVariableSyncFailed -> "用户变量同步失败"
    }
}
