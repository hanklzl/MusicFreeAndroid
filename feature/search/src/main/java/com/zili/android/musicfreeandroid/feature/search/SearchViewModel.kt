package com.zili.android.musicfreeandroid.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

    val availablePlugins: StateFlow<List<PluginInfo>> = pluginManager.plugins
        .map { plugins -> plugins.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun selectPlugin(platform: String) {
        _selectedPlugin.value = platform
        _uiState.value = SearchUiState.Idle
    }

    fun search(query: String) {
        val platform = _selectedPlugin.value ?: return
        val plugin = pluginManager.getPlugin(platform) ?: return

        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val result = plugin.search(query, page = 1)
                _uiState.value = SearchUiState.Success(
                    items = result.data,
                    isEnd = result.isEnd,
                    query = query,
                    page = 1,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _uiState.value = SearchUiState.Error(e.message ?: "搜索失败")
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current !is SearchUiState.Success || current.isEnd) return

        val platform = _selectedPlugin.value ?: return
        val plugin = pluginManager.getPlugin(platform) ?: return
        val nextPage = current.page + 1

        viewModelScope.launch {
            try {
                val result = plugin.search(current.query, page = nextPage)
                _uiState.value = current.copy(
                    items = current.items + result.data,
                    isEnd = result.isEnd,
                    page = nextPage,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
                // Keep current results, don't overwrite with error
            }
        }
    }

    suspend fun resolveAndPlay(item: MusicItem, queue: List<MusicItem>) {
        val platform = _selectedPlugin.value ?: return
        val plugin = pluginManager.getPlugin(platform) ?: return

        try {
            val source = plugin.getMediaSource(item)
            if (source != null) {
                val resolvedItem = item.copy(url = source.url)
                val index = queue.indexOfFirst { it.id == item.id && it.platform == item.platform }
                val resolvedQueue = queue.map { queueItem ->
                    if (queueItem.id == item.id && queueItem.platform == item.platform) {
                        resolvedItem
                    } else {
                        queueItem
                    }
                }
                playerController.playQueue(resolvedQueue, index.coerceAtLeast(0))
            } else {
                Log.w(TAG, "Failed to resolve media source for ${item.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveAndPlay failed for ${item.title}", e)
        }
    }
}
