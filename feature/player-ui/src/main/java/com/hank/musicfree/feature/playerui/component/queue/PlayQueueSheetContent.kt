package com.hank.musicfree.feature.playerui.component.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import com.hank.musicfree.core.model.PlaybackMode
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.playerui.playerModeDescription
import com.hank.musicfree.feature.playerui.playerModeIcon

@Composable
fun PlayQueueSheetContent(
    uiModel: PlayQueueUiModel,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag(FidelityAnchors.Player.Queue.SheetRoot)
            .semantics { testTagsAsResourceId = true },
    ) {
        QueueHeader(
            count = uiModel.count,
            playbackMode = uiModel.playbackMode,
            onCyclePlaybackMode = onCyclePlaybackMode,
            onClear = onClear,
        )
        if (uiModel.isEmpty) {
            QueueEmptyState()
        } else {
            QueueList(
                uiModel = uiModel,
                onPlayIndex = onPlayIndex,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun QueueHeader(
    count: Int,
    playbackMode: PlaybackMode,
    onCyclePlaybackMode: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(80))
            .padding(horizontal = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "播放列表 ",
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "(${count}首)",
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.title,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconTextButton(
            iconPainter = painterResource(playerModeIcon(playbackMode)),
            label = playerModeDescription(playbackMode),
            onClick = onCyclePlaybackMode,
            modifier = Modifier.testTag(FidelityAnchors.Player.Queue.RepeatModeButton),
        )
        Spacer(Modifier.size(rpx(16)))
        IconTextButton(
            icon = Icons.Outlined.DeleteOutline,
            label = "清空",
            onClick = onClear,
            modifier = Modifier.testTag(FidelityAnchors.Player.Queue.ClearButton),
        )
    }
}

@Composable
private fun QueueList(
    uiModel: PlayQueueUiModel,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(uiModel.items.size, uiModel.currentIndex) {
        if (!didInitialScroll &&
            uiModel.items.isNotEmpty() &&
            uiModel.currentIndex >= 0
        ) {
            listState.scrollToItem(uiModel.currentIndex)
            didInitialScroll = true
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(uiModel.items, key = { _, item -> "${item.platform}:${item.id}" }) { index, item ->
            PlayQueueRow(
                item = item,
                isCurrent = index == uiModel.currentIndex,
                onPlay = { onPlayIndex(index) },
                onRemove = { onRemove(index) },
            )
        }
    }
}

@Composable
private fun QueueEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rpx(120))
            .testTag(FidelityAnchors.Player.Queue.EmptyState),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "暂无歌曲",
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.content,
        )
    }
}

@Composable
private fun IconTextButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = rpx(8), vertical = rpx(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(rpx(36)),
            )
            iconPainter != null -> Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(rpx(36)),
            )
        }
        Spacer(Modifier.size(rpx(4)))
        Text(
            text = label,
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.description,
        )
    }
}
