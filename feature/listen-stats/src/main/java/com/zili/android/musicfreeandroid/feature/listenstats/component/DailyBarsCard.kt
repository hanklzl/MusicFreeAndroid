package com.zili.android.musicfreeandroid.feature.listenstats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DailyBucket

@Composable
fun DailyBarsCard(
    daily: List<DailyBucket>,
    onBarClick: (Long) -> Unit,
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
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.height(120.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                daily.forEach { b ->
                    val ratio = (b.seconds.toFloat() / maxSec).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(ratio.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(8.dp, 8.dp, 4.dp, 4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onBarClick(b.dayEpochDay) },
                    )
                }
            }
        }
    }
}
