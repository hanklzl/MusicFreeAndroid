package com.hank.musicfree.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.Playlist

@Composable
fun AddToPlaylistBottomSheetContent(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
    folderPlusIcon: Painter,
    favoriteCoverIcon: Painter,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreateNew)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("AddToPlaylist_CreateNew"),
        ) {
            Icon(painter = folderPlusIcon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("新建歌单", style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider()
        LazyColumn {
            items(items = playlists, key = { it.id }) { playlist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(playlist) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("AddToPlaylist_Row_${playlist.id}"),
                ) {
                    if (playlist.isDefault && playlist.coverUri == null) {
                        Icon(painter = favoriteCoverIcon, contentDescription = null)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${playlist.worksNum} 首",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
