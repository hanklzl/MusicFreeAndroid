package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.DateBucket

@Composable
fun HeatmapCard(cells: List<DateBucket>, onCellClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val maxSec = (cells.maxOfOrNull { it.seconds } ?: 1L).coerceAtLeast(1L)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("听歌日历", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "仅在「月 / 年 / 总计」视图显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            val perRow = 20
            cells.chunked(perRow).forEach { rowCells ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    rowCells.forEach { c ->
                        val ratio = (c.seconds.toFloat() / maxSec).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = ratio.coerceAtLeast(0.05f)))
                                .clickable { onCellClick(c.dayEpochDay) },
                        )
                    }
                    repeat(perRow - rowCells.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}
