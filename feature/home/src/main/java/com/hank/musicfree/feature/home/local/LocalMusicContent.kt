package com.hank.musicfree.feature.home.local

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.CoverImage

sealed interface LocalMusicUiState {
    data object Loading : LocalMusicUiState
    data class Success(val musicItems: List<MusicItem>) : LocalMusicUiState
    data class Error(val message: String) : LocalMusicUiState
}

@Composable
fun LocalMusicContent(
    uiState: LocalMusicUiState,
    onItemClick: (MusicItem, List<MusicItem>) -> Unit,
    onItemLongClick: (MusicItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    downloadedKeys: Set<String> = emptySet(),
) {
    when (uiState) {
        LocalMusicUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
            }
        }
        is LocalMusicUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.message, color = MusicFreeTheme.colors.danger)
                    Spacer(Modifier.height(8.dp))
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重试",
                            tint = MusicFreeTheme.colors.primary,
                        )
                    }
                }
            }
        }
        is LocalMusicUiState.Success -> {
            if (uiState.musicItems.isEmpty()) {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到本地音乐", color = MusicFreeTheme.colors.textSecondary)
                }
            } else {
                MusicList(
                    items = uiState.musicItems,
                    downloadedKeys = downloadedKeys,
                    onItemClick = { item -> onItemClick(item, uiState.musicItems) },
                    onItemLongClick = onItemLongClick,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun MusicList(
    items: List<MusicItem>,
    downloadedKeys: Set<String>,
    onItemClick: (MusicItem) -> Unit,
    onItemLongClick: (MusicItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                text = "${items.size} 首本地音乐",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
            )
        }
        itemsIndexed(items, key = { _, item -> "${item.platform}:${item.id}" }) { index, item ->
            MusicListItem(
                item = item,
                downloaded = downloadedKeys.contains("${item.id}@${item.platform}"),
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
            )
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MusicFreeTheme.colors.divider,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicListItem(
    item: MusicItem,
    downloaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(uri = item.artwork, size = 48.dp, cornerRadius = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = MusicFreeTheme.colors.text,
                    fontSize = FontSizes.content,
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
            }
            if (item.artist.isNotBlank()) {
                Text(
                    text = item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else "",
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
