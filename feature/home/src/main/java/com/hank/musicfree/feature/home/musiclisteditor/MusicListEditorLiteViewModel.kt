package com.hank.musicfree.feature.home.musiclisteditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.navigation.MusicListEditorLiteRoute
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MusicListEditorLiteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    private val playerController: PlayerController,
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val route: MusicListEditorLiteRoute = savedStateHandle.toMusicListEditorLiteRoute()
    private val sourceId: String = route.sourceId
    private val isLocalLibrary: Boolean =
        route.sourceType == MusicListEditorLiteRoute.SOURCE_TYPE_LOCAL_LIBRARY
    private val playlistId: String = sourceId

    private val playlistName = MutableStateFlow("")
    private val editableItems = MutableStateFlow<List<MusicItem>>(emptyList())
    private val selectedItemKeys = MutableStateFlow<Set<String>>(emptySet())
    private val hasPendingChanges = MutableStateFlow(false)

    private var baselineItems: List<MusicItem> = emptyList()

    val uiState: StateFlow<MusicListEditorLiteUiState> = combine(
        playlistName,
        editableItems,
        selectedItemKeys,
        hasPendingChanges,
        playlistRepository.observeAllPlaylists(),
    ) { name, items, selectedKeys, pendingChanges, playlists ->
        MusicListEditorLiteUiState(
            playlistName = name,
            items = items,
            selectedItemKeys = selectedKeys,
            selectedCount = items.count { itemKey(it) in selectedKeys },
            hasPendingChanges = pendingChanges,
            availableTargetPlaylists = if (isLocalLibrary) {
                playlists
            } else {
                playlists.filterNot { it.id == playlistId }
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MusicListEditorLiteUiState(),
    )

    init {
        viewModelScope.launch {
            playlistName.value = if (isLocalLibrary) {
                "本地音乐"
            } else {
                playlistRepository.getPlaylistById(playlistId)?.name.orEmpty()
            }
        }
        viewModelScope.launch {
            val sourceItems = if (isLocalLibrary) {
                musicRepository.observeLocalLibrary()
            } else {
                playlistRepository.observeMusicInPlaylist(playlistId)
            }
            sourceItems.collect { items ->
                val stagedRemovedKeys = if (hasPendingChanges.value) {
                    stagedRemovedKeys()
                } else {
                    emptySet()
                }

                baselineItems = items
                editableItems.value = if (stagedRemovedKeys.isEmpty()) {
                    items
                } else {
                    items.filterNot { itemKey(it) in stagedRemovedKeys }
                }
                selectedItemKeys.value = selectedItemKeys.value.filter { selectedKey ->
                    editableItems.value.any { itemKey(it) == selectedKey }
                }.toSet()
                hasPendingChanges.value = hasChangedFromBaseline()
            }
        }
    }

    fun toggleSelection(item: MusicItem) {
        val key = itemKey(item)
        if (editableItems.value.none { itemKey(it) == key }) return
        selectedItemKeys.value = selectedItemKeys.value.toMutableSet().apply {
            if (!add(key)) remove(key)
        }
    }

    fun selectAll() {
        selectedItemKeys.value = editableItems.value.mapTo(mutableSetOf(), ::itemKey)
    }

    fun clearSelection() {
        selectedItemKeys.value = emptySet()
    }

    fun removeSelectedFromPlaylist() {
        val selectedKeys = selectedItemKeys.value
        if (selectedKeys.isEmpty()) {
            logEditorSkipped("stage_remove_selected", "empty_selection")
            return
        }
        val removedCount = editableItems.value.count { itemKey(it) in selectedKeys }
        MfLog.detail(
            LogCategory.HOME,
            "music_list_editor_batch_edit_staged",
            editorFields("stage_remove_selected") + mapOf(
                "count" to removedCount,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        editableItems.value = editableItems.value.filterNot { itemKey(it) in selectedKeys }
        selectedItemKeys.value = emptySet()
        hasPendingChanges.value = hasChangedFromBaseline()
    }

    fun saveChanges() {
        val currentItems = editableItems.value
        val removedItems = baselineItems.filter { baselineItem ->
            currentItems.none { currentItem -> itemKey(currentItem) == itemKey(baselineItem) }
        }
        if (removedItems.isEmpty()) {
            logEditorSkipped("save_batch_edit", "empty_changes")
            return
        }

        viewModelScope.launch {
            runEditorAction(
                eventPrefix = "music_list_editor_batch_edit",
                operation = "save_batch_edit",
                fields = mapOf("count" to removedItems.size),
            ) {
                removedItems.forEach { item ->
                    if (isLocalLibrary) {
                        musicRepository.removeFromLocalLibrary(item)
                    } else {
                        playlistRepository.removeMusicFromPlaylist(playlistId, item)
                    }
                }
                baselineItems = currentItems
                hasPendingChanges.value = false
            }
        }
    }

    fun addSelectedToNextQueue() {
        val selected = selectedItemsInDisplayOrder()
        if (selected.isEmpty()) {
            logEditorSkipped("add_selected_to_next_queue", "empty_selection")
            return
        }
        MfLog.detail(
            LogCategory.HOME,
            "music_list_editor_add_next",
            editorFields("add_selected_to_next_queue") + mapOf(
                "count" to selected.size,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        selected.asReversed().forEach(playerController::addNextInQueue)
    }

    fun addSelectedToPlaylist(targetPlaylistId: String) {
        val selectedItems = selectedItemsInDisplayOrder()
        if (selectedItems.isEmpty()) {
            logEditorSkipped("add_selected_to_playlist", "empty_selection")
            return
        }

        viewModelScope.launch {
            runEditorAction(
                eventPrefix = "music_list_editor_add_to_playlist",
                operation = "add_selected_to_playlist",
                fields = mapOf("playlistId" to targetPlaylistId, "count" to selectedItems.size),
            ) {
                playlistRepository.addMusicsToPlaylist(targetPlaylistId, selectedItems)
            }
        }
    }

    fun createPlaylistAndAddSelected(name: String) {
        val selectedItems = selectedItemsInDisplayOrder()
        val trimmedName = name.trim()
        if (selectedItems.isEmpty() || trimmedName.isBlank()) {
            logEditorSkipped(
                operation = "create_playlist_and_add_selected",
                reason = if (selectedItems.isEmpty()) "empty_selection" else LogFields.Reason.EMPTY_INPUT,
            )
            return
        }

        viewModelScope.launch {
            val playlist = Playlist(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                coverUri = null,
            )
            runEditorAction(
                eventPrefix = "music_list_editor_create_playlist",
                operation = "create_playlist_and_add_selected",
                fields = mapOf(
                    "playlistId" to playlist.id,
                    "itemName" to trimmedName,
                    "count" to selectedItems.size,
                ),
            ) {
                playlistRepository.createPlaylist(playlist)
                playlistRepository.addMusicsToPlaylist(playlist.id, selectedItems)
            }
        }
    }

    fun downloadSelected() {
        val items = selectedItemsInDisplayOrder()
        if (items.isEmpty()) {
            logEditorSkipped("download_selected", "empty_selection")
            return
        }
        viewModelScope.launch {
            val quality = appPreferences.defaultDownloadQuality.first()
            MfLog.detail(
                LogCategory.DOWNLOAD,
                "download_intent",
                editorFields("download_selected") + mapOf(
                    "quality" to quality.name,
                    "count" to items.size,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            downloader.enqueue(items, quality)
        }
    }

    private fun selectedItemsInDisplayOrder(): List<MusicItem> {
        val selectedKeys = selectedItemKeys.value
        return editableItems.value.filter { itemKey(it) in selectedKeys }
    }

    private fun hasChangedFromBaseline(): Boolean {
        val currentKeys = editableItems.value.map(::itemKey)
        val baselineKeys = baselineItems.map(::itemKey)
        return currentKeys != baselineKeys
    }

    private fun stagedRemovedKeys(): Set<String> {
        val editableKeys = editableItems.value.mapTo(mutableSetOf(), ::itemKey)
        return baselineItems.mapTo(mutableSetOf(), ::itemKey).filterNot(editableKeys::contains).toSet()
    }

    private fun itemKey(item: MusicItem): String = "${item.platform}:${item.id}"

    private suspend fun runEditorAction(
        eventPrefix: String,
        operation: String,
        fields: Map<String, Any?> = emptyMap(),
        block: suspend () -> Unit,
    ) {
        val startedAt = System.nanoTime()
        val baseFields = editorFields(operation)
        MfLog.detail(
            LogCategory.HOME,
            "${eventPrefix}_start",
            baseFields + fields,
        )
        try {
            block()
            MfLog.detail(
                LogCategory.HOME,
                "${eventPrefix}_success",
                baseFields + fields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (e: CancellationException) {
            MfLog.detail(
                LogCategory.HOME,
                "${eventPrefix}_cancelled",
                baseFields + fields + mapOf(
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
                baseFields + fields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            throw e
        }
    }

    private fun logEditorSkipped(operation: String, reason: String) {
        MfLog.detail(
            LogCategory.HOME,
            "music_list_editor_action_skipped",
            editorFields(operation) + mapOf(
                "result" to LogFields.Result.SKIPPED,
                "reason" to reason,
            ),
        )
    }

    private fun editorFields(operation: String): Map<String, Any?> = mapOf(
        "screen" to SCREEN_MUSIC_LIST_EDITOR,
        "operation" to operation,
        "flowId" to "$SCREEN_MUSIC_LIST_EDITOR:$operation:${System.nanoTime()}",
        "playlistId" to playlistId,
        "sourceType" to route.sourceType,
    )

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val SCREEN_MUSIC_LIST_EDITOR = "music_list_editor"
    }
}

private fun SavedStateHandle.toMusicListEditorLiteRoute(): MusicListEditorLiteRoute {
    val sourceType = get<String>("sourceType")
    val sourceId = get<String>("sourceId")
    if (sourceType != null && sourceId != null) {
        return MusicListEditorLiteRoute(
            sourceId = sourceId,
            sourceType = sourceType,
        )
    }

    val playlistId = get<String>("playlistId")
    if (playlistId != null) {
        return MusicListEditorLiteRoute(playlistId)
    }

    return toRoute()
}
