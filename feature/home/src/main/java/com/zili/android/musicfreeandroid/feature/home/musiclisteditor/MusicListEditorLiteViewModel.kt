package com.zili.android.musicfreeandroid.feature.home.musiclisteditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.MusicListEditorLiteRoute
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
                musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)
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
        if (selectedKeys.isEmpty()) return
        editableItems.value = editableItems.value.filterNot { itemKey(it) in selectedKeys }
        selectedItemKeys.value = emptySet()
        hasPendingChanges.value = hasChangedFromBaseline()
    }

    fun saveChanges() {
        val currentItems = editableItems.value
        val removedItems = baselineItems.filter { baselineItem ->
            currentItems.none { currentItem -> itemKey(currentItem) == itemKey(baselineItem) }
        }
        if (removedItems.isEmpty()) return

        viewModelScope.launch {
            removedItems.forEach { item ->
                if (isLocalLibrary) {
                    musicRepository.delete(item)
                } else {
                    playlistRepository.removeMusicFromPlaylist(playlistId, item)
                }
            }
            baselineItems = currentItems
            hasPendingChanges.value = false
        }
    }

    fun addSelectedToNextQueue() {
        selectedItemsInDisplayOrder().asReversed().forEach(playerController::addNextInQueue)
    }

    fun addSelectedToPlaylist(targetPlaylistId: String) {
        val selectedItems = selectedItemsInDisplayOrder()
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            selectedItems.forEach { item ->
                playlistRepository.addMusicToPlaylist(targetPlaylistId, item)
            }
        }
    }

    fun downloadSelected() {
        val items = selectedItemsInDisplayOrder()
        if (items.isEmpty()) return
        viewModelScope.launch {
            val quality = appPreferences.defaultDownloadQuality.first()
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
