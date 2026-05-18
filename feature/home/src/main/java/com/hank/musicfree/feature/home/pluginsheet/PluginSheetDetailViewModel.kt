package com.hank.musicfree.feature.home.pluginsheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.navigation.PluginSheetDetailRoute
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.data.repository.StarredSheetRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.home.runtime.DetailRouteTypes
import com.hank.musicfree.feature.home.runtime.DetailSessionEntry
import com.hank.musicfree.feature.home.runtime.DetailSessionHeader
import com.hank.musicfree.feature.home.runtime.DetailSessionRequest
import com.hank.musicfree.feature.home.runtime.DetailSessionStore
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.feature.home.pluginsheet.navigation.PluginSheetRouteSeedResolver
import com.hank.musicfree.feature.home.pluginsheet.navigation.fallbackSheetSeed
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
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
class PluginSheetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val starredSheetRepository: StarredSheetRepository,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
    private val mediaSourceResolver: MediaSourceResolver,
    private val detailSessionStore: DetailSessionStore,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<PluginSheetDetailRoute>()
    private val seedResolver = PluginSheetRouteSeedResolver(route.seedToken) {
        route.fallbackSheetSeed()
    }
    private val detailKey = RuntimeStoreKey.detail(
        DetailRouteTypes.PLUGIN_SHEET,
        route.pluginPlatform,
        route.sheetId,
    ).value
    private val detailRequest: DetailSessionRequest by lazy {
        DetailSessionRequest(
            key = detailKey,
            routeType = DetailRouteTypes.PLUGIN_SHEET,
            platform = route.pluginPlatform,
            itemId = route.sheetId,
            seed = DetailSessionHeader.Sheet(seedSheet()),
            fallbackTitle = "歌单详情",
        )
    }

    private val _uiState = MutableStateFlow(PluginSheetDetailUiState(loading = true))
    val uiState: StateFlow<PluginSheetDetailUiState> = _uiState.asStateFlow()

    val isSheetStarred: StateFlow<Boolean> = starredSheetRepository
        .observeIsStarred(route.sheetId, route.pluginPlatform)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── Playlist / Favorite ──────────────────────────────────────────────────

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isFavoriteFlow(item: MusicItem): Flow<Boolean> = playlistRepository.isFavorite(item)

    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch {
            runUserAction(
                eventPrefix = "plugin_sheet_toggle_favorite",
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
                "plugin_sheet_play_all_skipped",
                mapOf(
                    "screen" to SCREEN_PLUGIN_SHEET_DETAIL,
                    "operation" to "play_all",
                    "platform" to route.pluginPlatform,
                    "sheetId" to route.sheetId,
                    "result" to LogFields.Result.SKIPPED,
                    "reason" to "empty_list",
                ),
            )
            return
        }
        viewModelScope.launch {
            runUserAction(
                eventPrefix = "plugin_sheet_play_all",
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
            val baseFields = if (items.size == 1) {
                musicItemFields(items[0]) + mapOf(
                    "playlistId" to targetPlaylistId,
                    "itemCount" to 1,
                )
            } else {
                mapOf(
                    "playlistId" to targetPlaylistId,
                    "itemCount" to items.size,
                )
            }
            val mutableFields: MutableMap<String, Any?> = baseFields.toMutableMap()
            runUserAction(
                eventPrefix = "plugin_sheet_add_to_playlist",
                operation = "add_to_playlist",
                fields = mutableFields,
            ) {
                val added = if (items.size == 1) {
                    if (playlistRepository.addMusicToPlaylist(targetPlaylistId, items[0])) 1 else 0
                } else {
                    playlistRepository.addMusicsToPlaylist(targetPlaylistId, items)
                }
                mutableFields["added"] = added
                mutableFields["skipped"] = items.size - added
                hideAddToPlaylistSheet()
            }
        }
    }

    fun createPlaylistAndAddPending(name: String) {
        val items = _sheetState.value.pendingItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val baseFields = if (items.size == 1) {
                musicItemFields(items[0]) + mapOf(
                    "playlistId" to newId,
                    "itemName" to name,
                    "itemCount" to 1,
                )
            } else {
                mapOf(
                    "playlistId" to newId,
                    "itemName" to name,
                    "itemCount" to items.size,
                )
            }
            val mutableFields: MutableMap<String, Any?> = baseFields.toMutableMap()
            runUserAction(
                eventPrefix = "plugin_sheet_create_playlist",
                operation = "create_playlist_and_add",
                fields = mutableFields,
            ) {
                playlistRepository.createPlaylist(Playlist(id = newId, name = name, coverUri = null))
                val added = if (items.size == 1) {
                    if (playlistRepository.addMusicToPlaylist(newId, items[0])) 1 else 0
                } else {
                    playlistRepository.addMusicsToPlaylist(newId, items)
                }
                mutableFields["added"] = added
                mutableFields["skipped"] = items.size - added
                hideAddToPlaylistSheet()
            }
        }
    }

    fun toggleSheetStarred() {
        val sheet = currentSheet() ?: seedSheet()
        val starredSheet = sheet.toStarredSheet()
        val wasStarred = isSheetStarred.value
        viewModelScope.launch {
            runUserAction(
                eventPrefix = "plugin_sheet_starred_toggle",
                operation = "toggle_sheet_starred",
                fields = sheetFields(sheet) + mapOf("wasStarred" to wasStarred),
            ) {
                starredSheetRepository.toggle(starredSheet)
                MfLog.detail(
                    category = LogCategory.APP,
                    event = if (wasStarred) "starred_removed" else "starred_added",
                    fields = mapOf(
                        "kind" to com.hank.musicfree.core.model.StarredKind.SHEET,
                        "platform" to starredSheet.platform,
                        "id" to starredSheet.id,
                        "title" to starredSheet.title,
                        "source" to "detail_sheet",
                    ),
                )
            }
        }
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            detailSessionStore.state
                .map { it.sessions[detailKey].toPluginSheetUiState(seedSheet()) }
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
                "plugin_sheet_play_skipped",
                mapOf(
                    "screen" to SCREEN_PLUGIN_SHEET_DETAIL,
                    "operation" to "play_at",
                    "platform" to route.pluginPlatform,
                    "sheetId" to route.sheetId,
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
                    "plugin_sheet_play_failed",
                    fields = musicItemFields(clicked) + mapOf(
                        "screen" to SCREEN_PLUGIN_SHEET_DETAIL,
                        "operation" to "play_at",
                        "sheetId" to route.sheetId,
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
            "plugin_sheet_play_success",
            musicItemFields(resolved) + mapOf(
                "screen" to SCREEN_PLUGIN_SHEET_DETAIL,
                "operation" to "play_at",
                "sheetId" to route.sheetId,
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

    private fun seedSheet(): MusicSheetItemBase =
        seedResolver.resolve()

    private fun currentSheet(): MusicSheetItemBase? =
        (detailSessionStore.session(detailKey)?.header as? DetailSessionHeader.Sheet)?.item

    val defaultDownloadQuality = appPreferences.defaultDownloadQuality

    fun download(item: MusicItem, quality: PlayQuality) {
        MfLog.detail(
            LogCategory.DOWNLOAD,
            "download_intent",
            musicItemFields(item) + mapOf(
                "screen" to SCREEN_PLUGIN_SHEET_DETAIL,
                "operation" to "download",
                "sheetId" to route.sheetId,
                "quality" to quality.name,
                "count" to 1,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
        downloader.enqueue(listOf(item), quality)
    }

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

    private fun actionFields(operation: String, flowId: String): Map<String, Any?> = mapOf(
        "screen" to SCREEN_PLUGIN_SHEET_DETAIL,
        "operation" to operation,
        "flowId" to flowId,
        "platform" to route.pluginPlatform,
        "sheetId" to route.sheetId,
    )

    private fun sheetFields(item: MusicSheetItemBase?): Map<String, Any?> = mapOf(
        "sheetId" to (item?.id ?: route.sheetId),
        "itemName" to item?.title.orEmpty(),
    )

    private fun musicItemFields(item: MusicItem): Map<String, Any?> = mapOf(
        "platform" to item.platform,
        "itemId" to item.id,
        "itemName" to item.title,
    )

    private fun newFlowId(operation: String, generation: Long): String =
        "$SCREEN_PLUGIN_SHEET_DETAIL:$operation:$generation"

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val SCREEN_PLUGIN_SHEET_DETAIL = "plugin_sheet_detail"
    }
}

private fun DetailSessionEntry?.toPluginSheetUiState(seed: MusicSheetItemBase): PluginSheetDetailUiState {
    if (this == null) return PluginSheetDetailUiState(loading = true)
    val sheet = (header as? DetailSessionHeader.Sheet)?.item ?: seed
    return PluginSheetDetailUiState(
        title = sheet.title ?: fallbackTitle,
        sheetItem = sheet,
        musicList = items,
        loading = loading,
        loadingMore = loadingMore,
        isEnd = isEnd,
        errorMessage = errorMessage,
    )
}
