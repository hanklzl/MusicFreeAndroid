package com.hank.musicfree.feature.settings.pluginsort

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PluginSortScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSortViewModel = hiltViewModel(),
) {
    val platforms by viewModel.sortedPlatforms.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onReorder(from.index, to.index)
    }

    MusicFreeScreenScaffold(
        title = "插件排序",
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = {
                viewModel.saveOrder()
                onBack()
            }) {
                Text("完成", color = MusicFreeTheme.colors.appBarText)
            }
        },
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = rpx(24), vertical = rpx(16)),
            verticalArrangement = Arrangement.spacedBy(rpx(8)),
            modifier = Modifier.padding(padding),
        ) {
            items(platforms, key = { it }) { platform ->
                ReorderableItem(reorderableState, key = platform) { isDragging ->
                    val elevation = if (isDragging) 4.dp else 0.dp
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = rpx(16), vertical = rpx(14)),
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "拖拽排序",
                                modifier = Modifier.draggableHandle(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(rpx(12)))
                            Text(
                                text = platform,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
