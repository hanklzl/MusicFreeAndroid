package com.zili.android.musicfreeandroid.feature.home.playlist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.core.navigation.PlaylistDetailRoute
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val musics: List<MusicItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PlaylistDetailRoute>()
    val playlistId: String = route.playlistId

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        playlistRepository.observePlaylist(playlistId),
        playlistRepository.observeMusicInPlaylist(playlistId),
    ) { playlist, musics ->
        PlaylistDetailUiState(playlist = playlist, musics = musics, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistDetailUiState())

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.observeAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isFavoriteFlow(item: MusicItem): Flow<Boolean> = playlistRepository.isFavorite(item)

    fun showAddToPlaylistSheet(item: MusicItem) {
        _sheetState.value = AddToPlaylistSheetState(visible = true, pendingItem = item)
    }

    fun hideAddToPlaylistSheet() {
        _sheetState.value = AddToPlaylistSheetState()
    }

    fun addPendingToPlaylist(targetPlaylistId: String) {
        val item = _sheetState.value.pendingItem ?: return
        viewModelScope.launch {
            playlistRepository.addMusicToPlaylist(targetPlaylistId, item)
            hideAddToPlaylistSheet()
        }
    }

    fun createPlaylistAndAddPending(name: String) {
        val item = _sheetState.value.pendingItem ?: return
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            playlistRepository.createPlaylist(
                Playlist(id = newId, name = name, coverUri = null)
            )
            playlistRepository.addMusicToPlaylist(newId, item)
            hideAddToPlaylistSheet()
        }
    }

    fun playAll(startIndex: Int = 0) {
        val items = uiState.value.musics
        if (items.isNotEmpty()) {
            playerController.playQueue(items, startIndex)
        }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch {
            if (mode == SortMode.Manual) {
                val currentVisualOrder = playlistRepository.observeMusicInPlaylist(playlistId).first()
                playlistRepository.setSortMode(playlistId, SortMode.Manual)
                playlistRepository.applyManualSortOrder(playlistId, currentVisualOrder)
            } else {
                playlistRepository.setSortMode(playlistId, mode)
            }
        }
    }

    fun updateInfo(name: String?, description: String?, coverUri: Uri?) {
        viewModelScope.launch {
            playlistRepository.updatePlaylistInfo(playlistId, name, description)
            if (coverUri != null) playlistRepository.setCover(playlistId, coverUri)
        }
    }

    fun deletePlaylistAndExit(onDone: () -> Unit) {
        val current = uiState.value.playlist ?: return
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(current)
                onDone()
            } catch (_: IllegalStateException) {
                // favorite — already filtered by UI but defensive guard
            }
        }
    }

    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch { playlistRepository.toggleFavorite(item) }
    }

    fun removeFromPlaylist(item: MusicItem) {
        viewModelScope.launch { playlistRepository.removeMusicFromPlaylist(playlistId, item) }
    }
}
