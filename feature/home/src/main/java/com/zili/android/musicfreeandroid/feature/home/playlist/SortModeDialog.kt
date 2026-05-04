package com.zili.android.musicfreeandroid.feature.home.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.SortMode

private val SortModeLabels = mapOf(
    SortMode.Manual to "手动排序",
    SortMode.Title to "按标题",
    SortMode.Artist to "按艺术家",
    SortMode.Album to "按专辑",
    SortMode.Newest to "最新加入",
    SortMode.Oldest to "最早加入",
)

@Composable
fun SortModeDialog(
    current: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序方式") },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
                        Text(SortModeLabels[mode] ?: mode.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
