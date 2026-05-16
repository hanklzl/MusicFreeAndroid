package com.hank.musicfree.feature.home.musicdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.core.ui.DownloadQualityDialog
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.plugin.api.MusicComment
import kotlinx.coroutines.launch

@Composable
fun MusicDetailScreen(
    onBack: () -> Unit,
    onOpenAlbumDetail: (MusicItem) -> Unit,
    onOpenArtistDetail: (MusicItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showQualityDialog by remember { mutableStateOf(false) }
    var defaultQuality by remember { mutableStateOf(PlayQuality.STANDARD) }

    MusicFreeScreenScaffold(
        title = uiState.musicItem?.title ?: "歌曲详情",
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
        when {
            uiState.loading && uiState.musicItem == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(rpx(12)),
                ) {
                    uiState.errorMessage?.let { message ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = rpx(24), vertical = rpx(12)),
                            ) {
                                Text(
                                    text = message,
                                    color = MusicFreeTheme.colors.danger,
                                    fontSize = FontSizes.content,
                                )
                                TextButton(onClick = viewModel::retry) {
                                    Text("重试", color = MusicFreeTheme.colors.primary)
                                }
                            }
                        }
                    }

                    uiState.musicItem?.let { item ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = rpx(24), vertical = rpx(8)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                            ) {
                                CoverImage(
                                    uri = item.artwork,
                                    size = rpx(136),
                                    cornerRadius = rpx(12),
                                )
                                Column(modifier = Modifier.weight(1f)) {
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
                                    val album = item.album
                                    if (!album.isNullOrBlank()) {
                                        Text(
                                            text = album,
                                            color = MusicFreeTheme.colors.textSecondary,
                                            fontSize = FontSizes.description,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = rpx(24)),
                                horizontalArrangement = Arrangement.spacedBy(rpx(24)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { onOpenAlbumDetail(item) }) {
                                    Text(
                                        text = "专辑预览: ${uiState.albumPreviewCount?.toString() ?: "--"} 首",
                                        color = MusicFreeTheme.colors.primary,
                                        fontSize = FontSizes.description,
                                    )
                                }
                                TextButton(onClick = { onOpenArtistDetail(item) }) {
                                    Text(
                                        text = "歌手作品: ${uiState.artistPreviewCount?.toString() ?: "--"} 首",
                                        color = MusicFreeTheme.colors.primary,
                                        fontSize = FontSizes.description,
                                    )
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        defaultQuality = viewModel.preferredDownloadQuality()
                                        showQualityDialog = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "下载",
                                        tint = MusicFreeTheme.colors.primary,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "歌词",
                            color = MusicFreeTheme.colors.text,
                            fontSize = FontSizes.subTitle,
                            modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(4)),
                        )
                    }

                    if (uiState.lyricLines.isEmpty()) {
                        item {
                            Text(
                                text = "暂无歌词",
                                color = MusicFreeTheme.colors.textSecondary,
                                fontSize = FontSizes.description,
                                modifier = Modifier.padding(horizontal = rpx(24)),
                            )
                        }
                    } else {
                        items(uiState.lyricLines.take(12), key = { it.timeMs }) { line ->
                            Text(
                                text = "${formatLyricTime(line.timeMs)}  ${line.text.ifBlank { "..." }}",
                                color = MusicFreeTheme.colors.textSecondary,
                                fontSize = FontSizes.description,
                                modifier = Modifier.padding(horizontal = rpx(24)),
                            )
                        }
                    }

                    item {
                        Text(
                            text = "评论",
                            color = MusicFreeTheme.colors.text,
                            fontSize = FontSizes.subTitle,
                            modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(4)),
                        )
                    }

                    if (uiState.comments.isEmpty()) {
                        item {
                            Text(
                                text = "暂无评论",
                                color = MusicFreeTheme.colors.textSecondary,
                                fontSize = FontSizes.description,
                                modifier = Modifier.padding(horizontal = rpx(24)),
                            )
                        }
                    } else {
                        items(
                            items = uiState.comments,
                            key = { it.id ?: "comment-${it.nickName}-${it.comment.hashCode()}" },
                        ) { comment ->
                            CommentItem(comment = comment)
                        }
                    }

                    item {
                        Text(
                            text = if (uiState.commentsIsEnd) "评论已到底" else "评论可继续分页（待接线）",
                            color = MusicFreeTheme.colors.textSecondary,
                            fontSize = FontSizes.description,
                            modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(16)),
                        )
                    }
                }
            }
        }
        }
    }

    if (showQualityDialog) {
        DownloadQualityDialog(
            initial = defaultQuality,
            onDismiss = { showQualityDialog = false },
            onConfirm = { q ->
                viewModel.download(q)
                showQualityDialog = false
            },
        )
    }
}

@Composable
private fun CommentItem(comment: MusicComment) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(6)),
    ) {
        Text(
            text = comment.nickName.ifBlank { "匿名用户" },
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.description,
        )
        Text(
            text = comment.comment.ifBlank { "（空评论）" },
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.description,
            modifier = Modifier.padding(top = rpx(4)),
        )
        if (comment.replies.isNotEmpty()) {
            Text(
                text = "回复 ${comment.replies.size} 条",
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
                modifier = Modifier.padding(top = rpx(2)),
            )
        }
    }
}

private fun formatLyricTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minute = totalSeconds / 60
    val second = totalSeconds % 60
    return "%02d:%02d".format(minute, second)
}
