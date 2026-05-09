package com.zili.android.musicfreeandroid.feature.home.pluginsheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetRouteSeedResolver
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.fallbackSheetSeed
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PluginSheetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<PluginSheetDetailRoute>()
    private val seedResolver = PluginSheetRouteSeedResolver(route.seedToken) {
        route.fallbackSheetSeed()
    }

    private val _uiState = MutableStateFlow(PluginSheetDetailUiState(loading = true))
    val uiState: StateFlow<PluginSheetDetailUiState> = _uiState.asStateFlow()

    private var page = 0
    private var currentSheet: MusicSheetItemBase? = null

    // ── Playlist / Favorite ──────────────────────────────────────────────────

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isFavoriteFlow(item: MusicItem): Flow<Boolean> = playlistRepository.isFavorite(item)

    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch { playlistRepository.toggleFavorite(item) }
    }

    fun showAddToPlaylistSheet(item: MusicItem) {
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

    // ── Loading ──────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
            loadInitial()
        }
    }

    fun retry() {
        viewModelScope.launch {
            loadInitial()
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val sheet = currentSheet
        if (state.loading || state.loadingMore || state.isEnd || sheet == null) {
            return
        }

        val plugin = pluginManager.getPlugin(route.pluginPlatform) ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true, errorMessage = null)
            runCatching {
                plugin.getMusicSheetInfo(sheet, page + 1)
            }.onSuccess { detail ->
                if (detail == null) {
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        errorMessage = "加载歌单失败",
                    )
                    return@onSuccess
                }
                page += 1
                currentSheet = detail.sheetItem ?: sheet
                _uiState.value = _uiState.value.copy(
                    title = detail.sheetItem?.title ?: _uiState.value.title,
                    sheetItem = currentSheet,
                    musicList = _uiState.value.musicList + detail.musicList,
                    isEnd = detail.isEnd,
                    loadingMore = false,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    errorMessage = e.message ?: "加载歌单失败",
                )
            }
        }
    }

    suspend fun playAt(index: Int): Boolean {
        val list = _uiState.value.musicList
        if (index !in list.indices) {
            return false
        }

        val plugin = pluginManager.getPlugin(route.pluginPlatform) ?: return false
        val clicked = list[index]
        val resolved = if (clicked.url.isNullOrBlank()) {
            val source = plugin.getMediaSource(clicked) ?: return false
            clicked.copy(url = source.url)
        } else {
            clicked
        }

        val queue = list.toMutableList()
        queue[index] = resolved
        playerController.playQueue(queue, index)
        return true
    }

    private suspend fun loadInitial() {
        _uiState.value = PluginSheetDetailUiState(loading = true)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            _uiState.value = PluginSheetDetailUiState(
                loading = false,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        val seed = seedSheet()
        runCatching {
            plugin.getMusicSheetInfo(seed, page = 1)
        }.onSuccess { detail ->
            if (detail == null) {
                _uiState.value = PluginSheetDetailUiState(
                    loading = false,
                    errorMessage = "加载歌单失败",
                )
                return@onSuccess
            }
            page = 1
            currentSheet = detail.sheetItem ?: seed
            _uiState.value = PluginSheetDetailUiState(
                title = detail.sheetItem?.title ?: seed.title ?: "歌单详情",
                sheetItem = currentSheet,
                musicList = detail.musicList,
                loading = false,
                isEnd = detail.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            _uiState.value = PluginSheetDetailUiState(
                loading = false,
                errorMessage = e.message ?: "加载歌单失败",
            )
        }
    }

    private fun seedSheet(): MusicSheetItemBase =
        seedResolver.resolve()

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }
}
