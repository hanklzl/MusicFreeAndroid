package com.hank.musicfree.feature.home.playlist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.core.navigation.PlaylistDetailRoute
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
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
        _sheetState.value = AddToPlaylistSheetState.single(item)
    }

    fun hideAddToPlaylistSheet() {
        _sheetState.value = AddToPlaylistSheetState()
    }

    fun addPendingToPlaylist(targetPlaylistId: String) {
        val item = _sheetState.value.pendingItem ?: return
        viewModelScope.launch {
            runPlaylistDetailAction(
                eventPrefix = "playlist_add_item",
                operation = "add_to_playlist",
                fields = musicItemFields(item) + mapOf(
                    "playlistId" to targetPlaylistId,
                    "sourcePlaylistId" to playlistId,
                ),
            ) {
                playlistRepository.addMusicToPlaylist(targetPlaylistId, item)
                hideAddToPlaylistSheet()
            }
        }
    }

    fun createPlaylistAndAddPending(name: String) {
        val item = _sheetState.value.pendingItem ?: return
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            runPlaylistDetailAction(
                eventPrefix = "playlist_create_and_add",
                operation = "create_playlist_and_add",
                fields = musicItemFields(item) + mapOf(
                    "playlistId" to newId,
                    "sourcePlaylistId" to playlistId,
                    "itemName" to name,
                ),
            ) {
                playlistRepository.createPlaylist(
                    Playlist(id = newId, name = name, coverUri = null)
                )
                playlistRepository.addMusicToPlaylist(newId, item)
                hideAddToPlaylistSheet()
            }
        }
    }

    fun playAll(startIndex: Int = 0) {
        val items = uiState.value.musics
        if (items.isNotEmpty()) {
            MfLog.detail(
                LogCategory.HOME,
                "playlist_play_item",
                mapOf(
                    "screen" to SCREEN_PLAYLIST_DETAIL,
                    "operation" to "play_all",
                    "playlistId" to playlistId,
                    "page" to startIndex,
                    "count" to items.size,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            playerController.playQueue(items, startIndex)
        }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch {
            runPlaylistDetailAction(
                eventPrefix = "playlist_sort",
                operation = "set_sort_mode",
                fields = mapOf("playlistId" to playlistId, "sortMode" to mode.name),
            ) {
                if (mode == SortMode.Manual) {
                    val currentVisualOrder = playlistRepository.observeMusicInPlaylist(playlistId).first()
                    playlistRepository.setSortMode(playlistId, SortMode.Manual)
                    playlistRepository.applyManualSortOrder(playlistId, currentVisualOrder)
                } else {
                    playlistRepository.setSortMode(playlistId, mode)
                }
            }
        }
    }

    fun updateInfo(name: String?, description: String?, coverUri: Uri?) {
        viewModelScope.launch {
            runPlaylistDetailAction(
                eventPrefix = "playlist_update_info",
                operation = "update_playlist_info",
                fields = mapOf(
                    "playlistId" to playlistId,
                    "itemName" to name.orEmpty(),
                    "coverUpdated" to (coverUri != null),
                ),
            ) {
                playlistRepository.updatePlaylistInfo(playlistId, name, description)
                if (coverUri != null) playlistRepository.setCover(playlistId, coverUri)
            }
        }
    }

    fun deletePlaylistAndExit(onDone: () -> Unit) {
        val current = uiState.value.playlist ?: return
        viewModelScope.launch {
            val flowId = newFlowId("delete_playlist")
            val startedAt = System.nanoTime()
            MfLog.detail(
                LogCategory.HOME,
                "playlist_delete_start",
                playlistFields(current) + mapOf(
                    "screen" to SCREEN_PLAYLIST_DETAIL,
                    "operation" to "delete_playlist",
                    "flowId" to flowId,
                ),
            )
            try {
                playlistRepository.deletePlaylist(current)
                MfLog.detail(
                    LogCategory.HOME,
                    "playlist_delete_success",
                    playlistFields(current) + mapOf(
                        "screen" to SCREEN_PLAYLIST_DETAIL,
                        "operation" to "delete_playlist",
                        "flowId" to flowId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
                onDone()
            } catch (e: CancellationException) {
                MfLog.detail(
                    LogCategory.HOME,
                    "playlist_delete_cancelled",
                    playlistFields(current) + mapOf(
                        "screen" to SCREEN_PLAYLIST_DETAIL,
                        "operation" to "delete_playlist",
                        "flowId" to flowId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.CANCELLED,
                        "reason" to LogFields.Reason.CANCELLED,
                    ),
                )
                throw e
            } catch (e: IllegalStateException) {
                MfLog.error(
                    LogCategory.HOME,
                    "playlist_delete_failed",
                    e,
                    playlistFields(current) + mapOf(
                        "screen" to SCREEN_PLAYLIST_DETAIL,
                        "operation" to "delete_playlist",
                        "flowId" to flowId,
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.FAILURE,
                        "reason" to "default_playlist",
                    ),
                )
                // favorite — already filtered by UI but defensive guard
            }
        }
    }

    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch {
            runPlaylistDetailAction(
                eventPrefix = "playlist_toggle_favorite",
                operation = "toggle_favorite",
                fields = musicItemFields(item) + mapOf("playlistId" to playlistId),
            ) {
                playlistRepository.toggleFavorite(item)
            }
        }
    }

    fun removeFromPlaylist(item: MusicItem) {
        viewModelScope.launch {
            runPlaylistDetailAction(
                eventPrefix = "playlist_remove_item",
                operation = "remove_from_playlist",
                fields = musicItemFields(item) + mapOf("playlistId" to playlistId),
            ) {
                playlistRepository.removeMusicFromPlaylist(playlistId, item)
            }
        }
    }

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        MfLog.detail(
            LogCategory.DOWNLOAD,
            "download_intent",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_PLAYLIST_DETAIL,
                "operation" to "download",
                "playlistId" to playlistId,
                "quality" to quality.name,
                "count" to 1,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        downloader.enqueue(listOf(item), quality)
    }

    private suspend fun runPlaylistDetailAction(
        eventPrefix: String,
        operation: String,
        fields: Map<String, Any?> = emptyMap(),
        block: suspend () -> Unit,
    ) {
        val flowId = newFlowId(operation)
        val startedAt = System.nanoTime()
        MfLog.detail(
            LogCategory.HOME,
            "${eventPrefix}_start",
            fields + mapOf(
                "screen" to SCREEN_PLAYLIST_DETAIL,
                "operation" to operation,
                "flowId" to flowId,
            ),
        )
        try {
            block()
            MfLog.detail(
                LogCategory.HOME,
                "${eventPrefix}_success",
                fields + mapOf(
                    "screen" to SCREEN_PLAYLIST_DETAIL,
                    "operation" to operation,
                    "flowId" to flowId,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (e: CancellationException) {
            MfLog.detail(
                LogCategory.HOME,
                "${eventPrefix}_cancelled",
                fields + mapOf(
                    "screen" to SCREEN_PLAYLIST_DETAIL,
                    "operation" to operation,
                    "flowId" to flowId,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw e
        } catch (e: Exception) {
            MfLog.error(
                LogCategory.HOME,
                "${eventPrefix}_failed",
                e,
                fields + mapOf(
                    "screen" to SCREEN_PLAYLIST_DETAIL,
                    "operation" to operation,
                    "flowId" to flowId,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            throw e
        }
    }

    private fun musicItemFields(item: MusicItem): Map<String, Any?> = mapOf(
        "platform" to item.platform,
        "itemId" to item.id,
        "itemName" to item.title,
    )

    private fun playlistFields(playlist: Playlist): Map<String, Any?> = mapOf(
        "playlistId" to playlist.id,
        "itemName" to playlist.name,
    )

    private fun newFlowId(operation: String): String = "$SCREEN_PLAYLIST_DETAIL:$operation:${System.nanoTime()}"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val SCREEN_PLAYLIST_DETAIL = "playlist_detail"
    }
}
