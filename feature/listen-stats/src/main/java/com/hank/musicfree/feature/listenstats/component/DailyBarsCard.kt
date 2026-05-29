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
import com.hank.musicfree.data.repository.listenstats.model.DailyBucket
import java.time.LocalDate

private val ChartHeight = 120.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBarsCard(
    daily: List<DailyBucket>,
    modifier: Modifier = Modifier,
) {
    val maxSec = (daily.maxOfOrNull { it.seconds } ?: 1L).coerceAtLeast(1L)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("每日时长", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "长按查看每日时长",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.height(ChartHeight).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                daily.forEach { b ->
                    val ratio = (b.seconds.toFloat() / maxSec).coerceIn(0f, 1f)
                    val barHeight = ChartHeight * ratio.coerceAtLeast(0.05f)
                    val date = LocalDate.ofEpochDay(b.dayEpochDay)
                    // 外层普通 Box 承担 Row 的 weight，TooltipBox 嵌在内部并用显式尺寸，
                    // 避免 TooltipBox 直接吃 weight 时不参与 Row 权重布局导致柱体塌陷。
                    Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text("${date.monthValue}月${date.dayOfMonth}日 · ${formatListenDuration(b.seconds)}")
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .testTag("daily-bar")
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(8.dp, 8.dp, 4.dp, 4.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }
    }
}
