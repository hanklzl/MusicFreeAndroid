package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

@Composable
fun MiniPlayerContent(
    uiModel: MiniPlayerUiModel,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MusicFreeTheme.colors.musicBar)
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(FidelityAnchors.Player.MiniRoot),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            uri = uiModel.coverUri,
            size = 48.dp,
            cornerRadius = 24.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiModel.title,
                color = MusicFreeTheme.colors.musicBarText,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = uiModel.subtitle,
                color = MusicFreeTheme.colors.musicBarText.copy(alpha = 0.6f),
                fontSize = FontSizes.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.testTag(FidelityAnchors.Player.MiniPlayPause),
        ) {
            Icon(
                imageVector = if (uiModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (uiModel.isPlaying) "暂停" else "播放",
                tint = MusicFreeTheme.colors.musicBarText,
                modifier = Modifier.size(32.dp),
            )
        }
        if (uiModel.showQueueButton) {
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.testTag(FidelityAnchors.Player.MiniQueue),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "播放队列",
                    tint = MusicFreeTheme.colors.musicBarText,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
