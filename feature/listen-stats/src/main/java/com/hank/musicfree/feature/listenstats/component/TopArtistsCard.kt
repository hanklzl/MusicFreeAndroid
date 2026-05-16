package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.db.dao.TopArtistRow

@Composable
fun TopArtistsCard(
    rows: List<TopArtistRow>,
    onSeeAll: () -> Unit,
    onRowClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Top 歌手", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("按播放次数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            rows.take(5).forEachIndexed { idx, row ->
                val rank = idx + 1
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onRowClick(row.artistName) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$rank",
                        Modifier.width(28.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(row.artistName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "出现在 ${row.songCount} 首歌中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Text("${row.playCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Text(
                "查看全部 Top 50",
                Modifier.fillMaxWidth().padding(8.dp).clickable { onSeeAll() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
