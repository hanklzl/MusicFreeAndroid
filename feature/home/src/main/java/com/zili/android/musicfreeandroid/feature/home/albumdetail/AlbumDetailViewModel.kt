package com.zili.android.musicfreeandroid.feature.home.albumdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.navigation.AlbumDetailRoute
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
    private val mediaSourceResolver: MediaSourceResolver,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<AlbumDetailRoute>()

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var page = 0
    private var currentAlbum: AlbumItemBase? = null

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
        val album = currentAlbum
        if (state.loading || state.loadingMore || state.isEnd || album == null) {
            return
        }

        val plugin = pluginManager.getPlugin(route.pluginPlatform) ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true, errorMessage = null)
            runCatching {
                plugin.getAlbumInfo(album, page + 1)
            }.onSuccess { detail ->
                if (detail == null) {
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        errorMessage = "加载专辑失败",
                    )
                    return@onSuccess
                }
                page += 1
                currentAlbum = detail.albumItem ?: album
                _uiState.value = _uiState.value.copy(
                    title = detail.albumItem?.title ?: _uiState.value.title,
                    musicList = _uiState.value.musicList + detail.musicList,
                    isEnd = detail.isEnd,
                    loadingMore = false,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    errorMessage = e.message ?: "加载专辑失败",
                )
            }
        }
    }

    suspend fun playAt(index: Int): Boolean {
        val list = _uiState.value.musicList
        if (index !in list.indices) {
            return false
        }

        val clicked = list[index]
        val resolved = if (clicked.url.isNullOrBlank()) {
            mediaSourceResolver.resolve(clicked)?.item ?: return false
        } else {
            clicked
        }

        val queue = list.toMutableList()
        queue[index] = resolved
        playerController.playQueue(queue, index)
        return true
    }

    private suspend fun loadInitial() {
        _uiState.value = AlbumDetailUiState(loading = true)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            _uiState.value = AlbumDetailUiState(
                loading = false,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        val seed = seedAlbum()
        runCatching {
            plugin.getAlbumInfo(seed, page = 1)
        }.onSuccess { detail ->
            if (detail == null) {
                _uiState.value = AlbumDetailUiState(
                    loading = false,
                    errorMessage = "加载专辑失败",
                )
                return@onSuccess
            }
            page = 1
            currentAlbum = detail.albumItem ?: seed
            _uiState.value = AlbumDetailUiState(
                title = detail.albumItem?.title ?: route.title ?: "专辑详情",
                loading = false,
                musicList = detail.musicList,
                isEnd = detail.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            _uiState.value = AlbumDetailUiState(
                loading = false,
                errorMessage = e.message ?: "加载专辑失败",
            )
        }
    }

    private fun seedAlbum(): AlbumItemBase {
        val raw = mutableMapOf<String, Any?>(
            "id" to route.albumId,
            "platform" to route.pluginPlatform,
        )
        route.title?.let { raw["title"] = it }
        route.artist?.let { raw["artist"] = it }
        route.artwork?.let { raw["artwork"] = it }

        return AlbumItemBase(
            id = route.albumId,
            platform = route.pluginPlatform,
            title = route.title,
            date = null,
            artist = route.artist,
            description = null,
            artwork = route.artwork,
            worksNum = null,
            raw = raw,
        )
    }

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }
}
