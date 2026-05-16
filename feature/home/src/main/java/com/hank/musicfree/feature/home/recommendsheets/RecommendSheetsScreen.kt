package com.hank.musicfree.feature.home.recommendsheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.horizontalTabSwipe
import com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityTabs
import com.hank.musicfree.plugin.api.MusicSheetItemBase

@OptIn(ExperimentalMaterial3Api::class)
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
    val selectedPluginIndex = plugins.indexOfFirst { it.platform == selectedPlugin }

    MusicFreeScreenScaffold(
        title = "推荐歌单",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.RecommendSheetsRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (plugins.isEmpty()) {
                EmptyState("暂无已安装插件，请先在设置中安装插件")
            } else {
                PluginCapabilityTabs(
                    plugins = plugins,
                    selectedPlatform = selectedPlugin,
                    onSelectPlugin = viewModel::selectPlugin,
                )

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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalTabSwipe(
                            selectedIndex = selectedPluginIndex,
                            pageCount = plugins.size,
                            enabled = selectedPluginIndex >= 0,
                            onSelectIndex = { index -> viewModel.selectPlugin(plugins[index].platform) },
                        ),
                ) {
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

                        uiState.sheets.isEmpty() && !uiState.emptyMessage.isNullOrBlank() -> {
                            EmptyState(uiState.emptyMessage ?: "当前没有支持推荐歌单的插件")
                        }

                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                                verticalArrangement = Arrangement.spacedBy(rpx(18)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = rpx(24),
                                    vertical = rpx(18),
                                ),
                            ) {
                                lazyGridItems(
                                    items = uiState.sheets,
                                    key = { item -> "${item.platform}:${item.id}" },
                                ) { item ->
                                    RecommendSheetGridItem(
                                        item = item,
                                        onClick = {
                                            val platform = selectedPlugin
                                            if (!platform.isNullOrBlank()) {
                                                onOpenSheetDetail(platform, item)
                                            }
                                        },
                                    )
                                }

                                if (!uiState.isEnd) {
                                    item(
                                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                                    ) {
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
        }
    }
}

@Composable
private fun RecommendSheetGridItem(
    item: MusicSheetItemBase,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(FidelityAnchors.RecommendSheets.Item),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            CoverImage(
                uri = item.artwork ?: item.coverImg,
                size = maxWidth,
                cornerRadius = rpx(12),
            )
        }
        Text(
            text = item.title ?: "未命名歌单",
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.subTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = rpx(12)),
        )
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
