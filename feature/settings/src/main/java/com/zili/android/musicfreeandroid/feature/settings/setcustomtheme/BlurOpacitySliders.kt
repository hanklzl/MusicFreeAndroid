package com.zili.android.musicfreeandroid.feature.settings.setcustomtheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

@Composable
fun BlurOpacitySliders(
    blur: Float,
    opacity: Float,
    onBlurChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        SliderRow(
            label = "模糊",
            value = blur,
            valueRange = 0f..30f,
            steps = 29,
            displayValue = "${blur.toInt()}",
            testTag = FidelityAnchors.SetCustomTheme.SliderBlur,
            onValueChange = onBlurChange,
        )
        SliderRow(
            label = "不透明度",
            value = opacity,
            valueRange = 0.3f..1f,
            steps = 0,
            displayValue = "${(opacity * 100).toInt()}%",
            testTag = FidelityAnchors.SetCustomTheme.SliderOpacity,
            onValueChange = onOpacityChange,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    testTag: String,
    onValueChange: (Float) -> Unit,
) {
    // Local draft so the slider feels responsive while only landing the final
    // value via onValueChangeFinished (writes go through DataStore).
    var draft by remember(value) { mutableFloatStateOf(value) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        Text(
            text = label,
            fontSize = FontSizes.content,
            color = MusicFreeTheme.colors.text,
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onValueChange(draft) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = MusicFreeTheme.colors.primary,
                activeTrackColor = MusicFreeTheme.colors.primary,
                inactiveTrackColor = MusicFreeTheme.colors.placeholder,
            ),
        )
        Text(
            text = displayValue,
            fontSize = FontSizes.description,
            color = MusicFreeTheme.colors.textSecondary,
        )
    }
}
