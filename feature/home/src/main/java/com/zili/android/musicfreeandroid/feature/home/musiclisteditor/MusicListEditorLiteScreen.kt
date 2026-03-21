package com.zili.android.musicfreeandroid.feature.home.musiclisteditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.feature.home.playlist.AddToPlaylistDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicListEditorLiteScreen(
    onBack: () -> Unit,
    viewModel: MusicListEditorLiteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = uiState.availableTargetPlaylists,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelect = { playlist -> viewModel.addSelectedToPlaylist(playlist.id) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = uiState.playlistName.ifBlank { "歌单编辑" },
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
            actions = {
                if (uiState.hasPendingChanges) {
                    TextButton(onClick = viewModel::saveChanges) {
                        Text("保存", color = MusicFreeTheme.colors.appBarText)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MusicFreeTheme.colors.appBar),
        )

        if (uiState.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (uiState.hasPendingChanges) "已移除全部歌曲，点击保存应用" else "播放列表为空",
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    MusicListEditorLiteActions(
                        uiState = uiState,
                        onSelectAll = viewModel::selectAll,
                        onClearSelection = viewModel::clearSelection,
                        onRemoveSelected = viewModel::removeSelectedFromPlaylist,
                        onAddToNextQueue = viewModel::addSelectedToNextQueue,
                        onAddToPlaylist = { showAddToPlaylistDialog = true },
                    )
                }
                items(uiState.items, key = { "${it.platform}:${it.id}" }) { item ->
                    MusicListEditorLiteRow(
                        item = item,
                        selected = "${item.platform}:${item.id}" in uiState.selectedItemKeys,
                        onToggleSelection = { viewModel.toggleSelection(item) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MusicFreeTheme.colors.divider,
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicListEditorLiteActions(
    uiState: MusicListEditorLiteUiState,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onAddToNextQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${uiState.items.size} 首歌曲",
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
            )
            Text(
                text = "已选 ${uiState.selectedCount}",
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onSelectAll) {
                Text("全选")
            }
            TextButton(
                onClick = onClearSelection,
                enabled = uiState.selectedCount > 0,
            ) {
                Text("清空选择")
            }
            TextButton(
                onClick = onRemoveSelected,
                enabled = uiState.selectedCount > 0,
            ) {
                Text("移除所选")
            }
            TextButton(
                onClick = onAddToNextQueue,
                enabled = uiState.selectedCount > 0,
            ) {
                Text("下一首播放")
            }
            TextButton(
                onClick = onAddToPlaylist,
                enabled = uiState.selectedCount > 0 && uiState.availableTargetPlaylists.isNotEmpty(),
            ) {
                Text("添加到歌单")
            }
        }

        if (uiState.hasPendingChanges) {
            Text(
                text = "有未保存删除项",
                color = MusicFreeTheme.colors.primary,
                fontSize = FontSizes.description,
            )
        }
    }
}

@Composable
private fun MusicListEditorLiteRow(
    item: MusicItem,
    selected: Boolean,
    onToggleSelection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelection)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggleSelection() },
        )
        Spacer(Modifier.width(8.dp))
        CoverImage(uri = item.artwork, size = 48.dp, cornerRadius = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = item.title,
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.artist.isNotBlank()) {
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
}
