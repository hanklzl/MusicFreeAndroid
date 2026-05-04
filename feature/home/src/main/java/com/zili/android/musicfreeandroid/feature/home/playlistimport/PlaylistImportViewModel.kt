package com.zili.android.musicfreeandroid.feature.home.playlistimport

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private companion object {
        const val TAG = "PlaylistImportViewModel"
        const val PARSE_ERROR_TOAST = "链接有误或目标歌单为空"
        const val PARSE_EXCEPTION_TOAST = "歌单导入失败"
        const val IMPORT_ERROR_TOAST = "导入失败，请重试"
    }

    private val _importState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    private val _events = Channel<PlaylistImportEvent>(capacity = Channel.BUFFERED)
    val events: Flow<PlaylistImportEvent> = _events.receiveAsFlow()

    private var activeJob: Job? = null
    private var targetImportInProgress = false

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun openImportSheet() {
        _importState.value = PlaylistImportState.LoadingPlugins

        launchImportJob {
            try {
                pluginManager.ensurePluginsLoaded()
                currentCoroutineContext().ensureActive()
                val plugins = pluginManager.getSortedEnabledPlugins().first()
                    .mapNotNull { plugin ->
                        if (!plugin.info.supportedMethods.contains("importMusicSheet")) return@mapNotNull null

                        ImportCapablePlugin(
                            platform = plugin.info.platform,
                            name = plugin.info.platform,
                            hints = plugin.info.hints?.get("importMusicSheet") ?: emptyList(),
                        )
                    }
                currentCoroutineContext().ensureActive()

                _importState.value = PlaylistImportState.ChoosePlugin(plugins)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("Failed to load import-capable plugins", e)
                postToast(PARSE_EXCEPTION_TOAST)
                _importState.value = PlaylistImportState.Idle
            }
        }
    }

    fun selectPlugin(platform: String) {
        val plugins = (_importState.value as? PlaylistImportState.ChoosePlugin)?.plugins ?: return
        val plugin = plugins.firstOrNull { it.platform == platform } ?: return
        _importState.value = PlaylistImportState.InputUrl(plugin)
    }

    fun submitUrl(urlLike: String) {
        val state = _importState.value as? PlaylistImportState.InputUrl ?: return

        val trimmed = urlLike.trim()
        if (trimmed.isBlank()) {
            _importState.value = state.copy(errorMessage = PARSE_ERROR_TOAST)
            return
        }

        _importState.value = PlaylistImportState.Parsing(state.plugin.name)
        launchImportJob {
            val plugin = pluginManager.getPlugin(state.plugin.platform)
            if (plugin == null) {
                postToast(PARSE_ERROR_TOAST)
                _importState.value = PlaylistImportState.Idle
                return@launchImportJob
            }

            try {
                val parsedItems = plugin.importMusicSheet(trimmed)
                currentCoroutineContext().ensureActive()
                if (parsedItems == null || parsedItems.isEmpty()) {
                    postToast(PARSE_ERROR_TOAST)
                    _importState.value = PlaylistImportState.Idle
                    return@launchImportJob
                }

                _importState.value = PlaylistImportState.ConfirmFound(state.plugin, parsedItems)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("Failed to import playlist from ${state.plugin.platform}", e)
                postToast(PARSE_EXCEPTION_TOAST)
                _importState.value = PlaylistImportState.Idle
            }
        }
    }

    fun confirmFoundItems() {
        val state = _importState.value as? PlaylistImportState.ConfirmFound ?: return
        _sheetState.value = AddToPlaylistSheetState.batch(state.items)
        _importState.value = PlaylistImportState.ChooseTarget(state.items)
    }

    fun confirmImportTarget(items: List<MusicItem>) {
        if (items.isEmpty()) return
        _sheetState.value = AddToPlaylistSheetState.batch(items)
        _importState.value = PlaylistImportState.ChooseTarget(items)
    }

    fun hideTargetSheet() {
        cancelActiveJob()
        targetImportInProgress = false
        _sheetState.value = AddToPlaylistSheetState()
        _importState.value = PlaylistImportState.Idle
    }

    fun dismissImportFlow() {
        cancelActiveJob()
        targetImportInProgress = false
        _sheetState.value = AddToPlaylistSheetState()
        _importState.value = PlaylistImportState.Idle
    }

    fun addImportedItemsToPlaylist(targetPlaylistId: String) {
        val items = currentPendingItems()
        if (!beginTargetImport(items)) return

        launchImportJob {
            try {
                val added = playlistRepository.addMusicsToPlaylist(targetPlaylistId, items)
                currentCoroutineContext().ensureActive()
                val skipped = items.size - added
                _sheetState.value = AddToPlaylistSheetState()
                _importState.value = PlaylistImportState.Completed(
                    added = added,
                    skipped = skipped,
                )
                postToast(importResultMessage(added, skipped))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("Failed to add imported items to playlist $targetPlaylistId", e)
                postToast(IMPORT_ERROR_TOAST)
                _sheetState.value = AddToPlaylistSheetState.batch(items)
                _importState.value = PlaylistImportState.ChooseTarget(items)
            } finally {
                targetImportInProgress = false
            }
        }
    }

    fun createPlaylistAndImport(name: String) {
        val playlistName = name.trim()
        if (playlistName.isBlank()) return

        val items = currentPendingItems()
        if (!beginTargetImport(items)) return

        launchImportJob {
            val playlistId = UUID.randomUUID().toString()
            var createdPlaylist: Playlist? = null

            try {
                val playlist = Playlist(
                    id = playlistId,
                    name = playlistName,
                    coverUri = null,
                )
                createdPlaylist = playlist
                playlistRepository.createPlaylist(playlist)
                currentCoroutineContext().ensureActive()

                val added = playlistRepository.addMusicsToPlaylist(playlistId, items)
                currentCoroutineContext().ensureActive()
                val skipped = items.size - added
                _sheetState.value = AddToPlaylistSheetState()
                _importState.value = PlaylistImportState.Completed(
                    added = added,
                    skipped = skipped,
                )
                postToast(importResultMessage(added, skipped))
            } catch (e: CancellationException) {
                createdPlaylist?.let { playlist ->
                    withContext(NonCancellable) { cleanupCreatedPlaylist(playlist) }
                }
                throw e
            } catch (e: Exception) {
                logError("Failed to create playlist and import items", e)
                createdPlaylist?.let { cleanupCreatedPlaylist(it) }
                postToast(IMPORT_ERROR_TOAST)
                _sheetState.value = AddToPlaylistSheetState.batch(items)
                _importState.value = PlaylistImportState.ChooseTarget(items)
            } finally {
                targetImportInProgress = false
            }
        }
    }

    private fun launchImportJob(block: suspend () -> Unit) {
        activeJob?.cancel()
        val job = viewModelScope.launch { block() }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) {
                activeJob = null
            }
        }
    }

    private fun cancelActiveJob() {
        activeJob?.cancel()
        activeJob = null
    }

    private fun beginTargetImport(items: List<MusicItem>): Boolean {
        if (items.isEmpty() || targetImportInProgress) return false
        targetImportInProgress = true
        _sheetState.value = AddToPlaylistSheetState()
        _importState.value = PlaylistImportState.Parsing("导入歌单")
        return true
    }

    private fun currentPendingItems(): List<MusicItem> =
        when (val state = _importState.value) {
            is PlaylistImportState.ChooseTarget -> state.items
            is PlaylistImportState.ConfirmFound -> state.items
            else -> _sheetState.value.pendingItems
        }

    private fun importResultMessage(added: Int, skipped: Int): String =
        if (skipped > 0) {
            "已导入 $added 首，跳过 $skipped 首重复歌曲"
        } else {
            "已导入 $added 首"
        }

    private suspend fun cleanupCreatedPlaylist(playlist: Playlist) {
        runCatching { playlistRepository.deletePlaylist(playlist) }
            .onFailure { e -> logError("Failed to clean up playlist ${playlist.id}", e) }
    }

    private fun postToast(message: String) {
        val result = _events.trySend(PlaylistImportEvent.Toast(message))
        if (result.isFailure) {
            val cause = result.exceptionOrNull()
                ?: IllegalStateException("Playlist import event channel rejected toast")
            logError("Failed to enqueue playlist import event", cause)
        }
    }

    private fun logError(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
