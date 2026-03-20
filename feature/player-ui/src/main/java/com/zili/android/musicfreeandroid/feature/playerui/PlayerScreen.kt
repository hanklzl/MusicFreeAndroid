package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val currentItem = state.currentItem

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicFreeTheme.colors.pageBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = currentItem?.title ?: "",
                            color = MusicFreeTheme.colors.text,
                            fontSize = FontSizes.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (currentItem?.artist?.isNotBlank() == true) {
                            Text(
                                text = currentItem.artist,
                                color = MusicFreeTheme.colors.textSecondary,
                                fontSize = FontSizes.description,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MusicFreeTheme.colors.text,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = currentItem?.artwork,
                    contentDescription = "专辑封面",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.weight(1f))

            SeekBar(
                position = state.position,
                duration = state.duration,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(8.dp))

            PlayControls(
                isPlaying = state.isPlaying,
                repeatMode = state.repeatMode,
                shuffleEnabled = state.shuffleEnabled,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onSkipNext = { viewModel.skipToNext() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isDragging) {
        dragPosition
    } else if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragPosition * duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MusicFreeTheme.colors.primary,
                activeTrackColor = MusicFreeTheme.colors.primary,
                inactiveTrackColor = MusicFreeTheme.colors.divider,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (isDragging) (dragPosition * duration).toLong() else position),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
            )
            Text(
                text = formatDuration(duration),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
            )
        }
    }
}

@Composable
private fun PlayControls(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "随机播放",
                tint = if (shuffleEnabled) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = onSkipPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一曲",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(48.dp),
            )
        }
        IconButton(onClick = onSkipNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一曲",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onCycleRepeatMode) {
            Icon(
                imageVector = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "重复模式",
                tint = if (repeatMode != RepeatMode.OFF) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
