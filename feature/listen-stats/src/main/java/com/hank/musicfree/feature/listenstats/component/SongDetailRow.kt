package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.data.repository.listenstats.model.ListenedSong
import java.time.Instant
import java.time.ZoneId

@Composable
fun SongDetailRow(song: ListenedSong, modifier: Modifier = Modifier, showFirstSeen: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            uri = song.artwork,
            size = 44.dp,
            cornerRadius = 10.dp,
            modifier = Modifier.testTag("song-cover"),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                song.artistRaw,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (showFirstSeen) {
                val date = Instant.ofEpochMilli(song.firstSeenMs).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    "${date.monthValue}/${date.dayOfMonth} 首次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "${song.playCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
