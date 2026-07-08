package com.hank.musicfree.feature.home.playlist

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.R as CoreR
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.core.ui.AddToPlaylistBottomSheetContent
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.MusicItemAction
import com.hank.musicfree.core.ui.MusicItemRow
import com.hank.musicfree.core.ui.MusicItemRowPlaybackState
import com.hank.musicfree.core.ui.logUiClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onNavigateToSearchMusicList: (String) -> Unit,
    onNavigateToMusicListEditorLite: (String) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val downloadedKeys by viewModel.downloadedKeys.collectAsStateWithLifecycle()
    val currentPlaybackItemState by viewModel.currentPlaybackItemState.collectAsStateWithLifecycle()

    PlaylistDetailContent(
        state = state,
        sheetState = sheetState,
        allPlaylists = allPlaylists,
        downloadedKeys = downloadedKeys,
        favoriteResolver = viewModel::isFavoriteFlow,
        currentPlaybackItemState = currentPlaybackItemState,
        onBack = onBack,
        onNavigateToSearchMusicList = onNavigateToSearchMusicList,
        onNavigateToMusicListEditorLite = onNavigateToMusicListEditorLite,
        actions = PlaylistDetailActions(
            playAll = viewModel::playAll,
            setSortMode = viewModel::setSortMode,
            updateInfo = viewModel::updateInfo,
            deletePlaylistAndExit = viewModel::deletePlaylistAndExit,
            toggleFavorite = viewModel::toggleFavorite,
            removeFromPlaylist = viewModel::removeFromPlaylist,
            showAddToPlaylistSheet = viewModel::showAddToPlaylistSheet,
            hideAddToPlaylistSheet = viewModel::hideAddToPlaylistSheet,
            addPendingToPlaylist = viewModel::addPendingToPlaylist,
            createPlaylistAndAddPending = viewModel::createPlaylistAndAddPending,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistDetailContent(
    state: PlaylistDetailUiState,
    sheetState: AddToPlaylistSheetState,
    allPlaylists: List<Playlist>,
    favoriteResolver: (MusicItem) -> Flow<Boolean>,
    downloadedKeys: Set<String> = emptySet(),
    currentPlaybackItemState: CurrentPlaybackItemState = CurrentPlaybackItemState(),
    onBack: () -> Unit,
    onNavigateToSearchMusicList: (String) -> Unit,
    onNavigateToMusicListEditorLite: (String) -> Unit,
    actions: PlaylistDetailActions,
) {
    val playlist = state.playlist
    val items = state.musics
    var menuExpanded by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showScrollToCurrentFab by remember { mutableStateOf(false) }
    var suppressProgrammaticScrollFab by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val currentPlayingIndex = remember(items, currentPlaybackItemState.item) {
        items.indexOfFirst { it.hasSameMediaIdentity(currentPlaybackItemState.item) }
    }

    LaunchedEffect(listState, currentPlayingIndex) {
        if (currentPlayingIndex < 0) {
            showScrollToCurrentFab = false
            return@LaunchedEffect
        }
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { isScrolling ->
                when {
                    isScrolling && !suppressProgrammaticScrollFab -> {
                        showScrollToCurrentFab = true
                    }
                    !isScrolling -> {
                        delay(SCROLL_TO_CURRENT_FAB_HIDE_DELAY_MS)
                        showScrollToCurrentFab = false
                    }
                }
            }
    }

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
                    text = { Text("批量编辑") },
                    onClick = {
                        val id = playlist?.id ?: return@DropdownMenuItem
                        menuExpanded = false
                        onNavigateToMusicListEditorLite(id)
                    },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("PlaylistDetail_list"),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "header") {
                    PlaylistDetailHeader(
                        playlist = playlist,
                        musicCount = items.size,
                        onPlayAll = {
                            actions.playAll(0)
                        },
                        onSearch = { onNavigateToSearchMusicList(playlist.id) },
                    )
                }

                if (items.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(onSearchAdd = { onNavigateToSearchMusicList(playlist.id) })
                    }
                } else {
                    itemsIndexed(items = items, key = { _, item -> "${item.platform}::${item.id}" }) { index, item ->
                        val isFavorite by favoriteResolver(item)
                            .collectAsStateWithLifecycle(initialValue = false)
                        val rowPlaybackState = when {
                            !item.hasSameMediaIdentity(currentPlaybackItemState.item) ->
                                MusicItemRowPlaybackState.None
                            currentPlaybackItemState.isPlaying ->
                                MusicItemRowPlaybackState.CurrentPlaying
                            else ->
                                MusicItemRowPlaybackState.CurrentPaused
                        }
                        MusicItemRow(
                            item = item,
                            isFavorite = isFavorite,
                            downloaded = downloadedKeys.contains("${item.id}@${item.platform}"),
                            playbackState = rowPlaybackState,
                            actions = setOf(
                                MusicItemAction.PlayNext,
                                MusicItemAction.ToggleFavorite,
                                MusicItemAction.AddToPlaylist,
                                MusicItemAction.RemoveFromPlaylist,
                            ),
                            onClick = { actions.playAll(index) },
                            onAction = { action ->
                                when (action) {
                                    MusicItemAction.ToggleFavorite -> actions.toggleFavorite(item)
                                    MusicItemAction.RemoveFromPlaylist -> actions.removeFromPlaylist(item)
                                    MusicItemAction.PlayNext -> { /* TODO: PlayerController.playNext when API exists */ }
                                    MusicItemAction.AddToPlaylist -> actions.showAddToPlaylistSheet(item)
                                }
                            },
                        )
                    }
                }
            }
            if (showScrollToCurrentFab && currentPlayingIndex >= 0) {
                FloatingActionButton(
                    onClick = {
                        val targetItem = items[currentPlayingIndex]
                        logUiClick(
                            targetId = "playlist_detail.fab.scroll_to_current",
                            screen = "playlist_detail",
                            targetLabel = "定位到当前播放歌曲",
                            extra = mapOf(
                                "playlistId" to playlist.id,
                                "itemId" to targetItem.id,
                                "platform" to targetItem.platform,
                                "itemIndex" to currentPlayingIndex,
                            ),
                        )
                        showScrollToCurrentFab = false
                        scrollScope.launch {
                            suppressProgrammaticScrollFab = true
                            try {
                                listState.animateScrollToItem(
                                    index = currentPlayingIndex + PLAYLIST_DETAIL_HEADER_ITEM_COUNT,
                                )
                            } finally {
                                suppressProgrammaticScrollFab = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "定位到当前播放歌曲",
                    )
                }
            }
        }
    }

    if (showSortDialog && playlist != null) {
        SortModeDialog(
            current = playlist.sortMode,
            onSelect = { mode ->
                actions.setSortMode(mode)
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
                actions.updateInfo(name, description, coverUri)
                showEditDialog = false
            },
        )
    }
    if (showDeleteDialog && playlist != null) {
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { showDeleteDialog = false },
            onDelete = {
                actions.deletePlaylistAndExit(onBack)
                showDeleteDialog = false
            },
        )
    }

    if (sheetState.visible) {
        var showCreateInSheet by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = { actions.hideAddToPlaylistSheet() },
        ) {
            AddToPlaylistBottomSheetContent(
                playlists = allPlaylists,
                onSelect = { actions.addPendingToPlaylist(it.id) },
                onCreateNew = { showCreateInSheet = true },
                folderPlusIcon = painterResource(id = CoreR.drawable.ic_folder_plus),
                favoriteCoverIcon = painterResource(id = CoreR.drawable.ic_playlist_favorite_cover),
            )
        }
        if (showCreateInSheet) {
            CreatePlaylistDialog(
                onDismiss = { showCreateInSheet = false },
                onCreate = { name ->
                    actions.createPlaylistAndAddPending(name)
                    showCreateInSheet = false
                },
            )
        }
    }

}

private const val PLAYLIST_DETAIL_HEADER_ITEM_COUNT = 1
private const val SCROLL_TO_CURRENT_FAB_HIDE_DELAY_MS = 5_000L

private fun MusicItem.hasSameMediaIdentity(other: MusicItem?): Boolean =
    other != null && id == other.id && platform == other.platform

internal data class PlaylistDetailActions(
    val playAll: (Int) -> Unit,
    val setSortMode: (SortMode) -> Unit,
    val updateInfo: (String?, String?, Uri?) -> Unit,
    val deletePlaylistAndExit: (() -> Unit) -> Unit,
    val toggleFavorite: (MusicItem) -> Unit,
    val removeFromPlaylist: (MusicItem) -> Unit,
    val showAddToPlaylistSheet: (MusicItem) -> Unit,
    val hideAddToPlaylistSheet: () -> Unit,
    val addPendingToPlaylist: (String) -> Unit,
    val createPlaylistAndAddPending: (String) -> Unit,
) {
    companion object {
        val Noop = PlaylistDetailActions(
            playAll = {},
            setSortMode = {},
            updateInfo = { _, _, _ -> },
            deletePlaylistAndExit = {},
            toggleFavorite = {},
            removeFromPlaylist = {},
            showAddToPlaylistSheet = {},
            hideAddToPlaylistSheet = {},
            addPendingToPlaylist = {},
            createPlaylistAndAddPending = {},
        )
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
