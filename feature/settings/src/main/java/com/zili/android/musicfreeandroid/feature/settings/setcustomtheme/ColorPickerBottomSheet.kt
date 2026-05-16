package com.zili.android.musicfreeandroid.feature.settings.setcustomtheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.theme.runtime.parseHexColor
import com.zili.android.musicfreeandroid.core.theme.runtime.toHexString
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

private val PRESET_COLORS: List<Color> = listOf(
    Color(0xFFF17D34),
    Color(0xFF3FA3B5),
    Color(0xFF2196F3),
    Color(0xFF4CAF50),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFFFFC107),
    Color(0xFF000000),
    Color(0xFFFFFFFF),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerBottomSheet(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (hex: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var red by remember { mutableFloatStateOf((initialColor.red * 255f)) }
    var green by remember { mutableFloatStateOf((initialColor.green * 255f)) }
    var blue by remember { mutableFloatStateOf((initialColor.blue * 255f)) }
    var alpha by remember { mutableFloatStateOf((initialColor.alpha * 255f)) }
    var hexInput by remember { mutableStateOf(initialColor.toHexString()) }

    val current = Color(
        red = red.toInt().coerceIn(0, 255) / 255f,
        green = green.toInt().coerceIn(0, 255) / 255f,
        blue = blue.toInt().coerceIn(0, 255) / 255f,
        alpha = alpha.toInt().coerceIn(0, 255) / 255f,
    )
    val currentHex = current.toHexString()

    fun applyColor(color: Color) {
        red = color.red * 255f
        green = color.green * 255f
        blue = color.blue * 255f
        alpha = color.alpha * 255f
        hexInput = color.toHexString()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MusicFreeTheme.colors.pageBackground,
        modifier = Modifier.testTag(FidelityAnchors.SetCustomTheme.ColorPickerSheet),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = rpx(24), vertical = rpx(16)),
            verticalArrangement = Arrangement.spacedBy(rpx(12)),
        ) {
            // Preview swatch + hex string.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rpx(16)),
            ) {
                Box(
                    modifier = Modifier
                        .size(rpx(80))
                        .clip(RoundedCornerShape(rpx(8)))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(rpx(8)))
                        .background(current),
                )
                Text(
                    text = currentHex,
                    fontSize = FontSizes.content,
                    fontWeight = FontWeight.Medium,
                    color = MusicFreeTheme.colors.text,
                )
            }

            ChannelSlider("R", red, Color(0xFFE53935)) { red = it; hexInput = currentSnapshot(red, green, blue, alpha) }
            ChannelSlider("G", green, Color(0xFF43A047)) { green = it; hexInput = currentSnapshot(red, green, blue, alpha) }
            ChannelSlider("B", blue, Color(0xFF1E88E5)) { blue = it; hexInput = currentSnapshot(red, green, blue, alpha) }
            ChannelSlider("A", alpha, MusicFreeTheme.colors.textSecondary) { alpha = it; hexInput = currentSnapshot(red, green, blue, alpha) }

            // Preset palette row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rpx(12)),
            ) {
                PRESET_COLORS.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .size(rpx(56))
                            .clip(CircleShape)
                            .border(1.dp, Color.LightGray, CircleShape)
                            .background(preset)
                            .clickable { applyColor(preset) },
                    )
                }
            }

            // Hex text input: parses on edit, falls back silently on bad input
            // so the slider state stays authoritative.
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    hexInput = raw
                    parseHexColor(raw)?.let { applyColor(it) }
                },
                label = { Text("HEX") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(rpx(8)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MusicFreeTheme.colors.textSecondary)
                }
                TextButton(
                    onClick = { onConfirm(currentHex) },
                    modifier = Modifier.testTag(FidelityAnchors.SetCustomTheme.ColorPickerConfirm),
                ) {
                    Text("确认", color = MusicFreeTheme.colors.primary)
                }
            }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rpx(12)),
    ) {
        Text(
            text = label,
            fontSize = FontSizes.subTitle,
            color = MusicFreeTheme.colors.text,
            modifier = Modifier.width(rpx(36)),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            steps = 0,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MusicFreeTheme.colors.placeholder,
            ),
        )
        Text(
            text = value.toInt().toString(),
            fontSize = FontSizes.description,
            color = MusicFreeTheme.colors.textSecondary,
            modifier = Modifier.width(rpx(64)),
        )
    }
}

// Recompute the hex string after a channel change without re-creating the Color.
private fun currentSnapshot(r: Float, g: Float, b: Float, a: Float): String {
    val color = Color(
        red = r.toInt().coerceIn(0, 255) / 255f,
        green = g.toInt().coerceIn(0, 255) / 255f,
        blue = b.toInt().coerceIn(0, 255) / 255f,
        alpha = a.toInt().coerceIn(0, 255) / 255f,
    )
    return color.toHexString()
}
