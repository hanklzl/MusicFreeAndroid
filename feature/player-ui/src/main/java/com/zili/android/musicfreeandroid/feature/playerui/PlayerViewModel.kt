package com.zili.android.musicfreeandroid.feature.playerui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackSpeeds
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.lyric.LyricTiming
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.LocalLyricKind
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricLoadState
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricSearchGroup
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricLoader
import com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueUiModel
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricsUiState
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val playerLyricLoader: PlayerLyricLoader,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState

    private val _internalErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val _errorEventsSink = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _errorEventsSink.asSharedFlow()

    init {
        viewModelScope.launch {
            playerController.errorEvents.collect { _errorEventsSink.tryEmit(it) }
        }
        viewModelScope.launch {
            _internalErrorEvents.collect { _errorEventsSink.tryEmit(it) }
        }
    }

    val queueUiModel: StateFlow<PlayQueueUiModel> = combine(
        playerController.queueState,
        playerState,
    ) { snapshot, player ->
        PlayQueueUiModel(
            items = snapshot.items,
            currentIndex = snapshot.currentIndex,
            repeatMode = player.repeatMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQueueUiModel.EMPTY)

    private val rawLyricLoadState: StateFlow<LyricLoadState> = playerState
        .map { it.currentItem }
        .distinctUntilChangedBy { item -> item?.let { it.platform to it.id } }
        .flatMapLatest { item ->
            playerLyricLoader.observeLyrics(item)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricLoadState.NoTrack)

    private val lyricLoadState: StateFlow<LyricLoadState> = rawLyricLoadState
        .scan<LyricLoadState, LyricLoadState?>(null) { previous, next ->
            val previousReady = previous as? LyricLoadState.Ready
            val nextLoading = next as? LyricLoadState.Loading
            if (previousReady != null && nextLoading != null && previousReady.music.sameMusicKey(nextLoading.music)) {
                previousReady
            } else {
                next
            }
        }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricLoadState.NoTrack)

    val lyricsUiState: StateFlow<PlayerLyricsUiState> = combine(
        playerState,
        lyricLoadState,
        appPreferences.lyricShowTranslation,
        appPreferences.lyricDetailFontSize,
    ) { playback, lyricState, showTranslation, fontSize ->
        val ready = lyricState as? LyricLoadState.Ready
        PlayerLyricsUiState(
            loadState = lyricState,
            document = ready?.document,
            currentLineIndex = ready?.document?.let {
                LyricTiming.currentLineIndex(
                    lines = it.lines,
                    playbackPositionMs = playback.position,
                    userOffsetMs = ready.userOffsetMs,
                    metaOffsetMs = it.metaOffsetMs,
                )
            },
            showTranslation = showTranslation,
            fontSizeLevel = fontSize,
            userOffsetMs = ready?.userOffsetMs ?: 0L,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerLyricsUiState())

    // ---- favorite ----

    val isCurrentFavorite: StateFlow<Boolean> = playerState
        .map { it.currentItem }
        .distinctUntilChangedBy { item -> item?.let { it.platform to it.id } }
        .flatMapLatest { item ->
            if (item == null) flowOf(false)
            else playlistRepository.isFavorite(item)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleCurrentFavorite() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            try {
                playlistRepository.toggleFavorite(item)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _internalErrorEvents.tryEmit("收藏操作失败: ${error.message ?: error::class.simpleName}")
            }
        }
    }

    // ---- quality ----

    val currentQuality: StateFlow<PlayQuality> =
        appPreferences.playQuality
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    fun setCurrentQuality(quality: PlayQuality) {
        viewModelScope.launch {
            appPreferences.setPlayQuality(quality)
        }
        playerController.changeQuality(quality)
    }

    // ---- playback speed ----

    val currentSpeed: StateFlow<Float> =
        appPreferences.playRate
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackSpeeds.DEFAULT)

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            appPreferences.setPlayRate(speed)
        }
        playerController.setPlaybackSpeed(speed)
    }

    // ---- download ----

    val isCurrentDownloaded: StateFlow<Boolean> = combine(
        playerState.map { it.currentItem }.distinctUntilChangedBy { it?.let { item -> item.platform to item.id } },
        downloader.downloadedKeys,
    ) { item, keys ->
        item != null && keys.contains(MediaKey.of(item))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun downloadCurrent(quality: PlayQuality) {
        val item = playerState.value.currentItem ?: return
        downloader.enqueue(listOf(item), quality)
    }

    // ---- add-to-playlist sheet ----

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.observeAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lyricSearchResults = MutableStateFlow<List<LyricSearchGroup>>(emptyList())
    val lyricSearchResults: StateFlow<List<LyricSearchGroup>> = _lyricSearchResults.asStateFlow()

    private val _lyricSearchLoading = MutableStateFlow(false)
    val lyricSearchLoading: StateFlow<Boolean> = _lyricSearchLoading.asStateFlow()

    fun showAddToPlaylistSheet() {
        val item = playerState.value.currentItem ?: return
        _sheetState.value = AddToPlaylistSheetState.single(item)
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
            playlistRepository.createPlaylist(Playlist(id = newId, name = name, coverUri = null))
            playlistRepository.addMusicToPlaylist(newId, item)
            hideAddToPlaylistSheet()
        }
    }

    // ---- playback controls ----

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            playerController.pause()
        } else {
            playerController.play()
        }
    }

    fun skipToNext() = playerController.skipToNext()

    fun skipToPrevious() = playerController.skipToPrevious()

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun cycleRepeatMode() = playerController.cycleRepeatMode()

    fun cyclePlaybackMode() = playerController.cyclePlaybackMode()

    fun toggleShuffle() = playerController.toggleShuffle()

    fun playQueueIndex(index: Int) = playerController.skipTo(index)

    fun removeFromQueue(index: Int) = playerController.removeFromQueue(index)

    fun clearQueue() = playerController.reset()

    fun setLyricShowTranslation(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLyricShowTranslation(enabled) }
    }

    fun setLyricDetailFontSize(level: Int) {
        viewModelScope.launch { appPreferences.setLyricDetailFontSize(level) }
    }

    fun searchLyrics() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            _lyricSearchLoading.value = true
            _lyricSearchResults.value = emptyList()
            try {
                _lyricSearchResults.value = playerLyricLoader.searchCandidates(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _lyricSearchResults.value = emptyList()
            } finally {
                _lyricSearchLoading.value = false
            }
        }
    }

    fun associateLyric(target: MusicItem) {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.associateLyric(item, target)
        }
    }

    fun clearAssociatedLyric() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.clearAssociatedLyric(item)
        }
    }

    fun setLyricOffset(offsetMs: Long) {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.setLyricOffset(item, offsetMs)
        }
    }

    fun importLocalLyric(rawText: String, kind: LocalLyricKind) {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.importLocalLyric(item, rawText, kind)
        }
    }

    fun deleteLocalLyric() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.deleteLocalLyric(item)
        }
    }

    fun seekToLyricLine(lineTimeMs: Long) {
        val ready = lyricsUiState.value.loadState as? LyricLoadState.Ready ?: return
        val duration = playerState.value.duration
        val seekMs = LyricTiming.seekPositionForLine(
            lineTimeMs = lineTimeMs,
            userOffsetMs = ready.userOffsetMs,
            metaOffsetMs = ready.document.metaOffsetMs,
            durationMs = duration,
        )
        playerController.seekTo(seekMs)
        playerController.play()
    }
}

private fun MusicItem.sameMusicKey(other: MusicItem): Boolean =
    platform == other.platform && id == other.id
