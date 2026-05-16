package com.hank.musicfree.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.MusicItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicItemOptionsSheet(
    item: MusicItem,
    onDismiss: () -> Unit,
    onDownload: (item: MusicItem) -> Unit,
    onRemoveFromLocalLibrary: ((item: MusicItem) -> Unit)? = null,
) {
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                text = "${item.title} - ${item.artist}",
                modifier = Modifier.padding(16.dp),
            )
            ListItem(
                headlineContent = { Text("下载") },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDownload(item); onDismiss() },
            )
            if (onRemoveFromLocalLibrary != null) {
                ListItem(
                    headlineContent = { Text("从本地音乐移除") },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onRemoveFromLocalLibrary(item)
                            onDismiss()
                        },
                )
            }
        }
    }
}
