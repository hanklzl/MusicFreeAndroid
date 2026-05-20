package com.hank.musicfree.feature.home.albumdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.R
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.core.ui.DownloadQualityDialog
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.MusicItemOptionsSheet
import com.hank.musicfree.core.ui.MusicSheetPageHeader
import com.hank.musicfree.core.ui.PlayAllBar
import com.hank.musicfree.core.ui.favoriteIconTint
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isStarred by viewModel.isAlbumStarred.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var optionsItem by remember { mutableStateOf<MusicItem?>(null) }
    var qualityFor by remember { mutableStateOf<MusicItem?>(null) }
    val defaultQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle(initialValue = PlayQuality.STANDARD)

    MusicFreeScreenScaffold(
        title = uiState.title,
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
        actions = {
            IconButton(onClick = { viewModel.toggleAlbumStarred() }) {
                Icon(
                    painter = painterResource(
                        id = if (isStarred) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                    ),
                    contentDescription = if (isStarred) "取消收藏专辑" else "收藏专辑",
                    tint = favoriteIconTint(
                        starred = isStarred,
                        inactiveTint = MusicFreeTheme.colors.appBarText,
                    ),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.loading && uiState.musicList.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                }
            }

            !uiState.errorMessage.isNullOrBlank() && uiState.musicList.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.errorMessage ?: "加载专辑失败",
                        color = MusicFreeTheme.colors.danger,
                        fontSize = FontSizes.content,
                    )
                    TextButton(onClick = viewModel::retry) {
                        Text("重试", color = MusicFreeTheme.colors.primary)
                    }
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    item(key = "header") {
                        val album = uiState.albumItem
                        val albumCover = album?.artwork ?: album?.raw?.get("coverImg")?.toString()
                        MusicSheetPageHeader(
                            cover = albumCover,
                            title = album?.title ?: uiState.title,
                            worksNum = album?.worksNum,
                            musicListSize = uiState.musicList.size,
                            description = album?.description,
                            actions = {
                                PlayAllBar(
                                    onPlayAll = { viewModel.playAll() },
                                    onAddToPlaylist = {},
                                    starred = isStarred,
                                    onToggleStarred = { viewModel.toggleAlbumStarred() },
                                    showAddToPlaylist = false,
                                )
                            },
                        )
                    }
                    itemsIndexed(
                        items = uiState.musicList,
                        key = { _, item -> "${item.platform}:${item.id}" },
                    ) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        scope.launch {
                                            viewModel.playAt(index)
                                        }
                                    },
                                    onLongClick = { optionsItem = item },
                                )
                                .padding(horizontal = rpx(24), vertical = rpx(12)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = MusicFreeTheme.colors.textSecondary,
                                fontSize = FontSizes.description,
                                modifier = Modifier.padding(end = rpx(12)),
                            )
                            CoverImage(
                                uri = item.artwork,
                                size = rpx(88),
                                cornerRadius = rpx(8),
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = rpx(18))
                                    .weight(1f),
                            ) {
                                Text(
                                    text = item.title,
                                    color = MusicFreeTheme.colors.text,
                                    fontSize = FontSizes.content,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.artist,
                                    color = MusicFreeTheme.colors.textSecondary,
                                    fontSize = FontSizes.description,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    if (!uiState.isEnd) {
                        item(key = "footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = rpx(20)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (uiState.loadingMore) {
                                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                                } else {
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text("加载更多", color = MusicFreeTheme.colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    optionsItem?.let { item ->
        MusicItemOptionsSheet(
            item = item,
            onDismiss = { optionsItem = null },
            onDownload = { qualityFor = it; optionsItem = null },
        )
    }
    qualityFor?.let { item ->
        DownloadQualityDialog(
            initial = defaultQuality,
            onDismiss = { qualityFor = null },
            onConfirm = { q -> viewModel.download(item, q); qualityFor = null },
        )
    }
}
