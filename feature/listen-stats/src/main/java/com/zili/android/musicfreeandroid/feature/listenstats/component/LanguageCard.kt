package com.zili.android.musicfreeandroid.feature.listenstats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.Distribution

private val DISTRIBUTION_SERIES = listOf(0xFFF17D34L, 0xFF5B6236L, 0xFF0A95C8L, 0xFF765847L, 0xFFA5887AL)
    .map { Color(it.toInt()) }

@Composable
fun LanguageCard(distribution: Distribution<String?>, onSegmentClick: (String?) -> Unit, modifier: Modifier = Modifier) {
    DistributionCard("语言分布", "来自插件 tag/language 字段", distribution, onSegmentClick, modifier)
}

@Composable
fun GenreCard(distribution: Distribution<String?>, onRowClick: (String?) -> Unit, modifier: Modifier = Modifier) {
    DistributionCard("音乐风格", "来自插件 genre/style/tags 字段", distribution, onRowClick, modifier)
}

@Composable
private fun DistributionCard(
    title: String,
    subtitle: String,
    distribution: Distribution<String?>,
    onItemClick: (String?) -> Unit,
    modifier: Modifier,
) {
    val total = distribution.buckets.sumOf { it.count }.coerceAtLeast(1)
    val coveragePct = (distribution.coverage * 100).toInt()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("覆盖 $coveragePct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                distribution.buckets.filter { it.key != null }.take(5).forEachIndexed { i, b ->
                    val w = b.count.toFloat() / total
                    Box(
                        modifier = Modifier.fillMaxHeight().weight(w)
                            .background(DISTRIBUTION_SERIES[i % DISTRIBUTION_SERIES.size])
                            .clickable { onItemClick(b.key) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            distribution.buckets.forEachIndexed { i, b ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onItemClick(b.key) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(10.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (b.key == null) Color.LightGray else DISTRIBUTION_SERIES[i % DISTRIBUTION_SERIES.size]),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(b.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val pct = (b.count * 100f / total).toInt()
                    Text("$pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
