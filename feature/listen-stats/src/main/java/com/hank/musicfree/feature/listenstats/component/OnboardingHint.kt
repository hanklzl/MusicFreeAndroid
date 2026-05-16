package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun OnboardingHint(firstEventDate: LocalDate?, modifier: Modifier = Modifier) {
    if (firstEventDate == null) return
    Text(
        text = "开始统计于 ${firstEventDate.year} 年 ${firstEventDate.monthValue} 月 ${firstEventDate.dayOfMonth} 日",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}
