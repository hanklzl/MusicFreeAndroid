package com.hank.musicfree.feature.settings.traffic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold

@Composable
fun TrafficStatsScreen(
    onBack: () -> Unit,
    vm: TrafficStatsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val cacheUsage by vm.cacheUsage.collectAsStateWithLifecycle()

    MusicFreeScreenScaffold(
        title = "流量统计",
        onBack = onBack,
        modifier = Modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.TrafficStatsRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        when (val s = state) {
            TrafficUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(rpx(24)),
                ) {
                    Text(
                        text = "加载中...",
                        fontSize = FontSizes.content,
                        color = MusicFreeTheme.colors.textSecondary,
                    )
                }
            }
            is TrafficUiState.Data -> TrafficStatsContent(
                state = s,
                cacheUsage = cacheUsage,
                onTabSelected = vm::selectTab,
                onShift = vm::shiftAnchor,
                onClearMediaCache = vm::clearMediaCache,
                onClearAllRecords = vm::clearAllRecords,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun TrafficStatsContent(
    state: TrafficUiState.Data,
    cacheUsage: Long,
    onTabSelected: (TrafficScope) -> Unit,
    onShift: (Int) -> Unit,
    onClearMediaCache: () -> Unit,
    onClearAllRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = rpx(24),
            vertical = rpx(16),
        ),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        item {
            Column {
                Text(
                    text = "本期总流量",
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(rpx(4)))
                Text(
                    text = formatBytes(state.totalBytes),
                    fontSize = FontSizes.title,
                    color = MusicFreeTheme.colors.text,
                )
                Spacer(modifier = Modifier.height(rpx(6)))
                Text(
                    text = "WiFi ${formatBytes(state.byNetwork[NetworkType.WIFI] ?: 0L)}  " +
                        "移动 ${formatBytes(state.byNetwork[NetworkType.CELLULAR] ?: 0L)}",
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
        }

        item {
            TrafficScopeTabs(
                current = state.scope,
                onChange = onTabSelected,
            )
        }

        if (state.scope != TrafficScope.TOTAL) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onShift(-1) }) { Text("←") }
                    Text(
                        text = state.anchor,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = FontSizes.content,
                        color = MusicFreeTheme.colors.text,
                    )
                    TextButton(onClick = { onShift(1) }) { Text("→") }
                }
            }
        } else {
            item {
                Text(
                    text = state.anchor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = FontSizes.content,
                    color = MusicFreeTheme.colors.text,
                )
            }
        }

        item {
            TrafficBarChart(
                bars = state.bars,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        items(state.bars.asReversed()) { bar ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bar.label,
                    modifier = Modifier.weight(1f),
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.text,
                )
                Text(
                    text = "WiFi ${formatBytes(bar.wifiBytes)}",
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.width(rpx(12)))
                Text(
                    text = "移动 ${formatBytes(bar.cellularBytes)}",
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClearMediaCache) {
                    Text("清空音频缓存（已用 ${formatBytes(cacheUsage)}）")
                }
                TextButton(onClick = onClearAllRecords) {
                    Text("清空流量统计记录")
                }
            }
        }
    }
}

@Composable
private fun TrafficScopeTabs(
    current: TrafficScope,
    onChange: (TrafficScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(modifier = Modifier.padding(rpx(4))) {
            TrafficScope.entries.forEach { sc ->
                val selected = sc == current
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50)),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    onClick = { onChange(sc) },
                ) {
                    Text(
                        text = scopeLabel(sc),
                        modifier = Modifier
                            .padding(vertical = rpx(8))
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
        }
    }
}

private fun scopeLabel(s: TrafficScope): String = when (s) {
    TrafficScope.DAY -> "日"
    TrafficScope.WEEK -> "周"
    TrafficScope.MONTH -> "月"
    TrafficScope.YEAR -> "年"
    TrafficScope.TOTAL -> "总"
}

private fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
    else -> "%.2f GB".format(b / 1024.0 / 1024.0 / 1024.0)
}
