package com.hank.musicfree.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.search.runtime.SearchSessionStore
import com.hank.musicfree.feature.search.runtime.resultsPagerUiState
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.timedSuspend
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.PluginManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val searchSessionStore: SearchSessionStore,
) : ViewModel() {

    // ── 搜索结果分页状态 ──
    val resultsPagerUiState = searchSessionStore.state
        .map { it.resultsPagerUiState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchSessionStore.state.value.resultsPagerUiState)

    val searchablePlugins: StateFlow<List<PluginInfo>> = resultsPagerUiState
        .map { it.mediaScenes[it.selectedMediaType]?.plugins.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pageStatus: StateFlow<SearchPageStatus> = searchSessionStore.state
        .map { it.pageStatus }
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchSessionStore.state.value.pageStatus)

    private var initialAutofocusConsumed = false

    // ── 搜索历史 ──
    val searchHistory: StateFlow<List<String>> = appPreferences.searchHistory
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentQuery: StateFlow<String> = searchSessionStore.state
        .map { it.query }
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchSessionStore.state.value.query)

    val selectedMediaType: StateFlow<SearchMediaType> = searchSessionStore.state
        .map { it.selectedMediaType }
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchSessionStore.state.value.selectedMediaType)

    val selectedPlatform: StateFlow<String?> = resultsPagerUiState
        .map { it.mediaScenes[it.selectedMediaType]?.selectedPlatform }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            searchSessionStore.state.value.resultsPagerUiState.mediaScenes[
                searchSessionStore.state.value.resultsPagerUiState.selectedMediaType
            ]?.selectedPlatform,
        )

    // ── 歌单 / 收藏 ──
    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.observeAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isFavoriteFlow(item: MusicItem): Flow<Boolean> = playlistRepository.isFavorite(item)

    fun consumeInitialAutofocusRequest(): Boolean {
        if (initialAutofocusConsumed) return false
        initialAutofocusConsumed = true
        return true
    }

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
    val searchResults: StateFlow<Map<SearchMediaType, Map<String, PluginSearchState>>> = searchSessionStore.state
        .map { it.results }
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchSessionStore.state.value.results)

    /** 当前选中的 Tab 组合对应的搜索状态 */
    val currentPluginState: StateFlow<PluginSearchState> = combine(
        searchResults,
        selectedMediaType,
        selectedPlatform,
    ) { results, mediaType, platform ->
        platform?.let { results[mediaType]?.get(it) } ?: PluginSearchState.Idle
    }.stateIn(viewModelScope, SharingStarted.Lazily, PluginSearchState.Idle)

    init {
        // Page-lifecycle: the store is per-ViewModel, so a fresh screen entry starts
        // with empty state. We intentionally do NOT restore a persisted snapshot here
        // (search no longer survives process death — matching RN), which also avoids
        // the prior bug where restore() overwrote the live `searchablePlugins` set and
        // left re-entered searches stuck with no results.
        viewModelScope.launch {
            SearchMediaType.entries.forEach { mediaType ->
                viewModelScope.launch {
                    pluginManager.getSearchablePlugins(mediaType.key)
                        .map { plugins -> mediaType to plugins.map { it.info } }
                        .collect { (type, pluginInfos) ->
                            handleSearchablePluginsChanged(type, pluginInfos)
                        }
                }
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
            searchSessionStore.setPluginsReady()
        }
    }

    private suspend fun handleSearchablePluginsChanged(mediaType: SearchMediaType, searchable: List<PluginInfo>) {
        searchSessionStore.setSearchablePlugins(mediaType, searchable)
    }

    // ── 搜索 ──

    fun searchAll(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            appPreferences.addSearchQuery(query)
            searchSessionStore.search(query)
        }
    }

    // ── Tab 切换 ──

    fun selectMediaType(type: SearchMediaType) {
        searchSessionStore.selectMediaType(type)
        viewModelScope.launch {
            searchSessionStore.ensureMediaSearched(type)
        }
    }

    fun selectPlatform(platform: String) {
        searchSessionStore.selectPlatform(platform)
    }

    fun selectPlatform(mediaType: SearchMediaType, platform: String) {
        searchSessionStore.selectPlatform(mediaType, platform)
    }

    // ── 分页 ──

    fun loadMore() {
        viewModelScope.launch {
            searchSessionStore.loadMore()
        }
    }

    fun loadMore(mediaType: SearchMediaType, platform: String) {
        viewModelScope.launch {
            searchSessionStore.loadMore(mediaType, platform)
        }
    }

    // ── 播放 ──

    sealed interface PlayEvent {
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
                    when (appPreferences.clickMusicInSearch.first()) {
                        SearchResultClickAction.PlayMusic -> playerController.playItem(resolvedItem)
                        SearchResultClickAction.PlayMusicAndReplace -> {
                            playerController.playQueue(resolvedQueue, startIndex)
                        }
                    }
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
        searchSessionStore.backToEditing()
    }

    // ── Download ──

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        downloader.enqueue(listOf(item), quality)
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
