package com.zili.android.musicfreeandroid.feature.home.artistdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.navigation.ArtistDetailRoute
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val mediaSourceResolver: MediaSourceResolver,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ArtistDetailRoute>()

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private var page = 0
    private var currentArtist: ArtistItemBase? = null

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
        val artist = currentArtist
        if (state.loading || state.loadingMore || state.isEnd || artist == null) {
            return
        }

        val plugin = pluginManager.getPlugin(route.pluginPlatform) ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true, errorMessage = null)
            runCatching {
                plugin.getArtistWorks(artist, page + 1, type = "music")
            }.onSuccess { result ->
                if (result == null) {
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        errorMessage = "加载歌手作品失败",
                    )
                    return@onSuccess
                }
                page += 1
                _uiState.value = _uiState.value.copy(
                    musicList = _uiState.value.musicList + result.musicList,
                    isEnd = result.isEnd,
                    loadingMore = false,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    errorMessage = e.message ?: "加载歌手作品失败",
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
        _uiState.value = ArtistDetailUiState(loading = true)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            _uiState.value = ArtistDetailUiState(
                loading = false,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        val seed = seedArtist()
        currentArtist = seed
        runCatching {
            plugin.getArtistWorks(seed, page = 1, type = "music")
        }.onSuccess { result ->
            if (result == null) {
                _uiState.value = ArtistDetailUiState(
                    loading = false,
                    errorMessage = "加载歌手作品失败",
                )
                return@onSuccess
            }
            page = 1
            _uiState.value = ArtistDetailUiState(
                title = route.name,
                loading = false,
                musicList = result.musicList,
                isEnd = result.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            _uiState.value = ArtistDetailUiState(
                loading = false,
                errorMessage = e.message ?: "加载歌手作品失败",
            )
        }
    }

    private fun seedArtist(): ArtistItemBase {
        val raw = mutableMapOf<String, Any?>(
            "id" to route.artistId,
            "platform" to route.pluginPlatform,
            "name" to route.name,
        )
        route.avatar?.let { raw["avatar"] = it }

        return ArtistItemBase(
            id = route.artistId,
            platform = route.pluginPlatform,
            name = route.name,
            avatar = route.avatar,
            fans = null,
            description = null,
            worksNum = null,
            raw = raw,
        )
    }
}
