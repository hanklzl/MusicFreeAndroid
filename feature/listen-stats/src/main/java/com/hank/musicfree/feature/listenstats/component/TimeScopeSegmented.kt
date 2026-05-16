package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.TimeScope

@Composable
fun TimeScopeSegmented(current: TimeScope, onChange: (TimeScope) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        TimeScope.DAY to "日", TimeScope.WEEK to "周",
        TimeScope.MONTH to "月", TimeScope.YEAR to "年",
        TimeScope.ALL_TIME to "总计",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(4.dp)) {
            items.forEach { (scope, label) ->
                val selected = scope == current
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    onClick = { onChange(scope) },
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
