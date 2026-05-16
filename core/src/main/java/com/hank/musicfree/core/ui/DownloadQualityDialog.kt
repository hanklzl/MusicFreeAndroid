package com.hank.musicfree.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.PlayQuality

@Composable
fun DownloadQualityDialog(
    initial: PlayQuality,
    onDismiss: () -> Unit,
    onConfirm: (PlayQuality) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载音质") },
        text = {
            Column(Modifier.selectableGroup()) {
                listOf(
                    PlayQuality.LOW to "低品",
                    PlayQuality.STANDARD to "标准",
                    PlayQuality.HIGH to "高品",
                    PlayQuality.SUPER to "超品",
                ).forEach { (q, label) ->
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = selected == q,
                                role = Role.RadioButton,
                                onClick = { selected = q },
                            )
                            .padding(8.dp),
                    ) {
                        RadioButton(selected = selected == q, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("下载") } },
    )
}
