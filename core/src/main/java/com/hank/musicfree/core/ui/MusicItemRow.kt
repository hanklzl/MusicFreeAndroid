package com.hank.musicfree.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.R
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.MusicFreeTheme

enum class MusicItemRowPlaybackState {
    None,
    CurrentPlaying,
    CurrentPaused,
}

@Composable
fun MusicItemRow(
    item: MusicItem,
    isFavorite: Boolean,
    actions: Set<MusicItemAction>,
    onClick: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    playbackState: MusicItemRowPlaybackState = MusicItemRowPlaybackState.None,
) {
    val primary = MusicFreeTheme.colors.primary
    val isCurrent = playbackState != MusicItemRowPlaybackState.None
    val rowBackground = if (isCurrent) primary.copy(alpha = 0.10f) else Color.Transparent
    val stateDescriptionText = when (playbackState) {
        MusicItemRowPlaybackState.CurrentPlaying -> "当前歌曲，播放中"
        MusicItemRowPlaybackState.CurrentPaused -> "当前歌曲，已暂停"
        MusicItemRowPlaybackState.None -> null
    }
    val rowPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag("MusicItemRow_root")
            .fillMaxWidth()
            .then(
                if (stateDescriptionText != null) {
                    Modifier.semantics { stateDescription = stateDescriptionText }
                } else {
                    Modifier
                },
            )
            .background(rowBackground)
            .drawBehind {
                if (isCurrent) {
                    val widthPx = 3.dp.toPx()
                    val verticalInset = 10.dp.toPx()
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(0f, verticalInset),
                        size = Size(widthPx, size.height - verticalInset * 2),
                        cornerRadius = CornerRadius(widthPx, widthPx),
                    )
                }
            }
            .clickable(onClick = onClick)
            .padding(rowPadding),
    ) {
        CoverImage(uri = item.artwork, size = 40.dp, cornerRadius = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tagText = platformTagText(item.platform)
                if (isCurrent) {
                    CurrentPlaybackWave(
                        isAnimating = playbackState == MusicItemRowPlaybackState.CurrentPlaying,
                        modifier = Modifier.testTag(
                            if (playbackState == MusicItemRowPlaybackState.CurrentPlaying) {
                                "MusicItemRow_current_playing_wave"
                            } else {
                                "MusicItemRow_current_paused_wave"
                            },
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent) primary else Color.Unspecified,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (downloaded) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "已下载",
                        tint = MusicFreeTheme.colors.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (tagText != null) {
                    PlatformTag(
                        text = tagText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = descriptionText(item),
                style = MaterialTheme.typography.bodySmall,
                color = MusicFreeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicItemMoreMenu(
            actions = actions,
            isFavorite = isFavorite,
            onAction = onAction,
            triggerIcon = painterResource(id = R.drawable.ic_ellipsis_vertical),
        )
    }
}

internal fun platformTagText(platform: String): String? {
    val normalized = platform.trim()
    if (normalized.isBlank()) return null
    return if (normalized == "local") "本地" else normalized
}

private fun descriptionText(item: MusicItem): String =
    item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else ""

@Composable
private fun CurrentPlaybackWave(
    isAnimating: Boolean,
    modifier: Modifier = Modifier,
) {
    val middleHeight = if (isAnimating) {
        val transition = rememberInfiniteTransition(label = "current_playback_wave")
        val animatedHeight by transition.animateFloat(
            initialValue = 14f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "current_playback_wave_middle",
        )
        animatedHeight
    } else {
        14f
    }

    Row(
        modifier = modifier.size(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        WaveBar(heightDp = 8f, alpha = if (isAnimating) 1f else 0.66f)
        WaveBar(heightDp = middleHeight, alpha = if (isAnimating) 1f else 0.66f)
        WaveBar(heightDp = 10f, alpha = if (isAnimating) 1f else 0.66f)
    }
}

@Composable
private fun WaveBar(
    heightDp: Float,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .testTag("MusicItemRow_current_wave_bar")
            .width(3.dp)
            .height(heightDp.dp)
            .background(
                color = MusicFreeTheme.colors.primary.copy(alpha = alpha),
                shape = RoundedCornerShape(2.dp),
            ),
    )
}
