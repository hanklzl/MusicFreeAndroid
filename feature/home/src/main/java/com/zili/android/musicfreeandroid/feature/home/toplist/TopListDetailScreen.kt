package com.zili.android.musicfreeandroid.feature.home.toplist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopListDetailScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopListDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = uiState.title,
                    color = MusicFreeTheme.colors.appBarText,
                    fontSize = FontSizes.appBar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MusicFreeTheme.colors.appBarText,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MusicFreeTheme.colors.appBar,
            ),
        )

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
                                .clickable {
                                    scope.launch {
                                        val ok = viewModel.playAt(index)
                                        if (ok) {
                                            onNavigateToPlayer()
                                        }
                                    }
                                }
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
