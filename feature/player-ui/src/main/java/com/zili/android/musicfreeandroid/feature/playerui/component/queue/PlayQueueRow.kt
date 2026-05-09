package com.zili.android.musicfreeandroid.feature.playerui.component.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.PlatformTag

@Composable
internal fun PlayQueueRow(
    item: MusicItem,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // RN spec uses `textHighlight ?? primary`; this codebase has no textHighlight, so use primary directly.
    val highlightColor = MusicFreeTheme.colors.primary
    val titleColor = if (isCurrent) highlightColor else MusicFreeTheme.colors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(108))
            .clickable(onClick = onPlay)
            .padding(horizontal = rpx(24))
            .testTag(FidelityAnchors.Player.Queue.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {}
                    .testTag(FidelityAnchors.Player.Queue.CurrentMarker),
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "当前播放",
                    tint = highlightColor,
                    modifier = Modifier.size(rpx(28)),
                )
            }
            Spacer(Modifier.width(rpx(6)))
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title,
                color = titleColor,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = " - ${item.artist}",
                    color = titleColor,
                    fontSize = FontSizes.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (item.platform.isNotBlank()) {
            Spacer(Modifier.width(rpx(8)))
            PlatformTag(text = item.platform)
        }
        Spacer(Modifier.width(rpx(14)))
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag(FidelityAnchors.Player.Queue.RemoveButton),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除",
                tint = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(rpx(36)),
            )
        }
    }
}
