package com.hank.musicfree.feature.settings.setcustomtheme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeColors
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.theme.runtime.CONFIGURABLE_COLOR_KEYS
import com.hank.musicfree.core.theme.runtime.toHexString
import com.hank.musicfree.core.ui.FidelityAnchors

@Composable
fun ConfigurableColorGrid(
    colors: MusicFreeColors,
    onColorClicked: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain Column + two-column rows: keeps this composable usable inside a
    // verticalScroll parent without the LazyVerticalGrid measurement conflict.
    val keys = CONFIGURABLE_COLOR_KEYS
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(24)),
    ) {
        keys.chunked(2).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rpx(24)),
            ) {
                rowKeys.forEach { key ->
                    Box(modifier = Modifier.weight(1f)) {
                        ColorItem(
                            key = key,
                            currentColor = colors.byKey(key),
                            onClick = { onColorClicked(key) },
                        )
                    }
                }
                // Pad an odd trailing cell so the last item keeps left-alignment.
                if (rowKeys.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorItem(
    key: String,
    currentColor: Color,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = colorKeyToLabel(key),
            fontSize = FontSizes.description,
            color = MusicFreeTheme.colors.text,
        )
        Spacer(modifier = Modifier.height(rpx(8)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = rpx(76), height = rpx(50))
                    .border(1.dp, Color.LightGray)
                    .clickable(onClick = onClick)
                    .testTag(FidelityAnchors.SetCustomTheme.ColorItemPrefix + key),
            ) {
                CheckerboardBackground(modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(currentColor),
                )
            }
            Spacer(modifier = Modifier.width(rpx(8)))
            Text(
                text = currentColor.toHexString(),
                fontSize = FontSizes.tag,
                color = MusicFreeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val tile = 6.dp.toPx()
        val cols = (size.width / tile).toInt() + 1
        val rows = (size.height / tile).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val dark = (r + c) % 2 == 0
                drawRect(
                    color = if (dark) Color(0xFFCCCCCC) else Color(0xFFFFFFFF),
                    topLeft = Offset(c * tile, r * tile),
                    size = Size(tile, tile),
                )
            }
        }
    }
}

internal fun colorKeyToLabel(key: String): String = when (key) {
    "primary" -> "主色调"
    "text" -> "文字"
    "appBar" -> "顶栏"
    "appBarText" -> "顶栏文字"
    "musicBar" -> "底栏"
    "musicBarText" -> "底栏文字"
    "pageBackground" -> "页面背景"
    "backdrop" -> "蒙层"
    "card" -> "卡片"
    "placeholder" -> "占位"
    "tabBar" -> "Tab 栏"
    "notification" -> "通知"
    else -> key
}

internal fun MusicFreeColors.byKey(key: String): Color = when (key) {
    "primary" -> primary
    "text" -> text
    "appBar" -> appBar
    "appBarText" -> appBarText
    "musicBar" -> musicBar
    "musicBarText" -> musicBarText
    "pageBackground" -> pageBackground
    "backdrop" -> backdrop
    "card" -> card
    "placeholder" -> placeholder
    "tabBar" -> tabBar
    "notification" -> notification
    else -> primary
}
