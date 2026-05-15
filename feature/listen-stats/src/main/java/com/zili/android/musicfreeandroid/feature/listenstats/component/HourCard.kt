package com.zili.android.musicfreeandroid.feature.listenstats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.HourBucket

@Composable
fun HourCard(buckets: List<HourBucket>, modifier: Modifier = Modifier) {
    val byHour: Map<Int, Long> = (0..23).associateWith { h -> buckets.firstOrNull { it.hourOfDay == h }?.seconds ?: 0L }
    val maxSec = (byHour.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val peakHour = byHour.maxByOrNull { it.value }?.key
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("听歌时段", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "你最常在 ${peakHour ?: "—"}:00 听歌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..23).forEach { h ->
                    val ratio = (byHour[h]!!.toFloat() / maxSec).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = (0.1f + ratio * 0.9f).coerceAtMost(1f))),
                    )
                }
            }
        }
    }
}
