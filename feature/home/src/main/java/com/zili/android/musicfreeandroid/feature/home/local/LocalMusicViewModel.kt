package com.zili.android.musicfreeandroid.feature.home.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
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
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val scanner: LocalMusicScanner,
    private val playerController: PlayerController,
    private val musicRepository: MusicRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocalMusicUiState>(LocalMusicUiState.Loading)
    val uiState: StateFlow<LocalMusicUiState> = _uiState

    private var latestRepositoryItems: List<MusicItem> = emptyList()
    private var repositoryEmissionCount: Int = 0

    val downloadActiveCount: StateFlow<Int> = downloader.tasks
        .map { tasks -> tasks.count { it.status != DownloadStatus.FAILED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadedKeys: StateFlow<Set<MediaKey>> = downloader.downloadedKeys

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    init {
        viewModelScope.launch {
            musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)
                .catch { e ->
                    _uiState.value = LocalMusicUiState.Error(e.message ?: "加载失败")
                }
                .collect { items ->
                    latestRepositoryItems = items
                    repositoryEmissionCount += 1
                    _uiState.value = LocalMusicUiState.Success(items)
                }
        }
    }

    fun scanLocalMusic(storageDirectoryUri: String? = null) {
        _uiState.value = LocalMusicUiState.Loading
        viewModelScope.launch {
            try {
                scanAndPersist(storageDirectoryUri ?: appPreferences.storageDirectoryUri.first())
            } catch (e: Exception) {
                _uiState.value = LocalMusicUiState.Error(e.message ?: "扫描失败")
            }
        }
    }

    fun persistStorageDirectoryAndScan(uri: String) {
        _uiState.value = LocalMusicUiState.Loading
        viewModelScope.launch {
            try {
                appPreferences.setStorageDirectoryUri(uri)
                scanAndPersist(uri)
            } catch (e: Exception) {
                _uiState.value = LocalMusicUiState.Error(e.message ?: "保存目录失败")
            }
        }
    }

    fun showError(message: String) {
        _uiState.value = LocalMusicUiState.Error(message)
    }

    private suspend fun scanAndPersist(storageDirectoryUri: String?) {
        val emissionCountBeforeScan = repositoryEmissionCount
        scanner.scan(storageDirectoryUri)
            .collect { items ->
                musicRepository.insertAll(items)
            }
        if (_uiState.value == LocalMusicUiState.Loading &&
            repositoryEmissionCount == emissionCountBeforeScan
        ) {
            _uiState.value = LocalMusicUiState.Success(latestRepositoryItems)
        }
    }

    fun playItem(item: MusicItem, queue: List<MusicItem>) {
        val index = queue.indexOf(item)
        playerController.playQueue(queue, if (index >= 0) index else 0)
    }

    fun removeFromLocalLibrary(item: MusicItem) {
        viewModelScope.launch {
            musicRepository.delete(item)
        }
    }

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }
}
