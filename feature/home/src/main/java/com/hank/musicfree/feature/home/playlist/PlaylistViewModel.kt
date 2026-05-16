package com.hank.musicfree.feature.home.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val playlistId = UUID.randomUUID().toString()
            runPlaylistAction(
                eventPrefix = "playlist_create",
                operation = "create_playlist",
                fields = mapOf("playlistId" to playlistId, "itemName" to name),
            ) {
                playlistRepository.createPlaylist(
                    Playlist(id = playlistId, name = name, coverUri = null)
                )
            }
        }
    }

    fun renamePlaylist(playlist: Playlist, newName: String) {
        viewModelScope.launch {
            runPlaylistAction(
                eventPrefix = "playlist_rename",
                operation = "rename_playlist",
                fields = playlistFields(playlist) + mapOf("itemName" to newName),
            ) {
                playlistRepository.updatePlaylistInfo(id = playlist.id, name = newName)
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            runPlaylistAction(
                eventPrefix = "playlist_delete",
                operation = "delete_playlist",
                fields = playlistFields(playlist),
            ) {
                playlistRepository.deletePlaylist(playlist)
            }
        }
    }

    private suspend fun runPlaylistAction(
        eventPrefix: String,
        operation: String,
        fields: Map<String, Any?> = emptyMap(),
        block: suspend () -> Unit,
    ) {
        val flowId = "$SCREEN_PLAYLIST:$operation:${System.nanoTime()}"
        val startedAt = System.nanoTime()
        MfLog.detail(
            LogCategory.HOME,
            "${eventPrefix}_start",
            fields + mapOf(
                "screen" to SCREEN_PLAYLIST,
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
                    "screen" to SCREEN_PLAYLIST,
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
                    "screen" to SCREEN_PLAYLIST,
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
                    "screen" to SCREEN_PLAYLIST,
                    "operation" to operation,
                    "flowId" to flowId,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            throw e
        }
    }

    private fun playlistFields(playlist: Playlist): Map<String, Any?> = mapOf(
        "playlistId" to playlist.id,
        "itemName" to playlist.name,
    )

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val SCREEN_PLAYLIST = "playlist"
    }
}
