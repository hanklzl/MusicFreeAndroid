package com.hank.musicfree.feature.home.toplist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.navigation.TopListDetailRoute
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.home.runtime.DetailRouteTypes
import com.hank.musicfree.feature.home.runtime.DetailSessionEntry
import com.hank.musicfree.feature.home.runtime.DetailSessionHeader
import com.hank.musicfree.feature.home.runtime.DetailSessionRequest
import com.hank.musicfree.feature.home.runtime.DetailSessionStore
import com.hank.musicfree.feature.home.pluginsheet.navigation.PluginSheetRouteSeedResolver
import com.hank.musicfree.feature.home.pluginsheet.navigation.fallbackTopListSeed
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TopListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
    private val mediaSourceResolver: MediaSourceResolver,
    private val detailSessionStore: DetailSessionStore,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TopListDetailRoute>()
    private val seedResolver = PluginSheetRouteSeedResolver(route.seedToken) {
        route.fallbackTopListSeed()
    }
    private val detailKey = RuntimeStoreKey.detail(
        DetailRouteTypes.TOP_LIST,
        route.pluginPlatform,
        route.topListId,
    ).value
    private val detailRequest: DetailSessionRequest by lazy {
        DetailSessionRequest(
            key = detailKey,
            routeType = DetailRouteTypes.TOP_LIST,
            platform = route.pluginPlatform,
            itemId = route.topListId,
            seed = DetailSessionHeader.TopList(seedTopList()),
            fallbackTitle = "榜单详情",
        )
    }

    private val _uiState = MutableStateFlow(TopListDetailUiState(loading = true))
    val uiState: StateFlow<TopListDetailUiState> = _uiState.asStateFlow()

    val downloadedKeys: StateFlow<Set<String>> = downloader.downloadedKeys
        .map { keys -> keys.mapTo(HashSet()) { it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // ── Playlist / Favorite ──────────────────────────────────────────────────

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isFavoriteFlow(item: MusicItem): Flow<Boolean> = playlistRepository.isFavorite(item)

    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch {
            runUserAction(
                eventPrefix = "top_list_detail_toggle_favorite",
                operation = "toggle_favorite",
                fields = musicItemFields(item),
            ) {
                playlistRepository.toggleFavorite(item)
            }
        }
    }

    fun playAll() {
        val list = _uiState.value.musicList
        if (list.isEmpty()) {
            MfLog.detail(
                LogCategory.HOME,
                "top_list_detail_play_all_skipped",
                mapOf(
                    "screen" to SCREEN_TOP_LIST_DETAIL,
                    "operation" to "play_all",
                    "platform" to route.pluginPlatform,
                    "result" to LogFields.Result.SKIPPED,
                    "reason" to "empty_list",
                ),
            )
            return
        }
        viewModelScope.launch {
            runUserAction(
                eventPrefix = "top_list_detail_play_all",
                operation = "play_all",
                fields = mapOf("count" to list.size),
            ) {
                playAt(0)
            }
        }
    }

    fun showAddToPlaylistSheet(item: MusicItem) {
        _sheetState.value = AddToPlaylistSheetState.single(item)
    }

    fun showBatchAddToPlaylistSheet() {
        val list = _uiState.value.musicList
        if (list.isEmpty()) return
        _sheetState.value = AddToPlaylistSheetState.batch(list)
    }

    fun hideAddToPlaylistSheet() {
        _sheetState.value = AddToPlaylistSheetState()
    }

    fun addPendingToPlaylist(targetPlaylistId: String) {
        val items = _sheetState.value.pendingItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (items.size == 1) {
                val item = items[0]
                runUserAction(
                    eventPrefix = "top_list_detail_add_to_playlist",
                    operation = "add_to_playlist",
                    fields = musicItemFields(item) + mapOf(
                        "playlistId" to targetPlaylistId,
                        "itemCount" to 1,
                    ),
                ) {
                    val ok = playlistRepository.addMusicToPlaylist(targetPlaylistId, item)
                    val added = if (ok) 1 else 0
                    MfLog.detail(
                        LogCategory.HOME,
                        "top_list_detail_add_to_playlist_result",
                        actionFields("add_to_playlist", newFlowId("add_to_playlist", System.nanoTime())) +
                            musicItemFields(item) + mapOf(
                                "playlistId" to targetPlaylistId,
                                "itemCount" to 1,
                                "added" to added,
                                "skipped" to (1 - added),
                            ),
                    )
                    hideAddToPlaylistSheet()
                }
            } else {
                runUserAction(
                    eventPrefix = "top_list_detail_add_to_playlist",
                    operation = "add_to_playlist",
                    fields = mapOf(
                        "playlistId" to targetPlaylistId,
                        "itemCount" to items.size,
                    ),
                ) {
                    val added = playlistRepository.addMusicsToPlaylist(targetPlaylistId, items)
                    val skipped = items.size - added
                    MfLog.detail(
                        LogCategory.HOME,
                        "top_list_detail_add_to_playlist_result",
                        actionFields("add_to_playlist", newFlowId("add_to_playlist", System.nanoTime())) + mapOf(
                            "playlistId" to targetPlaylistId,
                            "itemCount" to items.size,
                            "added" to added,
                            "skipped" to skipped,
                        ),
                    )
                    hideAddToPlaylistSheet()
                }
            }
        }
    }

    fun createPlaylistAndAddPending(name: String) {
        val items = _sheetState.value.pendingItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            if (items.size == 1) {
                val item = items[0]
                runUserAction(
                    eventPrefix = "top_list_detail_create_playlist",
                    operation = "create_playlist_and_add",
                    fields = musicItemFields(item) + mapOf(
                        "playlistId" to newId,
                        "itemName" to name,
                        "itemCount" to 1,
                    ),
                ) {
                    playlistRepository.createPlaylist(Playlist(id = newId, name = name, coverUri = null))
                    val ok = playlistRepository.addMusicToPlaylist(newId, item)
                    val added = if (ok) 1 else 0
                    MfLog.detail(
                        LogCategory.HOME,
                        "top_list_detail_create_playlist_result",
                        actionFields("create_playlist_and_add", newFlowId("create_playlist_and_add", System.nanoTime())) +
                            musicItemFields(item) + mapOf(
                                "playlistId" to newId,
                                "itemName" to name,
                                "itemCount" to 1,
                                "added" to added,
                                "skipped" to (1 - added),
                            ),
                    )
                    hideAddToPlaylistSheet()
                }
            } else {
                runUserAction(
                    eventPrefix = "top_list_detail_create_playlist",
                    operation = "create_playlist_and_add",
                    fields = mapOf(
                        "playlistId" to newId,
                        "itemName" to name,
                        "itemCount" to items.size,
                    ),
                ) {
                    playlistRepository.createPlaylist(Playlist(id = newId, name = name, coverUri = null))
                    val added = playlistRepository.addMusicsToPlaylist(newId, items)
                    val skipped = items.size - added
                    MfLog.detail(
                        LogCategory.HOME,
                        "top_list_detail_create_playlist_result",
                        actionFields("create_playlist_and_add", newFlowId("create_playlist_and_add", System.nanoTime())) + mapOf(
                            "playlistId" to newId,
                            "itemName" to name,
                            "itemCount" to items.size,
                            "added" to added,
                            "skipped" to skipped,
                        ),
                    )
                    hideAddToPlaylistSheet()
                }
            }
        }
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            detailSessionStore.state
                .map { it.sessions[detailKey].toTopListUiState(seedTopList()) }
                .collect { _uiState.value = it }
        }
        viewModelScope.launch {
            pluginManager.ensurePluginsLoaded()
            loadInitial()
        }
    }

    fun retry() {
        viewModelScope.launch {
            loadInitial(forceRefresh = true)
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            detailSessionStore.loadMore(detailKey)
        }
    }

    suspend fun playAt(index: Int): Boolean {
        val list = _uiState.value.musicList
        if (index !in list.indices) {
            MfLog.detail(
                LogCategory.HOME,
                "top_list_detail_play_skipped",
                mapOf(
                    "screen" to SCREEN_TOP_LIST_DETAIL,
                    "operation" to "play_at",
                    "platform" to route.pluginPlatform,
                    "page" to index,
                    "count" to list.size,
                    "result" to LogFields.Result.SKIPPED,
                    "reason" to "invalid_index",
                ),
            )
            return false
        }

        val clicked = list[index]
        val resolved = if (clicked.url.isNullOrBlank()) {
            mediaSourceResolver.resolve(clicked)?.item ?: run {
                MfLog.error(
                    LogCategory.HOME,
                    "top_list_detail_play_failed",
                    fields = musicItemFields(clicked) + mapOf(
                        "screen" to SCREEN_TOP_LIST_DETAIL,
                        "operation" to "play_at",
                        "page" to index,
                        "result" to LogFields.Result.FAILURE,
                        "reason" to "no_source",
                    ),
                )
                return false
            }
        } else {
            clicked
        }

        val queue = list.toMutableList()
        queue[index] = resolved
        when (appPreferences.clickMusicInAlbum.first()) {
            AlbumMusicClickAction.PlayMusic -> playerController.playItem(resolved)
            AlbumMusicClickAction.PlayAlbum -> playerController.playQueue(queue, index)
        }
        MfLog.detail(
            LogCategory.HOME,
            "top_list_detail_play_success",
            musicItemFields(resolved) + mapOf(
                "screen" to SCREEN_TOP_LIST_DETAIL,
                "operation" to "play_at",
                "page" to index,
                "count" to queue.size,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        return true
    }

    private suspend fun loadInitial(forceRefresh: Boolean = false) {
        detailSessionStore.loadInitial(detailRequest, forceRefresh = forceRefresh)
    }

    private fun seedTopList(): MusicSheetItemBase = seedResolver.resolve()

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        MfLog.detail(
            LogCategory.DOWNLOAD,
            "download_intent",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_TOP_LIST_DETAIL,
                "operation" to "download",
                "quality" to quality.name,
                "count" to 1,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        downloader.enqueue(listOf(item), quality)
    }

    private fun topListFields(item: MusicSheetItemBase?): Map<String, Any?> = mapOf(
        "listId" to item?.id.orEmpty(),
        "itemName" to item?.title.orEmpty(),
    )

    private fun musicItemFields(item: MusicItem): Map<String, Any?> = mapOf(
        "platform" to item.platform,
        "itemId" to item.id,
        "itemName" to item.title,
    )

    private fun newFlowId(operation: String, generation: Long): String =
        "$SCREEN_TOP_LIST_DETAIL:$operation:$generation"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private fun actionFields(operation: String, flowId: String): Map<String, Any?> = mapOf(
        "screen" to SCREEN_TOP_LIST_DETAIL,
        "operation" to operation,
        "flowId" to flowId,
        "platform" to route.pluginPlatform,
    )

    private suspend fun runUserAction(
        eventPrefix: String,
        operation: String,
        fields: Map<String, Any?>,
        block: suspend () -> Unit,
    ) {
        val flowId = newFlowId(operation, System.nanoTime())
        val startedAt = System.nanoTime()
        MfLog.detail(
            LogCategory.HOME,
            "${eventPrefix}_start",
            actionFields(operation, flowId) + fields,
        )
        try {
            block()
            MfLog.detail(
                LogCategory.HOME,
                "${eventPrefix}_success",
                actionFields(operation, flowId) + fields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) {
                MfLog.detail(
                    LogCategory.HOME,
                    "${eventPrefix}_cancelled",
                    actionFields(operation, flowId) + fields + mapOf(
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.CANCELLED,
                        "reason" to LogFields.Reason.CANCELLED,
                    ),
                )
                throw e
            }
            MfLog.error(
                LogCategory.HOME,
                "${eventPrefix}_failed",
                e,
                actionFields(operation, flowId) + fields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            throw e
        }
    }

    private companion object {
        const val SCREEN_TOP_LIST_DETAIL = "top_list_detail"
    }
}

private fun DetailSessionEntry?.toTopListUiState(seed: MusicSheetItemBase): TopListDetailUiState {
    if (this == null) return TopListDetailUiState(loading = true)
    val topList = (header as? DetailSessionHeader.TopList)?.item ?: seed
    return TopListDetailUiState(
        title = topList.title ?: fallbackTitle,
        topListItem = topList,
        musicList = items,
        loading = loading,
        loadingMore = loadingMore,
        isEnd = isEnd,
        errorMessage = errorMessage,
    )
}
