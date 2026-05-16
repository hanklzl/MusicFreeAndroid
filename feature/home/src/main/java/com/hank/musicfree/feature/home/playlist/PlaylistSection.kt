package com.hank.musicfree.feature.home.playlist

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.CoverImage

@Composable
fun PlaylistSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onEdit: (Playlist, name: String?, description: String?, coverUri: Uri?) -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = onCreate,
        )
    }

    editTarget?.let { playlist ->
        EditPlaylistDialog(
            playlist = playlist,
            onDismiss = { editTarget = null },
            onSave = { name, description, coverUri ->
                onEdit(playlist, name, description, coverUri)
                editTarget = null
            },
        )
    }

    deleteTarget?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { deleteTarget = null },
            onDelete = { onDelete(playlist) },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${playlists.size} 个播放列表",
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.description,
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建", tint = MusicFreeTheme.colors.primary)
                }
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistListItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
                onEdit = { editTarget = playlist },
                onDelete = { deleteTarget = playlist },
            )
        }
    }
}

@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(uri = playlist.coverUri, size = 56.dp, cornerRadius = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MusicFreeTheme.colors.textSecondary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MusicFreeTheme.colors.danger)
        }
    }
}
