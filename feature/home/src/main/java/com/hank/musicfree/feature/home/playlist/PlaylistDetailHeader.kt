package com.hank.musicfree.feature.home.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.R as CoreR
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.ui.CoverImage

@Composable
fun PlaylistDetailHeader(
    playlist: Playlist,
    musicCount: Int,
    onPlayAll: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth().padding(16.dp),
    ) {
        if (playlist.isDefault && playlist.coverUri == null) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_playlist_favorite_cover),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
        } else {
            CoverImage(
                uri = playlist.coverUri,
                size = 160.dp,
                cornerRadius = 12.dp,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f).height(160.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = playlist.description
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${musicCount} 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlayAll) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_play_circle),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部")
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onSearch) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_magnifying_glass),
                        contentDescription = "搜索",
                    )
                }
            }
        }
    }
}
