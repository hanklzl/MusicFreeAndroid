package com.zili.android.musicfreeandroid.feature.home.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.DownloadQualityDialog
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold
import com.zili.android.musicfreeandroid.core.ui.MusicItemOptionsSheet

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearchMusicList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var optionsItem by remember { mutableStateOf<MusicItem?>(null) }
    var qualityFor by remember { mutableStateOf<MusicItem?>(null) }
    val defaultQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle(initialValue = PlayQuality.STANDARD)

    MusicFreeScreenScaffold(
        title = "播放历史",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.HistoryRoot)
            .semantics { testTagsAsResourceId = true },
        actions = {
            IconButton(onClick = onNavigateToSearchMusicList) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MusicFreeTheme.colors.appBarText,
                )
            }
            if (history.isNotEmpty()) {
                IconButton(onClick = viewModel::clearHistory) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空历史",
                        tint = MusicFreeTheme.colors.appBarText,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无播放历史",
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.content,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(history, key = { _, item -> "${item.platform}:${item.id}" }) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { if (viewModel.playAt(index)) onNavigateToPlayer() },
                                    onLongClick = { optionsItem = item },
                                )
                                .padding(horizontal = rpx(24), vertical = rpx(16)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                color = MusicFreeTheme.colors.textSecondary,
                                fontSize = FontSizes.subTitle,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title.ifBlank { "未知歌曲" },
                                    color = MusicFreeTheme.colors.text,
                                    fontSize = FontSizes.content,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.artist.ifBlank { "未知歌手" },
                                    color = MusicFreeTheme.colors.textSecondary,
                                    fontSize = FontSizes.description,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
