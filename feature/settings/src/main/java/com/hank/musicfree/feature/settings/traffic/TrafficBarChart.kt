package com.hank.musicfree.feature.settings.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TrafficBarChart(
    bars: List<TrafficBar>,
    modifier: Modifier = Modifier,
    wifiColor: Color = Color(0xFF2196F3),
    cellularColor: Color = Color(0xFFFF9800),
    otherColor: Color = Color(0xFF9E9E9E),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            if (bars.isEmpty()) return@Canvas
            val maxBytes = bars.maxOf { it.wifiBytes + it.cellularBytes + it.otherBytes }
                .coerceAtLeast(1L)
            val w = size.width / bars.size
            val barW = (w * 0.7f).coerceAtMost(48f)
            bars.forEachIndexed { i, b ->
                val xCenter = i * w + w / 2f
                val xLeft = xCenter - barW / 2f
                val total = b.wifiBytes + b.cellularBytes + b.otherBytes
                if (total == 0L) return@forEachIndexed
                val totalH = (total.toFloat() / maxBytes) * size.height
                var y = size.height
                listOf(
                    b.otherBytes to otherColor,
                    b.cellularBytes to cellularColor,
                    b.wifiBytes to wifiColor,
                ).forEach { (bytes, color) ->
                    if (bytes == 0L) return@forEach
                    val h = (bytes.toFloat() / total) * totalH
                    drawRect(
                        color = color,
                        topLeft = Offset(xLeft, y - h),
                        size = Size(barW, h),
                    )
                    y -= h
                }
            }
        }
    }
}
