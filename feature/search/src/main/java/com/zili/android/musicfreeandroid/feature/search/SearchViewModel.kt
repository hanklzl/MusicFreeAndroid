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
        private const val WY_FALLBACK_PLATFORM = "元力WY"
    }

    val availablePlugins: StateFlow<List<PluginInfo>> = pluginManager.plugins
        .map { plugins -> plugins.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pluginManager.loadAllPlugins()
        }
        viewModelScope.launch {
            availablePlugins.collect { plugins ->
                val selected = _selectedPlugin.value
                _selectedPlugin.value = when {
                    plugins.isEmpty() -> null
                    selected == null -> plugins.first().platform
                    plugins.any { it.platform == selected } -> selected
                    else -> plugins.first().platform
                }
            }
        }
    }

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

    suspend fun resolveAndPlay(item: MusicItem, queue: List<MusicItem>): Boolean {
        val platform = _selectedPlugin.value ?: return false
        val plugin = pluginManager.getPlugin(platform) ?: return false

        return try {
            val resolvedItem = resolveMediaSourceWithFallback(
                primaryPlugin = plugin,
                selectedPlatform = platform,
                targetItem = item,
            )
            if (resolvedItem != null) {
                val index = queue.indexOfFirst {
                    it.id == item.id && it.platform == item.platform
                }
                val resolvedQueue = if (index >= 0) {
                    queue.mapIndexed { i, queueItem ->
                        if (i == index) resolvedItem else queueItem
                    }
                } else {
                    listOf(resolvedItem) + queue
                }
                val startIndex = if (index >= 0) index else 0
                playerController.playQueue(resolvedQueue, startIndex)
                true
            } else {
                Log.w(TAG, "Failed to resolve media source for ${item.title}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolveAndPlay failed for ${item.title}", e)
            false
        }
    }

    private suspend fun resolveMediaSourceWithFallback(
        primaryPlugin: com.zili.android.musicfreeandroid.plugin.api.PluginApi,
        selectedPlatform: String,
        targetItem: MusicItem,
    ): MusicItem? {
        val directSource = primaryPlugin.getMediaSource(targetItem)
        if (directSource?.url?.isNotBlank() == true) {
            return targetItem.copy(url = directSource.url)
        }

        if (selectedPlatform == WY_FALLBACK_PLATFORM) {
            return null
        }

        val wyPlugin = pluginManager.getPlugin(WY_FALLBACK_PLATFORM)
        if (wyPlugin == null) {
            Log.w(TAG, "Fallback plugin not installed: $WY_FALLBACK_PLATFORM")
            return null
        }

        val fallbackQuery = MusicMatch.buildFallbackQuery(targetItem)
        if (fallbackQuery.isBlank()) {
            return null
        }

        val fallbackSearch = wyPlugin.search(fallbackQuery, page = 1)
        val fallbackMatch = MusicMatch.pickBestCandidate(targetItem, fallbackSearch.data)
        if (fallbackMatch == null) {
            Log.w(TAG, "No WY fallback match for ${targetItem.title}")
            return null
        }

        val fallbackSource = wyPlugin.getMediaSource(fallbackMatch)
        val fallbackUrl = fallbackSource?.url
        if (fallbackUrl.isNullOrBlank()) {
            Log.w(TAG, "WY fallback getMediaSource failed for ${targetItem.title}")
            return null
        }

        Log.i(
            TAG,
            "Fallback playback resolved by $WY_FALLBACK_PLATFORM for ${targetItem.title}",
        )
        return fallbackMatch.copy(url = fallbackUrl)
    }
}
