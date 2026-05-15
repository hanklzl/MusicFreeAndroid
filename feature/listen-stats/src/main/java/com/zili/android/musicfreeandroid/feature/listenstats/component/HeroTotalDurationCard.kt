package com.zili.android.musicfreeandroid.feature.listenstats.component

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
fun HeroTotalDurationCard(totalSeconds: Long, scopeLabel: String, modifier: Modifier = Modifier) {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 24.dp)) {
            Text(scopeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$hours", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text(" 小时 ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                Text("$minutes", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text(" 分钟", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
