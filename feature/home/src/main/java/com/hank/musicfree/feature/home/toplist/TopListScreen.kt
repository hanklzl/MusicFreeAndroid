package com.hank.musicfree.feature.home.toplist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.hank.musicfree.plugin.api.MusicSheetGroupItem
import com.hank.musicfree.plugin.api.MusicSheetItemBase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopListScreen(
    onBack: () -> Unit,
    onOpenTopListDetail: (pluginPlatform: String, topList: MusicSheetItemBase) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TopListViewModel = hiltViewModel(),
) {
    val plugins by viewModel.availablePlugins.collectAsStateWithLifecycle()
    val selectedPlugin by viewModel.selectedPlugin.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPluginIndex = plugins.indexOfFirst { it.platform == selectedPlugin }

    MusicFreeScreenScaffold(
        title = "榜单",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.TopListRoot)
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
                    when (val state = uiState) {
                        is TopListUiState.Idle,
                        is TopListUiState.Loading,
                        -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                            }
                        }

                        is TopListUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = rpx(24)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = state.message,
                                    color = MusicFreeTheme.colors.danger,
                                    fontSize = FontSizes.content,
                                )
                                TextButton(onClick = { viewModel.refresh() }) {
                                    Text("重试", color = MusicFreeTheme.colors.primary)
                                }
                            }
                        }

                        is TopListUiState.Success -> {
                            if (state.groups.isEmpty()) {
                                EmptyState("当前插件不支持榜单")
                            } else {
                                TopListGroups(
                                    pluginPlatform = selectedPlugin,
                                    groups = state.groups,
                                    onOpenTopListDetail = onOpenTopListDetail,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopListGroups(
    pluginPlatform: String?,
    groups: List<MusicSheetGroupItem>,
    onOpenTopListDetail: (pluginPlatform: String, topList: MusicSheetItemBase) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEach { group ->
            val groupTitle = group.title
            if (!groupTitle.isNullOrBlank()) {
                item(key = "group-$groupTitle") {
                    Text(
                        text = groupTitle,
                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(18)),
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.subTitle,
                    )
                }
            }
            items(
                items = group.data,
                key = { item -> "${item.platform}:${item.id}" },
            ) { item ->
                TopListItemRow(
                    item = item,
                    onClick = {
                        if (!pluginPlatform.isNullOrBlank()) {
                            onOpenTopListDetail(pluginPlatform, item)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TopListItemRow(
    item: MusicSheetItemBase,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(FidelityAnchors.TopList.Item)
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
                text = item.title ?: "未命名榜单",
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = topListSubtitle(item)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun topListSubtitle(item: MusicSheetItemBase): String? =
    item.description?.trim()?.takeIf { it.isNotBlank() }
        ?: item.artist?.trim()?.takeIf { it.isNotBlank() }

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
