package com.zili.android.musicfreeandroid.feature.home.pluginsheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.AlbumMusicClickAction
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import com.zili.android.musicfreeandroid.data.repository.StarredSheetRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetRouteSeedResolver
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.fallbackSheetSeed
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.timedSuspend
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val route = savedStateHandle.toRoute<PluginSheetDetailRoute>()
    private val seedResolver = PluginSheetRouteSeedResolver(route.seedToken) {
        route.fallbackSheetSeed()
    }

    private val _uiState = MutableStateFlow(PluginSheetDetailUiState(loading = true))
    val uiState: StateFlow<PluginSheetDetailUiState> = _uiState.asStateFlow()

    private var page = 0
    private var currentSheet: MusicSheetItemBase? = null
    private var loadGeneration: Long = 0

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
        val sheet = currentSheet ?: seedSheet()
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
                        "kind" to com.zili.android.musicfreeandroid.core.model.StarredKind.SHEET,
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
            pluginManager.ensurePluginsLoaded()
            loadInitial(nextLoadGeneration())
        }
    }

    fun retry() {
        viewModelScope.launch {
            loadInitial(nextLoadGeneration())
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val sheet = currentSheet
        if (state.loading || state.loadingMore || state.isEnd || sheet == null) {
            return
        }

        val generation = nextLoadGeneration()
        val operation = "load_more"
        val flowId = newFlowId(operation, generation)
        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            logLoadFailure(
                throwable = null,
                operation = operation,
                flowId = flowId,
                generation = generation,
                startedAt = System.nanoTime(),
                fields = sheetFields(sheet) + mapOf("page" to page + 1, "reason" to "plugin_missing"),
            )
            return
        }
        viewModelScope.launch {
            val startedAt = System.nanoTime()
            _uiState.value = state.copy(loadingMore = true, errorMessage = null)
            logLoadStart(operation, flowId, generation, sheetFields(sheet) + mapOf("page" to page + 1))
            runCatching {
                timedSuspend { plugin.getMusicSheetInfo(sheet, page + 1) }
            }.onSuccess { (detail, durationMs) ->
                if (!isCurrentLoad(generation)) {
                    logLoadStale(operation, flowId, generation, startedAt, sheetFields(sheet) + mapOf("page" to page + 1))
                    return@onSuccess
                }
                if (detail == null) {
                    logLoadFailure(
                        throwable = null,
                        operation = operation,
                        flowId = flowId,
                        generation = generation,
                        startedAt = startedAt,
                        fields = sheetFields(sheet) + mapOf("page" to page + 1, "reason" to LogFields.Reason.UNKNOWN),
                    )
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        errorMessage = "加载歌单失败",
                    )
                    return@onSuccess
                }
                page += 1
                currentSheet = detail.sheetItem ?: sheet
                logLoadSuccess(
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    durationMs = durationMs,
                    fields = sheetFields(currentSheet) + mapOf(
                        "page" to page,
                        "count" to detail.musicList.size,
                        "isEnd" to detail.isEnd,
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    title = detail.sheetItem?.title ?: _uiState.value.title,
                    sheetItem = currentSheet,
                    musicList = _uiState.value.musicList + detail.musicList,
                    isEnd = detail.isEnd,
                    loadingMore = false,
                )
            }.onFailure { e ->
                if (e is CancellationException) {
                    logLoadCancelled(operation, flowId, generation, startedAt, sheetFields(sheet) + mapOf("page" to page + 1))
                    throw e
                }
                if (!isCurrentLoad(generation)) {
                    logLoadStale(operation, flowId, generation, startedAt, sheetFields(sheet) + mapOf("page" to page + 1))
                    return@onFailure
                }
                logLoadFailure(
                    throwable = e,
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    startedAt = startedAt,
                    fields = sheetFields(sheet) + mapOf("page" to page + 1),
                )
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    errorMessage = e.message ?: "加载歌单失败",
                )
            }
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

    private suspend fun loadInitial(generation: Long) {
        val operation = "load_initial"
        val flowId = newFlowId(operation, generation)
        val startedAt = System.nanoTime()
        _uiState.value = PluginSheetDetailUiState(loading = true)

        val plugin = pluginManager.getPlugin(route.pluginPlatform)
        if (plugin == null) {
            logLoadFailure(
                throwable = null,
                operation = operation,
                flowId = flowId,
                generation = generation,
                startedAt = startedAt,
                fields = mapOf("sheetId" to route.sheetId, "reason" to "plugin_missing"),
            )
            _uiState.value = PluginSheetDetailUiState(
                loading = false,
                errorMessage = "插件不存在：${route.pluginPlatform}",
            )
            return
        }

        val seed = seedSheet()
        logLoadStart(operation, flowId, generation, sheetFields(seed) + mapOf("page" to 1))
        runCatching {
            timedSuspend { plugin.getMusicSheetInfo(seed, page = 1) }
        }.onSuccess { (detail, durationMs) ->
            if (!isCurrentLoad(generation)) {
                logLoadStale(operation, flowId, generation, startedAt, sheetFields(seed) + mapOf("page" to 1))
                return@onSuccess
            }
            if (detail == null) {
                logLoadFailure(
                    throwable = null,
                    operation = operation,
                    flowId = flowId,
                    generation = generation,
                    startedAt = startedAt,
                    fields = sheetFields(seed) + mapOf("page" to 1, "reason" to LogFields.Reason.UNKNOWN),
                )
                _uiState.value = PluginSheetDetailUiState(
                    loading = false,
                    errorMessage = "加载歌单失败",
                )
                return@onSuccess
            }
            page = 1
            currentSheet = detail.sheetItem ?: seed
            logLoadSuccess(
                operation = operation,
                flowId = flowId,
                generation = generation,
                durationMs = durationMs,
                fields = sheetFields(currentSheet) + mapOf(
                    "page" to 1,
                    "count" to detail.musicList.size,
                    "isEnd" to detail.isEnd,
                ),
            )
            _uiState.value = PluginSheetDetailUiState(
                title = detail.sheetItem?.title ?: seed.title ?: "歌单详情",
                sheetItem = currentSheet,
                musicList = detail.musicList,
                loading = false,
                isEnd = detail.isEnd,
                errorMessage = null,
            )
        }.onFailure { e ->
            if (e is CancellationException) {
                logLoadCancelled(operation, flowId, generation, startedAt, sheetFields(seed) + mapOf("page" to 1))
                throw e
            }
            if (!isCurrentLoad(generation)) {
                logLoadStale(operation, flowId, generation, startedAt, sheetFields(seed) + mapOf("page" to 1))
                return@onFailure
            }
            logLoadFailure(
                throwable = e,
                operation = operation,
                flowId = flowId,
                generation = generation,
                startedAt = startedAt,
                fields = sheetFields(seed) + mapOf("page" to 1),
            )
            _uiState.value = PluginSheetDetailUiState(
                loading = false,
                errorMessage = e.message ?: "加载歌单失败",
            )
        }
    }

    private fun seedSheet(): MusicSheetItemBase =
        seedResolver.resolve()

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

    private fun nextLoadGeneration(): Long {
        loadGeneration += 1
        return loadGeneration
    }

    private fun isCurrentLoad(generation: Long): Boolean = loadGeneration == generation

    private fun logLoadStart(operation: String, flowId: String, generation: Long, fields: Map<String, Any?>) {
        MfLog.detail(
            LogCategory.HOME,
            "plugin_sheet_load_start",
            baseLoadFields(operation, flowId, generation) + fields,
        )
    }

    private fun logLoadSuccess(
        operation: String,
        flowId: String,
        generation: Long,
        durationMs: Long,
        fields: Map<String, Any?>,
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "plugin_sheet_load_success",
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to durationMs,
                "result" to LogFields.Result.SUCCESS,
            ),
        )
    }

    private fun logLoadFailure(
        throwable: Throwable?,
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
        fields: Map<String, Any?>,
    ) {
        MfLog.error(
            LogCategory.HOME,
            "plugin_sheet_load_failed",
            throwable,
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.FAILURE,
            ),
        )
    }

    private fun logLoadStale(
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
        fields: Map<String, Any?>,
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "plugin_sheet_load_stale",
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.STALE,
                "reason" to LogFields.Reason.STALE_GENERATION,
            ),
        )
    }

    private fun logLoadCancelled(
        operation: String,
        flowId: String,
        generation: Long,
        startedAt: Long,
        fields: Map<String, Any?>,
    ) {
        MfLog.detail(
            LogCategory.HOME,
            "plugin_sheet_load_cancelled",
            baseLoadFields(operation, flowId, generation) + fields + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to LogFields.Result.CANCELLED,
                "reason" to LogFields.Reason.CANCELLED,
            ),
        )
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

    private fun baseLoadFields(operation: String, flowId: String, generation: Long): Map<String, Any?> =
        actionFields(operation, flowId) + mapOf("generation" to generation)

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
