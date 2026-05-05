package com.zili.android.musicfreeandroid.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanner: LocalMusicScanner,
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .map { sortFavoriteFirst(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadActiveCount: StateFlow<Int> = downloader.tasks
        .map { tasks ->
            tasks.count { it.status != com.zili.android.musicfreeandroid.downloader.model.DownloadStatus.FAILED }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadedKeys: StateFlow<Set<com.zili.android.musicfreeandroid.downloader.model.MediaKey>> =
        downloader.downloadedKeys

    private fun sortFavoriteFirst(playlists: List<Playlist>): List<Playlist> {
        val (favorite, others) = playlists.partition { it.isDefault }
        return favorite + others.sortedByDescending { it.createdAt }
    }

    fun scanLocalMusic() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            val storageDirectoryUri = try {
                appPreferences.storageDirectoryUri.first()
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "扫描失败")
                return@launch
            }

            scanner.scan(storageDirectoryUri)
                .catch { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: "扫描失败")
                }
                .collect { items ->
                    _uiState.value = HomeUiState.Success(items)
                }
        }
    }

    fun playItem(item: MusicItem, queue: List<MusicItem>) {
        val index = queue.indexOf(item)
        playerController.playQueue(queue, if (index >= 0) index else 0)
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(
                Playlist(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    coverUri = null,
                )
            )
        }
    }

    fun addToPlaylist(playlistId: String, item: MusicItem) {
        viewModelScope.launch {
            playlistRepository.addMusicToPlaylist(playlistId, item)
        }
    }

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }
}
