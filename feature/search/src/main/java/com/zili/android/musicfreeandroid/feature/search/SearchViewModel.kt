package com.zili.android.musicfreeandroid.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private const val MUSIC_SEARCH_TYPE = "music"
    }

    private val _installedPlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val installedPlugins: StateFlow<List<PluginInfo>> = _installedPlugins.asStateFlow()

    private val _availablePlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val availablePlugins: StateFlow<List<PluginInfo>> = _availablePlugins.asStateFlow()

    private val _selectedPlugin = MutableStateFlow<String?>(null)
    val selectedPlugin: StateFlow<String?> = _selectedPlugin.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private fun supportsMusicSearch(info: PluginInfo): Boolean {
        return info.supportedSearchType.contains(MUSIC_SEARCH_TYPE)
    }

    private fun searchablePluginsOf(plugins: List<LoadedPlugin>): List<PluginInfo> {
        return plugins
            .filter { supportsMusicSearch(it.info) }
            .map { it.info }
    }

    private fun installedPluginsOf(plugins: List<LoadedPlugin>): List<PluginInfo> {
        return plugins.map { it.info }
    }

    private fun nextSelectedPlugin(
        searchablePlugins: List<PluginInfo>,
        previousSelected: String?,
    ): String? {
        if (searchablePlugins.isEmpty()) return null
        if (previousSelected != null && searchablePlugins.any { it.platform == previousSelected }) {
            return previousSelected
        }
        return searchablePlugins.first().platform
    }

    private fun updateSelectionAndUiState(
        installedPlugins: List<PluginInfo>,
        searchablePlugins: List<PluginInfo>,
    ) {
        val previousSelected = _selectedPlugin.value
        val nextSelected = nextSelectedPlugin(searchablePlugins, previousSelected)
        _selectedPlugin.value = nextSelected

        _uiState.value = when {
            installedPlugins.isEmpty() -> SearchUiState.NoPlugins
            searchablePlugins.isEmpty() -> SearchUiState.NoSearchablePlugins
            _uiState.value is SearchUiState.NoPlugins -> SearchUiState.Idle
            _uiState.value is SearchUiState.NoSearchablePlugins -> SearchUiState.Idle
            previousSelected != nextSelected && _uiState.value is SearchUiState.Success -> SearchUiState.Idle
            previousSelected != nextSelected -> SearchUiState.Idle
            else -> _uiState.value
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    init {
        viewModelScope.launch {
            pluginManager.plugins.collect { plugins ->
                val installed = installedPluginsOf(plugins)
                val searchable = searchablePluginsOf(plugins)
                _installedPlugins.value = installed
                _availablePlugins.value = searchable
                updateSelectionAndUiState(installed, searchable)
            }
        }
        viewModelScope.launch {
            pluginManager.loadAllPlugins()
        }
    }

    fun selectPlugin(platform: String) {
        if (availablePlugins.value.none { it.platform == platform }) {
            return
        }
        _selectedPlugin.value = platform
        _uiState.value = SearchUiState.Idle
    }

    fun search(query: String) {
        val platform = _selectedPlugin.value ?: return
        val plugin = pluginManager.getPlugin(platform) ?: return

        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val result = plugin.search(query, page = 1, type = MUSIC_SEARCH_TYPE)
                _uiState.value = SearchUiState.Success(
                    items = result.data,
                    isEnd = result.isEnd,
                    query = query,
                    page = 1,
                )
            } catch (e: Exception) {
                logError("Search failed", e)
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
                val result = plugin.search(current.query, page = nextPage, type = MUSIC_SEARCH_TYPE)
                _uiState.value = current.copy(
                    items = current.items + result.data,
                    isEnd = result.isEnd,
                    page = nextPage,
                )
            } catch (e: Exception) {
                logError("Load more failed", e)
                _uiState.value = current
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
                logWarn("Failed to resolve media source for ${item.title}")
                false
            }
        } catch (e: Exception) {
            logError("resolveAndPlay failed for ${item.title}", e)
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
            logWarn("Fallback plugin not installed: $WY_FALLBACK_PLATFORM")
            return null
        }

        val fallbackQuery = MusicMatch.buildFallbackQuery(targetItem)
        if (fallbackQuery.isBlank()) {
            return null
        }

        val fallbackSearch = wyPlugin.search(fallbackQuery, page = 1)
        val fallbackMatch = MusicMatch.pickBestCandidate(targetItem, fallbackSearch.data)
        if (fallbackMatch == null) {
            logWarn("No WY fallback match for ${targetItem.title}")
            return null
        }

        val fallbackSource = wyPlugin.getMediaSource(fallbackMatch)
        val fallbackUrl = fallbackSource?.url
        if (fallbackUrl.isNullOrBlank()) {
            logWarn("WY fallback getMediaSource failed for ${targetItem.title}")
            return null
        }

        logInfo("Fallback playback resolved by $WY_FALLBACK_PLATFORM for ${targetItem.title}")
        return fallbackMatch.copy(url = fallbackUrl)
    }
}
