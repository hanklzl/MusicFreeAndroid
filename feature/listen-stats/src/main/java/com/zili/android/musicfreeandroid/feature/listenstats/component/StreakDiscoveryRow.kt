package com.zili.android.musicfreeandroid.feature.listenstats.component

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StreakDiscoveryRow(
    streakDays: Int,
    maxStreak: Int,
    firstSeenCount: Int,
    onStreakClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakCell("$streakDays", "天", "最长 $maxStreak 天", "连续听歌", Modifier.weight(1f), onStreakClick)
        StreakCell("$firstSeenCount", "首", "本时段首次听到", "新发现", Modifier.weight(1f), onDiscoveryClick)
    }
}

@Composable
private fun StreakCell(
    value: String,
    unit: String,
    caption: String,
    title: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text(unit, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
