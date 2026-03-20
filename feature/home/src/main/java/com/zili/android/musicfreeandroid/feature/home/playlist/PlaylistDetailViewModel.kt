package com.zili.android.musicfreeandroid.feature.home.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.navigation.PlaylistDetailRoute
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PlaylistDetailRoute>()
    val playlistId: String = route.playlistId

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist

    val musicItems: StateFlow<List<MusicItem>> =
        playlistRepository.observeMusicInPlaylist(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _playlist.value = playlistRepository.getPlaylistById(playlistId)
        }
    }

    fun playAll(startIndex: Int = 0) {
        val items = musicItems.value
        if (items.isNotEmpty()) {
            playerController.playQueue(items, startIndex)
        }
    }

    fun removeSong(item: MusicItem) {
        viewModelScope.launch {
            playlistRepository.removeMusicFromPlaylist(playlistId, item)
        }
    }
}
