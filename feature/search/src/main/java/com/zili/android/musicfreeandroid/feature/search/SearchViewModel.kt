package com.zili.android.musicfreeandroid.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
        private const val WY_FALLBACK_PLATFORM = "元力WY"
    }

    // ── 插件状态 ──
    private val _searchablePlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val searchablePlugins: StateFlow<List<PluginInfo>> = _searchablePlugins.asStateFlow()

    // ── 页面状态 ──
    private val _pageStatus = MutableStateFlow(SearchPageStatus.EDITING)
    val pageStatus: StateFlow<SearchPageStatus> = _pageStatus.asStateFlow()

    // ── 搜索历史 ──
    val searchHistory: StateFlow<List<String>> = appPreferences.searchHistory
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── 当前查询 ──
    private val _currentQuery = MutableStateFlow("")
    val currentQuery: StateFlow<String> = _currentQuery.asStateFlow()

    // ── Tab 选择 ──
    private val _selectedMediaType = MutableStateFlow(SearchMediaType.MUSIC)
    val selectedMediaType: StateFlow<SearchMediaType> = _selectedMediaType.asStateFlow()

    private val _selectedPlatform = MutableStateFlow<String?>(null)
    val selectedPlatform: StateFlow<String?> = _selectedPlatform.asStateFlow()

    // ── 二维搜索结果 ──
    // searchResults[mediaType][platform] = PluginSearchState
    private val _searchResults = MutableStateFlow<Map<SearchMediaType, Map<String, PluginSearchState>>>(emptyMap())
    val searchResults: StateFlow<Map<SearchMediaType, Map<String, PluginSearchState>>> = _searchResults.asStateFlow()

    /** 当前选中的 Tab 组合对应的搜索状态 */
    val currentPluginState: StateFlow<PluginSearchState> = combine(
        _searchResults, _selectedMediaType, _selectedPlatform,
    ) { results, mediaType, platform ->
        platform?.let { results[mediaType]?.get(it) } ?: PluginSearchState.Idle
    }.stateIn(viewModelScope, SharingStarted.Lazily, PluginSearchState.Idle)

    init {
        viewModelScope.launch {
            pluginManager.plugins.collect { plugins ->
                val searchable = plugins
                    .filter { it.info.supportedSearchType.contains("music") || it.info.supportedSearchType.isEmpty() }
                    .map { it.info }
                _searchablePlugins.value = searchable
                if (_selectedPlatform.value == null && searchable.isNotEmpty()) {
                    _selectedPlatform.value = searchable.first().platform
                }
                if (searchable.isEmpty()) {
                    _pageStatus.value = SearchPageStatus.NO_PLUGIN
                }
            }
        }
        viewModelScope.launch { pluginManager.ensurePluginsLoaded() }
    }

    // ── 搜索 ──

    fun searchAll(query: String) {
        if (query.isBlank()) return
        _currentQuery.value = query
        _pageStatus.value = SearchPageStatus.SEARCHING

        viewModelScope.launch { appPreferences.addSearchQuery(query) }

        val mediaType = _selectedMediaType.value
        searchForMediaType(query, mediaType)
    }

    private fun searchForMediaType(query: String, mediaType: SearchMediaType) {
        val plugins = _searchablePlugins.value
        if (plugins.isEmpty()) return

        // 初始化该 mediaType 下所有插件为 Loading
        val typeResults = plugins.associate { it.platform to (PluginSearchState.Loading as PluginSearchState) }
        _searchResults.update { current ->
            current.toMutableMap().apply { put(mediaType, typeResults) }
        }

        // 并行搜索每个插件
        plugins.forEach { pluginInfo ->
            viewModelScope.launch {
                val plugin = pluginManager.getPlugin(pluginInfo.platform) ?: return@launch
                try {
                    val result = plugin.search(query, page = 1, type = mediaType.key)
                    updatePluginState(mediaType, pluginInfo.platform, PluginSearchState.Success(
                        items = result.data,
                        isEnd = result.isEnd,
                        page = 1,
                    ))
                } catch (e: Exception) {
                    runCatching { Log.e(TAG, "Search failed for ${pluginInfo.platform}", e) }
                    updatePluginState(mediaType, pluginInfo.platform,
                        PluginSearchState.Error(e.message ?: "搜索失败"))
                }
                checkSearchCompletion(mediaType)
            }
        }
    }

    private fun updatePluginState(mediaType: SearchMediaType, platform: String, state: PluginSearchState) {
        _searchResults.update { current ->
            current.toMutableMap().apply {
                val typeMap = (get(mediaType) ?: emptyMap()).toMutableMap()
                typeMap[platform] = state
                put(mediaType, typeMap)
            }
        }
    }

    private fun checkSearchCompletion(mediaType: SearchMediaType) {
        if (mediaType != _selectedMediaType.value) return  // 已切换 tab，忽略
        val typeResults = _searchResults.value[mediaType] ?: return
        val anyLoading = typeResults.values.any { it is PluginSearchState.Loading }
        if (!anyLoading) {
            _pageStatus.value = SearchPageStatus.RESULT
        }
    }

    // ── Tab 切换 ──

    fun selectMediaType(type: SearchMediaType) {
        _selectedMediaType.value = type
        val query = _currentQuery.value
        // 如果该类型还没搜过，触发搜索
        if (query.isNotBlank() && _searchResults.value[type] == null) {
            searchForMediaType(query, type)
        }
        // 自动选第一个插件
        val platforms = _searchResults.value[type]?.keys?.toList()
            ?: _searchablePlugins.value.map { it.platform }
        if (_selectedPlatform.value !in platforms && platforms.isNotEmpty()) {
            _selectedPlatform.value = platforms.first()
        }
    }

    fun selectPlatform(platform: String) {
        _selectedPlatform.value = platform
    }

    // ── 分页 ──

    fun loadMore() {
        val mediaType = _selectedMediaType.value
        val platform = _selectedPlatform.value ?: return
        val current = _searchResults.value[mediaType]?.get(platform)
        if (current !is PluginSearchState.Success || current.isEnd) return

        val plugin = pluginManager.getPlugin(platform) ?: return
        val nextPage = current.page + 1
        val query = _currentQuery.value

        viewModelScope.launch {
            try {
                val result = plugin.search(query, page = nextPage, type = mediaType.key)
                updatePluginState(mediaType, platform, current.copy(
                    items = current.items + result.data,
                    isEnd = result.isEnd,
                    page = nextPage,
                ))
            } catch (e: Exception) {
                runCatching { Log.e(TAG, "Load more failed", e) }
            }
        }
    }

    // ── 播放 ──

    fun playNext(item: MusicItem) {
        playerController.addNextInQueue(item)
        Log.d(TAG, "playNext: ${item.title} 已加入下一首")
    }

    suspend fun resolveAndPlay(item: MusicItem, queue: List<MusicItem>): Boolean {
        val platform = _selectedPlatform.value ?: return false
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

        Log.i(TAG, "Fallback playback resolved by $WY_FALLBACK_PLATFORM for ${targetItem.title}")
        return fallbackMatch.copy(url = fallbackUrl)
    }

    // ── 搜索历史 ──

    fun clearHistory() {
        viewModelScope.launch { appPreferences.clearSearchHistory() }
    }

    fun backToEditing() {
        _pageStatus.value = SearchPageStatus.EDITING
    }
}
