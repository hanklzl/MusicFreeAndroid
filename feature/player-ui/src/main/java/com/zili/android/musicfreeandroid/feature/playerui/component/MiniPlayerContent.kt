package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import kotlin.math.abs
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

@Composable
fun MiniPlayerContent(
    uiModel: MiniPlayerUiModel,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenQueue: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(132))
            .background(MusicFreeTheme.colors.musicBar)
            .testTag(FidelityAnchors.Player.MiniRoot)
            .semantics { testTagsAsResourceId = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // MusicInfo area - flex:1, clickable to open player
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOpenPlayer() })
                }
                .pointerInput(Unit) {
                    val width = size.width.toFloat()
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (abs(totalDrag) > width * 0.3f) {
                                if (totalDrag < 0) onSkipNext() else onSkipPrev()
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(rpx(24)))
            // Cover image - circle
            SubcomposeAsyncImage(
                model = uiModel.coverUri,
                contentDescription = null,
                modifier = Modifier
                    .size(rpx(96))
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MusicFreeTheme.colors.placeholder),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = MusicFreeTheme.colors.textSecondary, modifier = Modifier.size(rpx(48)))
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MusicFreeTheme.colors.placeholder),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = MusicFreeTheme.colors.textSecondary, modifier = Modifier.size(rpx(48)))
                    }
                },
            )
            Spacer(Modifier.width(rpx(24)))
            // Single-line title - artist
            Row(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiModel.title,
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.musicBarText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " - ",
                    color = MusicFreeTheme.colors.musicBarText.copy(alpha = 0.6f),
                    fontSize = FontSizes.content,
                )
                Text(
                    text = uiModel.artist,
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.musicBarText.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // Circular play button
        CircularPlayButton(
            isPlaying = uiModel.isPlaying,
            progress = uiModel.progress,
            onTogglePlayPause = onTogglePlayPause,
            modifier = Modifier.testTag(FidelityAnchors.Player.MiniPlayPause),
        )
        // Queue icon
        Spacer(Modifier.width(rpx(36)))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = "播放队列",
            tint = MusicFreeTheme.colors.musicBarText,
            modifier = Modifier
                .clickable(onClick = onOpenQueue)
                .size(rpx(56))
                .testTag(FidelityAnchors.Player.MiniQueue),
        )
        Spacer(Modifier.width(rpx(24)))
    }
}
