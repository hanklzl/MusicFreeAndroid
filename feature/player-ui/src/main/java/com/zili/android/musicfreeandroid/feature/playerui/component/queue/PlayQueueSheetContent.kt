package com.zili.android.musicfreeandroid.feature.playerui.component.queue

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
import androidx.compose.ui.text.font.FontWeight
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

@Composable
fun PlayQueueSheetContent(
    uiModel: PlayQueueUiModel,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag(FidelityAnchors.Player.Queue.SheetRoot),
    ) {
        QueueHeader(
            count = uiModel.count,
            repeatMode = uiModel.repeatMode,
            onCycleRepeatMode = onCycleRepeatMode,
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
    repeatMode: RepeatMode,
    onCycleRepeatMode: () -> Unit,
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
            iconPainter = painterResource(repeatModeIcon(repeatMode)),
            label = repeatModeLabel(repeatMode),
            onClick = onCycleRepeatMode,
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

private fun repeatModeIcon(mode: RepeatMode): Int = when (mode) {
    RepeatMode.OFF, RepeatMode.ALL -> R.drawable.ic_repeat_song
    RepeatMode.ONE -> R.drawable.ic_repeat_song_1
}

private fun repeatModeLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF -> "顺序播放"
    RepeatMode.ALL -> "列表循环"
    RepeatMode.ONE -> "单曲循环"
}
