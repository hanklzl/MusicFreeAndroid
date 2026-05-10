package com.zili.android.musicfreeandroid.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolution
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.timedSuspend
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
    private val playlistRepository: PlaylistRepository,
    private val downloader: Downloader,
    private val mediaSourceResolver: MediaSourceResolver,
) : ViewModel() {

    // ── 插件状态 ──
    private val _searchablePlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val searchablePlugins: StateFlow<List<PluginInfo>> = _searchablePlugins.asStateFlow()

    // ── 页面状态 ──
    private val _pageStatus = MutableStateFlow(SearchPageStatus.EDITING)
    val pageStatus: StateFlow<SearchPageStatus> = _pageStatus.asStateFlow()

    private var pluginsReady = false
    private var searchablePluginsMediaType: SearchMediaType? = null

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
                Playlist(id = newId, name = name, coverUri = null),
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
        _searchResults,
        _selectedMediaType,
        _selectedPlatform,
    ) { results, mediaType, platform ->
        platform?.let { results[mediaType]?.get(it) } ?: PluginSearchState.Idle
    }.stateIn(viewModelScope, SharingStarted.Lazily, PluginSearchState.Idle)

    init {
        viewModelScope.launch {
            _selectedMediaType
                .flatMapLatest { mediaType ->
                    pluginManager.getSearchablePlugins(mediaType.key)
                        .map { plugins -> mediaType to plugins }
                }
                .collect { (mediaType, plugins) ->
                    handleSearchablePluginsChanged(mediaType, plugins.map { it.info })
                }
        }
        viewModelScope.launch {
            runCatching {
                pluginManager.ensurePluginsLoaded()
            }.onFailure { error ->
                MfLog.error(
                    category = LogCategory.SEARCH,
                    event = "search_plugins_load_failed",
                    throwable = error,
                    fields = mapOf("status" to "failed"),
                )
            }
            pluginsReady = true
            updatePageStatusForPluginAvailability()
            runPendingSearchIfPossible()
        }
    }

    private fun handleSearchablePluginsChanged(mediaType: SearchMediaType, searchable: List<PluginInfo>) {
        if (mediaType != _selectedMediaType.value) return

        searchablePluginsMediaType = mediaType
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
        val searchable = searchablePluginsFor(_selectedMediaType.value)
        if (searchable == null) return
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
        if (pending.mediaType != _selectedMediaType.value) return
        if (searchablePluginsFor(pending.mediaType).isNullOrEmpty()) return

        pendingSearch = null
        _pageStatus.value = SearchPageStatus.SEARCHING
        searchForMediaType(pending.query, pending.mediaType)
    }

    private fun searchablePluginsFor(mediaType: SearchMediaType): List<PluginInfo>? {
        return if (searchablePluginsMediaType == mediaType) {
            _searchablePlugins.value
        } else {
            null
        }
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
        val plugins = searchablePluginsFor(mediaType)
        if (plugins.isNullOrEmpty()) {
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
        MfLog.detail(
            category = LogCategory.SEARCH,
            event = "search_start",
            fields = mapOf(
                "query" to query,
                "type" to mediaType.key,
                "platformCount" to plugins.size,
                "status" to "start",
            ),
        )

        // 并行搜索每个插件
        plugins.forEach { pluginInfo ->
            viewModelScope.launch {
                val plugin = pluginManager.getPlugin(pluginInfo.platform)
                if (plugin == null) {
                    MfLog.error(
                        category = LogCategory.SEARCH,
                        event = "search_plugin_failed",
                        fields = mapOf(
                            "platform" to pluginInfo.platform,
                            "type" to mediaType.key,
                            "query" to query,
                            "page" to 1,
                            "status" to "failed",
                            "reason" to "plugin_missing",
                        ),
                    )
                    updatePluginState(mediaType, pluginInfo.platform, PluginSearchState.Error("插件不可用"))
                    checkSearchCompletion(mediaType)
                    return@launch
                }

                val startedAt = System.nanoTime()
                try {
                    val (result, durationMs) = timedSuspend {
                        plugin.search(query, page = 1, type = mediaType.key)
                    }
                    MfLog.detail(
                        category = LogCategory.SEARCH,
                        event = "search_plugin_success",
                        fields = mapOf(
                            "platform" to pluginInfo.platform,
                            "type" to mediaType.key,
                            "query" to query,
                            "page" to 1,
                            "resultCount" to result.data.size,
                            "isEnd" to result.isEnd,
                            "status" to "success",
                            "durationMs" to durationMs,
                        ),
                    )
                    updatePluginState(
                        mediaType,
                        pluginInfo.platform,
                        PluginSearchState.Success(
                            items = result.data,
                            isEnd = result.isEnd,
                            page = 1,
                        ),
                    )
                } catch (error: Exception) {
                    MfLog.error(
                        category = LogCategory.SEARCH,
                        event = "search_plugin_failed",
                        throwable = error,
                        fields = mapOf(
                            "platform" to pluginInfo.platform,
                            "type" to mediaType.key,
                            "query" to query,
                            "page" to 1,
                            "status" to "failed",
                            "durationMs" to elapsedMs(startedAt),
                        ),
                    )
                    updatePluginState(
                        mediaType,
                        pluginInfo.platform,
                        PluginSearchState.Error(error.message ?: "搜索失败"),
                    )
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
        if (_selectedMediaType.value == type) return

        val query = _currentQuery.value
        // 先登记 pending，再切换类型，避免插件列表 collector 提前收到新类型。
        if (query.isNotBlank() && _searchResults.value[type] == null) {
            pendingSearch = PendingSearch(query, type)
            _pageStatus.value = SearchPageStatus.SEARCHING
        }
        searchablePluginsMediaType = null
        _searchablePlugins.value = emptyList()
        _selectedPlatform.value = null
        _selectedMediaType.value = type
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
            val startedAt = System.nanoTime()
            try {
                val (result, durationMs) = timedSuspend {
                    plugin.search(query, page = nextPage, type = mediaType.key)
                }
                MfLog.detail(
                    category = LogCategory.SEARCH,
                    event = "search_plugin_success",
                    fields = mapOf(
                        "platform" to platform,
                        "type" to mediaType.key,
                        "query" to query,
                        "page" to nextPage,
                        "resultCount" to result.data.size,
                        "isEnd" to result.isEnd,
                        "status" to "success",
                        "durationMs" to durationMs,
                    ),
                )
                updatePluginState(
                    mediaType,
                    platform,
                    current.copy(
                        items = current.items + result.data,
                        isEnd = result.isEnd,
                        page = nextPage,
                    ),
                )
            } catch (error: Exception) {
                MfLog.error(
                    category = LogCategory.SEARCH,
                    event = "search_plugin_failed",
                    throwable = error,
                    fields = mapOf(
                        "platform" to platform,
                        "type" to mediaType.key,
                        "query" to query,
                        "page" to nextPage,
                        "status" to "failed",
                        "durationMs" to elapsedMs(startedAt),
                    ),
                )
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
        MfLog.detail(
            category = LogCategory.SEARCH,
            event = "play_next_enqueued",
            fields = mapOf(
                "platform" to item.platform,
                "itemId" to item.id,
                "itemTitle" to item.title,
            ),
        )
    }

    fun resolveAndPlay(item: MusicItem, queue: List<MusicItem>) {
        viewModelScope.launch {
            val startedAt = System.nanoTime()
            var resolution: MediaSourceResolution? = null
            try {
                val (resolvedItem, durationMs) = timedSuspend {
                    if (item.url.isNullOrBlank()) {
                        mediaSourceResolver.resolve(item)?.also { resolution = it }?.item
                    } else {
                        item
                    }
                }
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
                    MfLog.detail(
                        category = LogCategory.SEARCH,
                        event = "playback_resolve_success",
                        fields = mapOf(
                            "platform" to item.platform,
                            "itemId" to item.id,
                            "itemTitle" to item.title,
                            "resolverPlatform" to (resolution?.resolverPlatform ?: item.platform),
                            "redirected" to (resolution?.redirected ?: false),
                            "status" to "success",
                            "durationMs" to durationMs,
                        ),
                    )
                    playerController.playQueue(resolvedQueue, startIndex)
                    _playEvent.emit(PlayEvent.NavigateToPlayer)
                } else {
                    MfLog.error(
                        category = LogCategory.SEARCH,
                        event = "playback_resolve_failed",
                        fields = mapOf(
                            "platform" to item.platform,
                            "itemId" to item.id,
                            "itemTitle" to item.title,
                            "status" to "failed",
                            "reason" to "no_source",
                            "durationMs" to durationMs,
                        ),
                    )
                    _playEvent.emit(PlayEvent.Failed("播放失败，请重试"))
                }
            } catch (error: Exception) {
                MfLog.error(
                    category = LogCategory.SEARCH,
                    event = "playback_resolve_failed",
                    throwable = error,
                    fields = mapOf(
                        "platform" to item.platform,
                        "itemId" to item.id,
                        "itemTitle" to item.title,
                        "status" to "failed",
                        "reason" to "resolve_and_play_error",
                        "durationMs" to elapsedMs(startedAt),
                    ),
                )
                _playEvent.emit(PlayEvent.Failed("播放失败，请重试"))
            }
        }
    }

    // ── 搜索历史 ──

    fun clearHistory() {
        viewModelScope.launch { appPreferences.clearSearchHistory() }
    }

    fun backToEditing() {
        _pageStatus.value = SearchPageStatus.EDITING
    }

    // ── Download ──

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
