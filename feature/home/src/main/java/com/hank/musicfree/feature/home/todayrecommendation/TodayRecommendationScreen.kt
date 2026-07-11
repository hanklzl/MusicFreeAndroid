package com.hank.musicfree.feature.home.todayrecommendation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.core.ui.LoggedIconButton
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.PlatformTag
import com.hank.musicfree.core.ui.logUiClick
import com.hank.musicfree.core.ui.loggedClick

@Composable
fun TodayRecommendationScreen(
    onBack: () -> Unit,
    onOpenSheetDetail: (RecommendedSheet) -> Unit,
    onOpenPluginList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayRecommendationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        title = "今日推荐",
        onBack = onBack,
        modifier = modifier.testTag("today_recommendation.root"),
        actions = {
            LoggedIconButton(
                targetId = "today_recommendation.toolbar.refresh",
                screen = "today_recommendation",
                targetLabel = "刷新推荐",
                enabled = !state.loading && !state.refreshing,
                onClick = viewModel::refresh,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新推荐",
                    tint = MusicFreeTheme.colors.appBarText,
                )
            }
        },
    ) { innerPadding ->
        TodayRecommendationContent(
            state = state,
            onRetry = viewModel::refresh,
            onOpenPluginList = onOpenPluginList,
            onOpenSheet = { item ->
                viewModel.logSheetOpen(item)
                onOpenSheetDetail(item)
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
internal fun TodayRecommendationContent(
    state: TodayRecommendationUiState,
    onRetry: () -> Unit,
    onOpenPluginList: () -> Unit,
    onOpenSheet: (RecommendedSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading && state.items.isEmpty() -> LoadingState(modifier)
        state.items.isEmpty() -> EmptyRecommendationState(
            noPlugins = state.noPlugins,
            errorMessage = state.errorMessage,
            onRetry = onRetry,
            onOpenPluginList = onOpenPluginList,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.refreshing || state.showingFallback || state.confidence == com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence.LOW || state.errorMessage != null) {
                item(key = "status") {
                    RecommendationStatusBanner(state)
                }
            }
            items(state.items, key = RecommendedSheet::key) { item ->
                RecommendedSheetRow(item = item, onClick = { onOpenSheet(item) })
            }
            item { Box(Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
            Text("正在生成今日推荐", color = MusicFreeTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun EmptyRecommendationState(
    noPlugins: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenPluginList: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    noPlugins -> "还没有可用于推荐的音乐插件"
                    errorMessage != null -> errorMessage
                    else -> "今天暂时没有找到合适的歌单"
                },
                color = MusicFreeTheme.colors.textSecondary,
            )
            TextButton(
                onClick = {
                    if (noPlugins) {
                        logUiClick("today_recommendation.empty.install_plugin", "today_recommendation", "去安装插件")
                        onOpenPluginList()
                    } else {
                        logUiClick("today_recommendation.empty.retry", "today_recommendation", "重新生成")
                        onRetry()
                    }
                },
            ) {
                Text(if (noPlugins) "去安装插件" else "重新生成", color = MusicFreeTheme.colors.primary)
            }
        }
    }
}

@Composable
private fun RecommendationStatusBanner(state: TodayRecommendationUiState) {
    val message = when {
        state.refreshing -> "正在刷新，当前推荐会保留到新结果生成"
        state.errorMessage != null -> state.errorMessage
        state.showingFallback -> "网络暂不可用，已展示上次推荐"
        else -> "先听一些歌，推荐会越来越准"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MusicFreeTheme.colors.card,
    ) {
        Text(
            text = message.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MusicFreeTheme.colors.textSecondary,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun RecommendedSheetRow(item: RecommendedSheet, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .semantics { role = Role.Button }
            .loggedClick(
                targetId = "today_recommendation.list.sheet_row",
                screen = "today_recommendation",
                targetLabel = item.sheet.title,
                fields = mapOf("platform" to item.sheet.platform, "sheetId" to item.sheet.id),
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(uri = item.sheet.coverImg ?: item.sheet.artwork, size = 68.dp, cornerRadius = 8.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = item.sheet.title.orEmpty().ifBlank { "未命名歌单" },
                style = MaterialTheme.typography.titleMedium,
                color = MusicFreeTheme.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformTag(item.sheet.platform)
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MusicFreeTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
