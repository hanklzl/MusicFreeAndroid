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
import androidx.compose.runtime.LaunchedEffect
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
import com.hank.musicfree.core.ui.MusicFreeScenePagerTabs
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.ScenePagerPage
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
    val pagerUiState by viewModel.pagerUiState.collectAsStateWithLifecycle()
    val pages = plugins.map { plugin -> ScenePagerPage(key = plugin.platform, label = plugin.label) }

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
                MusicFreeScenePagerTabs(
                    pages = pages,
                    selectedKey = pagerUiState.selectedPlatform,
                    onSelectedKeyChange = viewModel::selectPlugin,
                    edgePadding = 12.dp,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val pageState = pagerUiState.scenes[page.key] ?: RecommendSheetsSceneState()
                    LaunchedEffect(page.key) {
                        viewModel.ensureSceneLoaded(page.key)
                    }

                    RecommendSheetsScene(
                        state = pageState,
                        onSelectTag = { tagId ->
                            viewModel.selectTag(platform = page.key, tagId = tagId)
                        },
                        onRetry = { viewModel.refresh(platform = page.key) },
                        onLoadMore = { viewModel.loadMore(page.key) },
                        onOpenSheetDetail = { item ->
                            onOpenSheetDetail(page.key, item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendSheetsScene(
    state: RecommendSheetsSceneState,
    onSelectTag: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenSheetDetail: (MusicSheetItemBase) -> Unit,
) {
    if (state.tags.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.tags, key = { it.id }) { tag ->
                FilterChip(
                    selected = tag.id == state.selectedTagId,
                    onClick = { onSelectTag(tag.id) },
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
        state.loading && state.sheets.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
            }
        }

        !state.errorMessage.isNullOrBlank() && state.sheets.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.errorMessage ?: "加载推荐歌单失败",
                    color = MusicFreeTheme.colors.danger,
                    fontSize = FontSizes.content,
                )
                TextButton(onClick = onRetry) {
                    Text("重试", color = MusicFreeTheme.colors.primary)
                }
            }
        }

        state.sheets.isEmpty() && !state.emptyMessage.isNullOrBlank() -> {
            EmptyState(state.emptyMessage ?: "当前没有推荐歌单")
        }

        else -> {
            RecommendSheetsGrid(
                state = state,
                onLoadMore = onLoadMore,
                onOpenSheetDetail = onOpenSheetDetail,
            )
        }
    }
}

@Composable
private fun RecommendSheetsGrid(
    state: RecommendSheetsSceneState,
    onLoadMore: () -> Unit,
    onOpenSheetDetail: (MusicSheetItemBase) -> Unit,
) {
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
            items = state.sheets,
            key = { item -> "${item.platform}:${item.id}" },
        ) { item ->
            RecommendSheetGridItem(
                item = item,
                onClick = { onOpenSheetDetail(item) },
            )
        }

        if (!state.isEnd) {
            item(
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = rpx(20)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                    } else {
                        TextButton(onClick = onLoadMore) {
                            Text("加载更多", color = MusicFreeTheme.colors.primary)
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
