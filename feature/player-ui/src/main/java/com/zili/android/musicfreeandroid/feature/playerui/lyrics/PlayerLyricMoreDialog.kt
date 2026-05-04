package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun PlayerLyricMoreDialog(
    onDismiss: () -> Unit,
    onImportRaw: () -> Unit,
    onImportTranslation: () -> Unit,
    onDeleteLocal: () -> Unit,
    onClearAssociated: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("歌词更多") },
        text = {
            Column {
                TextButton(
                    onClick = onImportRaw,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导入本地歌词")
                }
                TextButton(
                    onClick = onImportTranslation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导入翻译歌词")
                }
                TextButton(
                    onClick = onDeleteLocal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除本地歌词")
                }
                TextButton(
                    onClick = onClearAssociated,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("解除关联歌词")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
