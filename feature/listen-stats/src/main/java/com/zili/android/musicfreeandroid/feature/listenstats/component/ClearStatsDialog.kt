package com.zili.android.musicfreeandroid.feature.listenstats.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ClearStatsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除统计数据") },
        text = { Text("这将删除所有听歌统计数据，且不可恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
