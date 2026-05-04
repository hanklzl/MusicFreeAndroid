package com.zili.android.musicfreeandroid.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
    private val playlistRepository: PlaylistRepository,
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

    private var pluginsReady = false

    private data class PendingSearch(
        val query: String,
        val mediaType: SearchMediaType,
    )

    private var pendingSearch: PendingSearch? = null

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

    // ── 歌单 / 收藏 ──
    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.observeAllPlaylists()
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
            playlistRepository.createPlaylist(
                Playlist(id = newId, name = name, coverUri = null)
            )
            playlistRepository.addMusicToPlaylist(newId, item)
            hideAddToPlaylistSheet()
        }
    }

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
            pluginManager.getSearchablePlugins().collect { plugins ->
                handleSearchablePluginsChanged(plugins.map { it.info })
            }
        }
        viewModelScope.launch {
            runCatching {
                pluginManager.ensurePluginsLoaded()
            }.onFailure { e ->
                runCatching { Log.e(TAG, "Failed to load plugins", e) }
            }
            pluginsReady = true
            updatePageStatusForPluginAvailability()
            runPendingSearchIfPossible()
        }
    }

    private fun handleSearchablePluginsChanged(searchable: List<PluginInfo>) {
        _searchablePlugins.value = searchable

        val selected = _selectedPlatform.value
        if (selected == null && searchable.isNotEmpty()) {
            _selectedPlatform.value = searchable.first().platform
        } else if (selected != null && searchable.none { it.platform == selected }) {
            _selectedPlatform.value = searchable.firstOrNull()?.platform
        }

        updatePageStatusForPluginAvailability()
        runPendingSearchIfPossible()
    }

    private fun updatePageStatusForPluginAvailability() {
        val searchable = _searchablePlugins.value
        if (searchable.isNotEmpty()) {
            if (_pageStatus.value == SearchPageStatus.NO_PLUGIN) {
                _pageStatus.value = SearchPageStatus.EDITING
            }
            return
        }

        if (!pluginsReady) return

        if (_pageStatus.value != SearchPageStatus.RESULT) {
            _pageStatus.value = SearchPageStatus.NO_PLUGIN
        }
    }

    private fun runPendingSearchIfPossible() {
        val pending = pendingSearch ?: return
        if (_searchablePlugins.value.isEmpty()) return

        pendingSearch = null
        _pageStatus.value = SearchPageStatus.SEARCHING
        searchForMediaType(pending.query, pending.mediaType)
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
        if (plugins.isEmpty()) {
            pendingSearch = PendingSearch(query, mediaType)
            updatePageStatusForPluginAvailability()
            return
        }

        pendingSearch = null

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

    sealed interface PlayEvent {
        data object NavigateToPlayer : PlayEvent
        data class Failed(val message: String) : PlayEvent
    }

    private val _playEvent = MutableSharedFlow<PlayEvent>(extraBufferCapacity = 1)
    val playEvent: SharedFlow<PlayEvent> = _playEvent.asSharedFlow()

    fun playNext(item: MusicItem) {
        playerController.addNextInQueue(item)
        Log.d(TAG, "playNext: ${item.title} 已加入下一首")
    }

    fun resolveAndPlay(item: MusicItem, queue: List<MusicItem>) {
        val platform = _selectedPlatform.value ?: return
        val plugin = pluginManager.getPlugin(platform) ?: return

        viewModelScope.launch {
            try {
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
                    _playEvent.emit(PlayEvent.NavigateToPlayer)
                } else {
                    Log.w(TAG, "Failed to resolve media source for ${item.title}")
                    _playEvent.emit(PlayEvent.Failed("播放失败，请重试"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveAndPlay failed for ${item.title}", e)
                _playEvent.emit(PlayEvent.Failed("播放失败，请重试"))
            }
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
