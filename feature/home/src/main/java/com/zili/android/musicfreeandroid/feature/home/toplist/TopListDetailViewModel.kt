package com.zili.android.musicfreeandroid.feature.home.toplist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TopListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TopListDetailRoute>()

    private val _uiState = MutableStateFlow(TopListDetailUiState(loading = true))
    val uiState: StateFlow<TopListDetailUiState> = _uiState.asStateFlow()

    private var page = 0
    private var currentTopList: MusicSheetItemBase? = null

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
        val topList = currentTopList
        if (state.loading || state.loadingMore || state.isEnd || topList == null) {
            return
        }

        val plugin = pluginManager.getPlugin(route.pluginPlatform) ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(loadingMore = true, errorMessage = null)
            runCatching {
                plugin.getTopListDetail(topList, page + 1)
            }.onSuccess { detail ->
                if (detail == null) {
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        errorMessage = "加载榜单失败",
                    )
                    return@onSuccess
                }
                page += 1
                currentTopList = detail.topListItem ?: topList
                _uiState.value = _uiState.value.copy(
                    title = detail.topListItem?.title ?: _uiState.value.title,
                    topListItem = currentTopList,
                    musicList = _uiState.value.musicList + detail.musicList,
                    isEnd = detail.isEnd,
                    loadingMore = false,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    errorMessage = e.message ?: "加载榜单失败",
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
        _uiState.value = TopListDetailUiState(loading = true)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            _uiState.value = TopListDetailUiState(
                loading = false,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        val seedTopList = findTopListById(route.topListId)
        if (seedTopList == null) {
            _uiState.value = TopListDetailUiState(
                loading = false,
                errorMessage = "未找到榜单：${route.topListId}",
            )
            return
        }

        runCatching {
            plugin.getTopListDetail(seedTopList, page = 1)
        }.onSuccess { detail ->
            if (detail == null) {
                _uiState.value = TopListDetailUiState(
                    loading = false,
                    errorMessage = "加载榜单失败",
                )
                return@onSuccess
            }
            page = 1
            currentTopList = detail.topListItem ?: seedTopList
            _uiState.value = TopListDetailUiState(
                title = detail.topListItem?.title ?: seedTopList.title ?: "榜单详情",
                topListItem = currentTopList,
                musicList = detail.musicList,
                loading = false,
                isEnd = detail.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            _uiState.value = TopListDetailUiState(
                loading = false,
                errorMessage = e.message ?: "加载榜单失败",
            )
        }
    }

    private suspend fun findTopListById(topListId: String): MusicSheetItemBase? {
        val plugin = pluginManager.getPlugin(route.pluginPlatform) ?: return null
        val groups = plugin.getTopLists()
        return groups.asSequence()
            .flatMap { it.data.asSequence() }
            .firstOrNull { it.id == topListId }
    }
}
