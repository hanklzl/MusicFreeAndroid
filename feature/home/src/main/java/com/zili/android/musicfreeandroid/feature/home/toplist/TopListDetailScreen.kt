package com.zili.android.musicfreeandroid.feature.home.toplist

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.DownloadQualityDialog
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold
import com.zili.android.musicfreeandroid.core.ui.MusicItemOptionsSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopListDetailScreen(
    onBack: () -> Unit,
    onOpenMusicDetail: (MusicItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopListDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var optionsItem by remember { mutableStateOf<MusicItem?>(null) }
    var qualityFor by remember { mutableStateOf<MusicItem?>(null) }
    val defaultQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle(initialValue = PlayQuality.STANDARD)

    MusicFreeScreenScaffold(
        title = uiState.title,
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
        when {
            uiState.loading && uiState.musicList.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                }
            }

            !uiState.errorMessage.isNullOrBlank() && uiState.musicList.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.errorMessage ?: "加载失败",
                        color = MusicFreeTheme.colors.danger,
                        fontSize = FontSizes.content,
                    )
                    TextButton(onClick = { viewModel.retry() }) {
                        Text("重试", color = MusicFreeTheme.colors.primary)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
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
                            TextButton(onClick = { onOpenMusicDetail(item) }) {
                                Text("详情", color = MusicFreeTheme.colors.primary)
                            }
                        }
                    }

                    if (!uiState.isEnd) {
                        item {
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
