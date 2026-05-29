package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.HourBucket

@OptIn(ExperimentalMaterial3Api::class)
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
                    // 外层普通 Box 承担 Row 的 weight，TooltipBox 嵌在内部，
                    // 避免 TooltipBox 直接吃 weight 时不参与 Row 权重布局导致 24 根柱体重叠。
                    Box(Modifier.weight(1f)) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text("${h}:00 · ${formatListenDuration(byHour[h]!!)}")
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .testTag("hour-bar")
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = (0.1f + ratio * 0.9f).coerceAtMost(1f),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // 每隔两小时一个刻度；标签用 unbounded 宽度居中，溢出到相邻空槽避免裁切。
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..23).forEach { h ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (h % 2 == 0) {
                            Text(
                                "$h",
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
