package com.zili.android.musicfreeandroid.feature.playerui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.DesktopLyricAlignment
import com.zili.android.musicfreeandroid.core.model.LyricAssociationType
import com.zili.android.musicfreeandroid.core.model.MusicDetailDefaultPage
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.PlaybackMode
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
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
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

    val musicDetailDefaultPage: StateFlow<MusicDetailDefaultPage?> =
        appPreferences.musicDetailDefaultPage
            .map<MusicDetailDefaultPage, MusicDetailDefaultPage?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val musicDetailAwake: StateFlow<Boolean> =
        appPreferences.musicDetailAwake
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lyricAssociationType: StateFlow<LyricAssociationType> =
        appPreferences.lyricAssociationType
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricAssociationType.Search)

    private val desktopLyricPositionState = combine(
        appPreferences.desktopLyricTopPercent,
        appPreferences.desktopLyricLeftPercent,
        appPreferences.desktopLyricWidthPercent,
    ) { topPercent, leftPercent, widthPercent ->
        Triple(topPercent, leftPercent, widthPercent)
    }

    private val desktopLyricStyleState = combine(
        appPreferences.desktopLyricFontSizeSp,
        appPreferences.desktopLyricTextColor,
        appPreferences.desktopLyricBackgroundColor,
    ) { fontSizeSp, textColor, backgroundColor ->
        Triple(fontSizeSp, textColor, backgroundColor)
    }

    internal val desktopLyricOverlayState: StateFlow<DesktopLyricOverlayState> = combine(
        appPreferences.desktopLyricEnabled,
        appPreferences.desktopLyricAlignment,
        desktopLyricPositionState,
        desktopLyricStyleState,
    ) { enabled, alignment, position, style ->
        DesktopLyricOverlayState(
            enabled = enabled,
            alignment = alignment,
            topPercent = position.first,
            leftPercent = position.second,
            widthPercent = position.third,
            fontSizeSp = style.first,
            textColor = style.second,
            backgroundColor = style.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DesktopLyricOverlayState())

    private val _internalErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorEvents: SharedFlow<String> =
        merge(playerController.errorEvents, _internalErrorEvents)
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 0)

    val queueUiModel: StateFlow<PlayQueueUiModel> = combine(
        playerController.queueState,
        playerState,
    ) { snapshot, player ->
        PlayQueueUiModel(
            items = snapshot.items,
            currentIndex = snapshot.currentIndex,
            playbackMode = PlaybackMode.from(
                shuffleEnabled = player.shuffleEnabled,
                repeatMode = player.repeatMode,
            ),
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
        val item = playerState.value.currentItem
        if (item == null) {
            logPlayerDetail(
                event = "player_favorite_toggle_skipped",
                operation = "toggle_favorite",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
            )
            return
        }
        viewModelScope.launch {
            try {
                playlistRepository.toggleFavorite(item)
                logPlayerDetail(
                    event = "player_favorite_toggle",
                    operation = "toggle_favorite",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                )
            } catch (error: CancellationException) {
                logPlayerDetail(
                    event = "player_favorite_toggle_cancelled",
                    operation = "toggle_favorite",
                    item = item,
                    result = LogFields.Result.CANCELLED,
                    reason = LogFields.Reason.CANCELLED,
                )
                throw error
            } catch (error: Throwable) {
                logPlayerError(
                    event = "player_favorite_toggle_failed",
                    operation = "toggle_favorite",
                    item = item,
                    error = error,
                )
                _internalErrorEvents.tryEmit("收藏操作失败: ${error.message ?: error::class.simpleName}")
            }
        }
    }

    // ---- quality ----

    val currentQuality: StateFlow<PlayQuality> =
        appPreferences.playQuality
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    fun setCurrentQuality(quality: PlayQuality) {
        val item = playerState.value.currentItem
        logPlayerDetail(
            event = "player_quality_change",
            operation = "change_quality",
            item = item,
            result = if (item == null) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
            reason = if (item == null) REASON_NO_CURRENT_ITEM else REASON_NONE,
            extra = mapOf("quality" to quality.logValue()),
        )
        viewModelScope.launch {
            try {
                appPreferences.setPlayQuality(quality)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logPlayerError(
                    event = "player_quality_preference_failed",
                    operation = "set_quality_preference",
                    item = item,
                    error = error,
                    extra = mapOf("quality" to quality.logValue()),
                )
                throw error
            }
        }
        playerController.changeQuality(quality)
    }

    // ---- playback speed ----

    val currentSpeed: StateFlow<Float> =
        appPreferences.playRate
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackSpeeds.DEFAULT)

    fun setPlaybackSpeed(speed: Float) {
        val item = playerState.value.currentItem
        logPlayerDetail(
            event = "player_speed_change",
            operation = "set_speed",
            item = item,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("speed" to speed),
        )
        viewModelScope.launch {
            try {
                appPreferences.setPlayRate(speed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logPlayerError(
                    event = "player_speed_preference_failed",
                    operation = "set_speed_preference",
                    item = item,
                    error = error,
                    extra = mapOf("speed" to speed),
                )
                throw error
            }
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
        val item = playerState.value.currentItem
        if (item == null) {
            logPlayerDetail(
                event = "player_download_enqueue_skipped",
                operation = "download",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
                extra = mapOf("quality" to quality.logValue()),
            )
            return
        }
        try {
            downloader.enqueue(listOf(item), quality)
            logPlayerDetail(
                event = "player_download_enqueue",
                operation = "download",
                item = item,
                result = LogFields.Result.SUCCESS,
                extra = mapOf("quality" to quality.logValue()),
            )
        } catch (error: Throwable) {
            logPlayerError(
                event = "player_download_enqueue_failed",
                operation = "download",
                item = item,
                error = error,
                extra = mapOf("quality" to quality.logValue()),
            )
            throw error
        }
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
        val item = playerState.value.currentItem
        if (item == null) {
            logPlayerDetail(
                event = "player_add_to_playlist_sheet_skipped",
                operation = "show_add_to_playlist",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
            )
            return
        }
        _sheetState.value = AddToPlaylistSheetState.single(item)
        logPlayerDetail(
            event = "player_add_to_playlist_sheet_shown",
            operation = "show_add_to_playlist",
            item = item,
            result = LogFields.Result.SUCCESS,
        )
    }

    fun hideAddToPlaylistSheet() {
        val item = _sheetState.value.pendingItem
        _sheetState.value = AddToPlaylistSheetState()
        logPlayerDetail(
            event = "player_add_to_playlist_sheet_hidden",
            operation = "hide_add_to_playlist",
            item = item,
            result = LogFields.Result.SUCCESS,
        )
    }

    fun addPendingToPlaylist(targetPlaylistId: String) {
        val item = _sheetState.value.pendingItem
        if (item == null) {
            logPlayerDetail(
                event = "player_add_to_playlist_skipped",
                operation = "add_to_playlist",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_PENDING_ITEM,
                extra = mapOf("playlistId" to targetPlaylistId),
            )
            return
        }
        viewModelScope.launch {
            try {
                playlistRepository.addMusicToPlaylist(targetPlaylistId, item)
                hideAddToPlaylistSheet()
                logPlayerDetail(
                    event = "player_add_to_playlist",
                    operation = "add_to_playlist",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                    extra = mapOf("playlistId" to targetPlaylistId),
                )
            } catch (error: CancellationException) {
                logPlayerDetail(
                    event = "player_add_to_playlist_cancelled",
                    operation = "add_to_playlist",
                    item = item,
                    result = LogFields.Result.CANCELLED,
                    reason = LogFields.Reason.CANCELLED,
                    extra = mapOf("playlistId" to targetPlaylistId),
                )
                throw error
            } catch (error: Throwable) {
                logPlayerError(
                    event = "player_add_to_playlist_failed",
                    operation = "add_to_playlist",
                    item = item,
                    error = error,
                    extra = mapOf("playlistId" to targetPlaylistId),
                )
                throw error
            }
        }
    }

    fun createPlaylistAndAddPending(name: String) {
        val item = _sheetState.value.pendingItem
        if (item == null) {
            logPlayerDetail(
                event = "player_create_playlist_skipped",
                operation = "create_playlist",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_PENDING_ITEM,
            )
            return
        }
        viewModelScope.launch {
            try {
                val newId = UUID.randomUUID().toString()
                playlistRepository.createPlaylist(Playlist(id = newId, name = name, coverUri = null))
                playlistRepository.addMusicToPlaylist(newId, item)
                hideAddToPlaylistSheet()
                logPlayerDetail(
                    event = "player_create_playlist",
                    operation = "create_playlist",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                    extra = mapOf(
                        "playlistId" to newId,
                        "playlistName" to name,
                    ),
                )
            } catch (error: CancellationException) {
                logPlayerDetail(
                    event = "player_create_playlist_cancelled",
                    operation = "create_playlist",
                    item = item,
                    result = LogFields.Result.CANCELLED,
                    reason = LogFields.Reason.CANCELLED,
                    extra = mapOf("playlistName" to name),
                )
                throw error
            } catch (error: Throwable) {
                logPlayerError(
                    event = "player_create_playlist_failed",
                    operation = "create_playlist",
                    item = item,
                    error = error,
                    extra = mapOf("playlistName" to name),
                )
                throw error
            }
        }
    }

    // ---- playback controls ----

    fun togglePlayPause() {
        val state = playerState.value
        val item = state.currentItem
        if (playerState.value.isPlaying) {
            playerController.pause()
            logPlayerDetail(
                event = "player_pause",
                operation = "pause",
                item = item,
                result = LogFields.Result.SUCCESS,
                extra = mapOf("positionMs" to state.position),
            )
        } else {
            playerController.play()
            logPlayerDetail(
                event = "player_play",
                operation = "play",
                item = item,
                result = LogFields.Result.SUCCESS,
                extra = mapOf("positionMs" to state.position),
            )
        }
    }

    fun skipToNext() {
        val state = playerState.value
        playerController.skipToNext()
        logPlayerDetail(
            event = "player_skip_next",
            operation = "skip_next",
            item = state.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("positionMs" to state.position),
        )
    }

    fun skipToPrevious() {
        val state = playerState.value
        playerController.skipToPrevious()
        logPlayerDetail(
            event = "player_skip_previous",
            operation = "skip_previous",
            item = state.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("positionMs" to state.position),
        )
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
        logPlayerDetail(
            event = "player_seek",
            operation = "seek",
            item = playerState.value.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("positionMs" to positionMs),
        )
    }

    fun cycleRepeatMode() {
        val previousMode = playerState.value.repeatMode
        playerController.cycleRepeatMode()
        logPlayerDetail(
            event = "player_mode_change",
            operation = "cycle_repeat_mode",
            item = playerState.value.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("previousMode" to previousMode.name.lowercase()),
        )
    }

    fun cyclePlaybackMode() {
        val state = playerState.value
        val previousMode = PlaybackMode.from(state.shuffleEnabled, state.repeatMode).name.lowercase()
        playerController.cyclePlaybackMode()
        logPlayerDetail(
            event = "player_mode_change",
            operation = "cycle_playback_mode",
            item = playerState.value.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("previousMode" to previousMode),
        )
    }

    fun toggleShuffle() {
        val wasEnabled = playerState.value.shuffleEnabled
        playerController.toggleShuffle()
        logPlayerDetail(
            event = "player_mode_change",
            operation = "toggle_shuffle",
            item = playerState.value.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("shuffleEnabled" to !wasEnabled),
        )
    }

    fun playQueueIndex(index: Int) {
        val item = queueUiModel.value.items.getOrNull(index)
        playerController.skipTo(index)
        logPlayerDetail(
            event = if (item == null) "player_queue_index_skipped" else "player_queue_index",
            operation = "queue_index",
            item = item,
            result = if (item == null) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
            reason = if (item == null) REASON_INVALID_INDEX else null,
            extra = mapOf("index" to index, "queueSize" to queueUiModel.value.items.size),
        )
    }

    fun removeFromQueue(index: Int) {
        val item = queueUiModel.value.items.getOrNull(index)
        playerController.removeFromQueue(index)
        logPlayerDetail(
            event = if (item == null) "player_queue_remove_skipped" else "player_queue_remove",
            operation = "queue_remove",
            item = item,
            result = if (item == null) LogFields.Result.SKIPPED else LogFields.Result.SUCCESS,
            reason = if (item == null) REASON_INVALID_INDEX else null,
            extra = mapOf("index" to index, "queueSize" to queueUiModel.value.items.size),
        )
    }

    fun clearQueue() {
        val queueSize = queueUiModel.value.items.size
        playerController.reset()
        logPlayerDetail(
            event = "player_queue_clear",
            operation = "queue_clear",
            item = playerState.value.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf("queueSize" to queueSize),
        )
    }

    fun setLyricShowTranslation(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLyricShowTranslation(enabled) }
    }

    fun setLyricDetailFontSize(level: Int) {
        viewModelScope.launch { appPreferences.setLyricDetailFontSize(level) }
    }

    fun setDesktopLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                appPreferences.setDesktopLyricEnabled(enabled)
                logLyricDetail(
                    event = "desktop_lyric_toggle",
                    operation = "toggle_desktop_lyric",
                    item = playerState.value.currentItem,
                    result = LogFields.Result.SUCCESS,
                    extra = mapOf("enabled" to enabled),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logLyricError(
                    event = "desktop_lyric_toggle_failed",
                    operation = "toggle_desktop_lyric",
                    item = playerState.value.currentItem,
                    error = error,
                    extra = mapOf("enabled" to enabled),
                )
                _internalErrorEvents.tryEmit("桌面歌词设置失败: ${error.message ?: error::class.simpleName}")
            }
        }
    }

    fun associateLyricFromManualInput(input: String): Boolean {
        val target = ManualLyricAssociationParser.parse(input, fallback = playerState.value.currentItem)
            ?: return false
        associateLyric(target)
        return true
    }

    fun searchLyrics() {
        val item = playerState.value.currentItem
        if (item == null) {
            logLyricDetail(
                event = "lyric_search_skipped",
                operation = "search",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
            )
            return
        }
        viewModelScope.launch {
            _lyricSearchLoading.value = true
            _lyricSearchResults.value = emptyList()
            logLyricDetail(
                event = "lyric_search_start",
                operation = "search",
                item = item,
                result = RESULT_START,
            )
            try {
                val candidates = playerLyricLoader.searchCandidates(item)
                _lyricSearchResults.value = candidates
                logLyricDetail(
                    event = "lyric_search_success",
                    operation = "search",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                    extra = mapOf("count" to candidates.sumOf { it.items.size }),
                )
            } catch (error: CancellationException) {
                logLyricDetail(
                    event = "lyric_search_cancelled",
                    operation = "search",
                    item = item,
                    result = LogFields.Result.CANCELLED,
                    reason = LogFields.Reason.CANCELLED,
                )
                throw error
            } catch (error: Exception) {
                logLyricError(
                    event = "lyric_search_failed",
                    operation = "search",
                    item = item,
                    error = error,
                )
                _lyricSearchResults.value = emptyList()
            } finally {
                _lyricSearchLoading.value = false
            }
        }
    }

    fun associateLyric(target: MusicItem) {
        val item = playerState.value.currentItem
        if (item == null) {
            logLyricDetail(
                event = "lyric_associate_skipped",
                operation = "associate",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
                extra = targetLyricFields(target),
            )
            return
        }
        viewModelScope.launch {
            try {
                playerLyricLoader.associateLyric(item, target)
                logLyricDetail(
                    event = "lyric_associate",
                    operation = "associate",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                    extra = targetLyricFields(target),
                )
            } catch (error: CancellationException) {
                logLyricDetail(
                    event = "lyric_associate_cancelled",
                    operation = "associate",
                    item = item,
                    result = LogFields.Result.CANCELLED,
                    reason = LogFields.Reason.CANCELLED,
                    extra = targetLyricFields(target),
                )
                throw error
            } catch (error: Throwable) {
                logLyricError(
                    event = "lyric_associate_failed",
                    operation = "associate",
                    item = item,
                    error = error,
                    extra = targetLyricFields(target),
                )
                throw error
            }
        }
    }

    fun clearAssociatedLyric() {
        val item = playerState.value.currentItem
        if (item == null) {
            logLyricDetail(
                event = "lyric_associate_clear_skipped",
                operation = "clear_association",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
            )
            return
        }
        viewModelScope.launch {
            try {
                playerLyricLoader.clearAssociatedLyric(item)
                logLyricDetail(
                    event = "lyric_associate_clear",
                    operation = "clear_association",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logLyricError(
                    event = "lyric_associate_clear_failed",
                    operation = "clear_association",
                    item = item,
                    error = error,
                )
                throw error
            }
        }
    }

    fun setLyricOffset(offsetMs: Long) {
        val item = playerState.value.currentItem
        if (item == null) {
            logLyricDetail(
                event = "lyric_offset_skipped",
                operation = "set_offset",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
                extra = mapOf("positionMs" to offsetMs, "offsetMs" to offsetMs),
            )
            return
        }
        viewModelScope.launch {
            try {
                playerLyricLoader.setLyricOffset(item, offsetMs)
                logLyricDetail(
                    event = "lyric_offset",
                    operation = "set_offset",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                    extra = mapOf("positionMs" to offsetMs, "offsetMs" to offsetMs),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logLyricError(
                    event = "lyric_offset_failed",
                    operation = "set_offset",
                    item = item,
                    error = error,
                    extra = mapOf("positionMs" to offsetMs, "offsetMs" to offsetMs),
                )
                throw error
            }
        }
    }

    fun importLocalLyric(rawText: String, kind: LocalLyricKind) {
        val item = playerState.value.currentItem
        if (item == null) {
            logLyricDetail(
                event = "lyric_import_skipped",
                operation = "import",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
                extra = mapOf("kind" to kind.name.lowercase(), "textLength" to rawText.length),
            )
            return
        }
        viewModelScope.launch {
            try {
                playerLyricLoader.importLocalLyric(item, rawText, kind)
                logLyricDetail(
                    event = "lyric_import",
                    operation = "import",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                    extra = mapOf("kind" to kind.name.lowercase(), "textLength" to rawText.length),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logLyricError(
                    event = "lyric_import_failed",
                    operation = "import",
                    item = item,
                    error = error,
                    extra = mapOf("kind" to kind.name.lowercase(), "textLength" to rawText.length),
                )
                throw error
            }
        }
    }

    fun deleteLocalLyric() {
        val item = playerState.value.currentItem
        if (item == null) {
            logLyricDetail(
                event = "lyric_delete_skipped",
                operation = "delete",
                item = null,
                result = LogFields.Result.SKIPPED,
                reason = REASON_NO_CURRENT_ITEM,
            )
            return
        }
        viewModelScope.launch {
            try {
                playerLyricLoader.deleteLocalLyric(item)
                logLyricDetail(
                    event = "lyric_delete",
                    operation = "delete",
                    item = item,
                    result = LogFields.Result.SUCCESS,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logLyricError(
                    event = "lyric_delete_failed",
                    operation = "delete",
                    item = item,
                    error = error,
                )
                throw error
            }
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
        logPlayerDetail(
            event = "player_lyric_line_seek",
            operation = "seek_lyric_line",
            item = playerState.value.currentItem,
            result = LogFields.Result.SUCCESS,
            extra = mapOf(
                "lineTimeMs" to lineTimeMs,
                "positionMs" to seekMs,
            ),
        )
    }
}

private const val RESULT_START = "start"
private const val REASON_INVALID_INDEX = "invalid_index"
private const val REASON_NO_CURRENT_ITEM = "no_current_item"
private const val REASON_NO_PENDING_ITEM = "no_pending_item"
private val REASON_NONE: String? = null

private fun logPlayerDetail(
    event: String,
    operation: String,
    item: MusicItem?,
    result: String,
    reason: String? = null,
    extra: Map<String, Any?> = emptyMap(),
) {
    MfLog.detail(
        category = LogCategory.PLAYER,
        event = event,
        fields = playbackFields(
            operation = operation,
            item = item,
            result = result,
            reason = reason,
            extra = extra,
        ),
    )
}

private fun logPlayerError(
    event: String,
    operation: String,
    item: MusicItem?,
    error: Throwable,
    reason: String = LogFields.Reason.UNKNOWN,
    extra: Map<String, Any?> = emptyMap(),
) {
    MfLog.error(
        category = LogCategory.PLAYER,
        event = event,
        throwable = error,
        fields = playbackFields(
            operation = operation,
            item = item,
            result = LogFields.Result.FAILURE,
            reason = reason,
            extra = extra,
        ),
    )
}

private fun logLyricDetail(
    event: String,
    operation: String,
    item: MusicItem?,
    result: String,
    reason: String? = null,
    extra: Map<String, Any?> = emptyMap(),
) {
    MfLog.detail(
        category = LogCategory.LYRICS,
        event = event,
        fields = playbackFields(
            operation = operation,
            item = item,
            result = result,
            reason = reason,
            extra = extra,
        ),
    )
}

private fun logLyricError(
    event: String,
    operation: String,
    item: MusicItem?,
    error: Throwable,
    reason: String = LogFields.Reason.UNKNOWN,
    extra: Map<String, Any?> = emptyMap(),
) {
    MfLog.error(
        category = LogCategory.LYRICS,
        event = event,
        throwable = error,
        fields = playbackFields(
            operation = operation,
            item = item,
            result = LogFields.Result.FAILURE,
            reason = reason,
            extra = extra,
        ),
    )
}

private fun playbackFields(
    operation: String,
    item: MusicItem?,
    result: String,
    reason: String?,
    extra: Map<String, Any?>,
): Map<String, Any?> {
    val fields = linkedMapOf<String, Any?>(
        "screen" to "player",
        "operation" to operation,
        "result" to result,
    )
    if (reason != null) fields["reason"] = reason
    item?.let {
        fields["itemId"] = it.id
        fields["itemName"] = it.title
        fields["platform"] = it.platform
    }
    fields.putAll(extra.filterValues { it != null })
    return fields
}

private fun targetLyricFields(target: MusicItem): Map<String, Any?> = mapOf(
    "targetItemId" to target.id,
    "targetItemName" to target.title,
    "targetPlatform" to target.platform,
)

private fun PlayQuality.logValue(): String = name.lowercase()

private fun MusicItem.sameMusicKey(other: MusicItem): Boolean =
    platform == other.platform && id == other.id
