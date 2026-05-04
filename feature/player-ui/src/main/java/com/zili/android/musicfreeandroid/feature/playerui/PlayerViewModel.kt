package com.zili.android.musicfreeandroid.feature.playerui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.lyric.LyricTiming
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.LyricLoadState
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricLoader
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricsUiState
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState
    val errorEvents: SharedFlow<String> = playerController.errorEvents

    private val lyricLoadState: StateFlow<LyricLoadState> = playerState
        .map { it.currentItem }
        .distinctUntilChanged()
        .flatMapLatest { item ->
            playerLyricLoader.observeLyrics(item)
        }
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
        .flatMapLatest { item ->
            if (item == null) flowOf(false)
            else playlistRepository.isFavorite(item)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleCurrentFavorite() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch { playlistRepository.toggleFavorite(item) }
    }

    // ---- add-to-playlist sheet ----

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.observeAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun showAddToPlaylistSheet() {
        val item = playerState.value.currentItem ?: return
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

    fun toggleShuffle() = playerController.toggleShuffle()

    fun setLyricShowTranslation(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLyricShowTranslation(enabled) }
    }

    fun setLyricDetailFontSize(level: Int) {
        viewModelScope.launch { appPreferences.setLyricDetailFontSize(level) }
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
