package com.zili.android.musicfreeandroid.feature.home.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.R as CoreR
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold
import com.zili.android.musicfreeandroid.core.ui.MusicItemAction
import com.zili.android.musicfreeandroid.core.ui.MusicItemMoreMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearchMusicList: (String) -> Unit,
    onNavigateToMusicListEditorLite: (String) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playlist = state.playlist
    val items = state.musics
    val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    MusicFreeScreenScaffold(
        title = playlist?.name ?: "歌单",
        onBack = onBack,
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_ellipsis_vertical),
                    contentDescription = "更多",
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("编辑信息") },
                    onClick = { menuExpanded = false; showEditDialog = true },
                )
                DropdownMenuItem(
                    text = { Text("排序") },
                    onClick = { menuExpanded = false; showSortDialog = true },
                )
                if (playlist?.isDefault == false) {
                    DropdownMenuItem(
                        text = { Text("删除歌单") },
                        onClick = { menuExpanded = false; showDeleteDialog = true },
                    )
                }
            }
        },
    ) { padding ->
        if (state.isLoading || playlist == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("加载中…") }
            return@MusicFreeScreenScaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PlaylistDetailHeader(
                playlist = playlist,
                musicCount = items.size,
                onPlayAll = {
                    viewModel.playAll()
                    onNavigateToPlayer()
                },
                onSearch = { onNavigateToSearchMusicList(playlist.id) },
            )
            if (items.isEmpty()) {
                EmptyState(onSearchAdd = { onNavigateToSearchMusicList(playlist.id) })
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = items, key = { "${it.platform}::${it.id}" }) { item ->
                        val isFavorite by viewModel.isFavoriteFlow(item).collectAsStateWithLifecycle(initialValue = false)
                        PlaylistRow(
                            item = item,
                            isFavorite = isFavorite,
                            onClickRow = {
                                val idx = items.indexOf(item)
                                viewModel.playAll(startIndex = if (idx >= 0) idx else 0)
                                onNavigateToPlayer()
                            },
                            onAction = { action ->
                                when (action) {
                                    MusicItemAction.ToggleFavorite -> viewModel.toggleFavorite(item)
                                    MusicItemAction.RemoveFromPlaylist -> viewModel.removeFromPlaylist(item)
                                    MusicItemAction.PlayNext -> {
                                        // TODO: PlayerController.playNext when API exists
                                    }
                                    MusicItemAction.AddToPlaylist -> viewModel.showAddToPlaylistSheet(item)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog && playlist != null) {
        SortModeDialog(
            current = playlist.sortMode,
            onSelect = { mode ->
                viewModel.setSortMode(mode)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false },
        )
    }
    if (showEditDialog && playlist != null) {
        EditPlaylistDialog(
            playlist = playlist,
            onDismiss = { showEditDialog = false },
            onSave = { name, description, coverUri ->
                viewModel.updateInfo(name, description, coverUri)
                showEditDialog = false
            },
        )
    }
    if (showDeleteDialog && playlist != null) {
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { showDeleteDialog = false },
            onDelete = {
                viewModel.deletePlaylistAndExit(onDone = onBack)
                showDeleteDialog = false
            },
        )
    }

    if (sheetState.visible) {
        var showCreateInSheet by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideAddToPlaylistSheet() },
        ) {
            AddToPlaylistBottomSheetContent(
                playlists = allPlaylists,
                onSelect = { viewModel.addPendingToPlaylist(it.id) },
                onCreateNew = { showCreateInSheet = true },
                folderPlusIcon = painterResource(id = CoreR.drawable.ic_folder_plus),
                favoriteCoverIcon = painterResource(id = CoreR.drawable.ic_playlist_favorite_cover),
            )
        }
        if (showCreateInSheet) {
            CreatePlaylistDialog(
                onDismiss = { showCreateInSheet = false },
                onCreate = { name ->
                    viewModel.createPlaylistAndAddPending(name)
                    showCreateInSheet = false
                },
            )
        }
    }
}

@Composable
private fun EmptyState(onSearchAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("歌单还没有歌曲", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSearchAdd) { Text("去搜索添加") }
    }
}

@Composable
private fun PlaylistRow(
    item: MusicItem,
    isFavorite: Boolean,
    onClickRow: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CoverImage(
            uri = item.artwork,
            size = 40.dp,
            cornerRadius = 4.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicItemMoreMenu(
            actions = setOf(
                MusicItemAction.PlayNext,
                MusicItemAction.ToggleFavorite,
                MusicItemAction.AddToPlaylist,
                MusicItemAction.RemoveFromPlaylist,
            ),
            isFavorite = isFavorite,
            onAction = onAction,
            triggerIcon = painterResource(id = CoreR.drawable.ic_ellipsis_vertical),
        )
    }
}
