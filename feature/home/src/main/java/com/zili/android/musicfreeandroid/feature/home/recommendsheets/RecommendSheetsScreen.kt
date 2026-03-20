package com.zili.android.musicfreeandroid.feature.home.recommendsheets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecommendSheetsScreen(
    onBack: () -> Unit,
    onOpenSheetDetail: (pluginPlatform: String, sheet: MusicSheetItemBase) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendSheetsViewModel = hiltViewModel(),
) {
    val plugins by viewModel.availablePlugins.collectAsStateWithLifecycle()
    val selectedPlugin by viewModel.selectedPlugin.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "推荐歌单",
                    color = MusicFreeTheme.colors.appBarText,
                    fontSize = FontSizes.appBar,
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

        if (plugins.isEmpty()) {
            EmptyState("暂无已安装插件，请先在设置中安装插件")
            return
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            plugins.forEachIndexed { index, plugin ->
                SegmentedButton(
                    selected = selectedPlugin == plugin.platform,
                    onClick = { viewModel.selectPlugin(plugin.platform) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = plugins.size,
                    ),
                ) {
                    Text(
                        text = plugin.platform,
                        fontSize = FontSizes.subTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (uiState.tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.tags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = tag.id == uiState.selectedTagId,
                        onClick = { viewModel.selectTag(tag.id) },
                        label = {
                            Text(
                                text = tag.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        when {
            uiState.loading && uiState.sheets.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                }
            }

            !uiState.errorMessage.isNullOrBlank() && uiState.sheets.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.errorMessage ?: "加载推荐歌单失败",
                        color = MusicFreeTheme.colors.danger,
                        fontSize = FontSizes.content,
                    )
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("重试", color = MusicFreeTheme.colors.primary)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = uiState.sheets,
                        key = { item -> "${item.platform}:${item.id}" },
                    ) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val platform = selectedPlugin
                                    if (!platform.isNullOrBlank()) {
                                        onOpenSheetDetail(platform, item)
                                    }
                                }
                                .padding(horizontal = rpx(24), vertical = rpx(14)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(
                                uri = item.coverImg ?: item.artwork,
                                size = rpx(96),
                                cornerRadius = rpx(10),
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = rpx(18))
                                    .weight(1f),
                            ) {
                                Text(
                                    text = item.title ?: "未命名歌单",
                                    color = MusicFreeTheme.colors.text,
                                    fontSize = FontSizes.content,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val artist = item.artist
                                if (!artist.isNullOrBlank()) {
                                    Text(
                                        text = artist,
                                        color = MusicFreeTheme.colors.textSecondary,
                                        fontSize = FontSizes.description,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
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

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.content,
        )
    }
}
