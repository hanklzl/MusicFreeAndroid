package com.zili.android.musicfreeandroid.feature.home.playlistimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private companion object {
        const val PARSE_ERROR_TOAST = "链接有误或目标歌单为空"
        const val PARSE_EXCEPTION_TOAST = "歌单导入失败"
        const val IMPORT_ERROR_TOAST = "导入失败，请重试"
    }

    private val _importState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    private val _events = MutableSharedFlow<PlaylistImportEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlaylistImportEvent> = _events.asSharedFlow()

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun openImportSheet() {
        _importState.value = PlaylistImportState.LoadingPlugins

        viewModelScope.launch {
            try {
                pluginManager.ensurePluginsLoaded()
                val plugins = pluginManager.getSortedEnabledPlugins().first()
                    .mapNotNull { plugin ->
                        if (!plugin.info.supportedMethods.contains("importMusicSheet")) return@mapNotNull null

                        ImportCapablePlugin(
                            platform = plugin.info.platform,
                            name = plugin.info.platform,
                            hints = plugin.info.hints?.get("importMusicSheet") ?: emptyList(),
                        )
                    }

                _importState.value = PlaylistImportState.ChoosePlugin(plugins)
            } catch (_: Exception) {
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
        viewModelScope.launch {
            val plugin = pluginManager.getPlugin(state.plugin.platform)
            if (plugin == null) {
                postToast(PARSE_ERROR_TOAST)
                _importState.value = PlaylistImportState.Idle
                return@launch
            }

            try {
                val parsedItems = plugin.importMusicSheet(trimmed)
                if (parsedItems == null || parsedItems.isEmpty()) {
                    postToast(PARSE_ERROR_TOAST)
                    _importState.value = PlaylistImportState.Idle
                    return@launch
                }

                _importState.value = PlaylistImportState.ConfirmFound(state.plugin, parsedItems)
            } catch (_: Exception) {
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
        _sheetState.value = AddToPlaylistSheetState()
        _importState.value = PlaylistImportState.Idle
    }

    fun dismissImportFlow() {
        _sheetState.value = AddToPlaylistSheetState()
        _importState.value = PlaylistImportState.Idle
    }

    fun addImportedItemsToPlaylist(targetPlaylistId: String) {
        val items = currentPendingItems()
        if (items.isEmpty()) return

        viewModelScope.launch {
            try {
                val added = playlistRepository.addMusicsToPlaylist(targetPlaylistId, items)
                val skipped = items.size - added
                _sheetState.value = AddToPlaylistSheetState()
                _importState.value = PlaylistImportState.Completed(
                    added = added,
                    skipped = skipped,
                )
                postToast(importResultMessage(added, skipped))
            } catch (_: Exception) {
                postToast(IMPORT_ERROR_TOAST)
                _importState.value = PlaylistImportState.ChooseTarget(items)
            }
        }
    }

    fun createPlaylistAndImport(name: String) {
        val playlistName = name.trim()
        if (playlistName.isBlank()) return

        val items = currentPendingItems()
        if (items.isEmpty()) return

        viewModelScope.launch {
            val playlistId = UUID.randomUUID().toString()

            try {
                playlistRepository.createPlaylist(
                    Playlist(
                        id = playlistId,
                        name = playlistName,
                        coverUri = null,
                    ),
                )

                val added = playlistRepository.addMusicsToPlaylist(playlistId, items)
                val skipped = items.size - added
                _sheetState.value = AddToPlaylistSheetState()
                _importState.value = PlaylistImportState.Completed(
                    added = added,
                    skipped = skipped,
                )
                postToast(importResultMessage(added, skipped))
            } catch (_: Exception) {
                postToast(IMPORT_ERROR_TOAST)
                _importState.value = PlaylistImportState.ChooseTarget(items)
            }
        }
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

    private suspend fun postToast(message: String) {
        _events.emit(PlaylistImportEvent.Toast(message))
    }
}
