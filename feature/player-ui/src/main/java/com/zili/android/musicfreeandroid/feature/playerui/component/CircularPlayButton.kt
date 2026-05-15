package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx

@Composable
fun CircularPlayButton(
    isPlaying: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diameter = rpx(72)
    val activeStrokeWidth = rpx(4)
    val inactiveStrokeWidth = rpx(2)
    val activeColor = MusicFreeTheme.colors.musicBarText
    val inactiveColor = MusicFreeTheme.colors.textSecondary.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .clickable(onClick = onTogglePlayPause),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = inactiveColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = inactiveStrokeWidth.toPx()),
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = activeStrokeWidth.toPx()),
            )
        }
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = MusicFreeTheme.colors.musicBarText,
        )
    }
}
